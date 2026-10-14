package app.nnvc.chess.extension.titles;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import app.nnvc.chess.extension.stockfish.StockfishExtension;
import app.nnvc.chess.extension.stockfish.StockfishSettings;

public class CustomTitlesExtension {
    private static final String TAG = "CustomTitlesExt";

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

    // Khởi tạo hoàn toàn local, không tải file mạng, áp dụng tức thời 0ms
    public static void init() {
        // No network fetch needed - 100% real-time local
    }

    /**
     * Tự động nhận diện username của tài khoản người dùng đang đăng nhập trên máy.
     */
    public static String getCurrentAppUsername(Context context) {
        if (context == null) {
            try {
                context = StockfishExtension.getContext();
            } catch (Throwable ignored) {
                context = null;
            }
        }
        if (context == null) {
            return null;
        }
        // 1. Kiểm tra cấu hình người dùng nhập thủ công trong settings
        try {
            String manualUser = StockfishSettings.getCustomTitleUser(context);
            if (manualUser != null && !manualUser.trim().isEmpty()) {
                return manualUser.trim();
            }
        } catch (Throwable ignored) {}

        // 2. Nhận diện từ SharedPreferences session của Chess.com
        try {
            SharedPreferences sp = context.getSharedPreferences("com.chess.app.session_preferences", Context.MODE_PRIVATE);
            String u = sp.getString("pref_username", null);
            if (u != null && !u.trim().isEmpty()) {
                return u.trim();
            }
        } catch (Throwable ignored) {}

        // 3. Fallback nhận diện từ SharedPreferences login_credentials
        try {
            SharedPreferences sp = context.getSharedPreferences("com.chess.app.login_credentials", Context.MODE_PRIVATE);
            String u = sp.getString("pref_username_or_email", null);
            if (u != null && !u.trim().isEmpty()) {
                return u.trim();
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Kiểm tra xem một đối tượng hoặc username có thuộc về chính người dùng đang chơi hay không.
     * Đảm bảo CHỈ áp dụng danh hiệu fake cho chính tài khoản của người dùng, không gán nhầm cho đối thủ!
     */
    public static boolean isTargetCurrentUser(Context context, String targetUsername, Object containerObj) {
        if (containerObj != null) {
            String className = containerObj.getClass().getName();
            String str = containerObj.toString();
            // LoginData luôn là session data của chính người dùng đăng nhập
            if (className.contains("LoginData") || (str != null && str.contains("LoginData"))) {
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

    /**
     * Xác định mã danh hiệu cuối cùng (GM, IM, FM, CM, NM, WGM...) áp dụng cho đối tượng.
     * Hoạt động 100% Real-Time và độc lập với mạng.
     */
    public static String getResolvedTitle(Context context, String username, Object containerObj, String defaultTitle) {
        if (context == null) {
            try {
                context = StockfishExtension.getContext();
            } catch (Throwable ignored) {
                context = null;
            }
        }

        boolean customTitleEnabled = (context != null) && StockfishSettings.isCustomTitleEnabled(context);
        if (customTitleEnabled && isTargetCurrentUser(context, username, containerObj)) {
            // Đúng là tài khoản của người dùng và tính năng Fake Title đang bật
            String customCode = StockfishSettings.getCustomTitleCode(context);
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
            Class<?> companionClass = Class.forName("com.chess.entities.ChessTitle$Companion");
            Class<?> chessTitleClass = Class.forName("com.chess.entities.ChessTitle");
            Object instance = null;
            try {
                instance = chessTitleClass.getField("Companion").get(null);
            } catch (Throwable t) {
                instance = chessTitleClass.getField("INSTANCE").get(null);
            }
            java.lang.reflect.Method fromCode = companionClass.getMethod("fromCode", String.class);
            Object overrideObj = fromCode.invoke(instance, upper);
            return overrideObj != null ? overrideObj : defaultTitleObj;
        } catch (Exception e) {
            try {
                Class<?> chessTitleClass = Class.forName("com.chess.entities.ChessTitle");
                java.lang.reflect.Method valueOf = chessTitleClass.getMethod("valueOf", String.class);
                return valueOf.invoke(null, upper);
            } catch (Throwable ex) {
                return defaultTitleObj;
            }
        }
    }

    public static Object getOverrideAsPaletteChessTitle(String titleCode, Object defaultTitleObj) {
        if (titleCode == null || titleCode.trim().isEmpty()) {
            return defaultTitleObj;
        }
        String upper = titleCode.trim().toUpperCase();
        try {
            Class<?> chessTitleClass = Class.forName("com.chess.palette.compose.component.ChessTitle");
            // 1. Enum.valueOf()
            try {
                java.lang.reflect.Method valueOfMethod = chessTitleClass.getMethod("valueOf", String.class);
                Object val = valueOfMethod.invoke(null, upper);
                if (val != null) {
                    return val;
                }
            } catch (Throwable ignored) {}

            // 2. Scan values() by name
            java.lang.reflect.Method valuesMethod = chessTitleClass.getMethod("values");
            Object[] values = (Object[]) valuesMethod.invoke(null);
            if (values != null) {
                for (Object title : values) {
                    if (title != null && title.toString().equalsIgnoreCase(upper)) {
                        return title;
                    }
                }
                // 3. Scan values() by field 'code'
                for (java.lang.reflect.Field f : chessTitleClass.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                        f.setAccessible(true);
                        for (Object title : values) {
                            if (title != null) {
                                String code = (String) f.get(title);
                                if (upper.equalsIgnoreCase(code)) {
                                    return title;
                                }
                            }
                        }
                        break;
                    }
                }
            }
            return defaultTitleObj;
        } catch (Exception e) {
            logE("getOverrideAsPaletteChessTitle failed: " + e.getMessage());
            return defaultTitleObj;
        }
    }

    public static String extractUsername(Object obj) {
        if (obj == null) return null;
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
                if (!"null".equalsIgnoreCase(username) && !username.isEmpty()) {
                    return username;
                }
            }
        }

        try {
            java.lang.reflect.Method getUsernameMethod = obj.getClass().getMethod("getUsername");
            Object u = getUsernameMethod.invoke(obj);
            if (u instanceof String && !((String) u).trim().isEmpty()) {
                return ((String) u).trim();
            }
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField("username");
            f.setAccessible(true);
            Object u = f.get(obj);
            if (u instanceof String && !((String) u).trim().isEmpty()) {
                return ((String) u).trim();
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static String overrideModelChessTitle(Object modelObj) {
        if (modelObj == null) {
            return null;
        }
        try {
            String username = extractUsername(modelObj);
            
            java.lang.reflect.Field chessTitleField = null;
            for (String fieldName : new String[]{"chess_title", "chessTitle"}) {
                try {
                    chessTitleField = modelObj.getClass().getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    // Ignore
                }
            }
            
            if (chessTitleField == null) {
                for (java.lang.reflect.Field f : modelObj.getClass().getDeclaredFields()) {
                    if (f.getType() == String.class && (f.getName().contains("title") || f.getName().contains("Title"))) {
                        chessTitleField = f;
                        break;
                    }
                }
            }
            
            String defaultTitle = null;
            if (chessTitleField != null) {
                chessTitleField.setAccessible(true);
                defaultTitle = (String) chessTitleField.get(modelObj);
            }
            
            return getResolvedTitle(StockfishExtension.getContext(), username, modelObj, defaultTitle);
        } catch (Exception e) {
            logE("overrideModelChessTitle failed: " + e.getMessage());
        }
        return null;
    }

    public static Object overridePaletteChessTitle(Object uiStateObj) {
        if (uiStateObj == null) {
            return null;
        }
        try {
            String username = extractUsername(uiStateObj);
            
            java.lang.reflect.Field chessTitleField = null;
            try {
                chessTitleField = uiStateObj.getClass().getDeclaredField("chessTitle");
            } catch (NoSuchFieldException e) {
                for (java.lang.reflect.Field f : uiStateObj.getClass().getDeclaredFields()) {
                    if (f.getType().getName().contains("palette") && f.getType().getName().contains("ChessTitle")) {
                        chessTitleField = f;
                        break;
                    }
                }
            }
            
            Object defaultTitleObj = null;
            if (chessTitleField != null) {
                chessTitleField.setAccessible(true);
                defaultTitleObj = chessTitleField.get(uiStateObj);
            }
            
            String defaultTitleCode = defaultTitleObj != null ? defaultTitleObj.toString() : null;
            String resolvedTitle = getResolvedTitle(StockfishExtension.getContext(), username, uiStateObj, defaultTitleCode);
            
            if (resolvedTitle != null) {
                return getOverrideAsPaletteChessTitle(resolvedTitle, defaultTitleObj);
            }
            return defaultTitleObj;
        } catch (Exception e) {
            logE("overridePaletteChessTitle failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Bổ sung thẻ banner danh hiệu [ 👑 Đại kiện tướng / Title Banner ] trong Trang cá nhân (User Profile).
     * Tự động sinh UserLabelUi chuẩn gốc của Chess.com (Vương miện 👑, nền đỏ thẫm #O, tên danh hiệu đa ngôn ngữ).
     * Tuyệt đối bảo toàn: Chỉ hiển thị cho chính người dùng (isCurrentUser == true), không can thiệp người khác.
     */
    public static java.util.List<?> overrideProfileLabels(Object uiModel) {
        if (uiModel == null) {
            return null;
        }
        java.util.List<?> originalLabels = null;
        try {
            java.lang.reflect.Field labelsField = uiModel.getClass().getDeclaredField("e");
            labelsField.setAccessible(true);
            originalLabels = (java.util.List<?>) labelsField.get(uiModel);
        } catch (Throwable t) {
            try {
                java.lang.reflect.Field labelsField = uiModel.getClass().getDeclaredField("labels");
                labelsField.setAccessible(true);
                originalLabels = (java.util.List<?>) labelsField.get(uiModel);
            } catch (Throwable ignored) {}
        }

        try {
            Context context = StockfishExtension.getContext();
            boolean enabled = (context != null) && StockfishSettings.isCustomTitleEnabled(context);
            if (!enabled) {
                return originalLabels;
            }

            // Kiểm tra xem có phải trang cá nhân của chính người dùng hay không
            boolean isCurrentUser = false;
            try {
                java.lang.reflect.Method isCurrentUserMethod = uiModel.getClass().getMethod("w");
                isCurrentUser = (Boolean) isCurrentUserMethod.invoke(uiModel);
            } catch (Throwable t) {
                try {
                    java.lang.reflect.Field f = uiModel.getClass().getDeclaredField("m");
                    f.setAccessible(true);
                    isCurrentUser = (Boolean) f.get(uiModel);
                } catch (Throwable ignored) {}
            }

            if (!isCurrentUser) {
                return originalLabels;
            }

            String titleCode = StockfishSettings.getCustomTitleCode(context);
            if (titleCode == null || titleCode.trim().isEmpty()) {
                return originalLabels;
            }
            titleCode = titleCode.trim().toUpperCase();

            // Kiểm tra xem danh sách hiện tại đã có thẻ danh hiệu chưa (tránh trùng lặp)
            if (originalLabels != null) {
                for (Object labelUi : originalLabels) {
                    if (labelUi != null && labelUi.toString().contains("TitledPlayer")) {
                        return originalLabels;
                    }
                }
            }

            // 1. Lấy enum com.chess.entities.ChessTitle tương ứng
            Object chessTitleEnum = getOverrideAsChessTitle(titleCode, null);
            if (chessTitleEnum == null) {
                return originalLabels;
            }

            // 2. Khởi tạo đối tượng UserLabel.TitledPlayer
            Class<?> titledPlayerClass = Class.forName("com.chess.net.v1.users.UserLabel$TitledPlayer");
            Class<?> chessTitleClass = Class.forName("com.chess.entities.ChessTitle");
            java.lang.reflect.Constructor<?> tpCtor = titledPlayerClass.getConstructor(chessTitleClass);
            Object titledPlayer = tpCtor.newInstance(chessTitleEnum);

            // 3. Gọi UserLabelUi.Companion.a(titledPlayer) để sinh UserLabelUi chuẩn gốc
            Class<?> userLabelUiClass = Class.forName("com.chess.profile.f5");
            java.lang.reflect.Field uiCompanionField = null;
            try {
                uiCompanionField = userLabelUiClass.getField("INSTANCE");
            } catch (Throwable t) {
                uiCompanionField = userLabelUiClass.getField("d");
            }
            Object uiCompanion = uiCompanionField.get(null);
            java.lang.reflect.Method createMethod = uiCompanion.getClass().getMethod("a", Class.forName("com.chess.net.v1.users.UserLabel"));
            Object userLabelUi = createMethod.invoke(uiCompanion, titledPlayer);

            if (userLabelUi == null) {
                return originalLabels;
            }

            // 4. Tạo danh sách mới với thẻ danh hiệu ở vị trí đầu tiên (Rank 3 trên Rank 4 Premium)
            java.util.List<Object> newList = new java.util.ArrayList<>();
            newList.add(userLabelUi);
            if (originalLabels != null) {
                newList.addAll(originalLabels);
            }
            return newList;
        } catch (Throwable e) {
            logE("overrideProfileLabels failed: " + e.getMessage(), e);
            return originalLabels;
        }
    }
}

