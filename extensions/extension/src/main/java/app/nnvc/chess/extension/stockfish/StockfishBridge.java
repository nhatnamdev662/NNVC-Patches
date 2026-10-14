package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.util.Log;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * StockfishBridge – singleton façade over StockfishProcess.
 *
 * Keeps a small LRU cache of finished searches keyed by position, so the same position
 * is never searched twice at the same (or lower) depth – e.g. when the board callback fires
 * several times for one move, when stepping back and forth through a game, or when browsing
 * a game that was just reviewed. This removes a lot of redundant CPU work (and heat).
 */
@SuppressWarnings("unused")
public class StockfishBridge {

    private static final String TAG = "NNVC";

    private static final StockfishProcess engine = new StockfishProcess();
    private static volatile boolean initialised = false;

    private static final int CACHE_SIZE = 512;

    private static final class CacheEntry {
        final StockfishProcess.AnalysisResult result;
        final int depth;
        final int multiPV;
        /** 0 = full strength, otherwise the UCI_Elo the result was searched with. */
        final int limited;
        final String engineChoice;
        final String komodoStyle;

        CacheEntry(StockfishProcess.AnalysisResult result, int depth, int multiPV, int limited, String engineChoice, String komodoStyle) {
            this.result = result;
            this.depth = depth;
            this.multiPV = multiPV;
            this.limited = limited;
            this.engineChoice = engineChoice != null ? engineChoice : "";
            this.komodoStyle = komodoStyle != null ? komodoStyle : "";
        }
    }

