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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        public final String threatType; // "danger" hoặc "attack"
        public final int threatDepth;   // 1 (trực tiếp)
        public final boolean threatAttackerIsWhite; // true: quân Trắng ra đòn, false: quân Đen ra đòn

        public ArrowData(String move, int tier, boolean isThreat) {
            this(move, tier, isThreat, false, null, "danger", 1, false);
        }

        public ArrowData(String move, int tier, boolean isThreat, String evalText) {
            this(move, tier, isThreat, false, evalText, "danger", 1, false);
        }

        public ArrowData(String move, int tier, boolean isThreat, boolean isPremove, String evalText) {
            this(move, tier, isThreat, isPremove, evalText, "danger", 1, false);
        }

        public ArrowData(String move, int tier, boolean isThreat, boolean isPremove, String evalText, String threatType, int threatDepth) {
            this(move, tier, isThreat, isPremove, evalText, threatType, threatDepth, false);
        }

        public ArrowData(String move, int tier, boolean isThreat, boolean isPremove, String evalText, String threatType, int threatDepth, boolean threatAttackerIsWhite) {
            this.move = move;
            this.from = move.substring(0, 2);
            this.to = move.substring(2, 4);
            this.tier = tier;
            this.isThreat = isThreat;
            this.isPremove = isPremove;
            this.evalText = evalText;
            this.threatType = threatType != null ? threatType : "danger";
            this.threatDepth = threatDepth > 0 ? threatDepth : 1;
            this.threatAttackerIsWhite = threatAttackerIsWhite;
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
    private float lastDashSqSize = -1f;
    private DashPathEffect cachedDashEffect = null;

    private float[] cachedPerpOffsets = new float[0];
    private float[] cachedTargetOffsets = new float[0];
    private float[] cachedStartOffsets = new float[0];
    private float lastSqSizeForOffsets = -1f;
    private int lastArrowsHash = 0;

    private final Path reusableArrowPath = new Path();
    private final Path reusableThreatHeadPath = new Path();
    private final Path reusableKnightLinePath = new Path();
    private final Path reusableKnightHeadPath = new Path();
    private final RectF reusablePillRectF = new RectF();
    private BlurMaskFilter cachedPillBlurFilter = null;
    private float cachedPillBlurRadius = -1f;

    private float lastThreatDashSqSize = -1f;
    private DashPathEffect cachedThreatDepth2DashEffect = null;
    private DashPathEffect cachedThreatNonDangerDashEffect = null;

    private LinearGradient cachedPremoveShader = null;
    private float cachedPremoveX1, cachedPremoveY1, cachedPremoveX2, cachedPremoveY2;

    private static class ShaderSlot {
        float x1, y1, x2, y2;
        int c1, c2, c3;
        LinearGradient shader;
        boolean matches(float x1, float y1, float x2, float y2, int c1, int c2, int c3) {
            return shader != null && this.x1 == x1 && this.y1 == y1 && this.x2 == x2 && this.y2 == y2
                    && this.c1 == c1 && this.c2 == c2 && this.c3 == c3;
        }
        void set(float x1, float y1, float x2, float y2, int c1, int c2, int c3, LinearGradient s) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.c1 = c1; this.c2 = c2; this.c3 = c3;
            this.shader = s;
        }
    }
    private final ShaderSlot[] cachedFillShaders = new ShaderSlot[8];
    private final ShaderSlot[] cachedEdgeShaders = new ShaderSlot[8];

    private static final String[] BOARD_SQUARES = new String[64];
    static {
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                BOARD_SQUARES[r * 8 + f] = "" + (char) ('a' + f) + (char) ('1' + r);
            }
        }
    }
    private final float[][] squareCenters = new float[64][2];
    private float lastCenterSqSize = -1f;
    private boolean lastCenterFlipped = false;

    private final String[] corridorSq = new String[32];
    private final char[] corridorAxis = new char[32];
    private int corridorCount = 0;

    private final float[][] placedBoxes = new float[16][4]; // x, y, w, h
    private int placedBoxCount = 0;

    private final float[][] candidateCoords = new float[12][2];

    public ArrowOverlayView(Context context) {
        super(context);
        for (int i = 0; i < 8; i++) {
            cachedFillShaders[i] = new ShaderSlot();
            cachedEdgeShaders[i] = new ShaderSlot();
        }
        setClickable(false);
        setFocusable(false);
        try {
            setElevation(2.0f);
        } catch (Throwable ignored) {}

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
        if (currentBadge != null) {
            drawSingleClassificationBadge(canvas, sqSize, currentBadge);
        }

        // 2. Draw arrows with authentic NNVC Desktop Extension Anti-Collision & Symmetric Lane Separation
        if (!arrows.isEmpty()) {
            int n = arrows.size();
            int currentHash = 0;
            for (int i = 0; i < n; i++) {
                ArrowData a = arrows.get(i);
                currentHash = 31 * currentHash + a.from.hashCode() * 17 + a.to.hashCode() + (a.isThreat ? 1 : 0);
            }
            if (cachedPerpOffsets.length != n || lastSqSizeForOffsets != sqSize || lastArrowsHash != currentHash) {
                cachedPerpOffsets = new float[n];
                cachedTargetOffsets = new float[n];
                cachedStartOffsets = new float[n];
                computeExtensionOffsets(this.arrows, this.flipped, sqSize, cachedPerpOffsets, cachedTargetOffsets, cachedStartOffsets);
                lastSqSizeForOffsets = sqSize;
                lastArrowsHash = currentHash;
            }
            float[] perpOffsets = cachedPerpOffsets;
            float[] targetOffsets = cachedTargetOffsets;
            float[] startOffsets = cachedStartOffsets;

            // Pass 0: Highlight ô cờ bị đe dọa (Threat Square Warning Aura)
            // QUY TẮC BẢO TOÀN SPRITE: TÂM Ô CỜ HOÀN TOÀN TRONG SUỐT (ZERO FILL)!
            // Tuyệt đối không vẽ lớp nền đặc lên tâm ô cờ để bảo toàn 100% sprite quân cờ bên dưới.
            for (int i = 0; i < n; i++) {
                ArrowData arrow = arrows.get(i);
                if (arrow.isThreat) {
                    boolean alreadyDrawn = false;
                    for (int prev = 0; prev < i; prev++) {
                        if (arrows.get(prev).isThreat && arrow.to.equals(arrows.get(prev).to)) {
                            alreadyDrawn = true;
                            break;
                        }
                    }
                    if (!alreadyDrawn) {
                        boolean isDanger = (arrow.threatType == null) || "danger".equalsIgnoreCase(arrow.threatType);
                        drawThreatSquareHighlight(canvas, sqSize, arrow.to, isDanger);
                    }
                }
            }

            // Pass 1: Mũi tên Đe Dọa (Threat Arrows - Chuẩn 100% SVG Cyber Line Desktop Extension)
            for (int i = 0; i < n; i++) {
                ArrowData arrow = arrows.get(i);
                if (arrow.isThreat) {
                    drawThreatArrow(canvas, arrow, sqSize, perpOffsets[i], targetOffsets[i], startOffsets[i]);
                }
            }

            // Pass 2: Mũi tên Premove của người chơi (Cam hổ phách neon)
            for (int i = 0; i < n; i++) {
                ArrowData arrow = arrows.get(i);
                if (arrow.isPremove) {
                    drawSingleArrow(canvas, arrow, sqSize, perpOffsets[i], targetOffsets[i], startOffsets[i]);
                }
            }

            // Pass 3: Mũi tên Engine (Tier 5 -> Tier 4 -> Tier 3 -> Tier 2 -> Tier 1)
            // Vẽ theo thứ tự giảm dần: Tier 1 (Nước Tốt Nhất / Best Move) LUÔN NẰM TRÊN CÙNG, KHÔNG BỊ BẤT KỲ MŨI TÊN NÀO CHE LẤP!
            for (int tier = 5; tier >= 1; tier--) {
                for (int i = 0; i < n; i++) {
                    ArrowData arrow = arrows.get(i);
                    if (!arrow.isThreat && !arrow.isPremove && arrow.tier == tier) {
                        drawSingleArrow(canvas, arrow, sqSize, perpOffsets[i], targetOffsets[i], startOffsets[i]);
                    }
                }
            }

            // Pass 4: Draw Eval Badges with Anti-collision avoidance synchronized with arrows (Lớp trên cùng)
            drawEvalBadges(canvas, sqSize);
        }
    }

    /**
     * Authentic Port of NNVC Desktop Extension Multi-Lane Separation & 100% Conflict Avoidance.
     * Computes symmetrical perpOffset, stepped targetOffset, and staggered startOffset so concurrent arrows:
     * - Sharing the same path, opposite directions, or collinear rays (lineMap)
     * - Targeting the same destination (targetMap)
     * - Originating from the same square (fromMap - Nan quạt xuất phát)
     * - Crossing / intersecting each other (segmentsIntersect yielding)
     * NEVER overlap, clash tails, or obscure one another.
     */
    void computeExtensionOffsets(float sqSize, float[] perpOffsets, float[] targetOffsets, float[] startOffsets) {
        computeExtensionOffsets(this.arrows, this.flipped, sqSize, perpOffsets, targetOffsets, startOffsets);
    }

    static void computeExtensionOffsets(List<ArrowData> arrows, boolean flipped, float sqSize,
                                        float[] perpOffsets, float[] targetOffsets, float[] startOffsets) {
        int n = arrows != null ? arrows.size() : 0;
        if (n == 0) return;

        Map<String, List<Integer>> lineMap = new HashMap<>();
        Map<String, List<Integer>> targetMap = new HashMap<>();
        Map<String, List<Integer>> fromMap = new HashMap<>();

        for (int i = 0; i < n; i++) {
            ArrowData a = arrows.get(i);
            String f = a.from, t = a.to;
            if (f == null || t == null) continue;
            String pairKey = f.compareTo(t) < 0 ? (f + "-" + t) : (t + "-" + f);
            if (!lineMap.containsKey(pairKey)) lineMap.put(pairKey, new ArrayList<Integer>());
            lineMap.get(pairKey).add(i);

            if (!targetMap.containsKey(t)) targetMap.put(t, new ArrayList<Integer>());
            targetMap.get(t).add(i);

            if (!fromMap.containsKey(f)) fromMap.put(f, new ArrayList<Integer>());
            fromMap.get(f).add(i);
        }

        // 1. Tách làn theo cặp ô đối đầu hoặc cùng hướng (Line Separation)
        for (Map.Entry<String, List<Integer>> entry : lineMap.entrySet()) {
            List<Integer> group = entry.getValue();
            if (group.size() <= 1) continue;

            float step = Math.min(sqSize * 0.14f, (sqSize * 0.28f) / group.size());
            float mid = (group.size() - 1) / 2.0f;
            for (int k = 0; k < group.size(); k++) {
                int idx = group.get(k);
                ArrowData a = arrows.get(idx);
                float po = (k - mid) * step;
                if (a.from.compareTo(a.to) > 0) po = -po;
                if (a.isThreat) {
                    po = Math.abs(po) < 1e-4f ? (sqSize * 0.15f) : (po > 0 ? po : -po);
                }
                perpOffsets[idx] = po;
            }
        }

        // 2. Tách và lùi bậc khi trùng ô đích (Same Destination Target Clearance - Triệt tiêu đè mũi tên)
        for (Map.Entry<String, List<Integer>> entry : targetMap.entrySet()) {
            List<Integer> group = entry.getValue();
            if (group.size() <= 1) continue;

            int stepOrder = 0;
            for (int idx : group) {
                ArrowData a = arrows.get(idx);
                if (a.isThreat) {
                    // Mũi tên đe dọa lùi đầu mũi tên lại để nhìn thấy trọn vẹn cả đòn đe dọa và nước gợi ý
                    targetOffsets[idx] += sqSize * 0.22f;
                    perpOffsets[idx] += sqSize * 0.12f;
                } else if (a.tier > 1) {
                    stepOrder++;
                    targetOffsets[idx] += stepOrder * (sqSize * 0.16f);
                    perpOffsets[idx] -= (stepOrder % 2 == 1 ? 1 : -1) * (sqSize * 0.10f);
                } else {
                    // Tier 1 Best Move giữ nguyên ô đích cờ
                    perpOffsets[idx] -= sqSize * 0.10f;
                }
            }
        }

        // 3. Tách đuôi hình nan quạt khi xuất phát cùng ô cờ (Same Origin Fan-Out Clearance)
        for (Map.Entry<String, List<Integer>> entry : fromMap.entrySet()) {
            List<Integer> group = entry.getValue();
            if (group.size() <= 1) continue;

            // Sắp xếp in-place: Tier 1 đứng trước, kế đến Tier 2, Tier 3, Threats
            for (int a = 1; a < group.size(); a++) {
                int keyIdx = group.get(a);
                ArrowData keyArrow = arrows.get(keyIdx);
                int keyPrio = keyArrow.isThreat ? 10 : (keyArrow.isPremove ? 9 : keyArrow.tier);
                int b = a - 1;
                while (b >= 0) {
                    int compIdx = group.get(b);
                    ArrowData compArrow = arrows.get(compIdx);
                    int compPrio = compArrow.isThreat ? 10 : (compArrow.isPremove ? 9 : compArrow.tier);
                    if (compPrio > keyPrio) {
                        group.set(b + 1, compIdx);
                        b--;
                    } else {
                        break;
                    }
                }
                group.set(b + 1, keyIdx);
            }

            int primaryIdx = group.get(0);
            ArrowData primaryArrow = arrows.get(primaryIdx);
            float pFromX = getSqCenterX(primaryArrow.from, flipped, sqSize);
            float pFromY = getSqCenterY(primaryArrow.from, flipped, sqSize);
            float pToX   = getSqCenterX(primaryArrow.to, flipped, sqSize);
            float pToY   = getSqCenterY(primaryArrow.to, flipped, sqSize);

            for (int k = 1; k < group.size(); k++) {
                int secIdx = group.get(k);
                ArrowData secArrow = arrows.get(secIdx);

                // Lùi điểm xuất phát ra xa tâm ô cờ để không đè lên đuôi của mũi tên trước
                startOffsets[secIdx] += k * (sqSize * 0.12f);

                float sToX = getSqCenterX(secArrow.to, flipped, sqSize);
                float sToY = getSqCenterY(secArrow.to, flipped, sqSize);
                float u0x = pToX - pFromX, u0y = pToY - pFromY;
                float u1x = sToX - pFromX, u1y = sToY - pFromY;
                float cross = u0x * u1y - u0y * u1x;

                // Tách theo nan quạt góc (V-shape fan-out)
                if (Math.abs(cross) > 1e-3f) {
                    float fanDir = cross > 0 ? 1.0f : -1.0f;
                    perpOffsets[secIdx] += fanDir * (sqSize * 0.08f);
                } else {
                    // Cùng hướng dọc/ngang (collinear)
                    perpOffsets[secIdx] += (k % 2 == 1 ? 1.0f : -1.0f) * (sqSize * 0.07f);
                }
            }
        }

        // 4. Nhường đường khi các mũi tên cắt ngang nhau (Crossing Arrows Yielding)
        for (int i = 0; i < n; i++) {
            ArrowData ai = arrows.get(i);
            float a1x = getSqCenterX(ai.from, flipped, sqSize);
            float a1y = getSqCenterY(ai.from, flipped, sqSize);
            float a2x = getSqCenterX(ai.to, flipped, sqSize);
            float a2y = getSqCenterY(ai.to, flipped, sqSize);

            for (int j = i + 1; j < n; j++) {
                ArrowData aj = arrows.get(j);
                if (ai.from.equals(aj.from) || ai.to.equals(aj.to) ||
                    ai.from.equals(aj.to) || ai.to.equals(aj.from)) {
                    continue; // Đã được xử lý ở lineMap, targetMap, fromMap
                }

                float b1x = getSqCenterX(aj.from, flipped, sqSize);
                float b1y = getSqCenterY(aj.from, flipped, sqSize);
                float b2x = getSqCenterX(aj.to, flipped, sqSize);
                float b2y = getSqCenterY(aj.to, flipped, sqSize);

                if (segmentsIntersect(a1x, a1y, a2x, a2y, b1x, b1y, b2x, b2y)) {
                    int prioI = ai.isThreat ? 10 : (ai.isPremove ? 9 : ai.tier);
                    int prioJ = aj.isThreat ? 10 : (aj.isPremove ? 9 : aj.tier);

                    int lowerIdx = (prioI > prioJ) ? i : j;
                    int higherIdx = (prioI > prioJ) ? j : i;
                    ArrowData higher = arrows.get(higherIdx);

                    // Mũi tên bậc thấp hơn lùi lại một khoảng targetOffset và dạt sang một bên
                    targetOffsets[lowerIdx] += sqSize * 0.16f;

                    float hFx = getSqCenterX(higher.from, flipped, sqSize);
                    float hFy = getSqCenterY(higher.from, flipped, sqSize);
                    float hTx = getSqCenterX(higher.to, flipped, sqSize);
                    float hTy = getSqCenterY(higher.to, flipped, sqSize);

                    ArrowData lower = arrows.get(lowerIdx);
                    float lTx = getSqCenterX(lower.to, flipped, sqSize);
                    float lTy = getSqCenterY(lower.to, flipped, sqSize);

                    float crossHL = (hTx - hFx) * (lTy - hFy) - (hTy - hFy) * (lTx - hFx);
                    float pushDir = crossHL >= 0 ? 1.0f : -1.0f;
                    perpOffsets[lowerIdx] += pushDir * (sqSize * 0.07f);
                }
            }
        }
    }

    static float getSqCenterX(String sq, boolean flipped, float sqSize) {
        if (sq == null || sq.length() < 2) return 0f;
        int file = sq.charAt(0) - 'a';
        int col = flipped ? (7 - file) : file;
        return col * sqSize + sqSize / 2.0f;
    }

    static float getSqCenterY(String sq, boolean flipped, float sqSize) {
        if (sq == null || sq.length() < 2) return 0f;
        int rank = sq.charAt(1) - '1';
        int row = flipped ? rank : (7 - rank);
        return row * sqSize + sqSize / 2.0f;
    }

    static boolean segmentsIntersect(float a1x, float a1y, float a2x, float a2y,
                                     float b1x, float b1y, float b2x, float b2y) {
        float d1 = ccw(b1x, b1y, b2x, b2y, a1x, a1y);
        float d2 = ccw(b1x, b1y, b2x, b2y, a2x, a2y);
        float d3 = ccw(a1x, a1y, a2x, a2y, b1x, b1y);
        float d4 = ccw(a1x, a1y, a2x, a2y, b2x, b2y);

        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
               ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    static float ccw(float p1x, float p1y, float p2x, float p2y, float p3x, float p3y) {
        return (p2x - p1x) * (p3y - p1y) - (p2y - p1y) * (p3x - p1x);
    }

    static float distToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        float l2 = dx * dx + dy * dy;
        if (l2 < 1e-4f) return (float) Math.hypot(px - x1, py - y1);
        float t = Math.max(0.0f, Math.min(1.0f, ((px - x1) * dx + (py - y1) * dy) / l2));
        float projX = x1 + t * dx;
        float projY = y1 + t * dy;
        return (float) Math.hypot(px - projX, py - projY);
    }

    /**
     * Authentic 100% Port of NNVC Desktop Extension SVG Threat Arrow (nnvc_main_bundle.js lines 14247-14376):
     * - Cyber Laser Line: lineWidth = 0.052 * sqSize with 2-pass GPU Neon Glow
     * - Sharp Triangle Arrowhead: headLen = 0.22 * sqSize, headWidth = 0.17 * sqSize
     * - High-contrast White Border on Triangle (1.1dp stroke)
     * - Pure Danger Red #EF4444 with rgba(239, 68, 68, 0.45) glow aura
     * - Clear Piece Visibility: startOffset = 0.22 * sqSize, endOffset = 0.18 * sqSize
     * - L-Shape Knight elbow with round caps
     */
    private void drawThreatArrow(Canvas canvas, ArrowData arrow, float sqSize, float perpOffset, float targetOffset, float customStartOffset) {
        float[] fromCenter = getSquareCenter(arrow.from, flipped, sqSize);
        float[] toCenter   = getSquareCenter(arrow.to,   flipped, sqSize);
        if (fromCenter == null || toCenter == null) return;

        float x1 = fromCenter[0], y1 = fromCenter[1];
        float x2 = toCenter[0],   y2 = toCenter[1];
        float dx = x2 - x1, dy = y2 - y1;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 2.0f) return;

        int fileDelta = Math.abs(arrow.to.charAt(0) - arrow.from.charAt(0));
        int rankDelta = Math.abs(arrow.to.charAt(1) - arrow.from.charAt(1));
        boolean isKnight = (fileDelta == 1 && rankDelta == 2) || (fileDelta == 2 && rankDelta == 1);

        float headLen = sqSize * 0.22f;
        float headWidth = sqSize * 0.17f;
        float lineWidth = sqSize * 0.052f;
        float startOffset = sqSize * 0.22f + customStartOffset;
        float endOffset = sqSize * 0.18f + targetOffset;

        boolean isDanger = (arrow.threatType == null) || "danger".equalsIgnoreCase(arrow.threatType);
        int shaftColor = isDanger ? 0xFFEF4444 : 0xFFF59E0B;
        float opacity = (arrow.threatDepth == 2) ? 0.78f : 0.92f;

        // 1. Quầng sáng Neon Aura (GPU accelerated 2-pass stroke)
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setMaskFilter(null);
        glowPaint.setColor(isDanger ? Color.argb((int)(0.45f * opacity * 255), 239, 68, 68) : Color.argb((int)(0.45f * opacity * 255), 245, 158, 11));
        glowPaint.setStrokeWidth(lineWidth + Math.max(3.2f, sqSize * 0.032f));
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);

        // 2. Thân Cyber Laser Line
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setShader(null);
        edgePaint.setColor(shaftColor);
        edgePaint.setStrokeWidth(lineWidth);
        edgePaint.setStrokeCap(Paint.Cap.ROUND);
        edgePaint.setStrokeJoin(Paint.Join.ROUND);
        edgePaint.setAlpha((int)(opacity * 255));

        if (arrow.threatDepth == 2) {
            if (cachedThreatDepth2DashEffect == null || Math.abs(lastThreatDashSqSize - sqSize) > 0.01f) {
                cachedThreatDepth2DashEffect = new DashPathEffect(new float[]{sqSize * 0.06f, sqSize * 0.03f}, 0);
                lastThreatDashSqSize = sqSize;
            }
            edgePaint.setPathEffect(cachedThreatDepth2DashEffect);
        } else if (!isDanger) {
            if (cachedThreatNonDangerDashEffect == null || Math.abs(lastThreatDashSqSize - sqSize) > 0.01f) {
                cachedThreatNonDangerDashEffect = new DashPathEffect(new float[]{sqSize * 0.05f, sqSize * 0.025f}, 0);
                lastThreatDashSqSize = sqSize;
            }
            edgePaint.setPathEffect(cachedThreatNonDangerDashEffect);
        } else {
            edgePaint.setPathEffect(null);
        }

        // 3. Đầu tam giác sắc nét có viền trắng tương phản
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setShader(null);
        fillPaint.setColor(shaftColor);
        fillPaint.setAlpha((int)(opacity * 255));

        if (isKnight) {
            float elbowX = (fileDelta == 2 && rankDelta == 1) ? x2 : x1;
            float elbowY = (fileDelta == 2 && rankDelta == 1) ? y1 : y2;

            float s1dx = elbowX - x1, s1dy = elbowY - y1;
            float s1len = (float) Math.hypot(s1dx, s1dy);
            if (s1len < 1e-3f) s1len = 1.0f;
            float u1x = s1dx / s1len, u1y = s1dy / s1len;
            float p1x = -u1y, p1y = u1x;

            float s2dx = x2 - elbowX, s2dy = y2 - elbowY;
            float s2len = (float) Math.hypot(s2dx, s2dy);
            if (s2len < 1e-3f) s2len = 1.0f;
            float u2x = s2dx / s2len, u2y = s2dy / s2len;
            float p2x = -u2y, p2y = u2x;

            float sx = x1 + u1x * startOffset + p1x * (perpOffset * 0.35f);
            float sy = y1 + u1y * startOffset + p1y * (perpOffset * 0.35f);
            float elbowShiftX = elbowX + p1x * perpOffset;
            float elbowShiftY = elbowY + p1y * perpOffset;

            float tipX = x2 + p2x * perpOffset - u2x * endOffset;
            float tipY = y2 + p2y * perpOffset - u2y * endOffset;
            float headBaseX = tipX - u2x * headLen;
            float headBaseY = tipY - u2y * headLen;

            float leftPtX = headBaseX + p2x * (headWidth / 2.0f);
            float leftPtY = headBaseY + p2y * (headWidth / 2.0f);
            float rightPtX = headBaseX - p2x * (headWidth / 2.0f);
            float rightPtY = headBaseY - p2y * (headWidth / 2.0f);

            reusableKnightLinePath.reset();
            reusableKnightLinePath.moveTo(sx, sy);
            reusableKnightLinePath.lineTo(elbowShiftX, elbowShiftY);
            reusableKnightLinePath.lineTo(headBaseX, headBaseY);

            canvas.drawPath(reusableKnightLinePath, glowPaint);
            canvas.drawPath(reusableKnightLinePath, edgePaint);

            reusableKnightHeadPath.reset();
            reusableKnightHeadPath.moveTo(tipX, tipY);
            reusableKnightHeadPath.lineTo(leftPtX, leftPtY);
            reusableKnightHeadPath.lineTo(rightPtX, rightPtY);
            reusableKnightHeadPath.close();

            canvas.drawPath(reusableKnightHeadPath, fillPaint);

            edgePaint.setColor(0xFFFFFFFF);
            edgePaint.setStrokeWidth(Math.max(1.1f, sqSize * 0.012f));
            edgePaint.setPathEffect(null);
            canvas.drawPath(reusableKnightHeadPath, edgePaint);
        } else {
            float ux = dx / dist;
            float uy = dy / dist;
            float px = -uy;
            float py = ux;
            float shiftX = px * perpOffset;
            float shiftY = py * perpOffset;

            float lineStartX = x1 + ux * startOffset + shiftX;
            float lineStartY = y1 + uy * startOffset + shiftY;
            float tipX = x2 - ux * endOffset + shiftX;
            float tipY = y2 - uy * endOffset + shiftY;
            float headBaseX = tipX - ux * headLen;
            float headBaseY = tipY - uy * headLen;
            float leftPtX = headBaseX + px * (headWidth / 2.0f);
            float leftPtY = headBaseY + py * (headWidth / 2.0f);
            float rightPtX = headBaseX - px * (headWidth / 2.0f);
            float rightPtY = headBaseY - py * (headWidth / 2.0f);

            canvas.drawLine(lineStartX, lineStartY, headBaseX, headBaseY, glowPaint);
            canvas.drawLine(lineStartX, lineStartY, headBaseX, headBaseY, edgePaint);

            reusableThreatHeadPath.reset();
            reusableThreatHeadPath.moveTo(tipX, tipY);
            reusableThreatHeadPath.lineTo(leftPtX, leftPtY);
            reusableThreatHeadPath.lineTo(rightPtX, rightPtY);
            reusableThreatHeadPath.close();

            canvas.drawPath(reusableThreatHeadPath, fillPaint);

            edgePaint.setColor(0xFFFFFFFF);
            edgePaint.setStrokeWidth(Math.max(1.1f, sqSize * 0.012f));
            edgePaint.setPathEffect(null);
            canvas.drawPath(reusableThreatHeadPath, edgePaint);
        }
    }

    /**
     * Highlight ô cờ bị đe dọa (Threat Square Warning Aura).
     * BẢO TOÀN SPRITE QUÂN CỜ: Tâm ô cờ hoàn toàn trong suốt 100% (Zero Fill),
     * không vẽ đè bất kỳ lớp phủ màu nào lên quân cờ để sprite quân cờ giữ trọn nét sắc sảo.
     * Chỉ vẽ quầng sáng ngoài (Ambient Danger Halo) + khung viền tiêu điểm (Focus Frame) + 4 vạch góc cảnh báo (Hazard Corners).
     */
    private void drawThreatSquareHighlight(Canvas canvas, float sqSize, String sq, boolean isDanger) {
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
        int color = isDanger ? 0xFFEF4444 : 0xFFF59E0B;

        // 1. Quầng sáng cảnh báo nguy hiểm ôm sát mép ô (Danger Outer Halo)
        float glowWidth = Math.max(3.8f, sqSize * 0.065f);
        float glowInset = glowWidth / 2f + 1f;
        squareGlowRectF.set(left + glowInset, top + glowInset, right - glowInset, bottom - glowInset);

        squareGlowPaint.setColor(color);
        squareGlowPaint.setStrokeWidth(glowWidth);
        squareGlowPaint.setAlpha(125);
        canvas.drawRoundRect(squareGlowRectF, cornerRadius, cornerRadius, squareGlowPaint);

        // 2. Khung viền sắc nét cảnh báo bên trong (Inner Danger Focus Frame)
        float strokeWidth = Math.max(2.0f, sqSize * 0.030f);
        float strokeInset = strokeWidth / 2f + 1f;
        squareRectF.set(left + strokeInset, top + strokeInset, right - strokeInset, bottom - strokeInset);

        squareBorderPaint.setColor(color);
        squareBorderPaint.setStrokeWidth(strokeWidth);
        squareBorderPaint.setAlpha(235);
        canvas.drawRoundRect(squareRectF, cornerRadius, cornerRadius, squareBorderPaint);

        // 3. Vạch góc cảnh báo nguy hiểm (Tactical Hazard Corner Accents)
        float cornerLen = sqSize * 0.15f;
        squareBorderPaint.setStrokeWidth(Math.max(2.2f, sqSize * 0.036f));
        squareBorderPaint.setColor(0xFFFFFFFF);
        squareBorderPaint.setAlpha(220);

        // Top-left
        canvas.drawLine(left + strokeInset, top + strokeInset, left + strokeInset + cornerLen, top + strokeInset, squareBorderPaint);
        canvas.drawLine(left + strokeInset, top + strokeInset, left + strokeInset, top + strokeInset + cornerLen, squareBorderPaint);
        // Top-right
        canvas.drawLine(right - strokeInset, top + strokeInset, right - strokeInset - cornerLen, top + strokeInset, squareBorderPaint);
        canvas.drawLine(right - strokeInset, top + strokeInset, right - strokeInset, top + strokeInset + cornerLen, squareBorderPaint);
        // Bottom-left
        canvas.drawLine(left + strokeInset, bottom - strokeInset, left + strokeInset + cornerLen, bottom - strokeInset, squareBorderPaint);
        canvas.drawLine(left + strokeInset, bottom - strokeInset, left + strokeInset, bottom - strokeInset - cornerLen, squareBorderPaint);
        // Bottom-right
        canvas.drawLine(right - strokeInset, bottom - strokeInset, right - strokeInset - cornerLen, bottom - strokeInset, squareBorderPaint);
        canvas.drawLine(right - strokeInset, bottom - strokeInset, right - strokeInset, bottom - strokeInset - cornerLen, squareBorderPaint);
    }

    private void drawSingleArrow(Canvas canvas, ArrowData arrow, float sqSize, float perpOffset, float targetOffset, float customStartOffset) {
        float[] fromCenter = getSquareCenter(arrow.from, flipped, sqSize);
        float[] toCenter   = getSquareCenter(arrow.to,   flipped, sqSize);
        if (fromCenter == null || toCenter == null) return;

        float x1 = fromCenter[0], y1 = fromCenter[1];
        float x2 = toCenter[0],   y2 = toCenter[1];

        int fileDelta = Math.abs(arrow.to.charAt(0) - arrow.from.charAt(0));
        int rankDelta = Math.abs(arrow.to.charAt(1) - arrow.from.charAt(1));
        boolean isKnight = (fileDelta == 1 && rankDelta == 2) || (fileDelta == 2 && rankDelta == 1);

        float thicknessScale = 1.0f - 0.06f * (arrow.tier - 1);
        float shaftHalf = sqSize * 0.042f * thicknessScale;
        float neckHalf = shaftHalf;
        float headHalf = sqSize * 0.17f * thicknessScale;
        float headLen = sqSize * 0.24f * thicknessScale;

        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        float startOffset = Math.min(len * 0.40f, sqSize * 0.16f + customStartOffset);
        float edgeStroke = Math.max(1.5f, sqSize * 0.032f);

        Path arrowPath;
        if (isKnight) {
            arrowPath = buildKnightPath(x1, y1, x2, y2, fileDelta, rankDelta,
                    startOffset, shaftHalf, neckHalf, headHalf, headLen, perpOffset, targetOffset, arrow.isThreat);
        } else {
            arrowPath = buildStraightPath(x1, y1, x2, y2, len,
                    startOffset, shaftHalf, neckHalf, headHalf, headLen, perpOffset, targetOffset, arrow.isThreat);
        }
        if (arrowPath == null) return;

        if (arrow.isPremove) {
            // Style riêng biệt cho Mũi Tên Premove (Premove Arrow):
            int premoveColor = 0xFFF59E0B; // Amber Premove
            int premoveDark = 0xFFEA580C;

            // 1. Glow viền cảnh báo (GPU accelerated 2-pass stroke)
            glowPaint.setMaskFilter(null);
            glowPaint.setColor(0x35F59E0B);
            glowPaint.setStrokeWidth(edgeStroke + sqSize * 0.035f);
            canvas.drawPath(arrowPath, glowPaint);

            glowPaint.setColor(0x60EA580C);
            glowPaint.setStrokeWidth(edgeStroke + sqSize * 0.016f);
            canvas.drawPath(arrowPath, glowPaint);

            // 2. Linear Gradient Fill Cam Hổ Phách
            if (cachedPremoveShader == null || cachedPremoveX1 != x1 || cachedPremoveY1 != y1 || cachedPremoveX2 != x2 || cachedPremoveY2 != y2) {
                cachedPremoveShader = new LinearGradient(x1, y1, x2, y2,
                        new int[]{premoveDark, premoveColor},
                        new float[]{0.0f, 1.0f},
                        Shader.TileMode.CLAMP);
                cachedPremoveX1 = x1;
                cachedPremoveY1 = y1;
                cachedPremoveX2 = x2;
                cachedPremoveY2 = y2;
            }
            fillPaint.setShader(cachedPremoveShader);
            canvas.drawPath(arrowPath, fillPaint);

            // 3. Viền mảnh tương phản cao
            edgePaint.setShader(null);
            edgePaint.setColor(0xCC78350F); // Deep amber contrast stroke
            edgePaint.setStrokeWidth(Math.max(1.5f, sqSize * 0.016f));
            canvas.drawPath(arrowPath, edgePaint);
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

        // 1. Neon Drop Shadow / Glow (GPU accelerated 2-pass stroke)
        glowPaint.setMaskFilter(null);
        glowPaint.setColor(Color.argb((int)(0.25f * opacity * 255), colMid[0], colMid[1], colMid[2]));
        glowPaint.setStrokeWidth(edgeStroke + sqSize * 0.040f);
        canvas.drawPath(arrowPath, glowPaint);

        glowPaint.setColor(Color.argb((int)(0.50f * opacity * 255), colMid[0], colMid[1], colMid[2]));
        glowPaint.setStrokeWidth(edgeStroke + sqSize * 0.018f);
        canvas.drawPath(arrowPath, glowPaint);

        // 2. Linear Gradient Fill
        int cTail = Color.argb((int)(0.88f * opacity * 255), colTail[0], colTail[1], colTail[2]);
        int cMid  = Color.argb((int)(0.96f * opacity * 255), colMid[0], colMid[1], colMid[2]);
        int cHead = Color.argb((int)(1.0f  * opacity * 255), colHeadLight[0], colHeadLight[1], colHeadLight[2]);
        int slotIdx = Math.max(0, Math.min(cachedFillShaders.length - 1, arrow.tier));
        ShaderSlot fillSlot = cachedFillShaders[slotIdx];
        if (!fillSlot.matches(x1, y1, x2, y2, cTail, cMid, cHead)) {
            fillSlot.set(x1, y1, x2, y2, cTail, cMid, cHead, new LinearGradient(x1, y1, x2, y2,
                    new int[]{cTail, cMid, cHead},
                    new float[]{0.0f, 0.45f, 1.0f},
                    Shader.TileMode.CLAMP));
        }
        fillPaint.setShader(fillSlot.shader);
        canvas.drawPath(arrowPath, fillPaint);

        // 3. Highlight Edge Stroke
        int cEdgeTail = Color.argb((int)(0.75f * opacity * 255), colEdge[0], colEdge[1], colEdge[2]);
        int cEdgeHead = Color.argb((int)(0.65f * opacity * 255), 255, 255, 255);
        edgePaint.setStrokeWidth(edgeStroke);
        ShaderSlot edgeSlot = cachedEdgeShaders[slotIdx];
        if (!edgeSlot.matches(x1, y1, x2, y2, cEdgeTail, cEdgeHead, 0)) {
            edgeSlot.set(x1, y1, x2, y2, cEdgeTail, cEdgeHead, 0, new LinearGradient(x1, y1, x2, y2,
                    new int[]{cEdgeTail, cEdgeHead},
                    new float[]{0.0f, 1.0f},
                    Shader.TileMode.CLAMP));
        }
        edgePaint.setShader(edgeSlot.shader);
        canvas.drawPath(arrowPath, edgePaint);
    }

    private Path buildStraightPath(float x1, float y1, float x2, float y2, float len,
                                   float startOffset, float shaftHalf, float neckHalf,
                                   float headHalf, float headLen, float perpOffset, float targetOffset, boolean isThreat) {
        if (len < 2.0f) return null;

        float ux = (x2 - x1) / len;
        float uy = (y2 - y1) / len;
        float px = -uy;
        float py = ux;

        float startX = x1 + ux * startOffset + px * (perpOffset * 0.25f);
        float startY = y1 + uy * startOffset + py * (perpOffset * 0.25f);
        float endX = x2 - ux * targetOffset + px * perpOffset;
        float endY = y2 - uy * targetOffset + py * perpOffset;

        HeadPoints h = buildHeadPoints(endX, endY, ux, uy, px, py, headLen, headHalf, neckHalf, isThreat);

        float leftStartX  = startX + px * shaftHalf;
        float leftStartY  = startY + py * shaftHalf;
        float rightStartX = startX - px * shaftHalf;
        float rightStartY = startY - py * shaftHalf;

        reusableArrowPath.reset();
        reusableArrowPath.moveTo(leftStartX, leftStartY);
        reusableArrowPath.lineTo(h.leftNotchX, h.leftNotchY);
        reusableArrowPath.lineTo(h.leftOuterX, h.leftOuterY);
        reusableArrowPath.lineTo(h.tipX, h.tipY);
        reusableArrowPath.lineTo(h.rightOuterX, h.rightOuterY);
        reusableArrowPath.lineTo(h.rightNotchX, h.rightNotchY);
        reusableArrowPath.lineTo(rightStartX, rightStartY);
        reusableArrowPath.close();
        return reusableArrowPath;
    }

    private Path buildKnightPath(float x1, float y1, float x2, float y2,
                                 int fileDelta, int rankDelta, float startOffset,
                                 float shaftHalf, float neckHalf, float headHalf, float headLen,
                                 float perpOffset, float targetOffset, boolean isThreat) {
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

        HeadPoints h = buildHeadPoints(endX, endY, u2x, u2y, p2x, p2y, headLen, headHalf, neckHalf, isThreat);

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

        reusableArrowPath.reset();
        reusableArrowPath.moveTo(leftStartX, leftStartY);
        reusableArrowPath.lineTo(leftElbow[0], leftElbow[1]);
        reusableArrowPath.lineTo(h.leftNotchX, h.leftNotchY);
        reusableArrowPath.lineTo(h.leftOuterX, h.leftOuterY);
        reusableArrowPath.lineTo(h.tipX, h.tipY);
        reusableArrowPath.lineTo(h.rightOuterX, h.rightOuterY);
        reusableArrowPath.lineTo(h.rightNotchX, h.rightNotchY);
        reusableArrowPath.lineTo(rightElbow[0], rightElbow[1]);
        reusableArrowPath.lineTo(rightStartX, rightStartY);
        reusableArrowPath.close();
        return reusableArrowPath;
    }

    private HeadPoints buildHeadPoints(float tipX, float tipY, float ux, float uy,
                                       float px, float py, float headLen,
                                       float headHalf, float neckHalf, boolean isThreat) {
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

        if (sqSize != lastCenterSqSize || flipped != lastCenterFlipped) {
            for (int r = 0; r < 8; r++) {
                for (int f = 0; f < 8; f++) {
                    int col = flipped ? (7 - f) : f;
                    int row = flipped ? r : (7 - r);
                    int idx = r * 8 + f;
                    squareCenters[idx][0] = col * sqSize + sqSize / 2.0f;
                    squareCenters[idx][1] = row * sqSize + sqSize / 2.0f;
                }
            }
            lastCenterSqSize = sqSize;
            lastCenterFlipped = flipped;
        }

        return squareCenters[rank * 8 + file];
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
    static boolean hasBoxCollision(float bx, float by, float bw, float bh, float pad, float[][] placedBoxes, int placedBoxCount) {
        for (int i = 0; i < placedBoxCount; i++) {
            float px = placedBoxes[i][0];
            float py = placedBoxes[i][1];
            float pw = placedBoxes[i][2];
            float ph = placedBoxes[i][3];
            if (!(bx + bw + pad < px || px + pw + pad < bx || by + bh + pad < py || py + ph + pad < by)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBoxCollision(float bx, float by, float bw, float bh, float pad) {
        return hasBoxCollision(bx, by, bw, bh, pad, this.placedBoxes, this.placedBoxCount);
    }

    private boolean isBoxNearArrowShaft(float bx, float by, float bw, float bh, int currentArrowIdx, float sqSize) {
        float cx = bx + bw / 2.0f;
        float cy = by + bh / 2.0f;
        float boxRadius = (float) Math.hypot(bw / 2.0f, bh / 2.0f);
        float clearance = boxRadius + sqSize * 0.045f;

        for (int i = 0; i < arrows.size(); i++) {
            ArrowData a = arrows.get(i);
            if (a.from == null || a.to == null) continue;
            float x1 = getSqCenterX(a.from, flipped, sqSize);
            float y1 = getSqCenterY(a.from, flipped, sqSize);
            float x2 = getSqCenterX(a.to,   flipped, sqSize);
            float y2 = getSqCenterY(a.to,   flipped, sqSize);

            if (i == currentArrowIdx) {
                float dx = x2 - x1, dy = y2 - y1;
                float dist = (float) Math.hypot(dx, dy);
                if (dist > 1e-3f) {
                    float headLen = sqSize * 0.24f;
                    float shaftEndX = x2 - (dx / dist) * headLen;
                    float shaftEndY = y2 - (dy / dist) * headLen;
                    if (distToSegment(cx, cy, x1, y1, shaftEndX, shaftEndY) < clearance) {
                        return true;
                    }
                }
            } else {
                int fileDelta = Math.abs(a.to.charAt(0) - a.from.charAt(0));
                int rankDelta = Math.abs(a.to.charAt(1) - a.from.charAt(1));
                boolean isKnight = (fileDelta == 1 && rankDelta == 2) || (fileDelta == 2 && rankDelta == 1);
                if (isKnight) {
                    float elbowX = (fileDelta == 2 && rankDelta == 1) ? x2 : x1;
                    float elbowY = (fileDelta == 2 && rankDelta == 1) ? y1 : y2;
                    if (distToSegment(cx, cy, x1, y1, elbowX, elbowY) < clearance) return true;
                    if (distToSegment(cx, cy, elbowX, elbowY, x2, y2) < clearance) return true;
                } else {
                    if (distToSegment(cx, cy, x1, y1, x2, y2) < clearance) return true;
                }
            }
        }
        return false;
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
        corridorCount = 0;
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
                        if (midRank >= 0 && midRank < 8 && midFile >= 0 && midFile < 8) {
                            String midSq = BOARD_SQUARES[midRank * 8 + midFile];
                            if (corridorCount < corridorSq.length) {
                                corridorSq[corridorCount] = midSq;
                                corridorAxis[corridorCount] = axis;
                                corridorCount++;
                            }
                        }
                    }
                }
            }
        }

        placedBoxCount = 0;

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
            placedBoxes[0][0] = 0f;
            placedBoxes[0][1] = 0f;
            placedBoxes[0][2] = evalBarOverlap;
            placedBoxes[0][3] = bHeight;
            placedBoxCount = 1;
        }

        float blurRadius = Math.max(1.5f, sqSize * 0.035f);
        if (cachedPillBlurFilter == null || Math.abs(cachedPillBlurRadius - blurRadius) > 0.01f) {
            cachedPillBlurFilter = new BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL);
            cachedPillBlurRadius = blurRadius;
        }

        for (int i = 0; i < arrows.size(); i++) {
            ArrowData arrow = arrows.get(i);
            if (arrow.evalText == null || arrow.evalText.trim().isEmpty()) continue;

            float[] fromCenter = getSquareCenter(arrow.from, flipped, sqSize);
            float[] toCenter   = getSquareCenter(arrow.to,   flipped, sqSize);
            if (fromCenter == null || toCenter == null) continue;

            float x2 = toCenter[0], y2 = toCenter[1];

            int tierVal = arrow.tier;
            String lblText = arrow.evalText.trim();

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

            char passAxis = 0;
            for (int c = 0; c < corridorCount; c++) {
                if (arrow.to.equals(corridorSq[c])) {
                    passAxis = corridorAxis[c];
                    break;
                }
            }

            int candCount = 0;
            if (passAxis == 'v') {
                candidateCoords[0][0] = sqLeft + 2f; candidateCoords[0][1] = sqTop + sqSize * 0.06f;
                candidateCoords[1][0] = sqRight - pillW - 2f; candidateCoords[1][1] = sqTop + sqSize * 0.06f;
                candidateCoords[2][0] = sqLeft + 2f; candidateCoords[2][1] = sqBottom - pillH - sqSize * 0.06f;
                candidateCoords[3][0] = sqRight - pillW - 2f; candidateCoords[3][1] = sqBottom - pillH - sqSize * 0.06f;
                candidateCoords[4][0] = sqLeft - pillW - 2f; candidateCoords[4][1] = sqTop + (sqSize - pillH) / 2f;
                candidateCoords[5][0] = sqRight + 2f; candidateCoords[5][1] = sqTop + (sqSize - pillH) / 2f;
                candCount = 6;
            } else if (passAxis == 'h') {
                candidateCoords[0][0] = sqLeft + sqSize * 0.06f; candidateCoords[0][1] = sqTop + 2f;
                candidateCoords[1][0] = sqRight - pillW - sqSize * 0.06f; candidateCoords[1][1] = sqTop + 2f;
                candidateCoords[2][0] = sqLeft + sqSize * 0.06f; candidateCoords[2][1] = sqBottom - pillH - 2f;
                candidateCoords[3][0] = sqRight - pillW - sqSize * 0.06f; candidateCoords[3][1] = sqBottom - pillH - 2f;
                candidateCoords[4][0] = sqLeft + (sqSize - pillW) / 2f; candidateCoords[4][1] = sqTop - pillH - 2f;
                candidateCoords[5][0] = sqLeft + (sqSize - pillW) / 2f; candidateCoords[5][1] = sqBottom + 2f;
                candCount = 6;
            } else {
                candidateCoords[0][0] = sqLeft + sqSize * 0.04f; candidateCoords[0][1] = sqTop + sqSize * 0.04f;
                candidateCoords[1][0] = sqRight - pillW - sqSize * 0.04f; candidateCoords[1][1] = sqTop + sqSize * 0.04f;
                candidateCoords[2][0] = sqLeft + sqSize * 0.04f; candidateCoords[2][1] = sqBottom - pillH - sqSize * 0.04f;
                candidateCoords[3][0] = sqRight - pillW - sqSize * 0.04f; candidateCoords[3][1] = sqBottom - pillH - sqSize * 0.04f;
                candidateCoords[4][0] = sqLeft + (sqSize - pillW) / 2f; candidateCoords[4][1] = sqTop - pillH - 2f;
                candidateCoords[5][0] = sqLeft + (sqSize - pillW) / 2f; candidateCoords[5][1] = sqBottom + 2f;
                candidateCoords[6][0] = sqLeft - pillW - 2f; candidateCoords[6][1] = sqTop + (sqSize - pillH) / 2f;
                candidateCoords[7][0] = sqRight + 2f; candidateCoords[7][1] = sqTop + (sqSize - pillH) / 2f;
                candCount = 8;
            }

            int prefIdx = tierVal == 1 ? 0 : (tierVal == 2 ? 1 : (tierVal == 3 ? 2 : (tierVal == 4 ? 3 : 0)));
            if (prefIdx >= candCount) prefIdx = 0;

            if (sqLeft < minSafeX) {
                if (passAxis == 'v') {
                    prefIdx = 1;
                } else if (passAxis == 0) {
                    prefIdx = (tierVal == 1 || tierVal == 3) ? 1 : 3;
                }
            }

            float boxPad = Math.max(12f, sqSize * 0.14f);
            float labelX = -1f, labelY = -1f;

            // 1. Ưu tiên tuyệt đối: Không chạm huy hiệu khác VÀ Không bị thân mũi tên cắt ngang
            for (int orderIdx = 0; orderIdx < candCount; orderIdx++) {
                int k = (orderIdx == 0) ? prefIdx : ((orderIdx <= prefIdx) ? (orderIdx - 1) : orderIdx);
                float candX = Math.max(minSafeX, Math.min(bWidth - pillW - 2f, candidateCoords[k][0]));
                float candY = Math.max(2f, Math.min(bHeight - pillH - 2f, candidateCoords[k][1]));

                if (!hasBoxCollision(candX, candY, pillW, pillH, boxPad) && !isBoxNearArrowShaft(candX, candY, pillW, pillH, i, sqSize)) {
                    labelX = candX;
                    labelY = candY;
                    break;
                }
            }

            // 2. Dự phòng khi bàn cờ dày đặc: Tìm vị trí không chạm huy hiệu khác
            if (labelX < 0f) {
                for (int orderIdx = 0; orderIdx < candCount; orderIdx++) {
                    int k = (orderIdx == 0) ? prefIdx : ((orderIdx <= prefIdx) ? (orderIdx - 1) : orderIdx);
                    float candX = Math.max(minSafeX, Math.min(bWidth - pillW - 2f, candidateCoords[k][0]));
                    float candY = Math.max(2f, Math.min(bHeight - pillH - 2f, candidateCoords[k][1]));

                    if (!hasBoxCollision(candX, candY, pillW, pillH, boxPad)) {
                        labelX = candX;
                        labelY = candY;
                        break;
                    }
                }
            }

            // 3. Dự phòng đệm tối thiểu nếu không gian siêu hẹp
            if (labelX < 0f) {
                float minPad = Math.max(4f, sqSize * 0.05f);
                for (int k = 0; k < candCount; k++) {
                    float candX = Math.max(minSafeX, Math.min(bWidth - pillW - 2f, candidateCoords[k][0]));
                    float candY = Math.max(2f, Math.min(bHeight - pillH - 2f, candidateCoords[k][1]));

                    if (!hasBoxCollision(candX, candY, pillW, pillH, minPad)) {
                        labelX = candX;
                        labelY = candY;
                        break;
                    }
                }
            }

            // 4. Mặc định
            if (labelX < 0f) {
                labelX = Math.max(minSafeX, Math.min(bWidth - pillW - 2f, candidateCoords[prefIdx][0]));
                labelY = Math.max(2f, Math.min(bHeight - pillH - 2f, candidateCoords[prefIdx][1]));
            }

            if (placedBoxCount < placedBoxes.length) {
                placedBoxes[placedBoxCount][0] = labelX;
                placedBoxes[placedBoxCount][1] = labelY;
                placedBoxes[placedBoxCount][2] = pillW;
                placedBoxes[placedBoxCount][3] = pillH;
                placedBoxCount++;
            }

            // Styling & Colors matching NNVC Extension:
            boolean isBookLabel = "BOOK".equals(lblText);
            int baseColor = (arrow.isPremove) ? 0xFFF59E0B
                    : (arrow.isThreat) ? 0xFFEF4444
                    : (getContext() != null ? StockfishSettings.getArrowTierColor(getContext(), arrow.tier)
                    : StockfishSettings.DEFAULT_TIER_COLORS[Math.max(1, Math.min(5, arrow.tier)) - 1]);

            int br = (baseColor >> 16) & 0xFF;
            int bg = (baseColor >> 8) & 0xFF;
            int bb = baseColor & 0xFF;

            if (arrow.tier == 2) {
                br = clamp((int)(br + (255 - br) * 0.12f));
                bg = clamp((int)(bg + (255 - bg) * 0.12f));
                bb = clamp((int)(bb + (255 - bb) * 0.12f));
            } else if (arrow.tier >= 3) {
                br = clamp((int)(br + (255 - br) * 0.30f));
                bg = clamp((int)(bg + (255 - bg) * 0.30f));
                bb = clamp((int)(bb + (255 - bb) * 0.30f));
            }

            int sr = clamp((int)(br * (1.0f - 0.58f)));
            int sg = clamp((int)(bg * (1.0f - 0.58f)));
            int sb = clamp((int)(bb * (1.0f - 0.58f)));

            float bgAlpha = tierVal == 1 ? 0.92f : 0.82f;
            int bgCol = isBookLabel
                    ? Color.argb((int)(0.94f * 255), 28, 25, 23)
                    : Color.argb((int)(bgAlpha * 255), sr, sg, sb);

            int tr = clamp((int)(br + (255 - br) * 0.54f));
            int tg = clamp((int)(bg + (255 - bg) * 0.54f));
            int tb = clamp((int)(bb + (255 - bb) * 0.54f));

            float borderAlpha = tierVal == 1 ? 0.64f : 0.44f;
            int borderCol = isBookLabel
                    ? Color.argb((int)(0.85f * 255), 245, 158, 11)
                    : Color.argb((int)(borderAlpha * 255), tr, tg, tb);

            int textColor = isBookLabel ? 0xFFFBBF24 : 0xFFF8FBFF;
            float cornerRadius = Math.max(5f, sqSize * 0.09f);

            reusablePillRectF.set(labelX, labelY, labelX + pillW, labelY + pillH);

            // Subtle drop shadow matching extension (0 5px 16px rgba(0,0,0,0.32))
            badgeGlowPaint.setColor(Color.argb(82, 0, 0, 0));
            badgeGlowPaint.setStrokeWidth(Math.max(1f, sqSize * 0.02f));
            badgeGlowPaint.setMaskFilter(cachedPillBlurFilter);
            canvas.drawRoundRect(reusablePillRectF, cornerRadius, cornerRadius, badgeGlowPaint);
            badgeGlowPaint.setMaskFilter(null);

            // Background Fill (Frosted Glass with tint/shade)
            badgeBgPaint.setColor(bgCol);
            canvas.drawRoundRect(reusablePillRectF, cornerRadius, cornerRadius, badgeBgPaint);

            // 1px Border Stroke
            badgeStrokePaint.setColor(borderCol);
            badgeStrokePaint.setStrokeWidth(Math.max(1.0f, sqSize * 0.012f));
            badgeStrokePaint.setMaskFilter(null);
            canvas.drawRoundRect(reusablePillRectF, cornerRadius, cornerRadius, badgeStrokePaint);

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

    private static final java.util.Map<String, Drawable.ConstantState> BADGE_STATE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

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

        Drawable.ConstantState state = BADGE_STATE_CACHE.get(resName);
        if (state != null) {
            return state.newDrawable(context.getResources()).mutate();
        }

        try {
            int resId = context.getResources().getIdentifier(resName, "drawable", context.getPackageName());
            if (resId != 0) {
                if (Build.VERSION.SDK_INT >= 21) {
                    Drawable d = context.getDrawable(resId);
                    if (d != null) {
                        Drawable.ConstantState cs = d.getConstantState();
                        if (cs != null) {
                            BADGE_STATE_CACHE.put(resName, cs);
                        }
                        return d.mutate();
                    }
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
