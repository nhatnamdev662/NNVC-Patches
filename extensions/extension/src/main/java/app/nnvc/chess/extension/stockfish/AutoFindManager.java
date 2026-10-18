package app.nnvc.chess.extension.stockfish;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Pattern;

/**
 * AutoFindManager – Manages automated matchmaking & new game queuing upon game over.
 * 
 * Follows NNVC Desktop Extension AutoFindController architecture:
 *  - State Machine: IDLE -> COUNTDOWN -> SEEKING -> PLAYING
 *  - 2-second user-cancelable countdown banner with haptic feedback
 *  - Dual Direct Execution Architecture:
 *      Tầng 1: Direct RealGameViewModel.Ka() [newGame(isRematch=false)]
 *      Tầng 2: UI View Button Scanner [Fallback]
 *  - Zero dev-lingo, 100% I18n bilingual support.
 */
public class AutoFindManager {

    private static final String TAG = "NNVC_AutoFind";
    private static final String BANNER_TAG = "nnvc_autofind_banner";

    public enum State {
        IDLE,
        COUNTDOWN,
        SEEKING,
        PLAYING
    }

    private static volatile State currentState = State.IDLE;
    private static volatile int remainingSeconds = 2;
    private static Handler mainHandler = null;
    private static Runnable countdownRunnable = null;
    private static WeakReference<Activity> activeActivityRef = new WeakReference<>(null);

    private static final Pattern NEW_GAME_BTN_PATTERN = Pattern.compile(
            "(?i)(v[áa]n m[ớo]i|ch[ơo]i l[ạa]i|ch[ơo]i ti[ếe]p|t[ìi]m tr[ậa]n|new game|play again|find opponent|next game)"
    );

    private static synchronized Handler getMainHandler() {
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        return mainHandler;
    }

    public static State getState() {
        return currentState;
    }

    public static boolean isSeeking() {
        return currentState == State.SEEKING;
    }

    public static boolean isCountingDown() {
        return currentState == State.COUNTDOWN;
    }

    /**
     * Called when game over is detected. Initiates countdown if enabled.
     */
    public static void onGameOver(final Activity activity) {
        if (activity == null) return;
        if (!StockfishSettings.isAutoFindMatchEnabled(activity)) {
            cancelCountdown("Auto find disabled");
            currentState = State.IDLE;
            return;
        }

        if (currentState == State.COUNTDOWN || currentState == State.SEEKING) {
            return; // Already in progress
        }

        activeActivityRef = new WeakReference<>(activity);
        currentState = State.COUNTDOWN;
        remainingSeconds = 2;

        getMainHandler().post(() -> {
            try {
                showCountdownBanner(activity, remainingSeconds);
            } catch (Throwable t) {
                NnvcLogger.d(TAG, "Failed to show banner: " + t.getMessage());
            }
            startCountdownTimer(activity);
        });
    }

    /**
     * Called when a new game starts (FEN startpos or board reset).
     */
    public static void onNewGameStarted() {
        cancelCountdown("New game started");
        currentState = State.PLAYING;
    }

    /**
     * Cancels the active countdown.
     */
    public static void cancelCountdown(String reason) {
        if (countdownRunnable != null) {
            getMainHandler().removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }

        Activity activity = activeActivityRef.get();
        if (activity != null) {
            getMainHandler().post(() -> removeCountdownBanner(activity));
        }

        if (currentState == State.COUNTDOWN) {
            NnvcLogger.i(TAG, "Cancelled Auto Find countdown (" + reason + ")");
        }
        currentState = State.IDLE;
    }

    private static void startCountdownTimer(final Activity activity) {
        if (countdownRunnable != null) {
            getMainHandler().removeCallbacks(countdownRunnable);
        }

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentState != State.COUNTDOWN) return;
                if (activity.isFinishing() || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
                    cancelCountdown("Activity destroyed");
                    return;
                }

