package app.prathxm.chess.extension.titles;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import app.prathxm.chess.extension.stockfish.StockfishExtension;
import app.prathxm.chess.extension.stockfish.StockfishSettings;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONObject;

public class CustomTitlesExtension {
    private static final String TAG = "CustomTitlesExt";
    private static final String JSON_URL = "https://raw.githubusercontent.com/PrathxmOp/my-apis/refs/heads/main/chess.json";
    static final Map<String, String> titleOverrides = new HashMap<>();
    private static boolean isInitialized = false;

    public static void init() {
        if (isInitialized) {
            return;
        }
        isInitialized = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(JSON_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setRequestMethod("GET");
                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                        
                        JSONObject json = new JSONObject(sb.toString());
                        JSONObject titlesJson = json.has("titles") ? json.getJSONObject("titles") : json;
                        
                        synchronized (titleOverrides) {
                            titleOverrides.clear();
                            Iterator<String> keys = titlesJson.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                String value = titlesJson.getString(key);
                                if (value != null && !value.isEmpty()) {
                                    titleOverrides.put(key.toLowerCase().trim(), value.toUpperCase().trim());
                                }
                            }
                        }
                        Log.i(TAG, "Loaded " + titleOverrides.size() + " custom titles from remote.");
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Fetch failed: " + e.getMessage());
                }
            }
        }).start();
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
        // Giữ nguyên danh hiệu thật của họ hoặc kiểm tra override remote từ GitHub nếu có
        if (username != null) {
            synchronized (titleOverrides) {
                String remoteOverride = titleOverrides.get(username.toLowerCase().trim());
                if (remoteOverride != null) {
                    return remoteOverride;
                }
            }
        }

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
            Log.e(TAG, "getOverrideAsPaletteChessTitle failed: " + e.getMessage());
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
            Log.e(TAG, "overrideModelChessTitle failed: " + e.getMessage());
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
            Log.e(TAG, "overridePaletteChessTitle failed: " + e.getMessage());
        }
        return null;
    }
}
