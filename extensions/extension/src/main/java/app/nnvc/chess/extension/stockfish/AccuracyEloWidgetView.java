/*
 * Copyright 2026 NNVC
 */

package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import java.util.Locale;

/**
 * AccuracyEloWidgetView – Widget nổi kéo thả hiển thị Độ Chính Xác (%) và Estimated Elo
 * theo phong cách chuẩn 100% của NNVC Chrome Extension.
 *
 * Tính năng chính:
 * 1. Thiết kế Cyber Glass cao cấp (frosted dark theme, card Trắng / Đen riêng biệt).
 * 2. Tự động nhận diện bên người chơi [BẠN] / [YOU] viền phát sáng Cyber Blue #0A84FF.
 * 3. Hỗ trợ kéo thả (drag & drop) tự do khắp màn hình, tự động nhớ vị trí người dùng.
 * 4. Nút thu gọn / mở rộng (collapse / expand) tiện lợi khi cần thoáng màn hình.
 * 5. Tự động kẹp an toàn (screen boundary clamp) không bao giờ bị rơi ra ngoài mép.
 * 6. Song ngữ Anh / Việt đồng bộ theo I18n.
 */
public class AccuracyEloWidgetView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pieceCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roleBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint metricBoxBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF bounds = new RectF();
    private final RectF cardBounds = new RectF();
    private final RectF stripBounds = new RectF();
    private final RectF badgeBounds = new RectF();
    private final RectF metricBoxBounds = new RectF();
    private final Path stripPath = new Path();

    private float whiteAccuracy = -1f;
    private float blackAccuracy = -1f;
    private int whiteElo = -1;
    private int blackElo = -1;
    private boolean userIsWhite = true;
    private boolean isCollapsed = false;
    private boolean isPositioned = false;

    // Kéo thả (Touch & Drag)
    private final int touchSlop;
    private float downRawX = 0f;
    private float downRawY = 0f;
    private float startTransX = 0f;
    private float startTransY = 0f;
    private boolean isDragging = false;

    private final OnLayoutChangeListener parentLayoutListener = new OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                   int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if ((right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) && !isDragging) {
                clampAndSetPosition(getTranslationX(), getTranslationY());
            }
        }
    };

    public AccuracyEloWidgetView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        isCollapsed = StockfishSettings.isAccuracyWidgetCollapsed(context);

        bgPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        pieceCirclePaint.setStyle(Paint.Style.FILL);
        roleBadgePaint.setStyle(Paint.Style.FILL);
        metricBoxBgPaint.setStyle(Paint.Style.FILL);

        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        valPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        setClickable(true);
        setFocusable(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getParent() instanceof View) {
            ((View) getParent()).addOnLayoutChangeListener(parentLayoutListener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (getParent() instanceof View) {
            ((View) getParent()).removeOnLayoutChangeListener(parentLayoutListener);
        }
    }

    public boolean isDragging() {
        return isDragging;
    }

    public boolean isPositioned() {
        return isPositioned;
    }

    public void updateData(float whiteAcc, float blackAcc, int whiteElo, int blackElo, boolean userIsWhite) {
        if (whiteAcc >= 0f) this.whiteAccuracy = whiteAcc;
        if (blackAcc >= 0f) this.blackAccuracy = blackAcc;
        if (whiteElo > 0) this.whiteElo = whiteElo;
        if (blackElo > 0) this.blackElo = blackElo;
        this.userIsWhite = userIsWhite;
        invalidate();
    }

    public void resetData() {
        this.whiteAccuracy = -1f;
        this.blackAccuracy = -1f;
        this.whiteElo = -1;
        this.blackElo = -1;
        invalidate();
    }

    public boolean isCollapsed() {
        return isCollapsed;
    }

    public int calculateDesiredWidth() {
        float density = getResources().getDisplayMetrics().density;
        return (int) Math.ceil(164f * density);
    }

    public int calculateExpandedHeight() {
        float density = getResources().getDisplayMetrics().density;
        return (int) Math.ceil(172f * density);
    }

    public int calculateCollapsedHeight() {
        float density = getResources().getDisplayMetrics().density;
        return (int) Math.ceil(32f * density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = calculateDesiredWidth();
        int h = isCollapsed ? calculateCollapsedHeight() : calculateExpandedHeight();
        setMeasuredDimension(w, h);
    }

    public void clampAndSetPosition(float x, float y) {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent == null) {
            setTranslationX(x);
            setTranslationY(y);
            isPositioned = true;
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float margin = 6f * density;
        int parentW = parent.getWidth();
        int parentH = parent.getHeight();
        int w = calculateDesiredWidth();
        int h = isCollapsed ? calculateCollapsedHeight() : calculateExpandedHeight();

        if (parentW <= 0) parentW = (int) (360f * density);
        if (parentH <= 0) parentH = (int) (640f * density);

        int topInset = parent.getPaddingTop();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.view.WindowInsets insets = parent.getRootWindowInsets();
            if (insets != null) {
                topInset = Math.max(topInset, insets.getSystemWindowInsetTop());
            }
        }

        float minX = margin;
        float maxX = Math.max(margin, parentW - w - margin);
        float minY = Math.max(margin, topInset + margin);
        float maxY = Math.max(minY, parentH - h - margin);

        float clampedX = Math.max(minX, Math.min(maxX, x));
        float clampedY = Math.max(minY, Math.min(maxY, y));

        setTranslationX(clampedX);
        setTranslationY(clampedY);
        isPositioned = true;
    }

    public void toggleCollapsed() {
        isCollapsed = !isCollapsed;
        StockfishSettings.setAccuracyWidgetCollapsed(getContext(), isCollapsed);
        updateDimensions();
    }

    public void updateDimensions() {
        int w = calculateDesiredWidth();
        int h = isCollapsed ? calculateCollapsedHeight() : calculateExpandedHeight();
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null) {
            lp.width = w;
            lp.height = h;
            setLayoutParams(lp);
        }
        clampAndSetPosition(getTranslationX(), getTranslationY());
        requestLayout();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                animate().cancel();
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startTransX = getTranslationX();
                startTransY = getTranslationY();
                isDragging = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!isDragging && Math.hypot(dx, dy) > touchSlop) {
                    isDragging = true;
                    animate().scaleX(1.02f).scaleY(1.02f).alpha(0.95f).setDuration(80).start();
                }
                if (isDragging) {
                    float newX = startTransX + dx;
                    float newY = startTransY + dy;
                    clampAndSetPosition(newX, newY);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;
                    animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(100).start();
                    StockfishSettings.setAccuracyWidgetPosition(getContext(), getTranslationX(), getTranslationY());
                } else if (action == MotionEvent.ACTION_UP) {
                    float touchY = event.getY();
                    float density = getResources().getDisplayMetrics().density;
                    // Chạm vào thanh header hoặc khi đang thu gọn -> chuyển đổi thu gọn / mở rộng
                    if (isCollapsed || touchY <= 32f * density) {
                        toggleCollapsed();
                    }
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        Context ctx = getContext();
        float density = getResources().getDisplayMetrics().density;
        float radius = 15f * density;

        // 1. Nền tổng thể Cyber Glass
        bounds.set(1f * density, 1f * density, w - 1f * density, h - 1f * density);
        bgPaint.setShader(new LinearGradient(0, 0, 0, h, 0xF20D1117, 0xF2161E28, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(bounds, radius, radius, bgPaint);

        strokePaint.setShader(null);
        strokePaint.setColor(isDragging ? 0x990A84FF : 0x24FFFFFF);
        strokePaint.setStrokeWidth(1.2f * density);
        canvas.drawRoundRect(bounds, radius, radius, strokePaint);

        // 2. Thanh tiêu đề (Header): Biểu tượng âm dương cờ vua + "Accuracy / Elo" + Nút thu gọn
        float headerY = 16f * density;
        float iconLeft = 8f * density;
        float iconTop = headerY - (9f * density);
        float iconSize = 18f * density;
        RectF iconBounds = new RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);

        // Khung icon vuông bo góc chia đôi màu trắng/đen chuẩn Extension
        headerIconPaint.setShader(new LinearGradient(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize,
                new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0xFF121720, 0xFF121720},
                new float[]{0f, 0.5f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(iconBounds, 5f * density, 5f * density, headerIconPaint);

        strokePaint.setColor(0x33FFFFFF);
        strokePaint.setStrokeWidth(0.8f * density);
        canvas.drawRoundRect(iconBounds, 5f * density, 5f * density, strokePaint);

        // Chấm tròn âm dương ngược lại ở tâm icon
        float dotR = 3.8f * density;
        headerDotPaint.setShader(new LinearGradient(iconBounds.centerX() - dotR, iconBounds.centerY() - dotR,
                iconBounds.centerX() + dotR, iconBounds.centerY() + dotR,
                new int[]{0xFF121720, 0xFF121720, 0xFFFFFFFF, 0xFFFFFFFF},
                new float[]{0f, 0.5f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(iconBounds.centerX(), iconBounds.centerY(), dotR, headerDotPaint);

        // Tiêu đề "Accuracy / Elo"
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(10.5f * density);
        textPaint.setColor(0xFFFFFFFF);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float titleBase = headerY - (fm.ascent + fm.descent) / 2f;
        canvas.drawText("Accuracy / Elo", iconLeft + iconSize + (6f * density), titleBase, textPaint);

        // Nút thu gọn / mở rộng (Chevron)
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(9f * density);
        textPaint.setColor(0x99FFFFFF);
        float chevronX = w - (12f * density);
        canvas.drawText(isCollapsed ? "▼" : "▲", chevronX, titleBase, textPaint);

        if (isCollapsed) {
            return; // Chỉ hiển thị header thanh thoát khi thu gọn
        }

        // 3. Hai thẻ hiển thị người chơi (White Card & Black Card)
        float cardLeft = 7f * density;
        float cardRight = w - (7f * density);
        float cardH = 64f * density;
        float cardRadius = 10f * density;

        // --- A. THẺ QUÂN TRẮNG ---
        float whiteTop = 31f * density;
        drawPlayerCard(canvas, ctx, density, true, userIsWhite, whiteAccuracy, whiteElo,
                cardLeft, whiteTop, cardRight, whiteTop + cardH, cardRadius);

        // --- B. THẺ QUÂN ĐEN ---
        float blackTop = whiteTop + cardH + (4.5f * density);
        drawPlayerCard(canvas, ctx, density, false, !userIsWhite, blackAccuracy, blackElo,
                cardLeft, blackTop, cardRight, blackTop + cardH, cardRadius);
    }

    private void drawPlayerCard(Canvas canvas, Context ctx, float density,
                                boolean isWhiteSide, boolean isYou,
                                float accuracy, int elo,
                                float cLeft, float cTop, float cRight, float cBottom,
                                float radius) {
        cardBounds.set(cLeft, cTop, cRight, cBottom);
        float cW = cRight - cLeft;

        // 1. Nền thẻ
        if (isWhiteSide) {
            bgPaint.setShader(new LinearGradient(cLeft, cTop, cRight, cBottom,
                    0xFFFFFFFF, 0xFFEAF0F6, Shader.TileMode.CLAMP));
        } else {
            bgPaint.setShader(new LinearGradient(cLeft, cTop, cRight, cBottom,
                    0xD9202632, 0xEB12171F, Shader.TileMode.CLAMP));
        }
        canvas.drawRoundRect(cardBounds, radius, radius, bgPaint);

        // 2. Viền thẻ: Nếu là [BẠN] -> Viền Cyber Blue #0A84FF phát sáng
        if (isYou) {
            strokePaint.setColor(0xFF0A84FF);
            strokePaint.setStrokeWidth(1.6f * density);
        } else {
            strokePaint.setColor(isWhiteSide ? 0x4D000000 : 0x1AFFFFFF);
            strokePaint.setStrokeWidth(0.8f * density);
        }
        canvas.drawRoundRect(cardBounds, radius, radius, strokePaint);

        // 3. Vạch dải màu nhấn ở mép trái (Accent Strip)
        float stripW = 3f * density;
        stripBounds.set(cLeft, cTop, cLeft + stripW, cBottom);
        if (isWhiteSide) {
            stripPaint.setShader(new LinearGradient(0, cTop, 0, cBottom, 0xFFFFFFFF, 0xFFB0BAC7, Shader.TileMode.CLAMP));
        } else {
            stripPaint.setShader(new LinearGradient(0, cTop, 0, cBottom, 0xFF64D2FF, 0xFF0A84FF, Shader.TileMode.CLAMP));
        }
        canvas.save();
        stripPath.reset();
        stripPath.addRoundRect(cardBounds, radius, radius, Path.Direction.CW);
        canvas.clipPath(stripPath);
        canvas.drawRect(stripBounds, stripPaint);
        canvas.restore();

        // 4. Hàng đầu: Quân cờ tròn + Tên màu quân + Huy hiệu [BẠN] / [ĐỐI THỦ]
        float headY = cTop + (11.5f * density);
        float pieceR = 5.8f * density;
        float pieceCx = cLeft + (15f * density);

        // Icon quân tròn
        if (isWhiteSide) {
            pieceCirclePaint.setShader(new RadialGradient(pieceCx - (2f * density), headY - (2f * density), pieceR * 1.5f,
                    0xFFFFFFFF, 0xFFCBD5E1, Shader.TileMode.CLAMP));
            canvas.drawCircle(pieceCx, headY, pieceR, pieceCirclePaint);
            strokePaint.setColor(0x26000000);
            strokePaint.setStrokeWidth(0.8f * density);
            canvas.drawCircle(pieceCx, headY, pieceR, strokePaint);
        } else {
            pieceCirclePaint.setShader(new RadialGradient(pieceCx - (2f * density), headY - (2f * density), pieceR * 1.5f,
                    0xFF5A6578, 0xFF0D1117, Shader.TileMode.CLAMP));
            canvas.drawCircle(pieceCx, headY, pieceR, pieceCirclePaint);
            strokePaint.setColor(0x33FFFFFF);
            strokePaint.setStrokeWidth(0.8f * density);
            canvas.drawCircle(pieceCx, headY, pieceR, strokePaint);
        }

        // Tên màu quân ("White" / "Trắng" hoặc "Black" / "Đen")
        String sideName = isWhiteSide ? I18n.get(ctx, "white") : I18n.get(ctx, "black");
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(10.5f * density);
        textPaint.setColor(isWhiteSide ? 0xFF090C10 : 0xFFFFFFFF);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float nameBase = headY - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(sideName, pieceCx + pieceR + (5f * density), nameBase, textPaint);

        // Huy hiệu Role Badge: [BẠN] / [YOU] (Cyber Blue đậm sắc nét) hoặc [ĐỐI THỦ] / [OPPONENT]
        String roleText = isYou ? I18n.get(ctx, "role_you") : I18n.get(ctx, "role_opponent");
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(8f * density);
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        labelPaint.setFakeBoldText(true);
        float badgeTextW = labelPaint.measureText(roleText);
        float badgeW = badgeTextW + (11f * density);
        float badgeH = 14f * density;
        float badgeRight = cRight - (7f * density);
        float badgeLeft = badgeRight - badgeW;
        badgeBounds.set(badgeLeft, headY - (badgeH / 2f), badgeRight, headY + (badgeH / 2f));

        if (isYou) {
            // Nền xanh Cyber Blue rực rỡ, đậm đà, 100% OPAQUE - không bao giờ bị dính alpha từ thẻ khác
            roleBadgePaint.setShader(null);
            roleBadgePaint.setColor(0xFF0A84FF);
            canvas.drawRoundRect(badgeBounds, 999f, 999f, roleBadgePaint);

            // Viền sắc nét cho badge Cyber Blue
            strokePaint.setColor(0xFF64D2FF);
            strokePaint.setStrokeWidth(0.8f * density);
            canvas.drawRoundRect(badgeBounds, 999f, 999f, strokePaint);

            // Chữ BẠN / YOU màu trắng tinh, đậm nét, cực kỳ rõ ràng
            labelPaint.setColor(0xFFFFFFFF);
        } else {
            roleBadgePaint.setShader(null);
            roleBadgePaint.setColor(isWhiteSide ? 0xFF161C26 : 0x33FFFFFF);
            canvas.drawRoundRect(badgeBounds, 999f, 999f, roleBadgePaint);

            strokePaint.setColor(isWhiteSide ? 0x26000000 : 0x2EFFFFFF);
            strokePaint.setStrokeWidth(0.8f * density);
            canvas.drawRoundRect(badgeBounds, 999f, 999f, strokePaint);

            labelPaint.setColor(isWhiteSide ? 0xFFFFFFFF : 0xF0FFFFFF);
        }
        Paint.FontMetrics bFm = labelPaint.getFontMetrics();
        float badgeBase = badgeBounds.centerY() - (bFm.ascent + bFm.descent) / 2f;
        canvas.drawText(roleText, badgeBounds.centerX(), badgeBase, labelPaint);
        labelPaint.setFakeBoldText(false);

        // 5. Hai ô số liệu (Metrics Grid): Ô Độ Chính Xác (%) & Ô Estimated Elo
        float gridTop = cTop + (22.5f * density);
        float gridH = 35f * density;
        float gap = 4.5f * density;
        float boxW = (cW - (14f * density) - gap) / 2f;

        // Ô 1: ACCURACY
        float b1Left = cLeft + (7f * density);
        float b1Right = b1Left + boxW;
        metricBoxBounds.set(b1Left, gridTop, b1Right, gridTop + gridH);
        drawMetricBox(canvas, ctx, density, isWhiteSide, metricBoxBounds,
                I18n.get(ctx, "label_accuracy"),
                accuracy >= 0f ? String.format(Locale.US, "%.1f%%", accuracy) : "--",
                true, accuracy);

        // Ô 2: ESTIMATED ELO
        float b2Left = b1Right + gap;
        float b2Right = b2Left + boxW;
        metricBoxBounds.set(b2Left, gridTop, b2Right, gridTop + gridH);
        drawMetricBox(canvas, ctx, density, isWhiteSide, metricBoxBounds,
                I18n.get(ctx, "label_elo"),
                elo > 0 ? String.valueOf(elo) : "--",
                false, elo);
    }

    private void drawMetricBox(Canvas canvas, Context ctx, float density,
                                boolean isWhiteSide, RectF boxRect,
                                String label, String value,
                                boolean isAccuracy, float numVal) {
        // Nền ô số liệu
        metricBoxBgPaint.setColor(isWhiteSide ? 0x0D0F141C : 0x14FFFFFF);
        canvas.drawRoundRect(boxRect, 6f * density, 6f * density, metricBoxBgPaint);

        strokePaint.setColor(isWhiteSide ? 0x0F000000 : 0x1AFFFFFF);
        strokePaint.setStrokeWidth(0.8f * density);
        canvas.drawRoundRect(boxRect, 6f * density, 6f * density, strokePaint);

        float centerX = boxRect.centerX();

        // Nhãn chỉ số nhỏ ("CHÍNH XÁC" / "ACCURACY" hoặc "ELO ĐÁNH GIÁ" / "EST. ELO")
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(6.5f * density);
        labelPaint.setColor(isWhiteSide ? 0x8A090C10 : 0x99FFFFFF);
        Paint.FontMetrics lFm = labelPaint.getFontMetrics();
        float labelBase = boxRect.top + (10f * density) - (lFm.ascent + lFm.descent) / 2f;
        canvas.drawText(label, centerX, labelBase, labelPaint);

        // Giá trị số lớn nổi bật
        valPaint.setTextAlign(Paint.Align.CENTER);
        valPaint.setTextSize(12f * density);

        if (isAccuracy) {
            if (numVal >= 85f) {
                valPaint.setColor(isWhiteSide ? 0xFF059669 : 0xFF34D399); // Emerald
            } else if (numVal >= 70f) {
                valPaint.setColor(isWhiteSide ? 0xFFD97706 : 0xFFF0B84B); // Amber
            } else if (numVal >= 0f) {
                valPaint.setColor(isWhiteSide ? 0xFF0A84FF : 0xFF64D2FF); // Cyber Blue
            } else {
                valPaint.setColor(isWhiteSide ? 0xFF4A5568 : 0x88FFFFFF); // Mặc định "--"
            }
        } else {
            // ELO
            if (numVal > 0) {
                valPaint.setColor(isWhiteSide ? 0xFF92400E : 0xFFFFD760); // Gold
            } else {
                valPaint.setColor(isWhiteSide ? 0xFF4A5568 : 0x88FFFFFF);
            }
        }

        Paint.FontMetrics vFm = valPaint.getFontMetrics();
        float valBase = boxRect.top + (24.5f * density) - (vFm.ascent + vFm.descent) / 2f;
        canvas.drawText(value, centerX, valBase, valPaint);
    }
}
