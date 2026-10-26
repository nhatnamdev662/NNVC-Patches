package app.nnvc.chess.extension.stockfish;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/**
 * AutoFindManager – Manages automated matchmaking & new game queuing upon game over.
 * 
 * Follows NNVC Desktop Extension AutoFindController architecture:
 *  - State Machine: IDLE -> COUNTDOWN -> SEEKING -> PLAYING
 *  - 5-second user-cancelable Cyber Countdown Card with Ring indicator
 *  - Dual Direct Execution Architecture:
 *      Tầng 1: Direct RealGameViewModel.Ka() [newGame(isRematch=false)]
 *      Tầng 2: UI View Button Scanner [Fallback]
 *  - Auto Re-queue / Re-seek when match search drops or cancels
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

    public static final int COUNTDOWN_DURATION_SEC = 5;

    private static volatile State currentState = State.IDLE;
    private static volatile int remainingSeconds = COUNTDOWN_DURATION_SEC;
    private static volatile long seekStartTimeMs = 0L;
    private static Handler mainHandler = null;
    private static Runnable countdownRunnable = null;
    private static WeakReference<Activity> activeActivityRef = new WeakReference<>(null);
    private static volatile Object lastHandledGameId = null;
    private static volatile String lastHandledGameUuid = null;
    private static volatile Object lastHandledGameEndData = null;
    private static volatile WeakReference<Object> lastNativeViewModelRef = null;

    private static volatile boolean userExplicitlyCancelled = false;
    private static volatile boolean isDismissingByAutoFind = false;
    private static final Set<Activity> dismissingSeekActivities =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static Runnable resetDismissingFlagRunnable = null;
    private static volatile int seekRetryCount = 0;
    private static final int MAX_SEEK_RETRIES = 5;
    private static Runnable seekingWatchdogRunnable = null;
    private static volatile WeakReference<Activity> activeSeekActivityRef = new WeakReference<>(null);
    public static final long SEEKING_TIMEOUT_MS = 20000L; // 20s matchmaking timeout ceiling

    public static int getSeekRetryCount() {
        return seekRetryCount;
    }

    public static void resetSeekRetryCountForTest() {
        seekRetryCount = 0;
    }

    public static boolean isDismissingByAutoFindForTest() {
        return isDismissingByAutoFind;
    }

    public static void setIsDismissingByAutoFindForTest(boolean val) {
        isDismissingByAutoFind = val;
    }

    public static Activity getDismissingSeekActivityForTest() {
        synchronized (dismissingSeekActivities) {
            return dismissingSeekActivities.isEmpty() ? null : dismissingSeekActivities.iterator().next();
        }
    }

    public static void setDismissingSeekActivityForTest(Activity act) {
        synchronized (dismissingSeekActivities) {
            dismissingSeekActivities.clear();
            if (act != null) {
                dismissingSeekActivities.add(act);
            }
        }
    }

    public static boolean isUserExplicitlyCancelledForTest() {
        return userExplicitlyCancelled;
    }

    public static void setUserExplicitlyCancelledForTest(boolean val) {
        userExplicitlyCancelled = val;
    }

    public static void setStateForTest(State state) {
        currentState = state;
    }

    private static volatile int consecutiveMatchesCount = 0;
    public static final int RECREATE_AFTER_MATCHES = 10;

    public static int getConsecutiveMatchesCount() {
        return consecutiveMatchesCount;
    }

    public static void resetConsecutiveMatchesCount() {
        consecutiveMatchesCount = 0;
    }

    private static synchronized Handler getMainHandler() {
        if (mainHandler == null) {
            try {
                if (Looper.getMainLooper() != null) {
                    mainHandler = new Handler(Looper.getMainLooper());
                }
            } catch (Throwable ignored) {}
        }
        return mainHandler;
    }

    public static void postOnMain(Runnable r) {
        if (r == null) return;
        Handler h = getMainHandler();
        if (h != null) {
            h.post(r);
        } else {
            r.run();
        }
    }

    public static void postDelayedOnMain(Runnable r, long delayMs) {
        if (r == null) return;
        Handler h = getMainHandler();
        if (h != null) {
            h.postDelayed(r, delayMs);
        } else if (delayMs <= 0) {
            r.run();
        } else {
            try {
                new java.util.Timer("AutoFindTimerFallback", true).schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        try {
                            r.run();
                        } catch (Throwable ignored) {}
                    }
                }, delayMs);
            } catch (Throwable t) {
                // Last-resort fallback
                r.run();
            }
        }
    }

    public static void removeCallbacksOnMain(Runnable r) {
        if (r == null) return;
        Handler h = getMainHandler();
        if (h != null) {
            h.removeCallbacks(r);
        }
    }

    public static long getUptimeMs() {
        try {
            return android.os.SystemClock.uptimeMillis();
        } catch (Throwable ignored) {
            return System.currentTimeMillis();
        }
    }

    public static State getState() {
        return currentState;
    }

    public static boolean isSeeking() {
        return currentState == State.SEEKING;
    }

    public static long getSeekStartTimeMs() {
        return seekStartTimeMs;
    }

    public static boolean isCountingDown() {
        return currentState == State.COUNTDOWN;
    }

    public static Object getLastKnownViewModel() {
        if (lastNativeViewModelRef != null) {
            return lastNativeViewModelRef.get();
        }
        return null;
    }

    public static void setLastKnownViewModel(Object vm) {
        lastNativeViewModelRef = (vm != null) ? new WeakReference<>(vm) : null;
    }

    public static boolean isCurrentGameHandled(Activity activity) {
        if (currentState == State.COUNTDOWN || currentState == State.SEEKING) {
            return true;
        }
        Object currentEndData = getCurrentGameEndData(activity);
        if (currentEndData == null) {
            return false;
        }
        if (lastHandledGameEndData != null && (currentEndData == lastHandledGameEndData || currentEndData.equals(lastHandledGameEndData))) {
            return true;
        }
        if (lastHandledGameId != null && isSameGameId(lastHandledGameId, currentEndData)) {
            return true;
        }
        String currentUuid = extractPureUuid(currentEndData);
        if (currentUuid != null && lastHandledGameUuid != null && currentUuid.equalsIgnoreCase(lastHandledGameUuid)) {
            return true;
        }
        return false;
    }

    /**
     * Called directly from Smali bytecode in RealGameViewModel.Gb(GameEndData, GameplayAnimationPreferences).
     * Instant 0ms callback whenever ANY game ends (Live chess, Daily, Bot, Checkmate, Timeout, Resign, Draw).
     */
    public static void onGameOverNative(final Object viewModel, final Object gameEndData) {
        Context ctx = StockfishExtension.getContext();
        if (ctx == null) {
            Activity act = StockfishExtension.getCurrentActivity();
            if (act != null) ctx = act.getApplicationContext();
        }
        if (ctx != null && !StockfishSettings.isAutoFindMatchEnabled(ctx)) {
            NnvcLogger.d(TAG, "Auto Find Match is disabled in settings. Skipping native game over.");
            return;
        }

        NnvcLogger.i(TAG, "Native game over detected via RealGameViewModel.Gb bytecode hook!");
        if (viewModel != null) {
            lastNativeViewModelRef = new WeakReference<>(viewModel);
            cancelCelebrationReinflationFromVm(viewModel);
        }

        postOnMain(() -> {
            Activity activity = StockfishExtension.getCurrentActivity();
            if (activity != null) {
                onGameOverWithContext(activity, viewModel, gameEndData);
            } else {
                NnvcLogger.w(TAG, "Current Activity is null during onGameOverNative, scheduling 300ms retry...");
                postDelayedOnMain(() -> {
                    Activity retryAct = StockfishExtension.getCurrentActivity();
                    if (retryAct != null) {
                        onGameOverWithContext(retryAct, viewModel, gameEndData);
                    } else if (viewModel != null) {
                        NnvcLogger.w(TAG, "No activity available after delay, triggering ViewModel directly.");
                        triggerViaViewModelInstance(viewModel);
                    }
                }, 300L);
            }
        });
    }

    /**
     * Called when game over is detected. Initiates countdown if enabled.
     */
    public static void onGameOver(final Activity activity) {
        if (activity == null) return;
        Object vm = getRealGameViewModel(activity);
        Object endData = getCurrentGameEndData(activity);
        onGameOverWithContext(activity, vm, endData);
    }

    public static void onGameOverWithContext(final Activity activity, final Object viewModel, final Object gameEndData) {
        if (activity == null) return;
        if (!StockfishSettings.isAutoFindMatchEnabled(activity)) {
            cancelCountdown("Auto find disabled");
            currentState = State.IDLE;
            return;
        }

        // Cancel celebration coroutine popup immediately to prevent race condition
        if (viewModel != null) {
            cancelCelebrationReinflationFromVm(viewModel);
        } else {
            cancelCelebrationReinflation(activity);
        }

        // Lightweight background GC after match ends to keep heap clean across long AFK runs
        new Thread(() -> {
            try {
                System.gc();
            } catch (Throwable ignored) {}
        }).start();

        if (currentState == State.COUNTDOWN || currentState == State.SEEKING) {
            return; // Already in progress
        }

        Object currentEndData = gameEndData != null ? gameEndData : getCurrentGameEndData(activity);
        Object gameId = null;
        String currentUuid = null;
        if (currentEndData != null) {
            gameId = extractInnerGameId(currentEndData);
            currentUuid = extractPureUuid(gameId);
        } else {
            Object vm = viewModel != null ? viewModel : getRealGameViewModel(activity);
            if (vm != null) {
                gameId = getCurrentGameId(vm);
                currentUuid = extractPureUuid(gameId);
            }
        }

        if (lastHandledGameId != null && gameId != null && isSameGameId(lastHandledGameId, gameId)) {
            return;
        }
        if (currentUuid != null && lastHandledGameUuid != null && currentUuid.equalsIgnoreCase(lastHandledGameUuid)) {
            return;
        }
        if (currentEndData != null && lastHandledGameEndData != null && (currentEndData == lastHandledGameEndData || currentEndData.equals(lastHandledGameEndData))) {
            return;
        }

        if (gameId != null) {
            lastHandledGameId = gameId;
        }
        if (currentUuid != null) {
            lastHandledGameUuid = currentUuid;
        }
        if (currentEndData != null) {
            lastHandledGameEndData = currentEndData;
        }

        activeActivityRef = new WeakReference<>(activity);
        currentState = State.COUNTDOWN;
        remainingSeconds = COUNTDOWN_DURATION_SEC;
        userExplicitlyCancelled = false;
        seekRetryCount = 0;

        postOnMain(() -> {
            try {
                showCountdownBanner(activity, remainingSeconds);
            } catch (Throwable t) {
                NnvcLogger.d(TAG, "Failed to show banner: " + t.getMessage());
            }
            startCountdownTimer(activity);
        });
    }

    public static void resetHandledGameEndData() {
        lastHandledGameId = null;
        lastHandledGameUuid = null;
        lastHandledGameEndData = null;
    }

    /**
     * Called when a new game starts (FEN startpos or board reset).
     */
    public static void onNewGameStarted() {
        userExplicitlyCancelled = false;
        seekRetryCount = 0;
        if (seekingWatchdogRunnable != null) {
            removeCallbacksOnMain(seekingWatchdogRunnable);
            seekingWatchdogRunnable = null;
        }
        cancelCountdown("New game started");
        resetHandledGameEndData();
        currentState = State.PLAYING;
    }

    /**
     * Cancels the active countdown.
     */
    public static void cancelCountdown(String reason) {
        cancelCountdown(null, reason);
    }

    public static void cancelCountdown(Activity triggeringActivity, String reason) {
        if (currentState == State.SEEKING && "Activity paused".equals(reason)) {
            // Game activity paused to show the seek/match screen (WaitGameActivity).
            // Keep seeking state and watchdog intact!
            return;
        }

        Activity activeAct = activeActivityRef.get();
        if (triggeringActivity != null && activeAct != null && triggeringActivity != activeAct) {
            // A different activity (e.g. WaitGameActivity, Dialog, Ad) paused/destroyed.
            // Do NOT cancel active countdown on the game activity!
            return;
        }

        if (countdownRunnable != null) {
            removeCallbacksOnMain(countdownRunnable);
            countdownRunnable = null;
        }
        if (seekingWatchdogRunnable != null) {
            removeCallbacksOnMain(seekingWatchdogRunnable);
            seekingWatchdogRunnable = null;
        }

        Activity activity = activeAct != null ? activeAct : triggeringActivity;
        if (activity != null) {
            postOnMain(() -> removeCountdownBanner(activity));
        }

        if (currentState == State.COUNTDOWN) {
            NnvcLogger.i(TAG, "Cancelled Auto Find countdown (" + reason + ")");
        }
        currentState = State.IDLE;
    }

    /**
     * Called when matchmaking is cancelled or returns to the game board without starting a new match.
     * Re-initiates the countdown sequence so the user can automatically find another match.
     */
    public static void onSeekingInterrupted(final Activity activity) {
        if (activity == null || userExplicitlyCancelled) return;
        NnvcLogger.i(TAG, "⚡ [AUTO RE-QUEUE] Match search cancelled/interrupted. Re-queuing matchmaking immediately (0ms)...");
        if (seekingWatchdogRunnable != null) {
            removeCallbacksOnMain(seekingWatchdogRunnable);
            seekingWatchdogRunnable = null;
        }
        triggerImmediateReSeek(activity);
    }

    private static void startCountdownTimer(final Activity activity) {
        if (countdownRunnable != null) {
            removeCallbacksOnMain(countdownRunnable);
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
                    postDelayedOnMain(this, 1000L);
                } else {
                    // Countdown complete -> Trigger new match
                    removeCountdownBanner(activity);
                    triggerAutoFind(activity);
                }
            }
        };

        postDelayedOnMain(countdownRunnable, 1000L);
    }

    private static void triggerAutoFind(final Activity activity) {
        userExplicitlyCancelled = false;
        consecutiveMatchesCount++;
        if (consecutiveMatchesCount >= RECREATE_AFTER_MATCHES && activity != null && !activity.isFinishing()) {
            consecutiveMatchesCount = 0;
            NnvcLogger.i(TAG, "Đã chơi liên tiếp " + RECREATE_AFTER_MATCHES + " ván. Tái tạo Activity để giải phóng RAM & làm mới WebSocket socket.");
            currentState = State.SEEKING;
            seekStartTimeMs = getUptimeMs();
            scheduleSeekingWatchdog(activity);
            vibrate(activity, 25);
            try {
                Toast.makeText(activity, I18n.get(activity, "toast_newgame"), Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
            activity.runOnUiThread(() -> {
                try {
                    activity.recreate();
                    postDelayedOnMain(() -> {
                        Activity cur = StockfishExtension.getCurrentActivity();
                        executeTrigger(cur != null ? cur : activity);
                    }, 600L);
                } catch (Throwable t) {
                    NnvcLogger.e(TAG, "Activity recreate failed: " + t.getMessage());
                    executeTrigger(activity);
                }
            });
            return;
        }

        currentState = State.SEEKING;
        seekStartTimeMs = getUptimeMs();
        vibrate(activity, 25);

        try {
            Toast.makeText(activity, I18n.get(activity, "toast_newgame"), Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {}

        NnvcLogger.i(TAG, "Triggering Auto Find match...");

        // Start Auto Re-queue / Re-seek watchdog when matchmaking drops or is cancelled
        scheduleSeekingWatchdog(activity);

        postOnMain(() -> executeTrigger(activity));
    }

    public static void onSeekActivityCreated(Activity activity) {
        if (activity == null) return;
        activeSeekActivityRef = new WeakReference<>(activity);
        // Do NOT reset isDismissingByAutoFind or clear dismissingSeekActivities here!
        // Previous seek activities being dismissed programmatically are still undergoing onDestroy in Android OS.
        dismissingSeekActivities.remove(activity);
        NnvcLogger.i(TAG, "⚡ [NATIVE HOOK] Seek screen created: " + activity.getClass().getSimpleName());
    }

    public static void onSeekActivityDestroyed(Activity activity) {
        if (activity == null) return;
        if (activeSeekActivityRef != null && activeSeekActivityRef.get() == activity) {
            activeSeekActivityRef = new WeakReference<>(null);
        }
        NnvcLogger.i(TAG, "⚡ [NATIVE HOOK] Seek screen destroyed: " + activity.getClass().getSimpleName());

        boolean isDismissedByUs = isDismissingByAutoFind || dismissingSeekActivities.contains(activity);
        if (isDismissedByUs) {
            // Dismissed programmatically by AutoFind for immediate re-queue; do not reset state.
            dismissingSeekActivities.remove(activity);
            if (dismissingSeekActivities.isEmpty()) {
                isDismissingByAutoFind = false;
                if (resetDismissingFlagRunnable != null) {
                    removeCallbacksOnMain(resetDismissingFlagRunnable);
                    resetDismissingFlagRunnable = null;
                }
            }
            return;
        }

        // Screen was closed by user manual action (back button / cancel button)
        if (currentState == State.SEEKING) {
            NnvcLogger.i(TAG, "⚡ [NATIVE HOOK] Seek screen closed manually by user. Resetting SEEKING to IDLE.");
            userExplicitlyCancelled = true;
            if (seekingWatchdogRunnable != null) {
                removeCallbacksOnMain(seekingWatchdogRunnable);
                seekingWatchdogRunnable = null;
            }
            currentState = State.IDLE;
        }
    }

    public static void onNativeChallengeCancelled() {
        NnvcLogger.w(TAG, "⚡ [NATIVE HOOK] Server cancelled challenge / seek. Re-queuing matchmaking immediately (0ms)...");
        if (currentState != State.SEEKING && currentState != State.COUNTDOWN) {
            return;
        }
        userExplicitlyCancelled = false;
        postOnMain(() -> {
            if (seekingWatchdogRunnable != null) {
                removeCallbacksOnMain(seekingWatchdogRunnable);
                seekingWatchdogRunnable = null;
            }
            Activity act = (activeActivityRef != null && activeActivityRef.get() != null && !activeActivityRef.get().isFinishing() && !(Build.VERSION.SDK_INT >= 17 && activeActivityRef.get().isDestroyed()))
                    ? activeActivityRef.get()
                    : StockfishExtension.getCurrentActivity();
            triggerImmediateReSeek(act);
        });
    }

    public static void onNativeSeekError(int errorCode) {
        NnvcLogger.w(TAG, "⚡ [NATIVE HOOK] Server seek error (code=" + errorCode + "). Re-queuing matchmaking immediately (0ms)...");
        if (currentState != State.SEEKING && currentState != State.COUNTDOWN) {
            return;
        }
        userExplicitlyCancelled = false;
        postOnMain(() -> {
            if (seekingWatchdogRunnable != null) {
                removeCallbacksOnMain(seekingWatchdogRunnable);
                seekingWatchdogRunnable = null;
            }
            Activity act = (activeActivityRef != null && activeActivityRef.get() != null && !activeActivityRef.get().isFinishing() && !(Build.VERSION.SDK_INT >= 17 && activeActivityRef.get().isDestroyed()))
                    ? activeActivityRef.get()
                    : StockfishExtension.getCurrentActivity();
            triggerImmediateReSeek(act);
        });
    }

    public static void dismissActiveSeekActivity() {
        isDismissingByAutoFind = true;
        Activity seekAct = activeSeekActivityRef != null ? activeSeekActivityRef.get() : null;
        if (seekAct != null) {
            dismissingSeekActivities.add(seekAct);
        }
        try {
            boolean dismissed = false;
            try {
                Class<?> seekClz = Class.forName("com.chess.realchess.ui.wait.LiveGameSeekV6Activity");
                try {
                    Field f = seekClz.getDeclaredField("sActiveInstance");
                    f.setAccessible(true);
                    Object inst = f.get(null);
                    if (inst instanceof Activity) {
                        dismissingSeekActivities.add((Activity) inst);
                    }
                } catch (Throwable ignored) {}

                Method m = seekClz.getMethod("dismissSeekNative");
                Object res = m.invoke(null);
                if (res instanceof Boolean && (Boolean) res) {
                    NnvcLogger.i(TAG, "⚡ [SEEK DISMISS] LiveGameSeekV6Activity dismissed via dismissSeekNative()!");
                    dismissed = true;
                }
            } catch (Throwable ignored) {}

            if (!dismissed) {
                if (seekAct != null && !seekAct.isFinishing()) {
                    NnvcLogger.i(TAG, "⚡ [SEEK DISMISS] Dismissing tracked seek activity: " + seekAct.getClass().getSimpleName());
                    seekAct.runOnUiThread(() -> {
                        try {
                            seekAct.finish();
                        } catch (Throwable t) {
                            NnvcLogger.e(TAG, "Failed to finish seek activity: " + t.getMessage());
                        }
                    });
                }
            }
        } catch (Throwable t) {
            NnvcLogger.e(TAG, "Error in dismissActiveSeekActivity: " + t.getMessage());
        } finally {
            if (resetDismissingFlagRunnable != null) {
                removeCallbacksOnMain(resetDismissingFlagRunnable);
            }
            resetDismissingFlagRunnable = () -> {
                isDismissingByAutoFind = false;
                dismissingSeekActivities.clear();
                resetDismissingFlagRunnable = null;
            };
            postDelayedOnMain(resetDismissingFlagRunnable, 2500L);
        }
    }

    private static void scheduleSeekingWatchdog(final Activity activity) {
        if (seekingWatchdogRunnable != null) {
            removeCallbacksOnMain(seekingWatchdogRunnable);
        }

        // Seeking watchdog (20s) in case matchmaking hangs or server drops challenge
        seekingWatchdogRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentState != State.SEEKING || userExplicitlyCancelled) {
                    return;
                }
                NnvcLogger.w(TAG, "⚡ [AUTO RE-QUEUE] Seeking timeout (20s) without new game.");
                Activity currentAct = StockfishExtension.getCurrentActivity();
                Activity actToUse = (currentAct != null && !currentAct.isFinishing() && !(Build.VERSION.SDK_INT >= 17 && currentAct.isDestroyed()))
                        ? currentAct : activity;
                triggerImmediateReSeek(actToUse);
            }
        };

        postDelayedOnMain(seekingWatchdogRunnable, SEEKING_TIMEOUT_MS);
    }

    public static void triggerImmediateReSeek(final Activity activity) {
        if (userExplicitlyCancelled) {
            NnvcLogger.i(TAG, "Re-queue aborted: user explicitly cancelled.");
            currentState = State.IDLE;
            return;
        }

        if (seekRetryCount >= MAX_SEEK_RETRIES) {
            NnvcLogger.w(TAG, "Max seeking retries reached (" + MAX_SEEK_RETRIES + "). Resetting to IDLE.");
            dismissActiveSeekActivity();
            currentState = State.IDLE;
            return;
        }

        seekRetryCount++;
        NnvcLogger.w(TAG, "⚡ [AUTO RE-QUEUE] Re-queuing match (attempt " + seekRetryCount + "/" + MAX_SEEK_RETRIES + ")...");
        dismissActiveSeekActivity();
        resetHandledGameEndData();
        currentState = State.SEEKING;
        seekStartTimeMs = getUptimeMs();

        Activity targetAct = (activity != null && !activity.isFinishing() && !(Build.VERSION.SDK_INT >= 17 && activity.isDestroyed()))
                ? activity : StockfishExtension.getCurrentActivity();

        if (targetAct != null && !targetAct.isFinishing() && !(Build.VERSION.SDK_INT >= 17 && targetAct.isDestroyed())) {
            scheduleSeekingWatchdog(targetAct);
            executeTrigger(targetAct);
        } else {
            postDelayedOnMain(() -> {
                if (currentState != State.SEEKING || userExplicitlyCancelled) return;
                Activity fallbackAct = StockfishExtension.getCurrentActivity();
                if (fallbackAct != null && !fallbackAct.isFinishing() && !(Build.VERSION.SDK_INT >= 17 && fallbackAct.isDestroyed())) {
                    scheduleSeekingWatchdog(fallbackAct);
                    executeTrigger(fallbackAct);
                } else {
                    boolean nativeOk = triggerNewGameNative();
                    if (nativeOk) {
                        scheduleSeekingWatchdog(fallbackAct != null ? fallbackAct : activity);
                    } else {
                        NnvcLogger.e(TAG, "❌ [AUTO RE-QUEUE] Could not locate active activity or native controller. Resetting to IDLE.");
                        currentState = State.IDLE;
                    }
                }
            }, 250L);
        }
    }

    public static boolean triggerNewGameNative() {
        try {
            Class<?> vmClz = Class.forName("com.chess.realchess.ui.game.RealGameViewModel");
            Method m = vmClz.getMethod("triggerNewGameNative");
            Object res = m.invoke(null);
            if (res instanceof Boolean && (Boolean) res) {
                NnvcLogger.i(TAG, "⚡ Auto Find triggered directly via RealGameViewModel.triggerNewGameNative()!");
                return true;
            }
        } catch (Throwable t) {
            NnvcLogger.e(TAG, "triggerNewGameNative error: " + t.getMessage(), t);
        }
        return false;
    }

    private static boolean executeTrigger(final Activity activity) {
        // Tầng 0: Full Native Lifecycle via Activity Controller (includes gameOverV6Listener.e(), UI Compose cleanup x6() & Ka())
        boolean triggered = false;
        if (activity != null) {
            triggered = triggerViaViewModel(activity);
        }

        // Tầng 1: Direct Cached Native ViewModel
        if (!triggered && lastNativeViewModelRef != null) {
            Object vm = lastNativeViewModelRef.get();
            if (vm != null) {
                triggered = triggerViaViewModelInstance(vm);
                if (triggered) {
                    NnvcLogger.i(TAG, "Auto Find triggered via cached native ViewModel!");
                }
            }
        }

        // Tầng 2: Direct Bytecode Hook RealGameViewModel.triggerNewGameNative()
        if (!triggered) {
            triggered = triggerNewGameNative();
        }

        if (triggered) {
            NnvcLogger.i(TAG, "Auto Find match triggered successfully!");
        } else {
            NnvcLogger.i(TAG, "Auto Find match trigger: could not locate native controller on first attempt. Scheduling retry in 400ms...");
            postDelayedOnMain(() -> {
                if (currentState == State.SEEKING && !userExplicitlyCancelled) {
                    boolean retry = (activity != null) ? triggerViaViewModel(activity) : false;
                    if (!retry && lastNativeViewModelRef != null) {
                        Object vm = lastNativeViewModelRef.get();
                        if (vm != null) retry = triggerViaViewModelInstance(vm);
                    }
                    if (!retry) {
                        retry = triggerNewGameNative();
                    }
                    if (retry) {
                        NnvcLogger.i(TAG, "Auto Find match triggered on retry!");
                    } else {
                        NnvcLogger.e(TAG, "❌ Auto Find match FAILED: Native RealGameViewModel không khả dụng (Zero-Fallback Architecture).");
                    }
                }
            }, 400L);
        }
        return triggered;
    }

    /**
     * Tầng 1: Invokes RealGameActivity UI cleanup & RealGameViewModel.Ka() on UI thread.
     */
    public static boolean triggerViaViewModel(final Activity activity) {
        if (activity == null) return false;
        boolean actionExecuted = false;
        try {
            // Cancel celebration re-inflation first
            cancelCelebrationReinflation(activity);

            // 1. Invoke gameOverV6Listener.e() directly (matches exact Chess.com New Game click action)
            try {
                Object listener = null;
                Field fListener = findFieldByName(activity.getClass(), "t0", "gameOverV6Listener");
                if (fListener != null) {
                    fListener.setAccessible(true);
                    listener = fListener.get(activity);
                }
                if (listener == null) {
                    // Dynamic scan for any listener field with method e()
                    for (Field f : activity.getClass().getDeclaredFields()) {
                        try {
                            f.setAccessible(true);
                            Object val = f.get(activity);
                            if (val != null) {
                                String cn = val.getClass().getName();
                                if (cn.contains("RealGameActivity$b") || cn.contains("GameOverV6") || cn.contains("v6.k1")) {
                                    listener = val;
                                    break;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                if (listener != null) {
                    Method eMethod = findMethodByName(listener.getClass(), "e");
                    if (eMethod != null) {
                        eMethod.setAccessible(true);
                        eMethod.invoke(listener);
                        NnvcLogger.d(TAG, "Invoked gameOverV6Listener.e() directly.");
                        return true;
                    }
                }
            } catch (Throwable t) {
                NnvcLogger.d(TAG, "gameOverV6Listener.e() failed: " + t.getMessage());
            }

            // 2. Direct x6() call on RealGameActivity (cleans up gameOverOverlayView & modal)
            try {
                Method x6 = findMethodByName(activity.getClass(), "x6");
                if (x6 != null) {
                    x6.setAccessible(true);
                    x6.invoke(activity);
                    NnvcLogger.d(TAG, "Invoked RealGameActivity.x6() directly.");
                    actionExecuted = true;
                }
            } catch (Throwable t) {
                NnvcLogger.d(TAG, "RealGameActivity.x6() failed: " + t.getMessage());
            }

            // 3. Fallback: Manually remove gameOverOverlayView / m0
            try {
                Field fGov = findFieldByName(activity.getClass(), "m0", "gameOverOverlayView");
                if (fGov != null) {
                    fGov.setAccessible(true);
                    Object gov = fGov.get(activity);
                    if (gov instanceof View) {
                        View v = (View) gov;
                        if (v.getParent() instanceof ViewGroup) {
                            ((ViewGroup) v.getParent()).removeView(v);
                        }
                    }
                    fGov.set(activity, null);
                }
            } catch (Throwable ignored) {}

            // 4. ViewModel cleanup Xa(), ub() and trigger Ka()
            try {
                Object vm = getRealGameViewModel(activity);
                if (vm != null) {
                    cancelCelebrationReinflationFromVm(vm);
                    Method xaMethod = findMethodByName(vm.getClass(), "Xa");
                    if (xaMethod != null) {
                        xaMethod.setAccessible(true);
                        xaMethod.invoke(vm);
                    }
                    Method ubMethod = findMethodByName(vm.getClass(), "ub");
                    if (ubMethod != null) {
                        ubMethod.setAccessible(true);
                        ubMethod.invoke(vm);
                    }
                    // Restore gameEndData into vm.i0 if null so Ka() doesn't call Q8() (_closeGame)
                    try {
                        Field fEndData = findFieldByName(vm.getClass(), "gameEndData", "i0");
                        if (fEndData != null) {
                            fEndData.setAccessible(true);
                            Object currentEndData = fEndData.get(vm);
                            if (currentEndData == null && lastHandledGameEndData != null) {
                                fEndData.set(vm, lastHandledGameEndData);
                                NnvcLogger.i(TAG, "Restored lastHandledGameEndData into RealGameViewModel.i0 before Ka()");
                            }
                        }
                    } catch (Throwable t) {
                        NnvcLogger.d(TAG, "Failed to restore gameEndData: " + t.getMessage());
                    }

                    Method kaMethod = findMethodByName(vm.getClass(), "Ka");
                    if (kaMethod != null) {
                        kaMethod.setAccessible(true);
                        kaMethod.invoke(vm);
                        NnvcLogger.d(TAG, "Invoked RealGameViewModel.Ka() directly.");
                        actionExecuted = true;
                    }
                }
            } catch (Throwable t) {
                NnvcLogger.d(TAG, "ViewModel Ka() trigger failed: " + t.getMessage());
            }
        } catch (Throwable t) {
            NnvcLogger.d(TAG, "triggerViaViewModel failed: " + t.getMessage());
        }
        return actionExecuted;
    }

    public static boolean triggerViaViewModelInstance(Object vm) {
        if (vm == null) return false;
        try {
            cancelCelebrationReinflationFromVm(vm);
            Method xaMethod = findMethodByName(vm.getClass(), "Xa");
            if (xaMethod != null) {
                xaMethod.setAccessible(true);
                xaMethod.invoke(vm);
            }
            Method ubMethod = findMethodByName(vm.getClass(), "ub");
            if (ubMethod != null) {
                ubMethod.setAccessible(true);
                ubMethod.invoke(vm);
            }

            // Restore gameEndData into vm.i0 if null so Ka() doesn't call Q8() (_closeGame)
            try {
                Field fEndData = findFieldByName(vm.getClass(), "gameEndData", "i0");
                if (fEndData != null) {
                    fEndData.setAccessible(true);
                    Object currentEndData = fEndData.get(vm);
                    if (currentEndData == null && lastHandledGameEndData != null) {
                        fEndData.set(vm, lastHandledGameEndData);
                        NnvcLogger.i(TAG, "Restored lastHandledGameEndData into RealGameViewModel.i0 before Ka()");
                    }
                }
            } catch (Throwable t) {
                NnvcLogger.d(TAG, "Failed to restore gameEndData: " + t.getMessage());
            }

            Method kaMethod = findMethodByName(vm.getClass(), "Ka");
            if (kaMethod != null) {
                kaMethod.setAccessible(true);
                kaMethod.invoke(vm);
                NnvcLogger.d(TAG, "Invoked RealGameViewModel.Ka() directly on native instance.");
                return true;
            }
        } catch (Throwable t) {
            NnvcLogger.d(TAG, "triggerViaViewModelInstance failed: " + t.getMessage());
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

        // Scan methods with 0 params returning RealGameViewModel
        try {
            for (Method m : activity.getClass().getMethods()) {
                if (m.getParameterTypes().length == 0 && m.getReturnType().getName().contains("RealGameViewModel")) {
                    m.setAccessible(true);
                    Object vm = m.invoke(activity);
                    if (vm != null) return vm;
                }
            }
        } catch (Throwable ignored) {}

        // Scan fields for ViewModel or Lazy ViewModel
        try {
            Class<?> c = activity.getClass();
            while (c != null && !c.getName().equals("android.app.Activity")) {
                for (Field f : c.getDeclaredFields()) {
                    try {
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
                    } catch (Throwable ignored) {}
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Checks if the active game is in Game Over state via RealGameViewModel reflection.
     * Works natively on modern Jetpack Compose Chess.com (4.10.20) where legacy Dialogs are absent.
     * Accurately prevents false-positives by verifying gameId match and non-null UI popups.
     */
    public static boolean isViewModelGameOver(Activity activity) {
        if (activity == null) return false;
        try {
            // 1. Inspect activity fields for gameOverOverlayView (m0)
            Field fGov = findFieldByName(activity.getClass(), "gameOverOverlayView", "m0");
            if (fGov != null) {
                try {
                    fGov.setAccessible(true);
                    Object gov = fGov.get(activity);
                    if (gov instanceof View) {
                        View gv = (View) gov;
                        if (gv.getVisibility() == View.VISIBLE && gv.getParent() != null) {
                            return true;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 2. Multi-layer ViewModel GameEndData / GameOver state check
            Object endData = getCurrentGameEndData(activity);
            if (endData != null) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static Object getCurrentGameId(Object vm) {
        if (vm == null) return null;
        try {
            Method mGameId = findMethodByName(vm.getClass(), "getGameId");
            if (mGameId != null) {
                mGameId.setAccessible(true);
                Object id = mGameId.invoke(vm);
                if (id != null) return id;
            }
        } catch (Throwable ignored) {}

        try {
            Field fGameId = findFieldByName(vm.getClass(), "gameId", "U");
            if (fGameId != null) {
                fGameId.setAccessible(true);
                Object id = fGameId.get(vm);
                if (id != null) return id;
            }
        } catch (Throwable ignored) {}

        // Fallback: Scan fields of type CompatId
        try {
            Class<?> c = vm.getClass();
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType().getName().contains("CompatId")) {
                        f.setAccessible(true);
                        Object val = f.get(vm);
                        if (val != null) return val;
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static void cancelCelebrationReinflation(Activity activity) {
        if (activity == null) return;
        Object vm = getRealGameViewModel(activity);
        if (vm != null) {
            cancelCelebrationReinflationFromVm(vm);
        }
    }

    public static void cancelCelebrationReinflationFromVm(Object vm) {
        if (vm == null) return;
        try {
            Method mLa = findMethodByName(vm.getClass(), "La");
            if (mLa != null) {
                mLa.setAccessible(true);
                if (mLa.getParameterTypes().length == 0) {
                    mLa.invoke(vm);
                } else if (mLa.getParameterTypes().length == 1 && mLa.getParameterTypes()[0] == boolean.class) {
                    mLa.invoke(vm, true);
                }
            }
        } catch (Throwable ignored) {}

        try {
            Field fZ0 = findFieldByName(vm.getClass(), "boardInteractedDuringGameOver", "Z0");
            if (fZ0 != null) {
                fZ0.setAccessible(true);
                fZ0.setBoolean(vm, true);
            }
        } catch (Throwable ignored) {}

        try {
            Method mSet = findMethodByName(vm.getClass(), "setBoardInteractedDuringGameOver");
            if (mSet != null) {
                mSet.setAccessible(true);
                if (mSet.getParameterTypes().length == 1 && mSet.getParameterTypes()[0] == boolean.class) {
                    mSet.invoke(vm, true);
                }
            }
        } catch (Throwable ignored) {}

        try {
            Method mXa = findMethodByName(vm.getClass(), "Xa");
            if (mXa != null) {
                mXa.setAccessible(true);
                mXa.invoke(vm);
            }
        } catch (Throwable ignored) {}
    }

    public static Object getCurrentGameEndData(Activity activity) {
        if (activity == null) return null;
        Object vm = getRealGameViewModel(activity);
        return getCurrentGameEndDataFromVm(vm);
    }

    public static Object getCurrentGameEndDataFromVm(Object vm) {
        if (vm == null) return null;

        Object curGameId = getCurrentGameId(vm);

        // 1. Inspect gameEndData field (field i0 or gameEndData)
        try {
            Field fEndData = findFieldByName(vm.getClass(), "gameEndData", "i0");
            if (fEndData != null) {
                fEndData.setAccessible(true);
                Object endData = fEndData.get(vm);
                if (endData != null) {
                    if (curGameId != null) {
                        if (isSameGameId(curGameId, endData)) {
                            return endData;
                        }
                    } else if (isPopupActive(vm)) {
                        return endData;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Inspect gameOver StateFlow (getter H9() or fields _gameOver a1 / gameOver b1)
        try {
            Method h9 = findMethodByName(vm.getClass(), "H9");
            if (h9 != null) {
                h9.setAccessible(true);
                Object flow = h9.invoke(vm);
                if (flow != null) {
                    Method getValue = flow.getClass().getMethod("getValue");
                    Object endData = getValue.invoke(flow);
                    if (endData != null) {
                        if (curGameId != null) {
                            if (isSameGameId(curGameId, endData)) {
                                return endData;
                            }
                        } else if (isPopupActive(vm)) {
                            return endData;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            Field fGameOver = findFieldByName(vm.getClass(), "_gameOver", "gameOver", "a1", "b1");
            if (fGameOver != null) {
                fGameOver.setAccessible(true);
                Object flow = fGameOver.get(vm);
                if (flow != null) {
                    Method getValue = flow.getClass().getMethod("getValue");
                    Object endData = getValue.invoke(flow);
                    if (endData != null) {
                        if (curGameId != null) {
                            if (isSameGameId(curGameId, endData)) {
                                return endData;
                            }
                        } else if (isPopupActive(vm)) {
                            return endData;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 3. Inspect _showGameOverPopup StateFlow (field X0 / getter oa())
        try {
            Method oa = findMethodByName(vm.getClass(), "oa");
            if (oa != null) {
                oa.setAccessible(true);
                Object flow = oa.invoke(vm);
                if (flow != null) {
                    Method getValue = flow.getClass().getMethod("getValue");
                    Object pair = getValue.invoke(flow);
                    if (pair != null) {
                        Object endData = extractFirstFromPair(pair);
                        if (endData != null) {
                            if (curGameId != null) {
                                if (isSameGameId(curGameId, endData)) {
                                    return endData;
                                }
                            } else {
                                return endData;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            Field fPopup = findFieldByName(vm.getClass(), "_showGameOverPopup", "showGameOverPopup", "X0", "Y0");
            if (fPopup != null) {
                fPopup.setAccessible(true);
                Object flow = fPopup.get(vm);
                if (flow != null) {
                    Method getValue = flow.getClass().getMethod("getValue");
                    Object pair = getValue.invoke(flow);
                    if (pair != null) {
                        Object endData = extractFirstFromPair(pair);
                        if (endData != null) {
                            if (curGameId != null) {
                                if (isSameGameId(curGameId, endData)) {
                                    return endData;
                                }
                            } else {
                                return endData;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static boolean isPopupActive(Object vm) {
        if (vm == null) return false;
        try {
            Method oa = findMethodByName(vm.getClass(), "oa");
            if (oa != null) {
                oa.setAccessible(true);
                Object flow = oa.invoke(vm);
                if (flow != null) {
                    Method getValue = flow.getClass().getMethod("getValue");
                    if (getValue.invoke(flow) != null) return true;
                }
            }
        } catch (Throwable ignored) {}
        try {
            Field fPopup = findFieldByName(vm.getClass(), "_showGameOverPopup", "showGameOverPopup", "X0", "Y0");
            if (fPopup != null) {
                fPopup.setAccessible(true);
                Object flow = fPopup.get(vm);
                if (flow != null) {
                    Method getValue = flow.getClass().getMethod("getValue");
                    if (getValue.invoke(flow) != null) return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static Object extractFirstFromPair(Object pair) {
        if (pair == null) return null;
        try {
            Method getFirst = findMethodByName(pair.getClass(), "getFirst", "a");
            if (getFirst != null) {
                getFirst.setAccessible(true);
                Object fd = getFirst.invoke(pair);
                if (fd != null) return fd;
            }
        } catch (Throwable ignored) {}
        try {
            Field fFirst = findFieldByName(pair.getClass(), "first", "a");
            if (fFirst != null) {
                fFirst.setAccessible(true);
                Object fd = fFirst.get(pair);
                if (fd != null) return fd;
            }
        } catch (Throwable ignored) {}
        return pair;
    }

    public static boolean isSameGameId(Object curIdObj, Object endIdObj) {
        if (curIdObj == null || endIdObj == null) {
            return false;
        }
        if (curIdObj == endIdObj || curIdObj.equals(endIdObj)) {
            return true;
        }

        Object curId = extractInnerGameId(curIdObj);
        Object endId = extractInnerGameId(endIdObj);
        if (curId == null || endId == null) {
            return false;
        }
        if (curId == endId || curId.equals(endId)) {
            return true;
        }

        // 1. UUID comparison
        String curUuid = extractPureUuid(curId);
        String endUuid = extractPureUuid(endId);
        if (curUuid != null && endUuid != null && !curUuid.isEmpty() && !endUuid.isEmpty()) {
            if (curUuid.equalsIgnoreCase(endUuid)) {
                return true;
            }
        }

        // 2. Numeric Long ID comparison
        Long curNum = extractNumericId(curId);
        Long endNum = extractNumericId(endId);
        if (curNum != null && endNum != null && curNum.longValue() > 0 && endNum.longValue() > 0) {
            if (curNum.longValue() == endNum.longValue()) {
                return true;
            }
        }

        // 3. Fallback: string representation
        String s1 = curId.toString();
        String s2 = endId.toString();
        if (!s1.isEmpty() && s1.equalsIgnoreCase(s2)) {
            return true;
        }

        return false;
    }

    public static Object extractInnerGameId(Object obj) {
        if (obj == null) return null;
        try {
            Method m = findMethodByName(obj.getClass(), "getGameId");
            if (m != null) {
                m.setAccessible(true);
                Object res = m.invoke(obj);
                if (res != null) return res;
            }
        } catch (Throwable ignored) {}
        try {
            Field f = findFieldByName(obj.getClass(), "gameId");
            if (f != null) {
                f.setAccessible(true);
                Object res = f.get(obj);
                if (res != null) return res;
            }
        } catch (Throwable ignored) {}
        return obj;
    }

    public static String extractPureUuid(Object obj) {
        if (obj == null) return null;
        Object inner = extractInnerGameId(obj);
        if (inner == null) return null;

        // 1. Try getUuid()
        try {
            Method m = findMethodByName(inner.getClass(), "getUuid");
            if (m != null) {
                m.setAccessible(true);
                Object res = m.invoke(inner);
                if (res instanceof String && !((String) res).isEmpty()) {
                    return (String) res;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try getStringUuid()
        try {
            Method m = findMethodByName(inner.getClass(), "getStringUuid");
            if (m != null) {
                m.setAccessible(true);
                Object res = m.invoke(inner);
                if (res instanceof String && !((String) res).isEmpty()) {
                    return (String) res;
                }
            }
        } catch (Throwable ignored) {}

        // 3. If CompatId.Uuid, getId() is UUID
        try {
            Method m = findMethodByName(inner.getClass(), "getId");
            if (m != null) {
                m.setAccessible(true);
                Object res = m.invoke(inner);
                if (res instanceof String) {
                    String str = (String) res;
                    if (str.contains("-") || !str.matches("^\\d+$")) {
                        return str;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 4. If String itself looks like UUID
        if (inner instanceof String) {
            String str = (String) inner;
            if (str.contains("-") || !str.matches("^\\d+$")) {
                return str;
            }
        }
        return null;
    }

    public static Long extractNumericId(Object obj) {
        if (obj == null) return null;
        Object inner = extractInnerGameId(obj);
        if (inner == null) return null;

        if (inner instanceof Number) {
            return ((Number) inner).longValue();
        }

        // 1. Try getLongId()
        try {
            Method m = findMethodByName(inner.getClass(), "getLongId");
            if (m != null) {
                m.setAccessible(true);
                Object res = m.invoke(inner);
                if (res instanceof Number) {
                    return ((Number) res).longValue();
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try getId() returning Long or numeric String
        try {
            Method m = findMethodByName(inner.getClass(), "getId");
            if (m != null) {
                m.setAccessible(true);
                Object res = m.invoke(inner);
                if (res instanceof Number) {
                    return ((Number) res).longValue();
                } else if (res instanceof String) {
                    String s = (String) res;
                    if (s.matches("^\\d+$")) {
                        try {
                            return Long.parseLong(s);
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 3. If String is pure digits
        if (inner instanceof String) {
            String s = (String) inner;
            if (s.matches("^\\d+$")) {
                try {
                    return Long.parseLong(s);
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    public static String extractGameUuid(Object idObj) {
        if (idObj == null) return null;
        String uuid = extractPureUuid(idObj);
        if (uuid != null && !uuid.isEmpty()) {
            return uuid;
        }
        Long num = extractNumericId(idObj);
        if (num != null) {
            return num.toString();
        }
        Object inner = extractInnerGameId(idObj);
        return inner != null ? inner.toString() : null;
    }

    // ── UI Countdown Card (Extension Parity) ──────────────────────────────────

    public static class CountdownRingView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcRect = new RectF();
        private float currentFraction = 1.0f;
        private int maxSeconds = COUNTDOWN_DURATION_SEC;
        private ValueAnimator animator = null;

        public CountdownRingView(Context context) {
            super(context);
            init(context);
        }

        private void init(Context context) {
            float density = (context != null && context.getResources() != null)
                    ? context.getResources().getDisplayMetrics().density : 2f;
            bgPaint.setStyle(Paint.Style.STROKE);
            bgPaint.setColor(0x33787880);
            bgPaint.setStrokeWidth(5.5f * density);

            progressPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setColor(0xFF30D158); // Neon Emerald (#30d158)
            progressPaint.setStrokeWidth(5.5f * density);
            progressPaint.setStrokeCap(Paint.Cap.ROUND);

            textPaint.setColor(0xFF30D158); // Clean neon emerald digits
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            textPaint.setTextSize(32f * density);
        }

        public void startSweep(int totalSeconds) {
            stopSweep();
            this.maxSeconds = totalSeconds;
            this.currentFraction = 1.0f;
            try {
                animator = ValueAnimator.ofFloat(1.0f, 0.0f);
                animator.setDuration(totalSeconds * 1000L);
                animator.setInterpolator(new LinearInterpolator());
                animator.addUpdateListener(animation -> {
                    Object val = animation.getAnimatedValue();
                    if (val instanceof Float) {
                        currentFraction = (Float) val;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            postInvalidateOnAnimation();
                        } else {
                            postInvalidate();
                        }
                    }
                });
                animator.start();
            } catch (Throwable t) {
                currentFraction = 0f;
                postInvalidate();
            }
        }

        public void stopSweep() {
            if (animator != null) {
                try {
                    animator.cancel();
                } catch (Throwable ignored) {}
                animator = null;
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            stopSweep();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            float density = getResources().getDisplayMetrics().density;
            float stroke = 5.5f * density;
            float pad = stroke / 2f + 2f * density;
            arcRect.set(pad, pad, w - pad, h - pad);

            // Background circular track
            canvas.drawOval(arcRect, bgPaint);

            // Progress arc (starts at 12 o'clock = -90 deg, sweeps smoothly clockwise)
            float sweep = Math.max(0f, currentFraction * 360f);
            canvas.drawArc(arcRect, -90f, sweep, false, progressPaint);

            // Center number (smooth dynamic integer rounded up)
            int displaySec = (int) Math.ceil(currentFraction * maxSeconds);
            if (displaySec <= 0 && currentFraction > 0.01f) displaySec = 1;
            String numText = String.valueOf(displaySec);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textY = (h / 2f) - ((fm.ascent + fm.descent) / 2f);
            canvas.drawText(numText, w / 2f, textY, textPaint);
        }
    }

    private static void showCountdownBanner(final Activity activity, int seconds) {
        if (activity == null || activity.getWindow() == null) return;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        final ViewGroup root = (ViewGroup) decor;

        removeCountdownBanner(activity);

        float density = activity.getResources().getDisplayMetrics().density;

        // 1. Fullscreen translucent scrim backdrop (Extension modal overlay)
        final FrameLayout overlay = new FrameLayout(activity);
        overlay.setTag(BANNER_TAG);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setBackgroundColor(0x8C000000); // 55% dimmed black glass

        // 2. Centered Cyber Card
        final LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setClickable(true); // Absorb taps inside card
        int padH = (int) (26 * density);
        int padV = (int) (22 * density);
        card.setPadding(padH, padV, padH, padV);

        // Cyber glass card background matching Extension rgba(34,34,38,.86)
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0222226); // Deep Cyber glass 94%
        bg.setCornerRadius(22 * density);
        bg.setStroke((int) (1f * density), 0x2EFFFFFF); // Subtle white glass border
        card.setBackground(bg);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(50f);
        }

        // Header Title: "TÌM TRẬN MỚI" / "FIND NEW MATCH"
        TextView titleText = new TextView(activity);
        titleText.setTag("banner_title");
        titleText.setTextColor(0xFFF5F5F7);
        titleText.setTextSize(12.5f);
        titleText.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titleText.setText(I18n.get(activity, "cd_label"));
        titleText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lpTitle.bottomMargin = (int) (14 * density);
        card.addView(titleText, lpTitle);

        // Countdown Ring View (90dp x 90dp with continuous 60fps sweep)
        CountdownRingView ringView = new CountdownRingView(activity);
        ringView.setTag("banner_ring");
        ringView.startSweep(COUNTDOWN_DURATION_SEC);
        int ringSize = (int) (90 * density);
        LinearLayout.LayoutParams lpRing = new LinearLayout.LayoutParams(ringSize, ringSize);
        lpRing.bottomMargin = (int) (14 * density);
        card.addView(ringView, lpRing);

        // Subtitle: "%d giây nữa tự tìm trận..." / "auto find match in %ds..."
        TextView subText = new TextView(activity);
        subText.setTag("banner_sub");
        subText.setTextColor(0xFF9898A0); // Apple secondary label grey
        subText.setTextSize(12f);
        subText.setGravity(Gravity.CENTER);
        subText.setText(String.format(java.util.Locale.US, I18n.get(activity, "cd_sub"), seconds));
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lpSub.bottomMargin = (int) (18 * density);
        card.addView(subText, lpSub);

        // Action Buttons Bar [⏹ HỦY] & [⚡ TÌM NGAY]
        LinearLayout btnBar = new LinearLayout(activity);
        btnBar.setOrientation(LinearLayout.HORIZONTAL);
        btnBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpBtnBar = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        card.addView(btnBar, lpBtnBar);

        // Button [⏹ HỦY]
        TextView btnCancel = new TextView(activity);
        btnCancel.setText(I18n.get(activity, "cd_cancel"));
        btnCancel.setTextColor(0xFFE5E5EA);
        btnCancel.setTextSize(12.5f);
        btnCancel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btnCancel.setGravity(Gravity.CENTER);
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(0x33787880);
        cancelBg.setCornerRadius(12 * density);
        btnCancel.setBackground(cancelBg);
        int btnPadV = (int) (10 * density);
        btnCancel.setPadding(0, btnPadV, 0, btnPadV);
        LinearLayout.LayoutParams lpCancel = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpCancel.rightMargin = (int) (6 * density);
        btnBar.addView(btnCancel, lpCancel);

        btnCancel.setOnClickListener(v -> {
            userExplicitlyCancelled = true;
            cancelCountdown("User tapped cancel banner");
            vibrate(activity, 15);
            try {
                Toast.makeText(activity, I18n.get(activity, "toast_cd_cancel"), Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
        });

        // Tapping dimmed background outside card also cancels
        overlay.setOnClickListener(v -> btnCancel.performClick());

        // Button [⚡ TÌM NGAY]
        TextView btnNow = new TextView(activity);
        btnNow.setText(I18n.get(activity, "cd_now"));
        btnNow.setTextColor(0xFF051B0B); // Dark emerald text on neon
        btnNow.setTextSize(12.5f);
        btnNow.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btnNow.setGravity(Gravity.CENTER);
        GradientDrawable nowBg = new GradientDrawable();
        nowBg.setColor(0xFF30D158); // Neon emerald
        nowBg.setCornerRadius(12 * density);
        btnNow.setBackground(nowBg);
        btnNow.setPadding(0, btnPadV, 0, btnPadV);
        LinearLayout.LayoutParams lpNow = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpNow.leftMargin = (int) (6 * density);
        btnBar.addView(btnNow, lpNow);

        btnNow.setOnClickListener(v -> {
            if (countdownRunnable != null) {
                removeCallbacksOnMain(countdownRunnable);
                countdownRunnable = null;
            }
            removeCountdownBanner(activity);
            triggerAutoFind(activity);
        });

        // Add card to overlay centered
        int cardW = (int) (285 * density);
        FrameLayout.LayoutParams lpCard = new FrameLayout.LayoutParams(
                cardW,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lpCard.gravity = Gravity.CENTER;
        overlay.addView(card, lpCard);

        // Add overlay to decorView full screen
        FrameLayout.LayoutParams lpOverlay = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        root.addView(overlay, lpOverlay);
        overlay.bringToFront();

        // Smooth Entrance Animation (Desktop Extension cd-overlay-in & cd-card-in)
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(200).start();

        card.setScaleX(0.85f);
        card.setScaleY(0.85f);
        card.animate().scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(new OvershootInterpolator(1.05f)).start();
    }

    private static void updateCountdownBanner(Activity activity, int seconds) {
        if (activity == null || activity.getWindow() == null) return;
        View decor = activity.getWindow().getDecorView();
        if (decor == null) return;

        View banner = decor.findViewWithTag(BANNER_TAG);
        if (banner != null) {
            View sub = banner.findViewWithTag("banner_sub");
            if (sub instanceof TextView) {
                ((TextView) sub).setText(String.format(java.util.Locale.US, I18n.get(activity, "cd_sub"), seconds));
            }
            banner.bringToFront();
        }
    }

    private static void removeCountdownBanner(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;

        final ViewGroup root = (ViewGroup) decor;
        final View banner = root.findViewWithTag(BANNER_TAG);
        if (banner != null) {
            View ring = banner.findViewWithTag("banner_ring");
            if (ring instanceof CountdownRingView) {
                ((CountdownRingView) ring).stopSweep();
            }
            // Smooth exit animation
            banner.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                try {
                    root.removeView(banner);
                } catch (Throwable ignored) {}
            }).start();
        }
    }

    private static Method findMethodByName(Class<?> clazz, String... names) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                for (String name : names) {
                    if (m.getName().equals(name)) {
                        return m;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Field findFieldByName(Class<?> clazz, String... names) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                for (String name : names) {
                    if (f.getName().equals(name)) {
                        return f;
                    }
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
