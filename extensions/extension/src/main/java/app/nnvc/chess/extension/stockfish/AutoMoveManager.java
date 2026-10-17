package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import java.lang.ref.WeakReference;
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

    /** Cached 64 static Square instances from com.chess.chessboard.w.b */
    private static volatile List<?> cachedSquaresList = null;
    private static volatile Method cachedTapMethod = null;

    /** Minimum cooldown between moves (ms) */
    private static final long COOLDOWN_MS = 500;

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
                        Log.i(TAG, "Initialized cachedSquaresList: " + (cachedSquaresList != null ? cachedSquaresList.size() : "null"));
                    }
                }
            }
            if (cachedSquaresList != null && idx < cachedSquaresList.size()) {
                return cachedSquaresList.get(idx);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to resolve Square object for " + sq + ": " + t.getMessage(), t);
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
     * Determines whether a UCI move represents a pawn promotion (e.g., e7e8q, c7c8, etc.)
     */
    public static boolean isPromotion(String uciMove) {
        if (uciMove == null || uciMove.length() < 4) return false;
        if (uciMove.length() >= 5) {
            char p = Character.toLowerCase(uciMove.charAt(4));
            if (p == 'q' || p == 'r' || p == 'b' || p == 'n') return true;
        }
        char fromRank = uciMove.charAt(1);
        char toRank = uciMove.charAt(3);
        return (fromRank == '7' && toRank == '8') || (fromRank == '2' && toRank == '1');
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
                    Log.i(TAG, "👑 Set PromotionTargets to QUEEN on ChessBoardView");
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "applyPromotionQueen failed: " + t.getMessage());
        }
        return false;
    }

    /**
     * Retrieves the native accessibilityContract (implements com.chess.chessboard.accessibility.b)
     * which controls square selection and move execution directly on the board.
     */
    private static Object getAccessibilityContract(View boardView) {
        if (boardView == null) return null;
        Class<?> c = boardView.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField("accessibilityContract");
                f.setAccessible(true);
                return f.get(boardView);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                Log.e(TAG, "Error getting accessibilityContract field: " + t.getMessage(), t);
                return null;
            }
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
                    Log.d(TAG, "Found active promoDialog: " + promoDialog);
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
            Log.w(TAG, "dismissOrResolvePromoDialog error: " + t.getMessage());
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
        if (context == null || boardView == null || uciMove == null || uciMove.length() < 4) {
            return;
        }

        if (!StockfishSettings.isAutoMoveEnabled(context)) {
            return;
        }

        if (isExecutingMove) {
            Log.d(TAG, "Auto Move skipped: execution already in progress.");
            return;
        }

        long now = nowMs();
        if (now - lastMoveTimestamp < COOLDOWN_MS) {
            Log.d(TAG, "Auto Move skipped: inside cooldown window.");
            return;
        }

        if (currentFen != null && currentFen.equals(lastAttemptedFen) && uciMove.equals(lastAttemptedUci)) {
            Log.d(TAG, "Auto Move skipped: already attempted on this position (" + uciMove + ").");
            return;
        }

        final String fromSq = uciMove.substring(0, 2);
        final String toSq = uciMove.substring(2, 4);
        final boolean isPromo = isPromotion(uciMove);
        final long fittsDelay = calculateFittsDelay(fromSq, toSq);

        isExecutingMove = true;
        lastAttemptedFen = currentFen;
        lastAttemptedUci = uciMove;
        lastMoveTimestamp = now;

        postToMain(new Runnable() {
            @Override
            public void run() {
                try {
                    ClassLoader cl = boardView.getContext().getClassLoader();
                    final Object fromSquare = getSquare(cl, fromSq);
                    final Object toSquare = getSquare(cl, toSq);

                    if (fromSquare == null || toSquare == null) {
                        Log.e(TAG, "❌ Direct Auto Move FAILED: Unable to resolve Square objects (" + fromSq + "=" + fromSquare + ", " + toSq + "=" + toSquare + "). NO FALLBACK.");
                        isExecutingMove = false;
                        return;
                    }

                    final Object contract = getAccessibilityContract(boardView);
                    if (contract == null) {
                        Log.e(TAG, "❌ Direct Auto Move FAILED: accessibilityContract not found on ChessBoardView. NO FALLBACK.");
                        isExecutingMove = false;
                        return;
                    }

                    final Method tapMethod = getTapMethod(contract, fromSquare.getClass());
                    if (tapMethod == null) {
                        Log.e(TAG, "❌ Direct Auto Move FAILED: Tap method 'b(Square)' not found on controller. NO FALLBACK.");
                        isExecutingMove = false;
                        return;
                    }

                    // Pre-apply Auto-Queen promotion targets
                    if (isPromo) {
                        applyPromotionQueen(boardView);
                    }

                    Log.i(TAG, "⚡ Direct Auto Move executing (Extension Style): " + uciMove + " (" + fromSq + " -> " + toSq + ", delay=" + fittsDelay + "ms, promo=" + isPromo + ")");

                    // Step 1: Select source square
                    tapMethod.invoke(contract, fromSquare);

                    // Step 2: Delay matching Fitts's Law before selecting destination square
                    postDelayedToMain(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (isPromo) {
                                    applyPromotionQueen(boardView);
                                }

                                // Step 3: Complete move to target square
                                tapMethod.invoke(contract, toSquare);

                                if (isPromo) {
                                    postDelayedToMain(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                dismissOrResolvePromoDialog(boardView);
                                            } catch (Throwable ignored) {
                                            } finally {
                                                isExecutingMove = false;
                                            }
                                        }
                                    }, 120);
                                } else {
                                    isExecutingMove = false;
                                }
                            } catch (Throwable t) {
                                Log.e(TAG, "❌ Direct Auto Move toSquare execution failed: " + t.getMessage(), t);
                                isExecutingMove = false;
                            }
                        }
                    }, fittsDelay);

                } catch (Throwable t) {
                    Log.e(TAG, "❌ Direct Auto Move fromSquare execution failed: " + t.getMessage(), t);
                    isExecutingMove = false;
                }
            }
        });
    }

    public static boolean isExecuting() {
        return isExecutingMove;
    }

    public static void reset() {
        isExecutingMove = false;
        lastAttemptedFen = null;
        lastAttemptedUci = null;
        lastMoveTimestamp = 0;
    }
}
