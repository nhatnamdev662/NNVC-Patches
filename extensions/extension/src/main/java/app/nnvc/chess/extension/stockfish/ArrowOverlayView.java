/*
 * Copyright 2026 NNVC
 */

package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Canvas Overlay for rendering glowing cyberpunk arrows matching 100% of the
 * NNVC Chess extension style: multi-tier palette, linear gradients, highlight edge strokes,
 * and neon drop shadows / blur glows.
 */
public class ArrowOverlayView extends View {

    public static class ClassificationBadgeData {
        public final String fromSquare;
        public final String toSquare;
        public final String classificationName;
        public final boolean isWhite;
        public final boolean isMyMove;
        public final long timestamp;

        public ClassificationBadgeData(String fromSquare, String toSquare, String classificationName, boolean isWhite, boolean isMyMove) {
            this.fromSquare = fromSquare;
            this.toSquare = toSquare;
            this.classificationName = classificationName;
            this.isWhite = isWhite;
            this.isMyMove = isMyMove;
            this.timestamp = android.os.SystemClock.uptimeMillis();
        }
    }

    public static class ArrowData {
        public final String move;
        public final String from;
        public final String to;
        public final int tier;
        public final boolean isThreat;
        public final boolean isPremove;
        public final String evalText;
        public final String threatType; // "danger" (đỏ) hoặc "attack" (vàng)
        public final int threatDepth;   // 1 (trực tiếp) hoặc 2 (phối hợp)

        public ArrowData(String move, int tier, boolean isThreat) {
            this(move, tier, isThreat, false, null, "danger", 1);
        }

        public ArrowData(String move, int tier, boolean isThreat, String evalText) {
            this(move, tier, isThreat, false, evalText, "danger", 1);
        }

        public ArrowData(String move, int tier, boolean isThreat, boolean isPremove, String evalText) {
            this(move, tier, isThreat, isPremove, evalText, "danger", 1);
        }

        public ArrowData(String move, int tier, boolean isThreat, boolean isPremove, String evalText, String threatType, int threatDepth) {
            this.move = move;
            this.from = move.substring(0, 2);
            this.to = move.substring(2, 4);
            this.tier = tier;
            this.isThreat = isThreat;
            this.isPremove = isPremove;
            this.evalText = evalText;
            this.threatType = threatType != null ? threatType : "danger";
            this.threatDepth = threatDepth > 0 ? threatDepth : 1;
        }
    }

    private static class HeadPoints {
        final float tipX, tipY;
        final float leftOuterX, leftOuterY;
        final float rightOuterX, rightOuterY;
        final float leftNotchX, leftNotchY;
        final float rightNotchX, rightNotchY;

        HeadPoints(float tipX, float tipY,
                   float leftOuterX, float leftOuterY,
                   float rightOuterX, float rightOuterY,
                   float leftNotchX, float leftNotchY,
                   float rightNotchX, float rightNotchY) {
            this.tipX = tipX;
            this.tipY = tipY;
            this.leftOuterX = leftOuterX;
            this.leftOuterY = leftOuterY;
            this.rightOuterX = rightOuterX;
            this.rightOuterY = rightOuterY;
            this.leftNotchX = leftNotchX;
            this.leftNotchY = leftNotchY;
            this.rightNotchX = rightNotchX;
            this.rightNotchY = rightNotchY;
        }
    }


    private final List<ArrowData> arrows = new ArrayList<>();
    private boolean flipped = false;

    private ClassificationBadgeData currentBadge = null;
    private final Paint badgeCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeTextPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint badgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint squareHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint squareGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint squareBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint squareDashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint brilliantCelebrationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF squareRectF = new RectF();
    private final RectF squareGlowRectF = new RectF();
    private final RectF brilliantCelebrationRectF = new RectF();
    private float lastDashSqSize = -1f;
    private DashPathEffect cachedDashEffect = null;

