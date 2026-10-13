/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.stockfish;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class OverlayManager {
    private static final String TAG = "OverlayManager";

    public static class BoardMetrics {
        public final int boardX;
        public final int boardY;
        public final int boardW;
        public final int boardH;
        public final View boardView;

        public BoardMetrics(int boardX, int boardY, int boardW, int boardH, View boardView) {
            this.boardX = boardX;
            this.boardY = boardY;
            this.boardW = boardW;
            this.boardH = boardH;
            this.boardView = boardView;
        }
    }

    public static BoardMetrics getBoardMetrics(ViewGroup decorView) {
        if (decorView == null) return null;
        View boardView = findChessBoardView(decorView);
        if (boardView == null) return null;

        // Ensure board has no lingering translation or scaling from previous versions
        if (boardView.getTranslationX() != 0f) boardView.setTranslationX(0f);
        if (boardView.getTranslationY() != 0f) boardView.setTranslationY(0f);
        if (boardView.getScaleX() != 1.0f) boardView.setScaleX(1.0f);
        if (boardView.getScaleY() != 1.0f) boardView.setScaleY(1.0f);

        int[] boardLoc = new int[2];
        boardView.getLocationInWindow(boardLoc);
        int[] decorLoc = new int[2];
        decorView.getLocationInWindow(decorLoc);
        int rawX = (boardLoc[0] - decorLoc[0]) - decorView.getPaddingLeft();
        int rawY = (boardLoc[1] - decorLoc[1]) - decorView.getPaddingTop();
        int rawW = boardView.getWidth();
        int rawH = boardView.getHeight();
        if (rawW <= 0 || rawH <= 0) return null;

        // Ensure exact 1:1 square chessboard geometry
        int squareSize = Math.min(rawW, rawH);
        if (rawH > squareSize) {
            rawY += (rawH - squareSize) / 2;
            rawH = squareSize;
        } else if (rawW > squareSize) {
            rawX += (rawW - squareSize) / 2;
            rawW = squareSize;
        }

        return new BoardMetrics(rawX, rawY, rawW, rawH, boardView);
    }

    public static void ensureZOrder(ViewGroup decorView) {
        if (decorView == null) return;
        try {
            View arrowView = decorView.findViewWithTag("nnvc_arrow_overlay");
            if (arrowView != null) {
                arrowView.setElevation(2f);
            }
            View evalBar = decorView.findViewWithTag("stockfish_eval_bar");
            if (evalBar != null && evalBar.getVisibility() == View.VISIBLE) {
                evalBar.setElevation(10f);
                evalBar.bringToFront();
            }
            View info = decorView.findViewWithTag("stockfish_engine_info");
            if (info != null && info.getVisibility() == View.VISIBLE) {
                info.setElevation(10f);
                info.bringToFront();
            }
            View widget = decorView.findViewWithTag("nnvc_accuracy_elo_widget");
            if (widget != null && widget.getVisibility() == View.VISIBLE) {
                widget.setElevation(25f);
                widget.bringToFront();
            }
            View topPill = decorView.findViewWithTag("nnvc_accuracy_top_pill");
            if (topPill != null && topPill.getVisibility() == View.VISIBLE) {
                topPill.setElevation(15f);
                topPill.bringToFront();
            }
            View botPill = decorView.findViewWithTag("nnvc_accuracy_bot_pill");
            if (botPill != null && botPill.getVisibility() == View.VISIBLE) {
                botPill.setElevation(15f);
                botPill.bringToFront();
            }
            View logPill = decorView.findViewWithTag(FloatingLogPillView.VIEW_TAG);
            if (logPill != null && logPill.getVisibility() == View.VISIBLE) {
                logPill.setElevation(40f);
                logPill.bringToFront();
            }
        } catch (Throwable ignored) {}
    }

    public static void updateArrowOverlay(final List<String> moves, final Object stateImpl) {
        updateArrowOverlay(moves, null, null, null, null, true, stateImpl);
    }

    public static void updateArrowOverlay(final List<String> moves,
                                          final float[] lineScores,
                                          final boolean hasMate,
                                          final int mateIn,
                                          final boolean whiteToMove,
                                          final Object stateImpl) {
        boolean[] lineMates = new boolean[lineScores != null ? lineScores.length : 0];
        int[] lineMateIn = new int[lineScores != null ? lineScores.length : 0];
        if (hasMate && lineMates.length > 0) {
            lineMates[0] = true;
            lineMateIn[0] = mateIn;
        }
        updateArrowOverlay(moves, null, lineScores, lineMates, lineMateIn, whiteToMove, stateImpl);
    }

    public static void updateArrowOverlay(final List<String> moves,
                                          final String threatMove,
                                          final float[] lineScores,
                                          final boolean hasMate,
                                          final int mateIn,
                                          final boolean whiteToMove,
                                          final Object stateImpl) {
        updateArrowOverlay(moves, threatMove, null, lineScores, hasMate, mateIn, whiteToMove, stateImpl);
    }

    public static void updateArrowOverlay(final List<String> moves,
                                          final String threatMove,
                                          final String premoveMove,
                                          final float[] lineScores,
                                          final boolean hasMate,
                                          final int mateIn,
                                          final boolean whiteToMove,
                                          final Object stateImpl) {
        boolean[] lineMates = new boolean[lineScores != null ? lineScores.length : 0];
        int[] lineMateIn = new int[lineScores != null ? lineScores.length : 0];
        if (hasMate && lineMates.length > 0) {
            lineMates[0] = true;
            lineMateIn[0] = mateIn;
        }
        updateArrowOverlay(moves, threatMove, premoveMove, lineScores, lineMates, lineMateIn, whiteToMove, stateImpl);
    }

    public static void updateArrowOverlay(final List<String> moves,
                                          final String threatMove,
                                          final float[] lineScores,
                                          final boolean[] lineMates,
                                          final int[] lineMateIn,
                                          final boolean whiteToMove,
                                          final Object stateImpl) {
        updateArrowOverlay(moves, threatMove, null, lineScores, lineMates, lineMateIn, whiteToMove, stateImpl);
    }

    public static void updateArrowOverlay(final List<String> moves,
                                          final String threatMove,
                                          final String premoveMove,
                                          final float[] lineScores,
                                          final boolean[] lineMates,
                                          final int[] lineMateIn,
                                          final boolean whiteToMove,
                                          final Object stateImpl) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null || StockfishExtension.isReviewOrAnalysisActivity(activity) || StockfishExtension.isGameOver(activity)) {
                        hideArrowOverlay(true);
                        return;
                    }
                    Window window = activity.getWindow();
                    if (window == null) return;
                    ViewGroup decorView = (ViewGroup) window.getDecorView();
                    if (decorView == null) return;

                    FloatingLogPillView.ensureAttached(decorView);

                    final BoardMetrics bm = getBoardMetrics(decorView);
                    if (bm == null) {
                        StockfishExtension.resetArrowSignature();
                        final View bv = findChessBoardView(decorView);
                        if (bv != null) {
                            bv.post(new Runnable() {
                                @Override
                                public void run() {
                                    updateArrowOverlay(moves, threatMove, premoveMove, lineScores, lineMates, lineMateIn, whiteToMove, stateImpl);
                                }
                            });
                        }
                        return;
                    }

                    int boardX = bm.boardX;
                    int boardY = bm.boardY;
                    int boardW = bm.boardW;
                    int boardH = bm.boardH;
                    View boardView = bm.boardView;

                    View overlay = decorView.findViewWithTag("nnvc_arrow_overlay");
                    ArrowOverlayView arrowView;
                    if (overlay instanceof ArrowOverlayView) {
                        arrowView = (ArrowOverlayView) overlay;
                    } else {
                        if (overlay != null) decorView.removeView(overlay);
                        arrowView = new ArrowOverlayView(decorView.getContext());
                        arrowView.setTag("nnvc_arrow_overlay");
                        decorView.addView(arrowView);
                    }

                    List<ArrowOverlayView.ArrowData> list = new ArrayList<>();
                    // 1. Mũi tên Đe dọa của đối thủ (màu đỏ nguy hiểm)
                    if (threatMove != null && threatMove.matches("^[a-h][1-8][a-h][1-8][qrbn]?$")) {
                        list.add(new ArrowOverlayView.ArrowData(threatMove, 1, true, false, null));
                    }
                    // 2. Mũi tên Premove của người chơi (màu cam hổ phách neon)
                    if (premoveMove != null && premoveMove.matches("^[a-h][1-8][a-h][1-8][qrbn]?$")) {
                        list.add(new ArrowOverlayView.ArrowData(premoveMove, 1, false, true, null));
                    }
                    if (moves != null) {
                        for (int i = 0; i < moves.size(); i++) {
                            String m = moves.get(i);
                            if (m != null && m.matches("^[a-h][1-8][a-h][1-8][qrbn]?$")) {
                                String evalText = null;
                                boolean branchHasMate = (lineMates != null && i < lineMates.length && lineMates[i]);
                                int branchMateIn = (lineMateIn != null && i < lineMateIn.length) ? lineMateIn[i] : 0;

                                if (branchHasMate && branchMateIn != 0) {
                                    evalText = formatArrowEvalLabel(true, branchMateIn, 0f);
                                } else if (lineScores != null && i < lineScores.length) {
                                    evalText = formatArrowEvalLabel(false, 0, lineScores[i]);
                                }
                                list.add(new ArrowOverlayView.ArrowData(m, i + 1, false, false, evalText));
                            }
                        }
                    }

                    if (list.isEmpty()) {
                        arrowView.clearArrowsOnly();
                        if (!arrowView.hasBadges()) {
                            arrowView.setVisibility(View.GONE);
                        }
                        return;
                    }

                    boolean flipped = isBoardFlipped(boardView, stateImpl);
                    arrowView.update(boardX, boardY, boardW, boardH, list, flipped);
                    arrowView.setVisibility(View.VISIBLE);
                    ensureZOrder(decorView);

                } catch (Throwable t) {
                    Log.e(TAG, "updateArrowOverlay failed: " + t.getMessage(), t);
                }
            }
        });
    }

    public static boolean hasArrows() {
        Activity activity = StockfishExtension.getCurrentActivity();
        if (activity == null || activity.getWindow() == null) return false;
        View decor = activity.getWindow().getDecorView();
        if (decor == null) return false;
        View overlay = decor.findViewWithTag("nnvc_arrow_overlay");
        if (overlay instanceof ArrowOverlayView) {
            ArrowOverlayView aov = (ArrowOverlayView) overlay;
            return aov.getVisibility() == View.VISIBLE && aov.hasArrows();
        }
        return false;
    }

    public static void hideArrowOverlay() {
        hideArrowOverlay(false);
    }

    public static void hideArrowOverlay(final boolean clearBadges) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    View decorView = window.getDecorView();
                    if (decorView == null) return;
                    View v = decorView.findViewWithTag("nnvc_arrow_overlay");
                    if (v instanceof ArrowOverlayView) {
                        ArrowOverlayView aov = (ArrowOverlayView) v;
                        aov.clearArrowsOnly();
                        if (clearBadges || StockfishExtension.isGameOver(activity)) {
                            aov.clearClassificationBadge();
                            aov.setVisibility(View.GONE);
                        } else if (!aov.hasBadges()) {
                            aov.setVisibility(View.GONE);
                        }
                    } else if (v != null) {
                        v.setVisibility(View.GONE);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "hideArrowOverlay failed: " + t.getMessage());
                }
            }
        });
    }

    public static void invalidateArrowOverlay() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    View decorView = window.getDecorView();
                    if (decorView == null) return;
                    View v = decorView.findViewWithTag("nnvc_arrow_overlay");
                    if (v instanceof ArrowOverlayView) {
                        v.invalidate();
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    public static void updateEvalBar(final float score, final boolean hasMate, final int mateIn, final Object stateImpl) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null || StockfishExtension.isReviewOrAnalysisActivity(activity)) {
                        hideEvalBar();
                        return;
                    }
                    Window window = activity.getWindow();
                    if (window == null) return;
                    ViewGroup decorView = (ViewGroup) window.getDecorView();
                    if (decorView == null) return;

                    final BoardMetrics bm = getBoardMetrics(decorView);
                    if (bm == null) {
                        final View bv = findChessBoardView(decorView);
                        if (bv != null) {
                            bv.post(new Runnable() {
                                @Override
                                public void run() {
                                    updateEvalBar(score, hasMate, mateIn, stateImpl);
                                }
                            });
                        }
                        return;
                    }

                    int boardX = bm.boardX;
                    int boardY = bm.boardY;
                    int boardW = bm.boardW;
                    int boardH = bm.boardH;
                    View boardView = bm.boardView;

                    float density = decorView.getContext().getResources().getDisplayMetrics().density;
                    int barWidth = (int) (12 * density);

                    int evalBarX;
                    if (boardX >= barWidth) {
                        evalBarX = boardX - barWidth;
                    } else {
                        evalBarX = Math.max(0, boardX);
                    }

                    boolean flipped = isBoardFlipped(boardView, stateImpl);

                    View evalBar = decorView.findViewWithTag("stockfish_eval_bar");
                    EvalBarView evalBarView;
                    if (evalBar instanceof EvalBarView) {
                        evalBarView = (EvalBarView) evalBar;
                    } else {
                        if (evalBar != null) decorView.removeView(evalBar);
                        evalBarView = new EvalBarView(decorView.getContext());
                        evalBarView.setTag("stockfish_eval_bar");
                        decorView.addView(evalBarView);
                    }

                    evalBarView.update(evalBarX, boardY, barWidth, boardH,
                                       score, hasMate, mateIn, flipped);
                    evalBarView.setVisibility(View.VISIBLE);
                    ensureZOrder(decorView);
                } catch (Throwable t) {
                    Log.e(TAG, "updateEvalBar failed: " + t.getMessage(), t);
                }
            }
        });
    }

    public static void resetEvalBar() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    ViewGroup decorView = (ViewGroup) window.getDecorView();
                    if (decorView == null) return;

                    View evalBar = decorView.findViewWithTag("stockfish_eval_bar");
                    if (evalBar instanceof EvalBarView) {
                        ((EvalBarView) evalBar).reset();
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "resetEvalBar failed: " + t.getMessage());
                }
            }
        });
    }

    public static void hideEvalBar() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    ViewGroup decorView = (ViewGroup) window.getDecorView();
                    if (decorView == null) return;
                    
                    View evalBar = decorView.findViewWithTag("stockfish_eval_bar");
                    if (evalBar instanceof EvalBarView) {
                        ((EvalBarView) evalBar).resetStabilizer();
                        evalBar.setVisibility(View.GONE);
                    } else if (evalBar != null) {
                        evalBar.setVisibility(View.GONE);
                    }

                    // Khôi phục vị trí và tỷ lệ gốc của bàn cờ
                    View boardView = findChessBoardView(decorView);
                    if (boardView != null) {
                        boardView.setTranslationX(0f);
                        boardView.setTranslationY(0f);
                        boardView.setScaleX(1.0f);
                        boardView.setScaleY(1.0f);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "hideEvalBar failed: " + t.getMessage());
                }
            }
        });
    }

    /** Width reserved for the engine info line. */
    private static final int INFO_SLOT_DP = 110;

    /**
     * Shows "depth · score" in the slot above the board's top-right corner,
     * e.g. "d22 · +0.35" or "d18 · M3".
     */
    public static void updateEngineInfo(final int depth, final float score, final boolean hasMate, final int mateIn) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null || activity.getWindow() == null) return;
                    ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                    BoardMetrics bm = getBoardMetrics(decorView);
                    if (bm == null) return;
                    int boardX = bm.boardX;
                    int boardY = bm.boardY;
                    int boardW = bm.boardW;
                    float density = decorView.getContext().getResources().getDisplayMetrics().density;
                    int h = (int) (14 * density);
                    int w = (int) (INFO_SLOT_DP * density);
                    int y = boardY - h - (int) (4 * density);
                    if (y < 0) return;

                    View existing = decorView.findViewWithTag("stockfish_engine_info");
                    TextView info;
                    if (existing instanceof TextView) {
                        info = (TextView) existing;
                    } else {
                        if (existing != null) decorView.removeView(existing);
                        info = new TextView(decorView.getContext());
                        info.setTag("stockfish_engine_info");
                        GradientDrawable bg = new GradientDrawable();
                        bg.setColor(0xCC1B1A18);
                        bg.setCornerRadius(h / 3f);
                        info.setBackground(bg);
                        info.setTextColor(0xFFE3E3E3);
                        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
                        info.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
                        info.setGravity(Gravity.CENTER);
                        info.setIncludeFontPadding(false);
                        info.setSingleLine(true);
                        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(w, h);
                        flp.gravity = Gravity.TOP | Gravity.START;
                        info.setLayoutParams(flp);
                        decorView.addView(info);
                    }
                    ViewGroup.LayoutParams lp = info.getLayoutParams();
                    if (lp.width != w || lp.height != h) {
                        lp.width = w;
                        lp.height = h;
                        info.setLayoutParams(lp);
                    }
                    info.setText(formatEngineInfo(depth, score, hasMate, mateIn));
                    info.setTranslationX(boardX + boardW - w);
                    info.setTranslationY(y);
                    info.setVisibility(View.VISIBLE);
                    ensureZOrder(decorView);
                } catch (Throwable t) {
                    Log.e(TAG, "updateEngineInfo failed: " + t.getMessage());
                }
            }
        });
    }

    /** "d22 · +0.35", "d18 · M3" / "d18 · -M2" (white's point of view). */
    static String formatEngineInfo(int depth, float score, boolean hasMate, int mateIn) {
        String eval;
        if (hasMate && mateIn != 0) {
            eval = (mateIn > 0 ? "M" : "-M") + Math.abs(mateIn);
        } else {
            eval = String.format(java.util.Locale.US, "%+.2f", score);
        }
        return (depth > 0 ? "d" + depth + " \u00B7 " : "") + eval;
    }

    /**
     * NNVC Extension standard formatting (_formatArrowEvalLabel, nnvc_main_bundle.js lines 13303-13308).
     * Formats evaluation from side-to-move POV (#mate or +cp / -cp).
     */
    public static String formatArrowEvalLabel(boolean branchHasMate, int branchMateIn, float sideToMovePawns) {
        if (branchHasMate && branchMateIn != 0) {
            return branchMateIn > 0 ? ("#" + branchMateIn) : ("#-" + Math.abs(branchMateIn));
        }
        float rounded = Math.round(sideToMovePawns * 10f) / 10f;
        String fixed = Math.abs(rounded) >= 10f
                ? String.format(java.util.Locale.US, "%.0f", (float) Math.abs(rounded))
                : String.format(java.util.Locale.US, "%.1f", (float) Math.abs(rounded));
        return (rounded > 0 ? "+" : (rounded < 0 ? "-" : "")) + fixed;
    }

    public static void hideEngineInfo() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null || activity.getWindow() == null) return;
                    View v = activity.getWindow().getDecorView().findViewWithTag("stockfish_engine_info");
                    if (v != null) v.setVisibility(View.GONE);
                } catch (Throwable t) {
                    Log.e(TAG, "hideEngineInfo failed: " + t.getMessage());
                }
            }
        });
    }

    public static void showMateAnnouncement(final int mateIn) {
        // Vô hiệu hóa vĩnh viễn theo yêu cầu người dùng
        hideMateAnnouncement();
    }

    public static void hideMateAnnouncement() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    View decorView = window.getDecorView();
                    if (decorView == null) return;
                    View v = decorView.findViewWithTag("stockfish_mate_banner");
                    if (v != null) v.setVisibility(View.GONE);
                } catch (Throwable t) {
                    Log.e(TAG, "hideMateAnnouncement failed: " + t.getMessage());
                }
            }
        });
    }

    private static final java.util.Set<Integer> hookedBoardViews = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public static void clearHookedBoardViews() {
        hookedBoardViews.clear();
    }

    public static View findChessBoardView(View root) {
        if (root == null) return null;
        // 1. First priority: Find the actual, inner ChessBoardView (NOT a Layout or Container)
        View board = findInnerChessBoardView(root);
        if (board == null) {
            // 2. Second priority: Any view with "BoardView" in class name (excluding Layouts)
            board = findFallbackBoardView(root);
        }
        if (board == null) {
            // 3. Third priority: Last resort fallback (any layout matching ChessBoard)
            board = findAnyBoardLayout(root);
        }
        if (board != null && hookedBoardViews.add(System.identityHashCode(board))) {
            board.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Activity act = StockfishExtension.getCurrentActivity();
                            if (act != null) StockfishExtension.tryLoadMove0(act);
                        }
                    });
                }
                @Override
                public void onViewDetachedFromWindow(View v) {}
            });
            board.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                        invalidateArrowOverlay();
                        if (!hasArrows() && !StockfishExtension.isMove0AnalysisCompleted()) {
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    Activity act = StockfishExtension.getCurrentActivity();
                                    if (act != null && !StockfishExtension.isGameOver(act) && !StockfishExtension.isReviewOrAnalysisActivity(act)) {
                                        StockfishExtension.tryLoadMove0(act);
                                    }
                                }
                            });
                        }
                    }
                }
            });
        }
        return board;
    }

    private static volatile boolean lastKnownFlipped = false;

    private static View findInnerChessBoardView(View view) {
        if (view == null) return null;

        String name = view.getClass().getName();
        boolean isLayoutOrContainer = name.contains("Layout")
                || name.contains("Container")
                || name.contains("Binding")
                || name.contains("Manager");

        // If this view itself is already the exact ChessBoardView, return it immediately
        if (!isLayoutOrContainer) {
            if (name.equals("com.chess.chessboard.view.ChessBoardView")
                    || name.equals("com.chess.chessboard.v2.ChessBoardView")
                    || name.endsWith(".ChessBoardView")
                    || name.endsWith("$ChessBoardView")
                    || name.endsWith("ChessBoardView")) {
                return view;
            }
        }

        // Depth-first search: check children
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findInnerChessBoardView(group.getChildAt(i));
                if (found != null) return found;
            }
        }

        return null;
    }

    private static View findFallbackBoardView(View view) {
        if (view == null) return null;

        String name = view.getClass().getName();
        boolean isLayoutOrContainer = name.contains("Layout")
                || name.contains("Container")
                || name.contains("Binding")
                || name.contains("Manager");

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findFallbackBoardView(group.getChildAt(i));
                if (found != null) return found;
            }
        }

        if (!isLayoutOrContainer) {
            if (name.contains("ChessBoard") || name.contains("BoardView")) {
                return view;
            }
        }

        return null;
    }

    private static View findAnyBoardLayout(View view) {
        if (view == null) return null;
        String name = view.getClass().getName();
        if (name.contains("ChessBoardLayout") || name.endsWith("ChessBoardView")) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findAnyBoardLayout(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    public static boolean isBoardFlipped(Object stateImpl) {
        Activity act = StockfishExtension.getCurrentActivity();
        View bv = null;
        if (act != null && act.getWindow() != null && act.getWindow().getDecorView() != null) {
            bv = findChessBoardView(act.getWindow().getDecorView());
        }
        return isBoardFlipped(bv, stateImpl);
    }

    public static boolean isBoardFlipped(View boardView, Object stateImpl) {
        if (boardView != null) {
            try {
                for (Method m : boardView.getClass().getMethods()) {
                    String n = m.getName();
                    if ((n.equals("getFlipBoard") || n.equals("isFlipped") || n.equals("getFlipped"))
                            && m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                        boolean res = (boolean) m.invoke(boardView);
                        lastKnownFlipped = res;
                        return res;
                    }
                }
                for (Field f : boardView.getClass().getDeclaredFields()) {
                    String n = f.getName();
                    if ((n.equals("flipBoard") || n.equals("flipped") || n.equals("isFlipped") || n.equals("t"))
                            && f.getType() == boolean.class) {
                        f.setAccessible(true);
                        boolean res = f.getBoolean(boardView);
                        lastKnownFlipped = res;
                        return res;
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (stateImpl != null) {
            try {
                for (Method m : stateImpl.getClass().getMethods()) {
                    String n = m.getName();
                    if ((n.equals("getFlipBoard") || n.equals("isFlipped") || n.equals("getFlipped"))
                            && m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                        boolean res = (boolean) m.invoke(stateImpl);
                        lastKnownFlipped = res;
                        return res;
                    }
                }
                for (Field f : stateImpl.getClass().getDeclaredFields()) {
                    String n = f.getName();
                    if ((n.equals("flipped") || n.equals("isFlipped")) && f.getType() == boolean.class) {
                        f.setAccessible(true);
                        boolean res = f.getBoolean(stateImpl);
                        lastKnownFlipped = res;
                        return res;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return lastKnownFlipped;
    }

    public static void setClassificationBadge(final String square, final String classificationName, final boolean isWhite, final boolean isMyMove) {
        setClassificationBadge(null, square, classificationName, isWhite, isMyMove);
    }

    public static void setClassificationBadge(final String fromSquare, final String toSquare, final String classificationName, final boolean isWhite, final boolean isMyMove) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null || StockfishExtension.isReviewOrAnalysisActivity(activity)) {
                        clearClassificationBadge();
                        return;
                    }
                    Window window = activity.getWindow();
                    if (window == null) return;
                    ViewGroup decorView = (ViewGroup) window.getDecorView();
                    if (decorView == null) return;

                    final BoardMetrics bm = getBoardMetrics(decorView);
                    if (bm == null) {
                        final View bv = findChessBoardView(decorView);
                        if (bv != null) {
                            bv.post(new Runnable() {
                                @Override
                                public void run() {
                                    setClassificationBadge(fromSquare, toSquare, classificationName, isWhite, isMyMove);
                                }
                            });
                        }
                        return;
                    }

                    int boardX = bm.boardX;
                    int boardY = bm.boardY;
                    int boardW = bm.boardW;
                    int boardH = bm.boardH;
                    View boardView = bm.boardView;

                    boolean isFlipped = isBoardFlipped(boardView, StockfishExtension.getStateImpl());

                    View overlay = decorView.findViewWithTag("nnvc_arrow_overlay");
                    ArrowOverlayView arrowView;
                    if (overlay instanceof ArrowOverlayView) {
                        arrowView = (ArrowOverlayView) overlay;
                        arrowView.updatePosition(boardX, boardY, boardW, boardH, isFlipped);
                    } else {
                        if (overlay != null) decorView.removeView(overlay);
                        arrowView = new ArrowOverlayView(decorView.getContext());
                        arrowView.setTag("nnvc_arrow_overlay");
                        decorView.addView(arrowView);
                        arrowView.update(boardX, boardY, boardW, boardH, null, isFlipped);
                    }

                    arrowView.setVisibility(View.VISIBLE);
                    arrowView.setClassificationBadge(fromSquare, toSquare, classificationName, isWhite, isMyMove);
                    ensureZOrder(decorView);
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to set classification badge: " + t.getMessage());
                }
            }
        });
    }

    public static void clearClassificationBadge() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    ViewGroup decorView = (ViewGroup) window.getDecorView();
                    if (decorView == null) return;
                    View overlay = decorView.findViewWithTag("nnvc_arrow_overlay");
                    if (overlay instanceof ArrowOverlayView) {
                        ArrowOverlayView aov = (ArrowOverlayView) overlay;
                        aov.clearClassificationBadge();
                        if (!aov.hasArrows()) {
                            aov.setVisibility(View.GONE);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    public static void updateAccuracyEloWidget(final float whiteAcc, final float blackAcc,
                                               final int whiteElo, final int blackElo,
                                               final boolean userIsWhite) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null || StockfishExtension.isReviewOrAnalysisActivity(activity)) {
                        hideAccuracyEloWidget();
                        return;
                    }
                    Window window = activity.getWindow();
                    if (window == null) return;
                    ViewGroup decorView = (ViewGroup) window.getDecorView();
                    if (decorView == null) return;

                    if (!StockfishSettings.isAccuracyEloEnabled(activity)) {
                        hideAccuracyEloWidget();
                        return;
                    }

                    // Loại bỏ các view pill cũ nếu còn sót lại từ phiên bản trước
                    View oldTop = decorView.findViewWithTag("nnvc_accuracy_top_pill");
                    if (oldTop != null) decorView.removeView(oldTop);
                    View oldBot = decorView.findViewWithTag("nnvc_accuracy_bot_pill");
                    if (oldBot != null) decorView.removeView(oldBot);

                    View widgetView = decorView.findViewWithTag("nnvc_accuracy_elo_widget");
                    AccuracyEloWidgetView widget;
                    boolean isNew = false;
                    if (widgetView instanceof AccuracyEloWidgetView) {
                        widget = (AccuracyEloWidgetView) widgetView;
                    } else {
                        if (widgetView != null) decorView.removeView(widgetView);
                        widget = new AccuracyEloWidgetView(decorView.getContext());
                        widget.setTag("nnvc_accuracy_elo_widget");
                        float density = decorView.getContext().getResources().getDisplayMetrics().density;
                        int widgetW = widget.calculateDesiredWidth();
                        int widgetH = widget.isCollapsed() ? widget.calculateCollapsedHeight() : widget.calculateExpandedHeight();
                        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(widgetW, widgetH);
                        lp.gravity = Gravity.TOP | Gravity.START;
                        decorView.addView(widget, lp);
                        isNew = true;
                    }

                    widget.updateData(whiteAcc, blackAcc, whiteElo, blackElo, userIsWhite);

                    // Chỉ khởi tạo vị trí lần đầu hoặc khi chưa được định vị, không giật vị trí khi người dùng đang kéo thả
                    if (isNew || !widget.isPositioned()) {
                        float savedX = StockfishSettings.getAccuracyWidgetX(activity, -1f);
                        float savedY = StockfishSettings.getAccuracyWidgetY(activity, -1f);

                        if (savedX >= 0 && savedY >= 0) {
                            widget.clampAndSetPosition(savedX, savedY);
                        } else {
                            float density = decorView.getContext().getResources().getDisplayMetrics().density;
                            int widgetW = widget.calculateDesiredWidth();
                            int widgetH = widget.isCollapsed() ? widget.calculateCollapsedHeight() : widget.calculateExpandedHeight();
                            BoardMetrics bm = getBoardMetrics(decorView);
                            int decorW = decorView.getWidth();
                            if (decorW <= 0) decorW = (int) (360f * density);
                            float defX = decorW - widgetW - (10f * density);
                            float defY = (bm != null && bm.boardY > widgetH + (20f * density))
                                    ? bm.boardY - widgetH - (6f * density)
                                    : 74f * density;
                            widget.clampAndSetPosition(defX, defY);
                        }
                    }

                    if (widget.getVisibility() != View.VISIBLE) {
                        widget.setVisibility(View.VISIBLE);
                    }
                    ensureZOrder(decorView);

                } catch (Throwable t) {
                    Log.w(TAG, "updateAccuracyEloWidget failed: " + t.getMessage());
                }
            }
        });
    }

    public static void updateAccuracyEloPills(final float whiteAcc, final float blackAcc,
                                               final int whiteElo, final int blackElo,
                                               final boolean userIsWhite) {
        updateAccuracyEloWidget(whiteAcc, blackAcc, whiteElo, blackElo, userIsWhite);
    }

    public static void hideAccuracyEloWidget() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    ViewGroup decorView = (ViewGroup) window.getDecorView();
                    if (decorView == null) return;

                    View widget = decorView.findViewWithTag("nnvc_accuracy_elo_widget");
                    if (widget != null) widget.setVisibility(View.GONE);

                    View oldTop = decorView.findViewWithTag("nnvc_accuracy_top_pill");
                    if (oldTop != null) oldTop.setVisibility(View.GONE);
                    View oldBot = decorView.findViewWithTag("nnvc_accuracy_bot_pill");
                    if (oldBot != null) oldBot.setVisibility(View.GONE);
                } catch (Throwable ignored) {}
            }
        });
    }

    public static void hideAccuracyEloPills() {
        hideAccuracyEloWidget();
    }

    public static void resetAccuracyEloWidget() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    ViewGroup decorView = (ViewGroup) window.getDecorView();
                    if (decorView == null) return;

                    View widgetView = decorView.findViewWithTag("nnvc_accuracy_elo_widget");
                    if (widgetView instanceof AccuracyEloWidgetView) {
                        ((AccuracyEloWidgetView) widgetView).resetData();
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    public static void refreshOverlaysLanguage(final Activity activity) {
        if (activity == null) return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Window window = activity.getWindow();
                    if (window == null) return;
                    ViewGroup decorView = (ViewGroup) window.getDecorView();
                    if (decorView == null) return;

                    BoardMetrics bm = getBoardMetrics(decorView);
                    int boardW = (bm != null) ? bm.boardW : 0;
                    int boardX = (bm != null) ? bm.boardX : 0;

                    float density = decorView.getContext().getResources().getDisplayMetrics().density;
                    int minPillW = (int) (232 * density);

                    View topView = decorView.findViewWithTag("nnvc_accuracy_top_pill");
                    View botView = decorView.findViewWithTag("nnvc_accuracy_bot_pill");

                    int topDes = (topView instanceof PlayerAccuracyPillView) ? ((PlayerAccuracyPillView) topView).calculateDesiredWidth() : 0;
                    int botDes = (botView instanceof PlayerAccuracyPillView) ? ((PlayerAccuracyPillView) botView).calculateDesiredWidth() : 0;
                    int pillW = Math.max(minPillW, Math.max(topDes, botDes));

                    if (topView instanceof PlayerAccuracyPillView && topView.getVisibility() == View.VISIBLE) {
                        ViewGroup.LayoutParams lp = topView.getLayoutParams();
                        if (lp != null) {
                            lp.width = pillW;
                            topView.setLayoutParams(lp);
                        }
                        if (boardW > 0) {
                            int pillX = boardX + (boardW - pillW) / 2;
                            topView.setTranslationX(pillX);
                        }
                        topView.invalidate();
                    }

                    if (botView instanceof PlayerAccuracyPillView && botView.getVisibility() == View.VISIBLE) {
                        ViewGroup.LayoutParams lp = botView.getLayoutParams();
                        if (lp != null) {
                            lp.width = pillW;
                            botView.setLayoutParams(lp);
                        }
                        if (boardW > 0) {
                            int pillX = boardX + (boardW - pillW) / 2;
                            botView.setTranslationX(pillX);
                        }
                        botView.invalidate();
                    }

                    View arrowView = decorView.findViewWithTag("nnvc_arrow_overlay");
                    if (arrowView != null) {
                        arrowView.invalidate();
                    }

                    View evalBar = decorView.findViewWithTag("stockfish_eval_bar");
                    if (evalBar != null) {
                        evalBar.invalidate();
                    }

                    View widgetView = decorView.findViewWithTag("nnvc_accuracy_elo_widget");
                    if (widgetView != null) {
                        widgetView.invalidate();
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "refreshOverlaysLanguage error: " + t.getMessage());
                }
            }
        });
    }
}

