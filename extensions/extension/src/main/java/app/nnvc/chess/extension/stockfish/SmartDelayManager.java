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

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SmartDelayManager – 100% Parity with NNVC Desktop Extension Auto Smart Delay
 * (Ported directly from NNVC/nnvc_main_bundle.js:7125-8308 & 16420-16437).
 *
 * 14-Step Human Simulation Architecture:
 *  1. Full dynamic time budgeting via _humanBudgetSec
 *  2. Multi-feature position extraction (_humanDelayFeatures -> obvious, tricky, easy, hard, preparedReply)
 *  3. Dynamic Markovian psychological state machine (_humanChooseState: flow, convert, careful, uncertain, panic, stable)
 *  4. Session fatigue & drift factor (_humanSessionDrift)
 *  5. Opponent reaction & psychological mirroring (_humanOpponentReaction)
 *  6. Box-Muller log-normal distribution + persona wobble & biometric physical jitter (_humanJitterSec)
 *  7. Micro-hesitation generator (_humanMicroHesitationSec)
 *  8. Monotony breaker (_humanBreakFactory)
 *  9. Full 8-scenario finalizer (_humanFinalizeDelay):
 *     - Scenario 1: Only move (0.14-0.75s)
 *     - Scenario 2: Under check assessment (0.75-9.5s)
 *     - Scenario 3: Recapture (0.35-3.2s)
 *     - Scenario 4: Opening to middlegame shift (1.2-22s)
 *     - Scenario 5: Queen trade transition (1.1-18s)
 *     - Scenario 6: Book / opening development (0.4-10s)
 *     - Scenario 7: Deep think (1.4-35s)
 *     - Scenario 8: Normal middlegame/endgame 3-band tempo rolls
 *     - Branching entropy factor (numLegal >= 32 bonus vs <= 10 discount)
 *     - Streak fatigue (moveNum >= 38)
 *     - Time trouble hard caps (<= 15s, <= 5s panic mode)
 *  10. Delay state committer (_humanCommitDelayState: timeDebt, fastChain, investChain, humanRelease, tempoEma)
 *  11. Engine think time deduction & physical buffer clamp (16420-16437)
 *  12. Mate panic reflex (180-500ms, 25ms buffer) & First move cap (350-1200ms)
 *  13. Headroom cap: Never sleep past 6.5s panic threshold
 *  14. 100% Native Time Control & Clock detection via RealGameViewModel & b.<init> Bytecode hook
 *
 * Strict Rule: Zero Fallback.
 */
public class SmartDelayManager {

    private static final String TAG = "NNVC_SmartDelay";
    private static final Random RANDOM = new Random();
    private static volatile Handler mainHandler = null;

    private static Handler getMainHandler() {
        if (mainHandler == null) {
            try {
                mainHandler = new Handler(Looper.getMainLooper());
            } catch (Throwable ignored) {}
        }
        return mainHandler;
    }

    private static void postToMain(Runnable r) {
        Handler h = getMainHandler();
        if (h != null) h.post(r);
    }

    private static void postDelayedToMain(Runnable r, long delayMs) {
        Handler h = getMainHandler();
        if (h != null) h.postDelayed(r, delayMs);
    }

    // ── 1. 9 Presets Time Control (_TC_PRESETS dòng 6934-7150) ───────────────────
    public static class TCPreset {
        public final int index;
        public final String label;
        public final double mean;
        public final double sigma;
        public final double lo;
        public final double hi;
        public final double flashBase;
        public final double flashCap;
        public final double deepBase;
        public final double deepCap;
        public final int rhythmMin;
        public final int rhythmMax;
        public final double tempoSwing;
        public final double smooth;
        public final double glanceProb;
        public final double hesitateProb;
        public final double hesitateLo;
        public final double hesitateHi;
        public final int releaseMin;
        public final int releaseMax;

        public TCPreset(int index, String label, double mean, double sigma, double lo, double hi,
                        double flashBase, double flashCap, double deepBase, double deepCap,
                        int rhythmMin, int rhythmMax, double tempoSwing, double smooth,
                        double glanceProb, double hesitateProb, double hesitateLo, double hesitateHi,
                        int releaseMin, int releaseMax) {
            this.index = index;
            this.label = label;
            this.mean = mean;
            this.sigma = sigma;
            this.lo = lo;
            this.hi = hi;
            this.flashBase = flashBase;
            this.flashCap = flashCap;
            this.deepBase = deepBase;
            this.deepCap = deepCap;
            this.rhythmMin = rhythmMin;
            this.rhythmMax = rhythmMax;
            this.tempoSwing = tempoSwing;
            this.smooth = smooth;
            this.glanceProb = glanceProb;
            this.hesitateProb = hesitateProb;
            this.hesitateLo = hesitateLo;
            this.hesitateHi = hesitateHi;
            this.releaseMin = releaseMin;
            this.releaseMax = releaseMax;
        }

        public String getLabel(boolean isVi) {
            if (isVi) return label;
            return label.replace("phút", "min");
        }
    }

    public static final TCPreset[] TC_PRESETS = new TCPreset[] {
            new TCPreset(0, "1 phút",  1.15, 0.58, 0.35, 3.2,  0.24, 0.46, 0.03, 0.12, 2, 3, 0.14, 0.10, 0.16, 0.010, 0.15, 0.65, 1, 1),
            new TCPreset(1, "1|1",     1.45, 0.58, 0.35, 4.0,  0.23, 0.45, 0.04, 0.14, 2, 3, 0.15, 0.11, 0.18, 0.012, 0.18, 0.75, 1, 1),
            new TCPreset(2, "2|1",     2.20, 0.56, 0.40, 5.5,  0.20, 0.40, 0.06, 0.18, 2, 4, 0.17, 0.13, 0.22, 0.015, 0.22, 0.95, 1, 2),
            new TCPreset(3, "3 phút",  3.80, 0.54, 0.75, 9.5,  0.18, 0.36, 0.08, 0.21, 3, 4, 0.18, 0.15, 0.25, 0.018, 0.28, 1.20, 1, 2),
            new TCPreset(4, "3|2",     4.50, 0.54, 0.80, 11.0, 0.17, 0.34, 0.10, 0.23, 3, 4, 0.19, 0.16, 0.27, 0.020, 0.35, 1.45, 1, 2),
            new TCPreset(5, "5 phút",  6.80, 0.52, 1.00, 16.0, 0.15, 0.30, 0.12, 0.25, 3, 5, 0.20, 0.18, 0.30, 0.024, 0.45, 1.80, 1, 2),
            new TCPreset(6, "10 phút", 12.00, 0.50, 1.20, 32.0, 0.12, 0.26, 0.16, 0.28, 4, 5, 0.22, 0.20, 0.34, 0.028, 0.65, 2.60, 1, 3),
            new TCPreset(7, "15|10",   20.00, 0.48, 1.50, 55.0, 0.10, 0.22, 0.20, 0.32, 4, 6, 0.24, 0.22, 0.38, 0.032, 0.85, 3.80, 2, 3),
            new TCPreset(8, "30 phút", 28.00, 0.46, 1.80, 85.0, 0.08, 0.18, 0.24, 0.36, 4, 7, 0.26, 0.24, 0.42, 0.036, 1.10, 4.50, 2, 4)
    };

    // ── 2. Full State Machine & State Tracking (_resetSdState dòng 7125-7201) ───
    public static class SdState {
        public int moveNum = 0;
        public Double oppClockBefore = null;
        public Double oppThinkSec = null;
        public long lastMoveMs = 0;
        public boolean lastWasCapture = false;
        public boolean lastWasCheck = false;
        public String lastOppMoveUci = "";
        public String lastOppFrom = "";
        public String lastOppTo = "";
        public boolean lastOppWasCapture = false;
        public boolean lastOppWasCheck = false;
        public int _deepThinkCooldown = 0;
        public boolean openingOver = false;
        public double timeDebtMs = 0;
        public double tiltLevel = 0;
        public final List<String> posHistory = new ArrayList<>();
        public final List<Double> oppThinkHistory = new ArrayList<>();
        public Double _oppTempoEma = null;
        public Double _myTempoEma = null;
        public final Map<String, Integer> _familiarFenMap = new HashMap<>();
        public String _lastBoardKey = "";
        public int _clockGlanceNextMove = 3 + RANDOM.nextInt(6);
        public int _clockGlanceInterval = 4 + RANDOM.nextInt(5);
        public boolean _openingFamiliar = RANDOM.nextDouble() > 0.25;
        public int _postDeepRelease = 0;
        public double _evalSwingCp = 0;
        public double _lastScoreChange = 0;
        public boolean _historyTrusted = true;
        public int _histMismatchCount = 0;
        public double _sessionDrift = 0.94 + RANDOM.nextDouble() * 0.14;
        public int _sessionDriftHold = 2 + RANDOM.nextInt(4);
        public String _sessionDriftTag = "base";
        public double _microHesitationBias = RANDOM.nextDouble() * 0.22;
        public int _reactionCarry = 0;
        public String _reactionTag = "none";
        public boolean _phaseShiftOpeningDone = false;
        public boolean _phaseShiftQueenTradeDone = false;
        public Integer _prevQueensCount = null;
        public boolean _lastIsCheck = false;
        public String _lastScenarioLabel = "Tự động";

        // Human Persona & Markovian state
        public HumanPersona _humanPersona = null;
        public String _humanState = "stable";
        public int _humanStateHold = 0;
        public int _humanRelease = 0;
        public final List<Double> _humanRecentDelay = new ArrayList<>();
        public final List<String> _humanRecentModes = new ArrayList<>();
        public int _humanFastChain = 0;
        public int _humanInvestChain = 0;
        public String _humanStateReason = "init";
        public double _humanLastBudget = 0;
    }

    public static volatile SdState sState = new SdState();

    public static class HumanPersona {
        public final String name;
        public final double tempo;
        public final double snapBias;
        public final double investBias;
        public final double wobble;
        public final int holdBase;
        public final int holdVar;
        public final int releaseMin;
        public final int releaseMax;

        public HumanPersona(String name, double tempo, double snapBias, double investBias, double wobble, int relMin, int relMax) {
            this(name, tempo, snapBias, investBias, wobble, 2, 2, relMin, relMax);
        }

        public HumanPersona(String name, double tempo, double snapBias, double investBias, double wobble, int holdBase, int holdVar, int relMin, int relMax) {
            this.name = name;
            this.tempo = tempo;
            this.snapBias = snapBias;
            this.investBias = investBias;
            this.wobble = wobble;
            this.holdBase = holdBase;
            this.holdVar = holdVar;
            this.releaseMin = relMin;
            this.releaseMax = relMax;
        }
    }

    private static volatile int activeTcIndex = 5; // Default 5 phút
    private static volatile String activeTcSignature = null;
    private static volatile HumanPersona currentPersona = null;
    private static volatile boolean lastWasCapture = false;
    private static volatile boolean lastWasCheck = false;
    private static volatile float lastEvalScore = 0f;
    private static volatile String lastScenarioLabel = "Tự động";

