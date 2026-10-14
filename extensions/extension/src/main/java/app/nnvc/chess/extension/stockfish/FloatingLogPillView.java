package app.nnvc.chess.extension.stockfish;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * FloatingLogPillView – Small draggable floating badge on screen.
 * Tapping it immediately pops open the NnvcLogViewerDialog for live inspection.
 */
public class FloatingLogPillView extends FrameLayout {

    private static final String TAG = "NNVC";
    public static final String VIEW_TAG = "nnvc_floating_log_pill";

    private float dX, dY;
    private float startX, startY;
    private boolean isDragging = false;

    public FloatingLogPillView(Context context) {
        super(context);
        initView();
    }

    private void initView() {
        setTag(VIEW_TAG);
        float density = getResources().getDisplayMetrics().density;

        TextView pillText = new TextView(getContext());
        pillText.setText("📜 LOG");
        pillText.setTextColor(0xFFE6EDF3);
        pillText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        pillText.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        pillText.setGravity(Gravity.CENTER);

        int padH = (int) (10 * density);
        int padV = (int) (6 * density);
        pillText.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE60D1117);
        bg.setCornerRadius(16 * density);
        bg.setStroke((int) (1.2f * density), 0xFF8B5CF6); // Cyber purple
        setBackground(bg);
        setElevation(35f);

        addView(pillText);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        setLayoutParams(lp);

        // Restore saved position
        float savedX = getContext().getSharedPreferences(StockfishSettings.PREFS_NAME, Context.MODE_PRIVATE)
                .getFloat("floating_log_pill_x", 20 * density);
        float savedY = getContext().getSharedPreferences(StockfishSettings.PREFS_NAME, Context.MODE_PRIVATE)
                .getFloat("floating_log_pill_y", 120 * density);

        setX(savedX);
        setY(savedY);

        setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dX = getX() - event.getRawX();
                    dY = getY() - event.getRawY();
                    startX = event.getRawX();
                    startY = event.getRawY();
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = Math.abs(event.getRawX() - startX);
                    float deltaY = Math.abs(event.getRawY() - startY);
                    if (deltaX > 8 * density || deltaY > 8 * density) {
                        isDragging = true;
                    }

                    if (isDragging) {
                        ViewGroup parent = (ViewGroup) getParent();
                        if (parent != null) {
                            float newX = event.getRawX() + dX;
                            float newY = event.getRawY() + dY;
                            float maxX = parent.getWidth() - getWidth();
                            float maxY = parent.getHeight() - getHeight();

                            setX(Math.max(0, Math.min(newX, maxX)));
                            setY(Math.max(0, Math.min(newY, maxY)));
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        Activity act = StockfishExtension.getCurrentActivity();
                        if (act != null) {
                            HapticHelper.pop(act, v);
                            NnvcLogViewerDialog.show(act);
                        }
                    } else {
                        // Save position
                        getContext().getSharedPreferences(StockfishSettings.PREFS_NAME, Context.MODE_PRIVATE)
                                .edit()
                                .putFloat("floating_log_pill_x", getX())
                                .putFloat("floating_log_pill_y", getY())
                                .apply();
                    }
                    return true;
            }
            return false;
        });
    }

    public static void updateVisibility(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Window window = activity.getWindow();
                if (window == null) return;
                ViewGroup decorView = (ViewGroup) window.getDecorView();
                if (decorView == null) return;

                boolean enabled = StockfishSettings.isFloatingLogPillEnabled(activity);
                View existing = decorView.findViewWithTag(VIEW_TAG);

                if (enabled) {
                    if (existing == null) {
                        FloatingLogPillView pill = new FloatingLogPillView(activity);
                        decorView.addView(pill);
                    } else {
                        existing.setVisibility(View.VISIBLE);
                        existing.bringToFront();
                    }
                } else {
                    if (existing != null) {
                        decorView.removeView(existing);
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    public static void ensureAttached(ViewGroup decorView) {
        if (decorView == null) return;
        try {
            Context ctx = decorView.getContext();
            if (ctx == null) return;
            boolean enabled = StockfishSettings.isFloatingLogPillEnabled(ctx);
            View existing = decorView.findViewWithTag(VIEW_TAG);
            if (enabled && existing == null) {
                FloatingLogPillView pill = new FloatingLogPillView(ctx);
                decorView.addView(pill);
            }
        } catch (Throwable ignored) {}
    }
}