    public ArrowOverlayView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        try {
            setElevation(2.0f);
        } catch (Throwable ignored) {}
        // Software layer ensures BlurMaskFilter renders smoothly on all devices
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        fillPaint.setStyle(Paint.Style.FILL);

        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeJoin(Paint.Join.ROUND);
        edgePaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);

        badgeBgPaint.setStyle(Paint.Style.FILL);

        badgeStrokePaint.setStyle(Paint.Style.STROKE);
        badgeStrokePaint.setStrokeJoin(Paint.Join.ROUND);

        badgeGlowPaint.setStyle(Paint.Style.STROKE);
        badgeGlowPaint.setStrokeJoin(Paint.Join.ROUND);

        badgeTextPaint.setTypeface(android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD));
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);

        squareHighlightPaint.setStyle(Paint.Style.FILL);

        squareGlowPaint.setStyle(Paint.Style.STROKE);
        squareGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        squareGlowPaint.setStrokeJoin(Paint.Join.ROUND);

        squareBorderPaint.setStyle(Paint.Style.STROKE);
        squareBorderPaint.setStrokeCap(Paint.Cap.ROUND);
        squareBorderPaint.setStrokeJoin(Paint.Join.ROUND);

        squareDashedPaint.setStyle(Paint.Style.STROKE);
        squareDashedPaint.setStrokeCap(Paint.Cap.ROUND);
        squareDashedPaint.setStrokeJoin(Paint.Join.ROUND);

        brilliantCelebrationPaint.setStyle(Paint.Style.STROKE);
        brilliantCelebrationPaint.setStrokeCap(Paint.Cap.ROUND);
        brilliantCelebrationPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setClassificationBadge(String square, String classificationName, boolean isWhite, boolean isMyMove) {
        setClassificationBadge(null, square, classificationName, isWhite, isMyMove);
    }

    public void setClassificationBadge(String fromSquare, String toSquare, String classificationName, boolean isWhite, boolean isMyMove) {
        this.currentBadge = new ClassificationBadgeData(fromSquare, toSquare, classificationName, isWhite, isMyMove);
        invalidate();
    }

    public void clearClassificationBadge() {
        this.currentBadge = null;
        invalidate();
    }

    public boolean hasBadges() {
        return currentBadge != null;
    }

    public boolean hasArrows() {
        return !arrows.isEmpty();
    }

    public void clearArrowsOnly() {
        this.arrows.clear();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Never consume touches so user can move pieces underneath
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    public void update(int boardX, int boardY, int width, int height, List<ArrowData> newArrows, boolean flipped) {
        this.flipped = flipped;
        this.arrows.clear();
        if (newArrows != null) {
            this.arrows.addAll(newArrows);
        }

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(width, height);
        } else {
            lp.width = width;
            lp.height = height;
        }
        if (lp instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) lp).gravity = Gravity.TOP | Gravity.START;
        }
        setLayoutParams(lp);
        setTranslationX(boardX);
        setTranslationY(boardY);

        invalidate();
    }

    public void updatePosition(int boardX, int boardY, int width, int height, boolean flipped) {
        this.flipped = flipped;
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(width, height);
        } else {
            lp.width = width;
            lp.height = height;
        }
        if (lp instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) lp).gravity = Gravity.TOP | Gravity.START;
        }
        setLayoutParams(lp);
        setTranslationX(boardX);
        setTranslationY(boardY);
        invalidate();
    }

    public void clear() {
        this.arrows.clear();
        this.currentBadge = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (arrows.isEmpty() && !hasBadges()) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float sqSize = Math.min(w, h) / 8.0f;

        // 1. Draw Square Classification Badge FIRST (Lớp nền: Highlight ô cờ & Badge phân loại vẽ trước)
        // Đảm bảo không đè lên mũi tên và nhãn eval của Stockfish.
        if (currentBadge != null) {
            drawSingleClassificationBadge(canvas, sqSize, currentBadge);
        }

        // 2. Draw arrows in reverse order (Tier 5 first, Tier 1 last so Tier 1 is on top)
        if (!arrows.isEmpty()) {
            for (int i = arrows.size() - 1; i >= 0; i--) {
                ArrowData arrow = arrows.get(i);
                float perpOffset = computePerpOffset(i, sqSize);
                float targetOffset = computeTargetOffset(i, sqSize);
                drawSingleArrow(canvas, arrow, sqSize, perpOffset, targetOffset);
            }

            // 3. Draw Eval Badges with Anti-collision avoidance synchronized with arrows (Lớp trên cùng)
            drawEvalBadges(canvas, sqSize);
        }
    }

    /**
     * Compute orthogonal lane separation (perpOffset) matching NNVC extension
     * so concurrent arrows sharing paths, opposite directions, or common squares don't overlap.
     */
    private float computePerpOffset(int currentIndex, float sqSize) {
        if (currentIndex <= 0 || currentIndex >= arrows.size()) return 0f;
        ArrowData current = arrows.get(currentIndex);
        float offset = 0f;

        for (int j = 0; j < currentIndex; j++) {
            ArrowData prev = arrows.get(j);
            boolean sameDirect = current.from.equals(prev.from) && current.to.equals(prev.to);
            boolean opposite   = current.from.equals(prev.to)   && current.to.equals(prev.from);
            boolean sameTarget = current.to.equals(prev.to);
            boolean sameSource = current.from.equals(prev.from);

            if (sameDirect) {
                offset += sqSize * 0.12f;
            } else if (opposite) {
                offset += sqSize * 0.10f;
            } else if (sameTarget) {
                offset += (currentIndex % 2 == 1 ? 1 : -1) * sqSize * 0.08f;
            } else if (sameSource) {
                offset += (currentIndex % 2 == 1 ? 1 : -1) * sqSize * 0.06f;
            }
        }
        return offset;
    }

    /**
     * Stepped offset for multiple arrows targeting the exact same destination square
     * matching NNVC extension (prevDestCount * sqSize * 0.22).
     */
    private float computeTargetOffset(int currentIndex, float sqSize) {
        if (currentIndex <= 0 || currentIndex >= arrows.size()) return 0f;
        ArrowData current = arrows.get(currentIndex);
        int count = 0;
        for (int j = 0; j < currentIndex; j++) {
            if (current.to.equals(arrows.get(j).to)) {
                count++;
            }
        }
        return count * (sqSize * 0.22f);
    }

    private void drawSingleArrow(Canvas canvas, ArrowData arrow, float sqSize, float perpOffset, float targetOffset) {
        float[] fromCenter = getSquareCenter(arrow.from, flipped, sqSize);
        float[] toCenter   = getSquareCenter(arrow.to,   flipped, sqSize);
        if (fromCenter == null || toCenter == null) return;

        float x1 = fromCenter[0], y1 = fromCenter[1];
        float x2 = toCenter[0],   y2 = toCenter[1];

        int fileDelta = Math.abs(arrow.to.charAt(0) - arrow.from.charAt(0));
        int rankDelta = Math.abs(arrow.to.charAt(1) - arrow.from.charAt(1));
        boolean isKnight = (fileDelta == 1 && rankDelta == 2) || (fileDelta == 2 && rankDelta == 1);

        float thicknessScale = 1.0f - 0.06f * (arrow.tier - 1);
        float baseShaftHalf = arrow.isThreat ? 0.048f : 0.042f;
        float baseHeadHalf = arrow.isThreat ? 0.18f : 0.17f;
        float baseHeadLen = arrow.isThreat ? 0.22f : 0.24f;

        float shaftHalf = sqSize * baseShaftHalf * thicknessScale;
        float neckHalf = shaftHalf;
        float headHalf = sqSize * baseHeadHalf * thicknessScale;
        float headLen = sqSize * baseHeadLen * thicknessScale;

        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        float startOffset = Math.min(len * 0.25f, sqSize * 0.16f);
        float edgeStroke = Math.max(1.5f, sqSize * 0.032f);

        Path arrowPath;
        if (isKnight) {
            arrowPath = buildKnightPath(x1, y1, x2, y2, fileDelta, rankDelta,
                    startOffset, shaftHalf, neckHalf, headHalf, headLen, perpOffset, targetOffset);
        } else {
            arrowPath = buildStraightPath(x1, y1, x2, y2, len,
                    startOffset, shaftHalf, neckHalf, headHalf, headLen, perpOffset, targetOffset);
        }
        if (arrowPath == null) return;

        if (arrow.isPremove) {
            // Style riêng biệt cho Mũi Tên Premove (Premove Arrow):
            // 1. Màu Cam Hổ Phách Neon (#F59E0B) phối Đỏ Cam (#EA580C) để phân biệt rõ ràng với mũi tên gợi ý Play và mũi tên Đe Dọa
            // 2. Viền nét việt quất/than đậm (#78350F) sắc nét
            // 3. Glow màu hổ phách cảnh báo nhẹ nhàng
            int premoveColor = 0xFFF59E0B; // Amber Premove
            int premoveDark = 0xFFEA580C;

            // 1. Glow viền cảnh báo
            glowPaint.setColor(Color.argb((int)(0.35f * 255), 245, 158, 11));
            glowPaint.setStrokeWidth(edgeStroke + sqSize * 0.025f);
            glowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1.0f, sqSize * 0.04f), BlurMaskFilter.Blur.NORMAL));
            canvas.drawPath(arrowPath, glowPaint);
            glowPaint.setMaskFilter(null);

            // 2. Linear Gradient Fill Cam Hổ Phách
            fillPaint.setShader(new LinearGradient(x1, y1, x2, y2,
                    new int[]{premoveDark, premoveColor},
                    new float[]{0.0f, 1.0f},
                    Shader.TileMode.CLAMP));
            canvas.drawPath(arrowPath, fillPaint);

            // 3. Viền mảnh tương phản cao
            edgePaint.setShader(null);
            edgePaint.setColor(0xCC78350F); // Deep amber contrast stroke
            edgePaint.setStrokeWidth(Math.max(1.5f, sqSize * 0.016f));
            canvas.drawPath(arrowPath, edgePaint);
            return;
        }

        if (arrow.isThreat) {
            // Style chuẩn 100% NNVC Desktop Extension:
            // 1. "danger": Quân đối thủ đe dọa quân ta -> Màu Đỏ Cảnh Báo (#EF4444 phối #B91C1C)
            // 2. "attack": Quân ta đe dọa quân đối thủ -> Màu Vàng/Hổ Phách (#F59E0B phối #D97706)
            // 3. depth == 2: Nét đứt (DashPathEffect) biểu thị đòn phối hợp tiềm năng
            boolean isAttack = "attack".equalsIgnoreCase(arrow.threatType);
            int threatLight = isAttack ? 0xFFF59E0B : 0xFFEF4444;
            int threatDark  = isAttack ? 0xFFD97706 : 0xFFB91C1C;
            int glowRgb     = isAttack ? Color.argb((int)(0.45f * 255), 245, 158, 11) : Color.argb((int)(0.45f * 255), 239, 68, 68);
            int strokeColor = isAttack ? 0xCC78350F : 0xCC7F1D1D;

            // 1. Glow viền cảnh báo
            glowPaint.setColor(glowRgb);
            glowPaint.setStrokeWidth(edgeStroke + sqSize * 0.035f);
            glowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1.0f, sqSize * 0.05f), BlurMaskFilter.Blur.NORMAL));
            canvas.drawPath(arrowPath, glowPaint);
            glowPaint.setMaskFilter(null);

            // 2. Linear Gradient Fill
            fillPaint.setShader(new LinearGradient(x1, y1, x2, y2,
                    new int[]{threatDark, threatLight},
                    new float[]{0.0f, 1.0f},
                    Shader.TileMode.CLAMP));
            if (arrow.threatDepth == 2) {
                fillPaint.setAlpha((int)(0.80f * 255));
            }
            canvas.drawPath(arrowPath, fillPaint);
            fillPaint.setAlpha(255);

            // 3. Viền mảnh tương phản cao (Hỗ trợ nét đứt nếu depth == 2)
            edgePaint.setShader(null);
            edgePaint.setColor(strokeColor);
            edgePaint.setStrokeWidth(Math.max(1.5f, sqSize * 0.018f));
            if (arrow.threatDepth == 2) {
                edgePaint.setPathEffect(new DashPathEffect(new float[]{sqSize * 0.12f, sqSize * 0.06f}, 0));
            }
            canvas.drawPath(arrowPath, edgePaint);
            edgePaint.setPathEffect(null);
            return;
        }

        // Color computation matching NNVC extension with custom tier palette
        int[] rawRgb = getBaseRgb(getContext(), arrow.tier, arrow.isThreat, arrow.isPremove);
        int[] rgb = (arrow.tier == 2) ? tint(rawRgb, 0.12f)
                : (arrow.tier >= 3) ? tint(rawRgb, 0.30f)
                : rawRgb;

        int[] colTail = shade(rgb, 0.14f);
        int[] colMid = rgb;
        int[] colHeadLight = tint(rgb, 0.22f);
        int[] colEdge = shade(rgb, 0.42f);

        float opacity = (arrow.tier == 1) ? 0.94f
                : (arrow.tier == 2) ? 0.82f
                : 0.65f;

        // 1. Neon Drop Shadow / Glow
        glowPaint.setColor(Color.argb((int)(0.40f * opacity * 255), colMid[0], colMid[1], colMid[2]));
        glowPaint.setStrokeWidth(edgeStroke + sqSize * 0.045f);
        glowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1.0f, sqSize * 0.06f), BlurMaskFilter.Blur.NORMAL));
        canvas.drawPath(arrowPath, glowPaint);
        glowPaint.setMaskFilter(null);

        // 2. Linear Gradient Fill
        int cTail = Color.argb((int)(0.88f * opacity * 255), colTail[0], colTail[1], colTail[2]);
        int cMid  = Color.argb((int)(0.96f * opacity * 255), colMid[0], colMid[1], colMid[2]);
        int cHead = Color.argb((int)(1.0f  * opacity * 255), colHeadLight[0], colHeadLight[1], colHeadLight[2]);
        fillPaint.setShader(new LinearGradient(x1, y1, x2, y2,
                new int[]{cTail, cMid, cHead},
                new float[]{0.0f, 0.45f, 1.0f},
                Shader.TileMode.CLAMP));
        canvas.drawPath(arrowPath, fillPaint);

        // 3. Highlight Edge Stroke
        int cEdgeTail = Color.argb((int)(0.75f * opacity * 255), colEdge[0], colEdge[1], colEdge[2]);
        int cEdgeHead = Color.argb((int)(0.65f * opacity * 255), 255, 255, 255);
        edgePaint.setStrokeWidth(edgeStroke);
        edgePaint.setShader(new LinearGradient(x1, y1, x2, y2,
                new int[]{cEdgeTail, cEdgeHead},
                new float[]{0.0f, 1.0f},
                Shader.TileMode.CLAMP));
        canvas.drawPath(arrowPath, edgePaint);
    }

    private Path buildStraightPath(float x1, float y1, float x2, float y2, float len,
                                   float startOffset, float shaftHalf, float neckHalf,
                                   float headHalf, float headLen, float perpOffset, float targetOffset) {
        if (len < 2.0f) return null;

        float ux = (x2 - x1) / len;
        float uy = (y2 - y1) / len;
        float px = -uy;
        float py = ux;

        float startX = x1 + ux * startOffset + px * (perpOffset * 0.25f);
        float startY = y1 + uy * startOffset + py * (perpOffset * 0.25f);
        float endX = x2 - ux * targetOffset + px * perpOffset;
        float endY = y2 - uy * targetOffset + py * perpOffset;

        HeadPoints h = buildHeadPoints(endX, endY, ux, uy, px, py, headLen, headHalf, neckHalf);

        float leftStartX  = startX + px * shaftHalf;
        float leftStartY  = startY + py * shaftHalf;
        float rightStartX = startX - px * shaftHalf;
        float rightStartY = startY - py * shaftHalf;

        Path p = new Path();
        p.moveTo(leftStartX, leftStartY);
        p.lineTo(h.leftNotchX, h.leftNotchY);
        p.lineTo(h.leftOuterX, h.leftOuterY);
        p.lineTo(h.tipX, h.tipY);
        p.lineTo(h.rightOuterX, h.rightOuterY);
        p.lineTo(h.rightNotchX, h.rightNotchY);
        p.lineTo(rightStartX, rightStartY);
        p.close();
        return p;
    }

    private Path buildKnightPath(float x1, float y1, float x2, float y2,
                                 int fileDelta, int rankDelta, float startOffset,
                                 float shaftHalf, float neckHalf, float headHalf, float headLen,
                                 float perpOffset, float targetOffset) {
        float elbowX = x1;
        float elbowY = y2;
        if (fileDelta == 2 && rankDelta == 1) {
            elbowX = x2;
            elbowY = y1;
        }

        float s1dx = elbowX - x1, s1dy = elbowY - y1;
        float s1len = (float) Math.hypot(s1dx, s1dy);
        if (s1len < 1e-3f) s1len = 1.0f;
        float u1x = s1dx / s1len, u1y = s1dy / s1len;
        float p1x = -u1y, p1y = u1x;

        float startX = x1 + u1x * startOffset + p1x * (perpOffset * 0.25f);
        float startY = y1 + u1y * startOffset + p1y * (perpOffset * 0.25f);

        float s2dx = x2 - elbowX, s2dy = y2 - elbowY;
        float s2len = (float) Math.hypot(s2dx, s2dy);
        if (s2len < 1e-3f) s2len = 1.0f;
        float u2x = s2dx / s2len, u2y = s2dy / s2len;
        float p2x = -u2y, p2y = u2x;

        float endX = x2 - u2x * targetOffset + p2x * perpOffset;
        float endY = y2 - u2y * targetOffset + p2y * perpOffset;

        HeadPoints h = buildHeadPoints(endX, endY, u2x, u2y, p2x, p2y, headLen, headHalf, neckHalf);

        float leftStartX  = startX + p1x * shaftHalf;
        float leftStartY  = startY + p1y * shaftHalf;
        float rightStartX = startX - p1x * shaftHalf;
        float rightStartY = startY - p1y * shaftHalf;

        float shiftedElbowX = elbowX + p1x * perpOffset;
        float shiftedElbowY = elbowY + p1y * perpOffset;

        float[] leftElbow = lineIntersect(leftStartX, leftStartY, u1x, u1y,
                shiftedElbowX + p2x * shaftHalf, shiftedElbowY + p2y * shaftHalf, u2x, u2y);
        float[] rightElbow = lineIntersect(rightStartX, rightStartY, u1x, u1y,
                shiftedElbowX - p2x * shaftHalf, shiftedElbowY - p2y * shaftHalf, u2x, u2y);

        Path p = new Path();
        p.moveTo(leftStartX, leftStartY);
        p.lineTo(leftElbow[0], leftElbow[1]);
        p.lineTo(h.leftNotchX, h.leftNotchY);
        p.lineTo(h.leftOuterX, h.leftOuterY);
        p.lineTo(h.tipX, h.tipY);
        p.lineTo(h.rightOuterX, h.rightOuterY);
        p.lineTo(h.rightNotchX, h.rightNotchY);
        p.lineTo(rightElbow[0], rightElbow[1]);
        p.lineTo(rightStartX, rightStartY);
        p.close();
        return p;
    }

    private HeadPoints buildHeadPoints(float tipX, float tipY, float ux, float uy,
                                       float px, float py, float headLen,
                                       float headHalf, float neckHalf) {
        float headBaseX = tipX - ux * headLen;
        float headBaseY = tipY - uy * headLen;
        return new HeadPoints(
                tipX, tipY,
                headBaseX + px * headHalf, headBaseY + py * headHalf,
                headBaseX - px * headHalf, headBaseY - py * headHalf,
                headBaseX + px * neckHalf, headBaseY + py * neckHalf,
                headBaseX - px * neckHalf, headBaseY - py * neckHalf
        );
    }

    private float[] lineIntersect(float ax, float ay, float adx, float ady,
                                  float bx, float by, float bdx, float bdy) {
        float den = adx * bdy - ady * bdx;
        if (Math.abs(den) < 1e-6f) return new float[]{ax, ay};
        float t = ((bx - ax) * bdy - (by - ay) * bdx) / den;
        return new float[]{ax + adx * t, ay + ady * t};
    }

    private float[] getSquareCenter(String sq, boolean flipped, float sqSize) {
        if (sq == null || sq.length() < 2) return null;
        int file = sq.charAt(0) - 'a';
        int rank = sq.charAt(1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) return null;

        int col = flipped ? (7 - file) : file;
        int row = flipped ? rank : (7 - rank);

        float cx = col * sqSize + sqSize / 2.0f;
        float cy = row * sqSize + sqSize / 2.0f;
        return new float[]{cx, cy};
    }

    private static int[] getBaseRgb(Context context, int tier, boolean isThreat, boolean isPremove) {
        if (isPremove) {
            return new int[]{245, 158, 11}; // #F59E0B (Amber Premove)
        }
        if (isThreat) {
            return new int[]{239, 68, 68}; // #EF4444 (Danger Red)
        }
        int color = (context != null)
                ? StockfishSettings.getArrowTierColor(context, tier)
                : StockfishSettings.DEFAULT_TIER_COLORS[Math.max(1, Math.min(5, tier)) - 1];
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return new int[]{r, g, b};
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int[] tint(int[] c, float amt) {
        return new int[]{
                clamp((int)(c[0] + (255 - c[0]) * amt)),
                clamp((int)(c[1] + (255 - c[1]) * amt)),
                clamp((int)(c[2] + (255 - c[2]) * amt))
        };
    }

    private static int[] shade(int[] c, float amt) {
        return new int[]{
                clamp((int)(c[0] * (1.0f - amt))),
                clamp((int)(c[1] * (1.0f - amt))),
                clamp((int)(c[2] * (1.0f - amt)))
        };
    }

    /**
     * Renders cyberpunk frosted eval score badges on top of arrows
     * matching NNVC browser extension logic 100%:
     * - Target square corner anchors per tier:
     *     Tier 1: Top-Left
     *     Tier 2: Bottom-Left
     *     Tier 3: Bottom-Right
     *     Tier 4: Top-Right
     *     Tier 5+: Midpoint of arrow shaft
     * - Clamped inside target square / board bounds.
     * - Extension style: shade(rgb, 0.58) background, tint(rgb, 0.54) border, JetBrains Mono font.
     */
    private static class RectBox {
        final float x, y, w, h;
        RectBox(float x, float y, float w, float h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }

    private static boolean checkOverlap(RectBox b1, RectBox b2, float pad) {
        return !(b1.x + b1.w + pad < b2.x
                || b2.x + b2.w + pad < b1.x
                || b1.y + b1.h + pad < b2.y
                || b2.y + b2.h + pad < b1.y);
    }

    private static class Corridor {
        final String sq;
        final char axis; // 'v', 'h', 'd'
        Corridor(String sq, char axis) {
            this.sq = sq; this.axis = axis;
        }
    }

    /**
     * Renders cyberpunk frosted eval score badges on top of arrows
     * matching NNVC browser extension logic 100%:
     * - passingCorridors detection for moves cutting through intermediate squares
     * - 8 Candidate positions per target square with corridor avoidance
     * - checkOverlap collision avoidance across all placed labels
     * - Exact extension dimensions, padding, colors (shade 0.58, tint 0.54), glow, and JetBrains Mono typography.
     */
    private void drawEvalBadges(Canvas canvas, float sqSize) {
        float bWidth = getWidth();
        float bHeight = getHeight();
        if (bWidth <= 0 || bHeight <= 0) return;

        // 1. Compute passing corridors (mũi tên dài đi xuyên qua các ô trung gian)
        List<Corridor> passingCorridors = new ArrayList<>();
        for (ArrowData a : arrows) {
            if (a.from == null || a.to == null || a.from.length() < 2 || a.to.length() < 2) continue;
            int fFile = a.from.charAt(0) - 'a';
            int fRank = a.from.charAt(1) - '1';
            int tFile = a.to.charAt(0) - 'a';
            int tRank = a.to.charAt(1) - '1';
            int dFile = tFile - fFile;
            int dRank = tRank - fRank;
            int stepX = dFile == 0 ? 0 : (dFile > 0 ? 1 : -1);
            int stepY = dRank == 0 ? 0 : (dRank > 0 ? 1 : -1);

            if (dFile == 0 || dRank == 0 || Math.abs(dFile) == Math.abs(dRank)) {
                int dist = Math.max(Math.abs(dFile), Math.abs(dRank));
                if (dist > 1) {
                    char axis = dFile == 0 ? 'v' : (dRank == 0 ? 'h' : 'd');
                    for (int s = 1; s < dist; s++) {
                        int midFile = fFile + stepX * s;
                        int midRank = fRank + stepY * s;
                        String midSq = "" + (char) ('a' + midFile) + (char) ('1' + midRank);
                        passingCorridors.add(new Corridor(midSq, axis));
                    }
                }
            }
        }

        List<RectBox> placedLabelBoxes = new ArrayList<>();

        // Tránh bị Eval Bar che lấp: Nếu Eval Bar hiển thị ở mép trái bàn cờ
        float density = getContext().getResources().getDisplayMetrics().density;
        float barWidth = 12f * density;
        float boardX = getTranslationX();
        float evalBarOverlap = 0f;
        if (StockfishSettings.isEvalBarEnabled(getContext())) {
            if (boardX < barWidth) {
                evalBarOverlap = (barWidth - boardX) + 5f * density;
            }
        }
        final float minSafeX = Math.max(2f, evalBarOverlap);
        if (evalBarOverlap > 0f) {
            placedLabelBoxes.add(new RectBox(0f, 0f, evalBarOverlap, bHeight));
        }

        for (int i = 0; i < arrows.size(); i++) {
            ArrowData arrow = arrows.get(i);
            if (arrow.evalText == null || arrow.evalText.trim().isEmpty()) continue;

            float[] fromCenter = getSquareCenter(arrow.from, flipped, sqSize);
            float[] toCenter   = getSquareCenter(arrow.to,   flipped, sqSize);
            if (fromCenter == null || toCenter == null) continue;

            float x1 = fromCenter[0], y1 = fromCenter[1];
            float x2 = toCenter[0],   y2 = toCenter[1];

            int tierVal = arrow.tier;
            String lblText = arrow.evalText.trim();

            // Sizing metrics matching NNVC Extension:
            // fontSize = Math.max(9, sqSize * 0.125)
            // padH = Math.max(4, sqSize * 0.065)
            // padV = Math.max(2, sqSize * 0.035)
            // pillW = Math.max(24, Math.min(sqSize * 0.62, (lblText.length * 0.58 + 0.3) * fontSize + padH * 2))
            // pillH = fontSize * 1.1 + padV * 2
            float fontSize = Math.max(9f, sqSize * 0.125f);
            float padH = Math.max(4f, sqSize * 0.065f);
            float padV = Math.max(2f, sqSize * 0.035f);

            badgeTextPaint.setTextSize(fontSize);
            float measuredTextW = badgeTextPaint.measureText(lblText);
            float calcW = (lblText.length() * 0.58f + 0.3f) * fontSize + padH * 2f;
            float rawW = Math.max(calcW, measuredTextW + padH * 2f);
            float pillW = Math.max(24f, Math.min(sqSize * 0.62f, rawW));
            float pillH = fontSize * 1.1f + padV * 2f;

            float sqLeft = x2 - sqSize / 2f;
            float sqTop = y2 - sqSize / 2f;
            float sqRight = x2 + sqSize / 2f;
            float sqBottom = y2 + sqSize / 2f;

            // Kiểm tra xem ô đích to có bị mũi tên nào khác đi xuyên qua không
            Corridor passCorridor = null;
            for (Corridor c : passingCorridors) {
                if (c.sq.equals(arrow.to)) {
                    passCorridor = c;
                    break;
                }
            }

            // 8 Candidates quanh ô đích: Tuyệt đối không đặt trên thân mũi tên!
            List<float[]> candidates = new ArrayList<>();
            if (passCorridor != null && passCorridor.axis == 'v') {
                // Mũi tên dài chạy dọc: dạt lệch hẳn sang Trái / Phải
                candidates.add(new float[]{sqLeft + 2f, sqTop + sqSize * 0.06f});
                candidates.add(new float[]{sqRight - pillW - 2f, sqTop + sqSize * 0.06f});
                candidates.add(new float[]{sqLeft + 2f, sqBottom - pillH - sqSize * 0.06f});
                candidates.add(new float[]{sqRight - pillW - 2f, sqBottom - pillH - sqSize * 0.06f});
                candidates.add(new float[]{sqLeft - pillW - 2f, sqTop + (sqSize - pillH) / 2f});
                candidates.add(new float[]{sqRight + 2f, sqTop + (sqSize - pillH) / 2f});
            } else if (passCorridor != null && passCorridor.axis == 'h') {
                // Mũi tên dài chạy ngang: dạt lệch hẳn lên Trên / Dưới
                candidates.add(new float[]{sqLeft + sqSize * 0.06f, sqTop + 2f});
                candidates.add(new float[]{sqRight - pillW - sqSize * 0.06f, sqTop + 2f});
                candidates.add(new float[]{sqLeft + sqSize * 0.06f, sqBottom - pillH - 2f});
                candidates.add(new float[]{sqRight - pillW - sqSize * 0.06f, sqBottom - pillH - 2f});
                candidates.add(new float[]{sqLeft + (sqSize - pillW) / 2f, sqTop - pillH - 2f});
                candidates.add(new float[]{sqLeft + (sqSize - pillW) / 2f, sqBottom + 2f});
            } else {
                // 0: Góc Trên - Trái ô đích
                candidates.add(new float[]{sqLeft + sqSize * 0.04f, sqTop + sqSize * 0.04f});
                // 1: Góc Trên - Phải ô đích
                candidates.add(new float[]{sqRight - pillW - sqSize * 0.04f, sqTop + sqSize * 0.04f});
                // 2: Góc Dưới - Trái ô đích
                candidates.add(new float[]{sqLeft + sqSize * 0.04f, sqBottom - pillH - sqSize * 0.04f});
                // 3: Góc Dưới - Phải ô đích
                candidates.add(new float[]{sqRight - pillW - sqSize * 0.04f, sqBottom - pillH - sqSize * 0.04f});
                // 4: Cạnh ngoài phía trên ô đích
                candidates.add(new float[]{sqLeft + (sqSize - pillW) / 2f, sqTop - pillH - 2f});
                // 5: Cạnh ngoài phía dưới ô đích
                candidates.add(new float[]{sqLeft + (sqSize - pillW) / 2f, sqBottom + 2f});
                // 6: Cạnh ngoài bên trái ô đích
                candidates.add(new float[]{sqLeft - pillW - 2f, sqTop + (sqSize - pillH) / 2f});
                // 7: Cạnh ngoài bên phải ô đích
                candidates.add(new float[]{sqRight + 2f, sqTop + (sqSize - pillH) / 2f});
            }

            int prefIdx = tierVal == 1 ? 0 : (tierVal == 2 ? 1 : (tierVal == 3 ? 2 : (tierVal == 4 ? 3 : 0)));
            if (prefIdx >= candidates.size()) prefIdx = 0;

            // Nếu ô đích ở sát mép trái (bị Eval Bar che lấp), ưu tiên các vị trí bên phải ô đích
            if (sqLeft < minSafeX) {
                if (passCorridor != null && passCorridor.axis == 'v') {
                    prefIdx = 1; // Bên phải trên
                } else if (passCorridor == null) {
                    prefIdx = (tierVal == 1 || tierVal == 3) ? 1 : 3; // Top-Right hoặc Bottom-Right
                }
            }

            List<Integer> order = new ArrayList<>();
            order.add(prefIdx);
            for (int k = 0; k < candidates.size(); k++) {
                if (k != prefIdx) order.add(k);
            }

            RectBox chosen = null;
            for (int candIdx : order) {
                float[] cand = candidates.get(candIdx);
                float clampedX = Math.max(minSafeX, Math.min(bWidth - pillW - 2f, cand[0]));
                float clampedY = Math.max(2f, Math.min(bHeight - pillH - 2f, cand[1]));
                RectBox box = new RectBox(clampedX, clampedY, pillW, pillH);

                boolean hasCollision = false;
                for (RectBox placed : placedLabelBoxes) {
                    if (checkOverlap(box, placed, 3f)) {
                        hasCollision = true;
                        break;
                    }
                }
                if (!hasCollision) {
                    chosen = box;
                    break;
                }
            }

            if (chosen == null) {
                float[] fallbackCand = candidates.get(prefIdx);
                chosen = new RectBox(
                        Math.max(minSafeX, Math.min(bWidth - pillW - 2f, fallbackCand[0])),
                        Math.max(2f, Math.min(bHeight - pillH - 2f, fallbackCand[1])),
                        pillW, pillH
                );
            }
            placedLabelBoxes.add(chosen);

            float labelX = chosen.x;
            float labelY = chosen.y;

            // Styling & Colors matching NNVC Extension:
            boolean isBookLabel = "BOOK".equals(lblText);
            int[] rawRgb = getBaseRgb(getContext(), arrow.tier, arrow.isThreat, arrow.isPremove);
            int[] rgb = (arrow.tier == 2) ? tint(rawRgb, 0.12f)
                    : (arrow.tier >= 3) ? tint(rawRgb, 0.30f)
                    : rawRgb;

            int[] shadeRgb = shade(rgb, 0.58f);
            float bgAlpha = tierVal == 1 ? 0.92f : 0.82f;
            int bgCol = isBookLabel
                    ? Color.argb((int)(0.94f * 255), 28, 25, 23)
                    : Color.argb((int)(bgAlpha * 255), shadeRgb[0], shadeRgb[1], shadeRgb[2]);

            int[] tintBorder = tint(rgb, 0.54f);
            float borderAlpha = tierVal == 1 ? 0.64f : 0.44f;
            int borderCol = isBookLabel
                    ? Color.argb((int)(0.85f * 255), 245, 158, 11)
                    : Color.argb((int)(borderAlpha * 255), tintBorder[0], tintBorder[1], tintBorder[2]);

            int textColor = isBookLabel ? 0xFFFBBF24 : 0xFFF8FBFF;
            float cornerRadius = Math.max(5f, sqSize * 0.09f);

            android.graphics.RectF rect = new android.graphics.RectF(labelX, labelY, labelX + pillW, labelY + pillH);

            // Subtle drop shadow matching extension (0 5px 16px rgba(0,0,0,0.32))
            badgeGlowPaint.setColor(Color.argb(82, 0, 0, 0));
            badgeGlowPaint.setStrokeWidth(Math.max(1f, sqSize * 0.02f));
            badgeGlowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1.5f, sqSize * 0.035f), BlurMaskFilter.Blur.NORMAL));
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, badgeGlowPaint);
            badgeGlowPaint.setMaskFilter(null);

            // Background Fill (Frosted Glass with tint/shade)
            badgeBgPaint.setColor(bgCol);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, badgeBgPaint);

            // 1px Border Stroke
            badgeStrokePaint.setColor(borderCol);
            badgeStrokePaint.setStrokeWidth(Math.max(1.0f, sqSize * 0.012f));
            badgeStrokePaint.setMaskFilter(null);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, badgeStrokePaint);

            // Text Draw
            badgeTextPaint.setColor(textColor);
            badgeTextPaint.setTextSize(fontSize);
            badgeTextPaint.setFakeBoldText(true);
            Paint.FontMetrics fm = badgeTextPaint.getFontMetrics();
            float textBaseline = labelY + (pillH - (fm.ascent + fm.descent)) / 2f;
            float textX = labelX + pillW / 2f;
            canvas.drawText(lblText, textX, textBaseline, badgeTextPaint);
        }
    }

    private static int getClassificationThemeColor(String classificationName) {
        if (classificationName == null) return 0xFF81B64C;
        String lower = classificationName.toLowerCase(java.util.Locale.US).replace(" ", "_");
        switch (lower) {
            case "brilliant":
                return 0xFF26C2A3; // Extension: #26c2a3 (Brilliant Cyan)
            case "great":
            case "greatfind":
            case "great_find":
                return 0xFF749BBF; // Extension: #749bbf (Great Blue)
            case "best":
                return 0xFF81B64C; // Extension: #81b64c (Best Green)
            case "forced":
                return 0xFF999999; // Extension: #999999 (Forced Gray)
            case "excellent":
                return 0xFF81B64C; // Extension: #81b64c (Excellent Green)
            case "good":
                return 0xFF95B776; // Extension: #95b776 (Good Light Green)
            case "book":
                return 0xFFD5A47D; // Extension: #d5a47d (Book Tan)
            case "inaccuracy":
                return 0xFFF7C631; // Extension: #f7c631 (Inaccuracy Yellow)
            case "mistake":
                return 0xFFFFA459; // Extension: #ffa459 (Mistake Orange)
            case "blunder":
                return 0xFFFA412D; // Extension: #fa412d (Blunder Red)
            case "miss":
            case "missed":
                return 0xFFFF7769; // Extension: #ff7769 (Miss Coral)
            case "missedwin":
            case "missed_win":
                return 0xFFF7C631; // Extension: #f7c631 (Missed Win Gold)
            default:
                return 0xFF81B64C;
        }
    }

    private void drawSquareHighlight(Canvas canvas, float sqSize, String sq, int themeColor, boolean isTargetSquare, boolean isBrilliantOrGreat) {
        if (sq == null || sq.length() < 2) return;
        int file = sq.charAt(0) - 'a';
        int rank = sq.charAt(1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) return;

        int col = flipped ? (7 - file) : file;
        int row = flipped ? rank : (7 - rank);

        float left = col * sqSize;
        float top = row * sqSize;
        float right = left + sqSize;
        float bottom = top + sqSize;

        float cornerRadius = sqSize * 0.08f;

        if (isTargetSquare) {
            // ==============================================================
            // Ô ĐÍCH (Target Square): NƠI QUÂN CỜ ĐANG ĐỨNG
            // ==============================================================
            // QUY TẮC SỐNG CÒN: TUYỆT ĐỐI KHÔNG VẼ LỚP NỀN (FILL) LÊN TÂM Ô CỜ!
            // Giữ cho sprite quân cờ Chess.com bên dưới hoàn toàn 100% nguyên bản,
            // trong trẻo, không bị nhiễm màu / lem màu hay biến đổi sắc tố.

            // 1. Lớp viền hào quang ngoài (Ambient Outer Halo / Glow):
            // Ôm trọn viền ngoài ô cờ, tạo hiệu ứng vầng sáng công nghệ cao cấp.
            float glowWidth = isBrilliantOrGreat ? Math.max(5f, sqSize * 0.085f) : Math.max(3.5f, sqSize * 0.055f);
            float glowInset = glowWidth / 2f + 1f;
            squareGlowRectF.set(left + glowInset, top + glowInset, right - glowInset, bottom - glowInset);

            squareGlowPaint.setColor(themeColor);
            squareGlowPaint.setStrokeWidth(glowWidth);
            squareGlowPaint.setAlpha(isBrilliantOrGreat ? 140 : 80);
            canvas.drawRoundRect(squareGlowRectF, cornerRadius, cornerRadius, squareGlowPaint);

            // 2. Lớp khung viền tiêu điểm sắc nét (Crisp Focus Frame):
            // Viền tương phản cao ôm sát mép ô cờ, định hình rõ nét ô đích mà tâm hoàn toàn thông thoáng.
            float strokeWidth = Math.max(2.2f, sqSize * 0.032f);
            float strokeInset = strokeWidth / 2f + 1f;
            squareRectF.set(left + strokeInset, top + strokeInset, right - strokeInset, bottom - strokeInset);

            squareBorderPaint.setColor(themeColor);
            squareBorderPaint.setStrokeWidth(strokeWidth);
            squareBorderPaint.setAlpha(235);
            canvas.drawRoundRect(squareRectF, cornerRadius, cornerRadius, squareBorderPaint);

        } else {
            // ==============================================================
            // Ô XUẤT PHÁT (Origin Square): QUÂN CỜ ĐÃ RỜI ĐI (Ô CỜ ĐANG TRỐNG)
            // ==============================================================
            // Ô này hoàn toàn trống (không có quân cờ đứng), chỉ điểm nhẹ nền mờ dịu mắt (18 / 255)
            // kèm viền nét đứt tinh tế thể hiện vị trí quân cờ vừa xuất phát đi.

            // 1. Nền mờ siêu nhẹ đánh dấu vị trí xuất phát
            squareHighlightPaint.setColor(themeColor);
            squareHighlightPaint.setAlpha(18);
            canvas.drawRect(left, top, right, bottom, squareHighlightPaint);

            // 2. Viền nét đứt thanh mảnh (Dashed Origin Frame)
            if (cachedDashEffect == null || Math.abs(sqSize - lastDashSqSize) > 0.5f) {
                float dashLen = sqSize * 0.12f;
                float dashGap = sqSize * 0.08f;
                cachedDashEffect = new DashPathEffect(new float[]{dashLen, dashGap}, 0f);
                lastDashSqSize = sqSize;
            }

            squareDashedPaint.setColor(themeColor);
            squareDashedPaint.setStrokeWidth(Math.max(1.8f, sqSize * 0.024f));
            squareDashedPaint.setAlpha(130);
            squareDashedPaint.setPathEffect(cachedDashEffect);

            float strokeInset = squareDashedPaint.getStrokeWidth() / 2f + 1f;
            squareRectF.set(left + strokeInset, top + strokeInset, right - strokeInset, bottom - strokeInset);
            canvas.drawRoundRect(squareRectF, cornerRadius, cornerRadius, squareDashedPaint);
        }
    }

    private void drawSingleClassificationBadge(Canvas canvas, float sqSize, ClassificationBadgeData badge) {
        if (badge == null || badge.toSquare == null || badge.toSquare.length() < 2) {
            return;
        }

        String toSq = badge.toSquare;
        int file = toSq.charAt(0) - 'a';
        int rank = toSq.charAt(1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) return;

        int col = flipped ? (7 - file) : file;
        int row = flipped ? rank : (7 - rank);

        float left = col * sqSize;
        float top = row * sqSize;

        int themeColor = getClassificationThemeColor(badge.classificationName);
        String lower = badge.classificationName != null ? badge.classificationName.toLowerCase(java.util.Locale.US).replace(" ", "_") : "";

        // 1. Highlight ô cờ thanh lịch chuẩn Extension (Khung viền kép không lem màu quân cờ)
        // Ô đích: Tâm ô cờ hoàn toàn trong suốt 100% không phủ đè màu lên quân cờ Chess.com.
        // Ô xuất phát: Viền nét đứt thanh mảnh kèm nền siêu nhẹ đánh dấu điểm khởi hành.
        boolean isForced = "forced".equals(lower);
        boolean isBrilliantOrGreat = lower.contains("brilliant") || lower.contains("great");

        if (!isForced) {
            if (badge.fromSquare != null && badge.fromSquare.length() >= 2) {
                drawSquareHighlight(canvas, sqSize, badge.fromSquare, themeColor, false, false);
            }
            drawSquareHighlight(canvas, sqSize, toSq, themeColor, true, isBrilliantOrGreat);
        }

        // 2. Tính toán Pop-in / Scale Animation & Brilliant Celebration Effects (800ms)
        long elapsed = android.os.SystemClock.uptimeMillis() - badge.timestamp;
        float scale = 1.0f;
        if (elapsed < 250) {
            float progress = (float) elapsed / 250f;
            // Overshoot interpolator: nở ra 1.15 rồi thu về 1.0
            if (progress < 0.6f) {
                scale = 0.3f + (0.85f * (progress / 0.6f)); // 0.3 -> 1.15
            } else {
                scale = 1.15f - (0.15f * ((progress - 0.6f) / 0.4f)); // 1.15 -> 1.0
            }
            postInvalidateOnAnimation();
        } else if (isBrilliantOrGreat && elapsed < 800) {
            postInvalidateOnAnimation();
        }

        // 4. Kích thước & Vị trí huy hiệu Chess.com (góc trên bên phải ô đích, căn chỉnh an toàn không tràn viền)
        float baseBadgeSize = sqSize * 0.35f;
        float badgeSize = baseBadgeSize * scale;

        // Tâm của huy hiệu tại góc trên bên phải ô đích
        float targetCenterX = left + sqSize - baseBadgeSize / 2f - sqSize * 0.04f;
        float targetCenterY = top + baseBadgeSize / 2f + sqSize * 0.04f;

        float badgeX = targetCenterX - badgeSize / 2f;
        float badgeY = targetCenterY - badgeSize / 2f;

        // Giới hạn tuyệt đối trong kích thước bàn cờ (tránh bị cắt mép trên/dưới/trái/phải)
        int bw = getWidth();
        int bh = getHeight();
        if (bw > 0 && bh > 0) {
            badgeX = Math.max(2f, Math.min(bw - badgeSize - 2f, badgeX));
            badgeY = Math.max(2f, Math.min(bh - badgeSize - 2f, badgeY));
        }

        // 5. Hiệu ứng đồ họa độc quyền khi có nước đi Brilliant / Great Move (Expanding Ripple & Sparkles)
        if (isBrilliantOrGreat && elapsed < 800) {
            drawBrilliantCelebrationEffects(canvas, sqSize, targetCenterX, targetCenterY, left, top, elapsed, lower.contains("brilliant"));
        }

        // Thử lấy Drawable vector gốc từ APK Chess.com
        Drawable nativeDrawable = getClassificationDrawable(getContext(), badge.classificationName);

        if (nativeDrawable != null) {
            // Bóng đổ tròn mờ (Extension chuẩn opacity 0.3 = 0x4D000000)
            badgeCirclePaint.setStyle(Paint.Style.FILL);
            badgeCirclePaint.setColor(0x4D000000);
            canvas.drawCircle(badgeX + badgeSize / 2f, badgeY + badgeSize / 2f + 1.5f * scale, badgeSize / 2f, badgeCirclePaint);

            nativeDrawable.setBounds((int) badgeX, (int) badgeY, (int) (badgeX + badgeSize), (int) (badgeY + badgeSize));
            nativeDrawable.draw(canvas);
        } else {
            // Fallback đồ hoạ bo tròn glyph
            drawFallbackBadge(canvas, badgeX, badgeY, badgeSize, badge.classificationName);
        }
    }

    private void drawBrilliantCelebrationEffects(Canvas canvas, float sqSize, float badgeCenterX, float badgeCenterY,
                                                 float squareLeft, float squareTop, long elapsed, boolean isBrilliant) {
        float animProgress = Math.min(1.0f, (float) elapsed / 800f);

        // 1. Sóng năng lượng hào quang tỏa rộng (Expanding Radiant Ripples)
        float maxRippleRadius = sqSize * (isBrilliant ? 0.65f : 0.50f);
        float baseBadgeRadius = sqSize * 0.175f;

        // Vòng sóng 1
        float ripple1Radius = baseBadgeRadius + (maxRippleRadius - baseBadgeRadius) * animProgress;
        int ripple1Alpha = (int) (220 * (1.0f - animProgress));
        if (ripple1Alpha > 0) {
            brilliantCelebrationPaint.setColor(isBrilliant ? 0xFF26C2A3 : 0xFF749BBF);
            brilliantCelebrationPaint.setAlpha(ripple1Alpha);
            brilliantCelebrationPaint.setStrokeWidth(Math.max(2.0f, sqSize * 0.035f * (1.0f - animProgress * 0.5f)));
            canvas.drawCircle(badgeCenterX, badgeCenterY, ripple1Radius, brilliantCelebrationPaint);
        }

        // Vòng sóng 2 (trễ hơn 1 chút)
        if (elapsed > 120) {
            float progress2 = Math.min(1.0f, (float) (elapsed - 120) / 680f);
            float ripple2Radius = baseBadgeRadius + (maxRippleRadius * 1.15f - baseBadgeRadius) * progress2;
            int ripple2Alpha = (int) (160 * (1.0f - progress2));
            if (ripple2Alpha > 0) {
                brilliantCelebrationPaint.setColor(isBrilliant ? 0xFF64FFDA : 0xFFA5D8FF);
                brilliantCelebrationPaint.setAlpha(ripple2Alpha);
                brilliantCelebrationPaint.setStrokeWidth(Math.max(1.5f, sqSize * 0.025f * (1.0f - progress2 * 0.5f)));
                canvas.drawCircle(badgeCenterX, badgeCenterY, ripple2Radius, brilliantCelebrationPaint);
            }
        }

        // 2. Các hạt tia sáng lấp lánh (Celebratory Sparkle Rays & Starburst)
        if (isBrilliant) {
            float rayLength = sqSize * 0.18f * (1.0f - (float) Math.pow(animProgress - 0.5f, 2) * 4f); // nở rộ ở giữa animation
            if (rayLength > 0.5f) {
                float distFromCenter = baseBadgeRadius * 1.35f + (sqSize * 0.15f * animProgress);
                int rayAlpha = (int) (240 * (1.0f - animProgress));
                brilliantCelebrationPaint.setColor(0xFFFFFFFF);
                brilliantCelebrationPaint.setAlpha(Math.max(0, rayAlpha));
                brilliantCelebrationPaint.setStrokeWidth(Math.max(1.8f, sqSize * 0.02f));

                // 6 tia sáng phát ra xung quanh huy hiệu theo các góc 30, 90, 150, 210, 270, 330 độ
                for (int i = 0; i < 6; i++) {
                    double angle = Math.toRadians(30.0 + i * 60.0);
                    float startX = badgeCenterX + (float) (Math.cos(angle) * distFromCenter);
                    float startY = badgeCenterY + (float) (Math.sin(angle) * distFromCenter);
                    float endX = badgeCenterX + (float) (Math.cos(angle) * (distFromCenter + rayLength));
                    float endY = badgeCenterY + (float) (Math.sin(angle) * (distFromCenter + rayLength));
                    canvas.drawLine(startX, startY, endX, endY, brilliantCelebrationPaint);
                }
            }

            // Hào quang vàng óng / kim cương siêu thực trên ô cờ (Corner Shimmer Accent)
            float shimmerCornerInset = sqSize * 0.06f;
            float shimmerCornerSize = sqSize * 0.14f * (1.0f - animProgress);
            int shimmerAlpha = (int) (180 * (1.0f - animProgress));
            if (shimmerAlpha > 0) {
                brilliantCelebrationPaint.setColor(0xFFE0F7FA);
                brilliantCelebrationPaint.setAlpha(shimmerAlpha);
                brilliantCelebrationPaint.setStrokeWidth(Math.max(2.2f, sqSize * 0.028f));
                // Góc trên trái ô cờ
                canvas.drawLine(squareLeft + shimmerCornerInset, squareTop + shimmerCornerInset,
                        squareLeft + shimmerCornerInset + shimmerCornerSize, squareTop + shimmerCornerInset, brilliantCelebrationPaint);
                canvas.drawLine(squareLeft + shimmerCornerInset, squareTop + shimmerCornerInset,
                        squareLeft + shimmerCornerInset, squareTop + shimmerCornerInset + shimmerCornerSize, brilliantCelebrationPaint);
            }
        }
    }

    public static Drawable getClassificationDrawable(Context context, String classificationName) {
        if (context == null || classificationName == null) return null;
        String resName;
        String lower = classificationName.toLowerCase(java.util.Locale.US).replace(" ", "_");
        switch (lower) {
            case "brilliant":
                resName = "move_classification_classification_brilliant";
                break;
            case "great":
            case "greatfind":
            case "great_find":
                resName = "move_classification_classification_great_find";
                break;
            case "best":
                resName = "move_classification_classification_best";
                break;
            case "excellent":
                resName = "move_classification_classification_excellent";
                break;
            case "good":
                resName = "move_classification_classification_good";
                break;
            case "book":
                resName = "move_classification_classification_book";
                break;
            case "inaccuracy":
                resName = "move_classification_classification_inaccuracy";
                break;
            case "mistake":
                resName = "move_classification_classification_mistake";
                break;
            case "blunder":
                resName = "move_classification_classification_blunder";
                break;
            case "miss":
            case "missed":
                resName = "move_classification_classification_miss";
                break;
            case "missedwin":
            case "missed_win":
                resName = "move_classification_classification_missed_win";
                break;
            case "forced":
                resName = "move_classification_classification_forced";
                break;
            default:
                resName = "move_classification_classification_good";
                break;
        }

        try {
            int resId = context.getResources().getIdentifier(resName, "drawable", context.getPackageName());
            if (resId != 0) {
                if (Build.VERSION.SDK_INT >= 21) {
                    Drawable d = context.getDrawable(resId);
                    return d != null ? d.mutate() : null;
                }
            }
        } catch (Throwable t) {
            Log.w("NNVC", "Failed to load drawable " + resName + ": " + t.getMessage());
        }
        return null;
    }

    private void drawFallbackBadge(Canvas canvas, float x, float y, float size, String classificationName) {
        int bgColor;
        String glyph;
        String lower = classificationName != null ? classificationName.toLowerCase(java.util.Locale.US).replace(" ", "_") : "";
        switch (lower) {
            case "brilliant":
                bgColor = 0xFF26C2A3; // Cyan Teal
                glyph = "!!";
                break;
            case "great":
            case "greatfind":
            case "great_find":
                bgColor = 0xFF749BBF; // Blue Teal
                glyph = "!";
                break;
            case "best":
                bgColor = 0xFF81B64C; // Chess.com Green
                glyph = "★";
                break;
            case "excellent":
                bgColor = 0xFF81B64C;
                glyph = "👍";
                break;
            case "good":
                bgColor = 0xFF95B776;
                glyph = "✓";
                break;
            case "book":
                bgColor = 0xFFD5A47D;
                glyph = "📖";
                break;
            case "inaccuracy":
                bgColor = 0xFFF7C631;
                glyph = "?!";
                break;
            case "mistake":
                bgColor = 0xFFFFA459;
                glyph = "?";
                break;
            case "blunder":
                bgColor = 0xFFFA412D;
                glyph = "??";
                break;
            case "miss":
            case "missed":
                bgColor = 0xFFFF7769;
                glyph = "✕";
                break;
            case "missedwin":
            case "missed_win":
                bgColor = 0xFFF7C631;
                glyph = "✕";
                break;
            case "forced":
                bgColor = 0xFF999999;
                glyph = "➔";
                break;
            default:
                bgColor = 0xFF81B64C;
                glyph = "✓";
                break;
        }

        float radius = size / 2.0f;
        float cx = x + radius;
        float cy = y + radius;

        // Vẽ nền tròn
        badgeCirclePaint.setStyle(Paint.Style.FILL);
        badgeCirclePaint.setColor(bgColor);
        canvas.drawCircle(cx, cy, radius, badgeCirclePaint);

        // Viền trắng mỏng
        badgeCirclePaint.setStyle(Paint.Style.STROKE);
        badgeCirclePaint.setColor(0xFFFFFFFF);
        badgeCirclePaint.setStrokeWidth(Math.max(1.5f, size * 0.08f));
        canvas.drawCircle(cx, cy, radius, badgeCirclePaint);

        // Vẽ text glyph
        badgeTextPaint2.setColor(0xFFFFFFFF);
        badgeTextPaint2.setTextSize(size * 0.55f);
        badgeTextPaint2.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        badgeTextPaint2.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = badgeTextPaint2.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(glyph, cx, textY, badgeTextPaint2);
    }
}
