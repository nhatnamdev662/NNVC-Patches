package app.prathxm.chess.extension.stockfish;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * StockfishProcess – 100% WebAssembly Chess Engine Manager.
 *
 * Runs WebAssembly Engines (Komodo Dragon 3.3 WASM as default & Stockfish 18 WASM)
 * in headless WebView Web Workers via WasmEngineManager with in-memory ArrayBuffer
 * and transferable worker postMessage architecture.
 *
 * 100% Parity with NNVC Extension. Zero Native Subprocess Fallback.
 */
@SuppressWarnings("unused")
public class StockfishProcess {

    private static final String TAG = "StockfishProcess";

    public static final float MATE_SCORE = 99.0f;
    private static final int READY_TIMEOUT_MS = 10_000;
    private static final int BESTMOVE_GRACE_MS = 10_000;
    private static final int DEFAULT_SEARCH_TIMEOUT_MS = 60_000;
    private static final int PROGRESS_MIN_DEPTH = 8;
    private static final long PROGRESS_INTERVAL_MS = 250;

    // Active Engine Mode: strictly "wasm"
    private volatile String activeEngineMode = "wasm";

    // WASM Engine Fields
    private WasmEngineManager wasmManager;

    // Option Caches
    private int curThreads = -1;
    private int curHash = -1;
    private int curMultiPV = -1;
    private Boolean curLimitStrength = null;
    private int curElo = -1;
    private int curSkillLevel = -1;
    private int curSkillErr = -1;
    private int curSkillProb = -1;
    private String curEngineChoice = null;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public synchronized boolean start(Context context) {
        if (context == null) return false;
        NnvcLogger.init(context);

        String engineChoice = StockfishSettings.getEngineChoice(context);
        curEngineChoice = engineChoice;
        NnvcLogger.i(TAG, "Starting WebAssembly engine with choice: " + engineChoice);

        wasmManager = WasmEngineManager.getInstance(context);

        // Pre-extract files from APK & assets if not already extracted
        wasmManager.extractBundledWasmFiles();

        if (wasmManager.hasWasmFiles(engineChoice)) {
            NnvcLogger.i(TAG, "WASM files found for " + engineChoice + ". Initializing WASM worker...");
            EngineStatusHUD.show(context, "📦 Đang nạp " + engineChoice.toUpperCase() + " WASM...", false, 3000);
            wasmManager.switchEngine(engineChoice);

            if (wasmManager.waitForPlayWorkerReady(8000)) {
                drainReady();
                sendUciCommand("uci");
                waitForLine("uciok", 5000);
                sendUciCommand("setoption name Threads value 1");
                sendUciCommand("setoption name Hash value 16");
                sendUciCommand("setoption name UCI_ShowWDL value true");
                sendUciCommand("isready");
                waitForLine("readyok", 5000);

                activeEngineMode = "wasm";
                NnvcLogger.i(TAG, "✓ WASM engine " + engineChoice + " ready!");
                EngineStatusHUD.showReady(context, engineChoice.toUpperCase() + " WASM");
                return true;
            } else {
                NnvcLogger.w(TAG, "WASM engine worker did not respond within timeout.");
                EngineStatusHUD.show(context, "⚠️ Đang tải/nạp " + engineChoice.toUpperCase() + " WASM...", false, 4000);
            }
        } else {
            NnvcLogger.w(TAG, "WASM files for " + engineChoice + " not found; triggering background install/download.");
            EngineStatusHUD.show(context, "📥 Đang tải engine " + engineChoice.toUpperCase() + " WASM...", false, 0);
            wasmManager.ensureEngineFilesAvailable(engineChoice, () -> {
                NnvcLogger.i(TAG, "Engine files downloaded, starting worker...");
                start(context);
            });
        }

        return wasmManager.isReady();
    }

    public boolean isReady() {
        return wasmManager != null && wasmManager.isReady();
    }

