/*
 * Copyright 2026 NNVC
 */

package app.prathxm.chess.extension.stockfish;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * PlayerAccuracyPillView – Thẻ hiển thị Độ Chính Xác (%) và Estimated Elo thời gian thực
 * gắn trực tiếp sát mép trên/dưới của bàn cờ Chess.com theo phong cách Cyber Glass.
 */
public class PlayerAccuracyPillView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pieceCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roleBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF bounds = new RectF();
    private final RectF roleBounds = new RectF();

    private boolean isWhite = true;
    private boolean isYou = true;
    private float accuracy = -1f;
    private int estimatedElo = -1;

    public PlayerAccuracyPillView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);

        bgPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        pieceCirclePaint.setStyle(Paint.Style.FILL);
        roleBadgePaint.setStyle(Paint.Style.FILL);

        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        valPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    public void updateData(boolean isWhite, boolean isYou, float accuracy, int estimatedElo) {
        this.isWhite = isWhite;
        this.isYou = isYou;
        this.accuracy = accuracy;
        this.estimatedElo = estimatedElo;
        invalidate();
    }

    public int calculateDesiredWidth() {
        Context ctx = getContext();
        float density = getResources().getDisplayMetrics().density;
        textPaint.setTextSize(9.5f * density);
        labelPaint.setTextSize(8.5f * density);
        valPaint.setTextSize(11f * density);

        String roleText = isYou ? I18n.get(ctx, "role_you") : I18n.get(ctx, "role_opponent");
        float roleWidth = textPaint.measureText(roleText);
        float roleBadgeW = roleWidth + (8 * density);

        String accLabel = I18n.get(ctx, "label_accuracy");
        String accStr = (accuracy >= 0f) ? String.format(java.util.Locale.US, "%.1f%%", accuracy) : "--";
        float accW = Math.max(valPaint.measureText(accStr), labelPaint.measureText(accLabel));

        String eloLabel = I18n.get(ctx, "label_elo");
        String eloStr = (estimatedElo > 0) ? String.valueOf(estimatedElo) : "--";
        float eloW = Math.max(valPaint.measureText(eloStr), labelPaint.measureText(eloLabel));

        float total = (10 * density) // left pad
                + (12 * density)     // piece circle diameter
                + (6 * density)      // gap to role
                + roleBadgeW
                + (8 * density)      // gap to acc
                + accW
                + (8 * density)      // gap to divider
                + (1 * density)      // divider
                + (8 * density)      // gap to elo
                + eloW
                + (14 * density);    // right margin to outer curve
        return (int) Math.ceil(total);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        Context ctx = getContext();
        float density = getResources().getDisplayMetrics().density;
        float radius = h / 2f;
        bounds.set(1.5f * density, 1.5f * density, w - 1.5f * density, h - 1.5f * density);

        // 1. Nền thẻ Cyber Glass
        if (isYou) {
            bgPaint.setShader(new LinearGradient(0, 0, w, 0,
                    0xF00D1624, 0xF0142338, Shader.TileMode.CLAMP));
            strokePaint.setColor(0xCC0A84FF); // Cyber Blue
            strokePaint.setStrokeWidth(1.5f * density);
        } else {
            bgPaint.setShader(new LinearGradient(0, 0, w, 0,
                    0xEB11151D, 0xEB1A202A, Shader.TileMode.CLAMP));
            strokePaint.setColor(0x4DFFFFFF);
            strokePaint.setStrokeWidth(1f * density);
        }
        canvas.drawRoundRect(bounds, radius, radius, bgPaint);
        canvas.drawRoundRect(bounds, radius, radius, strokePaint);

        float cy = h / 2f;

        // Tính toán thông số từng phần đồng bộ ngôn ngữ
        String roleText = isYou ? I18n.get(ctx, "role_you") : I18n.get(ctx, "role_opponent");
        textPaint.setTextSize(9.5f * density);
        float roleWidth = textPaint.measureText(roleText);
        float roleBadgeW = roleWidth + (8 * density);
        float roleH = 14 * density;

        String accLabel = I18n.get(ctx, "label_accuracy");
        labelPaint.setTextSize(8.5f * density);
        labelPaint.setColor(0x99FFFFFF);
        valPaint.setTextSize(11f * density);
        valPaint.setColor(accuracy >= 85f ? 0xFF34D399 : (accuracy >= 70f ? 0xFFF0B84B : 0xFF64D2FF));
        String accStr = (accuracy >= 0f) ? String.format(java.util.Locale.US, "%.1f%%", accuracy) : "--";
        float accW = Math.max(valPaint.measureText(accStr), labelPaint.measureText(accLabel));

        String eloLabel = I18n.get(ctx, "label_elo");
        valPaint.setColor(0xFFFFD760); // Gold
        String eloStr = (estimatedElo > 0) ? String.valueOf(estimatedElo) : "--";
        float eloW = Math.max(valPaint.measureText(eloStr), labelPaint.measureText(eloLabel));

        float leftPad = 10 * density;
        float pieceR = 6f * density;
        float cx = leftPad + pieceR;

        // 2. Biểu tượng quân cờ tròn (Trắng hoặc Đen)
        pieceCirclePaint.setColor(isWhite ? 0xFFF5F7FA : 0xFF2A2E39);
        canvas.drawCircle(cx, cy, pieceR, pieceCirclePaint);
        if (isWhite) {
            strokePaint.setColor(0x33000000);
            strokePaint.setStrokeWidth(1f * density);
            canvas.drawCircle(cx, cy, pieceR, strokePaint);
        } else {
            strokePaint.setColor(0x4DFFFFFF);
            strokePaint.setStrokeWidth(1f * density);
            canvas.drawCircle(cx, cy, pieceR, strokePaint);
        }

        // 3. Huy hiệu [BẠN] / [ĐỐI THỦ] (hoặc [YOU] / [OPPONENT])
        float roleLeft = cx + pieceR + (6 * density);
        roleBounds.set(roleLeft, cy - (roleH / 2f), roleLeft + roleBadgeW, cy + (roleH / 2f));
        roleBadgePaint.setColor(isYou ? 0xDD0A84FF : 0x33FFFFFF);
        canvas.drawRoundRect(roleBounds, 4 * density, 4 * density, roleBadgePaint);

        textPaint.setColor(0xFFFFFFFF);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(roleText, roleLeft + (4 * density), textY, textPaint);

        // 4. Dải Accuracy
        float accLeft = roleBounds.right + (8 * density);
        canvas.drawText(accLabel, accLeft, cy - (2 * density), labelPaint);
        valPaint.setColor(accuracy >= 85f ? 0xFF34D399 : (accuracy >= 70f ? 0xFFF0B84B : 0xFF64D2FF));
        canvas.drawText(accStr, accLeft, cy + (9 * density), valPaint);

        // Đường phân cách mỏng
        float divX = accLeft + accW + (8 * density);
        strokePaint.setColor(0x26FFFFFF);
        strokePaint.setStrokeWidth(1f * density);
        canvas.drawLine(divX, cy - (7 * density), divX, cy + (7 * density), strokePaint);

        // 5. Dải Estimated Elo
        float eloLeft = divX + (8 * density);
        labelPaint.setColor(0x99FFFFFF);
        canvas.drawText(eloLabel, eloLeft, cy - (2 * density), labelPaint);
        valPaint.setColor(0xFFFFD760);
        canvas.drawText(eloStr, eloLeft, cy + (9 * density), valPaint);
    }
}
