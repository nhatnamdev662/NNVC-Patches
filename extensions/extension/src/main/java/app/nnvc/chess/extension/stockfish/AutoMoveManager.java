package app.nnvc.chess.extension.stockfish;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * AutoMoveManager – Direct Controller/Model Chess Move Automation.
 * 
 * 100% Parity with NNVC Desktop Extension Auto-Move mechanism.
 * Directly commands the native Chess.com board controller without touch event simulation,
 * avoiding coordinate calculations, screen resolution discrepancies, and UI overlay blockage.
 * 
 * Strict Rule: FAIL-FAST. NO TOUCH FALLBACK.
 */
public class AutoMoveManager {

    private static final String TAG = "NNVC_AutoMove";
    private static volatile Handler mainHandler = null;
    private static volatile boolean isExecutingMove = false;
    private static volatile String lastAttemptedFen = null;
    private static volatile String lastAttemptedUci = null;
    private static volatile long lastMoveTimestamp = 0;

    /** Pending scheduled move task to allow instant cancellation on board changes or user interaction */
    private static volatile Runnable pendingMoveRunnable = null;
    private static volatile String pendingMoveFen = null;

    /** Cached 64 static Square instances from com.chess.chessboard.w.b */
    private static volatile List<?> cachedSquaresList = null;

    // Fast Reflection Cache for 0ms Direct Model Move (Zero-Overhead)
    private static volatile Method cachedPMethod = null;
    private static volatile Class<?> cachedPMethodClass = null;
    private static volatile Object cachedMoveVerification = null;
    private static volatile ClassLoader cachedMoveVerificationCl = null;
    private static volatile Constructor<?> cachedOConstructor = null;
    private static volatile Constructor<?> cachedPConstructor = null;
    private static volatile Constructor<?> cachedXConstructor = null;
    private static volatile Method cachedCastlingEMethod = null;
    private static volatile Method cachedGetStateMethod = null;
    private static volatile Method cachedGetPositionMethod = null;

    /** Minimum cooldown between moves (ms) */
    private static final long COOLDOWN_MS = 35;

    /**
     * Cancels any pending scheduled auto-move immediately.
     * Prevents unintended PREMOVE when board state changes or user makes a move manually.
     */
    public static void cancelPendingAutoMove(String reason) {
        Handler h = getMainHandler();
        if (h != null && pendingMoveRunnable != null) {
            h.removeCallbacks(pendingMoveRunnable);
            NnvcLogger.d(TAG, "🚫 Cancelled pending Auto Move (" + reason + ")");
        }
        pendingMoveRunnable = null;
        pendingMoveFen = null;
        isExecutingMove = false;
    }

    public static boolean hasPendingScheduledMove() {
        return pendingMoveRunnable != null || isExecutingMove;
    }

    /**
     * Clears any active square selection or in-progress drag on the board.
     * Prevents tap confusion when user tapped another square before Auto Move fires.
     * Dispatches ACTION_CANCEL (3) to dependencies.k1() (MoveHandler), triggering listener.P3() -> vmState.f1(l.a).
     */
    public static void clearBoardSelection(View boardView, boolean flipped) {
        if (boardView == null) return;
        try {
            Class<?> bvClass = boardView.getClass();
            Object deps = null;
            try {
                Method getDepsMethod = bvClass.getMethod("getDependencies$cbview_release");
                getDepsMethod.setAccessible(true);
                deps = getDepsMethod.invoke(boardView);
            } catch (Throwable ignored) {}

            if (deps == null) {
                Class<?> c = bvClass;
                while (c != null && c != Object.class && deps == null) {
                    for (String fName : new String[]{"dependencies", "u"}) {
                        try {
                            Field uField = c.getDeclaredField(fName);
                            uField.setAccessible(true);
                            deps = uField.get(boardView);
                            if (deps != null) break;
                        } catch (NoSuchFieldException ignored) {}
                    }
                    c = c.getSuperclass();
                }
            }

            if (deps != null) {
                Method k1Method = deps.getClass().getMethod("k1");
                k1Method.setAccessible(true);
                Object moveHandler = k1Method.invoke(deps);
                if (moveHandler != null) {
                    Method moveActionMethod = moveHandler.getClass().getMethod("b", float.class, float.class, int.class, float.class, boolean.class);
                    moveActionMethod.setAccessible(true);
                    float squareSize = boardView.getWidth() > 0 ? (boardView.getWidth() / 8.0f) : 100f;
                    // Action 3 = ACTION_CANCEL -> calls listener.P3() -> clears selectedSquare
                    moveActionMethod.invoke(moveHandler, 0f, 0f, 3, squareSize, flipped);
                    NnvcLogger.d(TAG, "🧹 Cleared board selection (ACTION_CANCEL dispatched to moveHandler)");
                }
            }
        } catch (Throwable t) {
            NnvcLogger.w(TAG, "clearBoardSelection failed (non-fatal): " + t.getMessage());
        }
    }