    public String getActiveEngineMode() {
        return activeEngineMode;
    }

    // ── Results ───────────────────────────────────────────────────────────────

    public static class AnalysisResult {
        public final List<String> moves;
        public final float score;
        public final boolean hasMate;
        public final int mateIn;
        public final int wdlWin;
        public final int wdlDraw;
        public final int wdlLoss;
        public final String ponder;
        public final List<String> pv;
        public final float[] lineScores;
        public final int depth;
        public final boolean terminal;

        public AnalysisResult(List<String> moves, float score, boolean hasMate, int mateIn,
                              int wdlWin, int wdlDraw, int wdlLoss, String ponder) {
            this(moves, score, hasMate, mateIn, wdlWin, wdlDraw, wdlLoss, ponder,
                    moves != null && !moves.isEmpty() ? Collections.singletonList(moves.get(0)) : new ArrayList<>(),
                    new float[]{score}, 0, false);
        }

        public AnalysisResult(List<String> moves, float score, boolean hasMate, int mateIn,
                              int wdlWin, int wdlDraw, int wdlLoss, String ponder,
                              List<String> pv, float[] lineScores, int depth, boolean terminal) {
            this.moves = moves != null ? moves : new ArrayList<>();
            this.score = score;
            this.hasMate = hasMate;
            this.mateIn = mateIn;
            this.wdlWin = wdlWin;
            this.wdlDraw = wdlDraw;
            this.wdlLoss = wdlLoss;
            this.ponder = ponder;
            this.pv = pv != null ? pv : new ArrayList<>();
            this.lineScores = lineScores != null ? lineScores : new float[0];
            this.depth = depth;
            this.terminal = terminal;
        }

        public static AnalysisResult empty() {
            return new AnalysisResult(new ArrayList<>(), 0f, false, 0, 0, 0, 0, null,
                    new ArrayList<>(), new float[0], 0, false);
        }

        public boolean isValid() {
            return !moves.isEmpty() || terminal;
        }
    }

    public interface ProgressListener {
        void onProgress(AnalysisResult partial);
    }

    // ── Analysis API ──────────────────────────────────────────────────────────

    public List<String> bestMoves(Context context, String fen, int depth, int multiPV) {
        return analyze(context, fen, depth, multiPV).moves;
    }

    public AnalysisResult analyze(Context context, String fen, int depth, int multiPV) {
        return analyze(context, fen, null, depth, multiPV, 0, true);
    }

    public AnalysisResult analyze(Context context, String fen, List<String> uciMoves, int depth,
                                  int multiPV, int movetimeMs, boolean honorLimit) {
        return analyze(context, fen, uciMoves, depth, multiPV, movetimeMs, honorLimit, null);
    }