    private static final Map<String, CacheEntry> cache =
            new LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > CACHE_SIZE;
                }
            };

    public static synchronized boolean init(Context context) {
        if (initialised && engine.isReady()) return true;
        initialised = engine.start(context);
        if (!initialised) Log.e(TAG, "Engine failed to start.");
        return initialised;
    }

    private static Context getApplicationContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method m = activityThreadClass.getMethod("currentApplication");
            return (Context) m.invoke(null);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to get Application Context: " + t.getMessage());
        }
        return null;
    }

    /** Position key: placement, side to move, castling and en passant (move counters ignored). */
    public static String positionKey(String fen) {
        if (fen == null) return null;
        String[] p = fen.trim().split("\\s+");
        if (p.length < 2) return fen;
        StringBuilder sb = new StringBuilder(fen.length());
        sb.append(p[0]).append(' ').append(p[1]);
        sb.append(' ').append(p.length > 2 ? p[2] : "-");
        sb.append(' ').append(p.length > 3 ? p[3] : "-");
        return sb.toString();
    }

    public static List<String> bestMoves(String fen, int depth, int multiPV) {
        return analyze(fen, depth, multiPV).moves;
    }

    /** Strength key for the cache: 0 = full strength, otherwise the actual Elo rating. */
    private static int strengthKey(Context ctx) {
        if (!StockfishSettings.isLimitStrength(ctx)) return 0;
        return StockfishSettings.getElo(ctx);
    }

    /**
     * Returns a cached result for the position if one exists that exactly matches the depth,
     * engineChoice, komodoStyle, and limited strength.
     */
    public static StockfishProcess.AnalysisResult getCached(String fen, int depth, int multiPV, int limited, String engineChoice, String komodoStyle) {
        String key = positionKey(fen);
        if (key == null) return null;
        CacheEntry e;
        synchronized (cache) {
            e = cache.get(key);
        }
        if (e == null || e.depth != depth || e.multiPV < multiPV || e.limited != limited
                || !e.engineChoice.equals(engineChoice != null ? engineChoice : "")
                || !e.komodoStyle.equals(komodoStyle != null ? komodoStyle : "")) {
            return null;
        }
        return trimLines(e.result, multiPV);
    }

    private static StockfishProcess.AnalysisResult trimLines(StockfishProcess.AnalysisResult r, int multiPV) {
        if (r.moves.size() <= multiPV) return r;
        java.util.List<String> moves = new java.util.ArrayList<>(r.moves.subList(0, multiPV));
        float[] lines = java.util.Arrays.copyOf(r.lineScores, Math.min(multiPV, r.lineScores.length));
        boolean[] lineMates = r.lineMates != null ? java.util.Arrays.copyOf(r.lineMates, Math.min(multiPV, r.lineMates.length)) : new boolean[lines.length];
        int[] lineMateIn = r.lineMateIn != null ? java.util.Arrays.copyOf(r.lineMateIn, Math.min(multiPV, r.lineMateIn.length)) : new int[lines.length];
        return new StockfishProcess.AnalysisResult(moves, r.score, r.hasMate, r.mateIn,
                r.wdlWin, r.wdlDraw, r.wdlLoss, r.ponder, r.pv, lines, lineMates, lineMateIn, r.depth, r.terminal);
    }

    private static void putCache(String fen, StockfishProcess.AnalysisResult r, int depth, int multiPV, int limited, String engineChoice, String komodoStyle) {
        if (r == null || !r.isValid()) return;
        // Never cache a search that was interrupted before reaching the requested depth.
        if (!r.terminal && !r.hasMate && r.depth < depth) return;
        String key = positionKey(fen);
        if (key == null) return;
        synchronized (cache) {
            cache.put(key, new CacheEntry(r, depth, multiPV, limited, engineChoice, komodoStyle));
        }
    }

    public static void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }

    private static boolean ensureRunning(Context ctx) {
        if (!initialised || !engine.isReady()) {
            initialised = engine.start(ctx);
        }
        return initialised;
    }

    /** Live analysis of a single FEN (honours the Elo limit setting). */
    public static StockfishProcess.AnalysisResult analyze(String fen, int depth, int multiPV) {
        return analyze(fen, depth, multiPV, null);
    }

    /**
     * Live analysis that also streams intermediate results (from depth 10 on) to
     * {@code progress}, so the UI updates while a deep search is still running.
     */
    public static synchronized StockfishProcess.AnalysisResult analyze(String fen, int depth, int multiPV,
                                                                       StockfishProcess.ProgressListener progress) {
        Context ctx = getApplicationContext();
        if (ctx == null) return StockfishProcess.AnalysisResult.empty();

        int limited = strengthKey(ctx);
        String engineChoice = StockfishSettings.getEngineChoice(ctx);
        String komodoStyle = StockfishSettings.getKomodoStyle(ctx);

        StockfishProcess.AnalysisResult cached = getCached(fen, depth, multiPV, limited, engineChoice, komodoStyle);
        if (cached != null) return cached;

        if (!ensureRunning(ctx)) return StockfishProcess.AnalysisResult.empty();
        StockfishProcess.AnalysisResult r;
        liveSearchRunning = true;
        List<String> uciMoves = MoveClassifier.getCurrentPositionMoves();
        try {
            r = engine.analyze(ctx, fen, uciMoves, depth, multiPV, 0, true, progress);
            if (!r.isValid() && !engine.isReady()) {
                // Engine crashed on this position; restart once and retry.
                if (ensureRunning(ctx)) r = engine.analyze(ctx, fen, uciMoves, depth, multiPV, 0, true, progress);
            }
        } finally {
            liveSearchRunning = false;
        }
        putCache(fen, r, depth, multiPV, limited, engineChoice, komodoStyle);
        return r;
    }


    /** Clears the engine hash before reviewing a new game. */
    public static synchronized void newGame() {
        Context ctx = getApplicationContext();
        if (ctx != null && ensureRunning(ctx)) engine.newGame();
    }

    public static String bestMove(String fen, int depth) {
        List<String> moves = bestMoves(fen, depth, 1);
        return moves.isEmpty() ? null : moves.get(0);
    }

    /** True while a live-analysis search (not a Game Review search) owns the engine. */
    private static volatile boolean liveSearchRunning = false;

    /**
     * Interrupt the running live-analysis search. Deliberately not synchronized. Does nothing
     * while the Game Review is searching: browsing the review board starts live analysis,
     * and its "stop" used to cut the review's own search short (shallow, wrong ratings).
     */
    public static void stopSearch() {
        if (initialised && liveSearchRunning) engine.stopSearch();
    }

    public static synchronized void quit() {
        if (initialised) {
            engine.stop();
            initialised = false;
        }
    }
}
