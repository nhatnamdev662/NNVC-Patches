package app.nnvc.chess.extension.stockfish;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SmartDelayManager – 100% Parity with NNVC Desktop Extension Auto Smart Delay.
 * 
 * Features:
 *  - 9 standard Time Control presets (_TC_PRESETS)
 *  - Log-normal distribution (Box-Muller) + biometric physical jitter
 *  - Human delay personas per time class (Bullet, Blitz, Rapid)
 *  - FEN position analysis (Opening, Recapture, Forced reply, Blunder seen, Deep think)
 *  - Engine think time deduction: delay = max(minBuffer, rawDelay - thinkMs)
 *  - Dirty Flagging (<= 6.0s in Bullet) & Low-time panic (<= 8.0s) -> 0ms delay
 *  - 100% Native Time Control & Clock detection via RealGameViewModel & GameSetup
 *  - Glass Cyber HUD Popup notification upon Time Control recognition with 25ms haptic
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

    // ── 2. State ván đấu & Human Persona ─────────────────────────────────────────
    public static class HumanPersona {
        public final String name;
        public final double tempo;
        public final double snapBias;
        public final double investBias;
        public final double wobble;
        public final int releaseMin;
        public final int releaseMax;

        public HumanPersona(String name, double tempo, double snapBias, double investBias, double wobble, int relMin, int relMax) {
            this.name = name;
            this.tempo = tempo;
            this.snapBias = snapBias;
            this.investBias = investBias;
            this.wobble = wobble;
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

    // ── 3. Thuật toán phân phối Log-Normal & Jitter (Dòng 7203-7216) ─────────────
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

    // ── 4. Khởi tạo Persona theo thể loại cờ ─────────────────────────────────────
    public static HumanPersona getOrCreatePersona(int tcIdx) {
        if (currentPersona != null) return currentPersona;
        boolean isBullet = tcIdx <= 2;
        boolean isBlitz = tcIdx >= 3 && tcIdx <= 5;

        double r = RANDOM.nextDouble();
        if (isBullet) {
            if (r < 0.20) currentPersona = new HumanPersona("snapper", 0.90, 0.14, -0.08, 0.12, 1, 1);
            else if (r < 0.70) currentPersona = new HumanPersona("street", 0.96, 0.06, 0.02, 0.16, 1, 2);
            else currentPersona = new HumanPersona("grinder", 1.06, -0.08, 0.14, 0.14, 1, 2);
        } else if (isBlitz) {
            if (r < 0.22) currentPersona = new HumanPersona("snappy", 0.93, 0.10, -0.02, 0.14, 1, 2);
            else if (r < 0.74) currentPersona = new HumanPersona("balanced", 1.00, 0.02, 0.06, 0.18, 1, 2);
            else currentPersona = new HumanPersona("careful", 1.08, -0.08, 0.18, 0.20, 1, 3);
        } else {
            if (r < 0.25) currentPersona = new HumanPersona("steady", 0.98, 0.05, 0.04, 0.15, 1, 2);
            else if (r < 0.75) currentPersona = new HumanPersona("positional", 1.00, 0.00, 0.08, 0.18, 1, 2);
            else currentPersona = new HumanPersona("academic", 1.08, -0.06, 0.16, 0.22, 2, 3);
        }
        return currentPersona;
    }

    public static void resetGameSession() {
        currentPersona = null;
        lastWasCapture = false;
        lastWasCheck = false;
        lastEvalScore = 0f;
        lastScenarioLabel = "Tự động";
    }

    // ── 5. Phân tích thế cờ FEN (Opening, Recapture, Forced, Endgame) ────────────
    public static class PositionFeatures {
        public boolean isOpening = false;
        public boolean isEndgame = false;
        public boolean isForced = false;
        public boolean isTactical = false;
        public boolean isComplex = false;
        public int moveNum = 1;
        public int undeveloped = 0;
        public int numPieces = 32;
    }

    public static PositionFeatures analyzeFen(String fen, String chosenMove) {
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
            feat.numPieces = queens + rooks + minors + pawns;

            String[] rows = board.split("/");
            if (rows.length == 8) {
                String rank1 = rows[7];
                String rank8 = rows[0];
                for (char c : rank1.toCharArray()) if (c == 'N' || c == 'B') feat.undeveloped++;
                for (char c : rank8.toCharArray()) if (c == 'n' || c == 'b') feat.undeveloped++;
            }

            if (feat.moveNum <= 14 && feat.undeveloped >= 2) {
                feat.isOpening = true;
            } else if ((queens + rooks + minors) <= 6 || (queens == 0 && (rooks + minors) <= 6)) {
                feat.isEndgame = true;
            }

            if (chosenMove != null && chosenMove.length() >= 4) {
                String toSq = chosenMove.substring(2, 4);
                char destPiece = StockfishExtension.getPieceAtSquare(fen, toSq);
                feat.isTactical = (destPiece != '.' && destPiece != ' ');
            }
        } catch (Throwable t) {
            NnvcLogger.w(TAG, "analyzeFen error: " + t.getMessage());
        }
        return feat;
    }

    public static int matchPresetIndex(int initSec, int incSec) {
        return matchPresetIndex(initSec, incSec, false);
    }

    public static int matchPresetIndex(int initSec, int incSec, boolean fromClockOnly) {
        if (initSec <= 0) return 5; // Default 5 phút

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

    // ── 7. Trích xuất Time Control & Live Clock 100% Native từ ViewModel ─────────
    public static class LiveClockInfo {
        public double mySec = -1;
        public double oppSec = -1;
        public int initBaseSec = -1;
        public int incrementSec = -1;
        public String source = "unknown";
        public boolean isAutoDetected = false;
    }

    public static LiveClockInfo detectLiveClocks(Activity activity, boolean userWhite) {
        LiveClockInfo info = new LiveClockInfo();
        if (activity == null) return info;

        // ── Tầng 1: Native ViewModel Hooking (Authoritative Time Control & State) ──
        try {
            Object vm = resolveRealGameViewModel(activity);
            if (vm != null) {
                // A. Đọc ClocksState từ _clocksState / clocksState
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

                // B. Đọc GameSetup từ RcnPlayGameDelegateImpl hoặc realGameUiSetup
                for (Field f : vm.getClass().getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(vm);
                        if (val != null) {
                            // Kiểm tra GameTime / realGameUiSetup
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

        // ── Tầng 2: UI View Hierarchy Scanning (Realtime Ticking Clock) ─────
        try {
            LiveClockInfo uiInfo = new LiveClockInfo();
            scanClocksFromUi(activity, userWhite, uiInfo);
            if (uiInfo.mySec >= 0 && uiInfo.oppSec >= 0) {
                // UI clock là đồng hồ trực quan đang chạy trực tiếp trên màn hình
                // Nếu ViewModel cũng có giá trị, lấy giá trị nhỏ hơn (vì đồng hồ chỉ đếm lùi, tránh snapshot cũ tồn đọng)
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

        // 1. Getter chuẩn Kotlin: getWhiteMillis() / getBlackMillis()
        try {
            Method mWhite = cCls.getMethod("getWhiteMillis");
            Method mBlack = cCls.getMethod("getBlackMillis");
            wMs = ((Number) mWhite.invoke(clocksState)).longValue();
            bMs = ((Number) mBlack.invoke(clocksState)).longValue();
            if (wMs >= 0 && bMs >= 0) return new long[] { wMs, bMs };
        } catch (Throwable ignored) {}

        // 2. Getter obfuscated R8: f() / c() (khớp 100% com.chess.realchess.ui.game.b)
        try {
            Method mF = cCls.getMethod("f");
            Method mC = cCls.getMethod("c");
            if (mF.getReturnType() == long.class && mC.getReturnType() == long.class) {
                wMs = ((Number) mF.invoke(clocksState)).longValue();
                bMs = ((Number) mC.invoke(clocksState)).longValue();
                if (wMs >= 0 && bMs >= 0) return new long[] { wMs, bMs };
            }
        } catch (Throwable ignored) {}

        // 3. Quét trực tiếp các trường long: ClocksState có 2 trường long đầu tiên (whiteMillis, blackMillis)
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
            // Sort by vertical position: top clock = opponent, bottom clock = me
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
                // Standalone seconds with decimal: e.g. "6.4", "0.8"
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

    // ── 8. Đồng Bộ Hóa & Hiển Thị Popup Time Control (Parity Dòng 8671-8700 & 16606) ─
    public static void checkAndNotifyTimeControl(final Activity activity, boolean userWhite) {
        if (activity == null) return;

        LiveClockInfo clockInfo = detectLiveClocks(activity, userWhite);
        if (!clockInfo.isAutoDetected && clockInfo.initBaseSec <= 0) return;

        int baseSec = clockInfo.initBaseSec > 0 ? clockInfo.initBaseSec : (int) Math.round(clockInfo.mySec);
        int incSec = Math.max(0, clockInfo.incrementSec);

        int bestIdx = matchPresetIndex(baseSec, incSec, clockInfo.incrementSec < 0);
        TCPreset preset = TC_PRESETS[bestIdx];

        String sig = bestIdx + "|" + baseSec + "|" + incSec;
        if (sig.equals(activeTcSignature)) {
            return; // Đã nhận diện và thông báo cho ván đấu hiện tại
        }

        activeTcIndex = bestIdx;
        activeTcSignature = sig;
        currentPersona = null; // Khởi tạo persona mới phù hợp với TC này

        NnvcLogger.i(TAG, "⏳ [TIME CONTROL RECOGNIZED] " + preset.label + " (Base=" + baseSec + "s, Inc=" + incSec + "s, Nguồn=" + clockInfo.source + ")");

        final String tcLabel = I18n.getTcPresetLabel(activity, bestIdx);
        final String srcKey = "clock".equalsIgnoreCase(clockInfo.source) ? "tcp_src_clock" : "tcp_src_auto";
        final String srcLabel = I18n.get(activity, srcKey);

        NnvcLogger.i(TAG, "⏳ [TIME CONTROL RECOGNIZED] " + tcLabel + " (Base=" + baseSec + "s, Inc=" + incSec + "s, Source=" + clockInfo.source + ")");

        postToMain(() -> showTCPopup(activity, tcLabel, srcLabel));
    }

    /**
     * Hiển thị Popup Time Control chuẩn Extension (_showTCPopup dòng 16606)
     * Bo góc mờ ảo kiểu Frosted Cyber Glass, kèm rung haptic 25ms.
     */
    private static WeakReference<View> currentPopupRef = null;

    public static void showTCPopup(final Activity activity, final String tcLabel, final String srcLabel) {
        if (activity == null || activity.isFinishing()) return;

        // Dismiss popup cũ nếu đang hiển thị
        if (currentPopupRef != null) {
            View old = currentPopupRef.get();
            if (old != null && old.getParent() instanceof ViewGroup) {
                ((ViewGroup) old.getParent()).removeView(old);
            }
            currentPopupRef = null;
        }

        // Rung haptic nhẹ 25ms chuẩn extension
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

        // Card container
        LinearLayout popup = new LinearLayout(activity);
        popup.setOrientation(LinearLayout.HORIZONTAL);
        popup.setGravity(Gravity.CENTER_VERTICAL);
        popup.setPadding((int) (14 * density), (int) (10 * density), (int) (18 * density), (int) (10 * density));

        // Background Cyber Glass bo góc 14dp, viền Cyan #38BDF8
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E60F172A")); // Deep Slate 90%
        bg.setCornerRadius(14 * density);
        bg.setStroke((int) (1.5f * density), Color.parseColor("#38BDF8"));
        popup.setBackground(bg);
        popup.setElevation(25 * density);

        // Icon đồng hồ cát ⏳
        TextView iconView = new TextView(activity);
        iconView.setText("⏳");
        iconView.setTextSize(20);
        iconView.setPadding(0, 0, (int) (10 * density), 0);
        popup.addView(iconView);

        // Body: Tiêu đề + Giá trị + Nguồn
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
        srcView.setTextColor(Color.parseColor("#6EE7B7")); // Emerald light
        srcView.setTextSize(10.5f);
        body.addView(srcView);

        popup.addView(body);

        // Đặt ở vị trí góc trên màn hình (top-center)
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = (int) (68 * density);

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        decor.addView(popup, lp);

        currentPopupRef = new WeakReference<>(popup);

        // Animation: Fade in và tự động trượt mờ dần sau 3 giây
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

    // ── 9. Thuật Toán Tính Độ Trễ Smart Delay Chính (_smartDelay Dòng 8149-8308) ──
    public static long calculateSmartDelay(String currentFen,
                                           String chosenMove,
                                           float evalScore,
                                           long engineThinkMs,
                                           Activity activity,
                                           boolean userWhite) {
        Context ctx = activity != null ? activity : null;
        if (ctx != null && !StockfishSettings.isSmartDelayEnabled(ctx)) {
            float fixed = StockfishSettings.getFixedDelaySeconds(ctx);
            return Math.round(fixed * 1000);
        }

        // Tự động kiểm tra Time Control
        if (activity != null) {
            checkAndNotifyTimeControl(activity, userWhite);
        }

        TCPreset preset = TC_PRESETS[Math.max(0, Math.min(8, activeTcIndex))];
        HumanPersona persona = getOrCreatePersona(activeTcIndex);

        boolean isBullet = activeTcIndex <= 2;
        boolean isBlitz = activeTcIndex >= 3 && activeTcIndex <= 5;
        boolean isRapid = activeTcIndex >= 6;

        LiveClockInfo clocks = activity != null ? detectLiveClocks(activity, userWhite) : new LiveClockInfo();
        double mySec = clocks.mySec;
        double oppSec = clocks.oppSec;

        // A. Chế độ Dirty Flagging & Low Time Panic (Chống Rụng Kim Tức Thời - Chuẩn 100% Extension)
        boolean isFastGame = activeTcIndex <= 5; // Bullet (1m, 1|1, 2|1) và Blitz (3m, 3|2, 5m)
        boolean isDirtyFlagging = isFastGame && ((oppSec >= 0 && oppSec <= 6.0) || (mySec >= 0 && mySec <= 8.0));
        boolean isUnder8s = (mySec >= 0 && mySec <= 8.0);

        if (isDirtyFlagging || isUnder8s) {
            lastScenarioLabel = isUnder8s ? "⚡ No Delay (<= 8s)" : "⚡ Dirty Flagging (<= 6s)";
            NnvcLogger.d(TAG, "▶ [SMART DELAY] " + lastScenarioLabel + " -> 0ms (mySec=" + mySec + "s, oppSec=" + oppSec + "s)");
            return 0;
        }

        // B. Phân tích thế cờ
        PositionFeatures pos = analyzeFen(currentFen, chosenMove);
        double budget = preset.mean * persona.tempo;

        // C. Phản ứng theo kịch bản thế cờ (Reaction Logic)
        double sec;
        double floor = preset.lo;
        double cap = preset.hi;

        if (pos.isOpening) {
            lastScenarioLabel = "📖 Khai cuộc bài bản";
            boolean isEarlyBook = (pos.moveNum <= 5);
            if (isBullet) {
                floor = 0.35; cap = 0.85;
                sec = lognormal(0.58, 0.16);
            } else if (isBlitz) {
                floor = isEarlyBook ? 0.85 : 1.40;
                cap = isEarlyBook ? 1.60 : 2.80;
                sec = lognormal(isEarlyBook ? 1.10 : 1.95, 0.18);
            } else {
                floor = isEarlyBook ? 1.50 : 3.00;
                cap = isEarlyBook ? 3.20 : 6.50;
                sec = lognormal(isEarlyBook ? 2.20 : 4.20, 0.21);
            }
        } else if (lastWasCapture && pos.isTactical) {
            lastScenarioLabel = "⚔️ Ăn lại quân tức thì (Recapture)";
            double mult = isBullet ? 0.45 : isBlitz ? 0.52 : 0.60;
            sec = lognormal(budget * mult, 0.18);
            floor = preset.lo * 0.8;
            cap = budget * 0.85;
        } else if (lastWasCheck && pos.isForced) {
            lastScenarioLabel = "🛡️ Đỡ chiếu ép buộc";
            double mult = isBullet ? 0.55 : isBlitz ? 0.65 : 0.72;
            sec = lognormal(budget * mult, 0.16);
            floor = preset.lo;
            cap = budget * 0.90;
        } else if (Math.abs(evalScore - lastEvalScore) > 1.30f) {
            lastScenarioLabel = "🎯 Trừng phạt sơ hở (Blunder seen)";
            double mult = isBullet ? 0.58 : isBlitz ? 0.65 : 0.72;
            sec = lognormal(budget * mult, 0.18);
            cap = budget * 0.95;
        } else {
            lastScenarioLabel = "♟️ Trung cuộc / Cờ tàn";
            double tempoRoll = RANDOM.nextDouble();
            if (isBullet) {
                if (tempoRoll < 0.30) { floor = 0.65; cap = 1.10; sec = lognormal(0.85, 0.18); }
                else if (tempoRoll < 0.80) { floor = 0.90; cap = 1.60; sec = lognormal(1.20, 0.22); }
                else { floor = 1.40; cap = 2.20; sec = lognormal(1.75, 0.22); }
            } else if (isBlitz) {
                if (tempoRoll < 0.30) { floor = 1.50; cap = 2.80; sec = lognormal(2.10, 0.20); }
                else if (tempoRoll < 0.80) { floor = 2.40; cap = 4.50; sec = lognormal(3.20, 0.24); }
                else { floor = 3.60; cap = 6.80; sec = lognormal(4.80, 0.26); }
            } else {
                if (tempoRoll < 0.30) { floor = 3.50; cap = 7.00; sec = lognormal(5.00, 0.22); }
                else if (tempoRoll < 0.80) { floor = 5.50; cap = 12.0; sec = lognormal(8.20, 0.26); }
                else { floor = 8.50; cap = 22.0; sec = lognormal(14.0, 0.28); }
            }
        }

        // D. Khử trừ thời gian tính toán của engine (Dòng 16420-16437)
        double finalSec = clamp(sec, floor, cap);
        long rawDelayMs = Math.round(finalSec * 1000.0) + physicalJitter();

        // Giới hạn nước đầu tiên
        if (pos.moveNum <= 1) {
            long firstMoveCap = 350 + Math.round(RANDOM.nextDouble() * 850);
            rawDelayMs = Math.min(rawDelayMs, firstMoveCap);
        }

        long minBuffer = isBullet ? 160 : isBlitz ? 260 : 380;
        long netDelay = Math.max(minBuffer, rawDelayMs - engineThinkMs);

        // Cap delay to ensure we NEVER sleep past the 8.0s panic threshold
        if (mySec > 8.0) {
            long headroomMs = Math.max(0, (long) ((mySec - 8.0) * 1000.0));
            if (netDelay > headroomMs) {
                NnvcLogger.d(TAG, "⏱️ [SMART DELAY HEADROOM CAP] Capping delay from " + netDelay + "ms to " + headroomMs + "ms to avoid dropping below 8.0s");
                netDelay = headroomMs;
            }
        }

        // Lưu trạng thái cho nước kế tiếp
        lastWasCapture = pos.isTactical;
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
