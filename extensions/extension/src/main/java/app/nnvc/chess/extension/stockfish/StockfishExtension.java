package app.nnvc.chess.extension.stockfish;

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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import app.nnvc.chess.extension.updater.InAppUpdater;

@SuppressWarnings({"unused", "JavaReflectionMemberAccess"})
public class StockfishExtension {

    private static final String TAG = "NNVC";

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

    private static volatile String lastLoadedMove0Fen = null;
    private static volatile String currentBoardFen = null;
    private static volatile boolean isMove0AnalysisScheduled = false;
    private static volatile boolean isMove0AnalysisCompleted = false;
    private static volatile Object pendingStateImpl = null;
    private static volatile Object pendingPositionObject = null;
    private static volatile android.os.Handler move0RetryHandler = null;

    public static String getCurrentBoardFen() {
        return currentBoardFen;
    }

    static Object getPendingStateImpl() {
        return pendingStateImpl;
    }

    static Object getPendingPositionObject() {
        return pendingPositionObject;
    }

    static void setPendingPosition(Object state, Object pos) {
        pendingStateImpl = state;
        pendingPositionObject = pos;
    }

    static void clearPendingPosition() {
        pendingStateImpl = null;
        pendingPositionObject = null;
    }

    static boolean isMove0AnalysisCompleted() {
        return isMove0AnalysisCompleted;
    }

    static void setMove0AnalysisCompleted(boolean val) {
        isMove0AnalysisCompleted = val;
    }

    static boolean isEngineReady() {
        return engineReady;
    }

    static void setEngineReady(boolean ready) {
        engineReady = ready;
    }

    private static android.os.Handler getMove0RetryHandler() {
        if (move0RetryHandler == null) {
            try {
                move0RetryHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            } catch (Throwable ignored) {}
        }
        return move0RetryHandler;
    }

    private static volatile android.os.Handler watchdogHandler = null;

    private static android.os.Handler getWatchdogHandler() {
        if (watchdogHandler == null) {
            try {
                watchdogHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            } catch (Throwable ignored) {}
        }
        return watchdogHandler;
    }

    private static Runnable gameOverWatchdogRunnable = null;
    private static android.view.ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener = null;
    private static WeakReference<View> currentDecorViewRef = new WeakReference<>(null);
    private static volatile boolean lastGameOverHandled = false;
    private static volatile long lastEngineThinkMs = 0;

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

    public static boolean isReviewOrAnalysisActivity(Activity activity) {
        if (activity == null) return false;
        String name = activity.getClass().getName();
        return name.contains("GameReview")
                || name.contains("StandaloneAnalysis")
                || name.contains("AnalysisSelfEngineless")
                || name.contains("ArchivedLiveGame")
                || name.contains("ArchiveSearch")
                || name.contains("ReviewPuzzle")
                || name.contains("RecentRushReview")
                || name.contains("AnalysisActivity")
                || name.contains("UserProfile")
                || name.contains("ProfileActivity");
    }

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

    private static volatile boolean soundSilenced = false;

    public static void silenceIllegalMoveSound() {
        if (soundSilenced) return;
        try {
            // Giữ lại âm thanh IllegalMove gốc của Chess.com (k.java).
            // Chỉ chặn StillInCheck (s.java) - nguồn phát âm thanh lỗi khi thao tác quân cờ / khai cuộc:
            muteSoundEventClass("com.chess.soundandhaptics.api.s"); // StillInCheck
            soundSilenced = true;
        } catch (Throwable ignored) {}
    }