                remainingSeconds--;
                if (remainingSeconds > 0) {
                    updateCountdownBanner(activity, remainingSeconds);
                    getMainHandler().postDelayed(this, 1000L);
                } else {
                    // Countdown complete -> Trigger new match
                    removeCountdownBanner(activity);
                    triggerAutoFind(activity);
                }
            }
        };

        getMainHandler().postDelayed(countdownRunnable, 1000L);
    }

    private static void triggerAutoFind(final Activity activity) {
        currentState = State.SEEKING;
        vibrate(activity, 25);

        try {
            Toast.makeText(activity, I18n.get(activity, "toast_newgame"), Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {}

        NnvcLogger.i(TAG, "Triggering Auto Find match...");

        // Tầng 1: Native ViewModel Controller
        boolean triggered = triggerViaViewModel(activity);

        // Tầng 2: UI View Button Scanner
        if (!triggered) {
            triggered = triggerViaButtonScan(activity);
        }

        if (triggered) {
            NnvcLogger.i(TAG, "Auto Find match triggered successfully!");
        } else {
            NnvcLogger.i(TAG, "Auto Find match trigger: could not locate native controller or action button.");
            currentState = State.IDLE;
        }
    }

    /**
     * Tầng 1: Invokes RealGameViewModel.Ka() directly on UI thread.
     */
    public static boolean triggerViaViewModel(Activity activity) {
        if (activity == null) return false;
        try {
            Object vm = getRealGameViewModel(activity);
            if (vm != null) {
                Method kaMethod = findMethodByName(vm.getClass(), "Ka");
                if (kaMethod != null) {
                    kaMethod.setAccessible(true);
                    kaMethod.invoke(vm);
                    NnvcLogger.d(TAG, "Invoked RealGameViewModel.Ka() directly.");
                    return true;
                }
            }
        } catch (Throwable t) {
            NnvcLogger.d(TAG, "ViewModel trigger failed: " + t.getMessage());
        }
        return false;
    }

    public static Object getRealGameViewModel(Activity activity) {
        if (activity == null) return null;
        try {
            Method x6 = findMethodByName(activity.getClass(), "X6");
            if (x6 != null) {
                x6.setAccessible(true);
                Object vm = x6.invoke(activity);
                if (vm != null) return vm;
            }
        } catch (Throwable ignored) {}

        try {
            Method i8 = findMethodByName(activity.getClass(), "I8");
            if (i8 != null) {
                i8.setAccessible(true);
                Object vm = i8.invoke(activity);
                if (vm != null) return vm;
            }
        } catch (Throwable ignored) {}

        try {
            Field fB = activity.getClass().getDeclaredField("B");
            fB.setAccessible(true);
            Object val = fB.get(activity);
            if (val != null) {
                try {
                    Method getValue = val.getClass().getMethod("getValue");
                    Object lazyVal = getValue.invoke(val);
                    if (lazyVal != null) return lazyVal;
                } catch (Throwable ignored) {
                    return val;
                }
            }
        } catch (Throwable ignored) {}

        // Scan fields for Lazy ViewModel
        try {
            Class<?> c = activity.getClass();
            while (c != null && !c.getName().equals("android.app.Activity")) {
                for (Field f : c.getDeclaredFields()) {
                    String fn = f.getName().toLowerCase(java.util.Locale.US);
                    if (fn.contains("viewmodel")) {
                        f.setAccessible(true);
                        Object val = f.get(activity);
                        if (val != null) {
                            if (val.getClass().getName().contains("RealGameViewModel")) {
                                return val;
                            }
                            // Kotlin Lazy
                            try {
                                Method getValue = val.getClass().getMethod("getValue");
                                Object lazyVal = getValue.invoke(val);
                                if (lazyVal != null && lazyVal.getClass().getName().contains("RealGameViewModel")) {
                                    return lazyVal;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Checks if the active game is in Game Over state via RealGameViewModel reflection.
     * Works natively on modern Jetpack Compose Chess.com (4.10.20) where legacy Dialogs are absent.
     */
    public static boolean isViewModelGameOver(Activity activity) {
        if (activity == null) return false;
        try {
            Object vm = getRealGameViewModel(activity);
            if (vm == null) return false;

            // 1. Direct Ca() call -> returns this.gameEndData != null
            Method ca = findMethodByName(vm.getClass(), "Ca");
            if (ca != null) {
                ca.setAccessible(true);
                Object res = ca.invoke(vm);
                if (res instanceof Boolean && (Boolean) res) {
                    return true;
                }
            }

            // 2. Direct gameEndData inspection
            Class<?> curr = vm.getClass();
            while (curr != null && curr != Object.class) {
                for (Field f : curr.getDeclaredFields()) {
                    String fn = f.getName();
                    if (fn.equals("gameEndData") || fn.equals("i0")) {
                        f.setAccessible(true);
                        if (f.get(vm) != null) {
                            return true;
                        }
                    }
                }
                curr = curr.getSuperclass();
            }

            // 3. StateFlow gameOver / _gameOver inspection
            curr = vm.getClass();
            while (curr != null && curr != Object.class) {
                for (Field f : curr.getDeclaredFields()) {
                    String fn = f.getName().toLowerCase(java.util.Locale.US);
                    if (fn.equals("gameover") || fn.equals("b1") || fn.equals("_gameover") || fn.equals("a1")) {
                        try {
                            f.setAccessible(true);
                            Object flow = f.get(vm);
                            if (flow != null) {
                                Method getValue = flow.getClass().getMethod("getValue");
                                if (getValue.invoke(flow) != null) {
                                    return true;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                curr = curr.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Tầng 2: Scans view hierarchy for New Game / Play Again buttons and clicks.
     */
    public static boolean triggerViaButtonScan(Activity activity) {
        if (activity == null || activity.getWindow() == null) return false;
        View decor = activity.getWindow().getDecorView();
        if (decor != null) {
            View targetBtn = findNewGameButton(decor);
            if (targetBtn != null) {
                targetBtn.performClick();
                NnvcLogger.d(TAG, "Clicked New Game button from decorView: " + targetBtn);
                return true;
            }
        }

        // Scan FragmentManager
        try {
            Method getFm = null;
            try {
                getFm = activity.getClass().getMethod("getSupportFragmentManager");
            } catch (NoSuchMethodException e) {
                try {
                    getFm = activity.getClass().getMethod("getFragmentManager");
                } catch (Throwable ignored) {}
            }
            if (getFm != null) {
                Object fm = getFm.invoke(activity);
                if (fm != null) {
                    Method getFrags = fm.getClass().getMethod("getFragments");
                    Object fragsList = getFrags.invoke(fm);
                    if (fragsList instanceof List) {
                        for (Object frag : (List<?>) fragsList) {
                            if (frag == null) continue;
                            try {
                                Method getView = frag.getClass().getMethod("getView");
                                Object v = getView.invoke(frag);
                                if (v instanceof View) {
                                    View target = findNewGameButton((View) v);
                                    if (target != null) {
                                        target.performClick();
                                        NnvcLogger.d(TAG, "Clicked New Game button from fragment: " + frag.getClass().getSimpleName());
                                        return true;
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private static View findNewGameButton(View root) {
        if (root == null || root.getVisibility() != View.VISIBLE) return null;

        if (root.isClickable()) {
            if (isMatchingNewGameButton(root)) {
                return root;
            }
        }

        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            int count = vg.getChildCount();
            for (int i = 0; i < count; i++) {
                View found = findNewGameButton(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean isMatchingNewGameButton(View view) {
        if (view == null) return false;

        // Check text if TextView/Button
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.length() > 0) {
                if (NEW_GAME_BTN_PATTERN.matcher(text).find()) {
                    return true;
                }
            }
        }

        // Check contentDescription
        CharSequence desc = view.getContentDescription();
        if (desc != null && desc.length() > 0) {
            if (NEW_GAME_BTN_PATTERN.matcher(desc).find()) {
                return true;
            }
        }

        // Check resource id entry name
        try {
            int id = view.getId();
            if (id != View.NO_ID && view.getResources() != null) {
                String entry = view.getResources().getResourceEntryName(id);
                if (entry != null) {
                    String lower = entry.toLowerCase(java.util.Locale.US);
                    if (lower.contains("new_game") || lower.contains("play_again") || lower.contains("find_opponent") || lower.contains("next_game")) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    // ── UI Countdown Banner ──────────────────────────────────────────────────

    private static void showCountdownBanner(final Activity activity, int seconds) {
        if (activity == null || activity.getWindow() == null) return;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        final ViewGroup root = (ViewGroup) decor;

        removeCountdownBanner(activity);

        float density = activity.getResources().getDisplayMetrics().density;

        final LinearLayout banner = new LinearLayout(activity);
        banner.setTag(BANNER_TAG);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding((int) (18 * density), (int) (10 * density), (int) (18 * density), (int) (10 * density));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEE141923); // Cyber dark glass
        bg.setCornerRadius(16 * density);
        bg.setStroke((int) (1.5f * density), 0x990A84FF); // Cyber blue accent
        banner.setBackground(bg);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            banner.setElevation(30f);
        }

        TextView titleText = new TextView(activity);
        titleText.setId(View.generateViewId());
        titleText.setTag("banner_title");
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(13.5f);
        titleText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleText.setText(String.format(java.util.Locale.US, I18n.get(activity, "toast_cd_countdown"), seconds));
        banner.addView(titleText);

        banner.setOnClickListener(v -> {
            cancelCountdown("User tapped cancel banner");
            vibrate(activity, 15);
            try {
                Toast.makeText(activity, I18n.get(activity, "toast_cd_cancel"), Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = (int) (48 * density);

        root.addView(banner, lp);
    }

    private static void updateCountdownBanner(Activity activity, int seconds) {
        if (activity == null || activity.getWindow() == null) return;
        View decor = activity.getWindow().getDecorView();
        if (decor == null) return;

        View banner = decor.findViewWithTag(BANNER_TAG);
        if (banner != null) {
            View title = banner.findViewWithTag("banner_title");
            if (title instanceof TextView) {
                ((TextView) title).setText(String.format(java.util.Locale.US, I18n.get(activity, "toast_cd_countdown"), seconds));
            }
        }
    }

    private static void removeCountdownBanner(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;

        ViewGroup root = (ViewGroup) decor;
        View banner = root.findViewWithTag(BANNER_TAG);
        if (banner != null) {
            root.removeView(banner);
        }
    }

    private static Method findMethodByName(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    return m;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static void vibrate(Context context, long ms) {
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(ms);
                }
            }
        } catch (Throwable ignored) {}
    }
}