    public AnalysisResult analyze(Context context, String fen, List<String> uciMoves, int depth,
                                  int multiPV, int movetimeMs, boolean honorLimit,
                                  ProgressListener progress) {
        if (!isReady() && !start(context)) {
            NnvcLogger.w(TAG, "analyze called but WASM engine is not ready and failed to start.");
            return AnalysisResult.empty();
        }
        if (fen == null) return AnalysisResult.empty();

        final boolean whiteToMove = isWhiteToMove(fen, uciMoves);
        final int elo = StockfishSettings.getElo(context);

        boolean isAutoDepth = StockfishSettings.isAutoDepthEnabled(context);
        int effectiveDepth;
        if (isAutoDepth && honorLimit) {
            effectiveDepth = StockfishSettings.autoDepthForElo(elo);
        } else {
            effectiveDepth = Math.max(1, depth > 0 ? depth : StockfishSettings.getDepth(context));
        }

        try {
            drainReady();
            applyPlayEngineOptions(context, elo, multiPV, honorLimit);

            StringBuilder pos = new StringBuilder(fen.length() + 8 + (uciMoves != null ? uciMoves.size() * 6 : 0));
            pos.append("position fen ").append(fen);
            if (uciMoves != null && !uciMoves.isEmpty()) {
                pos.append(" moves");
                for (String m : uciMoves) pos.append(' ').append(m);
            }
            sendUciCommand(pos.toString());

            String goCmd;
            if (isAutoDepth && honorLimit) {
                int softMs = movetimeMs > 0 ? movetimeMs : Math.max(700, Math.min(2400, effectiveDepth * 200));
                goCmd = "go depth " + effectiveDepth + " movetime " + softMs;
            } else {
                goCmd = movetimeMs > 0 ? ("go depth " + effectiveDepth + " movetime " + movetimeMs) : ("go depth " + effectiveDepth);
            }
            sendUciCommand(goCmd);
            NnvcLogger.d(TAG, "Search started [wasm]: " + goCmd + " (Elo=" + elo + ")");

            return readSearchOutput(Math.max(3, multiPV), whiteToMove,
                    movetimeMs > 0 ? movetimeMs + BESTMOVE_GRACE_MS : DEFAULT_SEARCH_TIMEOUT_MS,
                    effectiveDepth, progress);
        } catch (Throwable e) {
            NnvcLogger.e(TAG, "Search error in mode wasm: " + e.getMessage(), e);
            return AnalysisResult.empty();
        }
    }

    public void newGame() {
        if (!isReady()) return;
        try {
            sendUciCommand("ucinewgame");
            sendUciCommand("isready");
            waitForLine("readyok", READY_TIMEOUT_MS);
        } catch (Throwable ignored) {}
    }

    public void stopSearch() {
        sendUciCommand("stop");
    }

    public void stop() {
        if (wasmManager != null) {
            wasmManager.sendPlayCommand("quit");
        }
    }

    // ── Output Parsing ────────────────────────────────────────────────────────

