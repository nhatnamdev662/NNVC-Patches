package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

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
        final char promo = uciMove.length() >= 5 ? uciMove.charAt(4) : 0;

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

                    Log.i(TAG, "⚡ Auto Move executing: " + uciMove + " (" + fromSq + " -> " + toSq + ", flipped=" + flipped + ")");

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

                                                        // 3. Nếu là nước cờ phong cấp (Promotion), tự động tap chọn Hậu
                                                        if (promo != 0) {
                                                            postDelayedToMain(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    try {
                                                                        // Chạm tiếp vào ô đích để chọn quân Hậu mặc định
                                                                        long pDownTime = nowMs();
                                                                        MotionEvent pDown = MotionEvent.obtain(pDownTime, pDownTime, MotionEvent.ACTION_DOWN, toCoords[0], toCoords[1], 0);
                                                                        boardView.dispatchTouchEvent(pDown);
                                                                        pDown.recycle();
                                                                        MotionEvent pUp = MotionEvent.obtain(pDownTime, pDownTime + 20, MotionEvent.ACTION_UP, toCoords[0], toCoords[1], 0);
                                                                        boardView.dispatchTouchEvent(pUp);
                                                                        pUp.recycle();
                                                                    } catch (Throwable ignored) {
                                                                    } finally {
                                                                        isExecutingMove = false;
                                                                    }
                                                                }
                                                            }, 150);
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
