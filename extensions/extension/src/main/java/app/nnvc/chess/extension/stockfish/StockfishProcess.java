package app.nnvc.chess.extension.stockfish;

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

    private static final String TAG = "NNVC";

    public static final float MATE_SCORE = 99.0f;
    private static final int READY_TIMEOUT_MS = 10_000;
    private static final int BESTMOVE_GRACE_MS = 10_000;
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
    private int curContempt = -999;
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
        public final float[] sideToMoveScores;
        public final boolean[] lineMates;
        public final int[] lineMateIn;
        public final int depth;
        public final boolean terminal;

        public AnalysisResult(List<String> moves, float score, boolean hasMate, int mateIn,
                              int wdlWin, int wdlDraw, int wdlLoss, String ponder) {
            this(moves, score, hasMate, mateIn, wdlWin, wdlDraw, wdlLoss, ponder,
                    moves != null && !moves.isEmpty() ? Collections.singletonList(moves.get(0)) : new ArrayList<>(),
                    new float[]{score},
                    new float[]{score},
                    new boolean[]{hasMate},
                    new int[]{mateIn},
                    0, false);
        }

        public AnalysisResult(List<String> moves, float score, boolean hasMate, int mateIn,
                              int wdlWin, int wdlDraw, int wdlLoss, String ponder,
                              List<String> pv, float[] lineScores, int depth, boolean terminal) {
            this(moves, score, hasMate, mateIn, wdlWin, wdlDraw, wdlLoss, ponder, pv, lineScores, lineScores,
                    createDefaultLineMates(hasMate, lineScores != null ? lineScores.length : 0),
                    createDefaultLineMateIn(mateIn, lineScores != null ? lineScores.length : 0),
                    depth, terminal);
        }

        public AnalysisResult(List<String> moves, float score, boolean hasMate, int mateIn,
                              int wdlWin, int wdlDraw, int wdlLoss, String ponder,
                              List<String> pv, float[] lineScores,
                              boolean[] lineMates, int[] lineMateIn,
                              int depth, boolean terminal) {
            this(moves, score, hasMate, mateIn, wdlWin, wdlDraw, wdlLoss, ponder, pv, lineScores, lineScores,
                    lineMates, lineMateIn, depth, terminal);
        }

        public AnalysisResult(List<String> moves, float score, boolean hasMate, int mateIn,
                              int wdlWin, int wdlDraw, int wdlLoss, String ponder,
                              List<String> pv, float[] lineScores, float[] sideToMoveScores,
                              boolean[] lineMates, int[] lineMateIn,
                              int depth, boolean terminal) {
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
            this.sideToMoveScores = sideToMoveScores != null ? sideToMoveScores : (lineScores != null ? lineScores.clone() : new float[0]);
            this.lineMates = lineMates != null ? lineMates : new boolean[this.lineScores.length];
            this.lineMateIn = lineMateIn != null ? lineMateIn : new int[this.lineScores.length];
            this.depth = depth;
            this.terminal = terminal;
        }

        private static boolean[] createDefaultLineMates(boolean hasMate, int size) {
            boolean[] res = new boolean[size];
            if (hasMate && size > 0) res[0] = true;
            return res;
        }

        private static int[] createDefaultLineMateIn(int mateIn, int size) {
            int[] res = new int[size];
            if (size > 0) res[0] = mateIn;
            return res;
        }

        public static AnalysisResult empty() {
            return new AnalysisResult(new ArrayList<>(), 0f, false, 0, 0, 0, 0, null,
                    new ArrayList<>(), new float[0], new float[0], new boolean[0], new int[0], 0, false);
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
        final String engineChoice = StockfishSettings.getEngineChoice(context);

        // Auto-switch engine if user changed engine in settings
        if (curEngineChoice != null && !engineChoice.equals(curEngineChoice)) {
            NnvcLogger.i(TAG, "Engine choice changed in settings from " + curEngineChoice + " to " + engineChoice);
            curEngineChoice = engineChoice;
            resetOptionCache();
            if (wasmManager != null) {
                wasmManager.switchEngine(engineChoice);
                wasmManager.waitForPlayWorkerReady(8000);
            }
        }

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

            StringBuilder pos = new StringBuilder(fen.length() + 32 + (uciMoves != null ? uciMoves.size() * 6 : 0));
            // Send position startpos moves if moves list is present and matches standard startpos to maximize Transposition Table reuse (3x faster)
            if (uciMoves != null && !uciMoves.isEmpty() && WasmEngineManager.isStandardStartposMoves(uciMoves)) {
                pos.append("position startpos moves");
                for (String m : uciMoves) pos.append(' ').append(m);
            } else {
                pos.append("position fen ").append(fen);
            }
            sendUciCommand(pos.toString());

            // Soft Movetime standard NNVC Extension: fast responsiveness at Depth 12
            int softMs = movetimeMs > 0 ? movetimeMs : Math.max(700, Math.min(2100, effectiveDepth * 175));
            String goCmd = "go depth " + effectiveDepth + " movetime " + softMs;
            sendUciCommand(goCmd);
            NnvcLogger.d(TAG, "Search started [wasm]: " + goCmd + " (Elo=" + elo + ")");

            int targetMpv = Math.max(1, Math.min(8, multiPV));
            return readSearchOutput(targetMpv, whiteToMove,
                    softMs + BESTMOVE_GRACE_MS,
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
        float[] sideScores = new float[multiPV];
        boolean[] lineMates = new boolean[multiPV];
        int[] lineMateIn = new int[multiPV];
        long[] evalNums = new long[multiPV];
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

            long currentEvalNum = isMate
                    ? (scoreVal > 0 ? (100000L - Math.abs(scoreVal) * 100L) : (-100000L + Math.abs(scoreVal) * 100L))
                    : (long) scoreVal;

            float currentScore;
            float currentSideScore;
            int curWhiteMate = 0;
            if (isMate) {
                curWhiteMate = whiteToMove ? scoreVal : -scoreVal;
                currentScore = curWhiteMate > 0 ? (MATE_SCORE - Math.abs(curWhiteMate)) : (-MATE_SCORE + Math.abs(curWhiteMate));
                currentSideScore = scoreVal > 0 ? (MATE_SCORE - Math.abs(scoreVal)) : (-MATE_SCORE + Math.abs(scoreVal));
                if (mpv == 1) {
                    hasMate = true;
                    mateIn = curWhiteMate;
                }
            } else {
                float pawns = scoreVal / 100.0f;
                currentScore = whiteToMove ? pawns : -pawns;
                currentSideScore = pawns;
                if (mpv == 1) hasMate = false;
            }

            if (!bound) {
                scores[idx] = currentScore;
                sideScores[idx] = currentSideScore;
                lineMates[idx] = isMate;
                lineMateIn[idx] = scoreVal; // Side-to-move POV for arrow labels
                evalNums[idx] = currentEvalNum;
                haveExact[idx] = true;
                haveAny[idx] = true;
            } else if (!haveExact[idx]) {
                scores[idx] = currentScore;
                sideScores[idx] = currentSideScore;
                lineMates[idx] = isMate;
                lineMateIn[idx] = scoreVal;
                evalNums[idx] = currentEvalNum;
                haveAny[idx] = true;
            }

            if (w >= 0 && mpv == 1) {
                wdlW = whiteToMove ? w : l;
                wdlD = d;
                wdlL = whiteToMove ? l : w;
            }

            if (depth > reachedDepth) reachedDepth = depth;

            // Only report progress when ALL multiPV branches for this depth are received, never drop lines!
            if (progress != null && depth >= PROGRESS_MIN_DEPTH && (multiPV <= 1 || mpv == multiPV) && firstMoves[0] != null) {
                long now = System.currentTimeMillis();
                if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                    lastReport = now;
                    reportedDepth = depth;
                    try {
                        AnalysisResult partial = buildResult(firstMoves, scores, sideScores, lineMates, lineMateIn, evalNums,
                                haveAny, multiPV, hasMate, mateIn, wdlW, wdlD, wdlL,
                                bestPv != null && bestPv.size() > 1 ? bestPv.get(1) : null,
                                bestPv, depth, false, null, whiteToMove);
                        progress.onProgress(partial);
                    } catch (Throwable ignored) {}
                }
            }
        }

        return buildResult(firstMoves, scores, sideScores, lineMates, lineMateIn, evalNums,
                haveAny, multiPV, hasMate, mateIn,
                wdlW, wdlD, wdlL, ponder, bestPv, reachedDepth, terminal, bestmove, whiteToMove);
    }

    private static class CandidateLine {
        String move;
        float score;
        float sideScore;
        boolean isMate;
        int mateIn;
        long evalNum;
    }

    private AnalysisResult buildResult(String[] firstMovesIn, float[] scoresIn, float[] sideScoresIn,
                                       boolean[] lineMatesIn, int[] lineMateInIn, long[] evalNumsIn,
                                       boolean[] haveAny, int multiPV, boolean hasMate, int mateIn,
                                       int wdlW, int wdlD, int wdlL, String ponder,
                                       List<String> bestPv, int reachedDepth,
                                       boolean terminal, String bestmove, boolean whiteToMove) {
        String[] firstMoves = firstMovesIn.clone();
        float[] scores = scoresIn.clone();
        float[] sideScores = sideScoresIn.clone();
        boolean[] lineMates = lineMatesIn.clone();
        int[] lineMateIn = lineMateInIn.clone();
        long[] evalNums = evalNumsIn.clone();

        if (bestmove != null && firstMoves[0] == null) {
            firstMoves[0] = bestmove;
            haveAny[0] = true;
        }

        List<CandidateLine> candidates = new ArrayList<>();
        for (int i = 0; i < multiPV; i++) {
            if (firstMoves[i] == null) continue;
            boolean dup = false;
            for (CandidateLine c : candidates) {
                if (firstMoves[i].equals(c.move)) {
                    dup = true;
                    break;
                }
            }
            if (dup) continue;

            CandidateLine c = new CandidateLine();
            c.move = firstMoves[i];
            c.score = haveAny[i] ? scores[i] : scores[0];
            c.sideScore = haveAny[i] ? sideScores[i] : sideScores[0];
            c.isMate = haveAny[i] ? lineMates[i] : lineMates[0];
            c.mateIn = haveAny[i] ? lineMateIn[i] : lineMateIn[0];
            c.evalNum = haveAny[i] ? evalNums[i] : evalNums[0];
            candidates.add(c);
        }

        // Sort candidates descending by evalNum: strongest move for side-to-move first!
        Collections.sort(candidates, (a, b) -> Long.compare(b.evalNum, a.evalNum));

        // If bestmove is present, prioritize it at index 0
        if (bestmove != null) {
            for (int i = 0; i < candidates.size(); i++) {
                if (bestmove.equals(candidates.get(i).move)) {
                    if (i > 0) {
                        CandidateLine top = candidates.remove(i);
                        candidates.add(0, top);
                    }
                    break;
                }
            }
        }

        List<String> moves = new ArrayList<>(candidates.size());
        float[] finalLineScores = new float[candidates.size()];
        float[] finalSideScores = new float[candidates.size()];
        boolean[] finalLineMates = new boolean[candidates.size()];
        int[] finalLineMateIn = new int[candidates.size()];

        for (int i = 0; i < candidates.size(); i++) {
            CandidateLine c = candidates.get(i);
            moves.add(c.move);
            finalLineScores[i] = c.score;
            finalSideScores[i] = c.sideScore;
            finalLineMates[i] = c.isMate;
            finalLineMateIn[i] = c.mateIn;
        }

        boolean finalHasMate = finalLineMates.length > 0 ? finalLineMates[0] : hasMate;
        int finalMateIn = finalLineMates.length > 0 ? (whiteToMove ? finalLineMateIn[0] : -finalLineMateIn[0]) : mateIn;
        float finalScore = finalLineScores.length > 0 ? finalLineScores[0] : (scores.length > 0 ? scores[0] : 0f);

        boolean isTerminal = terminal && moves.isEmpty();
        if (isTerminal && !finalHasMate) {
            finalScore = 0f;
        }

        return new AnalysisResult(moves, finalScore, finalHasMate, finalMateIn, wdlW, wdlD, wdlL, ponder,
                bestPv != null ? bestPv : new ArrayList<>(),
                finalLineScores, finalSideScores, finalLineMates, finalLineMateIn,
                reachedDepth, isTerminal);
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
        curContempt = -999;
    }

    private void applyPlayEngineOptions(Context context, int elo, int multiPV, boolean honorLimit) {
        String engineChoice = StockfishSettings.getEngineChoice(context);
        boolean isKomodo = StockfishSettings.ENGINE_KOMODO.equals(engineChoice);

        int finalMpv = Math.max(1, Math.min(8, multiPV));
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

        if (isKomodo) {
            // Komodo Dragon 3.3 UCI options
            int skillFromElo = Math.max(0, Math.min(20, Math.round((elo - 600f) / 130f)));
            if (curSkillLevel != skillFromElo) {
                sendUciCommand("setoption name Skill Level value " + skillFromElo);
                curSkillLevel = skillFromElo;
            }
            if (elo < 2000) {
                int maxErr = Math.round((2000f - elo) / 70f) + 2;
                int prob = Math.round((2000f - elo) / 50f) + 1;
                if (curSkillErr != maxErr) {
                    sendUciCommand("setoption name Skill Level Maximum Error value " + maxErr);
                    curSkillErr = maxErr;
                }
                if (curSkillProb != prob) {
                    sendUciCommand("setoption name Skill Level Probability value " + prob);
                    curSkillProb = prob;
                }
            } else {
                if (curSkillErr != 0) {
                    sendUciCommand("setoption name Skill Level Maximum Error value 0");
                    curSkillErr = 0;
                }
                if (curSkillProb != 0) {
                    sendUciCommand("setoption name Skill Level Probability value 0");
                    curSkillProb = 0;
                }
            }

            // Komodo Style maps directly to Contempt option
            String style = StockfishSettings.getKomodoStyle(context);
            int contempt = 0;
            if (StockfishSettings.STYLE_AGGRESSIVE.equalsIgnoreCase(style)) {
                contempt = 50;
            } else if (StockfishSettings.STYLE_DEFENSIVE.equalsIgnoreCase(style)) {
                contempt = -50;
            } else {
                contempt = 0;
            }
            if (curContempt != contempt) {
                sendUciCommand("setoption name Contempt value " + contempt);
                curContempt = contempt;
            }
        } else {
            // Stockfish 18 options
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
    }

    public static boolean isWhiteToMove(String fen) {
        if (fen == null) return true;
        int sp = fen.indexOf(' ');
        if (sp >= 0 && sp + 1 < fen.length()) {
            return fen.charAt(sp + 1) != 'b';
        }
        return true;
    }

    public static boolean isWhiteToMove(String fen, List<String> moves) {
        return isWhiteToMove(fen);
    }

    private static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
