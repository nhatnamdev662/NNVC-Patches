package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * A clean, stable vertical evaluation bar.
 */
public class EvalBarView extends View {
    private float score = 0.0f; // from White's perspective (positive = white advantage)
    private boolean hasMate = false;
    private int mateIn = 0;
    private boolean flipped = false;

    private final Paint paintWhite  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBlack  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLine   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText   = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rectWhite = new RectF();
    private final RectF rectBlack = new RectF();
    private float cachedTextOffset = 0f;

    public EvalBarView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        try {
            setElevation(10.0f);
        } catch (Throwable ignored) {}
        paintWhite.setColor(0xFFFFFFFF); // pure crisp white
        paintBlack.setColor(0xFF312E2B); // Chess.com signature dark charcoal

        paintLine.setColor(0xFF797672);
        paintLine.setStrokeWidth(1.5f);

        paintBorder.setColor(0x33000000);
        paintBorder.setStrokeWidth(1.0f);

        float density = context.getResources().getDisplayMetrics().density;
        paintText.setTextSize(8.8f * density);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintText.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        Paint.FontMetrics fm = paintText.getFontMetrics();
        this.cachedTextOffset = (fm.descent + fm.ascent) / -2.0f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Never consume touches so user can tap/drag pieces on column 'a' without interference
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    public void reset() {
        if (animator != null) animator.cancel();
        score = 0.0f;
        hasMate = false;
        mateIn = 0;
        hasValue = false;
        shownRatio = 0.5f;
        invalidate();
    }

    public void resetStabilizer() {
        reset();
    }

    /**
     * Update the bar data and reposition it next to the chess board.
     * Call this from the main thread.
     */
    public void update(int x, int y, int width, int height,
                       float score, boolean hasMate, int mateIn, boolean flipped) {
        float target;
        if (hasMate) {
            target = (mateIn != 0 ? (mateIn > 0 ? 1.0f : 0.0f) : (score > 0 ? 1.0f : 0.0f));
        } else {
            target = ratioFor(score);
        }
        boolean firstShow = !hasValue || this.flipped != flipped;
        this.score   = score;
        this.hasMate = hasMate;
        this.mateIn  = mateIn;
        this.flipped = flipped;
        this.hasValue = true;
        animateTo(target, firstShow);

        boolean sizeChanged = (getWidth() != width || getHeight() != height);
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(width, height);
            if (lp instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) lp).gravity = Gravity.TOP | Gravity.START;
            }
            setLayoutParams(lp);
        } else if (sizeChanged || lp.width != width || lp.height != height) {
            lp.width  = width;
            lp.height = height;
            if (lp instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) lp).gravity = Gravity.TOP | Gravity.START;
            }
            setLayoutParams(lp);
        }

        if (getTranslationX() != x) setTranslationX(x);
        if (getTranslationY() != y) setTranslationY(y);
        invalidate();
    }

    /** Ratio currently drawn (0..1, 1 = all white); animated towards the latest score. */
    private float shownRatio = 0.5f;
    private boolean hasValue = false;
    private android.animation.ValueAnimator animator;

    /**
     * Evaluation (pawns, white POV) -> white share of the bar.
     * Linear Clamp formula according to NNVC Extension Module 1921.
     * Range: [-500 cp, +500 cp] mapped to [0.05, 0.95].
     */
    static float ratioFor(float scorePawns) {
        float cp = Math.max(-500.0f, Math.min(500.0f, scorePawns * 100.0f));
        float norm = (cp + 500.0f) / 1000.0f; // [0.0, 1.0]
        float whiteShare = 0.05f + 0.90f * norm; // [0.05, 0.95]
        return Math.max(0.05f, Math.min(0.95f, whiteShare));
    }

    private void animateTo(float target, boolean immediate) {
        if (animator != null) animator.cancel();
        if (immediate || getVisibility() != VISIBLE) {
            shownRatio = target;
            invalidate();
            return;
        }
        animator = android.animation.ValueAnimator.ofFloat(shownRatio, target);
        animator.setDuration(180);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            shownRatio = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float whiteRatio = shownRatio;

        float divY;
        if (flipped) {
            // Board is flipped (Black bottom, White top): White territory starts at top (0..divY)
            divY = h * whiteRatio;
            rectWhite.set(0, 0,    w, divY);
            rectBlack.set(0, divY, w, h);
        } else {
            // Normal board (White bottom, Black top): Black territory is at top (0..divY), White at bottom (divY..h)
            divY = h * (1.0f - whiteRatio);
            rectBlack.set(0, 0,    w, divY);
            rectWhite.set(0, divY, w, h);
        }

        canvas.drawRect(rectBlack, paintBlack);
        canvas.drawRect(rectWhite, paintWhite);
        canvas.drawLine(0, divY, w, divY, paintLine);

        // Tick mark at 0.00 (exact middle of the bar)
        float mid = h / 2.0f;
        canvas.drawLine(0, mid, w * 0.35f, mid, paintLine);
        canvas.drawLine(w * 0.65f, mid, w, mid, paintLine);

        // Right edge separator border
        canvas.drawLine(w - 1f, 0, w - 1f, h, paintBorder);

        // Score label: Extension standard (no '+' or '-' prefix, only "M<n>" or "<score>" 1 decimal digit)
        String label = hasMate ? (mateIn == 0 ? "M" : "M" + Math.abs(mateIn))
                                : formatScore(score);

        // Leading player side determines anchor & text color:
        // White leading (score >= 0): text displayed at White's home end
        // Black leading (score < 0):  text displayed at Black's home end
        boolean whiteAhead = score >= 0;
        boolean whiteHomeAtTop = flipped;
        boolean anchorAtTop = whiteAhead ? whiteHomeAtTop : !whiteHomeAtTop;

        float density = getContext().getResources().getDisplayMetrics().density;
        float padding = 6.0f * density;
        float textHalfLen = paintText.measureText(label) / 2.0f;
        float textY = anchorAtTop ? (padding + textHalfLen) : (h - padding - textHalfLen);

        if (whiteAhead) {
            // White's home end has pure white background -> dark text
            paintText.setColor(0xFF312E2B);
        } else {
            // Black's home end has dark background -> white text
            paintText.setColor(0xFFFFFFFF);
        }

        canvas.save();
        canvas.rotate(-90, w / 2.0f, textY);
        canvas.drawText(label, w / 2.0f, textY + cachedTextOffset, paintText);
        canvas.restore();
    }

    /** "0.4", "3.2", "12" (locale-independent: never "0,4"). Zero-allocation & zero-regex format. */
    static String formatScore(float score) {
        float a = Math.abs(score);
        if (a >= 9.95f) {
            return String.valueOf(Math.round(a));
        }
        int tenths = Math.round(a * 10.0f);
        int whole = tenths / 10;
        int frac = tenths % 10;
        if (frac == 10) {
            whole++;
            frac = 0;
        }
        return whole + "." + frac;
    }
}
