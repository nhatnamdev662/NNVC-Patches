package app.nnvc.chess.extension.titles;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import app.nnvc.chess.extension.stockfish.StockfishExtension;
import app.nnvc.chess.extension.stockfish.StockfishSettings;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CustomTitlesExtension {
    private static final String TAG = "NNVC";

    private static void logE(String msg) {
        try {
            Log.e(TAG, msg);
        } catch (Throwable ignored) {}
    }

    private static void logE(String msg, Throwable t) {
        try {
            Log.e(TAG, msg, t);
        } catch (Throwable ignored) {}
    }

    // ── Static Caches for Ultra-Fast UI Frame Performance (0ms lag) ──
    private static final Map<String, Object> PALETTE_TITLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> CODE_TO_ENUM_NAME = new HashMap<>();
    private static final Map<Class<?>, Field> USERNAME_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> CHESS_TITLE_FIELD_CACHE = new ConcurrentHashMap<>();

    private static volatile String cachedUsername = null;
    private static volatile long lastUsernameCheckTime = 0;

    static {
        CODE_TO_ENUM_NAME.put("GM", "GRANDMASTER");
        CODE_TO_ENUM_NAME.put("WGM", "WOMAN_GRANDMASTER");
        CODE_TO_ENUM_NAME.put("IM", "INTERNATIONAL_MASTER");
        CODE_TO_ENUM_NAME.put("WIM", "WOMAN_INTERNATIONAL_MASTER");
        CODE_TO_ENUM_NAME.put("FM", "FIDE_MASTER");
        CODE_TO_ENUM_NAME.put("WFM", "WOMAN_FIDE_MASTER");
        CODE_TO_ENUM_NAME.put("NM", "NATIONAL_MASTER");
        CODE_TO_ENUM_NAME.put("CM", "FIDE_CANDIDATE_MASTER");
        CODE_TO_ENUM_NAME.put("WCM", "FIDE_WOMAN_CANDIDATE_MASTER");
        CODE_TO_ENUM_NAME.put("WNM", "WOMAN_NATIONAL_MASTER");
        CODE_TO_ENUM_NAME.put("M", "MASTER");
    }

    // Khởi tạo hoàn toàn local, không tải file mạng, áp dụng tức thời 0ms
    public static void init() {
        // Pre-warm palette title cache on app startup if classes are ready
        try {
            Class<?> chessTitleClass = Class.forName("com.chess.palette.compose.component.ChessTitle");
            Method valueOfMethod = chessTitleClass.getMethod("valueOf", String.class);
            for (Map.Entry<String, String> entry : CODE_TO_ENUM_NAME.entrySet()) {
                try {
                    Object val = valueOfMethod.invoke(null, entry.getValue());
                    if (val != null) {
                        PALETTE_TITLE_CACHE.put(entry.getKey(), val);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Tự động nhận diện username của tài khoản người dùng đang đăng nhập trên máy.
     * Có bộ đệm 3000ms tránh nghẽn I/O SharedPreferences trên main thread của Compose.
     */
    public static String getCurrentAppUsername(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastUsernameCheckTime < 3000 && cachedUsername != null) {
            return cachedUsername;
        }

        if (context == null) {
            try {
                context = StockfishExtension.getContext();
            } catch (Throwable ignored) {
                context = null;
            }
        }
        if (context == null) {
            return cachedUsername;
        }

        // 1. Kiểm tra cấu hình người dùng nhập thủ công trong settings
        try {
            String manualUser = StockfishSettings.getCustomTitleUser(context);
            if (manualUser != null && !manualUser.trim().isEmpty()) {
                cachedUsername = manualUser.trim();
                lastUsernameCheckTime = now;
                return cachedUsername;
            }
        } catch (Throwable ignored) {}

        // 2. Nhận diện từ SharedPreferences session của Chess.com
        try {
            SharedPreferences sp = context.getSharedPreferences("com.chess.app.session_preferences", Context.MODE_PRIVATE);
            String u = sp.getString("pref_username", null);
            if (u != null && !u.trim().isEmpty()) {
                cachedUsername = u.trim();
                lastUsernameCheckTime = now;
                return cachedUsername;
            }
        } catch (Throwable ignored) {}

        // 3. Fallback nhận diện từ SharedPreferences login_credentials
        try {
            SharedPreferences sp = context.getSharedPreferences("com.chess.app.login_credentials", Context.MODE_PRIVATE);
            String u = sp.getString("pref_username_or_email", null);
            if (u != null && !u.trim().isEmpty()) {
                cachedUsername = u.trim();
                lastUsernameCheckTime = now;
                return cachedUsername;
            }
        } catch (Throwable ignored) {}

        return cachedUsername;
    }

    public static void setCachedUsernameForTest(String u) {
        cachedUsername = u;
        lastUsernameCheckTime = System.currentTimeMillis();
    }

    /**
     * Kiểm tra xem một đối tượng hoặc username có thuộc về chính người dùng đang chơi hay không.
     * Đảm bảo CHỈ áp dụng danh hiệu fake cho chính tài khoản của người dùng, không gán nhầm cho đối thủ!
     */
    public static boolean isTargetCurrentUser(Context context, String targetUsername, Object containerObj) {
        if (containerObj != null) {
            String className = containerObj.getClass().getName();
            // LoginData luôn là session data của chính người dùng đăng nhập
            if (className.contains("LoginData")) {
                return true;
            }
        }

        String currentAppUser = getCurrentAppUsername(context);
        if (currentAppUser != null && !currentAppUser.isEmpty()) {
            if (targetUsername != null && !targetUsername.trim().isEmpty()) {
                return targetUsername.trim().equalsIgnoreCase(currentAppUser);
            }
        }
        return false;
    }

    public static Context getSafeContext(Context context) {
        if (context != null) return context;
        try {
            return StockfishExtension.getContext();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Xác định mã danh hiệu cuối cùng (GM, IM, FM, CM, NM, WGM...) áp dụng cho đối tượng.
     * Hoạt động 100% Real-Time và độc lập với mạng.
     */
    public static String getResolvedTitle(Context context, String username, Object containerObj, String defaultTitle) {
        context = getSafeContext(context);

        boolean customTitleEnabled = true;
        if (context != null) {
            try {
                customTitleEnabled = StockfishSettings.isCustomTitleEnabled(context);
            } catch (Throwable ignored) {
                customTitleEnabled = true;
            }
        }

        if (customTitleEnabled && isTargetCurrentUser(context, username, containerObj)) {
            // Đúng là tài khoản của người dùng và tính năng Fake Title đang bật
            String customCode = "GM";
            if (context != null) {
                try {
                    customCode = StockfishSettings.getCustomTitleCode(context);
                } catch (Throwable ignored) {
                    customCode = "GM";
                }
            }
            if (customCode != null && !customCode.trim().isEmpty()) {
                return customCode.trim().toUpperCase();
            }
            return "GM";
        }

        // Không phải tài khoản của người dùng (đối thủ hoặc người khác):
        // Giữ nguyên 100% danh hiệu gốc của đối thủ, tuyệt đối không can thiệp!
        return defaultTitle;
    }

    public static String getOverride(String username, String defaultTitle) {
        return getResolvedTitle(null, username, null, defaultTitle);
    }

    public static String getOverrideOrNull(String username) {
        return getResolvedTitle(null, username, null, null);
    }

    public static Object getOverrideAsChessTitle(String titleCode, Object defaultTitleObj) {
        if (titleCode == null || titleCode.trim().isEmpty()) {
            return defaultTitleObj;
        }
        String upper = titleCode.trim().toUpperCase();
        try {
            Class<?> chessTitleClass = Class.forName("com.chess.entities.ChessTitle");
            String enumName = CODE_TO_ENUM_NAME.get(upper);
            if (enumName != null) {
                try {
                    Field f = chessTitleClass.getField(enumName);
                    Object val = f.get(null);
                    if (val != null) {
                        return val;
                    }
                } catch (Throwable ignored) {}
            }

            Class<?> companionClass = Class.forName("com.chess.entities.ChessTitle$Companion");
            Object instance = null;
            try {
                instance = chessTitleClass.getField("Companion").get(null);
            } catch (Throwable t) {
                instance = chessTitleClass.getField("INSTANCE").get(null);
            }
            Method fromCode = companionClass.getMethod("fromCode", String.class);
            Object overrideObj = fromCode.invoke(instance, upper);
            return overrideObj != null ? overrideObj : defaultTitleObj;
        } catch (Throwable e) {
            return defaultTitleObj;
        }
    }

    /**
     * Chuyển mã danh hiệu (GM, IM...) sang enum Palette ChessTitle tương ứng.
     * Tối ưu hóa cực hạn: O(1) cache lookup, không reflection lặp, không cấp phát bộ nhớ.
     */
    public static Object getOverrideAsPaletteChessTitle(String titleCode, Object defaultTitleObj) {
        if (titleCode == null || titleCode.trim().isEmpty()) {
            return defaultTitleObj;
        }
        String upper = titleCode.trim().toUpperCase();

        // 1. Fast path: Lấy trực tiếp từ cache đã tối ưu hóa
        Object cached = PALETTE_TITLE_CACHE.get(upper);
        if (cached != null) {
            return cached;
        }

        try {
            Class<?> chessTitleClass = Class.forName("com.chess.palette.compose.component.ChessTitle");

            // 2. Map từ Code ("GM") sang Tên enum chuẩn ("GRANDMASTER")
            String enumName = CODE_TO_ENUM_NAME.get(upper);
            if (enumName != null) {
                try {
                    Method valueOfMethod = chessTitleClass.getMethod("valueOf", String.class);
                    Object val = valueOfMethod.invoke(null, enumName);
                    if (val != null) {
                        PALETTE_TITLE_CACHE.put(upper, val);
                        return val;
                    }
                } catch (Throwable ignored) {}
            }

            // 3. Quét values() một lần duy nhất nếu enum chưa có trong map
            Method valuesMethod = chessTitleClass.getMethod("values");
            Object[] values = (Object[]) valuesMethod.invoke(null);
            if (values != null) {
                for (Object title : values) {
                    if (title != null && title.toString().equalsIgnoreCase(upper)) {
                        PALETTE_TITLE_CACHE.put(upper, title);
                        return title;
                    }
                }
                for (Field f : chessTitleClass.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                        f.setAccessible(true);
                        for (Object title : values) {
                            if (title != null) {
                                String code = (String) f.get(title);
                                if (upper.equalsIgnoreCase(code)) {
                                    PALETTE_TITLE_CACHE.put(upper, title);
                                    return title;
                                }
                            }
                        }
                        break;
                    }
                }
            }
            return defaultTitleObj;
        } catch (Throwable e) {
            logE("getOverrideAsPaletteChessTitle failed: " + e.getMessage());
            return defaultTitleObj;
        }
    }

    public static boolean isValidUsername(String s) {
        if (s == null) return false;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.contains("/")) {
            return false;
        }
        return true;
    }

    /**
     * Trích xuất username từ UI State object với hiệu năng cao, tránh gọi toString() liên tục.
     */
    public static String extractUsername(Object obj) {
        if (obj == null) return null;
        Class<?> clazz = obj.getClass();

        // 1. Thử lấy từ cache Field đã phát hiện trước đó
        Field cachedField = USERNAME_FIELD_CACHE.get(clazz);
        if (cachedField != null) {
            try {
                Object u = cachedField.get(obj);
                if (u instanceof String && isValidUsername((String) u)) {
                    return ((String) u).trim();
                }
            } catch (Throwable ignored) {}
        }

        // 2. Thử trực tiếp các trường phổ biến: "username", "b" (UserProfileHeader.b), "a" (UsernameUiState.a)
        for (String fieldName : new String[]{"username", "b", "a"}) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
                if (f.getType() == String.class) {
                    f.setAccessible(true);
                    Object u = f.get(obj);
                    if (u instanceof String && isValidUsername((String) u)) {
                        USERNAME_FIELD_CACHE.put(clazz, f);
                        return ((String) u).trim();
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. Thử qua getter method
        try {
            Method getUsernameMethod = clazz.getMethod("getUsername");
            Object u = getUsernameMethod.invoke(obj);
            if (u instanceof String && isValidUsername((String) u)) {
                return ((String) u).trim();
            }
        } catch (Throwable ignored) {}

        // 4. Fallback cuối cùng: toString parsing
        try {
            String str = obj.toString();
            int index = str.indexOf("username=");
            if (index != -1) {
                int start = index + "username=".length();
                int endComma = str.indexOf(",", start);
                int endParen = str.indexOf(")", start);
                int end = -1;
                if (endComma != -1 && endParen != -1) {
                    end = Math.min(endComma, endParen);
                } else if (endComma != -1) {
                    end = endComma;
                } else if (endParen != -1) {
                    end = endParen;
                }
                if (end != -1) {
                    String username = str.substring(start, end).trim();
                    if (!"null".equalsIgnoreCase(username) && isValidUsername(username)) {
                        return username;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static String overrideModelChessTitle(Object modelObj) {
        if (modelObj == null) {
            return null;
        }
        String defaultTitle = null;
        try {
            String username = extractUsername(modelObj);

            Class<?> clazz = modelObj.getClass();
            Field chessTitleField = CHESS_TITLE_FIELD_CACHE.get(clazz);
            if (chessTitleField == null) {
                for (String fieldName : new String[]{"chess_title", "chessTitle", "f"}) {
                    try {
                        Field f = clazz.getDeclaredField(fieldName);
                        if (f.getType() == String.class) {
                            chessTitleField = f;
                            break;
                        }
                    } catch (NoSuchFieldException ignored) {}
                }
                if (chessTitleField == null) {
                    for (Field f : clazz.getDeclaredFields()) {
                        if (f.getType() == String.class && (f.getName().contains("title") || f.getName().contains("Title"))) {
                            chessTitleField = f;
                            break;
                        }
                    }
                }
                if (chessTitleField != null) {
                    chessTitleField.setAccessible(true);
                    CHESS_TITLE_FIELD_CACHE.put(clazz, chessTitleField);
                }
            }

            if (chessTitleField != null) {
                defaultTitle = (String) chessTitleField.get(modelObj);
            }

            return getResolvedTitle(null, username, modelObj, defaultTitle);
        } catch (Throwable e) {
            logE("overrideModelChessTitle failed: " + e.getMessage());
            return defaultTitle;
        }
    }

    public static Object overrideEntitiesChessTitle(Object obj) {
        if (obj == null) {
            return null;
        }
        Object defaultTitleObj = null;
        try {
            Class<?> clazz = obj.getClass();
            Field titleField = null;
            for (String fn : new String[]{"f", "chessTitle"}) {
                try {
                    Field f = clazz.getDeclaredField(fn);
                    if (f.getType().getName().contains("ChessTitle") || f.getType() == Object.class) {
                        titleField = f;
                        break;
                    }
                } catch (NoSuchFieldException ignored) {}
            }
            if (titleField != null) {
                titleField.setAccessible(true);
                defaultTitleObj = titleField.get(obj);
            }

            String username = extractUsername(obj);

            Context context = getSafeContext(null);
            boolean customEnabled = true;
            if (context != null) {
                try {
                    customEnabled = StockfishSettings.isCustomTitleEnabled(context);
                } catch (Throwable ignored) {
                    customEnabled = true;
                }
            }
            if (customEnabled && isTargetCurrentUser(context, username, obj)) {
                String customCode = "GM";
                if (context != null) {
                    try {
                        customCode = StockfishSettings.getCustomTitleCode(context);
                    } catch (Throwable ignored) {
                        customCode = "GM";
                    }
                }
                if (customCode == null || customCode.trim().isEmpty()) {
                    customCode = "GM";
                }
                Object customTitle = getOverrideAsChessTitle(customCode.trim().toUpperCase(), defaultTitleObj);
                return customTitle != null ? customTitle : defaultTitleObj;
            }
            return defaultTitleObj;
        } catch (Throwable t) {
            logE("overrideEntitiesChessTitle failed: " + t.getMessage(), t);
            return defaultTitleObj;
        }
    }

    public static Object overridePaletteChessTitle(Object uiStateObj) {
        if (uiStateObj == null) {
            return null;
        }
        Object defaultTitleObj = null;
        try {
            String username = extractUsername(uiStateObj);

            Class<?> clazz = uiStateObj.getClass();
            Field chessTitleField = CHESS_TITLE_FIELD_CACHE.get(clazz);
            if (chessTitleField == null) {
                try {
                    chessTitleField = clazz.getDeclaredField("chessTitle");
                } catch (NoSuchFieldException e) {
                    for (String fn : new String[]{"f", "chessTitle"}) {
                        try {
                            Field f = clazz.getDeclaredField(fn);
                            if (f.getType().getName().contains("ChessTitle")) {
                                chessTitleField = f;
                                break;
                            }
                        } catch (Throwable ignored) {}
                    }
                    if (chessTitleField == null) {
                        for (Field f : clazz.getDeclaredFields()) {
                            if (f.getType().getName().contains("palette") && f.getType().getName().contains("ChessTitle")) {
                                chessTitleField = f;
                                break;
                            }
                        }
                    }
                }
                if (chessTitleField != null) {
                    chessTitleField.setAccessible(true);
                    CHESS_TITLE_FIELD_CACHE.put(clazz, chessTitleField);
                }
            }

            if (chessTitleField != null) {
                defaultTitleObj = chessTitleField.get(uiStateObj);
            }

            String defaultTitleCode = defaultTitleObj != null ? defaultTitleObj.toString() : null;
            String resolvedTitle = getResolvedTitle(null, username, uiStateObj, defaultTitleCode);

            if (resolvedTitle != null) {
                Object titleObj = getOverrideAsPaletteChessTitle(resolvedTitle, defaultTitleObj);
                try {
                    for (String bfName : new String[]{"hasBadge", "hasTitle", "k", "i"}) {
                        try {
                            Field bf = clazz.getDeclaredField(bfName);
                            if (bf.getType() == boolean.class) {
                                bf.setAccessible(true);
                                bf.setBoolean(uiStateObj, true);
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
                return titleObj;
            }
            return defaultTitleObj;
        } catch (Throwable e) {
            logE("overridePaletteChessTitle failed: " + e.getMessage());
            return defaultTitleObj;
        }
    }

    /**
     * Hiển thị thẻ banner thông tin danh hiệu FIDE [ 👑 Đại kiện tướng / FIDE Title ]
     * trên trang cá nhân Profile cho chính người dùng đang đăng nhập.
     * Bảo đảm 100% không crash, không văng màn hình, luôn trả về danh sách non-null
     * theo đúng hợp đồng của Kotlin.
     */
    public static List<?> overrideProfileLabels(Object uiModel) {
        if (uiModel == null) {
            return Collections.emptyList();
        }
        List<?> originalLabels = null;
        try {
            Field labelsField = null;
            for (String fieldName : new String[]{"e", "labels"}) {
                try {
                    labelsField = uiModel.getClass().getDeclaredField(fieldName);
                    break;
                } catch (Throwable ignored) {}
            }
            if (labelsField != null) {
                labelsField.setAccessible(true);
                originalLabels = (List<?>) labelsField.get(uiModel);
            }
        } catch (Throwable ignored) {}

        try {
            Context context = getSafeContext(null);
            boolean customEnabled = true;
            if (context != null) {
                try {
                    customEnabled = StockfishSettings.isCustomTitleEnabled(context);
                } catch (Throwable ignored) {
                    customEnabled = true;
                }
            }

            if (customEnabled) {
                // Nhận diện tài khoản người dùng: chỉ can thiệp trên chính profile của người dùng
                boolean isCurrentUser = false;
                try {
                    Method wMethod = uiModel.getClass().getMethod("w");
                    isCurrentUser = ((Boolean) wMethod.invoke(uiModel)).booleanValue();
                } catch (Throwable t1) {
                    try {
                        Method isCurMethod = uiModel.getClass().getMethod("isCurrentUser");
                        isCurrentUser = ((Boolean) isCurMethod.invoke(uiModel)).booleanValue();
                    } catch (Throwable t2) {
                        try {
                            Field mField = uiModel.getClass().getDeclaredField("m");
                            mField.setAccessible(true);
                            isCurrentUser = ((Boolean) mField.get(uiModel)).booleanValue();
                        } catch (Throwable ignored) {}
                    }
                }

                if (!isCurrentUser) {
                    try {
                        Field headerField = null;
                        for (String fn : new String[]{"d", "header"}) {
                            try {
                                headerField = uiModel.getClass().getDeclaredField(fn);
                                break;
                            } catch (Throwable ignored) {}
                        }
                        if (headerField != null) {
                            headerField.setAccessible(true);
                            Object headerObj = headerField.get(uiModel);
                            if (headerObj != null) {
                                String username = extractUsername(headerObj);
                                if (username != null && isTargetCurrentUser(context, username, headerObj)) {
                                    isCurrentUser = true;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                // Nếu không phải profile của chính mình: giữ nguyên 100% nhãn gốc của đối thủ/người khác
                if (isCurrentUser) {
                    // Kiểm tra xem danh sách đã có thẻ danh hiệu TitledPlayer chưa (tránh trùng lặp)
                    if (originalLabels != null) {
                        for (Object item : originalLabels) {
                            if (item != null && item.toString().contains("TitledPlayer")) {
                                return originalLabels;
                            }
                        }
                    }

                    String customCode = "GM";
                    if (context != null) {
                        try {
                            customCode = StockfishSettings.getCustomTitleCode(context);
                        } catch (Throwable ignored) {
                            customCode = "GM";
                        }
                    }
                    if (customCode == null || customCode.trim().isEmpty()) {
                        customCode = "GM";
                    }
                    String upperCode = customCode.trim().toUpperCase();

                    Object chessTitleObj = getOverrideAsChessTitle(upperCode, null);
                    if (chessTitleObj == null) {
                        chessTitleObj = getOverrideAsChessTitle("GM", null);
                    }

                    if (chessTitleObj != null) {
                        Class<?> titledPlayerClass = Class.forName("com.chess.net.v1.users.UserLabel$TitledPlayer");
                        Class<?> chessTitleClass = Class.forName("com.chess.entities.ChessTitle");
                        Object titledPlayerObj = titledPlayerClass.getConstructor(chessTitleClass).newInstance(chessTitleObj);

                        Class<?> userLabelUiClass = Class.forName("com.chess.profile.f5");
                        Field companionField = null;
                        for (String cfName : new String[]{"d", "INSTANCE", "Companion"}) {
                            try {
                                companionField = userLabelUiClass.getDeclaredField(cfName);
                                break;
                            } catch (Throwable ignored) {
                                try {
                                    companionField = userLabelUiClass.getField(cfName);
                                    break;
                                } catch (Throwable ignored2) {}
                            }
                        }
                        if (companionField != null) {
                            companionField.setAccessible(true);
                            Object companionObj = companionField.get(null);
                            Method fromMethod = null;
                            for (String mn : new String[]{"a", "from", "fromUserLabel"}) {
                                try {
                                    fromMethod = companionObj.getClass().getMethod(mn, Class.forName("com.chess.net.v1.users.UserLabel"));
                                    break;
                                } catch (Throwable ignored) {}
                            }
                            if (fromMethod != null) {
                                Object userLabelUiObj = fromMethod.invoke(companionObj, titledPlayerObj);

                                if (userLabelUiObj != null) {
                                    java.util.ArrayList<Object> result = new java.util.ArrayList<>();
                                    result.add(userLabelUiObj);
                                    if (originalLabels != null) {
                                        result.addAll(originalLabels);
                                    }
                                    return result;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logE("overrideProfileLabels error: " + t.getMessage(), t);
        }

        return originalLabels != null ? originalLabels : Collections.emptyList();
    }
}
