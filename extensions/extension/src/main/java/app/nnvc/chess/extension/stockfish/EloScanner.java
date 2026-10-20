/*
 * Copyright 2026 NNVC
 */

package app.nnvc.chess.extension.stockfish;

import android.app.Activity;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EloScanner – Quét và nhận diện chính xác chỉ số Elo/Rating của hai kỳ thủ (White & Black)
 * chạy 100% không làm nghẽn Main UI Thread (Triệt tiêu hiện tượng lag/khựng khi đi cờ).
 */
public class EloScanner {

    private static final String TAG = "NNVC";

    public static class EloPair {
        public final int whiteElo;
        public final int blackElo;
        public final String source;
        public final boolean isAutoDetected;

        public EloPair(int whiteElo, int blackElo, String source, boolean isAutoDetected) {
            this.whiteElo = whiteElo;
            this.blackElo = blackElo;
            this.source = source;
            this.isAutoDetected = isAutoDetected;
        }

        @Override
        public String toString() {
            return "WhiteElo=" + whiteElo + ", BlackElo=" + blackElo + " (nguồn=" + source + ")";
        }
    }

    private static volatile EloPair lastDetectedPair = null;
    private static volatile long lastScanTimeMs = 0;
    private static final ExecutorService SCAN_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean IS_SCANNING = new AtomicBoolean(false);

    public static void reset() {
        lastDetectedPair = null;
        lastScanTimeMs = 0;
    }

