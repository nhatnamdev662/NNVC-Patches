package app.nnvc.chess.extension.stockfish;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

public class CyberSwitchView extends View {

    private boolean isChecked = false;
    private float progress = 0.0f; // 0.0 = off, 1.0 = on
    private float thumbScale = 1.0f;
    private ValueAnimator animator;
    private OnCheckedChangeListener listener;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();

    // NNVC Colors
    private static final int COLOR_OFF_BG = 0x24FFFFFF;
    private static final int COLOR_OFF_BORDER = 0x33A0B4D2;
    private static final int COLOR_OFF_THUMB = 0xFFD8E2EC;

    private static final int COLOR_ON_BG = 0xF00A84FF;
    private static final int COLOR_ON_BORDER = 0xFF64D2FF;
    private static final int COLOR_ON_THUMB = 0xFFFFFFFF;
    private static final int COLOR_ON_GLOW = 0x6664D2FF;

    public interface OnCheckedChangeListener {
        void onCheckedChanged(CyberSwitchView switchView, boolean isChecked);
    }

    public CyberSwitchView(Context context) {
        super(context);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);

        borderPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStyle(Paint.Style.FILL);

        setOnClickListener(v -> toggleWithFeedback());
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean checked) {
        setChecked(checked, false);
    }

    public void setChecked(boolean checked, boolean animate) {
        if (this.isChecked == checked && Math.abs(progress - (checked ? 1f : 0f)) < 0.01f) {
            return;
        }
        this.isChecked = checked;

        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        if (animate) {
            float start = progress;
            float end = checked ? 1.0f : 0.0f;
            animator = ValueAnimator.ofFloat(start, end);
            animator.setDuration(240);
            animator.setInterpolator(new OvershootInterpolator(1.1f));
            animator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                // Dynamic thumb bounce while sliding
                float p = Math.abs(progress - 0.5f) * 2f; // 0 at center, 1 at ends
                thumbScale = 0.88f + (0.12f * p);
                invalidate();
            });
            animator.start();
        } else {
            progress = checked ? 1.0f : 0.0f;
            thumbScale = 1.0f;
            invalidate();
        }

        if (listener != null) {
            listener.onCheckedChanged(this, this.isChecked);
        }
    }

    public void toggleWithFeedback() {
        HapticHelper.pop(getContext(), this);
        animate().scaleX(0.90f).scaleY(0.90f).setDuration(80).withEndAction(() -> {
            animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        }).start();
        setChecked(!isChecked, true);
    }

    public void toggle() {
        toggleWithFeedback();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int defWidth = (int) (48 * density);
        int defHeight = (int) (27 * density);

        int width = resolveSize(defWidth, widthMeasureSpec);
        int height = resolveSize(defHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float density = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();
        float strokeWidth = 1.6f * density;

        borderPaint.setStrokeWidth(strokeWidth);

        // 1. Interpolate Colors
        float clampedProgress = Math.max(0f, Math.min(1f, progress));
        int curBg = blendColor(COLOR_OFF_BG, COLOR_ON_BG, clampedProgress);
        int curBorder = blendColor(COLOR_OFF_BORDER, COLOR_ON_BORDER, clampedProgress);
        int curThumb = blendColor(COLOR_OFF_THUMB, COLOR_ON_THUMB, clampedProgress);

        // Track Bounds
        float pad = strokeWidth / 2f;
        trackRect.set(pad, pad, w - pad, h - pad);
        float cornerRadius = h / 2f;

        // Draw Glow when ON
        if (clampedProgress > 0.05f) {
            glowPaint.setColor(COLOR_ON_GLOW);
            glowPaint.setAlpha((int) (90 * clampedProgress));
            canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, glowPaint);
        }

        // Draw Track Background
        trackPaint.setColor(curBg);
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint);

        // Draw Track Border
        borderPaint.setColor(curBorder);
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, borderPaint);

        // 2. Draw Thumb (Pill Slider Knob)
        float baseThumbRadius = (h - 6f * density) / 2f;
        float scaledThumbRadius = baseThumbRadius * thumbScale;
        float startX = 3f * density + baseThumbRadius;
        float endX = w - 3f * density - baseThumbRadius;
        float curThumbX = startX + (endX - startX) * clampedProgress;
        float curThumbY = h / 2f;

        // Thumb Outer Neon Ring when active
        if (clampedProgress > 0.15f) {
            glowPaint.setColor(COLOR_ON_BORDER);
            glowPaint.setAlpha((int) (140 * clampedProgress));
            canvas.drawCircle(curThumbX, curThumbY, scaledThumbRadius + 2.5f * density, glowPaint);
        }

        // Thumb Body
        thumbPaint.setColor(curThumb);
        canvas.drawCircle(curThumbX, curThumbY, scaledThumbRadius, thumbPaint);
    }

    private static int blendColor(int from, int to, float ratio) {
        float inverseRatio = 1f - ratio;
        float a = Color.alpha(from) * inverseRatio + Color.alpha(to) * ratio;
        float r = Color.red(from) * inverseRatio + Color.red(to) * ratio;
        float g = Color.green(from) * inverseRatio + Color.green(to) * ratio;
        float b = Color.blue(from) * inverseRatio + Color.blue(to) * ratio;
        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }
}
