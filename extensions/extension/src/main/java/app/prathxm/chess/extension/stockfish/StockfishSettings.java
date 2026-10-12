package app.prathxm.chess.extension.stockfish;

import android.content.Context;
import android.content.SharedPreferences;

public class StockfishSettings {

    public static final String PREFS_NAME = "stockfish_settings";
    
    // Preference Keys
    private static final String KEY_ENGINE_ENABLED = "engine_enabled";
    private static final String KEY_DEPTH = "analysis_depth";
    private static final String KEY_MULTIPV = "multipv_count";
    private static final String KEY_ARROWS_VISIBLE = "arrows_visible";
    private static final String KEY_MY_SIDE_ONLY = "my_side_only";
    private static final String KEY_LIMIT_STRENGTH = "limit_strength";
    private static final String KEY_ELO = "elo_rating";
    private static final String KEY_ARROW_COLOR = "arrow_color";
    private static final String KEY_ADS_REMOVED = "ads_removed";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isAdsRemoved(Context context) {
        return getPrefs(context).getBoolean(KEY_ADS_REMOVED, true);
    }

    public static void setAdsRemoved(Context context, boolean removed) {
        getPrefs(context).edit().putBoolean(KEY_ADS_REMOVED, removed).apply();
    }

    public static boolean isEngineEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ENGINE_ENABLED, true);
    }

    public static void setEngineEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ENGINE_ENABLED, enabled).apply();
    }

    private static final String KEY_LANGUAGE = "app_language";

    public static String getLanguage(Context context) {
        return getPrefs(context).getString(KEY_LANGUAGE, "en");
    }

    public static void setLanguage(Context context, String lang) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, lang).apply();
    }

    /** Max selectable live-analysis depth (strictly locked to 12 as per NNVC Extension). */
    public static final int MAX_DEPTH = 12;

    public static int getDepth(Context context) {
        return Math.max(1, Math.min(MAX_DEPTH, getPrefs(context).getInt(KEY_DEPTH, 10)));
    }

    // ── Engine power ─────────────────────────────────────────────────────────

    public static int getCpuCount() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    /** Live search threads; defaults to 1 for battery and thermal efficiency. */
    public static int getLiveThreads(Context context) {
        return 1;
    }

    /** Live search threads alias. */
    public static int getThreads(Context context) {
        return 1;
    }

    public static void setThreads(Context context, int threads) {
        // Kept for backward compatibility
    }

    /** Game review threads: automatically uses 7 threads (or cpus if < 7, min 1). */
    public static int getReviewThreads(Context context) {
        int cpus = getCpuCount();
        return Math.max(1, Math.min(cpus, 7));
    }

    /** Extra depth added on top of the Chess.com game review depth preset (0 = auto). */
    public static int getReviewDepthBoost(Context context) {
        return 0;
    }

    public static void setReviewDepthBoost(Context context, int boost) {
        // Kept for backward compatibility
    }

    public static void setDepth(Context context, int depth) {
        getPrefs(context).edit().putInt(KEY_DEPTH, depth).apply();
    }

    public static int getMultiPV(Context context) {
        return getPrefs(context).getInt(KEY_MULTIPV, 3);
    }

    public static void setMultiPV(Context context, int count) {
        getPrefs(context).edit().putInt(KEY_MULTIPV, count).apply();
    }

    public static boolean isArrowsVisible(Context context) {
        return getPrefs(context).getBoolean(KEY_ARROWS_VISIBLE, true);
    }

    public static void setArrowsVisible(Context context, boolean visible) {
        getPrefs(context).edit().putBoolean(KEY_ARROWS_VISIBLE, visible).apply();
    }

    public static boolean isMySideOnly(Context context) {
        return getPrefs(context).getBoolean(KEY_MY_SIDE_ONLY, false);
    }

    public static void setMySideOnly(Context context, boolean mySideOnly) {
        getPrefs(context).edit().putBoolean(KEY_MY_SIDE_ONLY, mySideOnly).apply();
    }

    public static boolean isLimitStrength(Context context) {
        return true;
    }

    public static void setLimitStrength(Context context, boolean limit) {
        getPrefs(context).edit().putBoolean(KEY_LIMIT_STRENGTH, true).apply();
    }

    /** Elo range & defaults matching Extension NNVC */
    public static final int MIN_ELO = 100;
    public static final int MAX_ELO = 3500;
    public static final int DEFAULT_ELO = 2200;
    public static final int ELO_STEP = 10;

    // Engine Choices matching Extension NNVC (100% WebAssembly)
    public static final String ENGINE_KOMODO = "komodo"; // Komodo Dragon 3.3 WASM (default)
    public static final String ENGINE_STOCKFISH18 = "stockfish18"; // Stockfish 18 WASM
    private static final String KEY_ENGINE_CHOICE = "engine_choice";

    public static String getEngineChoice(Context context) {
        String choice = getPrefs(context).getString(KEY_ENGINE_CHOICE, ENGINE_KOMODO);
        if ("stockfish_native".equals(choice)) {
            choice = ENGINE_KOMODO;
            setEngineChoice(context, choice);
        }
        return choice;
    }

    public static void setEngineChoice(Context context, String choice) {
        getPrefs(context).edit().putString(KEY_ENGINE_CHOICE, choice).apply();
    }

    private static final String KEY_FLOATING_LOG_PILL = "floating_log_pill_enabled";

    public static boolean isFloatingLogPillEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_FLOATING_LOG_PILL, false);
    }

    public static void setFloatingLogPillEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_FLOATING_LOG_PILL, enabled).apply();
    }

    // Komodo Styles matching Extension NNVC (_KOMODO_STYLES)
    public static final String STYLE_DEFAULT = "Default";
    public static final String STYLE_AGGRESSIVE = "Aggressive";
    public static final String STYLE_DEFENSIVE = "Defensive";
    private static final String KEY_KOMODO_STYLE = "komodo_style";

    private static final String KEY_AUTO_DEPTH = "auto_depth_enabled";

    public static String getKomodoStyle(Context context) {
        return getPrefs(context).getString(KEY_KOMODO_STYLE, STYLE_DEFAULT);
    }

    public static void setKomodoStyle(Context context, String style) {
        getPrefs(context).edit().putString(KEY_KOMODO_STYLE, style).apply();
    }

    public static boolean isAutoDepthEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_AUTO_DEPTH, true);
    }

    public static void setAutoDepthEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_DEPTH, enabled).apply();
    }

    /**
     * Exact auto depth calculation from Extension NNVC (_autoDepthForElo).
     */
    public static int autoDepthForElo(int effectiveElo) {
        int e = Math.max(MIN_ELO, Math.min(MAX_ELO, effectiveElo));
        if (e < 450) return 1;
        if (e < 700) return 2;
        if (e < 950) return 3;
        if (e < 1200) return 4;
        if (e < 1450) return 5;
        if (e < 1700) return 6;
        if (e < 1950) return 7;
        if (e < 2200) return 8;
        if (e < 2500) return 9;
        if (e < 2850) return 10;
        if (e < 3200) return 11;
        return 12;
    }

    /**
     * Elo Tier and class matching Extension NNVC (_eloRow).
     */
    public static String getEloTier(int elo) {
        if (elo < 1200) return "Beginner (C)";
        if (elo < 1600) return "Intermediate (B)";
        if (elo < 1900) return "Advanced (A)";
        if (elo < 2200) return "Expert (E)";
        if (elo < 2400) return "Master (M)";
        return "Grandmaster (G)";
    }

    public static int getElo(Context context) {
        int elo = getPrefs(context).getInt(KEY_ELO, DEFAULT_ELO);
        int snapped = Math.round((float) elo / ELO_STEP) * ELO_STEP;
        return Math.max(MIN_ELO, Math.min(MAX_ELO, snapped));
    }

    public static void setElo(Context context, int elo) {
        int snapped = Math.round((float) elo / ELO_STEP) * ELO_STEP;
        int clamped = Math.max(MIN_ELO, Math.min(MAX_ELO, snapped));
        getPrefs(context).edit().putInt(KEY_ELO, clamped).apply();
    }

    private static final String KEY_PREMIUM_ENABLED = "premium_enabled";

    public static boolean isPremiumEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_PREMIUM_ENABLED, true);
    }

    public static void setPremiumEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_PREMIUM_ENABLED, enabled).apply();
    }

    public static int getArrowColor(Context context) {
        return getArrowTierColor(context, 1);
    }

    public static void setArrowColor(Context context, int color) {
        setArrowTierColor(context, 1, color);
    }

    private static final String KEY_ARROW_COLOR_PREFIX = "arrow_color_tier_";
    public static final int[] DEFAULT_TIER_COLORS = {
            0xFFF0B84B, // Tier 1: Gold / Amber
            0xFF58B8FF, // Tier 2: Sky Blue
            0xFFD9DDE6, // Tier 3: Cool Silver
            0xFFC084FC, // Tier 4: Lavender Purple
            0xFF34D399  // Tier 5: Mint Emerald
    };

    public static int getArrowTierColor(Context context, int tier) {
        int index = Math.max(1, Math.min(5, tier)) - 1;
        int defaultColor = DEFAULT_TIER_COLORS[index];
        return getPrefs(context).getInt(KEY_ARROW_COLOR_PREFIX + tier, defaultColor);
    }

    public static void setArrowTierColor(Context context, int tier, int color) {
        int t = Math.max(1, Math.min(5, tier));
        getPrefs(context).edit().putInt(KEY_ARROW_COLOR_PREFIX + t, color).apply();
    }

    private static final String KEY_SHOW_EVAL_BAR = "show_eval_bar";

    public static boolean isEvalBarEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_EVAL_BAR, true);
    }

    public static void setEvalBarEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_EVAL_BAR, enabled).apply();
    }

    private static final String KEY_WARNING_ACCEPTED = "warning_accepted";

    public static boolean isWarningAccepted(Context context) {
        return getPrefs(context).getBoolean(KEY_WARNING_ACCEPTED, false);
    }

    public static void setWarningAccepted(Context context, boolean accepted) {
        getPrefs(context).edit().putBoolean(KEY_WARNING_ACCEPTED, accepted).apply();
    }

    private static final String KEY_SHOW_THREAT_ARROWS = "show_threat_arrows";

    public static boolean isThreatArrowsEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_THREAT_ARROWS, false);
    }

    public static void setThreatArrowsEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_THREAT_ARROWS, enabled).apply();
    }

    private static final String KEY_SHOW_MOVE_CLASSIFICATION = "show_move_classification";

    public static boolean isMoveClassificationEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_MOVE_CLASSIFICATION, true);
    }

    public static void setMoveClassificationEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_MOVE_CLASSIFICATION, enabled).apply();
    }

    private static final String KEY_COACH_DEPTH = "coach_depth";
    public static final int DEFAULT_COACH_DEPTH = 2; // Khớp 100% Extension DEFAULT_DEPTH = 2

    public static int getCoachDepth(Context context) {
        return Math.max(1, Math.min(10, getPrefs(context).getInt(KEY_COACH_DEPTH, DEFAULT_COACH_DEPTH)));
    }

    public static void setCoachDepth(Context context, int depth) {
        getPrefs(context).edit().putInt(KEY_COACH_DEPTH, Math.max(1, Math.min(10, depth))).apply();
    }

    private static final String KEY_ENABLE_BLUNDER_ALERTS = "enable_blunder_alerts";

    public static boolean isBlunderAlertsEnabled(Context context) {
        return false;
    }

    public static void setBlunderAlertsEnabled(Context context, boolean enabled) {
        // Disabled permanently
    }

    private static final String KEY_SHOW_MATE_ANNOUNCEMENT = "show_mate_announcement";

    public static boolean isMateAnnouncementEnabled(Context context) {
        return false;
    }

    public static void setMateAnnouncementEnabled(Context context, boolean enabled) {
        // Disabled permanently
    }

    private static final String KEY_SHOW_ENGINE_INFO = "show_engine_info";

    /** Small "depth · score" line above the board, next to the W/D/L bar. */
    public static boolean isEngineInfoEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_ENGINE_INFO, false);
    }

    public static void setEngineInfoEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_ENGINE_INFO, enabled).apply();
    }

    private static final String KEY_SHOW_ACCURACY_ELO = "show_accuracy_elo";
    private static final String KEY_ACCURACY_WIDGET_X = "accuracy_widget_x";
    private static final String KEY_ACCURACY_WIDGET_Y = "accuracy_widget_y";
    private static final String KEY_ACCURACY_WIDGET_COLLAPSED = "accuracy_widget_collapsed";

    public static boolean isAccuracyEloEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_ACCURACY_ELO, true);
    }

    public static void setAccuracyEloEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_ACCURACY_ELO, enabled).apply();
    }

    public static float getAccuracyWidgetX(Context context, float defValue) {
        return getPrefs(context).getFloat(KEY_ACCURACY_WIDGET_X, defValue);
    }

    public static float getAccuracyWidgetY(Context context, float defValue) {
        return getPrefs(context).getFloat(KEY_ACCURACY_WIDGET_Y, defValue);
    }

    public static void setAccuracyWidgetPosition(Context context, float x, float y) {
        getPrefs(context).edit()
                .putFloat(KEY_ACCURACY_WIDGET_X, x)
                .putFloat(KEY_ACCURACY_WIDGET_Y, y)
                .apply();
    }

    public static boolean isAccuracyWidgetCollapsed(Context context) {
        return getPrefs(context).getBoolean(KEY_ACCURACY_WIDGET_COLLAPSED, false);
    }

    public static void setAccuracyWidgetCollapsed(Context context, boolean collapsed) {
        getPrefs(context).edit().putBoolean(KEY_ACCURACY_WIDGET_COLLAPSED, collapsed).apply();
    }

    /** Restores every engine setting to its default (the tour flag is kept). */
    public static void resetToDefaults(Context context) {
        boolean tour = isTourShown(context);
        boolean warning = isWarningAccepted(context);
        getPrefs(context).edit().clear()
                .putBoolean(KEY_TOUR_SHOWN, tour)
                .putBoolean(KEY_WARNING_ACCEPTED, warning)
                .apply();
    }

    private static final String KEY_TOUR_SHOWN = "stockfish_tour_shown";

    public static boolean isTourShown(Context context) {
        return getPrefs(context).getBoolean(KEY_TOUR_SHOWN, false);
    }

    public static void setTourShown(Context context, boolean shown) {
        getPrefs(context).edit().putBoolean(KEY_TOUR_SHOWN, shown).apply();
    }
}

