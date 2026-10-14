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
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * EngineStatusHUD – Floating status indicator that informs the user about
 * engine loading, engine switching, and ready status.
 *
 * Prevents the user from thinking the app is frozen while engines initialize.
 * Tapping opens the NnvcLogViewerDialog for real-time diagnostics.
 */
public class EngineStatusHUD {

    private static final String TAG = "NNVC";
    private static final String HUD_TAG = "nnvc_engine_loading_hud";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static Runnable autoDismissRunnable = null;

    public static void show(final Context context, final String message, final boolean isError, final long autoDismissMs) {
        // Disabled: 100% offline local extraction, hide all loading/download HUD overlays
    }

    public static void update(final String message, final boolean isError) {
        // Disabled
    }

    public static void showReady(final Context context, final String engineName) {
        // Disabled
    }

    public static void dismiss(final Activity activity) {
        mainHandler.post(() -> {
            try {
                Activity act = activity != null ? activity : StockfishExtension.getCurrentActivity();
                if (act == null) return;
                Window window = act.getWindow();
                if (window == null) return;
                ViewGroup decorView = (ViewGroup) window.getDecorView();
                if (decorView == null) return;

                View hud = decorView.findViewWithTag(HUD_TAG);
                if (hud != null) {
                    AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
                    fadeOut.setDuration(250);
                    fadeOut.setAnimationListener(new Animation.AnimationListener() {
                        @Override public void onAnimationStart(Animation animation) {}
                        @Override public void onAnimationRepeat(Animation animation) {}
                        @Override
                        public void onAnimationEnd(Animation animation) {
                            try { decorView.removeView(hud); } catch (Throwable ignored) {}
                        }
                    });
                    hud.startAnimation(fadeOut);
                }
            } catch (Throwable ignored) {}
        });
    }

    private static View createHudView(final Activity activity, String message, boolean isError) {
        float density = activity.getResources().getDisplayMetrics().density;

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        int padH = (int) (14 * density);
        int padV = (int) (8 * density);
        layout.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(18 * density);
        bg.setColor(isError ? 0xE63D1111 : 0xE60D1117);
        bg.setStroke((int) (1.2f * density), isError ? 0xFFFA5252 : 0xAA64D2FF);
        layout.setBackground(bg);
        layout.setElevation(30f);

        // Spinner / Icon
        ProgressBar spinner = new ProgressBar(activity, null, android.R.attr.progressBarStyleSmall);
        spinner.setId(1001);
        int spinnerSize = (int) (14 * density);
        LinearLayout.LayoutParams spParams = new LinearLayout.LayoutParams(spinnerSize, spinnerSize);
        spParams.rightMargin = (int) (8 * density);
        spinner.setLayoutParams(spParams);
        spinner.setVisibility(message.startsWith("✓") ? View.GONE : View.VISIBLE);
        layout.addView(spinner);

        // Text
        TextView tv = new TextView(activity);
        tv.setId(1002);
        tv.setText(message);
        tv.setTextColor(isError ? 0xFFFF6B6B : 0xFFE6EDF3);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        layout.addView(tv);

        // Position: Top center below status bar or top board
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.topMargin = (int) (52 * density);
        layout.setLayoutParams(params);

        // Tap opens Log Viewer
        layout.setOnClickListener(v -> {
            HapticHelper.pop(activity, v);
            NnvcLogViewerDialog.show(activity);
        });

        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(200);
        layout.startAnimation(fadeIn);

        return layout;
    }

    private static void updateView(View hud, String message, boolean isError) {
        if (!(hud instanceof LinearLayout)) return;
        LinearLayout layout = (LinearLayout) hud;
        ProgressBar spinner = layout.findViewById(1001);
        TextView tv = layout.findViewById(1002);

        if (spinner != null) {
            spinner.setVisibility(message.startsWith("✓") || isError ? View.GONE : View.VISIBLE);
        }
        if (tv != null) {
            tv.setText(message);
            tv.setTextColor(isError ? 0xFFFF6B6B : 0xFFE6EDF3);
        }

        if (layout.getBackground() instanceof GradientDrawable) {
            GradientDrawable bg = (GradientDrawable) layout.getBackground();
            bg.setColor(isError ? 0xE63D1111 : 0xE60D1117);
            bg.setStroke((int) (1.2f * layout.getResources().getDisplayMetrics().density),
                    isError ? 0xFFFA5252 : 0xAA64D2FF);
        }
    }
}