    // ── 3. Thuật toán phân phối Log-Normal & Jitter (Dòng 7203-7226) ─────────────
    public static double lognormal(double mean, double sigma) {
        double u, v;
        do {
            u = RANDOM.nextDouble();
            v = RANDOM.nextDouble();
        } while (u <= 0.0);
        double z = Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2.0 * Math.PI * v);
        double mu = Math.log(mean) - 0.5 * sigma * sigma;
        return Math.exp(mu + z * sigma);
    }

    public static long physicalJitter() {
        if (RANDOM.nextDouble() < 0.82) {
            return Math.round(15 + RANDOM.nextDouble() * 65);
        }
        return Math.round(85 + RANDOM.nextDouble() * 115);
    }

    public static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    public static Double ema(Double prev, double next, double alpha) {
        if (prev == null || Double.isNaN(prev) || Double.isInfinite(prev)) return next;
        return prev + alpha * (next - prev);
    }

    public static double avg(List<Double> arr) {
        if (arr == null || arr.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double d : arr) sum += d;
        return sum / arr.size();
    }

    public static <T> T weightedPick(T[] items, double[] weights) {
        double total = 0.0;
        for (double w : weights) total += Math.max(0.0, w);
        if (total <= 0.0) return items[0];
        double r = RANDOM.nextDouble() * total;
        for (int i = 0; i < items.length; i++) {
            r -= Math.max(0.0, weights[i]);
            if (r <= 0.0) return items[i];
        }
        return items[items.length - 1];
    }

    // ── 4. Khởi tạo Persona theo thể loại cờ (_ensureHumanDelayPersona dòng 7237-7362) ─
    public static HumanPersona ensureHumanDelayPersona(boolean isBullet, boolean isBlitz, boolean isRapid) {
        if (sState._humanPersona != null) {
            currentPersona = sState._humanPersona;
            return sState._humanPersona;
        }

        HumanPersona[] pool;
        double[] weights;
        if (isBullet) {
            pool = new HumanPersona[] {
                    new HumanPersona("snapper", 0.90, 0.14, -0.08, 0.12, 2, 1, 1, 1),
                    new HumanPersona("street",  0.96, 0.06,  0.02, 0.16, 2, 2, 1, 2),
                    new HumanPersona("grinder", 1.06, -0.08, 0.14, 0.14, 2, 2, 1, 2)
            };
            weights = new double[] { 0.20, 0.50, 0.30 };
        } else if (isBlitz) {
            pool = new HumanPersona[] {
                    new HumanPersona("snappy",   0.93, 0.10, -0.02, 0.14, 2, 2, 1, 2),
                    new HumanPersona("balanced", 1.00, 0.02,  0.06, 0.18, 3, 2, 1, 2),
                    new HumanPersona("careful",  1.08, -0.08, 0.18, 0.20, 3, 2, 1, 3)
            };
            weights = new double[] { 0.22, 0.52, 0.26 };
        } else {
            pool = new HumanPersona[] {
                    new HumanPersona("practical", 0.92, 0.10,  0.02, 0.16, 3, 2, 1, 2),
                    new HumanPersona("balanced",  1.00, 0.02,  0.12, 0.20, 3, 3, 1, 3),
                    new HumanPersona("tanky",     1.12, -0.10, 0.24, 0.22, 4, 3, 2, 4)
            };
            weights = new double[] { 0.24, 0.46, 0.30 };
        }

        HumanPersona picked = weightedPick(pool, weights);
        sState._humanPersona = picked;
        sState._humanState = isBullet ? "stable" : (RANDOM.nextDouble() < 0.18 ? "careful" : "stable");
        sState._humanStateHold = picked.holdBase + (picked.holdVar > 0 ? RANDOM.nextInt(picked.holdVar + 1) : 0);
        sState._humanRelease = 0;
        sState._humanRecentDelay.clear();
        sState._humanRecentModes.clear();
        sState._humanFastChain = 0;
        sState._humanInvestChain = 0;
        sState._humanStateReason = "init";
        sState._humanLastBudget = 0;
        currentPersona = picked;
        return picked;
    }

    public static HumanPersona getOrCreatePersona(int tcIdx) {
        boolean isBullet = tcIdx <= 2;
        boolean isBlitz = tcIdx >= 3 && tcIdx <= 5;
        boolean isRapid = tcIdx >= 6;
        return ensureHumanDelayPersona(isBullet, isBlitz, isRapid);
    }

    public static void resetGameSession() {
        sState = new SdState();
        currentPersona = null;
        lastWasCapture = false;
        lastWasCheck = false;
        lastEvalScore = 0f;
        lastScenarioLabel = "Tự động";
    }

    // ── 5. Cấu hình State Tâm Lý Người Thật (_humanStateCfg dòng 7363-7420) ──────
    public static class HumanStateCfg {
        public final String name;
        public final double scale;
        public final double snap;
        public final double invest;
        public final double hiccup;
        public final int holdBase;
        public final int holdVar;

        public HumanStateCfg(String name, double scale, double snap, double invest, double hiccup, int holdBase, int holdVar) {
            this.name = name;
            this.scale = scale;
            this.snap = snap;
            this.invest = invest;
            this.hiccup = hiccup;
            this.holdBase = holdBase;
            this.holdVar = holdVar;
        }
    }

    public static HumanStateCfg humanStateCfg(String name) {
        if ("flow".equals(name)) {
            return new HumanStateCfg("flow", 0.64, 0.24, -0.14, 0.03, 2, 1);
        } else if ("convert".equals(name)) {
            return new HumanStateCfg("convert", 0.78, 0.12, -0.08, 0.04, 2, 2);
        } else if ("careful".equals(name)) {
            return new HumanStateCfg("careful", 1.24, -0.07, 0.18, 0.11, 2, 2);
        } else if ("uncertain".equals(name)) {
            return new HumanStateCfg("uncertain", 1.42, -0.14, 0.28, 0.18, 2, 2);
        } else if ("panic".equals(name)) {
            return new HumanStateCfg("panic", 0.26, 0.34, -0.44, 0.00, 1, 1);
        } else {
            return new HumanStateCfg("stable", 0.96, 0.02, 0.02, 0.07, 2, 2);
        }
    }

    // ── 6. Phân Tích Thế Cờ FEN & Legal Moves (_analyzePosition dòng 8309-8361) ───
    public static class PositionFeatures {
        public String phase = "middlegame";
        public String subphase = "early";
        public boolean isOpening = false;
        public boolean isEndgame = false;
        public boolean isForced = false;
        public boolean isTactical = false;
        public boolean isComplex = false;
        public boolean forced = false;
        public boolean tactical = false;
        public boolean complex = false;
        public boolean quiet = false;
        public boolean hasCaptures = false;
        public boolean hasChecks = false;
        public boolean isCheck = false;
        public int numLegal = 20;
        public int moveNum = 1;
        public int undeveloped = 0;
        public int numPieces = 32;
        public int totalPieces = 14;
        public int queens = 2;
        public int rooks = 4;
        public int minors = 4;
        public int pawns = 16;
        public boolean wKingHome = true;
        public boolean bKingHome = true;
    }

    public static PositionFeatures analyzePosition(String fen, List<BoardUtil.LegalMove> legal, String chosenMove) {
        PositionFeatures feat = new PositionFeatures();
        if (fen == null || fen.isEmpty()) return feat;

        try {
            String[] parts = fen.trim().split("\\s+");
            String board = parts[0];
            if (parts.length >= 6) {
                try { feat.moveNum = Integer.parseInt(parts[5]); } catch (Throwable ignored) {}
            }

            int queens = 0, rooks = 0, minors = 0, pawns = 0;
            for (char c : board.toCharArray()) {
                if (c == 'q' || c == 'Q') queens++;
                else if (c == 'r' || c == 'R') rooks++;
                else if (c == 'n' || c == 'N' || c == 'b' || c == 'B') minors++;
                else if (c == 'p' || c == 'P') pawns++;
            }
            feat.queens = queens;
            feat.rooks = rooks;
            feat.minors = minors;
            feat.pawns = pawns;
            feat.totalPieces = queens + rooks + minors;
            feat.numPieces = queens + rooks + minors + pawns;

            String[] rows = board.split("/");
            if (rows.length == 8) {
                String rank1 = rows[7];
                String rank8 = rows[0];
                for (char c : rank1.toCharArray()) if (c == 'N' || c == 'B') feat.undeveloped++;
                for (char c : rank8.toCharArray()) if (c == 'n' || c == 'b') feat.undeveloped++;
                feat.wKingHome = rank1.contains("K") && rank1.indexOf('K') == 4;
                feat.bKingHome = rank8.contains("k") && rank8.indexOf('k') == 4;
            }

            if (legal != null) {
                feat.numLegal = legal.size();
                for (BoardUtil.LegalMove m : legal) {
                    if (m.capturedPiece != '.') feat.hasCaptures = true;
                    if (m.isCheck) feat.hasChecks = true;
                }
            } else {
                feat.numLegal = 20;
            }

            feat.isCheck = sState.lastOppWasCheck || sState.lastWasCheck;

            // Phân loại Phase & Subphase chuẩn 100% dòng 8328-8342
            if (!sState.openingOver && feat.moveNum <= 14 && feat.undeveloped >= 2) {
                feat.phase = "opening";
                feat.isOpening = true;
                if (feat.moveNum <= 4 && feat.undeveloped >= 5) feat.subphase = "book";
                else if (feat.moveNum <= 8 && feat.undeveloped >= 3) feat.subphase = "develop";
                else feat.subphase = "transition";
            } else if (queens == 0 || (queens <= 1 && rooks <= 1 && minors <= 2) || (feat.totalPieces <= 3 && pawns <= 4)) {
                feat.phase = "endgame";
                feat.isEndgame = true;
                feat.subphase = (queens == 0 && minors <= 2) ? "technical" : "complex";
            } else {
                feat.phase = "middlegame";
                if (feat.moveNum <= 18 && feat.totalPieces >= 10) feat.subphase = "early";
                else if (feat.totalPieces >= 7) feat.subphase = "peak";
                else feat.subphase = "late";
            }

            if (chosenMove != null && chosenMove.length() >= 4) {
                String toSq = chosenMove.substring(2, 4);
                char destPiece = StockfishExtension.getPieceAtSquare(fen, toSq);
                if (destPiece != '.' && destPiece != ' ') {
                    feat.hasCaptures = true;
                }
            }

            feat.tactical = feat.hasCaptures || feat.hasChecks;
            feat.isTactical = feat.tactical;
            feat.forced = feat.numLegal <= 2;
            feat.isForced = feat.forced;
            feat.quiet = !feat.tactical && feat.numLegal > 14;
            feat.complex = feat.tactical && feat.numLegal > 10;
            feat.isComplex = feat.complex;

        } catch (Throwable t) {
            NnvcLogger.w(TAG, "analyzePosition error: " + t.getMessage());
        }
        return feat;
    }

    public static PositionFeatures analyzeFen(String fen, String chosenMove) {
        return analyzePosition(fen, null, chosenMove);
    }

    // ── 7. Ngân Sách Thời Gian Người Thật (_humanBudgetSec dòng 7421-7433) ────────
    public static double computeHumanBudget(TCPreset preset, PositionFeatures pos, Double mySec, Double oppSec, int inc, boolean isBullet, boolean isBlitz, boolean isRapid) {
        if (mySec == null) return preset.mean * (isRapid ? 0.92 : (isBlitz ? 0.86 : 0.78));
        int movesLeft;
        if ("opening".equals(pos.phase)) {
            movesLeft = isBullet ? 15 : (isBlitz ? 21 : 28);
        } else if ("endgame".equals(pos.phase)) {
            movesLeft = isBullet ? 11 : (isBlitz ? 16 : 22);
        } else {
            movesLeft = isBullet ? 13 : (isBlitz ? 19 : 26);
        }
        double safety = isBullet ? 0.26 : (isBlitz ? 0.19 : 0.14);
        double effSec = mySec + Math.min(inc * movesLeft * (isRapid ? 0.72 : (isBlitz ? 0.56 : 0.44)), mySec * (isRapid ? 0.55 : (isBlitz ? 0.40 : 0.28)));
        double budget = Math.max(preset.lo, effSec * (1.0 - safety) / Math.max(8, movesLeft));
        if ("opening".equals(pos.phase)) {
            budget *= isBullet ? 0.62 : (isBlitz ? 0.68 : 0.75);
        } else if ("middlegame".equals(pos.phase)) {
            budget *= isBullet ? 1.08 : (isBlitz ? 1.16 : 1.22);
        } else if ("endgame".equals(pos.phase) && "technical".equals(pos.subphase)) {
            budget *= isBullet ? 0.80 : (isBlitz ? 0.86 : 0.92);
        }
        if (oppSec != null && mySec < oppSec * 0.78) {
            budget *= 0.90;
        } else if (oppSec != null && mySec > oppSec * 1.45) {
            budget *= 1.05;
        }
        return clamp(budget, preset.lo, preset.hi * (isBullet ? 0.82 : (isBlitz ? 0.92 : 1.0)));
    }

    // ── 8. Trích Xuất Đặc Trưng Độ Trễ (_humanDelayFeatures dòng 7434-7486) ──────
    public static class DelayFeatures {
        public double obvious;
        public double tricky;
        public double easy;
        public double hard;
        public boolean preparedReply;
        public boolean oppPlayedFast;
        public boolean historyUnsafe;
    }

    public static DelayFeatures computeDelayFeatures(PositionFeatures pos, TCPreset preset, Double mySec, Double oppSec, double rushSec, int moveNum, int timesSeen, double scoreChange, boolean isBullet, boolean isBlitz, boolean isRapid) {
        DelayFeatures feat = new DelayFeatures();
        feat.historyUnsafe = sState._histMismatchCount > 0 || !sState._historyTrusted;
        feat.oppPlayedFast = sState.oppThinkSec != null && sState.oppThinkSec < Math.max(0.35, preset.mean * 0.60);
        feat.preparedReply = !feat.historyUnsafe && feat.oppPlayedFast && (pos.forced || (sState.lastWasCapture && pos.hasCaptures) || ("opening".equals(pos.phase) && ("book".equals(pos.subphase) || "develop".equals(pos.subphase))));

        double obvious = 0;
        double tricky = 0;
        if (feat.preparedReply) obvious += 1.1;
        if (pos.forced) obvious += 2.3;
        if (pos.numLegal <= 3) obvious += 0.95;
        if (sState.lastWasCapture && pos.hasCaptures) obvious += 1.2;
        if (sState.lastWasCapture && pos.hasCaptures && pos.numLegal <= 10) obvious += 0.75;
        if (sState.lastWasCheck && pos.numLegal <= 4) obvious += 0.9;
        if ("opening".equals(pos.phase) && "book".equals(pos.subphase)) obvious += 1.8;
        if ("opening".equals(pos.phase) && "develop".equals(pos.subphase)) obvious += 0.62;
        if ("endgame".equals(pos.phase) && "technical".equals(pos.subphase)) obvious += 0.74;
        if (moveNum <= 4 && sState._openingFamiliar && "opening".equals(pos.phase)) obvious += 0.55;
        if (sState._humanRelease > 0 && !pos.complex && !pos.tactical && !pos.forced) obvious += 0.52;
        if (!Double.isNaN(scoreChange) && scoreChange > 160) obvious += isRapid ? 0.16 : 0.12;

        if (pos.tactical) tricky += 0.82;
        if (pos.complex) tricky += 1.12;
        if (!sState.lastWasCapture && pos.hasCaptures && pos.numLegal > 6) tricky += 0.28;
        if (sState.lastWasCheck && pos.numLegal > 4) tricky += 0.48;
        if (pos.quiet && pos.numLegal >= 22) tricky += 0.22;
        if ("middlegame".equals(pos.phase) && pos.numLegal >= 26) tricky += 0.22;
        if (timesSeen >= 1 && !pos.complex && !pos.tactical) obvious += 0.28;
        if (timesSeen >= 2 && !pos.forced) obvious += 0.36;
        if (timesSeen >= 3) obvious += 0.22;
        if (avg(sState.oppThinkHistory) > Math.max(preset.mean * 1.55, 1.0)) tricky += 0.22;
        if (sState.tiltLevel >= 1.6) tricky += 0.16 + sState.tiltLevel * 0.07;
        if (!Double.isNaN(scoreChange) && scoreChange < -180) tricky += isBullet ? 0.20 : (isBlitz ? 0.34 : 0.50);

        if (mySec != null && oppSec != null) {
            double ratio = mySec / Math.max(oppSec, 1.0);
            if (ratio < 0.78) {
                obvious += 0.18;
                tricky = Math.max(0.0, tricky - 0.14);
            } else if (ratio > 1.45) {
                tricky += 0.12;
            }
        }

        if (feat.historyUnsafe) {
            obvious = Math.max(0.0, obvious - (isBullet ? 0.24 : (isBlitz ? 0.34 : 0.44)));
            tricky += isBullet ? 0.10 : (isBlitz ? 0.18 : 0.24);
        }

        feat.obvious = obvious;
        feat.tricky = tricky;
        feat.easy = obvious - tricky;
        feat.hard = tricky - obvious;
        return feat;
    }

    // ── 9. Chuyển Đổi Trạng Thái Tâm Lý Markovian (_humanChooseState dòng 7487-7588) ───
    public static HumanStateCfg chooseHumanState(DelayFeatures meta, Double mySec, double rushSec, boolean isBullet, boolean isBlitz, boolean isRapid) {
        HumanPersona persona = ensureHumanDelayPersona(isBullet, isBlitz, isRapid);
        String cur = sState._humanState != null ? sState._humanState : "stable";
        String desired = cur;
        boolean force = false;
        double panicGate = Math.min(rushSec * 0.68, isBullet ? 5.7 : (isBlitz ? 6.2 : 6.8));

        if (mySec != null && mySec <= panicGate) {
            desired = "panic";
            force = true;
        } else if (meta.preparedReply && meta.obvious >= 1.2) {
            desired = "flow";
        } else if (meta.hard >= 1.35 || sState.tiltLevel >= 2.2) {
            desired = (meta.hard >= 2.0 || sState.tiltLevel >= 3.0) ? "uncertain" : "careful";
            if (sState._lastScoreChange < -220) force = true;
        } else if (sState._humanRelease > 0 && meta.easy >= 0.6) {
            desired = "flow";
        } else if (sState._lastScoreChange > 180 && meta.hard < 0.8) {
            desired = "convert";
        } else if (meta.easy >= 1.3) {
            desired = RANDOM.nextDouble() < 0.72 ? "flow" : "stable";
        } else {
            desired = "stable";
        }

        if (meta.historyUnsafe && ("flow".equals(desired) || "convert".equals(desired))) {
            desired = RANDOM.nextDouble() < 0.65 ? "stable" : "careful";
        }

        int holdLeft = sState._humanStateHold;
        if (holdLeft <= 0 || force || "panic".equals(desired) || "uncertain".equals(desired) || (!desired.equals(cur) && RANDOM.nextDouble() < 0.56)) {
            String[] states;
            double[] weights;
            if ("flow".equals(desired)) {
                states = new String[] { "flow", "stable", "convert" };
                weights = new double[] { 0.68 + persona.snapBias, 0.24, 0.08 };
            } else if ("convert".equals(desired)) {
                states = new String[] { "convert", "flow", "stable" };
                weights = new double[] { 0.58, 0.28, 0.14 };
            } else if ("careful".equals(desired)) {
                states = new String[] { "careful", "stable", "uncertain" };
                weights = new double[] { 0.58, 0.26, 0.16 };
            } else if ("uncertain".equals(desired)) {
                states = new String[] { "uncertain", "careful", "stable" };
                weights = new double[] { 0.62, 0.24, 0.14 };
            } else if ("panic".equals(desired)) {
                states = new String[] { "panic", "flow" };
                weights = new double[] { 0.82, 0.18 };
            } else {
                states = new String[] { "stable", "flow", "careful" };
                weights = new double[] { 0.64, 0.20, 0.16 };
            }

            String picked = weightedPick(states, weights);
            sState._humanState = picked;
            HumanStateCfg cfg = humanStateCfg(picked);
            sState._humanStateHold = cfg.holdBase + (cfg.holdVar > 0 ? RANDOM.nextInt(cfg.holdVar + 1) : 0) + Math.max(0, persona.holdBase - 2);
            sState._humanStateReason = desired;
        } else {
            sState._humanStateHold = holdLeft - 1;
        }
        return humanStateCfg(sState._humanState != null ? sState._humanState : "stable");
    }

    // ── 10. Độ Lệch Nhịp Cả Trận (_humanSessionDrift dòng 7640-7669) ─────────────
    public static class SessionDrift {
        public final double mult;
        public final String tag;
        public SessionDrift(double mult, String tag) { this.mult = mult; this.tag = tag; }
    }

    public static SessionDrift computeSessionDrift(PositionFeatures pos, Double mySec, double rushSec, boolean isBullet, boolean isBlitz, boolean isRapid) {
        int hold = sState._sessionDriftHold;
        if (hold <= 0 || pos.complex || ("opening".equals(pos.phase) && sState.moveNum <= 6) || (sState._reactionCarry > 0 && RANDOM.nextDouble() < 0.28)) {
            double base = 0.96 + RANDOM.nextDouble() * 0.10;
            String tag = "base";
            if (mySec != null && mySec > rushSec * 1.6 && RANDOM.nextDouble() < (isRapid ? 0.24 : 0.18)) {
                base *= 1.03 + RANDOM.nextDouble() * 0.08;
                tag = "patient";
            } else if (mySec != null && mySec < rushSec * 1.15) {
                base *= 0.92 + RANDOM.nextDouble() * 0.05;
                tag = "urgent";
            } else if ("opening".equals(pos.phase)) {
                base *= 0.95 + RANDOM.nextDouble() * 0.05;
                tag = "familiar";
            } else if ("endgame".equals(pos.phase) && "technical".equals(pos.subphase)) {
                base *= 0.98 + RANDOM.nextDouble() * 0.06;
                tag = "technical";
            } else if (RANDOM.nextDouble() < 0.20) {
                base *= 1.0 + RANDOM.nextDouble() * 0.10;
                tag = "careful";
            }
            sState._sessionDrift = clamp(base, isBullet ? 0.88 : 0.90, isRapid ? 1.16 : 1.12);
            sState._sessionDriftHold = 2 + RANDOM.nextInt(pos.complex ? 2 : 4);
            sState._sessionDriftTag = tag;
        } else {
            sState._sessionDriftHold = hold - 1;
        }
        return new SessionDrift(sState._sessionDrift, sState._sessionDriftTag != null ? sState._sessionDriftTag : "base");
    }

    // ── 11. Phản Ứng Đối Thủ (_humanOpponentReaction dòng 7670-7724) ─────────────
    public static class OpponentReaction {
        public double mult = 1.0;
        public double extraSec = 0.0;
        public double snapBoost = 0.0;
        public double investBoost = 0.0;
        public String tag = "none";
    }

    public static OpponentReaction computeOpponentReaction(PositionFeatures pos, DelayFeatures meta, TCPreset preset, Double mySec, Double oppSec, double rushSec, boolean isBullet, boolean isBlitz, boolean isRapid) {
        OpponentReaction r = new OpponentReaction();
        boolean oppFast = sState.oppThinkSec != null && sState.oppThinkSec < Math.max(0.28, preset.mean * 0.55);
        boolean oppLong = sState.oppThinkSec != null && sState.oppThinkSec > Math.max(0.90, preset.mean * 1.65);
        double scoreSwing = sState._lastScoreChange;

        if (sState.lastWasCapture && pos.hasCaptures && pos.numLegal <= 10) {
            r.mult *= isBullet ? (0.42 + RANDOM.nextDouble() * 0.10) : (isBlitz ? (0.48 + RANDOM.nextDouble() * 0.12) : (0.56 + RANDOM.nextDouble() * 0.14));
            r.snapBoost += isBullet ? 0.14 : 0.10;
            r.tag = "recapture";
        } else if (sState.lastWasCheck) {
            if (pos.numLegal <= 4 || pos.forced) {
                r.mult *= isBullet ? (0.55 + RANDOM.nextDouble() * 0.12) : (isBlitz ? (0.64 + RANDOM.nextDouble() * 0.14) : (0.72 + RANDOM.nextDouble() * 0.16));
                r.snapBoost += 0.08;
                r.tag = "forced_reply";
            } else {
                r.mult *= 1.05 + RANDOM.nextDouble() * 0.12;
                r.extraSec += isBullet ? (0.02 + RANDOM.nextDouble() * 0.05) : (isBlitz ? (0.05 + RANDOM.nextDouble() * 0.10) : (0.08 + RANDOM.nextDouble() * 0.18));
                r.investBoost += 0.04;
                r.tag = "check_pause";
            }
        } else if (scoreSwing > 130 && meta.easy > 0.15) {
            r.mult *= isBullet ? (0.54 + RANDOM.nextDouble() * 0.14) : (isBlitz ? (0.60 + RANDOM.nextDouble() * 0.14) : (0.68 + RANDOM.nextDouble() * 0.16));
            r.snapBoost += isBullet ? 0.16 : (isBlitz ? 0.12 : 0.08);
            r.tag = "blunder_seen";
        } else if (oppFast && (meta.easy > 0.30 || meta.preparedReply)) {
            r.mult *= isBullet ? (0.62 + RANDOM.nextDouble() * 0.10) : (isBlitz ? (0.68 + RANDOM.nextDouble() * 0.12) : (0.74 + RANDOM.nextDouble() * 0.12));
            r.snapBoost += 0.08;
            r.tag = "fast_reply";
        } else if (oppLong && (meta.hard > 0.35 || pos.complex || pos.tactical)) {
            r.mult *= 1.06 + RANDOM.nextDouble() * 0.16;
            r.extraSec += isBullet ? (0.02 + RANDOM.nextDouble() * 0.06) : (isBlitz ? (0.06 + RANDOM.nextDouble() * 0.14) : (0.10 + RANDOM.nextDouble() * 0.24));
            r.investBoost += isRapid ? 0.10 : 0.06;
            r.tag = "respect_pause";
        } else if (oppLong && !pos.forced && pos.numLegal >= 14) {
            r.mult *= 1.03 + RANDOM.nextDouble() * 0.10;
            r.extraSec += isBullet ? (0.01 + RANDOM.nextDouble() * 0.03) : (isBlitz ? (0.03 + RANDOM.nextDouble() * 0.08) : (0.05 + RANDOM.nextDouble() * 0.14));
            r.tag = "odd_move";
        }

        if (oppSec != null && oppSec < 6.0 && mySec != null && mySec > oppSec * 1.1) {
            r.mult *= 0.78 + RANDOM.nextDouble() * 0.10;
            if ("none".equals(r.tag)) r.tag = "press_clock";
        }
        sState._reactionTag = r.tag;
        return r;
    }

    // ── 12. Phân Phối Jitter & Suy Nghĩ Sinh Học (_humanJitterSec dòng 7589-7639) ───
    public static class JitterResult {
        public final double sec;
        public final String mode;
        public JitterResult(double sec, String mode) { this.sec = sec; this.mode = mode; }
    }

    public static JitterResult computeHumanJitter(double baseSec, HumanPersona persona, HumanStateCfg state, DelayFeatures meta, PositionFeatures pos, OpponentReaction reaction, TCPreset preset, Double mySec, double rushSec, boolean isBullet, boolean isBlitz, boolean isRapid) {
        if (reaction == null) reaction = new OpponentReaction();
        boolean enough = mySec == null || mySec > rushSec * (isRapid ? 1.8 : (isBlitz ? 1.55 : 1.35));
        double easy = meta.easy;
        double hard = meta.hard;
        double sec = baseSec;
        String mode = "normal";

        boolean snapSafe = pos.forced || meta.preparedReply || ("opening".equals(pos.phase) && ("book".equals(pos.subphase) || "develop".equals(pos.subphase))) || (!meta.historyUnsafe && easy >= (isBullet ? 1.55 : (isBlitz ? 1.70 : 1.90))) || (mySec != null && mySec <= rushSec * 1.02);
        boolean stableClock = mySec == null || mySec > (isBullet ? 14 : (isBlitz ? 20 : 35));

        double snapChance = clamp(0.05 + Math.max(0.0, easy) * 0.12 + persona.snapBias + state.snap + (meta.preparedReply ? 0.22 : 0.0) + reaction.snapBoost + (sState._humanRelease > 0 ? 0.08 : 0.0), 0.02, isBullet ? 0.58 : (isBlitz ? 0.46 : 0.36));
        if (!snapSafe) {
            snapChance *= stableClock ? (isBullet ? 0.42 : (isBlitz ? 0.36 : 0.30)) : (isBullet ? 0.62 : (isBlitz ? 0.54 : 0.46));
        }
        if (sState._humanRelease > 0 && easy > 0.45) {
            snapChance += isBullet ? 0.08 : (isBlitz ? 0.06 : 0.05);
        }
        if (sState._humanFastChain >= 2 && easy > 0.55) {
            snapChance += isBullet ? 0.10 : (isBlitz ? 0.08 : 0.06);
        }
        snapChance = clamp(snapChance, 0.02, isBullet ? 0.72 : (isBlitz ? 0.58 : 0.46));
        if (meta.historyUnsafe) snapChance *= 0.30;

        double investBase = "middlegame".equals(pos.phase) ? 0.15 : 0.06;
        double maxInvest = enough ? ("middlegame".equals(pos.phase) ? (isRapid ? 0.66 : (isBlitz ? 0.56 : 0.38)) : (isRapid ? 0.48 : (isBlitz ? 0.38 : 0.24))) : 0.12;
        double investChance = clamp(investBase + Math.max(0.0, hard) * 0.22 + persona.investBias + state.invest + reaction.investBoost, 0.01, maxInvest);

        if (RANDOM.nextDouble() < snapChance) {
            mode = "snap";
            sec *= isBullet ? (0.22 + RANDOM.nextDouble() * 0.24) : (isBlitz ? (0.30 + RANDOM.nextDouble() * 0.24) : (0.40 + RANDOM.nextDouble() * 0.22));
        } else {
            double sigmaBase = "middlegame".equals(pos.phase) ? (isBullet ? 0.56 : (isBlitz ? 0.66 : 0.72)) : (isBullet ? 0.34 : (isBlitz ? 0.42 : 0.48));
            double sigma = sigmaBase + persona.wobble * 0.34 + Math.max(0.0, hard) * 0.10;
            sec = lognormal(Math.max(preset.lo, sec), sigma);
            if (enough && RANDOM.nextDouble() < investChance) {
                mode = "invest";
                if ("middlegame".equals(pos.phase)) {
                    sec *= isBullet ? (1.70 + RANDOM.nextDouble() * 0.95) : (isBlitz ? (1.95 + RANDOM.nextDouble() * 1.45) : (2.30 + RANDOM.nextDouble() * 2.00));
                } else {
                    sec *= isBullet ? (1.26 + RANDOM.nextDouble() * 0.46) : (isBlitz ? (1.36 + RANDOM.nextDouble() * 0.72) : (1.48 + RANDOM.nextDouble() * 1.05));
                }
            }
        }

        double hiccupChance = clamp(state.hiccup + persona.wobble * 0.16 + (easy > 0.8 ? 0.03 : 0.0), 0.01, 0.26);
        if (!"invest".equals(mode) && enough && easy > 0.45 && RANDOM.nextDouble() < hiccupChance) {
            mode = "hiccup";
            sec += isBullet ? (0.08 + RANDOM.nextDouble() * 0.16) : (isBlitz ? (0.18 + RANDOM.nextDouble() * 0.40) : (0.30 + RANDOM.nextDouble() * 0.90));
        }

        if (("invest".equals(mode) || hard > 1.0) && RANDOM.nextDouble() < (0.12 + persona.wobble * 0.10 + ("flow".equals(state.name) ? 0.06 : 0.0))) {
            sec *= 0.76 + RANDOM.nextDouble() * 0.16;
            if ("invest".equals(mode)) mode = "decide";
        }

        if (sState.moveNum >= sState._clockGlanceNextMove && enough && RANDOM.nextDouble() < 0.70) {
            sec += isBullet ? (0.02 + RANDOM.nextDouble() * 0.08) : (isBlitz ? (0.05 + RANDOM.nextDouble() * 0.18) : (0.10 + RANDOM.nextDouble() * 0.34));
            sState._clockGlanceNextMove = sState.moveNum + sState._clockGlanceInterval + RANDOM.nextInt(3);
        }

        return new JitterResult(sec, mode);
    }

    // ── 13. Do Dự Nhẹ Vi Mô (_humanMicroHesitationSec dòng 7725-7750) ────────────
    public static double computeMicroHesitation(String mode, PositionFeatures pos, DelayFeatures meta, OpponentReaction reaction, TCPreset preset, Double mySec, double rushSec, boolean isBullet, boolean isBlitz, boolean isRapid) {
        if (mySec != null && mySec <= rushSec * 1.08) return 0.0;
        if (pos.forced || "recapture".equals(reaction.tag) || "blunder_seen".equals(reaction.tag)) return 0.0;

        double p = 0.08 + sState._microHesitationBias * 0.34;
        if ("snap".equals(mode)) p += 0.06;
        if (meta.easy > 0.45) p += 0.05;
        if (meta.hard > 0.85) p += 0.04;
        if (pos.complex || pos.tactical) p += 0.03;
        if (sState._reactionCarry > 0) p += 0.05;
        p = clamp(p, 0.04, isBullet ? 0.24 : (isBlitz ? 0.28 : 0.34));
        if (RANDOM.nextDouble() >= p) return 0.0;

        double lo = isBullet ? 0.02 : (isBlitz ? 0.04 : 0.07);
        double hi = isBullet ? 0.08 : (isBlitz ? 0.14 : 0.24);
        if ("snap".equals(mode)) {
            lo *= 0.80;
            hi *= 0.90;
        } else if ("invest".equals(mode) || "decide".equals(mode)) {
            lo *= 1.10;
            hi *= 1.20;
        }
        if ("opening".equals(pos.phase) && "book".equals(pos.subphase)) {
            lo *= 0.70;
            hi *= 0.75;
        }
        return lo + RANDOM.nextDouble() * Math.max(0.01, hi - lo);
    }

    // ── 14. Phá Vỡ Độ Đơn Điệu (_humanBreakFactory dòng 7751-7759) ───────────────
    public static double applyHumanBreakFactory(double sec, double budget, TCPreset preset, Double mySec, double rushSec) {
        List<Double> recent = sState._humanRecentDelay;
        if (recent.size() < 3) return sec;
        double a = avg(recent);
        if (a <= 0) return sec;
        double maxDiff = 0.0;
        for (double v : recent) maxDiff = Math.max(maxDiff, Math.abs(v - a));
        if (maxDiff > a * 0.18) return sec;
        if (mySec != null && mySec <= rushSec * 1.25) return sec;
        return sec * (RANDOM.nextDouble() < 0.60 ? (0.70 + RANDOM.nextDouble() * 0.14) : (1.18 + RANDOM.nextDouble() * 0.18));
    }

    // ── 15. Chốt Hạ Độ Trễ 8 Kịch Bản Hoàn Chỉnh (_humanFinalizeDelay dòng 7760-8120) ─
    public static double finalizeHumanDelay(double sec, double budget, PositionFeatures pos, TCPreset preset, Double mySec, Double oppSec, double rushSec, boolean isBullet, boolean isBlitz, boolean isRapid, int tcIdx, List<BoardUtil.LegalMove> legal, double scoreChange, String chosenMove, String playstyle) {
        double floor = preset.lo;
        double cap = preset.hi;
        boolean isBulletTc = tcIdx <= 2;
        boolean isShortBlitz = tcIdx == 3 || tcIdx == 4;
        boolean isLongBlitz = tcIdx == 5;
        boolean isStandardRapid = tcIdx == 6;

        String chosenTo = (chosenMove != null && chosenMove.length() >= 4) ? chosenMove.substring(2, 4) : "";
        String chosenFrom = (chosenMove != null && chosenMove.length() >= 2) ? chosenMove.substring(0, 2) : "";

        // Kiểm tra Recapture chuẩn xác 100% dòng 7773-7783
        boolean isDirectRecapture = !chosenTo.isEmpty() && !sState.lastOppTo.isEmpty() && chosenTo.equals(sState.lastOppTo) &&
                (sState.lastOppWasCapture || (legal != null && legalHasCapture(legal, chosenFrom, chosenTo)));
        boolean hasDirectRecapture = isDirectRecapture ||
                ((sState.lastOppWasCapture || sState.lastWasCapture) && (legal != null && legalHasOppToCapture(legal, sState.lastOppTo)));

        boolean isOnlyMove = legal != null && legal.size() == 1;
        boolean isUnderCheck = sState.lastOppWasCheck || pos.isCheck;
        boolean isOpening = "opening".equals(pos.phase) && ("book".equals(pos.subphase) || "develop".equals(pos.subphase) || sState.moveNum <= 8);
        boolean isQueenTradeOrSac = legal != null && legalHasQueenTrade(legal, chosenFrom, chosenTo);
        boolean hasSuddenEvalSwing = Math.abs(scoreChange) >= 90;
        boolean isSharpTactics = (pos.tactical && pos.complex) || isQueenTradeOrSac || hasSuddenEvalSwing;

        boolean inDeepCooldown = sState._deepThinkCooldown > 0;
        if (inDeepCooldown) sState._deepThinkCooldown--;
        boolean isDeepThink = !isOnlyMove && !isOpening && !inDeepCooldown && mySec != null && mySec > rushSec * 1.25 &&
                ((isUnderCheck && pos.numLegal > 2) || isSharpTactics);
        if (isDeepThink) sState._deepThinkCooldown = 2;

        String scenarioLabel = "Bình thường";

        // Kịch bản 1: Nước đi duy nhất (Only move)
        if (isOnlyMove) {
            scenarioLabel = "⚡ Nước duy nhất (Only move)";
            if (isBulletTc) {
                floor = 0.14; cap = 0.28; sec = lognormal(0.20, 0.14);
            } else if (isShortBlitz) {
                floor = 0.20; cap = 0.42; sec = lognormal(0.28, 0.15);
            } else if (isLongBlitz) {
                floor = 0.28; cap = 0.55; sec = lognormal(0.38, 0.16);
            } else {
                floor = 0.38; cap = 0.75; sec = lognormal(0.52, 0.18);
            }
        }
        // Kịch bản 2: Bị chiếu bất ngờ (Check hesitation)
        else if (isUnderCheck && (mySec == null || mySec > (isBulletTc ? 5.5 : 7.0))) {
            scenarioLabel = "🛡️ Bị chiếu: Đánh giá & phòng thủ";
            if (isBulletTc) {
                floor = 0.75; cap = 1.60; sec = lognormal(1.15, 0.18);
            } else if (isShortBlitz) {
                floor = 1.35; cap = 2.80; sec = lognormal(1.95, 0.20);
            } else if (isLongBlitz) {
                floor = 1.80; cap = 3.80; sec = lognormal(2.60, 0.22);
            } else if (isStandardRapid) {
                floor = 3.20; cap = 7.00; sec = lognormal(4.80, 0.22);
            } else {
                floor = 4.50; cap = 9.50; sec = lognormal(6.50, 0.24);
            }
        }
        // Kịch bản 3: Ăn lại quân tức thì (Recapture)
        else if (hasDirectRecapture || "recapture".equals(sState._reactionTag)) {
            scenarioLabel = "⚡ Ăn quân tức thì (Recapture)";
            if (isBulletTc) {
                floor = 0.35; cap = 0.70; sec = lognormal(0.48, 0.15);
            } else if (isShortBlitz) {
                floor = 0.75; cap = 1.35; sec = lognormal(0.95, 0.16);
            } else if (isLongBlitz) {
                floor = 0.95; cap = 1.65; sec = lognormal(1.20, 0.17);
            } else if (isStandardRapid) {
                floor = 1.20; cap = 2.40; sec = lognormal(1.55, 0.18);
            } else {
                floor = 1.80; cap = 3.20; sec = lognormal(2.20, 0.20);
            }
        }
        // Kịch bản 4: Chuyển giai đoạn Khai cuộc ➔ Trung cuộc
        else if ("middlegame".equals(pos.phase) && !sState._phaseShiftOpeningDone && sState.moveNum >= 7 && (mySec == null || mySec > rushSec * 1.5)) {
            sState._phaseShiftOpeningDone = true;
            scenarioLabel = "🎯 Chuyển giai đoạn: Lập kế hoạch trung cuộc";
            if (isBulletTc) {
                floor = 1.20; cap = 2.40; sec = lognormal(1.70, 0.18);
            } else if (isShortBlitz) {
                floor = 2.60; cap = 4.80; sec = lognormal(3.50, 0.20);
            } else if (isLongBlitz) {
                floor = 3.50; cap = 6.80; sec = lognormal(4.80, 0.22);
            } else if (isStandardRapid) {
                floor = 7.00; cap = 14.0; sec = lognormal(9.80, 0.24);
            } else {
                floor = 12.0; cap = 22.0; sec = lognormal(16.0, 0.26);
            }
        }
        // Kịch bản 5: Hai bên vừa đổi Hậu ➔ Chuyển sang tàn cuộc / trung cuộc không Hậu
        else if (pos.queens == 0 && (sState._prevQueensCount != null && sState._prevQueensCount > 0) && !sState._phaseShiftQueenTradeDone && (mySec == null || mySec > rushSec * 1.5)) {
            sState._phaseShiftQueenTradeDone = true;
            scenarioLabel = "👑 Hậu rời sân: Định hình tàn cuộc";
            if (isBulletTc) {
                floor = 1.10; cap = 2.20; sec = lognormal(1.50, 0.18);
            } else if (isShortBlitz) {
                floor = 2.20; cap = 4.20; sec = lognormal(3.10, 0.20);
            } else if (isLongBlitz) {
                floor = 3.00; cap = 5.80; sec = lognormal(4.20, 0.22);
            } else if (isStandardRapid) {
                floor = 5.50; cap = 11.0; sec = lognormal(7.80, 0.24);
            } else {
                floor = 9.00; cap = 18.0; sec = lognormal(12.5, 0.26);
            }
        }
        // Kịch bản 6: Khai cuộc bài bản
        else if (isOpening) {
            scenarioLabel = "📖 Khai cuộc bài bản";
            boolean isEarlyBook = "book".equals(pos.subphase) || sState.moveNum <= 5;
            if (isBulletTc) {
                floor = 0.40; cap = 0.85; sec = lognormal(0.58, 0.16);
            } else if (isShortBlitz) {
                floor = isEarlyBook ? 0.85 : 1.40;
                cap = isEarlyBook ? 1.60 : 2.80;
                sec = lognormal(isEarlyBook ? 1.10 : 1.95, 0.18);
            } else if (isLongBlitz) {
                floor = isEarlyBook ? 1.10 : 2.00;
                cap = isEarlyBook ? 2.20 : 4.00;
                sec = lognormal(isEarlyBook ? 1.50 : 2.80, 0.19);
            } else if (isStandardRapid) {
                floor = isEarlyBook ? 1.50 : 3.00;
                cap = isEarlyBook ? 3.20 : 6.50;
                sec = lognormal(isEarlyBook ? 2.20 : 4.20, 0.21);
            } else {
                floor = isEarlyBook ? 2.50 : 4.50;
                cap = isEarlyBook ? 5.00 : 10.0;
                sec = lognormal(isEarlyBook ? 3.80 : 6.80, 0.24);
            }
        }
        // Kịch bản 7: Suy nghĩ sâu then chốt
        else if (isDeepThink) {
            scenarioLabel = "🧠 Suy nghĩ sâu then chốt";
            if (isBulletTc) {
                floor = 1.40; cap = 2.40; sec = lognormal(1.80, 0.20);
            } else if (isShortBlitz) {
                floor = 3.20; cap = (mySec != null && mySec > 60) ? 5.80 : 4.20;
                sec = lognormal(4.20, 0.20);
            } else if (isLongBlitz) {
                floor = 4.50; cap = (mySec != null && mySec > 120) ? 8.50 : 6.00;
                sec = lognormal(6.00, 0.22);
            } else if (isStandardRapid) {
                if (mySec != null && mySec > 300) {
                    floor = 10.0; cap = 18.0; sec = lognormal(13.5, 0.24);
                } else if (mySec != null && mySec > 120) {
                    floor = 7.00; cap = 12.0; sec = lognormal(9.00, 0.22);
                } else {
                    floor = 4.00; cap = 7.00; sec = lognormal(5.20, 0.20);
                }
            } else {
                floor = 15.0; cap = (mySec != null && mySec > 400) ? 35.0 : 25.0;
                sec = lognormal(22.0, 0.28);
            }
        }
        // Kịch bản 8: Trung cuộc bình thường với 3-band tempo rolls
        else {
            scenarioLabel = "♟️ Trung cuộc";
            double tempoRoll = RANDOM.nextDouble();
            if (isBulletTc) {
                if (tempoRoll < 0.30) { floor = 0.65; cap = 1.10; sec = lognormal(0.85, 0.18); }
                else if (tempoRoll < 0.80) { floor = 0.90; cap = 1.60; sec = lognormal(1.20, 0.22); }
                else { floor = 1.40; cap = 2.20; sec = lognormal(1.75, 0.22); }
            } else if (isShortBlitz) {
                if (tempoRoll < 0.30) { floor = 1.50; cap = 2.60; sec = lognormal(2.00, 0.20); }
                else if (tempoRoll < 0.80) { floor = 2.60; cap = 4.50; sec = lognormal(3.40, 0.24); }
                else { floor = 4.20; cap = 6.80; sec = lognormal(5.20, 0.25); }
            } else if (isLongBlitz) {
                if (tempoRoll < 0.30) { floor = 2.20; cap = 3.80; sec = lognormal(2.90, 0.22); }
                else if (tempoRoll < 0.80) { floor = 3.80; cap = 6.50; sec = lognormal(4.80, 0.25); }
                else { floor = 6.00; cap = 9.50; sec = lognormal(7.20, 0.26); }
            } else if (isStandardRapid) {
                if (tempoRoll < 0.30) { floor = 3.80; cap = 6.50; sec = lognormal(4.80, 0.24); }
                else if (tempoRoll < 0.80) { floor = 6.50; cap = 11.0; sec = lognormal(8.50, 0.26); }
                else { floor = 10.5; cap = 16.0; sec = lognormal(13.0, 0.28); }
            } else {
                if (tempoRoll < 0.30) { floor = 6.00; cap = 11.0; sec = lognormal(8.00, 0.25); }
                else if (tempoRoll < 0.80) { floor = 11.0; cap = 20.0; sec = lognormal(15.0, 0.28); }
                else { floor = 18.0; cap = 28.0; sec = lognormal(22.0, 0.30); }
            }
        }

        // Branching Entropy Factor dòng 8054-8065
        if (legal != null && !legal.isEmpty() && !isOnlyMove) {
            int branchCount = legal.size();
            if (branchCount >= 32) {
                double branchBonus = clamp((branchCount - 32) * 0.015, 0.0, 0.28);
                sec *= (1.0 + branchBonus);
                cap *= (1.0 + branchBonus);
            } else if (branchCount <= 10) {
                double branchDiscount = clamp((10 - branchCount) * 0.025, 0.0, 0.22);
                sec *= (1.0 - branchDiscount);
                floor = Math.max(preset.lo, floor * (1.0 - branchDiscount));
            }
        }

        // Streak Fatigue Factor dòng 8068-8077
        int moveNumber = sState.moveNum;
        if (moveNumber >= 38 && (mySec == null || mySec > rushSec * 1.3)) {
            double fatigueRoll = RANDOM.nextDouble();
            if (fatigueRoll < 0.14) {
                sec *= (1.25 + RANDOM.nextDouble() * 0.35);
                scenarioLabel += " (Khựng do mệt)";
            } else if (fatigueRoll > 0.82) {
                sec *= (0.82 + RANDOM.nextDouble() * 0.12);
            }
        }

        // Điều chỉnh Playstyle dòng 8079-8090
        if ("aggressive".equalsIgnoreCase(playstyle)) {
            if (isSharpTactics || isUnderCheck) {
                sec *= 0.84;
                cap *= 0.88;
            } else {
                sec *= 1.05;
            }
        } else if ("solid".equalsIgnoreCase(playstyle)) {
            sec *= 1.15;
            cap *= 1.16;
            floor *= 1.08;
        }

        // Bảo vệ Cận Giờ (Time Trouble) dòng 8093-8116
        if (mySec != null) {
            if (mySec <= 15.0) {
                floor = Math.min(floor, isBulletTc ? 0.22 : 0.42);
                cap = Math.min(cap, isBulletTc ? 0.70 : 1.05);
                sec = Math.min(sec, isBulletTc ? 0.60 : 0.95);
                scenarioLabel += " (Chống flag)";
            }
            if (mySec <= 5.0) {
                floor = preset.lo;
                cap = Math.min(cap, isBulletTc ? 0.28 : 0.50);
                sec = Math.min(sec, isBulletTc ? 0.22 : 0.40);
                scenarioLabel = "⚠️ Panic Mode (Chống rụng kim)";
            } else {
                double hardPct = "middlegame".equals(pos.phase) ? (isBulletTc ? 0.145 : (isBlitz ? 0.205 : 0.27)) : (isBulletTc ? 0.095 : (isBlitz ? 0.135 : 0.18));
                cap = Math.min(cap, Math.max(floor, mySec * hardPct));
                if (mySec < rushSec * 1.2) {
                    cap = Math.min(cap, Math.max(floor, mySec * ("middlegame".equals(pos.phase) ? 0.10 : 0.07)));
                }
            }
            if (oppSec != null && oppSec < 10.0 && mySec > oppSec * 1.5) {
                sec *= 0.70;
                cap *= 0.75;
            } else if (oppSec != null && mySec < oppSec * 0.70) {
                cap *= 0.84;
            }
        }

        double finalSec = clamp(sec, floor, Math.max(floor, cap));
        sState._lastScenarioLabel = scenarioLabel;
        return finalSec;
    }

    private static boolean legalHasCapture(List<BoardUtil.LegalMove> legal, String from, String to) {
        for (BoardUtil.LegalMove m : legal) {
            if (m.from.equals(from) && m.to.equals(to) && m.capturedPiece != '.') return true;
        }
        return false;
    }

    private static boolean legalHasOppToCapture(List<BoardUtil.LegalMove> legal, String oppTo) {
        for (BoardUtil.LegalMove m : legal) {
            if (m.capturedPiece != '.' && (oppTo == null || oppTo.isEmpty() || m.to.equals(oppTo))) return true;
        }
        return false;
    }

    private static boolean legalHasQueenTrade(List<BoardUtil.LegalMove> legal, String from, String to) {
        for (BoardUtil.LegalMove m : legal) {
            if (m.from.equals(from) && m.to.equals(to) && (m.capturedPiece == 'q' || m.capturedPiece == 'Q')) return true;
        }
        return false;
    }

    // ── 16. Ghi Nhận Trạng Thái Độ Trễ (_humanCommitDelayState dòng 8121-8148) ───
    public static void commitDelayState(String mode, double thinkSec, double budget, HumanPersona persona) {
        sState._humanRecentDelay.add(thinkSec);
        if (sState._humanRecentDelay.size() > 4) sState._humanRecentDelay.remove(0);

        sState._humanRecentModes.add(mode);
        if (sState._humanRecentModes.size() > 6) sState._humanRecentModes.remove(0);

        if ("snap".equals(mode) || thinkSec < budget * 0.72) {
            sState._humanFastChain++;
        } else {
            sState._humanFastChain = 0;
        }

        if ("invest".equals(mode) || thinkSec > budget * 1.42) {
            sState._humanInvestChain++;
            int relMin = persona.releaseMin;
            int relMax = Math.max(relMin, persona.releaseMax);
            int releaseBonus = relMin + (relMax > relMin ? RANDOM.nextInt(relMax - relMin + 1) : 0);
            sState._humanRelease = Math.max(sState._humanRelease, releaseBonus);
        } else if (sState._humanRelease > 0 && thinkSec < budget * 1.05) {
            sState._humanRelease--;
            sState._humanInvestChain = 0;
        } else {
            sState._humanInvestChain = 0;
        }

        double ms = thinkSec * 1000.0;
        if (ms > budget * 1320.0) {
            sState.timeDebtMs = Math.min(3200.0, sState.timeDebtMs + (ms - budget * 1000.0) * 0.62);
        } else {
            sState.timeDebtMs = Math.max(0.0, sState.timeDebtMs - 320.0);
        }

        sState._myTempoEma = ema(sState._myTempoEma, thinkSec, 0.28);
        sState._humanLastBudget = budget;

        if (!"none".equals(sState._reactionTag)) {
            sState._reactionCarry = Math.max(0, sState._reactionCarry + (thinkSec > budget * 1.15 ? 1 : 0));
        } else {
            sState._reactionCarry = Math.max(0, sState._reactionCarry - 1);
        }
    }

    // ── 17. Ghi Nhận Nước Đi Của Đối Thủ & Người Chơi ───────────────────────────
    public static void recordOpponentMove(String oppMoveUci, String currentFen, Activity activity, boolean userWhite) {
        if (oppMoveUci == null || oppMoveUci.length() < 4) return;
        Double oppNow = null;
        if (activity != null) {
            LiveClockInfo clk = detectLiveClocks(activity, userWhite);
            if (clk.oppSec >= 0) oppNow = clk.oppSec;
        }

        if (sState.oppClockBefore != null && oppNow != null) {
            double used = sState.oppClockBefore - oppNow;
            if (used >= 0.05 && used <= 180.0) {
                sState.oppThinkSec = used;
                sState.oppThinkHistory.add(used);
                sState._oppTempoEma = ema(sState._oppTempoEma, used, 0.32);
                if (sState.oppThinkHistory.size() > 3) {
                    sState.oppThinkHistory.remove(0);
                }
            }
        }
        sState.oppClockBefore = null;

        sState.lastOppMoveUci = oppMoveUci;
        sState.lastOppFrom = oppMoveUci.substring(0, 2);
        sState.lastOppTo = oppMoveUci.substring(2, 4);

        if (currentFen != null) {
            char[] board = BoardUtil.parseBoard(currentFen);
            sState.lastOppWasCheck = BoardUtil.isKingInCheck(board, userWhite);
            char target = StockfishExtension.getPieceAtSquare(currentFen, sState.lastOppTo);
            sState.lastOppWasCapture = (target != '.' && target != ' ');
        }
        sState.lastWasCapture = sState.lastOppWasCapture;
        sState.lastWasCheck = sState.lastOppWasCheck;
    }

    public static void recordMyMove(String myMoveUci, String currentFen, Activity activity, boolean userWhite) {
        sState.moveNum++;
        if (currentFen != null) {
            String[] parts = currentFen.trim().split("\\s+");
            String boardKey = parts[0] + (parts.length > 1 ? parts[1] : "");
            sState.posHistory.add(boardKey);
            if (sState.posHistory.size() > 24) sState.posHistory.remove(0);
            sState._familiarFenMap.put(boardKey, sState._familiarFenMap.getOrDefault(boardKey, 0) + 1);
            sState._lastBoardKey = boardKey;
        }
        if (activity != null) {
            LiveClockInfo clk = detectLiveClocks(activity, userWhite);
            if (clk.oppSec >= 0) {
                sState.oppClockBefore = clk.oppSec;
            }
        }
    }

    public static void recordMyMove(String myMoveUci, String currentFen) {
        recordMyMove(myMoveUci, currentFen, null, true);
    }

    // ── 18. Trích Xuất Time Control & Live Clock 100% Native ─────────────────────
    public static class LiveClockInfo {
        public double mySec = -1;
        public double oppSec = -1;
        public int initBaseSec = -1;
        public int incrementSec = -1;
        public String source = "unknown";
        public boolean isAutoDetected = false;
    }

    private static volatile long sLastWhiteMillis = -1;
    private static volatile long sLastBlackMillis = -1;
    private static volatile long sInitialWhiteMillis = -1;
    private static volatile long sInitialBlackMillis = -1;
    private static volatile String sLastRunningColor = null;
    private static volatile boolean hasNotifiedThisGame = false;

    public static void onClockUpdatedNative(long whiteMillis, long blackMillis, Object runningColor) {
        if (sInitialWhiteMillis < 0 && whiteMillis > 0) {
            sInitialWhiteMillis = whiteMillis;
        }
        if (sInitialBlackMillis < 0 && blackMillis > 0) {
            sInitialBlackMillis = blackMillis;
        }
        sLastWhiteMillis = whiteMillis;
        sLastBlackMillis = blackMillis;
        sLastRunningColor = runningColor != null ? runningColor.toString() : null;
    }

    public static void resetClockNative() {
        sLastWhiteMillis = -1;
        sLastBlackMillis = -1;
        sInitialWhiteMillis = -1;
        sInitialBlackMillis = -1;
        sLastRunningColor = null;
        hasNotifiedThisGame = false;
        activeTcSignature = null;
    }

    public static int matchPresetIndex(int initSec, int incSec) {
        return matchPresetIndex(initSec, incSec, false);
    }

    public static int matchPresetIndex(int initSec, int incSec, boolean fromClockOnly) {
        if (initSec <= 0) return 5;

        int[][] candidates = {
                {0, 60, 0, 18, 0},     // 1m
                {1, 60, 1, 18, 1},     // 1|1
                {2, 120, 1, 30, 1},    // 2|1
                {3, 180, 0, 35, 1},    // 3m
                {4, 180, 2, 35, 2},    // 3|2
                {5, 300, 0, 70, 3},    // 5m
                {6, 600, 0, 140, 8},   // 10m
                {7, 900, 10, 180, 10}, // 15|10
                {8, 1800, 0, 420, 30}  // 30m
        };

        int bestIdx = 5;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int[] cand : candidates) {
            int baseDiff = Math.abs(initSec - cand[1]);
            int incDiff = Math.abs(incSec - cand[2]);
            double score = baseDiff + incDiff * 22.0;

            if (baseDiff > cand[3]) score += (baseDiff - cand[3]) * 3.5;
            if (incDiff > cand[4]) score += (incDiff - cand[4]) * 35.0;

            if (fromClockOnly) {
                if (incSec == 0 && cand[2] > 0) score += (cand[2] >= 10 ? 90 : 28);
                if (initSec < 150 && cand[1] >= 300) score += 200;
                if (initSec > 240 && cand[1] <= 120) score += 160;
            }

            if (score < bestScore) {
                bestScore = score;
                bestIdx = cand[0];
            }
        }
        return bestIdx;
    }

    public static LiveClockInfo detectLiveClocks(Activity activity, boolean userWhite) {
        LiveClockInfo info = new LiveClockInfo();

        // Tầng 0: Bytecode Native Hook từ b.<init> (0ms delay, Zero-Fallback)
        if (sLastWhiteMillis >= 0 && sLastBlackMillis >= 0) {
            info.mySec = (userWhite ? sLastWhiteMillis : sLastBlackMillis) / 1000.0;
            info.oppSec = (userWhite ? sLastBlackMillis : sLastWhiteMillis) / 1000.0;
            long initMillis = userWhite ? sInitialWhiteMillis : sInitialBlackMillis;
            if (initMillis > 0) {
                info.initBaseSec = (int) Math.round(initMillis / 1000.0);
            }
            info.source = "native_clock";
            info.isAutoDetected = true;
            return info;
        }

        if (activity == null) return info;

        // Tầng 1: Native ViewModel Hooking
        try {
            Object vm = resolveRealGameViewModel(activity);
            if (vm != null) {
                Field fClocks = null;
                for (Field f : vm.getClass().getDeclaredFields()) {
                    if (f.getName().contains("clocksState") || f.getName().contains("_clocksState") || "P0".equals(f.getName())) {
                        fClocks = f;
                        break;
                    }
                }
                if (fClocks != null) {
                    fClocks.setAccessible(true);
                    Object stateFlow = fClocks.get(vm);
                    if (stateFlow != null) {
                        Method mVal = stateFlow.getClass().getMethod("getValue");
                        Object clocksState = mVal.invoke(stateFlow);
                        if (clocksState != null) {
                            long[] millis = extractClockMillis(clocksState);
                            if (millis != null && millis[0] >= 0 && millis[1] >= 0) {
                                long wMs = millis[0];
                                long bMs = millis[1];
                                info.mySec = (userWhite ? wMs : bMs) / 1000.0;
                                info.oppSec = (userWhite ? bMs : wMs) / 1000.0;
                                info.source = "auto";
                                info.isAutoDetected = true;
                            }
                        }
                    }
                }

                for (Field f : vm.getClass().getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(vm);
                        if (val != null) {
                            for (Method m : val.getClass().getMethods()) {
                                if ("getGameTime".equals(m.getName()) || "getSecPerGame".equals(m.getName())) {
                                    Object gt = "getGameTime".equals(m.getName()) ? m.invoke(val) : val;
                                    if (gt != null) {
                                        Method mSec = gt.getClass().getMethod("getSecPerGame");
                                        Method mInc = gt.getClass().getMethod("getBonusSecPerMove");
                                        info.initBaseSec = ((Number) mSec.invoke(gt)).intValue();
                                        info.incrementSec = ((Number) mInc.invoke(gt)).intValue();
                                        info.source = "auto";
                                        info.isAutoDetected = true;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            NnvcLogger.w(TAG, "Native clock detection error: " + t.getMessage());
        }

        // Tầng 2: UI View Hierarchy Scanning
        try {
            LiveClockInfo uiInfo = new LiveClockInfo();
            scanClocksFromUi(activity, userWhite, uiInfo);
            if (uiInfo.mySec >= 0 && uiInfo.oppSec >= 0) {
                if (info.mySec >= 0 && info.oppSec >= 0) {
                    info.mySec = Math.min(info.mySec, uiInfo.mySec);
                    info.oppSec = Math.min(info.oppSec, uiInfo.oppSec);
                } else {
                    info.mySec = uiInfo.mySec;
                    info.oppSec = uiInfo.oppSec;
                    info.source = "clock";
                    info.isAutoDetected = true;
                }
                if (info.initBaseSec <= 0 && uiInfo.initBaseSec > 0) {
                    info.initBaseSec = uiInfo.initBaseSec;
                    info.incrementSec = uiInfo.incrementSec;
                }
            }
        } catch (Throwable t) {
            NnvcLogger.w(TAG, "UI clock scan error: " + t.getMessage());
        }

        return info;
    }

    public static long[] extractClockMillis(Object clocksState) {
        if (clocksState == null) return null;
        Class<?> cCls = clocksState.getClass();
        long wMs = -1;
        long bMs = -1;

        try {
            Method mWhite = cCls.getMethod("getWhiteMillis");
            Method mBlack = cCls.getMethod("getBlackMillis");
            wMs = ((Number) mWhite.invoke(clocksState)).longValue();
            bMs = ((Number) mBlack.invoke(clocksState)).longValue();
            if (wMs >= 0 && bMs >= 0) return new long[] { wMs, bMs };
        } catch (Throwable ignored) {}

        try {
            Method mF = cCls.getMethod("f");
            Method mC = cCls.getMethod("c");
            if (mF.getReturnType() == long.class && mC.getReturnType() == long.class) {
                wMs = ((Number) mF.invoke(clocksState)).longValue();
                bMs = ((Number) mC.invoke(clocksState)).longValue();
                if (wMs >= 0 && bMs >= 0) return new long[] { wMs, bMs };
            }
        } catch (Throwable ignored) {}

        try {
            List<Long> longFields = new ArrayList<>();
            for (Field f : cCls.getDeclaredFields()) {
                if (f.getType() == long.class || f.getType() == Long.class) {
                    f.setAccessible(true);
                    Object val = f.get(clocksState);
                    if (val instanceof Number) {
                        longFields.add(((Number) val).longValue());
                    }
                }
            }
            if (longFields.size() >= 2) {
                return new long[] { longFields.get(0), longFields.get(1) };
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static Object resolveRealGameViewModel(Activity activity) {
        if (activity == null) return null;
        Object vm = AutoFindManager.getRealGameViewModel(activity);
        if (vm != null) return vm;
        try {
            Method mX6 = activity.getClass().getMethod("X6");
            return mX6.invoke(activity);
        } catch (Throwable ignored) {}
        try {
            Method mI8 = activity.getClass().getMethod("I8");
            return mI8.invoke(activity);
        } catch (Throwable ignored) {}
        try {
            Field fB = activity.getClass().getDeclaredField("B");
            fB.setAccessible(true);
            Object lazy = fB.get(activity);
            if (lazy != null) {
                return lazy.getClass().getMethod("getValue").invoke(lazy);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static final Pattern CLOCK_PATTERN = Pattern.compile("^(?:(?:(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})(?:\\.(\\d))?)|(?:(\\d{1,2})\\.(\\d)))$");

    private static void scanClocksFromUi(Activity activity, boolean userWhite, LiveClockInfo info) {
        if (activity.getWindow() == null || activity.getWindow().getDecorView() == null) return;
        View root = activity.getWindow().getDecorView();

        List<ViewSnapshot> clockViews = new ArrayList<>();
        findClockTextViews(root, clockViews);

        if (clockViews.size() >= 2) {
            Collections.sort(clockViews, (a, b) -> Integer.compare(a.y, b.y));
            ViewSnapshot top = clockViews.get(0);
            ViewSnapshot bottom = clockViews.get(clockViews.size() - 1);

            double topSec = parseClockText(top.text);
            double bottomSec = parseClockText(bottom.text);

            info.mySec = bottomSec;
            info.oppSec = topSec;
            info.source = "clock";
            info.isAutoDetected = true;

            if (info.initBaseSec <= 0) {
                info.initBaseSec = (int) Math.round(Math.max(topSec, bottomSec));
                info.incrementSec = 0;
            }
        }
    }

    private static class ViewSnapshot {
        final String text;
        final int y;
        ViewSnapshot(String text, int y) { this.text = text; this.y = y; }
    }

    private static void findClockTextViews(View v, List<ViewSnapshot> out) {
        if (v == null || v.getVisibility() != View.VISIBLE) return;
        if (v instanceof TextView) {
            CharSequence cs = ((TextView) v).getText();
            if (cs != null) {
                String str = cs.toString().trim();
                if (CLOCK_PATTERN.matcher(str).matches()) {
                    int[] loc = new int[2];
                    v.getLocationOnScreen(loc);
                    out.add(new ViewSnapshot(str, loc[1]));
                }
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                findClockTextViews(vg.getChildAt(i), out);
            }
        }
    }

    public static double parseClockText(String str) {
        if (str == null) return -1;
        Matcher m = CLOCK_PATTERN.matcher(str.trim());
        if (m.matches()) {
            if (m.group(5) != null) {
                int secs = Integer.parseInt(m.group(5));
                double tenths = m.group(6) != null ? Double.parseDouble("0." + m.group(6)) : 0.0;
                return secs + tenths;
            } else {
                String hoursStr = m.group(1);
                int hours = hoursStr != null ? Integer.parseInt(hoursStr) : 0;
                int mins = Integer.parseInt(m.group(2));
                int secs = Integer.parseInt(m.group(3));
                double tenths = m.group(4) != null ? Double.parseDouble("0." + m.group(4)) : 0.0;
                return hours * 3600 + mins * 60 + secs + tenths;
            }
        }
        return -1;
    }

    // ── 19. Đồng Bộ Hóa & Hiển Thị Popup Time Control ───────────────────────────
    public static void checkAndNotifyTimeControl(final Activity activity, boolean userWhite) {
        if (activity == null || hasNotifiedThisGame) return;

        LiveClockInfo clockInfo = detectLiveClocks(activity, userWhite);
        if (!clockInfo.isAutoDetected && clockInfo.initBaseSec <= 0) return;

        int baseSec = clockInfo.initBaseSec > 0 ? clockInfo.initBaseSec : (int) Math.round(clockInfo.mySec);
        int incSec = Math.max(0, clockInfo.incrementSec);

        int bestIdx = matchPresetIndex(baseSec, incSec, clockInfo.incrementSec < 0);
        TCPreset preset = TC_PRESETS[bestIdx];

        String sig = bestIdx + "|" + preset.label;
        if (sig.equals(activeTcSignature) || hasNotifiedThisGame) {
            hasNotifiedThisGame = true;
            return;
        }

        activeTcIndex = bestIdx;
        activeTcSignature = sig;
        hasNotifiedThisGame = true;
        sState._humanPersona = null;
        currentPersona = null;

        NnvcLogger.i(TAG, "⏳ [TIME CONTROL RECOGNIZED] " + preset.label + " (Base=" + baseSec + "s, Inc=" + incSec + "s, Nguồn=" + clockInfo.source + ")");

        final String tcLabel = I18n.getTcPresetLabel(activity, bestIdx);
        final String srcKey = "clock".equalsIgnoreCase(clockInfo.source) ? "tcp_src_clock" : "tcp_src_auto";
        final String srcLabel = I18n.get(activity, srcKey);

        postToMain(() -> showTCPopup(activity, tcLabel, srcLabel));
    }

    private static WeakReference<View> currentPopupRef = null;

    public static void showTCPopup(final Activity activity, final String tcLabel, final String srcLabel) {
        if (activity == null || activity.isFinishing()) return;

        if (currentPopupRef != null) {
            View old = currentPopupRef.get();
            if (old != null && old.getParent() instanceof ViewGroup) {
                ((ViewGroup) old.getParent()).removeView(old);
            }
            currentPopupRef = null;
        }

        try {
            Vibrator vib = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null && vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vib.vibrate(25);
                }
            }
        } catch (Throwable ignored) {}

        float density = activity.getResources().getDisplayMetrics().density;

        LinearLayout popup = new LinearLayout(activity);
        popup.setOrientation(LinearLayout.HORIZONTAL);
        popup.setGravity(Gravity.CENTER_VERTICAL);
        popup.setPadding((int) (14 * density), (int) (10 * density), (int) (18 * density), (int) (10 * density));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E60F172A"));
        bg.setCornerRadius(14 * density);
        bg.setStroke((int) (1.5f * density), Color.parseColor("#38BDF8"));
        popup.setBackground(bg);
        popup.setElevation(25 * density);

        TextView iconView = new TextView(activity);
        iconView.setText("⏳");
        iconView.setTextSize(20);
        iconView.setPadding(0, 0, (int) (10 * density), 0);
        popup.addView(iconView);

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);

        TextView labelView = new TextView(activity);
        labelView.setText(I18n.get(activity, "tcp_label"));
        labelView.setTextColor(Color.parseColor("#94A3B8"));
        labelView.setTextSize(10.5f);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        body.addView(labelView);

        TextView valView = new TextView(activity);
        valView.setText(tcLabel);
        valView.setTextColor(Color.parseColor("#38BDF8"));
        valView.setTextSize(16.5f);
        valView.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        body.addView(valView);

        TextView srcView = new TextView(activity);
        srcView.setText(srcLabel);
        srcView.setTextColor(Color.parseColor("#6EE7B7"));
        srcView.setTextSize(10.5f);
        body.addView(srcView);

        popup.addView(body);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = (int) (68 * density);

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        decor.addView(popup, lp);

        currentPopupRef = new WeakReference<>(popup);

        popup.setAlpha(0.0f);
        popup.setTranslationY(-20 * density);
        popup.animate().alpha(1.0f).translationY(0f).setDuration(220).start();

        postDelayedToMain(() -> {
            View v = currentPopupRef != null ? currentPopupRef.get() : null;
            if (v != null && v == popup) {
                v.animate().alpha(0.0f).translationY(-25 * density).setDuration(320).withEndAction(() -> {
                    if (v.getParent() instanceof ViewGroup) {
                        ((ViewGroup) v.getParent()).removeView(v);
                    }
                }).start();
            }
        }, 3000);
    }

    // ── 20. Thuật Toán Tính Độ Trễ Smart Delay Chính (_smartDelay Dòng 8149-8308 & 16420-16437) ──
    public static long calculateSmartDelay(String currentFen,
                                           String chosenMove,
                                           float evalScore,
                                           long engineThinkMs,
                                           Activity activity,
                                           boolean userWhite) {
        return calculateSmartDelay(currentFen, chosenMove, evalScore, engineThinkMs, activity, userWhite, false, 0);
    }

    public static long calculateSmartDelay(String currentFen,
                                           String chosenMove,
                                           float evalScore,
                                           long engineThinkMs,
                                           Activity activity,
                                           boolean userWhite,
                                           boolean hasMate,
                                           int mateIn) {
        Context ctx = activity != null ? activity : null;
        if (ctx != null && !StockfishSettings.isSmartDelayEnabled(ctx)) {
            float fixed = StockfishSettings.getFixedDelaySeconds(ctx);
            return Math.round(fixed * 1000);
        }

        if (activity != null) {
            checkAndNotifyTimeControl(activity, userWhite);
        }

        int tcIdx = Math.max(0, Math.min(8, activeTcIndex));
        TCPreset preset = TC_PRESETS[tcIdx];

        LiveClockInfo clocks = activity != null ? detectLiveClocks(activity, userWhite) : new LiveClockInfo();
        Double mySec = clocks.mySec >= 0 ? clocks.mySec : null;
        Double oppSec = clocks.oppSec >= 0 ? clocks.oppSec : null;
        int inc = Math.max(0, clocks.incrementSec);
        long loMs = Math.round(preset.lo * 1000.0);

        // A. Dirty Flagging & Low Time Panic (Chống Rụng Kim Tức Thời - Chuẩn 100% Extension dòng 8158-8180)
        boolean isFastGame = tcIdx <= 5;
        boolean isDirtyFlagging = isFastGame && ((oppSec != null && oppSec <= 6.0) || (mySec != null && mySec <= 6.5));
        boolean isUnder6_5s = (mySec != null && mySec <= 6.5);

        if (isDirtyFlagging || isUnder6_5s) {
            sState.lastMoveMs = 0;
            sState._lastScenarioLabel = isUnder6_5s ? "⚡ No Delay (<= 6.5s)" : "⚡ Dirty Flagging (<= 6s)";
            lastScenarioLabel = sState._lastScenarioLabel;
            NnvcLogger.d(TAG, "▶ [SMART DELAY] " + sState._lastScenarioLabel + " -> 0ms (mySec=" + mySec + "s, oppSec=" + oppSec + "s)");
            return 0;
        }

        // B. Phân tích bàn cờ & Legal Moves
        char[] board = BoardUtil.parseBoard(currentFen);
        List<BoardUtil.LegalMove> legal = BoardUtil.generateLegalMoves(board, userWhite);
        PositionFeatures pos = analyzePosition(currentFen, legal, chosenMove);
        if (!"opening".equals(pos.phase)) sState.openingOver = true;

        boolean isBullet = tcIdx <= 1;
        boolean isBlitz = tcIdx >= 2 && tcIdx <= 5;
        boolean isRapid = tcIdx >= 6;
        HumanPersona persona = ensureHumanDelayPersona(isBullet, isBlitz, isRapid);
        int moveNum = sState.moveNum;
        double panicSec = 5.0;
        double panicGate = isBullet ? 4.8 : (isBlitz ? 5.4 : 6.2);
        double rushSec = isBullet ? 8.5 : (isBlitz ? 10.5 : 14.0);

        if (mySec != null && mySec <= panicGate) {
            long d = Math.round(preset.lo * 1000.0) + RANDOM.nextInt(60);
            sState.lastMoveMs = d;
            sState._lastScenarioLabel = "⚠️ Panic Gate (Cận giờ)";
            lastScenarioLabel = sState._lastScenarioLabel;
            return d;
        }

        // C. Board Key & Lịch sử nhận diện
        String[] fenParts = currentFen != null ? currentFen.trim().split("\\s+") : new String[]{""};
        String boardKey = fenParts[0] + (fenParts.length > 1 ? fenParts[1] : "");
        int timesSeen = 0;
        for (String p : sState.posHistory) {
            if (boardKey.equals(p)) timesSeen++;
        }
        sState._familiarFenMap.put(boardKey, sState._familiarFenMap.getOrDefault(boardKey, 0) + 1);
        sState._lastBoardKey = boardKey;

        double sc = (evalScore - lastEvalScore) * 100.0;
        sState._evalSwingCp = sc;

        // D. 14-Step Human Model Execution
        double budget = computeHumanBudget(preset, pos, mySec, oppSec, inc, isBullet, isBlitz, isRapid);
        DelayFeatures meta = computeDelayFeatures(pos, preset, mySec, oppSec, rushSec, moveNum, timesSeen, sc, isBullet, isBlitz, isRapid);
        HumanStateCfg state = chooseHumanState(meta, mySec, rushSec, isBullet, isBlitz, isRapid);
        SessionDrift drift = computeSessionDrift(pos, mySec, rushSec, isBullet, isBlitz, isRapid);
        OpponentReaction reaction = computeOpponentReaction(pos, meta, preset, mySec, oppSec, rushSec, isBullet, isBlitz, isRapid);

        double target = budget * persona.tempo * state.scale;
        target *= drift.mult;
        target *= reaction.mult;
        target *= 1.0 + meta.tricky * 0.34 - meta.obvious * 0.22;
        target += reaction.extraSec;

        if (oppSec != null && mySec != null) {
            Double oppThinkLast = sState.oppThinkSec;
            double oppThinkAvg = avg(sState.oppThinkHistory);
            if (oppThinkAvg <= 0) oppThinkAvg = preset.mean;
            double clockRatio = mySec / Math.max(oppSec, 1.0);

            if (oppSec < 6.0) {
                target *= 0.18 + RANDOM.nextDouble() * 0.14;
            } else if (clockRatio >= 1.6 && mySec > rushSec * 1.2) {
                double leadBonus = isBullet ? 1.08 : (isBlitz ? 1.16 : 1.24);
                target *= leadBonus + RANDOM.nextDouble() * 0.14;
            } else if (clockRatio < 0.72) {
                double urgency = clamp((0.72 - clockRatio) / 0.5, 0.0, 1.0);
                target *= 0.74 - urgency * 0.18 + RANDOM.nextDouble() * 0.10;
            }

            double ratioToMean = (sState._oppTempoEma != null ? sState._oppTempoEma : oppThinkAvg) / Math.max(0.15, preset.mean);
            if (ratioToMean > 1.0 && mySec > rushSec * 1.1) {
                double mirrorUp = clamp((ratioToMean - 1.0) * 0.16, 0.0, isBullet ? 0.12 : (isBlitz ? 0.20 : 0.28));
                target *= 1.0 + mirrorUp;
            } else if (ratioToMean < 0.82 && mySec > rushSec * 1.1) {
                double mirrorDown = clamp((0.82 - ratioToMean) * 0.22, 0.0, isBullet ? 0.16 : (isBlitz ? 0.22 : 0.28));
                target *= 1.0 - mirrorDown;
            }

            if (oppThinkLast != null && oppThinkLast > preset.mean * 1.8 && clockRatio >= 0.85) {
                target *= isBullet ? (1.06 + RANDOM.nextDouble() * 0.12) : (isBlitz ? (1.10 + RANDOM.nextDouble() * 0.18) : (1.14 + RANDOM.nextDouble() * 0.24));
            }
        }

        if (mySec != null && mySec < rushSec) {
            double softSqueeze = (0.94 + clamp((mySec - panicSec) / Math.max(0.5, rushSec - panicSec), 0.0, 1.0) * 0.06) * (0.98 + RANDOM.nextDouble() * 0.06);
            target *= softSqueeze;
        }

        if (meta.preparedReply) target *= 0.48 + RANDOM.nextDouble() * 0.18;
        if (pos.forced) target *= 0.28 + RANDOM.nextDouble() * 0.10;
        else if ("opening".equals(pos.phase) && "book".equals(pos.subphase)) target *= 0.42 + RANDOM.nextDouble() * 0.18;
        else if ("opening".equals(pos.phase) && "develop".equals(pos.subphase)) target *= 0.72 + RANDOM.nextDouble() * 0.18;
        else if ("endgame".equals(pos.phase) && "technical".equals(pos.subphase) && meta.hard < 0.8) target *= 0.84 + RANDOM.nextDouble() * 0.16;

        if ("middlegame".equals(pos.phase) && mySec != null && mySec > rushSec * 1.3) {
            double oppTempo = sState._oppTempoEma != null ? sState._oppTempoEma : avg(sState.oppThinkHistory);
            if (oppTempo <= 0) oppTempo = preset.mean;
            boolean strongThinkSignal = meta.hard > 0.95 || pos.complex || pos.tactical || sState._lastScoreChange < -140 || oppTempo > preset.mean * 1.45;
            boolean strongSnapSignal = meta.easy > 1.05 || meta.preparedReply || pos.forced || sState._humanRelease > 0;
            if (strongThinkSignal && RANDOM.nextDouble() < (isBullet ? 0.05 : (isBlitz ? 0.09 : 0.13))) {
                target *= isBullet ? (1.45 + RANDOM.nextDouble() * 0.55) : (isBlitz ? (1.70 + RANDOM.nextDouble() * 0.90) : (1.95 + RANDOM.nextDouble() * 1.25));
            } else if (strongSnapSignal && RANDOM.nextDouble() < (isBullet ? 0.10 : (isBlitz ? 0.08 : 0.06))) {
                target *= 0.42 + RANDOM.nextDouble() * 0.18;
            }
        }

        if ("opening".equals(pos.phase) && "develop".equals(pos.subphase) && !meta.historyUnsafe) {
            if (RANDOM.nextDouble() < (isBullet ? 0.30 : (isBlitz ? 0.24 : 0.18))) {
                target *= 0.52 + RANDOM.nextDouble() * 0.28;
            } else if (RANDOM.nextDouble() < 0.12) {
                target *= 1.40 + RANDOM.nextDouble() * 0.80;
            }
        }

        if ("endgame".equals(pos.phase) && mySec != null && mySec > rushSec * 1.5) {
            if (RANDOM.nextDouble() < (isBullet ? 0.06 : (isBlitz ? 0.10 : 0.16))) {
                target *= 1.50 + RANDOM.nextDouble() * 1.20;
            }
        }

        if (sState._humanRelease > 0 && meta.easy >= -0.1 && !pos.complex) {
            target *= 0.76 + RANDOM.nextDouble() * 0.10;
        }

        if (sState.lastWasCapture && pos.hasCaptures && pos.numLegal <= 10) {
            target *= isBullet ? (0.34 + RANDOM.nextDouble() * 0.14) : (isBlitz ? (0.42 + RANDOM.nextDouble() * 0.16) : (0.50 + RANDOM.nextDouble() * 0.18));
        }

        if (sState.timeDebtMs > 0 && meta.easy > 0.25 && !pos.complex && !pos.tactical) {
            double debtCutSec = Math.min(sState.timeDebtMs / 1000.0, target * 0.46);
            target = Math.max(preset.lo, target - debtCutSec);
        }

        if (sState._humanInvestChain >= 1 && sState._humanRelease > 0 && meta.easy > 0.45) {
            target *= 0.62 + RANDOM.nextDouble() * 0.10;
        }
        if (sState._humanFastChain >= 2 && meta.hard > 1.0) {
            target *= 1.12 + RANDOM.nextDouble() * 0.18;
        }
        if (sState._humanInvestChain >= 1 && meta.easy > 0.8) {
            target *= 0.72 + RANDOM.nextDouble() * 0.10;
        }
        target = Math.max(preset.lo, target);

        JitterResult picked = computeHumanJitter(target, persona, state, meta, pos, reaction, preset, mySec, rushSec, isBullet, isBlitz, isRapid);
        double thinkSec = picked.sec;
        String mode = picked.mode;

        double microHesitation = computeMicroHesitation(mode, pos, meta, reaction, preset, mySec, rushSec, isBullet, isBlitz, isRapid);
        if (microHesitation > 0) {
            thinkSec += microHesitation;
            if ("snap".equals(mode)) mode = "snap-hes";
        }

        boolean stableClock = mySec == null || mySec > (isBullet ? 14 : (isBlitz ? 20 : 35));
        boolean snapSafe = pos.forced || meta.preparedReply || ("opening".equals(pos.phase) && ("book".equals(pos.subphase) || "develop".equals(pos.subphase))) || (!meta.historyUnsafe && meta.easy >= (isBullet ? 1.55 : (isBlitz ? 1.70 : 1.90))) || (mySec != null && mySec <= rushSec * 1.02);
        if (stableClock && !snapSafe && meta.hard > 0.55 && !sState.lastWasCapture) {
            double steadyFloor = budget * (isBullet ? 0.22 : (isBlitz ? 0.28 : 0.36));
            if ("snap".equals(mode) && meta.hard > 0.85) mode = "quick";
            if ("middlegame".equals(pos.phase) || meta.hard > 0.55) {
                thinkSec = Math.max(thinkSec, steadyFloor * (0.90 + RANDOM.nextDouble() * 0.14));
            }
        }

        thinkSec = applyHumanBreakFactory(thinkSec, budget, preset, mySec, rushSec);
        thinkSec = finalizeHumanDelay(thinkSec, budget, pos, preset, mySec, oppSec, rushSec, isBullet, isBlitz, isRapid, tcIdx, legal, sc, chosenMove, "balanced");

        long rawDelayMs = Math.round(thinkSec * 1000.0) + physicalJitter();
        rawDelayMs = Math.max(loMs, rawDelayMs);

        commitDelayState(mode, thinkSec, budget, persona);
        sState.lastMoveMs = rawDelayMs;
        sState._lastScoreChange = sc;
        sState._prevQueensCount = pos.queens;
        sState._lastIsCheck = pos.isCheck;
        lastScenarioLabel = sState._lastScenarioLabel;

        // E. Khấu Trừ Thời Gian Nghĩ Của Engine & Physical Buffers (Dòng 16420-16437)
        if (pos.moveNum <= 1) {
            long firstMoveCap = 350 + Math.round(RANDOM.nextDouble() * 850);
            rawDelayMs = Math.min(rawDelayMs, firstMoveCap);
        }

        boolean isThreatenedMate = (hasMate && mateIn < 0) || evalScore <= -30.0f;
        if (isThreatenedMate) {
            long matePanic = 180 + Math.round(RANDOM.nextDouble() * 320);
            rawDelayMs = Math.min(rawDelayMs, matePanic);
            sState._lastScenarioLabel = "⚠️ Đỡ chiếu hết tức thì (Mate panic)";
            lastScenarioLabel = sState._lastScenarioLabel;
        }

        boolean isPanic = isThreatenedMate;
        long minPhysicalBuffer = isPanic ? 0 : (isBullet ? 160 : (isBlitz ? 260 : 380));
        long netDelay = isPanic ? Math.max(25, rawDelayMs - engineThinkMs) : Math.max(minPhysicalBuffer, rawDelayMs - engineThinkMs);

        // Cap delay to ensure we NEVER sleep past the 6.5s panic threshold
        if (mySec != null && mySec > 6.5) {
            long headroomMs = Math.max(0, (long) ((mySec - 6.5) * 1000.0));
            if (netDelay > headroomMs) {
                NnvcLogger.d(TAG, "⏱️ [SMART DELAY HEADROOM CAP] Capping delay from " + netDelay + "ms to " + headroomMs + "ms to avoid dropping below 6.5s");
                netDelay = headroomMs;
            }
        }

        lastWasCapture = sState.lastWasCapture;
        lastEvalScore = evalScore;

        NnvcLogger.i(TAG, "⏱️ [NNCV-SmartDelay] " + lastScenarioLabel + " ➔ Chờ " + String.format(java.util.Locale.US, "%.2f", netDelay / 1000.0) + "s (" + preset.label + ") [Persona=" + persona.name + ", Think=" + engineThinkMs + "ms]");

        return netDelay;
    }

    public static int getActiveTcIndex() {
        return activeTcIndex;
    }

    public static void setActiveTcIndex(int index) {
        activeTcIndex = Math.max(0, Math.min(8, index));
    }

    public static String getActiveTcLabel() {
        return TC_PRESETS[Math.max(0, Math.min(8, activeTcIndex))].label;
    }
}