    private AnalysisResult readSearchOutput(int multiPV, boolean whiteToMove, long timeoutMs,
                                            int targetDepth, ProgressListener progress) throws Exception {
        String[] firstMoves = new String[multiPV];
        float[] scores = new float[multiPV];
        boolean[] haveExact = new boolean[multiPV];
        boolean[] haveAny = new boolean[multiPV];
        List<String> bestPv = null;

        boolean hasMate = false;
        int mateIn = 0;
        int wdlW = 0, wdlD = 0, wdlL = 0;
        int reachedDepth = 0;
        boolean terminal = false;
        String bestmove = null;
        String ponder = null;

        long deadline = System.currentTimeMillis() + timeoutMs;
        boolean stopSent = false;
        int reportedDepth = 0;
        long lastReport = 0;

        String line;
        while (true) {
            if (!stopSent && System.currentTimeMillis() > deadline) {
                sendUciCommand("stop");
                stopSent = true;
                deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
            } else if (stopSent && System.currentTimeMillis() > deadline) {
                NnvcLogger.e(TAG, "Engine unresponsive after stop; restarting search.");
                stopSearch();
                break;
            }

            line = readLineFromActiveEngine();
            if (line == null) {
                NnvcLogger.w(TAG, "Engine output stream closed or timed out");
                break;
            }

            if (line.startsWith("bestmove")) {
                String[] parts = line.split("\\s+");
                if (parts.length > 1 && !"(none)".equals(parts[1])) bestmove = parts[1];
                if (parts.length > 3 && "ponder".equals(parts[2])) ponder = parts[3];
                if (bestmove == null) terminal = true;
                NnvcLogger.d(TAG, "BestMove: " + bestmove + ", eval=" + scores[0] + ", depth=" + reachedDepth);
                break;
            }

            if (!line.startsWith("info ")) continue;
            if (line.indexOf(" score ") < 0) continue;

            String[] t = line.split("\\s+");
            int mpv = 1;
            int depth = 0;
            boolean bound = false;
            boolean isMate = false;
            int scoreVal = 0;
            boolean haveScore = false;
            int w = -1, d = -1, l = -1;
            int pvStart = -1;

            for (int i = 1; i < t.length; i++) {
                String k = t[i];
                switch (k) {
                    case "depth":
                        if (i + 1 < t.length) depth = parseIntSafe(t[++i], 0);
                        break;
                    case "multipv":
                        if (i + 1 < t.length) mpv = parseIntSafe(t[++i], 1);
                        break;
                    case "score":
                        if (i + 2 < t.length) {
                            isMate = "mate".equals(t[i + 1]);
                            scoreVal = parseIntSafe(t[i + 2], 0);
                            haveScore = true;
                            i += 2;
                        }
                        break;
                    case "lowerbound":
                    case "upperbound":
                        bound = true;
                        break;
                    case "wdl":
                        if (i + 3 < t.length) {
                            w = parseIntSafe(t[i + 1], -1);
                            d = parseIntSafe(t[i + 2], -1);
                            l = parseIntSafe(t[i + 3], -1);
                            i += 3;
                        }
                        break;
                    case "pv":
                        pvStart = i + 1;
                        i = t.length;
                        break;
                }
            }

            if (!haveScore || mpv < 1 || mpv > multiPV) continue;
            int idx = mpv - 1;

            if (pvStart > 0 && pvStart < t.length) {
                firstMoves[idx] = t[pvStart];
                if (mpv == 1) {
                    List<String> newPv = new ArrayList<>(t.length - pvStart);
                    for (int j = pvStart; j < t.length; j++) newPv.add(t[j]);
                    bestPv = newPv;
                }
            }

            float currentScore;
            if (isMate) {
                int curMateIn = whiteToMove ? scoreVal : -scoreVal;
                currentScore = curMateIn > 0 ? (MATE_SCORE - curMateIn) : (-MATE_SCORE - curMateIn);
                if (mpv == 1) {
                    hasMate = true;
                    mateIn = curMateIn;
                }
            } else {
                float pawns = scoreVal / 100.0f;
                currentScore = whiteToMove ? pawns : -pawns;
                if (mpv == 1) hasMate = false;
            }

            if (!bound) {
                scores[idx] = currentScore;
                haveExact[idx] = true;
                haveAny[idx] = true;
            } else if (!haveExact[idx]) {
                scores[idx] = currentScore;
                haveAny[idx] = true;
            }

            if (w >= 0 && mpv == 1) {
                wdlW = whiteToMove ? w : l;
                wdlD = d;
                wdlL = whiteToMove ? l : w;
            }

            if (depth > reachedDepth) reachedDepth = depth;

            if (progress != null && depth >= PROGRESS_MIN_DEPTH && mpv == 1 && firstMoves[0] != null) {
                long now = System.currentTimeMillis();
                float whiteScore = scores[0];
                if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                    lastReport = now;
                    reportedDepth = depth;
                    try {
                        progress.onProgress(new AnalysisResult(
                                Collections.singletonList(firstMoves[0]),
                                whiteScore, hasMate,
                                mateIn, wdlW, wdlD, wdlL,
                                bestPv != null && bestPv.size() > 1 ? bestPv.get(1) : null,
                                bestPv, new float[]{whiteScore}, depth, false));
                    } catch (Throwable ignored) {}
                }
            }
        }

