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
    private static volatile Method cachedTapMethod = null;

    /** Minimum cooldown between moves (ms) */
    private static final long COOLDOWN_MS = 500;

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
                try {
                    Field uField = bvClass.getDeclaredField("u");
                    uField.setAccessible(true);
                    deps = uField.get(boardView);
                } catch (Throwable ignored) {}
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
     * Retrieves the native accessibilityContract (implements com.chess.chessboard.accessibility.b)
     * which controls square selection and move execution directly on the board.
     * Searches by obfuscated field name 'o', named 'accessibilityContract', or by interface type.
     */
    private static Object getAccessibilityContract(View boardView) {
        if (boardView == null) return null;
        Class<?> c = boardView.getClass();
        while (c != null && c != Object.class) {
            // 1. Direct field lookup by obfuscated names
            for (String fName : new String[]{"o", "accessibilityContract"}) {
                try {
                    Field f = c.getDeclaredField(fName);
                    f.setAccessible(true);
                    Object val = f.get(boardView);
                    if (val != null) return val;
                } catch (NoSuchFieldException ignored) {
                } catch (Throwable t) {
                    NnvcLogger.w(TAG, "Error accessing field " + fName + ": " + t.getMessage());
                }
            }

            // 2. Scan all fields for implementation of com.chess.chessboard.accessibility.b or ChessBoardView$b
            try {
                for (Field f : c.getDeclaredFields()) {
                    try {
                        Class<?> fType = f.getType();
                        if (fType.getName().endsWith("$b") || fType.getName().contains("accessibility")) {
                            f.setAccessible(true);
                            Object val = f.get(boardView);
                            if (val != null) {
                                for (Class<?> iface : val.getClass().getInterfaces()) {
                                    if (iface.getName().contains("accessibility")) {
                                        return val;
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            c = c.getSuperclass();
        }
        return null;
    }

    /**
     * Direct Move Executor interface wrapping either:
     * 1. accessibilityContract.b(square)
     * 2. dependencies.k1().b(fE, f, action, squareSize, flipped)
     */
    public interface DirectMoveExecutor {
        boolean executeTap(Object square, String sqCoord, boolean flipped) throws Exception;
    }

    /**
     * Resolves the Direct Move Executor for the board view.
     * Zero fallback: Only uses internal native controller / model logic.
     */
    private static DirectMoveExecutor resolveDirectMoveExecutor(final View boardView, final Class<?> squareClass) {
        if (boardView == null || squareClass == null) return null;

        // 1. First choice: Native Accessibility Contract (ChessBoardView$b / com.chess.chessboard.accessibility.b)
        final Object contract = getAccessibilityContract(boardView);
        if (contract != null) {
            final Method tapMethod = getTapMethod(contract, squareClass);
            if (tapMethod != null) {
                NnvcLogger.d(TAG, "Using DirectMoveExecutor via AccessibilityContract: " + contract.getClass().getName());
                return new DirectMoveExecutor() {
                    @Override
                    public boolean executeTap(Object square, String sqCoord, boolean flipped) throws Exception {
                        tapMethod.invoke(contract, square);
                        return true;
                    }
                };
            }
        }

        // 2. Second choice: Direct MoveHandler via dependencies.k1()
        // Replicates exact bytecode of ChessBoardView$b.b(Square square)
        try {
            Class<?> bvClass = boardView.getClass();
            Object deps = null;
            try {
                Method getDepsMethod = bvClass.getMethod("getDependencies$cbview_release");
                getDepsMethod.setAccessible(true);
                deps = getDepsMethod.invoke(boardView);
            } catch (Throwable ignored) {}

            if (deps == null) {
                try {
                    Field uField = bvClass.getDeclaredField("u");
                    uField.setAccessible(true);
                    deps = uField.get(boardView);
                } catch (Throwable ignored) {}
            }

            if (deps != null) {
                final Method k1Method = deps.getClass().getMethod("k1");
                k1Method.setAccessible(true);
                final Object moveHandler = k1Method.invoke(deps);

                // Method cVarK1.b(float fE, float f, int action, float squareSize, boolean flipped)
                final Method moveActionMethod = moveHandler.getClass().getMethod("b", float.class, float.class, int.class, float.class, boolean.class);
                moveActionMethod.setAccessible(true);

                // Find square.e(flipped) and square.f(flipped)
                final Method eMethod = squareClass.getMethod("e", boolean.class);
                eMethod.setAccessible(true);
                final Method fMethod = squareClass.getMethod("f", boolean.class);
                fMethod.setAccessible(true);

                // Find drawDelegate or compute squareSize directly from boardView.getWidth() / 8.0f
                Method getDrawDelegateMethod = null;
                try {
                    getDrawDelegateMethod = bvClass.getMethod("getDrawDelegate");
                    getDrawDelegateMethod.setAccessible(true);
                } catch (Throwable ignored) {}

                final Method finalGetDrawDelegate = getDrawDelegateMethod;
                final Object finalDeps = deps;

                NnvcLogger.d(TAG, "Using DirectMoveExecutor via MoveHandler (dependencies.k1()): " + moveHandler.getClass().getName());
                return new DirectMoveExecutor() {
                    @Override
                    public boolean executeTap(Object square, String sqCoord, boolean flipped) throws Exception {
                        float squareSize = 0f;
                        if (finalGetDrawDelegate != null) {
                            try {
                                Object drawDelegate = finalGetDrawDelegate.invoke(boardView);
                                if (drawDelegate != null) {
                                    Method getSqSize = drawDelegate.getClass().getMethod("getSquareSize");
                                    getSqSize.setAccessible(true);
                                    squareSize = ((Number) getSqSize.invoke(drawDelegate)).floatValue();
                                }
                            } catch (Throwable ignored) {}
                        }
                        if (squareSize <= 0f) {
                            int w = boardView.getWidth();
                            squareSize = (w > 0) ? (w / 8.0f) : (boardView.getHeight() / 8.0f);
                        }

                        int fileIdx = ((Number) eMethod.invoke(square, flipped)).intValue();
                        int rankIdx = ((Number) fMethod.invoke(square, flipped)).intValue();
                        float fE = (fileIdx + 0.5f) * squareSize;
                        float f = (rankIdx + 0.5f) * squareSize;

                        // cVarK1.b(fE, f, 0, squareSize, flipped);
                        // cVarK1.b(fE, f, 1, squareSize, flipped);
                        moveActionMethod.invoke(moveHandler, fE, f, 0, squareSize, flipped);
                        moveActionMethod.invoke(moveHandler, fE, f, 1, squareSize, flipped);
                        return true;
                    }
                };
            }
        } catch (Throwable t) {
            NnvcLogger.e(TAG, "Failed to resolve DirectMoveExecutor via MoveHandler: " + t.getMessage(), t);
        }

        return null;
    }

    /**
     * Finds the controller tap method: void b(com.chess.chessboard.t square)
     */
    private static Method getTapMethod(Object contract, Class<?> squareClass) {
        if (contract == null || squareClass == null) return null;
        if (cachedTapMethod != null) return cachedTapMethod;

        try {
            Method m = contract.getClass().getMethod("b", squareClass);
            m.setAccessible(true);
            cachedTapMethod = m;
            return m;
        } catch (Throwable ignored) {}

        // Fallback search across declared methods for 1-arg matching squareClass
        for (Method m : contract.getClass().getMethods()) {
            Class<?>[] pTypes = m.getParameterTypes();
            if (pTypes.length == 1 && pTypes[0].isAssignableFrom(squareClass)) {
                m.setAccessible(true);
                cachedTapMethod = m;
                return m;
            }
        }
        return null;
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

    /**
     * Resolves the native CBViewModel instance controlling the chessboard.
     * Priority 1: Direct ChessBoardView.getViewModel()
     * Priority 2: Method scanning on ChessBoardView returning CBViewModel
     * Priority 3: RealGameViewModel.J().getValue()
     * Priority 4: StockfishExtension.getStateImpl() provider lookup
     */
    public static Object resolveCBViewModel(View boardView, Activity activity) {
        return resolveCBViewModel((Object) boardView, activity);
    }

    public static Object resolveCBViewModel(Object boardView, Activity activity) {
        if (boardView != null) {
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
        }

        Activity act = (activity != null) ? activity : StockfishExtension.getCurrentActivity();
        Object rvm = null;
        if (act != null) {
            rvm = AutoFindManager.getRealGameViewModel(act);
        }
        if (rvm == null) {
            rvm = AutoFindManager.getLastKnownViewModel();
        }
        if (rvm != null) {
            try {
                Method jMethod = rvm.getClass().getMethod("J");
                jMethod.setAccessible(true);
                Object flow = jMethod.invoke(rvm);
                if (flow != null) {
                    Method gv = flow.getClass().getMethod("getValue");
                    gv.setAccessible(true);
                    Object vm = gv.invoke(flow);
                    if (vm != null) return vm;
                }
            } catch (Throwable ignored) {}
        }

        try {
            Object state = StockfishExtension.getStateImpl();
            if (state != null) {
                for (Method m : state.getClass().getMethods()) {
                    if (m.getParameterTypes().length == 0 && m.getReturnType().getName().contains("CBViewModel")) {
                        m.setAccessible(true);
                        Object vm = m.invoke(state);
                        if (vm != null) return vm;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Constructs a native Move object (com.chess.chessboard.l).
     * Normal move: com.chess.chessboard.o(fromSquare, toSquare)
     * Queen promotion: com.chess.chessboard.p(fromSquare, toSquare, PieceKind.f)
     */
    public static Object createMoveObject(ClassLoader cl, Object fromSquare, Object toSquare, boolean isPromo) {
        return createMoveObject(cl, fromSquare, toSquare, isPromo, 'q');
    }

    /**
     * Constructs a native Move object with specific promotion piece kind (q, r, b, n).
     */
    public static Object createMoveObject(ClassLoader cl, Object fromSquare, Object toSquare, boolean isPromo, char promoPiece) {
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
                    for (Constructor<?> ctor : pClass.getConstructors()) {
                        Class<?>[] pts = ctor.getParameterTypes();
                        if (pts.length == 3 && pts[0].isAssignableFrom(squareClass) && pts[1].isAssignableFrom(squareClass) && pts[2].isAssignableFrom(pieceKindClass)) {
                            ctor.setAccessible(true);
                            return ctor.newInstance(fromSquare, toSquare, promoKind);
                        }
                    }
                }
            }

            Class<?> oClass = Class.forName("com.chess.chessboard.o", true, cl);
            for (Constructor<?> ctor : oClass.getConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length == 2 && pts[0].isAssignableFrom(squareClass) && pts[1].isAssignableFrom(squareClass)) {
                    ctor.setAccessible(true);
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
        try {
            Class<?> j0Class = Class.forName("com.chess.chessboard.vm.movesinput.j0", true, cl);
            Field aField = j0Class.getDeclaredField("a");
            aField.setAccessible(true);
            return aField.get(null);
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
        return executeDirectModelMove(cl, cbVm, fromSq, toSq, isPromo, 'q');
    }

    public static boolean executeDirectModelMove(ClassLoader cl, Object cbVm, String fromSq, String toSq, boolean isPromo, char promoPiece) {
        if (cl == null || cbVm == null) return false;
        try {
            Object fromSquare = getSquare(cl, fromSq);
            Object toSquare = getSquare(cl, toSq);
            if (fromSquare == null || toSquare == null) {
                NnvcLogger.w(TAG, "executeDirectModelMove: Square objects not resolved (" + fromSq + ", " + toSq + ")");
                return false;
            }

            Object moveObj = createMoveObject(cl, fromSquare, toSquare, isPromo, promoPiece);
            if (moveObj == null) {
                NnvcLogger.w(TAG, "executeDirectModelMove: Failed to construct Move object");
                return false;
            }

            Object moveVerification = getMoveVerification(cl);
            if (moveVerification == null) {
                NnvcLogger.w(TAG, "executeDirectModelMove: MoveVerification not found");
                return false;
            }

            Method pMethod = null;
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
            if (pMethod == null) {
                NnvcLogger.w(TAG, "executeDirectModelMove: Method p not found on " + cbVm.getClass().getName());
                return false;
            }

            pMethod.setAccessible(true);
            pMethod.invoke(cbVm, moveObj, moveVerification, false);
            NnvcLogger.i(TAG, "⚡ [DIRECT MODEL AUTO MOVE] Executed 0ms move directly via CBViewModel.p: " + fromSq + toSq + (isPromo ? promoPiece : ""));

            // Phát âm thanh nước đi của quân cờ chính hãng Chess.com 0ms
            try {
                Context ctx = StockfishExtension.getContext();
                if (ctx != null) {
                    boolean isCastle = ("e1".equals(fromSq) && ("g1".equals(toSq) || "c1".equals(toSq))) ||
                                       ("e8".equals(fromSq) && ("g8".equals(toSq) || "c8".equals(toSq)));
                    String currFen = StockfishExtension.getCurrentBoardFen();
                    boolean isCapture = false;
                    if (currFen != null && toSq != null && toSq.length() >= 2) {
                        char targetPiece = StockfishExtension.getPieceAtSquare(currFen, toSq);
                        if (targetPiece != '.') {
                            isCapture = true;
                        } else if (currFen.contains(" ")) {
                            String[] parts = currFen.split("\\s+");
                            if (parts.length >= 4 && toSq.equalsIgnoreCase(parts[3])) {
                                char fromPiece = StockfishExtension.getPieceAtSquare(currFen, fromSq);
                                if (Character.toLowerCase(fromPiece) == 'p') {
                                    isCapture = true;
                                }
                            }
                        }
                    }
                    SoundManager.getInstance(ctx).playMoveSound(isCapture, isCastle, isPromo);
                }
            } catch (Throwable st) {
                NnvcLogger.w(TAG, "Failed to play move sound: " + st.getMessage());
            }

            return true;
        } catch (Throwable t) {
            NnvcLogger.w(TAG, "executeDirectModelMove invoke error: " + t.getMessage());
            return false;
        }
    }

    public static boolean executeDirectModelMove(ClassLoader cl, View boardView, Activity activity, String fromSq, String toSq, boolean isPromo) {
        return executeDirectModelMove(cl, boardView, activity, fromSq, toSq, isPromo, 'q');
    }

    public static boolean executeDirectModelMove(ClassLoader cl, View boardView, Activity activity, String fromSq, String toSq, boolean isPromo, char promoPiece) {
        Object cbVm = resolveCBViewModel(boardView, activity);
        if (cbVm == null) return false;
        return executeDirectModelMove(cl, cbVm, fromSq, toSq, isPromo, promoPiece);
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
            // 1. Just-In-Time (JIT) check: verify board is still on target position and it is user's turn
            if (!isTurnAndPositionMatching(boardView, currentFen)) {
                NnvcLogger.i(TAG, "🛑 [AUTO MOVE JIT ABORT] Ván cờ đã đổi hoặc không còn là lượt người chơi. Hủy nước đi chống PREMOVE: " + uciMove);
                cancelPendingAutoMove("JIT mismatch (prevented PREMOVE)");
                return;
            }

            ClassLoader cl = boardView.getContext().getClassLoader();
            clearBoardSelection(boardView, flipped);

            // 2. Direct Model Hook (0ms execution, 100% Desktop Extension Parity)
            boolean directSuccess = executeDirectModelMove(cl, boardView, activity, fromSq, toSq, isPromo, promoChar);
            if (directSuccess) {
                if (isPromo && promoChar == 'q') {
                    applyPromotionQueen(boardView);
                }
                NnvcLogger.i(TAG, "⚡ [DIRECT MODEL AUTO MOVE] Nước đi đã được đẩy trực tiếp vào CBViewModel (0ms): " + uciMove);
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

            // 4. Graceful Fallback: Controller-based executor
            NnvcLogger.w(TAG, "⚠️ Direct Model Move không khả dụng, chuyển sang cơ chế Controller Executor...");
            final Object fromSquare = getSquare(cl, fromSq);
            final Object toSquare = getSquare(cl, toSq);
            if (fromSquare == null || toSquare == null) {
                NnvcLogger.e(TAG, "❌ Auto Move FAILED: Không thể tìm thấy Square objects (" + fromSq + ", " + toSq + ")");
                isExecutingMove = false;
                pendingMoveRunnable = null;
                return;
            }

            final DirectMoveExecutor moveExecutor = resolveDirectMoveExecutor(boardView, fromSquare.getClass());
            if (moveExecutor == null) {
                NnvcLogger.e(TAG, "❌ Auto Move FAILED: Không tìm thấy DirectMoveExecutor trên ChessBoardView.");
                isExecutingMove = false;
                pendingMoveRunnable = null;
                return;
            }

            if (isPromo && promoChar == 'q') {
                applyPromotionQueen(boardView);
            }

            NnvcLogger.i(TAG, "⚡ Controller Auto Move executing (Fallback): " + uciMove + " (" + fromSq + " -> " + toSq + ", delay=" + fittsDelay + "ms)");
            moveExecutor.executeTap(fromSquare, fromSq, flipped);
            NnvcLogger.d(TAG, "▶ [AUTO MOVE STEP 1] Đã chọn ô nguồn: " + fromSq);

            postDelayedToMain(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!isTurnAndPositionMatching(boardView, currentFen)) {
                            NnvcLogger.i(TAG, "🛑 [AUTO MOVE STEP 2 ABORT] Lượt đi thay đổi trước khi tap ô đích: " + toSq);
                            cancelPendingAutoMove("Turn changed before step 2");
                            return;
                        }

                        if (isPromo && promoChar == 'q') {
                            applyPromotionQueen(boardView);
                        }

                        moveExecutor.executeTap(toSquare, toSq, flipped);
                        NnvcLogger.d(TAG, "▶ [AUTO MOVE STEP 2] Đã chọn ô đích: " + toSq + " (hoàn tất nước đi)");

                        if (isPromo) {
                            postDelayedToMain(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        dismissOrResolvePromoDialog(boardView);
                                    } catch (Throwable ignored) {
                                    } finally {
                                        schedulePostMoveValidation(boardView, currentFen, uciMove);
                                    }
                                }
                            }, 120);
                        } else {
                            schedulePostMoveValidation(boardView, currentFen, uciMove);
                        }
                    } catch (Throwable t) {
                        NnvcLogger.e(TAG, "❌ Controller Auto Move toSquare execution failed: " + t.getMessage(), t);
                        isExecutingMove = false;
                        pendingMoveRunnable = null;
                        lastAttemptedFen = null;
                    }
                }
            }, fittsDelay);

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
        if (context == null || boardView == null || uciMove == null || uciMove.length() < 4) {
            NnvcLogger.w(TAG, "executeAutoMove aborted: invalid arguments (ctx=" + (context != null) + ", bv=" + (boardView != null) + ", move=" + uciMove + ")");
            return;
        }

        if (!StockfishSettings.isAutoMoveEnabled(context)) {
            NnvcLogger.w(TAG, "executeAutoMove aborted: Auto Move is DISABLED in settings (Tab LIVE).");
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
        if (now - lastMoveTimestamp < COOLDOWN_MS) {
            NnvcLogger.d(TAG, "Auto Move skipped: inside cooldown window (" + (now - lastMoveTimestamp) + "ms < " + COOLDOWN_MS + "ms).");
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
            calculatedDelay = SmartDelayManager.calculateSmartDelay(currentFen, uciMove, evalScore, engineThinkMs, activity, userWhite);
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
        }, 350);
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
