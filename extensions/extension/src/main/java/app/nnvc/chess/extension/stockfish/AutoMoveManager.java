package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * AutoMoveManager – In-Process Touch Injection & Chess Move Automation.
 * 
 * 100% Parity with NNVC Desktop Extension Auto-Move mechanism.
 * Automatically executes engine recommended moves directly on ChessBoardView
 * via sequential dispatchTouchEvent without requiring Accessibility services or Root.
 */
public class AutoMoveManager {

    private static final String TAG = "NNVC_AutoMove";
    private static volatile Handler mainHandler = null;
    private static volatile boolean isExecutingMove = false;
    private static volatile String lastAttemptedFen = null;
    private static volatile String lastAttemptedUci = null;
    private static volatile long lastMoveTimestamp = 0;

    /** Natural human mechanical touch intervals */
    private static final long TAP_HOLD_MS = 25;
    private static final long TAP_GAP_MS = 95;
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
     * Calculates the exact pixel center coordinates (X, Y) for a chess square (e.g., "e2", "e4").
     * Respects board orientation (flipped / unflipped).
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
     * This matches the native Chess.com auto-queen mechanism:
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
     * Checks if Auto Move is eligible and executes the move asynchronously on the Main UI Thread.
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

        isExecutingMove = true;
        lastAttemptedFen = currentFen;
        lastAttemptedUci = uciMove;
        lastMoveTimestamp = now;

        postToMain(new Runnable() {
            @Override
            public void run() {
                try {
                    float boardW = boardView.getWidth();
                    float boardH = boardView.getHeight();
                    if (boardW <= 0 || boardH <= 0) {
                        isExecutingMove = false;
                        return;
                    }

                    float[] fromCoords = getSquareCenter(fromSq, flipped, boardW, boardH);
                    float[] toCoords = getSquareCenter(toSq, flipped, boardW, boardH);
                    if (fromCoords == null || toCoords == null) {
                        isExecutingMove = false;
                        return;
                    }

                    // Nếu là nước cờ phong cấp (Promotion), tự động áp dụng cờ phong Hậu trực tiếp
                    if (isPromo) {
                        applyPromotionQueen(boardView);
                    }

                    Log.i(TAG, "⚡ Auto Move executing: " + uciMove + " (" + fromSq + " -> " + toSq + ", flipped=" + flipped + ", promo=" + isPromo + ")");

                    // 1. Tap ô nguồn (From Square)
                    long downTime1 = nowMs();
                    MotionEvent down1 = MotionEvent.obtain(downTime1, downTime1, MotionEvent.ACTION_DOWN, fromCoords[0], fromCoords[1], 0);
                    boardView.dispatchTouchEvent(down1);
                    down1.recycle();

                    postDelayedToMain(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                long upTime1 = nowMs();
                                MotionEvent up1 = MotionEvent.obtain(downTime1, upTime1, MotionEvent.ACTION_UP, fromCoords[0], fromCoords[1], 0);
                                boardView.dispatchTouchEvent(up1);
                                up1.recycle();

                                // 2. Delay cơ học tự nhiên trước khi tap ô đích (To Square)
                                postDelayedToMain(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            long downTime2 = nowMs();
                                            MotionEvent down2 = MotionEvent.obtain(downTime2, downTime2, MotionEvent.ACTION_DOWN, toCoords[0], toCoords[1], 0);
                                            boardView.dispatchTouchEvent(down2);
                                            down2.recycle();

                                            postDelayedToMain(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        long upTime2 = nowMs();
                                                        MotionEvent up2 = MotionEvent.obtain(downTime2, upTime2, MotionEvent.ACTION_UP, toCoords[0], toCoords[1], 0);
                                                        boardView.dispatchTouchEvent(up2);
                                                        up2.recycle();

                                                        // 3. Nếu là nước cờ phong cấp, kiểm tra xử lý dialog dự phòng
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
                                                        Log.e(TAG, "Error finishing toSquare tap: " + t.getMessage(), t);
                                                        isExecutingMove = false;
                                                    }
                                                }
                                            }, TAP_HOLD_MS);

                                        } catch (Throwable t) {
                                            Log.e(TAG, "Error executing toSquare tap: " + t.getMessage(), t);
                                            isExecutingMove = false;
                                        }
                                    }
                                }, TAP_GAP_MS);

                            } catch (Throwable t) {
                                Log.e(TAG, "Error finishing fromSquare tap: " + t.getMessage(), t);
                                isExecutingMove = false;
                            }
                        }
                    }, TAP_HOLD_MS);

                } catch (Throwable t) {
                    Log.e(TAG, "Failed to dispatch Auto Move touch events: " + t.getMessage(), t);
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
