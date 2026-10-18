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
    private static volatile Object lastHandledGameId = null;
    private static volatile String lastHandledGameUuid = null;
    private static volatile Object lastHandledGameEndData = null;
    private static volatile WeakReference<Object> lastNativeViewModelRef = null;

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

        getMainHandler().post(() -> {
            Activity activity = StockfishExtension.getCurrentActivity();
            if (activity != null) {
                onGameOverWithContext(activity, viewModel, gameEndData);
            } else {
                NnvcLogger.w(TAG, "Current Activity is null during onGameOverNative, scheduling 300ms retry...");
                getMainHandler().postDelayed(() -> {
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

    public static void resetHandledGameEndData() {
        lastHandledGameId = null;
        lastHandledGameUuid = null;
        lastHandledGameEndData = null;
    }

    /**
     * Called when a new game starts (FEN startpos or board reset).
     */
    public static void onNewGameStarted() {
        cancelCountdown("New game started");
        resetHandledGameEndData();
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
        if ("Activity paused".equals(reason)) {
            resetHandledGameEndData();
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

        // Safety watchdog: auto-reset SEEKING to IDLE after 15 seconds if no match or board change occurred
        getMainHandler().postDelayed(() -> {
            if (currentState == State.SEEKING) {
                NnvcLogger.d(TAG, "Safety watchdog: auto-resetting lingering SEEKING state to IDLE");
                currentState = State.IDLE;
            }
        }, 15000L);

        getMainHandler().post(() -> {
            // Tầng 0: Direct Cached Native ViewModel (highest priority, 100% reliable)
            boolean triggered = false;
            if (lastNativeViewModelRef != null) {
                Object vm = lastNativeViewModelRef.get();
                if (vm != null) {
                    triggered = triggerViaViewModelInstance(vm);
                    if (triggered) {
                        NnvcLogger.i(TAG, "Auto Find triggered via cached native ViewModel!");
                    }
                }
            }

            // Tầng 1: Native ViewModel Controller (includes UI Compose cleanup & modal dismiss)
            if (!triggered) {
                triggered = triggerViaViewModel(activity);
            }

            // Tầng 2: UI View Button Scanner
            if (!triggered) {
                triggered = triggerViaButtonScan(activity);
            }

            if (triggered) {
                NnvcLogger.i(TAG, "Auto Find match triggered successfully!");
            } else {
                NnvcLogger.i(TAG, "Auto Find match trigger: could not locate native controller or action button on first attempt. Scheduling retry in 400ms...");
                getMainHandler().postDelayed(() -> {
                    if (currentState == State.SEEKING) {
                        boolean retry = triggerViaButtonScan(activity);
                        if (!retry) {
                            retry = triggerViaViewModel(activity);
                        }
                        if (retry) {
                            NnvcLogger.i(TAG, "Auto Find match triggered on retry!");
                        } else {
                            NnvcLogger.i(TAG, "Auto Find match trigger: could not locate native controller or action button after retry.");
                            currentState = State.IDLE;
                        }
                    }
                }, 400L);
            }
        });
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
            banner.setElevation(50f);
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
        banner.bringToFront();
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
            banner.bringToFront();
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
