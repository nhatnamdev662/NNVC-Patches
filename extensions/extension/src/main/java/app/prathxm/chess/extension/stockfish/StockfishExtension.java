package app.prathxm.chess.extension.stockfish;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings({"unused", "JavaReflectionMemberAccess"})
public class StockfishExtension {

    private static final String TAG = "StockfishExt";

    private static final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "stockfish-analysis");
                t.setDaemon(true);
                return t;
            });

    private static volatile Future<?> currentJob = null;

    private static final AtomicReference<WeakReference<Object>> stateImplRef =
            new AtomicReference<>(new WeakReference<>(null));

    private static volatile boolean engineReady = false;
    private static volatile boolean lifecycleCallbacksRegistered = false;
    private static volatile boolean isInitializing = false;
    public static volatile boolean isReviewMode = false;
    public static volatile boolean isDeveloperMode = false;

    public static void registerChessBoardView(Object view) {
        // no-op, kept for compatibility
    }

    public static void invalidateAllBoards() {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = getCurrentActivity();
                    if (activity == null) {
                        Log.d(TAG, "invalidateAllBoards: no current activity");
                        return;
                    }
                    Window window = activity.getWindow();
                    if (window == null) return;
                    View decorView = window.getDecorView();
                    if (decorView == null) return;
                    findAndInvalidateChessBoardViews(decorView);
                } catch (Throwable t) {
                    Log.e(TAG, "invalidateAllBoards failed: " + t.getMessage());
                }
            }
        });
    }

    /** Resumed activity, tracked by the lifecycle callbacks (cheap, no hidden-API reflection). */
    private static volatile WeakReference<Activity> resumedActivity = new WeakReference<>(null);

    public static Activity getCurrentActivity() {
        Activity tracked = resumedActivity.get();
        if (tracked != null && !tracked.isFinishing()) return tracked;
        return findResumedActivityReflectively();
    }

    /** Fallback before the callbacks run: ActivityThread.mActivities (hidden API, slow). */
    private static Activity findResumedActivityReflectively() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Object activitiesMap = activitiesField.get(activityThread);
            if (activitiesMap instanceof java.util.Map) {
                for (Object activityRecord : ((java.util.Map<?, ?>) activitiesMap).values()) {
                    Class<?> recordClass = activityRecord.getClass();
                    Field pausedField = recordClass.getDeclaredField("paused");
                    pausedField.setAccessible(true);
                    if (!pausedField.getBoolean(activityRecord)) {
                        Field activityField = recordClass.getDeclaredField("activity");
                        activityField.setAccessible(true);
                        return (Activity) activityField.get(activityRecord);
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "getCurrentActivity failed: " + t.getMessage());
        }
        return null;
    }

    private static void findAndInvalidateChessBoardViews(View view) {
        if (view == null) return;
        if (view.getClass().getName().equals("com.chess.chessboard.view.ChessBoardView")) {
            view.invalidate();
            Log.d(TAG, "invalidated ChessBoardView: " + view);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                findAndInvalidateChessBoardViews(group.getChildAt(i));
            }
        }
    }

    public static Object[] ensureHintArrowsEnabled(Object[] optionalPainters) {
        if (optionalPainters == null) {
            return null;
        }
        try {
            boolean hasKeyMoveHints = false;
            for (Object type : optionalPainters) {
                if (type != null && "KEY_MOVE_HINTS".equals(type.toString())) {
                    hasKeyMoveHints = true;
                    break;
                }
            }
            if (!hasKeyMoveHints) {
                Log.d(TAG, "injecting KEY_MOVE_HINTS into optional painters array");
                Class<?> optionalPainterClass = Class.forName("com.chess.internal.utils.chessboard.ChessBoardViewOptionalPainterType");
                Object gField = null;
                for (Field field : optionalPainterClass.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) &&
                        field.getType().equals(optionalPainterClass)) {
                        try {
                            field.setAccessible(true);
                            Object val = field.get(null);
                            if (val != null && "KEY_MOVE_HINTS".equals(val.toString())) {
                                gField = val;
                                break;
                            }
                        } catch (Throwable ignored) {}
                    }
                }

                if (gField == null) {
                    Log.e(TAG, "Could not find KEY_MOVE_HINTS field in ChessBoardViewOptionalPainterType");
                    return optionalPainters;
                }

                Object[] newPainters = (Object[]) java.lang.reflect.Array.newInstance(optionalPainterClass, optionalPainters.length + 1);
                System.arraycopy(optionalPainters, 0, newPainters, 0, optionalPainters.length);
                newPainters[optionalPainters.length] = gField;
                return newPainters;
            }
        } catch (Throwable t) {
            Log.e(TAG, "ensureHintArrowsEnabled failed: " + t.getMessage(), t);
        }
        return optionalPainters;
    }

    static {
        silenceIllegalMoveSound();
    }

    private static void muteSoundEventClass(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            for (Field f : clazz.getDeclaredFields()) {
                // NEVER null out the instance field 'a', otherwise Kotlin Intrinsics checkNotNullParameter(<this>) throws NPE!
                if (f.getType().equals(clazz) || f.getName().equals("a")) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    try {
                        Field modifiersField = Field.class.getDeclaredField("accessFlags");
                        modifiersField.setAccessible(true);
                        modifiersField.setInt(f, f.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
                    } catch (Throwable ignoredModifiers) {}

                    if (f.getType().getName().contains("Sound") || f.getName().equals("b") || f.getName().equals("sound")) {
                        f.set(null, null);
                    } else if (f.getType().getName().contains("s0") || f.getName().equals("c") || f.getName().equals("haptics")) {
                        f.set(null, null);
                    }
                } catch (Throwable ignoredField) {}
            }
        } catch (Throwable ignored) {}
    }

    public static void silenceIllegalMoveSound() {
        try {
            // Giữ lại âm thanh IllegalMove gốc của Chess.com (k.java).
            // Chỉ chặn StillInCheck (s.java) - nguồn phát âm thanh lỗi khi thao tác quân cờ / khai cuộc:
            muteSoundEventClass("com.chess.soundandhaptics.api.s"); // StillInCheck
        } catch (Throwable ignored) {}
    }

    public static void ensureEngineReady() {
        silenceIllegalMoveSound();
        if (engineReady || isInitializing) return;
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentApplicationMethod = activityThreadClass.getMethod("currentApplication");
            final Context ctx = (Context) currentApplicationMethod.invoke(null);
            if (ctx == null) {
                return;
            }

            NnvcLogger.init(ctx);
            NnvcLogger.i(TAG, "ensureEngineReady triggered");

            Application app = (Application) ctx.getApplicationContext();
            if (!lifecycleCallbacksRegistered && app != null) {
                registerLifecycleCallbacks(app);
                lifecycleCallbacksRegistered = true;
            }

            isInitializing = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        try {
                            TorchEngine.getInstance(ctx);
                        } catch (Throwable te) {
                            NnvcLogger.e(TAG, "Failed to start TorchEngine early: " + te.getMessage(), te);
                        }
                        boolean ok = StockfishBridge.init(ctx);
                        engineReady = ok;
                        NnvcLogger.i(TAG, ok
                            ? "Stockfish engine initialised successfully."
                            : "Stockfish engine failed to initialise.");
                        if (ok) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    triggerAnalysisForCurrentState();
                                }
                            });
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Exception in async Stockfish init: " + t.getMessage());
                    } finally {
                        isInitializing = false;
                    }
                }
            }).start();

        } catch (Throwable t) {
            Log.e(TAG, "Exception initialising Stockfish: " + t.getMessage());
            isInitializing = false;
        }
    }

    public static void onBoardChanged(Object stateImplObject, Object positionObject) {
        if (stateImplObject == null || positionObject == null) return;
        
        silenceIllegalMoveSound();
        ArrowInjector.clearEngineArrows(stateImplObject);
        stateImplRef.set(new WeakReference<>(stateImplObject));

        final Activity activity = getCurrentActivity();
        if (activity != null) {
            unlockScreenshots(activity);
            if (isLiveMatch(activity)) {
                isReviewMode = false;
            }
        }

        if (isGameOver(activity)) {
            OverlayManager.hideArrowOverlay();
            ArrowInjector.clearEngineArrows(stateImplObject);
            StockfishBridge.stopSearch();
            Context c = getContext();
            if (c != null) {
                TorchEngine.getInstance(c).cancelPendingRequests();
            }
            return;
        }

        GestureInterceptor.ensureGestureInterceptorRegistered();
        ensureEngineReady();
        if (!engineReady) return;

        Context ctx = getContext();
        if (ctx != null && !StockfishSettings.isEngineEnabled(ctx)) {
            Log.d(TAG, "Engine is disabled in settings.");
            OverlayManager.hideArrowOverlay();
            OverlayManager.hideEvalBar();
            OverlayManager.hideWdlBar();
            OverlayManager.hideMateAnnouncement();
            OverlayManager.hideEngineInfo();
            return;
        }

        OverlayManager.hideArrowOverlay();
        OverlayManager.clearClassificationBadge();
        lastArrowSignature = null;

        String fen = extractFen(positionObject);
        if (fen == null) {
            Log.w(TAG, "onBoardChanged: could not extract FEN");
            return;
        }

        Log.d(TAG, "Position changed → FEN: " + fen);
        boolean isStartPos = fen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
        if (isStartPos) {
            OverlayManager.clearClassificationBadge();
            OverlayManager.hideArrowOverlay();
            OverlayManager.resetAccuracyEloWidget();
            OverlayManager.hideAccuracyEloPills();
            ArrowInjector.clearEngineArrows(stateImplObject);
            if (ctx != null) {
                TorchEngine.getInstance(ctx).cancelPendingRequests();
                WasmEngineManager.getInstance(ctx).resetClassifyGame();
            }
            resetUserWhiteCache();
            MoveClassifier.clearHistory();
            OverlayManager.resetEvalBar();
        }
        MoveClassifier.updateHistory(fen, positionObject);
        if (activity != null) {
            EloScanner.scanAndApply(activity, stateImplObject);
        }

        // 1. Independent Eval Bar: Dedicated Stockfish 18 classify instance (Depth 10, MultiPV 3, Hash 64)
        if (ctx != null && StockfishSettings.isEvalBarEnabled(ctx)) {
            WasmEngineManager.getInstance(ctx).evaluateClassifyPosition(fen, MoveClassifier.getCurrentPositionMoves());
        }

        // 2. Independent Move Classification: Torch CEE with 100ms debounce
        if (ctx != null) {
            MoveClassifier.classifyMoveDebounced(ctx, fen);
        }

        // 3. Play Engine Analysis: Best moves and threat arrows
        scheduleAnalysis(fen);
    }

    // Stubs for real Game Review compatibility
    public static Object getLocalAnalysisFlowForConfig(Class<?> flowClass, Object pgn, Object depth) {
        Log.d(TAG, "getLocalAnalysisFlowForConfig called (stub for real game review)");
        return null;
    }

    public static boolean shouldUseDummyMove(Object o1, Object o2) {
        return false;
    }

    public static Object buildDummyMoveResult(Class<?> clazz, Object o) {
        return null;
    }

    public static Object getFullGameAnalysisPermissions() {
        return null;
    }

    public static void onArrowsChanged(Object stateImplObject, List<?> arrows) {
        if (stateImplObject == null) return;
        stateImplRef.set(new WeakReference<>(stateImplObject));

        Activity activity = getCurrentActivity();
        if (isGameOver(activity)) {
            OverlayManager.hideArrowOverlay();
            ArrowInjector.clearEngineArrows(stateImplObject);
            return;
        }

        GestureInterceptor.ensureGestureInterceptorRegistered();
        ensureEngineReady();

        // Auto-trigger analysis for move 0 / initial position if no job has been scheduled yet
        if (engineReady && currentJob == null) {
            triggerAnalysisForCurrentState();
        }
    }

    /** Signature (position + moves) of the engine arrows currently on the board. */
    private static volatile String lastArrowSignature = null;

    public static void resetArrowSignature() {
        lastArrowSignature = null;
    }

    /** Position (FEN key) of the most recently scheduled live analysis. */
    private static volatile String lastScheduledKey = null;

    private static void scheduleAnalysis(String fen) {
        scheduleAnalysis(fen, false);
    }

    /** @param force restart even if this position is already being analysed (settings changed). */
    private static void scheduleAnalysis(String fen, boolean force) {
        // The board callback fires several times for the same position (move animation,
        // arrow updates, re-renders). Restarting an identical search each time just burns CPU.
        String posKey = StockfishBridge.positionKey(fen);
        Future<?> running = currentJob;
        if (!force && posKey != null && posKey.equals(lastScheduledKey) && running != null && !running.isDone()) {
            return;
        }
        lastScheduledKey = posKey;
        Future<?> prev = currentJob;
        if (prev != null && !prev.isDone()) {
            prev.cancel(true);
            StockfishBridge.stopSearch();
        }

        final String jobKey = posKey;
        currentJob = executor.submit(() -> {
            try {
                Context context = getContext();
                if (context == null) return;

                int depth = StockfishSettings.isAutoDepthEnabled(context)
                        ? StockfishSettings.autoDepthForElo(StockfishSettings.getElo(context))
                        : StockfishSettings.getDepth(context);
                int multiPV = StockfishSettings.getMultiPV(context);
                if (StockfishSettings.isMoveClassificationEnabled(context)) {
                    multiPV = Math.max(multiPV, 2);
                }

                Log.d(TAG, "Analysing FEN with " + StockfishSettings.getEngineChoice(context) + " at depth " + depth + " with MultiPV=" + multiPV + "…");
                // Stream intermediate depths to the classifier, but NEVER paint intermediate arrows to UI.
                // In NNVC Extension standard (nnvc_main_bundle.js lines 6138-6156 & 16474), arrows are painted
                // atomically ONLY once after engine finishes full search (bestmove), preventing any jumping/jitter.
                StockfishProcess.AnalysisResult result = StockfishBridge.analyze(fen, depth, multiPV,
                        partial -> {
                            MoveClassifier.recordResult(fen, partial);
                        });

                MoveClassifier.recordResult(fen, result);

                // The user moved on while we were searching: never paint an outdated result
                if (isStale(jobKey)) {
                    return;
                }

                if (result.moves.isEmpty()) {
                    if (result.terminal) {
                        OverlayManager.hideArrowOverlay(true);
                        ArrowInjector.clearEngineArrows(getStateImpl());
                        TorchEngine.getInstance(context).cancelPendingRequests();
                        TorchEngine.log("[GAME OVER] Ván đấu kết thúc (terminal=true). Đã ẩn mũi tên và hủy hàng chờ Torch.");
                    }
                    Log.d(TAG, "Engine returned no best moves.");
                    return;
                }

                Log.i(TAG, "Best moves: " + result.moves + ", Score: " + result.score + ", depth " + result.depth);
                displayLiveResult(context, fen, result, true);

            } catch (Throwable t) {
                if (!Thread.currentThread().isInterrupted()) {
                    Log.e(TAG, "Analysis error: " + t.getMessage());
                }
            }
        });
    }

    /** True if the running job was cancelled or a newer position has been scheduled. */
    private static boolean isStale(String jobKey) {
        if (Thread.currentThread().isInterrupted()) return true;
        String latest = lastScheduledKey;
        return jobKey != null && !jobKey.equals(latest);
    }

    /**
     * Paints an analysis result (arrows, eval bar, WDL bar, mate banner). Intermediate results
     * ({@code isFinal == false}) only update arrows and bars; the mate banner waits for the
     * final search so it does not flicker.
     */
    private static void displayLiveResult(Context context, String fen,
                                          StockfishProcess.AnalysisResult result, boolean isFinal) {
        if (result == null || result.moves.isEmpty()) return;
        if (!StockfishSettings.isEngineEnabled(context)) return;

        Activity activity = getCurrentActivity();
        if (activity != null && isGameOver(activity)) {
            OverlayManager.hideArrowOverlay(true);
            ArrowInjector.clearEngineArrows(getStateImpl());
            StockfishBridge.stopSearch();
            TorchEngine.getInstance(context).cancelPendingRequests();
            return;
        }

        boolean isLive = false;
        if (activity != null && isLiveMatch(activity)) {
            isLive = true;
        }
        boolean disableOverlays = isLive && !isReviewMode;

        boolean isWhiteTurn = isWhiteTurnFromFen(fen);
        Boolean userWhite = isUserWhite(getStateImpl());
        boolean isMyTurn = (userWhite == null) || (isWhiteTurn == userWhite.booleanValue());

        boolean showArrows = !disableOverlays && StockfishSettings.isArrowsVisible(context);
        if (showArrows && StockfishSettings.isMySideOnly(context) && !isMyTurn) {
            showArrows = false;
        }

        boolean threatEnabled = !disableOverlays && StockfishSettings.isThreatArrowsEnabled(context);
        String threatToInject = null;
        if (threatEnabled) {
            if (isMyTurn) {
                // On user's turn: Threat arrow is the opponent's counter-threat (ponder)
                if (result.ponder != null && !result.ponder.isEmpty()) {
                    threatToInject = result.ponder;
                }
            } else {
                // On opponent's turn: Threat arrow is what opponent threatens to play right now
                if (result.moves != null && !result.moves.isEmpty()) {
                    threatToInject = result.moves.get(0);
                }
            }
        }
        boolean showThreat = (threatToInject != null);

        if (showArrows || showThreat) {
            java.util.List<String> movesToInject = (showArrows && result.moves != null) ? result.moves : java.util.Collections.emptyList();

            if (showThreat && threatToInject != null) {
                ArrowInjector.injectThreatArrow(context, getStateImpl(), threatToInject);
            }

            String sig = fen + '|' + (showArrows ? (result.moves != null ? result.moves.toString() : "") : "") + '|'
                    + (showThreat && threatToInject != null ? threatToInject : "") + '|'
                    + StockfishSettings.getArrowTierColor(context, 1) + '|'
                    + StockfishSettings.getArrowTierColor(context, 2) + '|'
                    + StockfishSettings.getArrowTierColor(context, 3);
            if (!sig.equals(lastArrowSignature)) {
                lastArrowSignature = sig;
                boolean whiteTurn = isWhiteTurnFromFen(fen);
                OverlayManager.updateArrowOverlay(movesToInject, threatToInject, result.lineScores, result.lineMates, result.lineMateIn, whiteTurn, getStateImpl());
            }
        } else if (isFinal) {
            lastArrowSignature = null;
            OverlayManager.hideArrowOverlay();
            ArrowInjector.clearEngineArrows(getStateImpl());
        }

        if (disableOverlays || !StockfishSettings.isEvalBarEnabled(context)) {
            OverlayManager.hideEvalBar();
        }

        if (!disableOverlays && StockfishSettings.isWdlEnabled(context)) {
            OverlayManager.updateWdlBar(result.wdlWin, result.wdlDraw, result.wdlLoss);
        } else {
            OverlayManager.hideWdlBar();
        }

        if (!disableOverlays && StockfishSettings.isEngineInfoEnabled(context)) {
            OverlayManager.updateEngineInfo(result.depth, result.score, result.hasMate, result.mateIn);
        } else {
            OverlayManager.hideEngineInfo();
        }

        if (!disableOverlays && StockfishSettings.isAccuracyEloEnabled(context)) {
            Boolean uWhite = isUserWhite(getStateImpl());
            boolean finalUserIsWhite = (uWhite != null) ? uWhite.booleanValue() : true;
            OverlayManager.updateAccuracyEloWidget(-1f, -1f, -1, -1, finalUserIsWhite);
        } else {
            OverlayManager.hideAccuracyEloWidget();
        }

        if (!isFinal) return;
        OverlayManager.hideMateAnnouncement();
    }

    private static boolean isWhiteTurnFromFen(String fen) {
        if (fen == null) return true;
        String[] parts = fen.split("\\s+");
        return parts.length > 1 && parts[1].equals("w");
    }

    private static void registerLifecycleCallbacks(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState) {
                silenceIllegalMoveSound();
                String name = activity.getClass().getName();
                if (name.startsWith("com.chess.features.puzzles.")) {
                    activity.finish();
                    activity.overridePendingTransition(0, 0);
                    
                    try {
                        android.content.Intent intent = new android.content.Intent(activity, Class.forName("app.prathxm.chess.extension.lichesspuzzle.LichessPuzzleJourneyActivity"));
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(intent);
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to redirect puzzle activity: " + t.getMessage(), t);
                    }
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {
                silenceIllegalMoveSound();
                unlockScreenshots(activity);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                resumedActivity = new WeakReference<>(activity);
                silenceIllegalMoveSound();
                unlockScreenshots(activity);
                GestureInterceptor.registerGestureInterceptor(activity);
                lastArrowSignature = null;
                lastScheduledKey = null;
                // Multiple retry checks to catch Move 0 as board view loads into view hierarchy
                for (long delay : new long[]{100, 300, 600, 1200}) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (activity.isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
                                if (!isGameOver(activity)) {
                                    triggerAnalysisForCurrentState();
                                    invalidateAllBoards();
                                } else {
                                    OverlayManager.hideArrowOverlay();
                                }
                            } catch (Throwable t) {
                                Log.e(TAG, "Failed to refresh onActivityResumed: " + t.getMessage());
                            }
                        }
                    }, delay);
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (resumedActivity.get() == activity) resumedActivity = new WeakReference<>(null);
            }

            @Override
            public void onActivityStopped(Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(Activity activity, android.os.Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {}
        });
    }

    public static void unlockScreenshots(final Activity activity) {
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Window window = activity.getWindow();
                    if (window != null) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to clear FLAG_SECURE: " + t.getMessage());
                }
            }
        });
    }

    public static void toggleEverything(Activity activity) {
        boolean enabled = !StockfishSettings.isEngineEnabled(activity);
        StockfishSettings.setEngineEnabled(activity, enabled);
        
        if (!enabled) {
            Future<?> prev = currentJob;
            if (prev != null && !prev.isDone()) {
                prev.cancel(true);
            }
            StockfishBridge.stopSearch();
            
            OverlayManager.hideArrowOverlay();
            lastArrowSignature = null;
            OverlayManager.hideEvalBar();
            OverlayManager.hideWdlBar();
            OverlayManager.hideMateAnnouncement();
            OverlayManager.hideEngineInfo();
            OverlayManager.hideAccuracyEloPills();
        } else {
            triggerAnalysisForCurrentState();
        }
    }

    public static boolean isGameOver() {
        Activity act = getCurrentActivity();
        return isGameOver(act);
    }

    public static boolean isGameOver(Activity activity) {
        if (activity == null) return false;
        try {
            // 1. Inspect activity fields for gameOverOverlayView (RealGameActivity.m0 or gameOverOverlayView)
            Class<?> actClass = activity.getClass();
            while (actClass != null && !actClass.getName().equals("android.app.Activity")) {
                for (Field f : actClass.getDeclaredFields()) {
                    String fn = f.getName();
                    if (fn.equals("gameOverOverlayView") || fn.equals("m0") || fn.toLowerCase().contains("gameover")) {
                        f.setAccessible(true);
                        Object val = f.get(activity);
                        if (val instanceof View) {
                            View gv = (View) val;
                            if (gv.getVisibility() == View.VISIBLE && gv.getParent() != null) {
                                return true;
                            }
                        } else if (val instanceof Boolean && ((Boolean) val).booleanValue()) {
                            return true;
                        }
                    }
                }
                actClass = actClass.getSuperclass();
            }

            // 2. Check FragmentManager for DialogFragments (e.g. BaseGameOverDialog, BotGameOverDialog, LiveGameOverDialog, etc.)
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
                        String[] knownTags = new String[]{
                                "BaseGameOverDialog",
                                "LiveGameOverDialog",
                                "LiveGameOverDialogV2",
                                "LiveArenaGameOverDialog",
                                "PracticePlayGameOverDialog",
                                "BotGameOverDialog",
                                "PassAndPlayGameOverDialog",
                                "VisionChallengeCompleteDialog"
                        };
                        try {
                            Method findByTag = fm.getClass().getMethod("findFragmentByTag", String.class);
                            for (String tag : knownTags) {
                                Object frag = findByTag.invoke(fm, tag);
                                if (frag != null) {
                                    boolean isVis = true;
                                    try {
                                        Method isVisM = frag.getClass().getMethod("isVisible");
                                        isVis = (Boolean) isVisM.invoke(frag);
                                    } catch (Throwable ignored2) {}
                                    if (isVis) return true;
                                }
                            }
                        } catch (Throwable ignored) {}

                        try {
                            Method getFrags = fm.getClass().getMethod("getFragments");
                            Object fragsList = getFrags.invoke(fm);
                            if (fragsList instanceof List) {
                                for (Object frag : (List<?>) fragsList) {
                                    if (frag == null) continue;
                                    String fName = frag.getClass().getName();
                                    if (fName.contains("GameOver") || fName.contains("GameEnd") || fName.contains("GameFinished")) {
                                        boolean isVis = true;
                                        try {
                                            Method isVisM = frag.getClass().getMethod("isVisible");
                                            isVis = (Boolean) isVisM.invoke(frag);
                                        } catch (Throwable ignored2) {}
                                        if (isVis) return true;
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}

            // 3. Check decorView hierarchy for GameOver views
            if (activity.getWindow() != null && activity.getWindow().getDecorView() != null) {
                View decorView = activity.getWindow().getDecorView();
                if (hasGameOverView(decorView)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean hasGameOverView(View view) {
        if (view == null) return false;
        if (view.getVisibility() == View.VISIBLE) {
            String className = view.getClass().getName();
            if (className.contains("GameOver") || className.contains("GameEnd") || className.contains("GameFinished")) {
                return true;
            }
            try {
                int id = view.getId();
                if (id != View.NO_ID && view.getResources() != null) {
                    String entryName = view.getResources().getResourceEntryName(id);
                    if (entryName != null) {
                        String lower = entryName.toLowerCase();
                        if (lower.contains("game_over") || lower.contains("gameover") || lower.contains("game_end")) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {}
            Object tag = view.getTag();
            if (tag instanceof String) {
                String stag = ((String) tag).toLowerCase();
                if (stag.contains("game_over") || stag.contains("gameover")) {
                    return true;
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                if (hasGameOverView(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Object extractPositionFromBoardView(View boardView) {
        if (boardView == null) return null;
        try {
            Method getPos = boardView.getClass().getMethod("getPositionWithPremoves");
            Object pos = getPos.invoke(boardView);
            if (pos != null && extractFen(pos) != null) return pos;
        } catch (Throwable ignored) {}
        try {
            Method getDispPos = boardView.getClass().getMethod("getPosition");
            Object dispPos = getDispPos.invoke(boardView);
            if (dispPos != null) {
                if (extractFen(dispPos) != null) return dispPos;
                try {
                    Method getA = dispPos.getClass().getMethod("a");
                    Object pos = getA.invoke(dispPos);
                    if (pos != null && extractFen(pos) != null) return pos;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        try {
            Method getVm = boardView.getClass().getMethod("getViewModel");
            Object vm = getVm.invoke(boardView);
            if (vm != null) {
                Method getState = vm.getClass().getMethod("getState");
                Object state = getState.invoke(vm);
                if (state != null) {
                    stateImplRef.set(new WeakReference<>(state));
                    Method getPosition = state.getClass().getMethod("getPosition");
                    Object pos = getPosition.invoke(state);
                    if (pos != null && extractFen(pos) != null) return pos;
                }
            }
        } catch (Throwable ignored) {}
        for (Field f : boardView.getClass().getDeclaredFields()) {
            if (f.getName().equalsIgnoreCase("position") || f.getName().equals("p") || f.getName().equals("mPosition")) {
                try {
                    f.setAccessible(true);
                    Object pos = f.get(boardView);
                    if (pos != null && extractFen(pos) != null) return pos;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    public static void onInitialPositionLoaded(Object obj) {
        if (obj == null) return;
        silenceIllegalMoveSound();
        try {
            if (obj instanceof View) {
                View boardView = (View) obj;
                Object pos = extractPositionFromBoardView(boardView);
                if (pos != null) {
                    onBoardChanged(getStateImpl(), pos);
                    return;
                }
            } else {
                stateImplRef.set(new WeakReference<>(obj));
                try {
                    Method getPos = obj.getClass().getMethod("getPosition");
                    Object pos = getPos.invoke(obj);
                    if (pos != null) {
                        onBoardChanged(obj, pos);
                        return;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        triggerAnalysisForCurrentState();
    }

    public static void triggerAnalysisForCurrentState() {
        Activity act = getCurrentActivity();
        if (isGameOver(act)) {
            OverlayManager.hideArrowOverlay(true);
            StockfishBridge.stopSearch();
            return;
        }
        // Settings or app state changed: always repaint and restart analysis
        lastArrowSignature = null;
        lastScheduledKey = null;
        OverlayManager.hideArrowOverlay();
        Object state = getStateImpl();
        Object positionObject = null;
        if (state != null) {
            try {
                Method getPosition = state.getClass().getMethod("getPosition");
                positionObject = getPosition.invoke(state);
            } catch (Throwable ignored) {}
        }
        if (positionObject == null) {
            try {
                if (act != null && act.getWindow() != null && act.getWindow().getDecorView() != null) {
                    View boardView = OverlayManager.findChessBoardView(act.getWindow().getDecorView());
                    if (boardView != null) {
                        positionObject = extractPositionFromBoardView(boardView);
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (positionObject != null) {
            try {
                String fen = extractFen(positionObject);
                if (fen != null) {
                    Context ctx = getContext();
                    boolean isStartPos = fen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
                    List<String> moves = MoveClassifier.extractMovesFromPosition(positionObject);
                    if (isStartPos || (moves != null && moves.isEmpty())) {
                        OverlayManager.clearClassificationBadge();
                        OverlayManager.resetAccuracyEloWidget();
                        OverlayManager.hideAccuracyEloPills();
                        if (state != null) ArrowInjector.clearEngineArrows(state);
                        if (ctx != null) {
                            TorchEngine.getInstance(ctx).cancelPendingRequests();
                            WasmEngineManager.getInstance(ctx).resetClassifyGame();
                        }
                        resetUserWhiteCache();
                        MoveClassifier.clearHistory();
                        OverlayManager.resetEvalBar();
                    }
                    MoveClassifier.updateHistory(fen, positionObject);
                    if (ctx != null && StockfishSettings.isEvalBarEnabled(ctx)) {
                        WasmEngineManager.getInstance(ctx).evaluateClassifyPosition(fen, MoveClassifier.getCurrentPositionMoves());
                    }
                    scheduleAnalysis(fen, true);
                }
            } catch (Throwable t) {
                Log.e(TAG, "triggerAnalysisForCurrentState failed: " + t.getMessage());
            }
        }
    }

    /**
     * Re-triggers only the play analysis (best moves / arrows) without touching the Classify Eval Bar.
     * Used when toggling play engines or Komodo play styles so that Eval Bar remains rock-solid.
     */
    public static void triggerPlayAnalysisOnly() {
        Activity act = getCurrentActivity();
        if (isGameOver(act)) {
            OverlayManager.hideArrowOverlay(true);
            StockfishBridge.stopSearch();
            return;
        }
        lastArrowSignature = null;
        lastScheduledKey = null;
        OverlayManager.hideArrowOverlay();
        Object state = getStateImpl();
        Object positionObject = null;
        if (state != null) {
            try {
                Method getPosition = state.getClass().getMethod("getPosition");
                positionObject = getPosition.invoke(state);
            } catch (Throwable ignored) {}
        }
        if (positionObject == null) {
            try {
                if (act != null && act.getWindow() != null && act.getWindow().getDecorView() != null) {
                    View boardView = OverlayManager.findChessBoardView(act.getWindow().getDecorView());
                    if (boardView != null) {
                        positionObject = extractPositionFromBoardView(boardView);
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (positionObject != null) {
            try {
                String fen = extractFen(positionObject);
                if (fen != null) {
                    scheduleAnalysis(fen, true);
                }
            } catch (Throwable t) {
                Log.e(TAG, "triggerPlayAnalysisOnly failed: " + t.getMessage());
            }
        }
    }

    /** Application context, cached after the first successful lookup (it never changes). */
    private static volatile Context appContext;

    /**
     * The Application. Called many times per move and from hot getters (ads, premium), so the
     * reflective ActivityThread lookup runs only until it first succeeds.
     */
    public static Context getContext() {
        Context ctx = appContext;
        if (ctx == null) {
            try {
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                Method currentApplicationMethod = activityThreadClass.getMethod("currentApplication");
                ctx = (Context) currentApplicationMethod.invoke(null);
                if (ctx != null) appContext = ctx;
            } catch (Throwable t) {
                Log.e(TAG, "getContext failed: " + t.getMessage());
                return null;
            }
        }
        if (ctx != null && !engineReady) {
            ensureEngineReady();
        }
        return ctx;
    }

    public static Object getStateImpl() {
        WeakReference<Object> ref = stateImplRef.get();
        Object current = (ref != null) ? ref.get() : null;
        if (current != null) {
            return current;
        }
        try {
            Activity act = getCurrentActivity();
            if (act != null && act.getWindow() != null && act.getWindow().getDecorView() != null) {
                View decorView = act.getWindow().getDecorView();
                View boardView = OverlayManager.findChessBoardView(decorView);
                if (boardView != null) {
                    try {
                        Method getVm = boardView.getClass().getMethod("getViewModel");
                        Object vm = getVm.invoke(boardView);
                        if (vm != null) {
                            Method getState = vm.getClass().getMethod("getState");
                            Object state = getState.invoke(vm);
                            if (state != null) {
                                stateImplRef.set(new WeakReference<>(state));
                                return state;
                            }
                        }
                    } catch (Throwable t1) {
                        for (Field f : boardView.getClass().getDeclaredFields()) {
                            if (f.getName().contains("viewModel") || f.getType().getName().contains("ViewModel") || f.getName().equals("x")) {
                                f.setAccessible(true);
                                Object vm = f.get(boardView);
                                if (vm != null) {
                                    for (Method m : vm.getClass().getMethods()) {
                                        if (m.getName().equals("getState") && m.getParameterCount() == 0) {
                                            Object state = m.invoke(vm);
                                            if (state != null) {
                                                stateImplRef.set(new WeakReference<>(state));
                                                return state;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static volatile Boolean cachedUserIsWhite = null;

    public static void resetUserWhiteCache() {
        cachedUserIsWhite = null;
    }

    public static Boolean isUserWhite(Object stateImplObject) {
        // Priority 1: Check live board orientation via stateImplObject getFlipBoard()
        if (stateImplObject != null) {
            try {
                for (Method m : stateImplObject.getClass().getMethods()) {
                    String n = m.getName();
                    if ((n.equals("getFlipBoard") || n.equals("isFlipped") || n.equals("getFlipped"))
                            && m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                        boolean flipped = (boolean) m.invoke(stateImplObject);
                        boolean userWhite = !flipped;
                        cachedUserIsWhite = userWhite;
                        return userWhite;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Priority 2: Check live board orientation via OverlayManager (which checks ChessBoardView)
        try {
            boolean flipped = OverlayManager.isBoardFlipped(stateImplObject);
            boolean userWhite = !flipped;
            cachedUserIsWhite = userWhite;
            return userWhite;
        } catch (Throwable ignored) {}

        // Priority 3: Fall back to previously cached game orientation
        if (cachedUserIsWhite != null) {
            return cachedUserIsWhite;
        }

        // Move 0 default: Player starting a match is White
        return Boolean.TRUE;
    }

    /**
     * Colour the user plays from the board's {@code Side} value (WHITE, BLACK, BOTH, NONE).
     *
     * <p>The accessor for the side's colour is obfuscated ({@code c()} in 4.10.17), but the enum
     * constant names are not, so they are checked first; the Color-returning accessor is found
     * by return type as a fallback.
     *
     * @return TRUE for white, FALSE for black, null if the user plays both sides or neither
     */
    static Boolean sideToWhite(Object side) {
        if (side == null) return null;
        String name = side instanceof Enum ? ((Enum<?>) side).name() : String.valueOf(side);
        if ("WHITE".equalsIgnoreCase(name)) return Boolean.TRUE;
        if ("BLACK".equalsIgnoreCase(name)) return Boolean.FALSE;
        if ("BOTH".equalsIgnoreCase(name) || "NONE".equalsIgnoreCase(name)) return null;
        try {
            Class<?> colorClass = Class.forName("com.chess.entities.Color");
            for (Method m : side.getClass().getMethods()) {
                if (m.getParameterTypes().length == 0 && m.getReturnType() == colorClass) {
                    m.setAccessible(true);
                    Object color = m.invoke(side);
                    if (color == null) return null;
                    String c = color instanceof Enum ? ((Enum<?>) color).name() : color.toString();
                    if ("WHITE".equalsIgnoreCase(c)) return Boolean.TRUE;
                    if ("BLACK".equalsIgnoreCase(c)) return Boolean.FALSE;
                    return null;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "sideToWhite failed: " + t.getMessage());
        }
        return null;
    }

    /** Cached {@code variants.d.o()}: the position's full FEN (lazy property "fen" in 4.10.17). */
    private static volatile Method fullFenMethod;
    private static volatile boolean fullFenResolved;

    /**
     * FEN of an app position. Uses the app's own full FEN (with the real half-move clock and
     * move number, so Stockfish sees the 50-move rule), and falls back to assembling it from
     * FenUtilsKt with "0 1" counters.
     */
    public static String extractFen(Object position) {
        if (position == null) return null;
        String full = fullFen(position);
        if (full != null) return full;
        return assembleFen(position);
    }

    static String fullFen(Object position) {
        try {
            if (!fullFenResolved) {
                Method found = null;
                try {
                    Method o = Class.forName("com.chess.chessboard.variants.d").getMethod("o");
                    if (o.getReturnType() == String.class) found = o;
                } catch (Throwable ignored) {}
                fullFenMethod = found;
                fullFenResolved = true;
            }
            Method m = fullFenMethod;
            if (m == null || !m.getDeclaringClass().isInstance(position)) return null;
            Object r = m.invoke(position);
            return r instanceof String ? sanitizeFen((String) r) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Returns the FEN if it has 6 well-formed fields, otherwise null. */
    static String sanitizeFen(String fen) {
        if (fen == null) return null;
        String[] p = fen.trim().split("\\s+");
        if (p.length != 6 || p[0].split("/", -1).length != 8) return null;
        if (!(p[1].equals("w") || p[1].equals("b"))) return null;
        try {
            if (Integer.parseInt(p[4]) < 0 || Integer.parseInt(p[5]) < 1) return null;
        } catch (NumberFormatException e) {
            return null;
        }
        return p[0] + ' ' + p[1] + ' ' + p[2] + ' ' + p[3] + ' ' + p[4] + ' ' + p[5];
    }

    private static String assembleFen(Object position) {
        try {
            Class<?> posExtKt = Class.forName(
                "com.chess.chessboard.variants.standard.bitboard.FenUtilsKt");
            
            String board = null;
            String castling = null;
            String enPassant = null;
            
            for (Method m : posExtKt.getMethods()) {
                if (m.getParameterCount() == 1) {
                    if (m.getName().equals("b")) {
                        Object res = m.invoke(null, position);
                        if (res != null) board = res.toString();
                    } else if (m.getName().equals("c")) {
                        Object res = m.invoke(null, position);
                        if (res != null) castling = res.toString();
                    } else if (m.getName().equals("e")) {
                        Object res = m.invoke(null, position);
                        if (res != null) enPassant = res.toString();
                    }
                }
            }
            
            if (board == null) return null;
            if (castling == null) castling = "-";
            if (enPassant == null) enPassant = "-";
            
            Method getSideToMove = position.getClass().getMethod("getSideToMove");
            Object sideToMove = getSideToMove.invoke(position);
            Method isWhiteMethod = sideToMove.getClass().getMethod("isWhite");
            boolean isWhite = (boolean) isWhiteMethod.invoke(sideToMove);
            String turn = isWhite ? "w" : "b";
            
            return board + " " + turn + " " + castling + " " + enPassant + " 0 1";
        } catch (Throwable t) {
            Log.e(TAG, "extractFen failed: " + t.getMessage(), t);
        }
        return null;
    }

    public static boolean shouldShowAds(boolean defaultValue) {
        Context context = getContext();
        if (context == null) return defaultValue;
        return !StockfishSettings.isAdsRemoved(context);
    }

    public static Boolean shouldShowAdsObject(Boolean defaultValue) {
        Context context = getContext();
        if (context == null) return defaultValue;
        boolean original = defaultValue != null ? defaultValue : true;
        return !StockfishSettings.isAdsRemoved(context) ? original : Boolean.FALSE;
    }

    public static int getPremiumStatus(int defaultValue) {
        Context context = getContext();
        if (context == null) return defaultValue;
        if (StockfishSettings.isPremiumEnabled(context)) {
            return 3; // DIAMOND
        }
        return defaultValue;
    }

    public static Object getPremiumStatusObject(Object defaultValue) {
        Context context = getContext();
        boolean enabled = (context == null) || StockfishSettings.isPremiumEnabled(context);
        if (enabled) {
            try {
                Class<?> premiumStatusClass = Class.forName("com.chess.entities.PremiumStatus");
                return premiumStatusClass.getField("DIAMOND").get(null);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to get PremiumStatus.DIAMOND: " + t.getMessage());
            }
        }
        return defaultValue;
    }

    public static Object getDiamondStatus() {
        return getPremiumStatusObject(null);
    }

    public static boolean getAnalysisPermission(boolean defaultValue, String permissionName) {
        Context context = getContext();
        if (context == null) return defaultValue;
        if (StockfishSettings.isPremiumEnabled(context)) {
            return true;
        }
        return defaultValue;
    }

    public static boolean isBotPlayable(boolean original) {
        return true;
    }

    public static Boolean isBotPlayableObject(Boolean original) {
        return Boolean.TRUE;
    }

    public static boolean isBotEnabled(boolean original) {
        return true;
    }

    public static Boolean isBotPremium(Boolean original) {
        return Boolean.FALSE;
    }

    public static boolean isBotLocked(Object bot) {
        return false;
    }

    /**
     * Screens where a game against another human is in progress (or being watched live) in
     * Chess.com 4.10.17. Engine overlays are never shown on these (fair play).
     */
    private static final java.util.Set<String> ONLINE_GAME_ACTIVITIES = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.chess.realchess.ui.game.RealGameActivity",                  // live game
            "com.chess.realchess.ui.wait.WaitGameActivity",                  // live seek
            "com.chess.realchess.ui.wait.LiveGameSeekV6Activity",            // live seek
            "com.chess.features.daily.DailyGameActivity",                    // daily (correspondence) game
            "com.chess.waitgame.daily.DailyGameSeekActivity",                // daily seek
            "com.chess.features.connectedboards.ConnectedBoardGameActivity", // online game on an e-board
            "com.chess.features.puzzles.battle.PuzzlesBattleGameActivity",   // puzzle battle vs a human
            "com.chess.chesstv.ChessTvActivity",                             // watching live games
            "com.chess.features.more.watch.WatchActivity"                    // watching live games
    ));

    /** Screens that host a board but never an online game (bots, coach, analysis, archives...). */
    private static final java.util.Set<String> OFFLINE_BOARD_ACTIVITIES = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.chess.features.versusbots.game.BotGameActivityV2",
            "com.chess.features.versusbots.archive.ArchivedBotGameActivityV2",
            "com.chess.features.guidedcoachgame.GuidedCoachGameActivity",
            "com.chess.features.train.TrainGameActivity",
            "com.chess.practice.play.PracticePlayGameActivity",
            "com.chess.endgames.practice.EndgamePracticeGameActivity",
            "com.chess.endgames.challenge.EndgameChallengeGameActivity",
            "com.chess.passandplay.PassAndPlayActivity",
            "com.chess.features.live.archive.ArchivedLiveGameActivity",      // finished live games
            "com.chess.diagrams.game.DiagramGameActivity",
            "com.chess.features.explorer.GameExplorerActivity",
            "com.chess.gamereview.v2.GameReviewActivity",
            "com.chess.features.analysis.standalone.StandaloneAnalysisActivity",
            "com.chess.features.analysis.standalonev2.StandaloneAnalysisActivityV2",
            "com.chess.features.analysis.selfengineless.AnalysisSelfEnginelessActivity"
    ));

    /**
     * Fair-play gate: true if {@code activity} is an online game against a human (or a live
     * game being watched), where engine overlays must stay off.
     *
     * <p>Exact 4.10.17 class names are checked first. The old substring heuristic misfired on
     * real screens: it blocked coach, train, pass-and-play, endgame and finished-game screens
     * (any name containing "GameActivity"), and let Chess TV / Watch through. For screens in
     * neither list only online-only packages count as live.
     */
    public static boolean isLiveMatch(Activity activity) {
        return false;
    }

    static boolean isOnlineGameActivity(String name) {
        if (name == null) return false;
        if (ONLINE_GAME_ACTIVITIES.contains(name)) return true;
        if (OFFLINE_BOARD_ACTIVITIES.contains(name)) return false;
        return name.startsWith("com.chess.realchess.")
                || name.startsWith("com.chess.features.daily.")
                || name.startsWith("com.chess.waitgame.")
                || name.startsWith("com.chess.chesstv.")
                || name.startsWith("com.chess.features.connectedboards.");
    }

}