        return buildResult(firstMoves, scores, haveAny, multiPV, hasMate, mateIn,
                wdlW, wdlD, wdlL, ponder, bestPv, reachedDepth, terminal, bestmove);
    }

    private AnalysisResult buildResult(String[] firstMovesIn, float[] scoresIn,
                                       boolean[] haveAny, int multiPV, boolean hasMate, int mateIn,
                                       int wdlW, int wdlD, int wdlL, String ponder,
                                       List<String> bestPv, int reachedDepth,
                                       boolean terminal, String bestmove) {
        String[] firstMoves = firstMovesIn.clone();
        float[] scores = scoresIn.clone();

        List<String> moves = new ArrayList<>(multiPV);
        List<Float> lineScoreList = new ArrayList<>(multiPV);
        if (bestmove != null && firstMoves[0] == null) {
            firstMoves[0] = bestmove;
            haveAny[0] = true;
        }
        for (int i = 0; i < multiPV; i++) {
            if (firstMoves[i] == null) continue;
            if (moves.contains(firstMoves[i])) continue;
            moves.add(firstMoves[i]);
            lineScoreList.add(haveAny[i] ? scores[i] : scores[0]);
        }
        float[] lineScores = new float[lineScoreList.size()];
        for (int i = 0; i < lineScores.length; i++) lineScores[i] = lineScoreList.get(i);

        boolean isTerminal = terminal && moves.isEmpty();
        if (isTerminal && !hasMate) {
            scores[0] = 0f;
        }

        return new AnalysisResult(moves, scores[0], hasMate, mateIn, wdlW, wdlD, wdlL, ponder,
                bestPv != null ? bestPv : new ArrayList<>(), lineScores, reachedDepth, isTerminal);
    }

    // ── Communication Helpers ──────────────────────────────────────────────────

    private void sendUciCommand(String cmd) {
        if (wasmManager != null) {
            wasmManager.sendPlayCommand(cmd);
        }
    }

    private String readLineFromActiveEngine() throws IOException {
        return wasmManager != null ? wasmManager.readPlayLine(500) : null;
    }

    private boolean waitForLine(String token, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String line = wasmManager != null ? wasmManager.readPlayLine(500) : null;
            if (line != null && line.startsWith(token)) return true;
        }
        return false;
    }

    private void drainReady() {
        if (wasmManager != null) {
            wasmManager.clearPlayOutputQueue();
        }
    }

    // ── Options ───────────────────────────────────────────────────────────────

    private void resetOptionCache() {
        curThreads = -1;
        curHash = -1;
        curMultiPV = -1;
        curLimitStrength = null;
        curElo = -1;
        curSkillLevel = -1;
        curSkillErr = -1;
        curSkillProb = -1;
    }

    private void applyPlayEngineOptions(Context context, int elo, int multiPV, boolean honorLimit) {
        int neededMpv = Math.max(3, multiPV);
        int finalMpv = Math.max(1, Math.min(8, neededMpv));
        if (finalMpv != curMultiPV) {
            sendUciCommand("setoption name MultiPV value " + finalMpv);
            curMultiPV = finalMpv;
        }

        if (curHash != 16) {
            sendUciCommand("setoption name Hash value 16");
            curHash = 16;
        }
        if (curThreads != 1) {
            sendUciCommand("setoption name Threads value 1");
            curThreads = 1;
        }

        sendUciCommand("setoption name Ponder value false");

        if (elo >= 1320) {
            if (curLimitStrength == null || !curLimitStrength) {
                sendUciCommand("setoption name UCI_LimitStrength value true");
                curLimitStrength = true;
            }
            if (curElo != elo) {
                sendUciCommand("setoption name UCI_Elo value " + elo);
                curElo = elo;
            }
        } else {
            if (curLimitStrength == null || curLimitStrength) {
                sendUciCommand("setoption name UCI_LimitStrength value false");
                curLimitStrength = false;
            }
            int skillFromElo = Math.max(0, Math.min(20, Math.round((elo - 600f) / 130f)));
            if (curSkillLevel != skillFromElo) {
                sendUciCommand("setoption name Skill Level value " + skillFromElo);
                curSkillLevel = skillFromElo;
            }
        }
    }

    private static boolean isWhiteToMove(String fen, List<String> moves) {
        boolean white = true;
        int sp = fen.indexOf(' ');
        if (sp >= 0 && sp + 1 < fen.length()) white = fen.charAt(sp + 1) != 'b';
        if (moves != null && (moves.size() & 1) == 1) white = !white;
        return white;
    }

    private static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