    public static void ensureEngineReady() {
        silenceIllegalMoveSound();
        if (engineReady || isInitializing) return;
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentApplicationMethod = activityThreadClass.getMethod("currentApplication");
            Context resolved = (Context) currentApplicationMethod.invoke(null);
            if (resolved == null) {
                Activity act = getCurrentActivity();
                if (act != null) {
                    resolved = act.getApplicationContext();
                }
            }
            final Context ctx = resolved;
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
                            NnvcLogger.e(TAG, "Failed to start torch engine early: " + te.getMessage(), te);
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
                                    if (pendingPositionObject != null) {
                                        Object s = pendingStateImpl;
                                        Object p = pendingPositionObject;
                                        pendingStateImpl = null;
                                        pendingPositionObject = null;
                                        Log.i(TAG, "Engine ready: dispatching cached pending position for Move 0.");
                                        onBoardChanged(s, p);
                                    } else {
                                        triggerAnalysisForCurrentState();
                                    }
                                }
                            });
                        } else {
                            pendingStateImpl = null;
                            pendingPositionObject = null;
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Exception in async Stockfish init: " + t.getMessage());
                        pendingStateImpl = null;
                        pendingPositionObject = null;
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
        if (positionObject == null) return;
        
        silenceIllegalMoveSound();
        if (stateImplObject != null) {
            ArrowInjector.clearEngineArrows(stateImplObject);
            stateImplRef.set(new WeakReference<>(stateImplObject));
        }

        final Activity activity = getCurrentActivity();
        if (activity != null) {
            unlockScreenshots(activity);
            if (isReviewOrAnalysisActivity(activity)) {
                OverlayManager.hideArrowOverlay(true);
                OverlayManager.hideEvalBar();
                OverlayManager.hideEngineInfo();
                OverlayManager.hideAccuracyEloWidget();
                if (stateImplObject != null) {
                    ArrowInjector.clearEngineArrows(stateImplObject);
                }
                StockfishBridge.stopSearch();
                Context c = getContext();
                if (c != null) {
                    TorchEngine.getInstance(c).cancelPendingRequests();
                }
                return;
            }
            if (isLiveMatch(activity)) {
                isReviewMode = false;
            }
        }

        if (isGameOver(activity)) {
            OverlayManager.hideArrowOverlay();
            if (stateImplObject != null) {
                ArrowInjector.clearEngineArrows(stateImplObject);
            }
            StockfishBridge.stopSearch();
            Context c = getContext();
            if (c != null) {
                TorchEngine.getInstance(c).cancelPendingRequests();
            }
            return;
        }

        GestureInterceptor.ensureGestureInterceptorRegistered();
        ensureEngineReady();
        if (!engineReady) {
            pendingStateImpl = stateImplObject;
            pendingPositionObject = positionObject;
            Log.d(TAG, "Stockfish engine not ready yet, cached pending position for Move 0 dispatch.");
            return;
        }
        pendingStateImpl = null;
        pendingPositionObject = null;

        Context ctx = getContext();
        if (ctx != null && !StockfishSettings.isEngineEnabled(ctx)) {
            Log.d(TAG, "Engine is disabled in settings.");
            OverlayManager.hideArrowOverlay();
            OverlayManager.hideEvalBar();
            OverlayManager.hideMateAnnouncement();
            OverlayManager.hideEngineInfo();
            return;
        }

        OverlayManager.clearClassificationBadge();
        lastArrowSignature = null;

        String fen = extractFen(positionObject);
        if (fen == null) {
            Log.w(TAG, "onBoardChanged: could not extract FEN");
            return;
        }

        Log.d(TAG, "Position changed → FEN: " + fen);
        currentBoardFen = fen;
        AutoMoveManager.cancelPendingAutoMove("onBoardChanged: position changed to " + fen);
        boolean isStartPos = fen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
        if (isStartPos) {
            if (isMove0AnalysisScheduled && fen.equals(lastLoadedMove0Fen) && (OverlayManager.hasArrows() || isMove0AnalysisCompleted)) {
                Log.d(TAG, "[MOVE 0 SKIP] onBoardChanged Move 0 already scheduled and rendered, skipping duplicate reset.");
                return;
            }
            lastLoadedMove0Fen = fen;
            isMove0AnalysisScheduled = true;
            isMove0AnalysisCompleted = false;
            if (OverlayManager.hasArrows() || isMove0AnalysisCompleted) {
                android.os.Handler h = getMove0RetryHandler();
                if (h != null) h.removeCallbacksAndMessages(null);
            }

            OverlayManager.clearClassificationBadge();
            OverlayManager.hideArrowOverlay();
            OverlayManager.resetAccuracyEloWidget();
            if (stateImplObject != null) {
                ArrowInjector.clearEngineArrows(stateImplObject);
            }
            if (ctx != null) {
                TorchEngine.getInstance(ctx).cancelPendingRequests();
                WasmEngineManager.getInstance(ctx).resetClassifyGame();
            }
            resetUserWhiteCache();
            MoveClassifier.clearHistory();
            OverlayManager.resetEvalBar();
        } else {
            isMove0AnalysisScheduled = false;
            lastLoadedMove0Fen = null;
            isMove0AnalysisCompleted = false;
        }
        MoveClassifier.updateHistory(fen, positionObject);
        if (activity != null && stateImplObject != null) {
            EloScanner.scanAndApply(activity, stateImplObject);
        }

        // 1. Independent Eval Bar: Dedicated Stockfish 18 classify instance (Depth 10, MultiPV 3, Hash 64)
        // Khi Play Engine là Stockfish 18 và đang BẬT engine, playWorker đã tự đánh giá Eval Bar ở displayLiveResult,
        // bỏ qua evaluateClassifyPosition để tránh chạy 2 worker Stockfish 18 song song gây giật lag
        if (ctx != null && StockfishSettings.isEvalBarEnabled(ctx)) {
            String playEngine = StockfishSettings.getEngineChoice(ctx);
            boolean engineOn = StockfishSettings.isEngineEnabled(ctx);
            if (!("stockfish18".equalsIgnoreCase(playEngine) && engineOn)) {
                WasmEngineManager.getInstance(ctx).evaluateClassifyPosition(fen, MoveClassifier.getCurrentPositionMoves());
            }
        }

        // 2. Independent Move Classification: Torch CEE with 100ms debounce
        if (ctx != null && !isStartPos) {
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
        if (activity != null && isReviewOrAnalysisActivity(activity)) {
            OverlayManager.hideArrowOverlay(true);
            ArrowInjector.clearEngineArrows(stateImplObject);
            return;
        }
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

    private static volatile String lastThreatCacheFen = null;
    private static volatile boolean lastThreatCacheWhite = false;
    private static volatile String lastThreatCacheMove = null;
    private static volatile List<BoardUtil.ThreatInfo> lastThreatCacheResult = Collections.emptyList();

    public static void resetArrowSignature() {
        lastArrowSignature = null;
        lastThreatCacheFen = null;
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
        Context context = getContext();
        if (context != null) {
            int depth = StockfishSettings.isAutoDepthEnabled(context)
                    ? StockfishSettings.autoDepthForElo(StockfishSettings.getElo(context))
                    : StockfishSettings.getDepth(context);
            int multiPV = StockfishSettings.getMultiPV(context);
            if (StockfishSettings.isSafeModeEnabled(context)) {
                multiPV = Math.max(3, multiPV);
            }
            int limited = StockfishSettings.isLimitStrength(context) ? StockfishSettings.getElo(context) : 0;
            String engineChoice = StockfishSettings.getEngineChoice(context);
            String komodoStyle = StockfishSettings.getKomodoStyle(context);
            StockfishProcess.AnalysisResult cached = StockfishBridge.getCached(fen, depth, multiPV, limited, engineChoice, komodoStyle);
            if (cached != null && !cached.moves.isEmpty()) {
                lastScheduledKey = posKey;
                Future<?> prev = currentJob;
                if (prev != null && !prev.isDone()) {
                    prev.cancel(true);
                    StockfishBridge.stopSearch();
                }
                lastEngineThinkMs = 0;
                displayLiveResult(context, fen, cached, true);
                return;
            }
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
                Context ctx = getContext();
                if (ctx == null) return;

                int depth = StockfishSettings.isAutoDepthEnabled(ctx)
                        ? StockfishSettings.autoDepthForElo(StockfishSettings.getElo(ctx))
                        : StockfishSettings.getDepth(ctx);
                int multiPV = StockfishSettings.getMultiPV(ctx);
                if (StockfishSettings.isSafeModeEnabled(ctx)) {
                    multiPV = Math.max(3, multiPV);
                }

                Log.d(TAG, "Analysing FEN with " + StockfishSettings.getEngineChoice(ctx) + " at depth " + depth + " with MultiPV=" + multiPV + "…");
                // Stream intermediate depths to the classifier, but NEVER paint intermediate arrows to UI.
                // In NNVC Extension standard (nnvc_main_bundle.js lines 6138-6156 & 16474), arrows are painted
                // atomically ONLY once after engine finishes full search (bestmove), preventing any jumping/jitter.
                long searchStartMs = System.currentTimeMillis();
                StockfishProcess.AnalysisResult result = StockfishBridge.analyze(fen, depth, multiPV,
                        partial -> {
                            MoveClassifier.recordResult(fen, partial);
                        });
                lastEngineThinkMs = Math.max(0, System.currentTimeMillis() - searchStartMs);

                MoveClassifier.recordResult(fen, result);

                // The user moved on while we were searching: never paint an outdated result
                if (isStale(jobKey)) {
                    return;
                }

                if (result.moves.isEmpty()) {
                    if (result.terminal) {
                        OverlayManager.hideArrowOverlay(true);
                        ArrowInjector.clearEngineArrows(getStateImpl());
                        TorchEngine.getInstance(ctx).cancelPendingRequests();
                        TorchEngine.log("[GAME OVER] Ván đấu kết thúc (terminal=true). Đã ẩn mũi tên và hủy hàng chờ Torch.");
                    }
                    Log.d(TAG, "Engine returned no best moves.");
                    return;
                }

                Log.i(TAG, "Best moves: " + result.moves + ", Score: " + result.score + ", depth " + result.depth);
                displayLiveResult(ctx, fen, result, true);

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
     * Paints an analysis result (arrows, engine info, mate banner). Intermediate results
     * ({@code isFinal == false}) only update arrows; the mate banner waits for the
     * final search so it does not flicker.
     */
    private static void displayLiveResult(Context context, String fen,
                                          StockfishProcess.AnalysisResult result, boolean isFinal) {
        if (result == null || result.moves.isEmpty()) return;
        if (!StockfishSettings.isEngineEnabled(context)) return;

        Activity activity = getCurrentActivity();
        if (activity != null && isReviewOrAnalysisActivity(activity)) {
            OverlayManager.hideArrowOverlay(true);
            OverlayManager.hideEvalBar();
            OverlayManager.hideEngineInfo();
            OverlayManager.hideAccuracyEloWidget();
            ArrowInjector.clearEngineArrows(getStateImpl());
            StockfishBridge.stopSearch();
            TorchEngine.getInstance(context).cancelPendingRequests();
            return;
        }
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

        boolean isStartPos = fen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
        boolean showArrows = !disableOverlays && StockfishSettings.isArrowsVisible(context);
        if (showArrows && StockfishSettings.isMySideOnly(context) && !isMyTurn) {
            showArrows = false;
        }

        boolean threatEnabled = !disableOverlays && StockfishSettings.isThreatArrowsEnabled(context);
        List<BoardUtil.ThreatInfo> threatListToInject = Collections.emptyList();
        if (threatEnabled) {
            // MŨI TÊN ĐE DỌA (THREAT ARROWS) - Màu Đỏ Neon chuẩn Extension (chỉ hiện đối thủ):
            boolean userIsWhiteSide = (userWhite != null) ? userWhite.booleanValue() : isWhiteTurn;
            String lastMove = MoveClassifier.getLastDeduceUciMove();
            if (fen.equals(lastThreatCacheFen) && userIsWhiteSide == lastThreatCacheWhite &&
                    (lastMove == null ? lastThreatCacheMove == null : lastMove.equals(lastThreatCacheMove))) {
                threatListToInject = lastThreatCacheResult;
            } else {
                List<BoardUtil.ThreatInfo> rawThreats = BoardUtil.computeBoardThreats(fen, userIsWhiteSide, lastMove, "enemy");
                if (rawThreats != null && !rawThreats.isEmpty()) {
                    threatListToInject = new ArrayList<>();
                    for (BoardUtil.ThreatInfo ti : rawThreats) {
                        if ("danger".equalsIgnoreCase(ti.type)) {
                            threatListToInject.add(ti);
                        }
                    }
                }
                lastThreatCacheFen = fen;
                lastThreatCacheWhite = userIsWhiteSide;
                lastThreatCacheMove = lastMove;
                lastThreatCacheResult = threatListToInject;
            }
        }

        boolean premoveEnabled = !disableOverlays && StockfishSettings.isPremoveArrowsEnabled(context);
        String premoveMoveToInject = null;
        if (premoveEnabled && !isMyTurn) {
            // MŨI TÊN PREMOVE (Premove Arrow - Màu cam hổ phách neon):
            // CHỈ hiển thị sau khi người chơi vừa thực hiện nước đi (đang ở lượt đối thủ, !isMyTurn).
            // Tìm nước đáp trả của người chơi (recapture / piece exchange) để chuẩn bị sẵn sàng premove:
            String oppMove = (result.moves != null && !result.moves.isEmpty()) ? result.moves.get(0) : null;
            String userReply = null;
            if (result.pv != null && result.pv.size() > 1) {
                userReply = result.pv.get(1);
            } else if (result.ponder != null && !result.ponder.isEmpty()) {
                userReply = result.ponder;
            }

            if (oppMove != null && oppMove.length() >= 4 && userReply != null && userReply.length() >= 4) {
                String replyFrom = userReply.substring(0, 2);
                String replyTo = userReply.substring(2, 4);
                String oppTo = oppMove.substring(2, 4);

                boolean userIsWhiteSide = (userWhite != null) ? userWhite.booleanValue() : !isWhiteTurn;
                boolean oppWhite = !userIsWhiteSide;
                char[] currentBoard = BoardUtil.parseBoard(fen);

                // Điều kiện 1: Nước đi trao đổi quân hiển nhiên (Recapture piece exchange)
                boolean fromHasUserPiece = isUserPiece(fen, replyFrom, userIsWhiteSide);
                boolean targetHasUserPiece = isUserPiece(fen, replyTo, userIsWhiteSide);
                boolean isExchangeRecapture = replyTo.equals(oppTo) && targetHasUserPiece && fromHasUserPiece;

                // Điều kiện 2: Tôi chiếu đối thủ mà đối thủ chỉ có đúng 1 nước để đi (Only Move khi đối thủ bị chiếu)
                // (Đối thủ 100% bắt buộc phải đi oppMove, người chơi premove trước nước đáp trả userReply)
                boolean isOppInCheckOnlyMove = false;
                if (BoardUtil.isKingInCheck(currentBoard, oppWhite)) {
                    if (BoardUtil.isOnlyLegalMove(currentBoard, oppWhite)) {
                        isOppInCheckOnlyMove = true;
                    }
                }

                // Điều kiện 3: Đối thủ chuẩn bị chiếu tôi mà tôi chỉ có đúng 1 nước để đi (Only Move khi người chơi bị chiếu)
                // (Sau khi đối thủ đi oppMove, người chơi bị chiếu và chỉ có đúng 1 nước thoát chiếu duy nhất userReply)
                boolean isUserInCheckOnlyMove = false;
                char[] boardAfterOpp = currentBoard.clone();
                BoardUtil.apply(boardAfterOpp, oppMove);
                if (BoardUtil.isKingInCheck(boardAfterOpp, userIsWhiteSide)) {
                    if (BoardUtil.isOnlyLegalMove(boardAfterOpp, userIsWhiteSide)) {
                        isUserInCheckOnlyMove = true;
                    }
                }

                if (isExchangeRecapture || isOppInCheckOnlyMove || isUserInCheckOnlyMove) {
                    premoveMoveToInject = userReply;
                }
            }
        }

        boolean showThreat = (threatListToInject != null && !threatListToInject.isEmpty());
        boolean showPremove = (premoveMoveToInject != null);
        // Ensure result is a safe local copy before Safe Mode reordering to prevent cache contamination
        StockfishProcess.AnalysisResult liveResult = new StockfishProcess.AnalysisResult(
                new java.util.ArrayList<>(result.moves),
                result.score,
                result.hasMate,
                result.mateIn,
                result.wdlWin,
                result.wdlDraw,
                result.wdlLoss,
                result.ponder,
                result.pv != null ? new java.util.ArrayList<>(result.pv) : new java.util.ArrayList<>(),
                result.lineScores != null ? result.lineScores.clone() : new float[0],
                result.sideToMoveScores != null ? result.sideToMoveScores.clone() : new float[0],
                result.lineMates != null ? result.lineMates.clone() : new boolean[0],
                result.lineMateIn != null ? result.lineMateIn.clone() : new int[0],
                result.depth,
                result.terminal
        );
        result = liveResult;

        // ── Safe Mode Move Selection & Synchronization (100% Extension Parity) ──
        String safeChosenMove = null;
        if (isMyTurn && result.moves != null && !result.moves.isEmpty()) {
            boolean isSafeMode = StockfishSettings.isSafeModeEnabled(context);
            int arrowLimit = StockfishSettings.getMultiPV(context);
            boolean finalUserIsWhite = (userWhite != null) ? userWhite.booleanValue() : isWhiteTurn;
            long myClockMs = -1L;
            long oppClockMs = -1L;
            if (activity != null) {
                SmartDelayManager.LiveClockInfo clk = SmartDelayManager.detectLiveClocks(activity, finalUserIsWhite);
                if (clk != null) {
                    if (clk.mySec >= 0) myClockMs = (long) (clk.mySec * 1000.0);
                    if (clk.oppSec >= 0) oppClockMs = (long) (clk.oppSec * 1000.0);
                }
            }

            SafeMoveSelector.MoveDecision decision = SafeMoveSelector.selectMove(
                    result.moves, result.sideToMoveScores, result.lineMates, result.lineMateIn,
                    arrowLimit, myClockMs, oppClockMs, fen, isSafeMode, finalUserIsWhite);
            safeChosenMove = decision.chosenMove;

            // If selected safe move is not index 0, reorder result.moves and line arrays
            // so the chosen safe move becomes index 0 (Tier 1 Gold Arrow)
            if (decision.chosenIndex > 0 && decision.chosenIndex < result.moves.size()) {
                int pick = decision.chosenIndex;
                String pickedMove = result.moves.remove(pick);
                result.moves.add(0, pickedMove);

                if (result.sideToMoveScores != null && pick < result.sideToMoveScores.length) {
                    float pickedScore = result.sideToMoveScores[pick];
                    for (int k = pick; k > 0; k--) {
                        result.sideToMoveScores[k] = result.sideToMoveScores[k - 1];
                    }
                    result.sideToMoveScores[0] = pickedScore;
                }
                if (result.lineScores != null && pick < result.lineScores.length) {
                    float pickedScore = result.lineScores[pick];
                    for (int k = pick; k > 0; k--) {
                        result.lineScores[k] = result.lineScores[k - 1];
                    }
                    result.lineScores[0] = pickedScore;
                }
                if (result.lineMates != null && pick < result.lineMates.length) {
                    boolean pickedMate = result.lineMates[pick];
                    for (int k = pick; k > 0; k--) {
                        result.lineMates[k] = result.lineMates[k - 1];
                    }
                    result.lineMates[0] = pickedMate;
                }
                if (result.lineMateIn != null && pick < result.lineMateIn.length) {
                    int pickedMateIn = result.lineMateIn[pick];
                    for (int k = pick; k > 0; k--) {
                        result.lineMateIn[k] = result.lineMateIn[k - 1];
                    }
                    result.lineMateIn[0] = pickedMateIn;
                }
                NnvcLogger.i(TAG, "🛡️ [SAFE MODE] Picked Top " + (pick + 1) + " [" + pickedMove + "] (Reason: " + decision.reason + " | Gap: " + decision.cpGap + "cp). Reordered to Tier 1 Gold Arrow.");
            }
        }

        if (showArrows || showThreat || showPremove) {
            int maxArrows = StockfishSettings.getMultiPV(context);
            java.util.List<String> movesToInject = java.util.Collections.emptyList();
            if (showArrows && result.moves != null && !result.moves.isEmpty()) {
                int count = Math.min(result.moves.size(), Math.max(1, maxArrows));
                movesToInject = new java.util.ArrayList<>(result.moves.subList(0, count));
            }

            if (showThreat) {
                ArrowInjector.injectThreatArrow(context, getStateImpl(), null);
            }

            String sig = fen + '|' + (showArrows ? movesToInject.toString() : "") + '|'
                    + (showThreat ? threatListToInject.toString() : "") + '|'
                    + (showPremove && premoveMoveToInject != null ? premoveMoveToInject : "") + '|'
                    + StockfishSettings.getArrowTierColor(context, 1) + '|'
                    + StockfishSettings.getArrowTierColor(context, 2) + '|'
                    + StockfishSettings.getArrowTierColor(context, 3);
            if (!sig.equals(lastArrowSignature)) {
                lastArrowSignature = sig;
                boolean whiteTurn = isWhiteTurnFromFen(fen);
                OverlayManager.updateArrowOverlay(movesToInject, threatListToInject, premoveMoveToInject, result.sideToMoveScores, result.lineMates, result.lineMateIn, whiteTurn, getStateImpl());
            }
        } else if (isFinal) {
            lastArrowSignature = null;
            OverlayManager.hideArrowOverlay();
            ArrowInjector.clearEngineArrows(getStateImpl());
        }

        if (disableOverlays || !StockfishSettings.isEvalBarEnabled(context)) {
            OverlayManager.hideEvalBar();
        } else {
            String engineChoice = StockfishSettings.getEngineChoice(context);
            if ("stockfish18".equalsIgnoreCase(engineChoice) && result != null && result.depth >= 6) {
                OverlayManager.updateEvalBar(result.score, result.hasMate, result.mateIn, getStateImpl());
            }
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
        if (isStartPos) {
            if (!showArrows || OverlayManager.hasArrows()) {
                isMove0AnalysisCompleted = true;
            }
        }
        OverlayManager.hideMateAnnouncement();

        // ── Auto Move Execution (100% Parity with NNVC Desktop Extension) ──
        boolean autoMoveEnabled = StockfishSettings.isAutoMoveEnabled(context);
        if (isFinal) {
            if (!autoMoveEnabled) {
                NnvcLogger.d("NNVC_AutoMove", "[AUTO MOVE] Bỏ qua: Tính năng Tự Động Đi (Auto Move) đang TẮT trong cài đặt.");
            } else if (!isMyTurn) {
                NnvcLogger.d("NNVC_AutoMove", "[AUTO MOVE] Bỏ qua: Không phải lượt đi của người chơi (isWhiteTurn=" + isWhiteTurn + ", userWhite=" + userWhite + ").");
            } else {
                boolean over = (activity != null && isGameOver(activity));
                boolean review = (activity != null && isReviewOrAnalysisActivity(activity));
                if (over || review) {
                    NnvcLogger.d("NNVC_AutoMove", "[AUTO MOVE] Bỏ qua: gameOver=" + over + ", review=" + review);
                } else {
                    String bestMove = safeChosenMove != null ? safeChosenMove : ((result.moves != null && !result.moves.isEmpty()) ? result.moves.get(0) : null);
                    if (bestMove != null && bestMove.length() >= 4) {
                        View bv = null;
                        if (activity != null && activity.getWindow() != null) {
                            bv = OverlayManager.findChessBoardView(activity.getWindow().getDecorView());
                        }
                        if (bv == null) {
                            bv = OverlayManager.getCachedBoardView();
                        }
                        if (bv != null) {
                            boolean flipped = OverlayManager.isBoardFlipped(bv, getStateImpl());
                            boolean finalUserIsWhite = (userWhite != null) ? userWhite.booleanValue() : true;
                            NnvcLogger.i("NNVC_AutoMove", "⚡ [AUTO MOVE TRIGGER] Chuẩn bị đi nước: " + bestMove + " (flipped=" + flipped + ", thinkMs=" + lastEngineThinkMs + "ms)");
                            AutoMoveManager.executeAutoMove(context, bv, bestMove, flipped, fen, result.score, lastEngineThinkMs, finalUserIsWhite, activity);
                        } else {
                            NnvcLogger.w("NNVC_AutoMove", "❌ [AUTO MOVE ERROR] Không tìm thấy ChessBoardView trên giao diện.");
                        }
                    }
                }
            }
        }
    }

    public static char getPieceAtSquare(String fen, String square) {
        if (fen == null || square == null || square.length() < 2) return '.';
        int file = square.charAt(0) - 'a';
        int rank = square.charAt(1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) return '.';

        int targetRow = 7 - rank; // 0 is 8th rank, 7 is 1st rank
        String boardPart = fen.trim().split("\\s+")[0];
        String[] rows = boardPart.split("/");
        if (rows.length != 8 || targetRow < 0 || targetRow >= 8) return '.';

        String row = rows[targetRow];
        int col = 0;
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (Character.isDigit(c)) {
                col += (c - '0');
            } else {
                if (col == file) {
                    return c;
                }
                col++;
            }
            if (col > file) break;
        }
        return '.';
    }

    public static boolean isUserPiece(String fen, String square, boolean userIsWhite) {
        char piece = getPieceAtSquare(fen, square);
        if (piece == '.') return false;
        return userIsWhite ? Character.isUpperCase(piece) : Character.isLowerCase(piece);
    }

    private static boolean isWhiteTurnFromFen(String fen) {
        if (fen == null) return true;
        String[] parts = fen.trim().split("\\s+");
        return parts.length < 2 || !"b".equalsIgnoreCase(parts[1]);
    }

    private static void registerLifecycleCallbacks(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState) {
                silenceIllegalMoveSound();
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

                // Auto-check remote in-app updates & live announcements with throttling
                try {
                    InAppUpdater.checkOnAppResume(activity);
                } catch (Throwable ignored) {}

                if (isReviewOrAnalysisActivity(activity)) {
                    stopGameOverWatchdog();
                    lastLoadedMove0Fen = null;
                    isMove0AnalysisScheduled = false;
                    OverlayManager.hideArrowOverlay(true);
                    OverlayManager.hideEvalBar();
                    OverlayManager.hideEngineInfo();
                    OverlayManager.hideAccuracyEloWidget();
                    ArrowInjector.clearEngineArrows(getStateImpl());
                    StockfishBridge.stopSearch();
                    Context c = getContext();
                    if (c != null) {
                        try {
                            TorchEngine.getInstance(c).cancelPendingRequests();
                        } catch (Throwable ignored) {}
                    }
                    return;
                }
                GestureInterceptor.registerGestureInterceptor(activity);
                lastArrowSignature = null;
                lastScheduledKey = null;
                lastLoadedMove0Fen = null;
                isMove0AnalysisScheduled = false;
                isMove0AnalysisCompleted = false;

                setupGameOverWatchdog(activity);

                // Cancel any pending retries from prior resumed events
                android.os.Handler h = getMove0RetryHandler();
                if (h != null) h.removeCallbacksAndMessages(null);

                // Multiple retry checks to catch Move 0 as board view loads into view hierarchy
                for (long delay : new long[]{100, 300, 600, 1200, 2400, 4000}) {
                    if (h != null) {
                        h.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (activity.isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
                                    if (!isGameOver(activity)) {
                                        boolean loaded = tryLoadMove0(activity);
                                        if (loaded && (OverlayManager.hasArrows() || isMove0AnalysisCompleted)) {
                                            android.os.Handler curH = getMove0RetryHandler();
                                            if (curH != null) curH.removeCallbacksAndMessages(null);
                                        }
                                        invalidateAllBoards();
                                    } else {
                                        OverlayManager.hideArrowOverlay(true);
                                    }
                                } catch (Throwable t) {
                                    Log.e(TAG, "Failed to refresh onActivityResumed: " + t.getMessage());
                                }
                            }
                        }, delay);
                    }
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (resumedActivity.get() == activity) resumedActivity = new WeakReference<>(null);
                AutoFindManager.cancelCountdown("Activity paused");
                stopGameOverWatchdog();
                lastLoadedMove0Fen = null;
                isMove0AnalysisScheduled = false;
                isMove0AnalysisCompleted = false;
                android.os.Handler ph = getMove0RetryHandler();
                if (ph != null) ph.removeCallbacksAndMessages(null);
            }

            @Override
            public void onActivityStopped(Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(Activity activity, android.os.Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {
                AutoFindManager.cancelCountdown("Activity destroyed");
                stopGameOverWatchdog();
                lastLoadedMove0Fen = null;
                isMove0AnalysisScheduled = false;
                isMove0AnalysisCompleted = false;
                android.os.Handler dh = getMove0RetryHandler();
                if (dh != null) dh.removeCallbacksAndMessages(null);
            }
        });
    }

    private static void setupGameOverWatchdog(final Activity activity) {
        stopGameOverWatchdog();
        lastGameOverHandled = false;

        // Check immediately
        checkAndHandleGameOver(activity);

        // 1. Layout listener on decorView with debounce to eliminate 60fps main thread overhead
        try {
            Window window = activity.getWindow();
            if (window != null) {
                View decorView = window.getDecorView();
                if (decorView != null) {
                    currentDecorViewRef = new WeakReference<>(decorView);
                    globalLayoutListener = new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        private long lastCheckTime = 0L;
                        @Override
                        public void onGlobalLayout() {
                            long now = android.os.SystemClock.uptimeMillis();
                            if (now - lastCheckTime >= 500L) {
                                lastCheckTime = now;
                                checkAndHandleGameOver(activity);
                            }
                        }
                    };
                    decorView.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to register OnGlobalLayoutListener: " + t.getMessage());
        }

        // 2. Periodic watchdog Runnable (300ms)
        gameOverWatchdogRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (activity.isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
                        return;
                    }
                    unlockScreenshots(activity);
                    boolean over = checkAndHandleGameOver(activity);
                    if (!over && !isReviewOrAnalysisActivity(activity)) {
                        checkAndTriggerMove0Watchdog(activity);
                    }
                    android.os.Handler wh = getWatchdogHandler();
                    if (wh != null) wh.postDelayed(this, over ? 500 : 300);
                } catch (Throwable ignored) {}
            }
        };
        android.os.Handler wh = getWatchdogHandler();
        if (wh != null) wh.postDelayed(gameOverWatchdogRunnable, 300);
    }

    private static volatile long lastMove0SmoothScanTime = 0L;
    private static volatile boolean isEvalBarEvaluatedForMove0 = false;

    public static void checkAndTriggerMove0Watchdog(Activity activity) {
        if (activity == null || isReviewOrAnalysisActivity(activity) || isGameOver(activity)) return;
        if (!StockfishSettings.isEngineEnabled(activity)) return;
        if (activity.getWindow() == null || activity.getWindow().getDecorView() == null) return;

        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        ViewGroup decorView = (ViewGroup) decor;

        // 1. Geometry & Layout Check: Board MUST be physically measured and visible on screen
        // (Guards against early triggering during matchmaking spinner / 0x0 layout, preventing any flicker)
        OverlayManager.BoardMetrics bm = OverlayManager.getBoardMetrics(decorView);
        if (bm == null || bm.boardW <= 0 || bm.boardH <= 0) return;

        // 2. Extract position
        Object pos = null;
        if (bm.boardView != null) {
            pos = extractPositionFromBoardView(bm.boardView);
        }
        if (pos == null) {
            pos = extractPositionFromActivity(activity);
        }
        if (pos == null) {
            Object state = getStateImpl();
            if (state != null) {
                try {
                    Method getPos = state.getClass().getMethod("getPosition");
                    pos = getPos.invoke(state);
                } catch (Throwable ignored) {}
            }
        }
        if (pos == null) return;
        String fen = extractFen(pos);
        if (fen == null) return;

        boolean isMove0 = fen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
        if (!isMove0) {
            // Already progressed to Move 1+
            isMove0AnalysisScheduled = false;
            isMove0AnalysisCompleted = false;
            isEvalBarEvaluatedForMove0 = false;
            return;
        }

        // 3. Eval Bar initialization for Move 0 (Smooth - zero flicker)
        Context ctx = getContext();
        if (ctx != null && StockfishSettings.isEvalBarEnabled(ctx) && !isEvalBarEvaluatedForMove0) {
            isEvalBarEvaluatedForMove0 = true;
            WasmEngineManager.getInstance(ctx).evaluateClassifyPosition(fen, java.util.Collections.<String>emptyList());
        }

        // 4. Check if arrows are desired on Move 0
        Boolean userWhite = isUserWhite(getStateImpl());
        boolean isWhiteTurn = isWhiteTurnFromFen(fen); // true for startpos
        boolean isMyTurn = (userWhite == null) || (isWhiteTurn == userWhite.booleanValue());
        boolean showArrows = StockfishSettings.isArrowsVisible(activity);
        if (showArrows && StockfishSettings.isMySideOnly(activity) && !isMyTurn) {
            showArrows = false; // User plays Black with isMySideOnly enabled
        }

        if (!showArrows) {
            return;
        }

        // 5. ROCK-SOLID ANTI-FLICKER: If arrows are ALREADY on screen, NEVER re-trigger or reload!
        if (OverlayManager.hasArrows()) {
            isMove0AnalysisCompleted = true;
            return;
        }

        // 6. If engine is currently computing, let it complete
        if (currentJob != null && !currentJob.isDone()) {
            return;
        }

        // 7. Ensure engine is ready
        if (!engineReady) {
            ensureEngineReady();
            if (isInitializing || pendingPositionObject != null) {
                return;
            }
        }

        // 8. Cooldown debounce (600ms)
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastMove0SmoothScanTime < 600) {
            return;
        }
        lastMove0SmoothScanTime = now;

        // 9. Smooth Trigger: Exactly like changing Elo/Depth, re-runs play search without wiping Eval Bar or Widget!
        Log.i(TAG, "[MOVE 0 SMOOTH SCAN] Bàn cờ Move 0 đã sẵn sàng nhưng chưa có mũi tên. Đang kích hoạt phân tích êm dịu...");
        lastArrowSignature = null;
        lastScheduledKey = null;
        scheduleAnalysis(fen, true);
    }

    private static void stopGameOverWatchdog() {
        android.os.Handler wh = getWatchdogHandler();
        if (wh != null) wh.removeCallbacksAndMessages(null);
        gameOverWatchdogRunnable = null;
        View decor = currentDecorViewRef.get();
        if (decor != null && globalLayoutListener != null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= 16) {
                    decor.getViewTreeObserver().removeOnGlobalLayoutListener(globalLayoutListener);
                } else {
                    decor.getViewTreeObserver().removeGlobalOnLayoutListener(globalLayoutListener);
                }
            } catch (Throwable ignored) {}
        }
        globalLayoutListener = null;
        currentDecorViewRef = new WeakReference<>(null);
    }

    public static boolean checkAndHandleGameOver(Activity activity) {
        if (activity == null) return false;
        if (isGameOver(activity)) {
            if (!lastGameOverHandled) {
                lastGameOverHandled = true;
                lastLoadedMove0Fen = null;
                isMove0AnalysisScheduled = false;
                isMove0AnalysisCompleted = false;
                OverlayManager.hideArrowOverlay(true);
                ArrowInjector.clearEngineArrows(getStateImpl());
                StockfishBridge.stopSearch();
                Context c = getContext();
                if (c != null) {
                    try {
                        TorchEngine.getInstance(c).cancelPendingRequests();
                    } catch (Throwable ignored) {}
                }
                AutoFindManager.onGameOver(activity);
                Log.i(TAG, "[ACTIVE GAME OVER] Ván đấu kết thúc! Đã chủ động ẩn toàn bộ mũi tên và dừng engine.");
            }
            return true;
        } else {
            if (lastGameOverHandled) {
                // Chuyển từ Game Over sang Ván mới (Rematch / New Game)
                Log.i(TAG, "[NEW GAME DETECTED] Phát hiện chuyển sang ván mới sau Game Over! Đang khởi tạo lại Move 0...");
                lastGameOverHandled = false;
                lastLoadedMove0Fen = null;
                isMove0AnalysisScheduled = false;
                isMove0AnalysisCompleted = false;
                lastArrowSignature = null;
                lastScheduledKey = null;
                AutoFindManager.onNewGameStarted();
                unlockScreenshots(activity);
                OverlayManager.clearHookedBoardViews();
                tryLoadMove0(activity);
            }
            return false;
        }
    }

    public static boolean tryLoadMove0(Activity activity) {
        if (activity == null || isReviewOrAnalysisActivity(activity) || activity.getWindow() == null) return false;
        View decorView = activity.getWindow().getDecorView();
        if (decorView == null) return false;
        View boardView = OverlayManager.findChessBoardView(decorView);
        Object pos = null;
        if (boardView != null) {
            pos = extractPositionFromBoardView(boardView);
        }
        if (pos == null) {
            pos = extractPositionFromActivity(activity);
        }
        if (pos == null) return false;

        String fen = extractFen(pos);
        if (fen == null) return false;

        boolean isMove0 = fen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
        if (isMove0 && isMove0AnalysisScheduled && fen.equals(lastLoadedMove0Fen) && (OverlayManager.hasArrows() || isMove0AnalysisCompleted)) {
            return true; // Already loaded, scheduled and visibly rendered or analysis completed!
        }

        onBoardChanged(getStateImpl(), pos);
        return true;
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
            OverlayManager.hideMateAnnouncement();
            OverlayManager.hideEngineInfo();
            OverlayManager.hideAccuracyEloPills();
            AutoMoveManager.cancelPendingAutoMove("Engine toggled off");
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
            // Guard: If current board is at startpos, it is unequivocally a new game!
            try {
                if (activity.getWindow() != null && activity.getWindow().getDecorView() != null) {
                    View bv = OverlayManager.findChessBoardView(activity.getWindow().getDecorView());
                    if (bv != null) {
                        Object pos = extractPositionFromBoardView(bv);
                        if (pos != null) {
                            String f = extractFen(pos);
                            if (f != null && f.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")) {
                                return false;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 1. Inspect activity fields for gameOverOverlayView (m0)
            Class<?> actClass = activity.getClass();
            while (actClass != null && !actClass.getName().equals("android.app.Activity")) {
                for (Field f : actClass.getDeclaredFields()) {
                    String fn = f.getName();
                    if (fn.equals("gameOverOverlayView") || fn.equals("m0")) {
                        try {
                            f.setAccessible(true);
                            Object val = f.get(activity);
                            if (val instanceof View) {
                                View gv = (View) val;
                                if (gv.getVisibility() == View.VISIBLE && gv.getParent() != null) {
                                    return true;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                actClass = actClass.getSuperclass();
            }

            // 3. Check FragmentManager for active DialogFragments (BaseGameOverDialog, etc.)
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
                                if (frag != null && isFragmentAttachedOrShowing(frag)) return true;
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
                                        if (isFragmentAttachedOrShowing(frag)) return true;
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}

            // 4. Check decorView hierarchy for explicit GameOver overlay views
            if (activity.getWindow() != null && activity.getWindow().getDecorView() != null) {
                View decorView = activity.getWindow().getDecorView();
                if (hasGameOverView(decorView)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isFragmentAttachedOrShowing(Object frag) {
        if (frag == null) return false;
        try {
            Method isRemM = frag.getClass().getMethod("isRemoving");
            if ((Boolean) isRemM.invoke(frag)) return false;
        } catch (Throwable ignored) {}
        try {
            Method getDialogM = frag.getClass().getMethod("getDialog");
            Object dlg = getDialogM.invoke(frag);
            if (dlg != null) {
                Method isShowingM = dlg.getClass().getMethod("isShowing");
                if ((Boolean) isShowingM.invoke(dlg)) return true;
            }
        } catch (Throwable ignored) {}
        try {
            Method getViewM = frag.getClass().getMethod("getView");
            Object v = getViewM.invoke(frag);
            if (v instanceof View) {
                View fv = (View) v;
                if (fv.getVisibility() == View.VISIBLE && fv.isShown() && fv.getParent() != null) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean hasGameOverView(View view) {
        if (view == null) return false;
        if (view.getVisibility() == View.VISIBLE) {
            String className = view.getClass().getName();
            if (className.contains("GameOverOverlay") || className.contains("GameOverDialog") || className.contains("GameEndOverlay")) {
                return true;
            }
            try {
                int id = view.getId();
                if (id != View.NO_ID && view.getResources() != null) {
                    String entryName = view.getResources().getResourceEntryName(id);
                    if (entryName != null) {
                        String lower = entryName.toLowerCase(java.util.Locale.US);
                        if (lower.contains("game_over_overlay") || lower.contains("game_over_dialog") || lower.contains("gameover_overlay")) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {}
            Object tag = view.getTag();
            if (tag instanceof String) {
                String stag = ((String) tag).toLowerCase(java.util.Locale.US);
                if (stag.contains("game_over_dialog") || stag.contains("game_over_overlay")) {
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

    public static Object resolveVariantPosition(Object obj) {
        if (obj == null) return null;
        if (obj instanceof View || obj instanceof Activity || obj instanceof Context) return null;
        if (extractFen(obj) != null) return obj;
        try {
            Method m = obj.getClass().getMethod("a");
            if (m.getReturnType() != void.class) {
                Object inner = m.invoke(obj);
                if (inner != null && extractFen(inner) != null) return inner;
            }
        } catch (Throwable ignored) {}
        try {
            Method m = obj.getClass().getMethod("getPosition");
            if (m.getReturnType() != void.class) {
                Object inner = m.invoke(obj);
                if (inner != null && extractFen(inner) != null) return inner;
            }
        } catch (Throwable ignored) {}
        for (Method m : obj.getClass().getMethods()) {
            String name = m.getName();
            if (m.getParameterCount() == 0 && m.getReturnType() != void.class
                    && !name.equals("getClass") && (name.startsWith("get") || name.length() <= 2)) {
                try {
                    Object inner = m.invoke(obj);
                    if (inner != null && extractFen(inner) != null) return inner;
                } catch (Throwable ignored) {}
            }
        }
        for (Field f : obj.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object inner = f.get(obj);
                if (inner != null && extractFen(inner) != null) return inner;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static Object extractPositionFromBoardView(View boardView) {
        if (boardView == null) return null;
        // 1. Try declared fields on boardView (Fastest & most direct: field position / r / p)
        for (Field f : boardView.getClass().getDeclaredFields()) {
            String fn = f.getName().toLowerCase(java.util.Locale.US);
            if (fn.contains("position") || fn.equals("r") || fn.equals("p") || fn.equals("f0")) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(boardView);
                    Object pos = resolveVariantPosition(val);
                    if (pos != null) return pos;
                } catch (Throwable ignored) {}
            }
        }

        // 2. Try getPositionWithPremoves (public or private declared)
        try {
            Method getPos = null;
            try {
                getPos = boardView.getClass().getMethod("getPositionWithPremoves");
            } catch (NoSuchMethodException e) {
                getPos = boardView.getClass().getDeclaredMethod("getPositionWithPremoves");
                getPos.setAccessible(true);
            }
            if (getPos != null) {
                Object pos = resolveVariantPosition(getPos.invoke(boardView));
                if (pos != null) return pos;
            }
        } catch (Throwable ignored) {}

        // 3. Try getPosition (public or private declared)
        try {
            Method getDispPos = null;
            try {
                getDispPos = boardView.getClass().getMethod("getPosition");
            } catch (NoSuchMethodException e) {
                getDispPos = boardView.getClass().getDeclaredMethod("getPosition");
                getDispPos.setAccessible(true);
            }
            if (getDispPos != null) {
                Object pos = resolveVariantPosition(getDispPos.invoke(boardView));
                if (pos != null) return pos;
            }
        } catch (Throwable ignored) {}

        // 4. Try getViewModel() -> getState() -> getPosition() (guarded against UninitializedPropertyAccessException)
        try {
            Method getVm = boardView.getClass().getMethod("getViewModel");
            Object vm = getVm.invoke(boardView);
            if (vm != null) {
                Method getState = vm.getClass().getMethod("getState");
                Object state = getState.invoke(vm);
                if (state != null) {
                    stateImplRef.set(new WeakReference<>(state));
                    Object pos = resolveVariantPosition(state);
                    if (pos != null) return pos;
                }
            }
        } catch (Throwable ignored) {}

        // 5. Try any other declared fields on boardView
        for (Field f : boardView.getClass().getDeclaredFields()) {
            String fn = f.getName().toLowerCase(java.util.Locale.US);
            if (fn.contains("viewmodel") || fn.equals("x")) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(boardView);
                    Object pos = resolveVariantPosition(val);
                    if (pos != null) return pos;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    public static Object extractPositionFromViewModel(Object vm) {
        if (vm == null) return null;
        try {
            // 1. Try vm.getState() -> state.getPosition() / state.a()
            Method getState = null;
            try {
                getState = vm.getClass().getMethod("getState");
            } catch (NoSuchMethodException ignored) {}
            if (getState != null) {
                Object state = getState.invoke(vm);
                if (state != null) {
                    stateImplRef.set(new WeakReference<>(state));
                    Object pos = resolveVariantPosition(state);
                    if (pos != null) return pos;
                }
            }
            // 2. Try vm.getPosition() directly
            try {
                Method getPos = vm.getClass().getMethod("getPosition");
                Object pos = getPos.invoke(vm);
                if (pos != null) {
                    Object resolved = resolveVariantPosition(pos);
                    if (resolved != null) return resolved;
                }
            } catch (Throwable ignored) {}
            // 3. Fallback to resolveVariantPosition on vm itself
            Object res = resolveVariantPosition(vm);
            if (res != null) return res;
        } catch (Throwable ignored) {}
        return null;
    }

    public static Object extractPositionFromActivity(Activity activity) {
        if (activity == null) return null;
        try {
            // Priority A: Try activity.X6() (RealGameActivity ViewModel getter)
            Object vm = null;
            try {
                Method x6 = activity.getClass().getMethod("X6");
                vm = x6.invoke(activity);
            } catch (Throwable ignored) {}

            // Priority B: Try field B (Lazy<RealGameViewModel>) or viewModel
            if (vm == null) {
                for (Field f : activity.getClass().getDeclaredFields()) {
                    String fn = f.getName();
                    if (fn.equals("viewModel") || fn.equals("B")) {
                        try {
                            f.setAccessible(true);
                            Object lazyVal = f.get(activity);
                            if (lazyVal != null) {
                                Method getVal = lazyVal.getClass().getMethod("getValue");
                                vm = getVal.invoke(lazyVal);
                                if (vm != null) break;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

            if (vm != null) {
                // In RealGameViewModel: query cbDelegate / q() / S()
                try {
                    Method qMethod = vm.getClass().getMethod("q");
                    Object prov = qMethod.invoke(vm);
                    if (prov != null) {
                        Method getMethod = prov.getClass().getMethod("get");
                        Object cbVm = getMethod.invoke(prov);
                        if (cbVm != null) {
                            Object pos = extractPositionFromViewModel(cbVm);
                            if (pos != null) return pos;
                        }
                    }
                } catch (Throwable ignored) {}

                for (Field vf : vm.getClass().getDeclaredFields()) {
                    String vfn = vf.getName();
                    if (vfn.equals("cbDelegate") || vfn.equals("K") || vfn.equals("b4Var")) {
                        try {
                            vf.setAccessible(true);
                            Object delegate = vf.get(vm);
                            if (delegate != null) {
                                // delegate can have boardViewModel field (j) or q()
                                try {
                                    Method dm = delegate.getClass().getMethod("q");
                                    Object prov = dm.invoke(delegate);
                                    if (prov != null) {
                                        Method getMethod = prov.getClass().getMethod("get");
                                        Object cbVm = getMethod.invoke(prov);
                                        if (cbVm != null) {
                                            Object pos = extractPositionFromViewModel(cbVm);
                                            if (pos != null) return pos;
                                        }
                                    }
                                } catch (Throwable ignored) {}
                                for (Field df : delegate.getClass().getDeclaredFields()) {
                                    if (df.getName().equals("boardViewModel") || df.getName().equals("j")) {
                                        try {
                                            df.setAccessible(true);
                                            Object cbVm = df.get(delegate);
                                            if (cbVm != null) {
                                                Object pos = extractPositionFromViewModel(cbVm);
                                                if (pos != null) return pos;
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                }
                                Object pos = extractPositionFromViewModel(delegate);
                                if (pos != null) return pos;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

            // Priority C: Try Activity fields: chessBoardView (c0), game (T)
            for (Field f : activity.getClass().getDeclaredFields()) {
                String fn = f.getName();
                if (fn.equals("chessBoardView") || fn.equals("c0")) {
                    try {
                        f.setAccessible(true);
                        Object bv = f.get(activity);
                        if (bv instanceof View) {
                            Object pos = extractPositionFromBoardView((View) bv);
                            if (pos != null) return pos;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            // Priority D: Fallback to RealGameUiSetup startingFen via activity.N6() or Intent
            try {
                Method n6 = activity.getClass().getMethod("N6");
                Object uiSetup = n6.invoke(activity);
                if (uiSetup != null) {
                    try {
                        Method getFen = uiSetup.getClass().getMethod("getStartingFen");
                        Object fenObj = getFen.invoke(uiSetup);
                        if (fenObj instanceof String) {
                            String sFen = (String) fenObj;
                            if (sFen.length() > 0) {
                                String sanitized = sanitizeFen(sFen);
                                if (sanitized != null) return sanitized;
                            } else {
                                return "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return null;
    }

    public static void onInitialPositionLoaded(Object obj) {
        if (obj == null) return;
        silenceIllegalMoveSound();
        try {
            Object pos = null;
            if (obj instanceof View) {
                View boardView = (View) obj;
                pos = extractPositionFromBoardView(boardView);
            } else {
                stateImplRef.set(new WeakReference<>(obj));
                pos = resolveVariantPosition(obj);
            }
            if (pos == null) {
                Activity act = getCurrentActivity();
                if (act != null) {
                    pos = extractPositionFromActivity(act);
                }
            }
            if (pos != null) {
                String fen = extractFen(pos);
                if (fen != null) {
                    boolean isMove0 = fen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
                    if (isMove0 && isMove0AnalysisScheduled && fen.equals(lastLoadedMove0Fen) && (OverlayManager.hasArrows() || isMove0AnalysisCompleted)) {
                        Log.d(TAG, "[MOVE 0 SKIP] Thế cờ Move 0 đã được nạp và đã có mũi tên hoặc phân tích hoàn tất, bỏ qua gọi lặp lại.");
                        return;
                    }
                }
                onBoardChanged(getStateImpl(), pos);
                return;
            }
        } catch (Throwable ignored) {}
        triggerAnalysisForCurrentState();
    }

    public static void triggerAnalysisForCurrentState() {
        Activity act = getCurrentActivity();
        if (isGameOver(act)) {
            OverlayManager.hideArrowOverlay(true);
            StockfishBridge.stopSearch();
            AutoMoveManager.cancelPendingAutoMove("Game over");
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
        if (positionObject == null && act != null) {
            positionObject = extractPositionFromActivity(act);
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
        if (positionObject == null && act != null) {
            positionObject = extractPositionFromActivity(act);
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
            }
        }
        if (ctx == null) {
            Activity act = getCurrentActivity();
            if (act != null) {
                ctx = act.getApplicationContext();
                if (ctx != null) appContext = ctx;
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
            if (act != null) {
                // Fallback: RealGameActivity X6() -> q().get() -> getState()
                Object vm = null;
                try {
                    Method x6 = act.getClass().getMethod("X6");
                    vm = x6.invoke(act);
                } catch (Throwable ignored) {}
                if (vm != null) {
                    try {
                        Method qMethod = vm.getClass().getMethod("q");
                        Object prov = qMethod.invoke(vm);
                        if (prov != null) {
                            Method getMethod = prov.getClass().getMethod("get");
                            Object cbVm = getMethod.invoke(prov);
                            if (cbVm != null) {
                                Method getState = cbVm.getClass().getMethod("getState");
                                Object state = getState.invoke(cbVm);
                                if (state != null) {
                                    stateImplRef.set(new WeakReference<>(state));
                                    return state;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
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
        // Priority 1: Inspect userSide directly from RealGameActivity / RealGameViewModel / RealGameUiSetup
        // (CRITICAL: In live match Move 0, flipBoard defaults to false before layout! userSide from Intent/ViewModel is the source of truth)
        try {
            Activity act = getCurrentActivity();
            if (act != null) {
                // Check RealGameActivity.N6() -> RealGameUiSetup.getUserSide()
                try {
                    Method n6 = act.getClass().getMethod("N6");
                    Object uiSetup = n6.invoke(act);
                    if (uiSetup != null) {
                        Method getSide = uiSetup.getClass().getMethod("getUserSide");
                        Object side = getSide.invoke(uiSetup);
                        Boolean sw = sideToWhite(side);
                        if (sw != null) {
                            cachedUserIsWhite = sw;
                            return sw;
                        }
                    }
                } catch (Throwable ignored) {}

                // Check RealGameActivity.X6() -> RealGameViewModel.userSide / gameSetup.getUserSide()
                Object vm = null;
                try {
                    Method x6 = act.getClass().getMethod("X6");
                    vm = x6.invoke(act);
                } catch (Throwable ignored) {}
                if (vm != null) {
                    for (Field vf : vm.getClass().getDeclaredFields()) {
                        String vfn = vf.getName().toLowerCase(java.util.Locale.US);
                        if (vfn.contains("userside") || vfn.contains("side")) {
                            try {
                                vf.setAccessible(true);
                                Object sideVal = vf.get(vm);
                                Boolean sw = sideToWhite(sideVal);
                                if (sw != null) {
                                    cachedUserIsWhite = sw;
                                    return sw;
                                }
                            } catch (Throwable ignored) {}
                        }
                        if (vfn.contains("gamesetup")) {
                            try {
                                vf.setAccessible(true);
                                Object setupVal = vf.get(vm);
                                if (setupVal != null) {
                                    Method gs = setupVal.getClass().getMethod("getUserSide");
                                    Object side = gs.invoke(setupVal);
                                    Boolean sw = sideToWhite(side);
                                    if (sw != null) {
                                        cachedUserIsWhite = sw;
                                        return sw;
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Priority 2: Check live board orientation directly from active ChessBoardView
        try {
            Activity act = getCurrentActivity();
            if (act != null && act.getWindow() != null && act.getWindow().getDecorView() != null) {
                View bv = OverlayManager.findChessBoardView(act.getWindow().getDecorView());
                if (bv != null) {
                    for (Method m : bv.getClass().getMethods()) {
                        String n = m.getName();
                        if ((n.equals("getFlipBoard") || n.equals("isFlipped") || n.equals("getFlipped"))
                                && m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                            boolean flipped = (boolean) m.invoke(bv);
                            boolean userWhite = !flipped;
                            cachedUserIsWhite = userWhite;
                            return userWhite;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Priority 3: Check live board orientation via stateImplObject getFlipBoard()
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

        // Priority 4: Check live board orientation via OverlayManager (which checks ChessBoardView)
        try {
            boolean flipped = OverlayManager.isBoardFlipped(stateImplObject);
            boolean userWhite = !flipped;
            cachedUserIsWhite = userWhite;
            return userWhite;
        } catch (Throwable ignored) {}

        // Priority 5: Fall back to previously cached game orientation
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
        if (position instanceof String) {
            String s = sanitizeFen((String) position);
            if (s != null) return s;
        }
        String full = fullFen(position);
        if (full != null) return full;
        return assembleFen(position);
    }

    static String fullFen(Object position) {
        try {
            // Priority 1: Direct duck typing for method "o" or "getFen" on position's class
            try {
                Method m = position.getClass().getMethod("o");
                if (m.getReturnType() == String.class) {
                    Object r = m.invoke(position);
                    if (r instanceof String) {
                        String sanitized = sanitizeFen((String) r);
                        if (sanitized != null) return sanitized;
                    }
                }
            } catch (Throwable ignored) {}
            try {
                Method m = position.getClass().getMethod("getFen");
                if (m.getReturnType() == String.class) {
                    Object r = m.invoke(position);
                    if (r instanceof String) {
                        String sanitized = sanitizeFen((String) r);
                        if (sanitized != null) return sanitized;
                    }
                }
            } catch (Throwable ignored) {}

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
            try {
                Log.e(TAG, "extractFen failed: " + t.getMessage(), t);
            } catch (Throwable ignored) {}
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
        // Do not force DIAMOND status so diamond badge is never displayed next to user's name
        return defaultValue;
    }

    public static Object getDiamondStatus() {
        return null;
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
