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
}