    /**
     * Retrieves live board FEN directly from ChessBoardView.getPosition() if available.
     */
    public static String getCurrentBoardFen(View boardView) {
        if (boardView == null) return null;
        try {
            Method getPosMethod = boardView.getClass().getMethod("getPosition");
            getPosMethod.setAccessible(true);
            Object pos = getPosMethod.invoke(boardView);
            if (pos != null) {
                return StockfishExtension.extractFen(pos);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Just-In-Time (JIT) validation: Verifies whether the board is still in the exact same position
     * and whose turn it is before executing the move.
     * Prevents accidental PREMOVE if the user moved manually while the delay timer was ticking.
     */
    public static boolean isTurnAndPositionMatching(View boardView, String targetFen) {
        if (targetFen == null) return true;
        String liveFen = getCurrentBoardFen(boardView);
        if (liveFen == null) {
            liveFen = StockfishExtension.getCurrentBoardFen();
        }
        if (liveFen != null) {
            String[] liveParts = liveFen.trim().split("\\s+");
            String[] targetParts = targetFen.trim().split("\\s+");
            if (liveParts.length >= 2 && targetParts.length >= 2) {
                // 1. Piece placement must match
                if (!liveParts[0].equals(targetParts[0])) {
                    NnvcLogger.w(TAG, "🛑 JIT FEN mismatch: piece layout changed (live=" + liveParts[0] + ", target=" + targetParts[0] + ")");
                    return false;
                }
                // 2. Active turn must match
                if (!liveParts[1].equals(targetParts[1])) {
                    NnvcLogger.w(TAG, "🛑 JIT Turn mismatch: side to move changed (live=" + liveParts[1] + ", target=" + targetParts[1] + ")");
                    return false;
                }
            }
        }
        return true;
    }

    private static Handler getMainHandler() {
        if (mainHandler == null) {
            synchronized (AutoMoveManager.class) {
                if (mainHandler == null) {
                    try {
                        mainHandler = new Handler(Looper.getMainLooper());
                    } catch (Throwable ignored) {
                        mainHandler = null;
                    }
                }
            }
        }
        return mainHandler;
    }

    private static void postToMain(Runnable r) {
        Handler h = getMainHandler();
        if (h != null) h.post(r);
    }

    private static void postDelayedToMain(Runnable r, long delayMs) {
        Handler h = getMainHandler();
        if (h != null) h.postDelayed(r, delayMs);
    }

    private static long nowMs() {
        try {
            return SystemClock.uptimeMillis();
        } catch (Throwable t) {
            return System.currentTimeMillis();
        }
    }

    /**
     * Converts chess coordinate notation (e.g. "a1", "e4", "h8") to the 0..63 index
     * within the static squares list com.chess.chessboard.w.b.
     * 
     * Formula derived from com.chess.chessboard.w.c(file, rank):
     * rankIdx = rank.ordinal() (0 for rank 1, 7 for rank 8)
     * index = (7 - rankIdx) * 8 + fileIdx
     */
    public static int sqToIndex(String sq) {
        if (sq == null || sq.length() < 2) return -1;
        int fileIdx = sq.charAt(0) - 'a';
        int rankIdx = sq.charAt(1) - '1';
        if (fileIdx < 0 || fileIdx > 7 || rankIdx < 0 || rankIdx > 7) return -1;
        return (7 - rankIdx) * 8 + fileIdx;
    }

    /**
     * Resolves the native com.chess.chessboard.t (Square) instance for a coordinate.
     */
    public static Object getSquare(ClassLoader cl, String sq) {
        int idx = sqToIndex(sq);
        if (idx < 0 || idx >= 64 || cl == null) return null;
        try {
            if (cachedSquaresList == null) {
                synchronized (AutoMoveManager.class) {
                    if (cachedSquaresList == null) {
                        Class<?> wClass = Class.forName("com.chess.chessboard.w", true, cl);
                        Field bField = wClass.getDeclaredField("b");
                        bField.setAccessible(true);
                        cachedSquaresList = (List<?>) bField.get(null);
                        NnvcLogger.i(TAG, "Initialized cachedSquaresList: " + (cachedSquaresList != null ? cachedSquaresList.size() : "null"));
                    }
                }
            }
            if (cachedSquaresList != null && idx < cachedSquaresList.size()) {
                return cachedSquaresList.get(idx);
            }
        } catch (Throwable t) {
            NnvcLogger.e(TAG, "Failed to resolve Square object for " + sq + ": " + t.getMessage(), t);
        }
        return null;
    }

    /**
     * Calculates authentic biometric delay based on Fitts's Law.
     * Exact 100% parity with NNVC Desktop Extension (nnvc_main_bundle.js:16544):
     * delay = 95 + dist * 18 + random * 45 ms
     */
    public static long calculateFittsDelay(String fromSq, String toSq) {
        if (fromSq == null || toSq == null || fromSq.length() < 2 || toSq.length() < 2) {
            return 120;
        }
        int dx = Math.abs(toSq.charAt(0) - fromSq.charAt(0));
        int dy = Math.abs(toSq.charAt(1) - fromSq.charAt(1));
        double dist = Math.hypot(dx, dy);
        return (long) (95 + dist * 18 + Math.random() * 45);
    }

    /**
     * Legacy pixel center coordinate calculation for test assertions & metrics.
     */
    public static float[] getSquareCenter(String sq, boolean flipped, float boardWidth, float boardHeight) {
        if (sq == null || sq.length() < 2 || boardWidth <= 0 || boardHeight <= 0) return null;
        int file = sq.charAt(0) - 'a';
        int rank = sq.charAt(1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) return null;

        float sqSizeW = boardWidth / 8.0f;
        float sqSizeH = boardHeight / 8.0f;

        int col = flipped ? (7 - file) : file;
        int row = flipped ? rank : (7 - rank);

        float cx = col * sqSizeW + sqSizeW / 2.0f;
        float cy = row * sqSizeH + sqSizeH / 2.0f;
        return new float[]{cx, cy};
    }

    /**
     * Determines whether a UCI move represents a pawn promotion (e.g., e7e8q, c7c8q, or pawn reaching last rank).
     * If currentFen is provided, checks if the moving piece is actually a pawn ('p' or 'P').
     */
    public static boolean isPromotion(String uciMove, String currentFen) {
        if (uciMove == null || uciMove.length() < 4) return false;
        if (uciMove.length() >= 5) {
            char p = Character.toLowerCase(uciMove.charAt(4));
            if (p == 'q' || p == 'r' || p == 'b' || p == 'n') return true;
        }
        char fromRank = uciMove.charAt(1);
        char toRank = uciMove.charAt(3);
        boolean rankMatches = (fromRank == '7' && toRank == '8') || (fromRank == '2' && toRank == '1');
        if (!rankMatches) return false;

        // If FEN is available, ensure the piece moving is indeed a Pawn
        if (currentFen != null && !currentFen.isEmpty()) {
            char piece = StockfishExtension.getPieceAtSquare(currentFen, uciMove.substring(0, 2));
            if (piece != '.' && piece != ' ') {
                return piece == 'p' || piece == 'P';
            }
        }
        return true;
    }

    public static boolean isPromotion(String uciMove) {
        return isPromotion(uciMove, null);
    }

    /**
     * Configures ChessBoardView to promote directly to Queen without displaying a popup dialog.
     * Native Chess.com auto-queen mechanism:
     * chessBoardView.setPromotionTargets(PromotionTargets.b)
     */
    public static boolean applyPromotionQueen(View boardView) {
        if (boardView == null) return false;
        try {
            ClassLoader cl = boardView.getContext().getClassLoader();
            Class<?> promoTargetsClass = Class.forName("com.chess.chessboard.variants.PromotionTargets", true, cl);
            Field queenField = null;
            for (Field f : promoTargetsClass.getDeclaredFields()) {
                if (f.getType().equals(promoTargetsClass) && ("b".equals(f.getName()) || "QUEEN".equals(f.getName()))) {
                    queenField = f;
                    break;
                }
            }
            if (queenField != null) {
                queenField.setAccessible(true);
                Object queenTarget = queenField.get(null);
                if (queenTarget != null) {
                    Method m = boardView.getClass().getMethod("setPromotionTargets", promoTargetsClass);
                    m.setAccessible(true);
                    m.invoke(boardView, queenTarget);
                    NnvcLogger.i(TAG, "👑 Set PromotionTargets to QUEEN on ChessBoardView");
                    return true;
                }
            }
        } catch (Throwable t) {
            NnvcLogger.w(TAG, "applyPromotionQueen failed: " + t.getMessage());
        }
        return false;
    }

    /**
     * Secondary fallback: If a promo popup or dialog is present, dismiss or select Queen directly.
     */
    public static void dismissOrResolvePromoDialog(View boardView) {
        if (boardView == null) return;
        try {
            Field promoField = null;
            Class<?> c = boardView.getClass();
            while (c != null && c != Object.class) {
                try {
                    promoField = c.getDeclaredField("promoDialog");
                    break;
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
            if (promoField != null) {
                promoField.setAccessible(true);
                Object promoDialog = promoField.get(boardView);
                if (promoDialog != null) {
                    NnvcLogger.d(TAG, "Found active promoDialog: " + promoDialog);
                    for (Field f : promoDialog.getClass().getDeclaredFields()) {
                        f.setAccessible(true);
                        Object val = f.get(promoDialog);
                        if (val instanceof WeakReference) {
                            Object ref = ((WeakReference<?>) val).get();
                            if (ref instanceof PopupWindow) {
                                PopupWindow pw = (PopupWindow) ref;
                                View content = pw.getContentView();
                                if (content != null) {
                                    clickFirstClickable(content);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            NnvcLogger.w(TAG, "dismissOrResolvePromoDialog error: " + t.getMessage());
        }
    }

    private static boolean clickFirstClickable(View v) {
        if (v == null) return false;
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child.isClickable() || child.hasOnClickListeners()) {
                    child.performClick();
                    return true;
                }
                if (child instanceof ViewGroup) {
                    if (clickFirstClickable(child)) return true;
                }
            }
            if (vg.getChildCount() > 0) {
                vg.getChildAt(0).performClick();
                return true;
            }
        }
        return v.performClick();
    }

    public static boolean isBoardReadyForAutoMove(Activity activity, View boardView) {
        return isBoardReadyForAutoMove((Object) activity, (Object) boardView);
    }

    public static boolean isBoardReadyForAutoMove(Object activity, Object boardView) {
        if (activity != null) {
            try {
                if (activity instanceof Activity) {
                    Activity act = (Activity) activity;
                    if (act.isFinishing()) return false;
                    if (android.os.Build.VERSION.SDK_INT >= 17 && act.isDestroyed()) return false;
                    if (StockfishExtension.isGameOver(act)) return false;
                } else {
                    for (Method m : activity.getClass().getMethods()) {
                        if ("isFinishing".equals(m.getName()) && m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                            if ((boolean) m.invoke(activity)) return false;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (boardView != null) {
            try {
                if (boardView instanceof View) {
                    if (((View) boardView).getVisibility() != View.VISIBLE) return false;
                } else {
                    for (Method m : boardView.getClass().getMethods()) {
                        if ("getVisibility".equals(m.getName()) && m.getParameterCount() == 0 && m.getReturnType() == int.class) {
                            if ((int) m.invoke(boardView) != 0) return false;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return true;
    }

    public static boolean isV2Board(View boardView) {
        return isV2Board((Object) boardView);
    }

    public static boolean isV2Board(Object boardView) {
        if (boardView == null) return false;
        String name = boardView.getClass().getName();
        return name.contains(".v2.") || name.contains("V2") || name.equals("com.chess.chessboard.v2.ChessBoardView");
    }

    public static Object extractCBViewModelFromObject(Object obj) {
        return extractCBViewModelFromObject(obj, 0);
    }

    private static Object extractCBViewModelFromObject(Object obj, int depth) {
        if (obj == null || depth > 2) return null;
        if (obj.getClass().getName().contains("CBViewModel")) {
            return obj;
        }

        // 1. Check no-arg methods returning CBViewModel (e.g. T6(), c8())
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getName().contains("CBViewModel")) {
                    try {
                        m.setAccessible(true);
                        Object res = m.invoke(obj);
                        if (res != null) return res;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // 2. Check declared fields
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    String typeName = f.getType().getName();
                    String fName = f.getName();

                    // Direct field match
                    if (typeName.contains("CBViewModel")) {
                        f.setAccessible(true);
                        Object val = f.get(obj);
                        if (val != null) return val;
                    }

                    // Check Kotlin Lazy or Function0 (e.g. field z or lambda in RatedPuzzlesGameViewModel)
                    if (depth < 2 && (typeName.contains("Lazy") || typeName.contains("Function0") || fName.equals("z") || fName.contains("viewModel") || fName.contains("ViewModel"))) {
                        f.setAccessible(true);
                        Object val = f.get(obj);
                        if (val != null) {
                            if (val.getClass().getName().contains("CBViewModel")) {
                                return val;
                            }
                            // Try Lazy.getValue()
                            try {
                                Method gv = val.getClass().getMethod("getValue");
                                gv.setAccessible(true);
                                Object lazyVal = gv.invoke(val);
                                if (lazyVal != null) {
                                    if (lazyVal.getClass().getName().contains("CBViewModel")) {
                                        return lazyVal;
                                    }
                                    Object extracted = extractCBViewModelFromObject(lazyVal, depth + 1);
                                    if (extracted != null) return extracted;
                                }
                            } catch (NoSuchMethodException ignored) {}

                            // Try Function0.invoke()
                            try {
                                Method inv = val.getClass().getMethod("invoke");
                                if (inv.getParameterCount() == 0) {
                                    inv.setAccessible(true);
                                    Object fnVal = inv.invoke(val);
                                    if (fnVal != null) {
                                        if (fnVal.getClass().getName().contains("CBViewModel")) {
                                            return fnVal;
                                        }
                                        Object extracted = extractCBViewModelFromObject(fnVal, depth + 1);
                                        if (extracted != null) return extracted;
                                    }
                                }
                            } catch (NoSuchMethodException ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }
        return null;
    }

    public static Object resolveCBViewModelFromActivity(Activity activity) {
        if (activity == null) return null;
        try {
            // 1. Inspect ViewModelStore
            try {
                Method getVmStore = activity.getClass().getMethod("getViewModelStore");
                getVmStore.setAccessible(true);
                Object vmStore = getVmStore.invoke(activity);
                if (vmStore != null) {
                    Field mapField = null;
                    Class<?> sc = vmStore.getClass();
                    while (sc != null && sc != Object.class && mapField == null) {
                        for (Field f : sc.getDeclaredFields()) {
                            if (Map.class.isAssignableFrom(f.getType())) {
                                mapField = f;
                                break;
                            }
                        }
                        sc = sc.getSuperclass();
                    }
                    if (mapField != null) {
                        mapField.setAccessible(true);
                        Map<?, ?> map = (Map<?, ?>) mapField.get(vmStore);
                        if (map != null) {
                            for (Object vm : map.values()) {
                                if (vm != null) {
                                    Object resolved = extractCBViewModelFromObject(vm);
                                    if (resolved != null) {
                                        NnvcLogger.d(TAG, "Resolved CBViewModel from ViewModelStore map: " + resolved.getClass().getName());
                                        return resolved;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 2. Inspect Activity's own fields and methods
            Object resolved = extractCBViewModelFromObject(activity);
            if (resolved != null) {
                NnvcLogger.d(TAG, "Resolved CBViewModel from Activity hierarchy: " + resolved.getClass().getName());
                return resolved;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean executeDirectV2Move(ClassLoader cl, View boardView, String fromSq, String toSq, boolean isPromo, char promoPiece, String fenBeforeMove) {
        return executeDirectV2Move(cl, (Object) boardView, fromSq, toSq, isPromo, promoPiece, fenBeforeMove);
    }

    public static boolean executeDirectV2Move(ClassLoader cl, Object boardView, String fromSq, String toSq, boolean isPromo, char promoPiece, String fenBeforeMove) {
        if (cl == null || boardView == null || fromSq == null || toSq == null) return false;
        try {
            Object fromSquare = getSquare(cl, fromSq);
            Object toSquare = getSquare(cl, toSq);
            if (fromSquare == null || toSquare == null) {
                NnvcLogger.w(TAG, "executeDirectV2Move: Square objects not resolved (" + fromSq + ", " + toSq + ")");
                return false;
            }

            // 1. Get listener via getChessBoardGestureListener()
            Object listener = null;
            for (Method m : boardView.getClass().getMethods()) {
                if ("getChessBoardGestureListener".equals(m.getName()) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    listener = m.invoke(boardView);
                    break;
                }
            }
            if (listener == null) {
                Class<?> c = boardView.getClass();
                while (c != null && c != Object.class) {
                    for (Field f : c.getDeclaredFields()) {
                        if (f.getType().getName().contains(".chessboard.v2.") || f.getName().contains("gestureListener") || f.getName().contains("Listener")) {
                            f.setAccessible(true);
                            Object val = f.get(boardView);
                            if (val != null) {
                                listener = val;
                                break;
                            }
                        }
                    }
                    if (listener != null) break;
                    c = c.getSuperclass();
                }
            }

            boolean dispatched = false;
            if (listener != null) {
                // Method s.e(Square from, Square to)
                for (Method m : listener.getClass().getMethods()) {
                    if ("e".equals(m.getName()) && m.getParameterCount() == 2) {
                        m.setAccessible(true);
                        m.invoke(listener, fromSquare, toSquare);
                        dispatched = true;
                        NnvcLogger.i(TAG, "⚡ [DIRECT V2 MOVE] Dispatched piece drop via listener.e: " + fromSq + " -> " + toSq);
                        break;
                    }
                }
            }

            // 2. Animate move via boardView.j(Square from, Square to)
            for (Method m : boardView.getClass().getMethods()) {
                if ("j".equals(m.getName()) && m.getParameterCount() == 2) {
                    m.setAccessible(true);
                    m.invoke(boardView, fromSquare, toSquare);
                    NnvcLogger.d(TAG, "⚡ [DIRECT V2 MOVE] Animated piece move via boardView.j: " + fromSq + " -> " + toSq);
                    break;
                }
            }

            return dispatched;
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            NnvcLogger.w(TAG, "executeDirectV2Move error: " + cause.getMessage());
            return false;
        }
    }

    /**
     * Resolves the native CBViewModel instance controlling the chessboard.
     * Priority 1: Safe Field Reflection on boardView (viewModel, x, or CBViewModel type)
     * Priority 2: Synchronous Provider rvm.q().get()
     * Priority 3: Deep Delegate Reflection on rvm.cbDelegate (field K / b4 / j4: boardViewModel field j, _cbDataSource field c)
     * Priority 4: StockfishExtension.getStateImpl() provider lookup
     * Back-propagation Wire-up: Reflectively syncs resolved CBViewModel into boardView if missing.
     */
    public static Object resolveCBViewModel(View boardView, Activity activity) {
        return resolveCBViewModel((Object) boardView, activity);
    }

    public static Object resolveCBViewModel(Object boardView, Activity activity) {
        // Priority 1: Safe Field Reflection on boardView
        if (boardView != null) {
            Class<?> c = boardView.getClass();
            while (c != null && c != Object.class) {
                for (String fName : new String[]{"viewModel", "x"}) {
                    try {
                        Field f = c.getDeclaredField(fName);
                        f.setAccessible(true);
                        Object vm = f.get(boardView);
                        if (vm != null) return vm;
                    } catch (NoSuchFieldException ignored) {
                    } catch (Throwable ignored) {}
                }
                try {
                    for (Field f : c.getDeclaredFields()) {
                        try {
                            if (f.getType().getName().contains("CBViewModel")) {
                                f.setAccessible(true);
                                Object vm = f.get(boardView);
                                if (vm != null) return vm;
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
                c = c.getSuperclass();
            }

            // Safe fallback to getter method if implemented safely (e.g. mock objects)
            try {
                Method m = boardView.getClass().getMethod("getViewModel");
                m.setAccessible(true);
                Object vm = m.invoke(boardView);
                if (vm != null) return vm;
            } catch (Throwable ignored) {}

            try {
                for (Method m : boardView.getClass().getMethods()) {
                    if (m.getParameterTypes().length == 0 && m.getReturnType().getName().contains("CBViewModel")) {
                        m.setAccessible(true);
                        Object vm = m.invoke(boardView);
                        if (vm != null) return vm;
                    }
                }
            } catch (Throwable ignored) {}

            Object vmExt = extractCBViewModelFromObject(boardView);
            if (vmExt != null) return vmExt;
        }

        // Priority 2 & 3: Resolve from RealGameViewModel (rvm)
        Activity act = (activity != null) ? activity : StockfishExtension.getCurrentActivity();
        if (act == null && boardView != null) {
            try {
                Context ctx = null;
                if (boardView instanceof View) {
                    ctx = ((View) boardView).getContext();
                } else {
                    Method getCtx = boardView.getClass().getMethod("getContext");
                    ctx = (Context) getCtx.invoke(boardView);
                }
                while (ctx != null) {
                    if (ctx instanceof Activity) {
                        act = (Activity) ctx;
                        break;
                    }
                    try {
                        Method getBase = ctx.getClass().getMethod("getBaseContext");
                        Object base = getBase.invoke(ctx);
                        if (base instanceof Context && base != ctx) {
                            ctx = (Context) base;
                        } else {
                            break;
                        }
                    } catch (Throwable ignored) {
                        break;
                    }
                }
            } catch (Throwable ignored) {}
        }

        Object rvm = null;
        if (act != null) {
            rvm = AutoFindManager.getRealGameViewModel(act);
        }
        if (rvm == null) {
            rvm = AutoFindManager.getLastKnownViewModel();
        }

        Object cbVm = null;
        if (rvm != null) {
            // Priority 2: Synchronous Provider rvm.q().get()
            try {
                Method qMethod = rvm.getClass().getMethod("q");
                qMethod.setAccessible(true);
                Object provider = qMethod.invoke(rvm);
                if (provider != null) {
                    Method getMethod = provider.getClass().getMethod("get");
                    getMethod.setAccessible(true);
                    cbVm = getMethod.invoke(provider);
                    if (cbVm != null) {
                        NnvcLogger.d(TAG, "Resolved CBViewModel via rvm.q() Provider: " + cbVm.getClass().getName());
                    }
                }
            } catch (Throwable ignored) {}

            // Priority 3: Deep Delegate Reflection on rvm.cbDelegate (b4 / j4 / field K)
            if (cbVm == null) {
                Class<?> rvmCls = rvm.getClass();
                while (rvmCls != null && rvmCls != Object.class && cbVm == null) {
                    for (Field f : rvmCls.getDeclaredFields()) {
                        f.setAccessible(true);
                        Object delegate = null;
                        try {
                            delegate = f.get(rvm);
                        } catch (Throwable ignored) {}
                        if (delegate == null) continue;

                        String typeName = f.getType().getName();
                        String delClsName = delegate.getClass().getName();
                        boolean isDelegate = f.getName().equals("K") || f.getName().equals("cbDelegate") ||
                                             typeName.contains("b4") || typeName.contains("j4") ||
                                             delClsName.contains("b4") || delClsName.contains("j4");

                        if (isDelegate || f.getName().equals("K")) {
                            // 3a. Try delegate.q() provider
                            try {
                                Method qMethod = delegate.getClass().getMethod("q");
                                qMethod.setAccessible(true);
                                Object prov = qMethod.invoke(delegate);
                                if (prov != null) {
                                    Method getMethod = prov.getClass().getMethod("get");
                                    getMethod.setAccessible(true);
                                    cbVm = getMethod.invoke(prov);
                                    if (cbVm != null) {
                                        NnvcLogger.d(TAG, "Resolved CBViewModel via cbDelegate.q(): " + cbVm.getClass().getName());
                                        break;
                                    }
                                }
                            } catch (Throwable ignored) {}

                            // 3b. Try field 'boardViewModel' or 'j' or scanning fields of type CBViewModel / d
                            Class<?> delCls = delegate.getClass();
                            while (delCls != null && delCls != Object.class && cbVm == null) {
                                for (String jName : new String[]{"boardViewModel", "j"}) {
                                    try {
                                        Field jField = delCls.getDeclaredField(jName);
                                        jField.setAccessible(true);
                                        Object val = jField.get(delegate);
                                        if (val != null) {
                                            cbVm = val;
                                            NnvcLogger.d(TAG, "Resolved CBViewModel via cbDelegate." + jName + ": " + cbVm.getClass().getName());
                                            break;
                                        }
                                    } catch (Throwable ignored) {}
                                }
                                if (cbVm != null) break;

                                for (Field df : delCls.getDeclaredFields()) {
                                    try {
                                        if (df.getType().getName().contains("CBViewModel") || df.getType().getName().equals("com.chess.internal.utils.chessboard.d")) {
                                            df.setAccessible(true);
                                            Object val = df.get(delegate);
                                            if (val != null) {
                                                cbVm = val;
                                                NnvcLogger.d(TAG, "Resolved CBViewModel via cbDelegate." + df.getName() + " (type match): " + cbVm.getClass().getName());
                                                break;
                                            }
                                        }
                                    } catch (Throwable ignored) {}
                                }
                                if (cbVm != null) break;
                                delCls = delCls.getSuperclass();
                            }
                            if (cbVm != null) break;

                            // 3c. Try field '_cbDataSource' or 'c' (StateFlow)
                            delCls = delegate.getClass();
                            while (delCls != null && delCls != Object.class && cbVm == null) {
                                for (String cName : new String[]{"_cbDataSource", "c"}) {
                                    try {
                                        Field cField = delCls.getDeclaredField(cName);
                                        cField.setAccessible(true);
                                        Object flow = cField.get(delegate);
                                        if (flow != null) {
                                            Method gv = flow.getClass().getMethod("getValue");
                                            gv.setAccessible(true);
                                            Object val = gv.invoke(flow);
                                            if (val != null) {
                                                cbVm = val;
                                                NnvcLogger.d(TAG, "Resolved CBViewModel via cbDelegate." + cName + ".getValue(): " + cbVm.getClass().getName());
                                                break;
                                            }
                                        }
                                    } catch (Throwable ignored) {}
                                }
                                if (cbVm != null) break;
                                delCls = delCls.getSuperclass();
                            }
                            if (cbVm != null) break;
                        }
                    }
                    rvmCls = rvmCls.getSuperclass();
                }
            }

            // Fallback: check rvm.J().getValue() in case J() is a StateFlow
            if (cbVm == null) {
                try {
                    Method jMethod = rvm.getClass().getMethod("J");
                    jMethod.setAccessible(true);
                    Object flow = jMethod.invoke(rvm);
                    if (flow != null) {
                        Method gv = flow.getClass().getMethod("getValue");
                        gv.setAccessible(true);
                        cbVm = gv.invoke(flow);
                    }
                } catch (Throwable ignored) {}
            }
        }

        // Priority 3.5: Resolve CBViewModel from non-RealGame Activity (Lessons, Puzzles, Daily, Bots)
        if (cbVm == null && act != null) {
            cbVm = resolveCBViewModelFromActivity(act);
        }

        // Priority 4: StockfishExtension.getStateImpl()
        if (cbVm == null) {
            try {
                Object state = StockfishExtension.getStateImpl();
                if (state != null) {
                    for (Method m : state.getClass().getMethods()) {
                        if (m.getParameterTypes().length == 0 && m.getReturnType().getName().contains("CBViewModel")) {
                            m.setAccessible(true);
                            cbVm = m.invoke(state);
                            if (cbVm != null) break;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Back-propagation Wire-up: If resolved from rvm/state and boardView is missing viewModel, sync it
        if (cbVm != null && boardView != null) {
            wireUpViewModelToBoardView(boardView, cbVm);
        }

        return cbVm;
    }

    /**
     * Reflectively syncs the resolved CBViewModel into ChessBoardView so the View
     * is immediately fully configured without waiting for the Activity coroutine.
     */
    public static void wireUpViewModelToBoardView(Object boardView, Object cbVm) {
        if (boardView == null || cbVm == null) return;
        try {
            Class<?> c = boardView.getClass();
            boolean alreadySet = false;
            // Check if boardView already has viewModel non-null
            while (c != null && c != Object.class) {
                for (String fName : new String[]{"viewModel", "x"}) {
                    try {
                        Field f = c.getDeclaredField(fName);
                        f.setAccessible(true);
                        if (f.get(boardView) != null) {
                            alreadySet = true;
                            break;
                        }
                    } catch (NoSuchFieldException ignored) {}
                }
                if (alreadySet) break;
                c = c.getSuperclass();
            }

            if (alreadySet) return;

            // 1. Try calling setViewModel(cbVm)
            for (Method m : boardView.getClass().getMethods()) {
                if ("setViewModel".equals(m.getName()) && m.getParameterTypes().length == 1) {
                    try {
                        m.setAccessible(true);
                        m.invoke(boardView, cbVm);
                        NnvcLogger.d(TAG, "⚡ Wire-up: Invoked boardView.setViewModel() successfully");
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            // 2. Set field directly (field 'x' or 'viewModel' or any field matching CBViewModel type)
            c = boardView.getClass();
            while (c != null && c != Object.class) {
                for (String fName : new String[]{"viewModel", "x"}) {
                    try {
                        Field f = c.getDeclaredField(fName);
                        f.setAccessible(true);
                        f.set(boardView, cbVm);
                        NnvcLogger.d(TAG, "⚡ Wire-up: Set boardView." + fName + " field successfully");
                    } catch (NoSuchFieldException ignored) {}
                }
                for (Field f : c.getDeclaredFields()) {
                    try {
                        if (f.getType().getName().contains("CBViewModel") || f.getType().isAssignableFrom(cbVm.getClass())) {
                            f.setAccessible(true);
                            if (f.get(boardView) == null) {
                                f.set(boardView, cbVm);
                                NnvcLogger.d(TAG, "⚡ Wire-up: Set boardView." + f.getName() + " (type match) successfully");
                            }
                        }
                    } catch (Throwable ignored) {}
                }
                c = c.getSuperclass();
            }
        } catch (Throwable t) {
            NnvcLogger.w(TAG, "wireUpViewModelToBoardView non-fatal error: " + t.getMessage());
        }
    }
    /**
     * Constructs a native Move object (com.chess.chessboard.l).
     * Normal move: com.chess.chessboard.o(fromSquare, toSquare)
     * Castling move: com.chess.chessboard.x(color, castlingFiles, castlingType)
     * Queen promotion: com.chess.chessboard.p(fromSquare, toSquare, PieceKind.f)
     */
    public static Object createMoveObject(ClassLoader cl, Object fromSquare, Object toSquare, boolean isPromo) {
        return createMoveObject(cl, fromSquare, toSquare, isPromo, 'q', null, null, null, null);
    }

    /**
     * Constructs a native Move object with specific promotion piece kind (q, r, b, n).
     */
    public static Object createMoveObject(ClassLoader cl, Object fromSquare, Object toSquare, boolean isPromo, char promoPiece) {
        return createMoveObject(cl, fromSquare, toSquare, isPromo, promoPiece, null, null, null, null);
    }

    /**
     * Comprehensive Move factory supporting standard moves, under-promotions, and native atomic castling.
     */
    public static Object createMoveObject(ClassLoader cl, Object fromSquare, Object toSquare, boolean isPromo, char promoPiece, String fromSq, String toSq, String snapshotFen, Object positionObj) {
        if (cl == null || fromSquare == null || toSquare == null) return null;
        Class<?> squareClass = fromSquare.getClass();
        try {
            if (isPromo) {
                Class<?> pClass = Class.forName("com.chess.chessboard.p", true, cl);
                Class<?> pieceKindClass = Class.forName("com.chess.chessboard.PieceKind", true, cl);
                Object promoKind = null;
                char pChar = Character.toLowerCase(promoPiece);
                String fieldName = "f";
                String enumName = "QUEEN";
                if (pChar == 'n') {
                    fieldName = "c";
                    enumName = "KNIGHT";
                } else if (pChar == 'b') {
                    fieldName = "d";
                    enumName = "BISHOP";
                } else if (pChar == 'r') {
                    fieldName = "e";
                    enumName = "ROOK";
                }
                try {
                    Field f = pieceKindClass.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    promoKind = f.get(null);
                } catch (Throwable ignored) {}
                if (promoKind == null) {
                    for (Object enumConst : pieceKindClass.getEnumConstants()) {
                        if (enumName.equalsIgnoreCase(((Enum<?>) enumConst).name())) {
                            promoKind = enumConst;
                            break;
                        }
                    }
                }
                if (promoKind != null) {
                    if (cachedPConstructor != null) {
                        try {
                            return cachedPConstructor.newInstance(fromSquare, toSquare, promoKind);
                        } catch (Throwable ignored) {}
                    }
                    for (Constructor<?> ctor : pClass.getConstructors()) {
                        Class<?>[] pts = ctor.getParameterTypes();
                        if (pts.length == 3 && pts[0].isAssignableFrom(squareClass) && pts[1].isAssignableFrom(squareClass) && pts[2].isAssignableFrom(pieceKindClass)) {
                            ctor.setAccessible(true);
                            cachedPConstructor = ctor;
                            return ctor.newInstance(fromSquare, toSquare, promoKind);
                        }
                    }
                }
            }

            // Nhận diện nước đi nhập thành chuẩn (Castling): com.chess.chessboard.x(color, castlingFiles, castlingType)
            boolean isCastleKingside = ("e1".equals(fromSq) && ("g1".equals(toSq) || "h1".equals(toSq))) ||
                                       ("e8".equals(fromSq) && ("g8".equals(toSq) || "h8".equals(toSq)));
            boolean isCastleQueenside = ("e1".equals(fromSq) && ("c1".equals(toSq) || "a1".equals(toSq))) ||
                                        ("e8".equals(fromSq) && ("c8".equals(toSq) || "a8".equals(toSq)));
            boolean isCastling = isCastleKingside || isCastleQueenside;

            if (isCastling && snapshotFen != null && fromSq != null) {
                char fromPiece = StockfishExtension.getPieceAtSquare(snapshotFen, fromSq);
                if (fromPiece != '.' && fromPiece != 'K' && fromPiece != 'k') {
                    isCastling = false; // Quân khác vua đi ngang (ví dụ Hậu/Xe)
                }
            }

            if (isCastling) {
                try {
                    boolean isWhite = "e1".equals(fromSq) || (snapshotFen != null && StockfishExtension.getPieceAtSquare(snapshotFen, fromSq) == 'K');

                    Class<?> colorClass = Class.forName("com.chess.entities.Color", true, cl);
                    Object colorObj = null;
                    for (Object c : colorClass.getEnumConstants()) {
                        if ((isWhite && "WHITE".equalsIgnoreCase(((Enum<?>) c).name())) ||
                            (!isWhite && "BLACK".equalsIgnoreCase(((Enum<?>) c).name()))) {
                            colorObj = c;
                            break;
                        }
                    }

                    Class<?> castlingTypeClass = Class.forName("com.chess.chessboard.CastlingType", true, cl);
                    Object castlingTypeObj = null;
                    for (Object ct : castlingTypeClass.getEnumConstants()) {
                        if ((isCastleKingside && "KINGSIDE".equalsIgnoreCase(((Enum<?>) ct).name())) ||
                            (!isCastleKingside && "QUEENSIDE".equalsIgnoreCase(((Enum<?>) ct).name()))) {
                            castlingTypeObj = ct;
                            break;
                        }
                    }

                    // Cách 1: Gọi StandardPosition.i(color, castlingType) nếu có
                    if (positionObj != null && colorObj != null && castlingTypeObj != null) {
                        try {
                            for (Method m : positionObj.getClass().getMethods()) {
                                if ("i".equals(m.getName()) && m.getParameterCount() == 2) {
                                    Class<?>[] pts = m.getParameterTypes();
                                    if (pts[0].isAssignableFrom(colorClass) && pts[1].isAssignableFrom(castlingTypeClass)) {
                                        m.setAccessible(true);
                                        Object xMove = m.invoke(positionObj, colorObj, castlingTypeObj);
                                        if (xMove != null) {
                                            NnvcLogger.i(TAG, "⚡ Constructed Castling Move via position.i: " + fromSq + toSq);
                                            return xMove;
                                        }
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }

                    // Cách 2: Tìm castlingFiles (com.chess.chessboard.c)
                    Object castlingFilesObj = null;
                    if (positionObj != null) {
                        try {
                            Method jMethod = positionObj.getClass().getMethod("j");
                            jMethod.setAccessible(true);
                            castlingFilesObj = jMethod.invoke(positionObj);
                        } catch (Throwable ignored) {}
                    }
                    if (castlingFilesObj == null) {
                        try {
                            Class<?> cbClass = Class.forName("com.chess.chessboard.c$b", true, cl);
                            Field aField = cbClass.getDeclaredField("a");
                            aField.setAccessible(true);
                            castlingFilesObj = aField.get(null);
                        } catch (Throwable ignored) {}
                    }

                    if (colorObj != null && castlingFilesObj != null && castlingTypeObj != null) {
                        if (cachedCastlingEMethod != null) {
                            try {
                                Object xMove = cachedCastlingEMethod.invoke(null, castlingFilesObj, colorObj, castlingTypeObj);
                                if (xMove != null) return xMove;
                            } catch (Throwable ignored) {}
                        }
                        // Thử qua helper com.chess.chessboard.e.a(castlingFiles, color, castlingType)
                        try {
                            Class<?> eClass = Class.forName("com.chess.chessboard.e", true, cl);
                            for (Method m : eClass.getMethods()) {
                                if ("a".equals(m.getName()) && m.getParameterCount() == 3) {
                                    Class<?>[] pts = m.getParameterTypes();
                                    if (pts[0].isAssignableFrom(castlingFilesObj.getClass()) &&
                                        pts[1].isAssignableFrom(colorClass) &&
                                        pts[2].isAssignableFrom(castlingTypeClass)) {
                                        m.setAccessible(true);
                                        cachedCastlingEMethod = m;
                                        Object xMove = m.invoke(null, castlingFilesObj, colorObj, castlingTypeObj);
                                        if (xMove != null) {
                                            NnvcLogger.i(TAG, "⚡ Constructed Castling Move via e.a: " + fromSq + toSq);
                                            return xMove;
                                        }
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}

                        if (cachedXConstructor != null) {
                            try {
                                Object xMove = cachedXConstructor.newInstance(colorObj, castlingFilesObj, castlingTypeObj);
                                if (xMove != null) return xMove;
                            } catch (Throwable ignored) {}
                        }
                        // Thử khởi tạo trực tiếp new com.chess.chessboard.x(color, castlingFiles, castlingType)
                        try {
                            Class<?> xClass = Class.forName("com.chess.chessboard.x", true, cl);
                            for (Constructor<?> ctor : xClass.getConstructors()) {
                                Class<?>[] pts = ctor.getParameterTypes();
                                if (pts.length == 3 &&
                                    pts[0].isAssignableFrom(colorClass) &&
                                    pts[1].isAssignableFrom(castlingFilesObj.getClass()) &&
                                    pts[2].isAssignableFrom(castlingTypeClass)) {
                                    ctor.setAccessible(true);
                                    cachedXConstructor = ctor;
                                    Object xMove = ctor.newInstance(colorObj, castlingFilesObj, castlingTypeObj);
                                    if (xMove != null) {
                                        NnvcLogger.i(TAG, "⚡ Constructed Castling Move via x ctor: " + fromSq + toSq);
                                        return xMove;
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ct) {
                    NnvcLogger.w(TAG, "Castling move resolution error: " + ct.getMessage());
                }
            }

            if (cachedOConstructor != null) {
                try {
                    return cachedOConstructor.newInstance(fromSquare, toSquare);
                } catch (Throwable ignored) {}
            }
            Class<?> oClass = Class.forName("com.chess.chessboard.o", true, cl);
            for (Constructor<?> ctor : oClass.getConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length == 2 && pts[0].isAssignableFrom(squareClass) && pts[1].isAssignableFrom(squareClass)) {
                    ctor.setAccessible(true);
                    cachedOConstructor = ctor;
                    return ctor.newInstance(fromSquare, toSquare);
                }
            }
        } catch (Throwable t) {
            NnvcLogger.w(TAG, "createMoveObject failed: " + t.getMessage());
        }
        return null;
    }

    /**
     * Resolves the MoveVerification singleton instance (com.chess.chessboard.vm.movesinput.j0.a).
     */
    public static Object getMoveVerification(ClassLoader cl) {
        if (cl == null) return null;
        if (cachedMoveVerification != null && cachedMoveVerificationCl == cl) {
            return cachedMoveVerification;
        }
        try {
            Class<?> j0Class = Class.forName("com.chess.chessboard.vm.movesinput.j0", true, cl);
            Field aField = j0Class.getDeclaredField("a");
            aField.setAccessible(true);
            Object inst = aField.get(null);
            cachedMoveVerification = inst;
            cachedMoveVerificationCl = cl;
            return inst;
        } catch (Throwable t) {
            NnvcLogger.w(TAG, "getMoveVerification failed: " + t.getMessage());
        }
        return null;
    }

    /**
     * Executes a move directly on the native CBViewModel instance in 0ms (Desktop Extension Parity).
     * Invokes CBViewModel.p(com.chess.chessboard.l, MoveVerification, boolean).
     */
    public static boolean executeDirectModelMove(ClassLoader cl, Object cbVm, String fromSq, String toSq, boolean isPromo) {
        return executeDirectModelMove(cl, cbVm, fromSq, toSq, isPromo, 'q', null);
    }

    public static boolean executeDirectModelMove(ClassLoader cl, Object cbVm, String fromSq, String toSq, boolean isPromo, char promoPiece) {
        return executeDirectModelMove(cl, cbVm, fromSq, toSq, isPromo, promoPiece, null);
    }

    public static boolean executeDirectModelMove(ClassLoader cl, Object cbVm, String fromSq, String toSq, boolean isPromo, char promoPiece, String fenBeforeMove) {
        if (cl == null || cbVm == null) return false;
        try {
            Object fromSquare = getSquare(cl, fromSq);
            Object toSquare = getSquare(cl, toSq);
            if (fromSquare == null || toSquare == null) {
                NnvcLogger.w(TAG, "executeDirectModelMove: Square objects not resolved (" + fromSq + ", " + toSq + ")");
                return false;
            }

            String snapshotFen = (fenBeforeMove != null) ? fenBeforeMove : StockfishExtension.getCurrentBoardFen();

            Object positionObj = null;
            try {
                Method getStateMethod = cachedGetStateMethod;
                if (getStateMethod == null || getStateMethod.getDeclaringClass() != cbVm.getClass()) {
                    getStateMethod = cbVm.getClass().getMethod("getState");
                    getStateMethod.setAccessible(true);
                    cachedGetStateMethod = getStateMethod;
                }
                Object stateObj = getStateMethod.invoke(cbVm);
                if (stateObj != null) {
                    Method getPosMethod = cachedGetPositionMethod;
                    if (getPosMethod == null || getPosMethod.getDeclaringClass() != stateObj.getClass()) {
                        getPosMethod = stateObj.getClass().getMethod("getPosition");
                        getPosMethod.setAccessible(true);
                        cachedGetPositionMethod = getPosMethod;
                    }
                    positionObj = getPosMethod.invoke(stateObj);
                }
            } catch (Throwable ignored) {}

            Object moveObj = createMoveObject(cl, fromSquare, toSquare, isPromo, promoPiece, fromSq, toSq, snapshotFen, positionObj);
            if (moveObj == null) {
                NnvcLogger.w(TAG, "executeDirectModelMove: Failed to construct Move object");
                return false;
            }

            Object moveVerification = getMoveVerification(cl);
            if (moveVerification == null) {
                NnvcLogger.w(TAG, "executeDirectModelMove: MoveVerification not found");
                return false;
            }

            Method pMethod = cachedPMethod;
            if (pMethod == null || cachedPMethodClass != cbVm.getClass()) {
                for (Method m : cbVm.getClass().getMethods()) {
                    if ("p".equals(m.getName()) && m.getParameterCount() == 3) {
                        Class<?>[] pts = m.getParameterTypes();
                        if (pts[2] == boolean.class) {
                            pMethod = m;
                            break;
                        }
                    }
                }
                if (pMethod == null) {
                    Class<?> c = cbVm.getClass();
                    while (c != null && c != Object.class) {
                        for (Method m : c.getDeclaredMethods()) {
                            if ("p".equals(m.getName()) && m.getParameterCount() == 3) {
                                Class<?>[] pts = m.getParameterTypes();
                                if (pts[2] == boolean.class) {
                                    pMethod = m;
                                    break;
                                }
                            }
                        }
                        if (pMethod != null) break;
                        c = c.getSuperclass();
                    }
                }
                if (pMethod != null) {
                    pMethod.setAccessible(true);
                    cachedPMethod = pMethod;
                    cachedPMethodClass = cbVm.getClass();
                }
            }
            if (pMethod == null) {
                NnvcLogger.w(TAG, "executeDirectModelMove: Method p not found on " + cbVm.getClass().getName());
                return false;
            }

            // Xác định chính xác loại nước đi dựa trên FEN TRƯỚC KHI THỰC HIỆN NƯỚC ĐI
            boolean isCastle = ("e1".equals(fromSq) && ("g1".equals(toSq) || "c1".equals(toSq))) ||
                               ("e8".equals(fromSq) && ("g8".equals(toSq) || "c8".equals(toSq)));
            boolean isCapture = false;
            if (snapshotFen != null && toSq != null && toSq.length() >= 2 && fromSq != null && fromSq.length() >= 2) {
                char targetPiece = StockfishExtension.getPieceAtSquare(snapshotFen, toSq);
                char fromPiece = StockfishExtension.getPieceAtSquare(snapshotFen, fromSq);
                if (isCastle && (fromPiece == 'K' || fromPiece == 'k')) {
                    isCapture = false;
                } else if (targetPiece != '.') {
                    isCapture = true;
                } else if (Character.toLowerCase(fromPiece) == 'p' && fromSq.charAt(0) != toSq.charAt(0)) {
                    // En passant: Tốt đi chéo vào ô trống là nước ăn quân
                    isCapture = true;
                }
            }

            pMethod.setAccessible(true);
            pMethod.invoke(cbVm, moveObj, moveVerification, true);
            NnvcLogger.i(TAG, "⚡ [DIRECT MODEL AUTO MOVE] Executed 0ms move directly via CBViewModel.p: " + fromSq + toSq + (isPromo ? promoPiece : ""));
            return true;
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            NnvcLogger.w(TAG, "executeDirectModelMove invoke error: " + cause.getMessage());
            return false;
        }
    }

    public static boolean executeDirectModelMove(ClassLoader cl, View boardView, Activity activity, String fromSq, String toSq, boolean isPromo) {
        return executeDirectModelMove(cl, boardView, activity, fromSq, toSq, isPromo, 'q', null);
    }

    public static boolean executeDirectModelMove(ClassLoader cl, View boardView, Activity activity, String fromSq, String toSq, boolean isPromo, char promoPiece) {
        return executeDirectModelMove(cl, boardView, activity, fromSq, toSq, isPromo, promoPiece, null);
    }

    public static boolean executeDirectModelMove(ClassLoader cl, View boardView, Activity activity, String fromSq, String toSq, boolean isPromo, char promoPiece, String fenBeforeMove) {
        Object cbVm = resolveCBViewModel(boardView, activity);
        if (cbVm == null) return false;
        return executeDirectModelMove(cl, cbVm, fromSq, toSq, isPromo, promoPiece, fenBeforeMove);
    }

    /**
     * Executes Auto Move with retry and graceful fallback between Direct Model Hook and Controller Executor.
     */
    public static void executeMoveWithRetry(final Context context,
                                            final View boardView,
                                            final String uciMove,
                                            final boolean flipped,
                                            final String currentFen,
                                            final float evalScore,
                                            final long engineThinkMs,
                                            final boolean userWhite,
                                            final Activity activity,
                                            final String fromSq,
                                            final String toSq,
                                            final boolean isPromo,
                                            final long fittsDelay,
                                            final int retryCount) {
        executeMoveWithRetry(context, boardView, uciMove, flipped, currentFen, evalScore, engineThinkMs, userWhite, activity, fromSq, toSq, isPromo, (uciMove != null && uciMove.length() >= 5) ? Character.toLowerCase(uciMove.charAt(4)) : 'q', fittsDelay, retryCount);
    }

    public static void executeMoveWithRetry(final Context context,
                                            final View boardView,
                                            final String uciMove,
                                            final boolean flipped,
                                            final String currentFen,
                                            final float evalScore,
                                            final long engineThinkMs,
                                            final boolean userWhite,
                                            final Activity activity,
                                            final String fromSq,
                                            final String toSq,
                                            final boolean isPromo,
                                            final char promoChar,
                                            final long fittsDelay,
                                            final int retryCount) {
        try {
            // 0. Board and Activity readiness check
            if (!isBoardReadyForAutoMove(activity, boardView)) {
                NnvcLogger.d(TAG, "executeMoveWithRetry aborted: Board or Activity is not ready.");
                isExecutingMove = false;
                pendingMoveRunnable = null;
                return;
            }

            // 1. Just-In-Time (JIT) check: verify board is still on target position and it is user's turn
            if (!isTurnAndPositionMatching(boardView, currentFen)) {
                NnvcLogger.i(TAG, "🛑 [AUTO MOVE JIT ABORT] Ván cờ đã đổi hoặc không còn là lượt người chơi. Hủy nước đi chống PREMOVE: " + uciMove);
                cancelPendingAutoMove("JIT mismatch (prevented PREMOVE)");
                return;
            }

            ClassLoader cl = boardView.getContext().getClassLoader();

            // 2. Direct Model Hook (0ms execution, 100% Desktop Extension Parity)
            boolean directSuccess = executeDirectModelMove(cl, boardView, activity, fromSq, toSq, isPromo, promoChar, currentFen);
            if (!directSuccess && isV2Board(boardView)) {
                directSuccess = executeDirectV2Move(cl, boardView, fromSq, toSq, isPromo, promoChar, currentFen);
            }
            if (directSuccess) {
                isExecutingMove = false;
                pendingMoveRunnable = null;
                if (isPromo && promoChar == 'q') {
                    applyPromotionQueen(boardView);
                }
                NnvcLogger.i(TAG, "⚡ [DIRECT AUTO MOVE] Nước đi đã được đẩy trực tiếp (0ms): " + uciMove);
                SmartDelayManager.recordMyMove(uciMove, currentFen, activity, userWhite);
                schedulePostMoveValidation(boardView, currentFen, uciMove);
                return;
            }

            // 3. Retry if CBViewModel is temporarily null at Move 0
            if (retryCount < 3 && resolveCBViewModel(boardView, activity) == null) {
                NnvcLogger.d(TAG, "⏳ CBViewModel tạm thời null (Move 0 setup), tự động thử lại sau 50ms (lần " + (retryCount + 1) + ")");
                postDelayedToMain(new Runnable() {
                    @Override
                    public void run() {
                        executeMoveWithRetry(context, boardView, uciMove, flipped, currentFen, evalScore, engineThinkMs, userWhite, activity, fromSq, toSq, isPromo, promoChar, fittsDelay, retryCount + 1);
                    }
                }, 50);
                return;
            }

            // 4. ZERO-FALLBACK: Fail-fast, log ERROR and stop. Never tap or touch the screen!
            NnvcLogger.e(TAG, "❌ Auto Move FAILED: Direct Model Move thất bại cho nước đi " + uciMove + " (Zero-Fallback Architecture: Fail-Fast, tuyệt đối không chạm/tap màn hình!)");
            isExecutingMove = false;
            pendingMoveRunnable = null;
            lastAttemptedFen = null;
            return;

        } catch (Throwable t) {
            NnvcLogger.e(TAG, "❌ executeMoveWithRetry failed: " + t.getMessage(), t);
            isExecutingMove = false;
            pendingMoveRunnable = null;
            lastAttemptedFen = null;
        }
    }

    /**
     * Executes Auto Move using Direct Controller/Model invocation (Extension Style).
     * 
     * STRICT REQUIREMENT: NO TOUCH SIMULATION FALLBACK.
     * If the native controller or square instance cannot be found, it fails fast and logs the error.
     */
    public static void executeAutoMove(final Context context,
                                       final View boardView,
                                       final String uciMove,
                                       final boolean flipped,
                                       final String currentFen) {
        executeAutoMove(context, boardView, uciMove, flipped, currentFen, 0.0f, 0L, true, null);
    }

    /**
     * Executes Auto Move using Direct Controller/Model invocation (Extension Style)
     * with Smart Delay biometric human-like pacing and think time deduction.
     */
    public static void executeAutoMove(final Context context,
                                       final View boardView,
                                       final String uciMove,
                                       final boolean flipped,
                                       final String currentFen,
                                       final float evalScore,
                                       final long engineThinkMs,
                                       final boolean userWhite,
                                       final Activity activity) {
        executeAutoMove(context, boardView, uciMove, flipped, currentFen, evalScore, engineThinkMs, userWhite, activity, false, 0);
    }

    public static void executeAutoMove(final Context context,
                                       final View boardView,
                                       final String uciMove,
                                       final boolean flipped,
                                       final String currentFen,
                                       final float evalScore,
                                       final long engineThinkMs,
                                       final boolean userWhite,
                                       final Activity activity,
                                       final boolean hasMate,
                                       final int mateIn) {
        if (context == null || boardView == null || uciMove == null || uciMove.length() < 4) {
            NnvcLogger.w(TAG, "executeAutoMove aborted: invalid arguments (ctx=" + (context != null) + ", bv=" + (boardView != null) + ", move=" + uciMove + ")");
            return;
        }

        if (!StockfishSettings.isAutoMoveEnabled(context)) {
            NnvcLogger.w(TAG, "executeAutoMove aborted: Auto Move is DISABLED in settings (Tab LIVE).");
            return;
        }

        if (!isBoardReadyForAutoMove(activity, boardView)) {
            NnvcLogger.d(TAG, "executeAutoMove skipped: Board or Activity not ready.");
            return;
        }

        if (pendingMoveRunnable != null || isExecutingMove) {
            if (pendingMoveFen != null && currentFen != null && !pendingMoveFen.split(" ")[0].equals(currentFen.split(" ")[0])) {
                cancelPendingAutoMove("Position changed to new FEN while previous move was pending");
            } else {
                NnvcLogger.d(TAG, "Auto Move skipped: execution or schedule already in progress for this position.");
                return;
            }
        }

        long now = nowMs();
        long elapsed = now - lastMoveTimestamp;
        if (elapsed < COOLDOWN_MS) {
            final long waitTime = COOLDOWN_MS - elapsed;
            NnvcLogger.d(TAG, "Auto Move throttled by cooldown window (" + elapsed + "ms < " + COOLDOWN_MS + "ms). Scheduling in " + waitTime + "ms.");
            final Runnable throttledTask = new Runnable() {
                @Override
                public void run() {
                    executeAutoMove(context, boardView, uciMove, flipped, currentFen, evalScore, engineThinkMs, userWhite, activity, hasMate, mateIn);
                }
            };
            pendingMoveRunnable = throttledTask;
            pendingMoveFen = currentFen;
            postDelayedToMain(throttledTask, waitTime);
            return;
        }

        if (currentFen != null && currentFen.equals(lastAttemptedFen) && uciMove.equals(lastAttemptedUci)) {
            NnvcLogger.d(TAG, "Auto Move skipped: already attempted on this position (" + uciMove + ").");
            return;
        }

        final String fromSq = uciMove.substring(0, 2);
        final String toSq = uciMove.substring(2, 4);
        final boolean isPromo = isPromotion(uciMove, currentFen);
        final char promoChar = (uciMove.length() >= 5) ? Character.toLowerCase(uciMove.charAt(4)) : 'q';
        final long fittsDelay = calculateFittsDelay(fromSq, toSq);

        // Tính toán độ trễ thông minh Smart Delay (chuẩn 100% Desktop Extension)
        long calculatedDelay = 0;
        if (StockfishSettings.isSmartDelayEnabled(context)) {
            calculatedDelay = SmartDelayManager.calculateSmartDelay(currentFen, uciMove, evalScore, engineThinkMs, activity, userWhite, hasMate, mateIn);
        } else {
            float fixed = StockfishSettings.getFixedDelaySeconds(context);
            if (fixed > 0) {
                calculatedDelay = Math.round(fixed * 1000.0f);
            }
        }
        final long preMoveDelayMs = calculatedDelay;

        isExecutingMove = true;
        lastAttemptedFen = currentFen;
        lastAttemptedUci = uciMove;
        lastMoveTimestamp = now;
        pendingMoveFen = currentFen;

        final Runnable moveExecutionTask = new Runnable() {
            @Override
            public void run() {
                executeMoveWithRetry(context, boardView, uciMove, flipped, currentFen, evalScore, engineThinkMs, userWhite, activity, fromSq, toSq, isPromo, promoChar, fittsDelay, 0);
            }
        };

        pendingMoveRunnable = moveExecutionTask;
        if (preMoveDelayMs > 0) {
            NnvcLogger.i(TAG, "⏳ [SMART DELAY SCHEDULER] Chờ " + preMoveDelayMs + "ms trước khi kích hoạt nước đi " + uciMove);
            postDelayedToMain(moveExecutionTask, preMoveDelayMs);
        } else {
            postToMain(moveExecutionTask);
        }
    }

    /**
     * Post-move validation: Checks after 350ms if board position advanced.
     * If position remained exactly unchanged and still our turn (e.g. user tapped or interrupted),
     * resets lastAttemptedFen to allow immediate retry rather than stalling indefinitely.
     */
    private static void schedulePostMoveValidation(final View boardView, final String fenBefore, final String moveUci) {
        postDelayedToMain(new Runnable() {
            @Override
            public void run() {
                try {
                    isExecutingMove = false;
                    pendingMoveRunnable = null;

                    String live = getCurrentBoardFen(boardView);
                    if (live == null) live = StockfishExtension.getCurrentBoardFen();
                    if (live != null && fenBefore != null) {
                        String[] liveParts = live.trim().split("\\s+");
                        String[] beforeParts = fenBefore.trim().split("\\s+");
                        if (liveParts.length >= 2 && beforeParts.length >= 2 &&
                            liveParts[0].equals(beforeParts[0]) && liveParts[1].equals(beforeParts[1])) {
                            NnvcLogger.w(TAG, "⚠️ [AUTO MOVE STUCK RECOVERY] Thế cờ chưa thay đổi sau nước đi " + moveUci + ". Xóa lastAttemptedFen để cho phép thử lại.");
                            lastAttemptedFen = null;
                            lastAttemptedUci = null;
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    isExecutingMove = false;
                    pendingMoveRunnable = null;
                }
            }
        }, 200);
    }

    public static boolean isExecuting() {
        return isExecutingMove || pendingMoveRunnable != null;
    }

    public static void reset() {
        cancelPendingAutoMove("reset");
        lastAttemptedFen = null;
        lastAttemptedUci = null;
        lastMoveTimestamp = 0;
    }
}