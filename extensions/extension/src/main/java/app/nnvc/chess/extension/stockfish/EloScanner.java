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

    private static class ViewSnapshot {
        final String text;
        final int y;
        ViewSnapshot(String text, int y) {
            this.text = text;
            this.y = y;
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
        // Tránh quét dồn dập: giãn cách tối thiểu 3 giây
        if (now - lastScanTimeMs < 3000) {
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

    /**
     * Nhận diện cặp Elo theo thứ tự ưu tiên:
     * 1. Deep Reflection từ Model/State/Activity
     * 2. Quét View Hierarchy theo toạ độ bàn cờ
     * 3. Fallback: Cài đặt người dùng
     */
    public static EloPair detectEloPair(Activity activity, Object stateImplObject) {
        if (activity == null) return null;

        // ── 1. Thử qua Deep Reflection ──
        try {
            EloPair reflectPair = tryReflectionElo(activity, stateImplObject);
            if (reflectPair != null) {
                TorchEngine.log("[ELO DEBUG] Reflection thành công: " + reflectPair);
                return reflectPair;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Lỗi tryReflectionElo: " + t.getMessage());
        }

        // ── 2. Quét View Hierarchy theo toạ độ bàn cờ ──
        try {
            EloPair viewPair = tryScanViewHierarchy(activity, stateImplObject);
            if (viewPair != null) {
                TorchEngine.log("[ELO DEBUG] Quét View thành công: " + viewPair);
                return viewPair;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Lỗi tryScanViewHierarchy: " + t.getMessage());
        }

        // ── 3. Fallback an toàn: Dùng Elo cài đặt từ người dùng ──
        int defaultElo = StockfishSettings.getElo(activity);
        return new EloPair(defaultElo, defaultElo, "cài đặt mặc định", false);
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

    private static EloPair tryScanViewHierarchy(Activity activity, Object stateImpl) {
        final List<ViewSnapshot> snapshots = new ArrayList<>();
        final int[] boardData = new int[3]; // [boardY, boardH, screenH]
        final CountDownLatch latch = new CountDownLatch(1);

        // Snapshot cực nhanh trên UI Thread (< 1ms, không reflection, không regex)
        activity.runOnUiThread(() -> {
            try {
                if (activity.getWindow() != null && activity.getWindow().getDecorView() != null) {
                    View decorView = activity.getWindow().getDecorView();
                    boardData[2] = decorView.getHeight();

                    View boardView = OverlayManager.findChessBoardView(decorView);
                    if (boardView != null && boardView.getWidth() > 0 && boardView.getHeight() > 0) {
                        int[] loc = new int[2];
                        boardView.getLocationOnScreen(loc);
                        boardData[0] = loc[1];
                        boardData[1] = boardView.getHeight();
                    }
                    collectSnapshots(decorView, snapshots);
                }
            } catch (Throwable ignored) {
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(300, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {}

        if (snapshots.isEmpty()) return null;

        // Xử lý và tính toán vị trí trên Background Thread
        int boardY = boardData[0];
        int boardH = boardData[1];
        int screenH = boardData[2];

        int boardCenterY;
        if (boardH > 0) {
            boardCenterY = boardY + (boardH / 2);
        } else {
            boardCenterY = screenH > 0 ? (screenH / 2) : 1000;
        }

        Integer topZoneElo = null;
        Integer bottomZoneElo = null;
        int closestTopDist = Integer.MAX_VALUE;
        int closestBottomDist = Integer.MAX_VALUE;

        for (ViewSnapshot snap : snapshots) {
            Integer parsed = parseStrictElo(snap.text);
            if (parsed == null) continue;

            int tvY = snap.y;
            if (tvY < boardCenterY) {
                int dist = boardCenterY - tvY;
                if (dist < closestTopDist) {
                    closestTopDist = dist;
                    topZoneElo = parsed;
                }
            } else {
                int dist = tvY - boardCenterY;
                if (dist < closestBottomDist) {
                    closestBottomDist = dist;
                    bottomZoneElo = parsed;
                }
            }
        }

        TorchEngine.log("[ELO DEBUG] Views count=" + snapshots.size() + ", TopZoneElo=" + topZoneElo + ", BottomZoneElo=" + bottomZoneElo);

        if (topZoneElo != null || bottomZoneElo != null) {
            boolean flipped = OverlayManager.isBoardFlipped(stateImpl);
            int def = StockfishSettings.getElo(activity);
            int top = (topZoneElo != null) ? topZoneElo : def;
            int bot = (bottomZoneElo != null) ? bottomZoneElo : def;

            int finalWhite;
            int finalBlack;
            if (!flipped) {
                finalWhite = bot;
                finalBlack = top;
            } else {
                finalWhite = top;
                finalBlack = bot;
            }

            return new EloPair(finalWhite, finalBlack, "view_hierarchy_zones", true);
        }

        return null;
    }

    private static void collectSnapshots(View root, List<ViewSnapshot> out) {
        if (root == null || root.getVisibility() != View.VISIBLE) return;

        // 1. Quét sâu qua Jetpack Compose Semantics (Nếu view là ComposeView hoặc AndroidComposeView)
        try {
            collectComposeSnapshots(root, out);
        } catch (Throwable ignored) {}

        // 2. Quét sâu qua AccessibilityNodeInfo
        try {
            AccessibilityNodeInfo rootNode = root.createAccessibilityNodeInfo();
            if (rootNode != null) {
                collectAccessibilitySnapshots(rootNode, out);
                try {
                    rootNode.recycle();
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // 3. Dự phòng: Quét qua cây View/TextView Android truyền thống
        collectViewSnapshots(root, out);
    }

    private static void collectComposeSnapshots(View view, List<ViewSnapshot> out) {
        if (view == null) return;
        String clsName = view.getClass().getName();
        if (clsName.contains("AndroidComposeView") || clsName.contains("ComposeView")) {
            try {
                // Thử lấy SemanticsOwner (z trên AndroidComposeView hoặc getSemanticsOwner())
                Object semanticsOwner = null;
                try {
                    Method mOwner = findMethod(view.getClass(), "getSemanticsOwner");
                    if (mOwner != null) semanticsOwner = mOwner.invoke(view);
                } catch (Throwable ignored) {}

                if (semanticsOwner == null) {
                    try {
                        Field fz = view.getClass().getDeclaredField("z");
                        fz.setAccessible(true);
                        semanticsOwner = fz.get(view);
                    } catch (Throwable ignored) {}
                }

                if (semanticsOwner != null) {
                    // SemanticsOwner.d() trả về SemanticsNode gốc
                    Method md = findMethod(semanticsOwner.getClass(), "d");
                    if (md != null) {
                        Object rootNode = md.invoke(semanticsOwner);
                        if (rootNode != null) {
                            collectFromSemanticsNode(rootNode, out);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            int count = g.getChildCount();
            for (int i = 0; i < count; i++) {
                collectComposeSnapshots(g.getChildAt(i), out);
            }
        }
    }

    private static void collectFromSemanticsNode(Object semanticsNode, List<ViewSnapshot> out) {
        if (semanticsNode == null) return;
        try {
            // Lấy toạ độ y
            int nodeY = 0;
            try {
                Method mf = findMethod(semanticsNode.getClass(), "f"); // NodeCoordinator
                if (mf != null) {
                    Object coordinator = mf.invoke(semanticsNode);
                    if (coordinator != null) {
                        Method mPos = findMethod(coordinator.getClass(), "c");
                        // vị trí toạ độ
                    }
                }
            } catch (Throwable ignored) {}

            // Lấy text/contentDescription từ config (field 'd' trên SemanticsNode: com.google.android.seb)
            Field fd = semanticsNode.getClass().getDeclaredField("d");
            fd.setAccessible(true);
            Object config = fd.get(semanticsNode);
            if (config != null) {
                // config có field 'b' : Map<SemanticsPropertyKey, Object>
                Field fb = config.getClass().getDeclaredField("b");
                fb.setAccessible(true);
                Object mapObj = fb.get(config);
                if (mapObj instanceof java.util.Map) {
                    java.util.Map<?, ?> map = (java.util.Map<?, ?>) mapObj;
                    for (Object val : map.values()) {
                        if (val != null) {
                            if (val instanceof CharSequence) {
                                out.add(new ViewSnapshot(val.toString(), nodeY));
                            } else if (val instanceof java.util.List) {
                                for (Object elem : (java.util.List<?>) val) {
                                    if (elem instanceof CharSequence) {
                                        out.add(new ViewSnapshot(elem.toString(), nodeY));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Đệ quy duyệt children của SemanticsNode: gọi g() hoặc h() trả về List<SemanticsNode>
            Method mg = findMethod(semanticsNode.getClass(), "g", "h");
            if (mg != null) {
                Object children = mg.invoke(semanticsNode);
                if (children instanceof java.util.List) {
                    for (Object child : (java.util.List<?>) children) {
                        collectFromSemanticsNode(child, out);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void collectAccessibilitySnapshots(AccessibilityNodeInfo node, List<ViewSnapshot> out) {
        if (node == null || !node.isVisibleToUser()) return;

        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            out.add(new ViewSnapshot(text.toString(), bounds.top));
        } else {
            CharSequence desc = node.getContentDescription();
            if (desc != null && desc.length() > 0) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                out.add(new ViewSnapshot(desc.toString(), bounds.top));
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectAccessibilitySnapshots(child, out);
                try {
                    child.recycle();
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void collectViewSnapshots(View root, List<ViewSnapshot> out) {
        if (root == null || root.getVisibility() != View.VISIBLE) return;
        if (root instanceof TextView) {
            CharSequence cs = ((TextView) root).getText();
            if (cs != null && cs.length() > 0) {
                int[] loc = new int[2];
                root.getLocationOnScreen(loc);
                out.add(new ViewSnapshot(cs.toString(), loc[1]));
            }
        } else if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            int count = g.getChildCount();
            for (int i = 0; i < count; i++) {
                collectViewSnapshots(g.getChildAt(i), out);
            }
        }
    }

    /**
     * Bộ lọc nghiêm ngặt (Strict Filter):
     * Nhận diện chính xác số Elo từ 100 đến 3800.
     * Hỗ trợ text nhiều dòng, tên kèm rating trong ngoặc, từ khoá Elo/Rapid/Blitz.
     * Loại bỏ triệt để: đồng hồ đếm ngược (10:00, 3:15), phép chia thời gian, chênh lệch quân.
     */
    public static Integer parseStrictElo(String rawText) {
        if (rawText == null) return null;
        String s = rawText.trim();
        if (s.isEmpty()) return null;

        String[] lines = s.split("\\r?\\n");
        for (String line : lines) {
            Integer res = parseStrictEloLine(line.trim());
            if (res != null) return res;
        }
        return null;
    }

    private static Integer parseStrictEloLine(String s) {
        if (s == null || s.isEmpty()) return null;

        // 1. Loại bỏ định dạng đồng hồ đếm ngược (e.g. 10:00, 3:15, 0:45)
        if (Pattern.compile("\\b\\d{1,2}:\\d{2}\\b").matcher(s).find()) {
            return null;
        }

        // 2. Loại bỏ đơn vị thời gian (min, sec, phút, giây) và phép chia thời lượng (3|2, 5+3)
        String lower = s.toLowerCase(java.util.Locale.US);
        if (lower.contains("min") || lower.contains("sec") || lower.contains("phút") || lower.contains("giây")) {
            return null;
        }
        if (Pattern.compile("\\b\\d+\\s*[|+x]\\s*\\d+\\b").matcher(s).find()) {
            return null;
        }

        // 3. Loại bỏ điểm chênh lệch quân cờ (+1, -3)
        if (Pattern.compile("^[+-]\\d+$").matcher(s).matches()) {
            return null;
        }

        // 4. Ưu tiên số trong ngoặc đơn (dùng find() để bắt trúng ngay cả khi có emoji/cờ/ký tự lạ)
        Matcher mBracket = Pattern.compile("\\((\\d{3,4})\\??\\)").matcher(s);
        if (mBracket.find()) {
            try {
                int val = Integer.parseInt(mBracket.group(1));
                if (isValidElo(val)) return val;
            } catch (Throwable ignored) {}
        }

        // 5. Dạng kèm từ khoá: "Rating: 1500", "Elo: 1500", "1500 Rapid", "Blitz • 1820"
        Matcher mWord = Pattern.compile("(?:rating|elo|rapid|blitz|bullet|daily)\\s*[:•\\-\\s]\\s*\\(?(\\d{3,4})\\??\\)?", Pattern.CASE_INSENSITIVE).matcher(s);
        if (mWord.find()) {
            try {
                int val = Integer.parseInt(mWord.group(1));
                if (isValidElo(val)) return val;
            } catch (Throwable ignored) {}
        }

        Matcher mWordRev = Pattern.compile("\\(?(\\d{3,4})\\??\\)?\\s*[:•\\-\\s]\\s*(?:rating|elo|rapid|blitz|bullet|daily)", Pattern.CASE_INSENSITIVE).matcher(s);
        if (mWordRev.find()) {
            try {
                int val = Integer.parseInt(mWordRev.group(1));
                if (isValidElo(val)) return val;
            } catch (Throwable ignored) {}
        }

        // 6. Số đứng độc lập hoặc trong ngoặc: "1500", "(1500)"
        Matcher mDirect = Pattern.compile("^\\(?(\\d{3,4})\\??\\)?$").matcher(s);
        if (mDirect.matches()) {
            try {
                int val = Integer.parseInt(mDirect.group(1));
                if (isValidElo(val)) return val;
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private static boolean isValidElo(int val) {
        return val >= 100 && val <= 3800;
    }
}