    /**
     * Quét và tự động đồng bộ hóa Elo vào Torch WebAssembly Engine.
     * Chạy hoàn toàn trên Background Worker, tuyệt đối không chặn Main Thread.
     */
    public static void scanAndApply(Activity activity, Object stateImplObject) {
        if (activity == null) return;

        long now = System.currentTimeMillis();
        // Tránh quét dồn dập: giãn cách tối thiểu 10 giây
        if (now - lastScanTimeMs < 10000) {
            return;
        }
        // Nếu đã nhận diện thành công cho ván đấu hiện tại, không quét lại để tiết kiệm CPU
        if (lastDetectedPair != null && lastDetectedPair.isAutoDetected) {
            return;
        }

        if (!IS_SCANNING.compareAndSet(false, true)) {
            return;
        }
        lastScanTimeMs = now;

        SCAN_EXECUTOR.submit(() -> {
            try {
                TorchEngine.log("[ELO SCAN] Bắt đầu quét Elo người chơi...");
                EloPair pair = detectEloPair(activity, stateImplObject);
                if (pair != null && pair.isAutoDetected) {
                    if (lastDetectedPair == null || lastDetectedPair.whiteElo != pair.whiteElo || lastDetectedPair.blackElo != pair.blackElo) {
                        lastDetectedPair = pair;
                        TorchEngine.log("[ELO SCANNER] ĐÃ PHÁT HIỆN: " + pair.toString());
                        TorchEngine.getInstance(activity.getApplicationContext()).updateRatings(pair.whiteElo, pair.blackElo);
                    }
                } else {
                    int def = StockfishSettings.getElo(activity);
                    TorchEngine.log("[ELO DEBUG] Không phát hiện tự động, dùng cài đặt mặc định: " + def);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Lỗi khi quét Elo: " + t.getMessage(), t);
                TorchEngine.log("[ELO ERROR] " + t.getMessage());
            } finally {
                IS_SCANNING.set(false);
            }
        });
    }

    public static EloPair getLastDetectedPair() {
        return lastDetectedPair;
    }

    public static void onPlayersLoadedNative(Object firstPlayer, Object secondPlayer) {
        if (firstPlayer == null && secondPlayer == null) return;
        try {
            int r1 = extractRatingFromUserInfo(firstPlayer);
            int r2 = extractRatingFromUserInfo(secondPlayer);
            Boolean c1 = extractColorFromUserInfo(firstPlayer);
            Boolean c2 = extractColorFromUserInfo(secondPlayer);

            int whiteElo = -1;
            int blackElo = -1;

            if (Boolean.TRUE.equals(c1) || Boolean.FALSE.equals(c2)) {
                whiteElo = r1;
                blackElo = r2;
            } else if (Boolean.FALSE.equals(c1) || Boolean.TRUE.equals(c2)) {
                whiteElo = r2;
                blackElo = r1;
            } else {
                whiteElo = r1;
                blackElo = r2;
            }

            if (whiteElo > 0 || blackElo > 0) {
                EloPair pair = new EloPair(whiteElo > 0 ? whiteElo : 1500, blackElo > 0 ? blackElo : 1500, "native_players", true);
                lastDetectedPair = pair;
                NnvcLogger.i(TAG, "[ELO NATIVE] Nhận diện Elo chính xác qua onPlayersLoadedNative: " + pair);
                Activity act = StockfishExtension.getCurrentActivity();
                if (act != null) {
                    TorchEngine.getInstance(act.getApplicationContext()).updateRatings(pair.whiteElo, pair.blackElo);
                }
            }
        } catch (Throwable t) {
            NnvcLogger.e(TAG, "onPlayersLoadedNative error: " + t.getMessage(), t);
        }
    }

    /**
     * Nhận diện cặp Elo (Zero-Fallback Architecture):
     * 1. Native bytecode hook onPlayersLoadedNative (0ms, 100% chính xác)
     * 2. Deep Reflection từ Model/State/Activity
     * Tuyệt đối không quét TextView, không dùng settings fallback.
     */
    public static EloPair detectEloPair(Activity activity, Object stateImplObject) {
        if (lastDetectedPair != null && lastDetectedPair.isAutoDetected) {
            return lastDetectedPair;
        }

        if (activity == null) return null;

        // ── 1. Thử qua Deep Reflection từ Model ──
        try {
            EloPair reflectPair = tryReflectionElo(activity, stateImplObject);
            if (reflectPair != null) {
                TorchEngine.log("[ELO DEBUG] Reflection thành công: " + reflectPair);
                return reflectPair;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Lỗi tryReflectionElo: " + t.getMessage());
        }

        // ZERO-FALLBACK: Fail-fast, do not scrape TextViews or fallback to default settings.
        return null;
    }

    private static EloPair tryReflectionElo(Activity activity, Object stateImpl) {
        if (stateImpl != null) {
            EloPair pair = inspectObjectForElo(stateImpl, 0);
            if (pair != null) return pair;
        }

        if (activity != null) {
            // 1. Kiểm tra trực tiếp RealGameActivity / ViewModel
            try {
                EloPair vmPair = tryExtractFromRealGameActivity(activity);
                if (vmPair != null) return vmPair;
            } catch (Throwable ignored) {}

            try {
                for (Field f : activity.getClass().getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(activity);
                        if (val != null) {
                            if (val.getClass().getName().contains("Lazy")) {
                                val = unwrapLazy(val);
                            }
                            if (val != null && !isFrameworkClass(val.getClass().getName())) {
                                EloPair pair = inspectObjectForElo(val, 0);
                                if (pair != null) return pair;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private static Object unwrapLazy(Object lazyObj) {
        if (lazyObj == null) return null;
        try {
            Method m = lazyObj.getClass().getMethod("getValue");
            return m.invoke(lazyObj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static EloPair tryExtractFromRealGameActivity(Activity activity) {
        Object vm = null;
        try {
            Method mX6 = activity.getClass().getMethod("X6");
            vm = mX6.invoke(activity);
        } catch (Throwable ignored) {}

        if (vm == null) {
            try {
                Method mI8 = activity.getClass().getMethod("I8");
                vm = mI8.invoke(activity);
            } catch (Throwable ignored) {}
        }

        if (vm == null) {
            try {
                Field fB = activity.getClass().getDeclaredField("B");
                fB.setAccessible(true);
                vm = unwrapLazy(fB.get(activity));
            } catch (Throwable ignored) {}
        }

        if (vm == null) return null;

        TorchEngine.log("[ELO REFLECT] Đã tìm thấy ViewModel: " + vm.getClass().getSimpleName());

        // A. Trích xuất từ RcnPlayGameDelegateImpl (field 'm' trong RealGameViewModel)
        try {
            Field fm = vm.getClass().getDeclaredField("m");
            fm.setAccessible(true);
            Object delegateM = fm.get(vm);
            if (delegateM != null) {
                Method mb = delegateM.getClass().getMethod("b");
                Object playK = mb.invoke(delegateM);
                if (playK != null) {
                    EloPair pair = extractFromRcnPlay(playK);
                    if (pair != null) {
                        TorchEngine.log("[ELO REFLECT] Thành công qua RcnPlay: " + pair);
                        return pair;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // B. Trích xuất từ GameViewModelPlayersImpl (field 'd' trong RealGameViewModel)
        try {
            Field fd = vm.getClass().getDeclaredField("d");
            fd.setAccessible(true);
            Object playersImpl = fd.get(vm);
            if (playersImpl != null) {
                EloPair pair = inspectObjectForElo(playersImpl, 0);
                if (pair != null) {
                    TorchEngine.log("[ELO REFLECT] Thành công qua GameViewModelPlayersImpl: " + pair);
                    return pair;
                }
            }
        } catch (Throwable ignored) {}

        // C. Quét đệ quy toàn bộ fields của ViewModel
        EloPair vmInspect = inspectObjectForElo(vm, 0);
        if (vmInspect != null) {
            TorchEngine.log("[ELO REFLECT] Thành công qua ViewModel fields: " + vmInspect);
            return vmInspect;
        }

        return null;
    }

    private static EloPair extractFromRcnPlay(Object playK) {
        if (playK == null) return null;
        try {
            // playK có thể là RcnPlayPlatformServiceImpl hoặc i
            // Thử gọi M() để lấy RcnGameState
            Method mM = findMethod(playK.getClass(), "M");
            if (mM != null) {
                Object gameState = mM.invoke(playK);
                if (gameState != null) {
                    Method mw = findMethod(gameState.getClass(), "getWhiteRating");
                    Method mb = findMethod(gameState.getClass(), "getBlackRating");
                    if (mw != null && mb != null) {
                        Object w = mw.invoke(gameState);
                        Object b = mb.invoke(gameState);
                        if (w instanceof Number && b instanceof Number) {
                            int wVal = ((Number) w).intValue();
                            int bVal = ((Number) b).intValue();
                            if (isValidElo(wVal) && isValidElo(bVal)) {
                                return new EloPair(wVal, bVal, "reflection_rcn_game_state", true);
                            }
                        }
                    }
                }
            }

            // Thử field 'r' (RcnGameState) hoặc 'q' (RcnGame) trên playK
            for (Field f : playK.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(playK);
                    if (val != null) {
                        Method mw = findMethod(val.getClass(), "getWhiteRating");
                        Method mb = findMethod(val.getClass(), "getBlackRating");
                        if (mw != null && mb != null) {
                            Object w = mw.invoke(val);
                            Object b = mb.invoke(val);
                            if (w instanceof Number && b instanceof Number) {
                                int wVal = ((Number) w).intValue();
                                int bVal = ((Number) b).intValue();
                                if (isValidElo(wVal) && isValidElo(bVal)) {
                                    return new EloPair(wVal, bVal, "reflection_rcn_field_" + f.getName(), true);
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isFrameworkClass(String name) {
        return name.startsWith("android.") || name.startsWith("java.") || name.startsWith("androidx.") || name.startsWith("kotlin.");
    }

    private static EloPair inspectObjectForElo(Object obj, int depth) {
        if (obj == null || depth > 2) return null;
        Class<?> clazz = obj.getClass();
        if (isFrameworkClass(clazz.getName())) return null;

        // A. Direct getters: getWhiteRating / getBlackRating
        try {
            Method wM = findMethod(clazz, "getWhiteRating", "getWhiteElo");
            Method bM = findMethod(clazz, "getBlackRating", "getBlackElo");
            if (wM != null && bM != null) {
                Object w = wM.invoke(obj);
                Object b = bM.invoke(obj);
                if (w instanceof Number && b instanceof Number) {
                    int wVal = ((Number) w).intValue();
                    int bVal = ((Number) b).intValue();
                    if (isValidElo(wVal) && isValidElo(bVal)) {
                        return new EloPair(wVal, bVal, "reflection_" + clazz.getSimpleName(), true);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // B. UserInfo / LiveUserInfo
        try {
            Integer whiteElo = null;
            Integer blackElo = null;
            for (Method m : clazz.getMethods()) {
                if (m.getParameterCount() == 0) {
                    String retName = m.getReturnType().getName();
                    if (retName.endsWith("UserInfo") || retName.endsWith("LiveUserInfo") || retName.endsWith("DailyUserInfo") || retName.endsWith("RcnPlayerData")) {
                        Object uInfo = m.invoke(obj);
                        if (uInfo != null) {
                            int r = extractRatingFromUserInfo(uInfo);
                            Boolean isWhite = extractColorFromUserInfo(uInfo);
                            if (isValidElo(r) && isWhite != null) {
                                if (isWhite) whiteElo = r;
                                else blackElo = r;
                            }
                        }
                    }
                }
            }
            if (whiteElo != null && blackElo != null) {
                return new EloPair(whiteElo, blackElo, "reflection_userinfo_" + clazz.getSimpleName(), true);
            }
        } catch (Throwable ignored) {}

        // C. Pair of UserInfo / RcnPlayerData: e.g. com.chess.gameutils.e (a, b)
        try {
            Method ma = findMethod(clazz, "a");
            Method mb = findMethod(clazz, "b");
            if (ma != null && mb != null) {
                Object ua = ma.invoke(obj);
                Object ub = mb.invoke(obj);
                if (ua != null && ub != null) {
                    int ra = extractRatingFromUserInfo(ua);
                    int rb = extractRatingFromUserInfo(ub);
                    Boolean ca = extractColorFromUserInfo(ua);
                    Boolean cb = extractColorFromUserInfo(ub);
                    if (isValidElo(ra) && isValidElo(rb)) {
                        if (ca != null) {
                            return ca ? new EloPair(ra, rb, "reflection_pair_" + clazz.getSimpleName(), true)
                                      : new EloPair(rb, ra, "reflection_pair_" + clazz.getSimpleName(), true);
                        } else if (cb != null) {
                            return cb ? new EloPair(rb, ra, "reflection_pair_" + clazz.getSimpleName(), true)
                                      : new EloPair(ra, rb, "reflection_pair_" + clazz.getSimpleName(), true);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // D. Recursive fields search
        if (depth < 2) {
            try {
                for (Field f : clazz.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object child = f.get(obj);
                        if (child != null) {
                            if (child.getClass().getName().contains("Lazy")) {
                                child = unwrapLazy(child);
                            }
                            if (child != null && !isFrameworkClass(child.getClass().getName())) {
                                EloPair childPair = inspectObjectForElo(child, depth + 1);
                                if (childPair != null) return childPair;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private static Method findMethod(Class<?> clazz, String... names) {
        for (String n : names) {
            try {
                Method m = clazz.getMethod(n);
                if (m.getParameterCount() == 0) return m;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static int extractRatingFromUserInfo(Object userInfo) {
        if (userInfo == null) return -1;
        try {
            Method m = findMethod(userInfo.getClass(), "getRating", "rating");
            if (m != null) {
                Object r = m.invoke(userInfo);
                if (r instanceof Number) return ((Number) r).intValue();
            }
        } catch (Throwable ignored) {}
        try {
            Field f = userInfo.getClass().getDeclaredField("rating");
            f.setAccessible(true);
            Object r = f.get(userInfo);
            if (r instanceof Number) return ((Number) r).intValue();
        } catch (Throwable ignored) {}
        return -1;
    }

    private static Boolean extractColorFromUserInfo(Object userInfo) {
        if (userInfo == null) return null;
        try {
            Method m = findMethod(userInfo.getClass(), "getColor", "color");
            if (m != null) {
                Object c = m.invoke(userInfo);
                if (c != null) {
                    String s = c.toString().toUpperCase(java.util.Locale.US);
                    if (s.contains("WHITE")) return Boolean.TRUE;
                    if (s.contains("BLACK")) return Boolean.FALSE;
                }
            }
        } catch (Throwable ignored) {}
        try {
            Field f = userInfo.getClass().getDeclaredField("color");
            f.setAccessible(true);
            Object c = f.get(userInfo);
            if (c != null) {
                String s = c.toString().toUpperCase(java.util.Locale.US);
                if (s.contains("WHITE")) return Boolean.TRUE;
                if (s.contains("BLACK")) return Boolean.FALSE;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isValidElo(int val) {
        return val >= 100 && val <= 3800;
    }
}
