package app.nnvc.chess.extension.stockfish;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import app.nnvc.chess.extension.BuildConfig;

public class StockfishSettingsDialog {

    // ══ NNVC Cyber Luxury Frosted Palette ══════════════════════════════════════
    private static final int COLOR_BG_PANEL       = 0xF20C0F16; // Deep Frosted Midnight Glass
    private static final int COLOR_CARD_BG        = 0xCC121722; // Layered Glass Card
    private static final int COLOR_CARD_BORDER    = 0x330A84FF; // Cyber Blue Glow Border (20%)
    private static final int COLOR_ACCENT_BLUE    = 0xFF0A84FF; // NNVC Royal Blue
    private static final int COLOR_ACCENT_CYAN    = 0xFF64D2FF; // Cyber Cyan
    private static final int COLOR_ACCENT_GOLD    = 0xFFFFD760; // Amber Gold Accent
    private static final int COLOR_TEXT_PRIMARY   = 0xFFFFFFFF; // Pure White
    private static final int COLOR_TEXT_SECONDARY = 0xD0EBF0FF; // Muted White / Ice Blue
    private static final int COLOR_TEXT_MUTED     = 0x88A0B4D2; // Dim Subtitle
    private static final int COLOR_GREEN_READY    = 0xFF30D158; // Active Ready
    public static final int COLOR_CHESS_TITLE_BG   = 0xFF7C2929; // Chuẩn bgChesstitle Design System Chess.com (Crimson Burgundy)
    public static final int COLOR_CHESS_TITLE_STROKE = 0xFF9E3838; // Viền đỏ thẫm tinh tế
    public static final int COLOR_CHESS_TITLE_ACCENT = 0xFFFA412D; // red300 accent
    private static final int COLOR_DANGER_RED     = 0xFFFF453A; // Danger / Red

    public static void showSettingsMenu(Activity activity) {
        if (activity == null) return;
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        final float density = dm.density;
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;

        // Cố định chiều cao Dialog: 70% chiều cao màn hình để không bị co giật khi chuyển tab
        final int targetDialogHeight = (int) (screenHeight * 0.70f);
        final int targetDialogWidth = (int) (screenWidth * 0.92f);

        // Frosted Glass Window Frame với hiệu ứng viền thở động (Ambient Breathing Glow)
        final GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setColor(COLOR_BG_PANEL);
        dialogBg.setCornerRadius(22 * density);
        dialogBg.setStroke((int) (1.4f * density), COLOR_ACCENT_BLUE);
        dialog.getWindow().setBackgroundDrawable(dialogBg);

        // Theme động: Hiệu ứng viền thở Cyber Neon tuần hoàn
        ValueAnimator borderGlowAnim = ValueAnimator.ofFloat(0f, 1f);
        borderGlowAnim.setDuration(1600);
        borderGlowAnim.setRepeatMode(ValueAnimator.REVERSE);
        borderGlowAnim.setRepeatCount(ValueAnimator.INFINITE);
        borderGlowAnim.addUpdateListener(animation -> {
            float frac = (float) animation.getAnimatedValue();
            int glowColor = blendColor(0x330A84FF, 0xAA64D2FF, frac);
            dialogBg.setStroke((int) ((1.4f + 0.4f * frac) * density), glowColor);
        });
        borderGlowAnim.start();

        final boolean[] hasTitleChanged = { false };
        dialog.setOnDismissListener(d -> {
            borderGlowAnim.cancel();
            if (hasTitleChanged[0]) {
                try {
                    activity.recreate();
                } catch (Throwable ignored) {}
            }
        });

        final int[] currentTabIndex = { 0 };
        View windowRoot = buildSettingsContentView(activity, dialog, currentTabIndex, targetDialogWidth, targetDialogHeight, density, hasTitleChanged);
        dialog.setContentView(windowRoot);
        dialog.show();

        // Đảm bảo kích thước cửa sổ chuẩn cố định sau khi show
        dialog.getWindow().setLayout(targetDialogWidth, targetDialogHeight);
    }

    private static View buildSettingsContentView(final Activity activity, final Dialog dialog, final int[] currentTabIndex, final int targetDialogWidth, final int targetDialogHeight, final float density, final boolean[] hasTitleChanged) {
        // Root container (Cố định chiều cao và chiều rộng)
        LinearLayout windowRoot = new LinearLayout(activity);
        windowRoot.setOrientation(LinearLayout.VERTICAL);
        windowRoot.setLayoutParams(new ViewGroup.LayoutParams(
                targetDialogWidth,
                targetDialogHeight
        ));

        // ══════════════════════════════════════════════════════════════════════
        // 1. NNVC HEADER
        // ══════════════════════════════════════════════════════════════════════
        LinearLayout headerLayout = new LinearLayout(activity);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding((int) (16 * density), (int) (14 * density), (int) (16 * density), (int) (12 * density));

        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(0x330A84FF); // 20% cyber blue glow
        headerBg.setCornerRadii(new float[]{
                22 * density, 22 * density,
                22 * density, 22 * density,
                0, 0, 0, 0
        });
        headerLayout.setBackground(headerBg);

        // Logo Icon
        ImageView logoView = NNVCLogoHelper.createLogoView(activity, (int) (34 * density));
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams((int) (34 * density), (int) (34 * density));
        logoParams.rightMargin = (int) (10 * density);
        logoView.setLayoutParams(logoParams);
        headerLayout.addView(logoView);

        // Title Column
        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleColParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleCol.setLayoutParams(titleColParams);

        // Brand Row (.ttl-name)
        LinearLayout brandRow = new LinearLayout(activity);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView brandText = new TextView(activity);
        brandText.setText("NNVC");
        brandText.setTextColor(COLOR_TEXT_PRIMARY);
        brandText.setTextSize(15f);
        brandText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        brandText.setLetterSpacing(0.08f);
        brandRow.addView(brandText);

        // VIP Pill Badge (.nncv-vip-pill)
        TextView vipPill = new TextView(activity);
        vipPill.setText("VIP");
        vipPill.setTextColor(0xFFFFF7D6);
        vipPill.setTextSize(9.5f);
        vipPill.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        vipPill.setLetterSpacing(0.12f);
        int pillPadH = (int) (7 * density);
        int pillPadV = (int) (2 * density);
        vipPill.setPadding(pillPadH, pillPadV, pillPadH, pillPadV);

        GradientDrawable vipBg = new GradientDrawable();
        vipBg.setColor(0x40FFD760);
        vipBg.setStroke((int) (1.2f * density), 0x99FFD760);
        vipBg.setCornerRadius(999 * density);
        vipPill.setBackground(vipBg);

        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        pillParams.leftMargin = (int) (8 * density);
        vipPill.setLayoutParams(pillParams);
        brandRow.addView(vipPill);

        titleCol.addView(brandRow);

        // Subtitle (.ttl-sub)
        TextView subtitleTv = new TextView(activity);
        subtitleTv.setText(I18n.get(activity, "ttl_sub"));
        subtitleTv.setTextColor(COLOR_TEXT_MUTED);
        subtitleTv.setTextSize(8.5f);
        subtitleTv.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        subtitleTv.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = (int) (2 * density);
        subtitleTv.setLayoutParams(subParams);
        titleCol.addView(subtitleTv);

        headerLayout.addView(titleCol);

        // Action Buttons in Header: [VI/EN] + [✕] Close Button (Extension Style)
        LinearLayout headerBtns = new LinearLayout(activity);
        headerBtns.setOrientation(LinearLayout.HORIZONTAL);
        headerBtns.setGravity(Gravity.CENTER_VERTICAL);

        // Language Switch Button
        TextView langBtn = new TextView(activity);
        boolean isCurrentVi = "vi".equalsIgnoreCase(StockfishSettings.getLanguage(activity));
        langBtn.setText(isCurrentVi ? "VI" : "EN");
        langBtn.setTextColor(COLOR_TEXT_PRIMARY);
        langBtn.setTextSize(11);
        langBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        langBtn.setGravity(Gravity.CENTER);
        int langPadH = (int) (9 * density);
        int langPadV = (int) (4 * density);
        langBtn.setPadding(langPadH, langPadV, langPadH, langPadV);

        GradientDrawable langBg = new GradientDrawable();
        langBg.setColor(0x22FFFFFF);
        langBg.setCornerRadius(8 * density);
        langBg.setStroke((int) (1 * density), COLOR_CARD_BORDER);
        langBtn.setBackground(langBg);

        langBtn.setOnClickListener(v -> {
            HapticHelper.pop(activity, v);
            String newLang = isCurrentVi ? "en" : "vi";
            StockfishSettings.setLanguage(activity, newLang);
            OverlayManager.refreshOverlaysLanguage(activity);
            View newRoot = buildSettingsContentView(activity, dialog, currentTabIndex, targetDialogWidth, targetDialogHeight, density, hasTitleChanged);
            dialog.setContentView(newRoot);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(targetDialogWidth, targetDialogHeight);
            }
        });
        headerBtns.addView(langBtn);

        // Spacer between Lang button and Close button
        View hdrSpacer = new View(activity);
        hdrSpacer.setLayoutParams(new LinearLayout.LayoutParams((int) (8 * density), 1));
        headerBtns.addView(hdrSpacer);

        // Extension Style Close Button (✕)
        TextView closeBtn = new TextView(activity);
        closeBtn.setText("✕");
        closeBtn.setTextColor(COLOR_TEXT_SECONDARY);
        closeBtn.setTextSize(13);
        closeBtn.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        closeBtn.setGravity(Gravity.CENTER);
        int closePad = (int) (6 * density);
        closeBtn.setPadding(closePad, (int) (3 * density), closePad, (int) (3 * density));

        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(0x1FFFFFFF);
        closeBg.setCornerRadius(8 * density);
        closeBg.setStroke((int) (1 * density), 0x33FFFFFF);
        closeBtn.setBackground(closeBg);

        closeBtn.setOnClickListener(v -> {
            HapticHelper.pop(activity, v);
            dialog.dismiss();
        });
        headerBtns.addView(closeBtn);

        headerLayout.addView(headerBtns);

        windowRoot.addView(headerLayout);

        // Thin Cyber Divider Line
        View headerDivider = new View(activity);
        headerDivider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (1.2f * density)
        ));
        GradientDivider(headerDivider, COLOR_ACCENT_BLUE);
        windowRoot.addView(headerDivider);

        // ══════════════════════════════════════════════════════════════════════
        // 2. SEGMENTED TABS (.tab-nav)
        // ══════════════════════════════════════════════════════════════════════
        LinearLayout tabNav = new LinearLayout(activity);
        tabNav.setOrientation(LinearLayout.HORIZONTAL);
        tabNav.setPadding((int) (4 * density), (int) (4 * density), (int) (4 * density), (int) (4 * density));
        LinearLayout.LayoutParams tabNavParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        tabNavParams.setMargins((int) (14 * density), (int) (8 * density), (int) (14 * density), (int) (6 * density));
        tabNav.setLayoutParams(tabNavParams);

        GradientDrawable tabNavBg = new GradientDrawable();
        tabNavBg.setColor(0x66000000);
        tabNavBg.setCornerRadius(14 * density);
        tabNavBg.setStroke((int) (1 * density), 0x1AFFFFFF);
        tabNav.setBackground(tabNavBg);

        int initialTab = (currentTabIndex != null && currentTabIndex.length > 0) ? currentTabIndex[0] : 0;
        final TextView tabLive = createTabButton(activity, I18n.get(activity, "tab_live"), initialTab == 0, density);
        final TextView tabEngine = createTabButton(activity, I18n.get(activity, "tab_engine"), initialTab == 1, density);
        final TextView tabCoach = createTabButton(activity, I18n.get(activity, "tab_coach"), initialTab == 2, density);
        final TextView tabVisual = createTabButton(activity, I18n.get(activity, "tab_visual"), initialTab == 3, density);
        final TextView tabArrows = createTabButton(activity, I18n.get(activity, "tab_arrows"), initialTab == 4, density);

        tabNav.addView(tabLive);
        tabNav.addView(tabEngine);
        tabNav.addView(tabCoach);
        tabNav.addView(tabVisual);
        tabNav.addView(tabArrows);

        windowRoot.addView(tabNav);

        // ══════════════════════════════════════════════════════════════════════
        // 3. TAB CONTENT CONTAINERS (SCROLLABLE VỚI CHIỀU CAO CỐ ĐỊNH)
        // ══════════════════════════════════════════════════════════════════════
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setFillViewport(true); // Đảm bảo cuộn đầy đủ khung nhìn cố định
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1.0f
        );
        scrollView.setLayoutParams(scrollParams);

        LinearLayout tabContentRoot = new LinearLayout(activity);
        tabContentRoot.setOrientation(LinearLayout.VERTICAL);
        int contentPadding = (int) (14 * density);
        tabContentRoot.setPadding(contentPadding, 0, contentPadding, (int) (8 * density));
        scrollView.addView(tabContentRoot);

        // ─── TAB 1: LIVE (Trực tiếp & Thế cờ) ───
        final LinearLayout panelLive = new LinearLayout(activity);
        panelLive.setOrientation(LinearLayout.VERTICAL);
        panelLive.setVisibility(initialTab == 0 ? View.VISIBLE : View.GONE);
        tabContentRoot.addView(panelLive);

        // Mini Dashboard Card with Pulsing LED (.dash)
        LinearLayout dashCard = createGlassCard(activity, density);
        panelLive.addView(dashCard);

        LinearLayout statusRow = new LinearLayout(activity);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        dashCard.addView(statusRow);

        View statusDot = new View(activity);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setColor(StockfishSettings.isEngineEnabled(activity) ? COLOR_GREEN_READY : COLOR_DANGER_RED);
        dotBg.setCornerRadius(999 * density);
        statusDot.setBackground(dotBg);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams((int) (8 * density), (int) (8 * density));
        dotParams.rightMargin = (int) (8 * density);
        statusDot.setLayoutParams(dotParams);
        statusRow.addView(statusDot);

        // LED Breathing Pulse Animation
        AlphaAnimation pulseAnim = new AlphaAnimation(0.35f, 1.0f);
        pulseAnim.setDuration(700);
        pulseAnim.setRepeatMode(Animation.REVERSE);
        pulseAnim.setRepeatCount(Animation.INFINITE);
        statusDot.startAnimation(pulseAnim);

        TextView statusLabel = new TextView(activity);
        statusLabel.setText(I18n.get(activity, StockfishSettings.isEngineEnabled(activity) ? "status_ready" : "status_off"));
        statusLabel.setTextColor(COLOR_TEXT_SECONDARY);
        statusLabel.setTextSize(11);
        statusLabel.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        statusRow.addView(statusLabel);

        addDialogSpacer(panelLive, 8, density);

        // Card: Điều Khiển (Controls)
        LinearLayout liveCard = createGlassCard(activity, density);
        panelLive.addView(liveCard);

        final CyberSwitchView enabledSwitch = addCyberSwitchRow(liveCard,
                I18n.get(activity, "enable_stockfish"),
                I18n.get(activity, "panic_hint"),
                StockfishSettings.isEngineEnabled(activity),
                density, activity);

        enabledSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            StockfishSettings.setEngineEnabled(activity, isChecked);
            statusDot.setBackgroundColor(isChecked ? COLOR_GREEN_READY : COLOR_DANGER_RED);
            GradientDrawable dBg = new GradientDrawable();
            dBg.setColor(isChecked ? COLOR_GREEN_READY : COLOR_DANGER_RED);
            dBg.setCornerRadius(999 * density);
            statusDot.setBackground(dBg);
            statusLabel.setText(I18n.get(activity, isChecked ? "status_ready" : "status_off"));
            if (!isChecked) {
                OverlayManager.hideArrowOverlay();
                OverlayManager.hideEvalBar();
                OverlayManager.hideMateAnnouncement();
                OverlayManager.hideEngineInfo();
            } else {
                if (StockfishSettings.isEvalBarEnabled(activity)) {
                    // Eval bar will be shown on analysis
                }
                StockfishExtension.triggerAnalysisForCurrentState();
            }
        });

        // ─── TAB 2: ENGINE (Sức mạnh & Phân loại) ───
        final LinearLayout panelEngine = new LinearLayout(activity);
        panelEngine.setOrientation(LinearLayout.VERTICAL);
        panelEngine.setVisibility(initialTab == 1 ? View.VISIBLE : View.GONE);
        tabContentRoot.addView(panelEngine);

        // Card 1: Chọn Engine (Komodo 3.3 vs Stockfish 18)
        LinearLayout engineChoiceCard = createGlassCard(activity, density);
        panelEngine.addView(engineChoiceCard);

        TextView engineChoiceTitle = new TextView(activity);
        engineChoiceTitle.setText(I18n.get(activity, "engine_choice"));
        engineChoiceTitle.setTextColor(COLOR_TEXT_PRIMARY);
        engineChoiceTitle.setTextSize(13.5f);
        engineChoiceTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        engineChoiceCard.addView(engineChoiceTitle);

        TextView engineChoiceHint = new TextView(activity);
        engineChoiceHint.setText(I18n.get(activity, "engine_choice_hint"));
        engineChoiceHint.setTextColor(COLOR_TEXT_MUTED);
        engineChoiceHint.setTextSize(10.5f);
        LinearLayout.LayoutParams ecHintParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ecHintParams.topMargin = (int) (2 * density);
        ecHintParams.bottomMargin = (int) (8 * density);
        engineChoiceHint.setLayoutParams(ecHintParams);
        engineChoiceCard.addView(engineChoiceHint);

        final String[] curEngine = { StockfishSettings.getEngineChoice(activity) };
        final String[] curStyle = { StockfishSettings.getKomodoStyle(activity) };

        final Runnable[] updateEngineSub = new Runnable[1];
        final Runnable[] updateStyleSub = new Runnable[1];

        // 1. Dòng Chọn Engine (CyberSelectRow)
        final CyberSelectRow[] engineSelectRow = new CyberSelectRow[1];
        final CyberSelectRow[] styleSelectRow = new CyberSelectRow[1];

        String initialEngineLabel = StockfishSettings.ENGINE_STOCKFISH18.equals(curEngine[0]) ? "Stockfish 18" : "Komodo 3.3";
        String initialEngineSub = StockfishSettings.ENGINE_STOCKFISH18.equals(curEngine[0])
                ? (I18n.isVi(activity) ? "Khuyên dùng để phân tích; độ sâu và sức mạnh cực đại." : "Recommended for deep tactical analysis.")
                : (I18n.isVi(activity) ? "Khuyên dùng để chơi thực chiến; phong cách tự nhiên." : "Recommended for real play; human-like style.");

        engineSelectRow[0] = addCyberSelectRow(
                engineChoiceCard,
                I18n.get(activity, "engine_choice"),
                initialEngineSub,
                initialEngineLabel,
                density,
                v -> {
                    HapticHelper.pop(activity, v);
                    PickerOption[] engineOptions = new PickerOption[] {
                        new PickerOption(StockfishSettings.ENGINE_KOMODO, "Komodo 3.3",
                                I18n.isVi(activity) ? "Khuyên dùng để chơi thực chiến; đánh mượt mà, phong cách tự nhiên." : "Recommended for real play; human-like, balanced playstyle."),
                        new PickerOption(StockfishSettings.ENGINE_STOCKFISH18, "Stockfish 18",
                                I18n.isVi(activity) ? "Khuyên dùng để phân tích; độ sâu và sức mạnh tính toán cực đại." : "Recommended for deep tactical analysis and brute calculation.")
                    };

                    showCyberPickerDialog(activity, density,
                            I18n.isVi(activity) ? "CHỌN ENGINE" : "SELECT ENGINE",
                            engineOptions, curEngine[0],
                            opt -> {
                                curEngine[0] = opt.id;
                                StockfishSettings.setEngineChoice(activity, curEngine[0]);
                                engineSelectRow[0].selectBtn.setText(opt.label + "  ▾");
                                boolean isKomodo = StockfishSettings.ENGINE_KOMODO.equals(curEngine[0]);
                                engineSelectRow[0].subtitle.setText(isKomodo
                                        ? (I18n.isVi(activity) ? "Khuyên dùng để chơi thực chiến; phong cách tự nhiên." : "Recommended for real play; human-like style.")
                                        : (I18n.isVi(activity) ? "Khuyên dùng để phân tích; độ sâu và sức mạnh cực đại." : "Recommended for deep tactical analysis."));
                                if (styleSelectRow[0] != null) {
                                    styleSelectRow[0].row.setVisibility(isKomodo ? View.VISIBLE : View.GONE);
                                }
                                if (StockfishSettings.isEngineEnabled(activity)) {
                                    StockfishExtension.triggerPlayAnalysisOnly();
                                }
                            });
                }
        );

        addCardSeparator(engineChoiceCard, density);

        // 2. Dòng Phong Cách Đánh (Komodo Style)
        String initialStyleLabel = StockfishSettings.STYLE_AGGRESSIVE.equals(curStyle[0]) ? "⚔️ " + I18n.get(activity, "style_aggressive")
                : (StockfishSettings.STYLE_DEFENSIVE.equals(curStyle[0]) ? "🛡️ " + I18n.get(activity, "style_defensive")
                : "⚖️ " + I18n.get(activity, "style_default"));

        String initialStyleSub = StockfishSettings.STYLE_AGGRESSIVE.equals(curStyle[0])
                ? (I18n.isVi(activity) ? "Tấn công chủ động, tìm kiếm sơ hở." : "Active attack, looking for weaknesses.")
                : (StockfishSettings.STYLE_DEFENSIVE.equals(curStyle[0])
                ? (I18n.isVi(activity) ? "Phòng thủ vững chắc, an toàn tối đa." : "Solid defense, maximum security.")
                : (I18n.isVi(activity) ? "Đánh bài bản, vừa công vừa thủ." : "Balanced strategy, attack and defense."));

        styleSelectRow[0] = addCyberSelectRow(
                engineChoiceCard,
                I18n.get(activity, "komodo_style"),
                initialStyleSub,
                initialStyleLabel,
                density,
                v -> {
                    HapticHelper.pop(activity, v);
                    PickerOption[] styleOptions = new PickerOption[] {
                        new PickerOption(StockfishSettings.STYLE_DEFAULT, "⚖️ " + I18n.get(activity, "style_default"),
                                I18n.isVi(activity) ? "Đánh bài bản, vừa công vừa thủ." : "Balanced strategy, attack and defense."),
                        new PickerOption(StockfishSettings.STYLE_AGGRESSIVE, "⚔️ " + I18n.get(activity, "style_aggressive"),
                                I18n.isVi(activity) ? "Tấn công chủ động, tìm kiếm sơ hở." : "Active attack, looking for weaknesses."),
                        new PickerOption(StockfishSettings.STYLE_DEFENSIVE, "🛡️ " + I18n.get(activity, "style_defensive"),
                                I18n.isVi(activity) ? "Phòng thủ vững chắc, an toàn tối đa." : "Solid defense, maximum security.")
                    };

                    showCyberPickerDialog(activity, density,
                            I18n.isVi(activity) ? "PHONG CÁCH ĐÁNH" : "PLAY STYLE",
                            styleOptions, curStyle[0],
                            opt -> {
                                curStyle[0] = opt.id;
                                StockfishSettings.setKomodoStyle(activity, curStyle[0]);
                                styleSelectRow[0].selectBtn.setText(opt.label + "  ▾");
                                styleSelectRow[0].subtitle.setText(opt.desc);
                                if (StockfishSettings.isEngineEnabled(activity)) {
                                    StockfishExtension.triggerPlayAnalysisOnly();
                                }
                            });
                }
        );

        // Ẩn dòng phong cách nếu đang chọn Stockfish 18
        if (!StockfishSettings.ENGINE_KOMODO.equals(curEngine[0])) {
            styleSelectRow[0].row.setVisibility(View.GONE);
        }

        // Card 2: Sức mạnh & Độ sâu (Auto Depth, Elo 100-3500)
        LinearLayout engineCard = createGlassCard(activity, density);
        panelEngine.addView(engineCard);

        final CyberSwitchView autoDepthSwitch = addCyberSwitchRow(engineCard,
                I18n.get(activity, "auto_depth"),
                I18n.get(activity, "auto_depth_hint"),
                StockfishSettings.isAutoDepthEnabled(activity),
                density, activity);

        addCardSeparator(engineCard, density);

        int currentElo = StockfishSettings.getElo(activity);
        int eloProg = (currentElo - StockfishSettings.MIN_ELO) / StockfishSettings.ELO_STEP;
        int eloMax = (StockfishSettings.MAX_ELO - StockfishSettings.MIN_ELO) / StockfishSettings.ELO_STEP;

        final TextView[] depthBadgeHolder = new TextView[1];
        final SeekBar[] depthSeekBarHolder = new SeekBar[1];

        final android.os.Handler realtimeDebounceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] pendingRealtimeReanalysis = new Runnable[1];

        final SeekBar eloSeekBar = addEloSeekBarWithBadge(engineCard,
                I18n.get(activity, "row_elo"),
                currentElo, eloProg, eloMax,
                I18n.get(activity, "elo_hint"),
                density, activity,
                new OnEloChangeListener() {
                    @Override
                    public void onEloChanged(int newElo) {
                        StockfishSettings.setElo(activity, newElo);
                        if (autoDepthSwitch.isChecked() && depthBadgeHolder[0] != null) {
                            int autoD = StockfishSettings.autoDepthForElo(newElo);
                            depthBadgeHolder[0].setText(autoD + " (Auto)");
                        }
                        if (pendingRealtimeReanalysis[0] != null) {
                            realtimeDebounceHandler.removeCallbacks(pendingRealtimeReanalysis[0]);
                        }
                        pendingRealtimeReanalysis[0] = () -> {
                            if (StockfishSettings.isEngineEnabled(activity)) {
                                StockfishBridge.clearCache();
                                StockfishBridge.stopSearch();
                                StockfishExtension.triggerPlayAnalysisOnly();
                            }
                        };
                        realtimeDebounceHandler.postDelayed(pendingRealtimeReanalysis[0], 100);
                    }
                    @Override
                    public void onEloCommitted(int finalElo) {
                        if (pendingRealtimeReanalysis[0] != null) {
                            realtimeDebounceHandler.removeCallbacks(pendingRealtimeReanalysis[0]);
                        }
                        if (StockfishSettings.isEngineEnabled(activity)) {
                            StockfishBridge.clearCache();
                            StockfishBridge.stopSearch();
                            StockfishExtension.triggerPlayAnalysisOnly();
                        }
                    }
                });

        addCardSeparator(engineCard, density);

        int currentDepth = StockfishSettings.getDepth(activity);
        boolean initialAuto = autoDepthSwitch.isChecked();
        String initialDepthBadge = initialAuto
                ? StockfishSettings.autoDepthForElo(currentElo) + " (Auto)"
                : String.valueOf(currentDepth);

        final SeekBar depthSeekBar = addDepthSeekBarWithBadge(engineCard,
                I18n.get(activity, "depth"),
                initialDepthBadge,
                currentDepth - 1, StockfishSettings.MAX_DEPTH - 1, 1,
                I18n.get(activity, "depth_hint"),
                density, activity, depthBadgeHolder,
                new OnValueChangeListener() {
                    @Override
                    public void onValueChanged(int newDepth) {
                        StockfishSettings.setDepth(activity, newDepth);
                        if (pendingRealtimeReanalysis[0] != null) {
                            realtimeDebounceHandler.removeCallbacks(pendingRealtimeReanalysis[0]);
                        }
                        pendingRealtimeReanalysis[0] = () -> {
                            if (StockfishSettings.isEngineEnabled(activity)) {
                                StockfishBridge.clearCache();
                                StockfishBridge.stopSearch();
                                StockfishExtension.triggerPlayAnalysisOnly();
                            }
                        };
                        realtimeDebounceHandler.postDelayed(pendingRealtimeReanalysis[0], 100);
                    }
                    @Override
                    public void onValueCommitted(int finalDepth) {
                        if (pendingRealtimeReanalysis[0] != null) {
                            realtimeDebounceHandler.removeCallbacks(pendingRealtimeReanalysis[0]);
                        }
                        if (StockfishSettings.isEngineEnabled(activity)) {
                            StockfishBridge.clearCache();
                            StockfishBridge.stopSearch();
                            StockfishExtension.triggerPlayAnalysisOnly();
                        }
                    }
                });
        depthSeekBarHolder[0] = depthSeekBar;

        if (initialAuto) {
            depthSeekBar.setAlpha(0.35f);
            depthSeekBar.setEnabled(false);
        }

        autoDepthSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            StockfishSettings.setAutoDepthEnabled(activity, isChecked);
            int elo = StockfishSettings.MIN_ELO + eloSeekBar.getProgress() * StockfishSettings.ELO_STEP;
            if (isChecked) {
                int autoD = StockfishSettings.autoDepthForElo(elo);
                if (depthBadgeHolder[0] != null) depthBadgeHolder[0].setText(autoD + " (Auto)");
                depthSeekBar.setAlpha(0.35f);
                depthSeekBar.setEnabled(false);
            } else {
                int manualD = Math.max(1, depthSeekBar.getProgress() + 1);
                if (depthBadgeHolder[0] != null) depthBadgeHolder[0].setText(String.valueOf(manualD));
                depthSeekBar.setAlpha(1.0f);
                depthSeekBar.setEnabled(true);
            }
            if (StockfishSettings.isEngineEnabled(activity)) {
                StockfishExtension.triggerPlayAnalysisOnly();
            }
        });

        // ─── TAB 3: COACH (Torch WebAssembly Coach Engine) ───
        final LinearLayout panelCoach = new LinearLayout(activity);
        panelCoach.setOrientation(LinearLayout.VERTICAL);
        panelCoach.setVisibility(initialTab == 2 ? View.VISIBLE : View.GONE);
        tabContentRoot.addView(panelCoach);

        LinearLayout coachCard = createGlassCard(activity, density);
        panelCoach.addView(coachCard);

        final CyberSwitchView classifSwitch = addCyberSwitchRow(coachCard,
                I18n.get(activity, "rate_moves"),
                I18n.get(activity, "rate_moves_hint"),
                StockfishSettings.isMoveClassificationEnabled(activity),
                density, activity);

        classifSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            StockfishSettings.setMoveClassificationEnabled(activity, isChecked);
        });

        addCardSeparator(coachCard, density);

        int currentCoachDepth = StockfishSettings.getCoachDepth(activity);
        final String coachDepthPrefix = I18n.get(activity, "coach_depth");
        final SeekBar coachDepthSeekBar = addGlassSeekBarWithBadge(coachCard, coachDepthPrefix,
                String.valueOf(currentCoachDepth),
                currentCoachDepth - 1, 9, 1,
                I18n.get(activity, "coach_depth_hint"),
                density, activity,
                (newCoachDepth) -> {
                    StockfishSettings.setCoachDepth(activity, newCoachDepth);
                });

        addCardSeparator(coachCard, density);

        final CyberSwitchView accuracyEloSwitch = addCyberSwitchRow(coachCard,
                I18n.get(activity, "show_accuracy_elo"),
                I18n.get(activity, "show_accuracy_elo_hint"),
                StockfishSettings.isAccuracyEloEnabled(activity),
                density, activity);

        accuracyEloSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            StockfishSettings.setAccuracyEloEnabled(activity, isChecked);
            if (!isChecked) {
                OverlayManager.hideAccuracyEloWidget();
            } else {
                OverlayManager.updateAccuracyEloWidget(-1f, -1f, -1, -1, true);
                OverlayManager.refreshOverlaysLanguage(activity);
            }
        });



        // ─── TAB 4: VISUAL (Giao Diện & Lớp Phủ) ───
        final LinearLayout panelVisual = new LinearLayout(activity);
        panelVisual.setOrientation(LinearLayout.VERTICAL);
        panelVisual.setVisibility(initialTab == 3 ? View.VISIBLE : View.GONE);
        tabContentRoot.addView(panelVisual);

        LinearLayout visualCard = createGlassCard(activity, density);
        panelVisual.addView(visualCard);

        final CyberSwitchView evalBarSwitch = addCyberSwitchRow(visualCard,
                I18n.get(activity, "eval_bar"),
                I18n.get(activity, "eval_bar_hint"),
                StockfishSettings.isEvalBarEnabled(activity),
                density, activity);

        evalBarSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            StockfishSettings.setEvalBarEnabled(activity, isChecked);
            if (!isChecked) {
                OverlayManager.hideEvalBar();
            } else {
                if (StockfishSettings.isEngineEnabled(activity)) {
                    StockfishExtension.triggerAnalysisForCurrentState();
                }
            }
        });

        // ─── Custom Title Card (Chỉ Áp Dụng Cho Tài Khoản Của Bạn) ───
        LinearLayout titleCard = createGlassCard(activity, density);
        panelVisual.addView(titleCard);

        final CyberSwitchView titleSwitch = addCyberSwitchRow(titleCard,
                I18n.get(activity, "custom_title"),
                I18n.get(activity, "custom_title_hint"),
                StockfishSettings.isCustomTitleEnabled(activity),
                density, activity);

        final LinearLayout titleSelectLayout = new LinearLayout(activity);
        titleSelectLayout.setOrientation(LinearLayout.VERTICAL);
        titleSelectLayout.setVisibility(StockfishSettings.isCustomTitleEnabled(activity) ? View.VISIBLE : View.GONE);
        titleCard.addView(titleSelectLayout);

        addCardSeparator(titleSelectLayout, density);

        // Hàng chọn danh hiệu phong cách Cyber Select: [ Label + Subtitle ] ........... [ GM ▾ ]
        final String currentCode = StockfishSettings.getCustomTitleCode(activity);
        final CyberSelectRow titleSelectRow = addCyberSelectRow(
                titleSelectLayout,
                I18n.get(activity, "choose_title"),
                getTitleDisplayName(activity, currentCode),
                currentCode,
                density,
                null
        );

        GradientDrawable titleBtnBg = new GradientDrawable();
        titleBtnBg.setColor(COLOR_CHESS_TITLE_BG); // 0xFF7C2929
        titleBtnBg.setCornerRadius(8 * density);
        titleBtnBg.setStroke((int) (1.2f * density), COLOR_CHESS_TITLE_STROKE);
        titleSelectRow.selectBtn.setBackground(titleBtnBg);

        titleSelectRow.selectBtn.setOnClickListener(v -> {
            HapticHelper.pop(activity, v);
            showTitlePickerDialog(activity, density, titleSelectRow.selectBtn, titleSelectRow.subtitle, hasTitleChanged);
        });

        titleSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            StockfishSettings.setCustomTitleEnabled(activity, isChecked);
            hasTitleChanged[0] = true;
            try {
                activity.getWindow().getDecorView().invalidate();
                activity.getWindow().getDecorView().requestLayout();
            } catch (Throwable ignored) {}
            titleSelectLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });


        // ─── TAB 5: ARROWS (Thiết Lập Mũi Tên & Bảng Màu) ───
        final LinearLayout panelArrows = new LinearLayout(activity);
        panelArrows.setOrientation(LinearLayout.VERTICAL);
        panelArrows.setVisibility(initialTab == 4 ? View.VISIBLE : View.GONE);
        tabContentRoot.addView(panelArrows);

        LinearLayout arrowCard = createGlassCard(activity, density);
        panelArrows.addView(arrowCard);

        final CyberSwitchView arrowsSwitch = addCyberSwitchRow(arrowCard,
                I18n.get(activity, "best_move_arrows"),
                I18n.get(activity, "best_move_arrows_hint"),
                StockfishSettings.isArrowsVisible(activity),
                density, activity);

        arrowsSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            StockfishSettings.setArrowsVisible(activity, isChecked);
            StockfishExtension.resetArrowSignature();
            if (!isChecked) {
                OverlayManager.hideArrowOverlay(true);
            } else {
                StockfishExtension.triggerPlayAnalysisOnly();
            }
        });

        addCardSeparator(arrowCard, density);

        int currentPV = StockfishSettings.getMultiPV(activity);
        final String pvPrefix = I18n.get(activity, "num_arrows");
        final SeekBar pvSeekBar = addGlassSeekBarWithBadge(arrowCard, pvPrefix,
                currentPV + " PV",
                currentPV - 1, 4, 1,
                I18n.get(activity, "arrows_hint"),
                density, activity,
                new OnValueChangeListener() {
                    @Override
                    public void onValueChanged(int newPV) {
                        StockfishSettings.setMultiPV(activity, newPV);
                        if (pendingRealtimeReanalysis[0] != null) {
                            realtimeDebounceHandler.removeCallbacks(pendingRealtimeReanalysis[0]);
                        }
                        pendingRealtimeReanalysis[0] = () -> {
                            if (StockfishSettings.isEngineEnabled(activity)) {
                                StockfishBridge.clearCache();
                                StockfishBridge.stopSearch();
                                StockfishExtension.triggerPlayAnalysisOnly();
                            }
                        };
                        realtimeDebounceHandler.postDelayed(pendingRealtimeReanalysis[0], 100);
                    }
                    @Override
                    public void onValueCommitted(int finalPV) {
                        if (pendingRealtimeReanalysis[0] != null) {
                            realtimeDebounceHandler.removeCallbacks(pendingRealtimeReanalysis[0]);
                        }
                        if (StockfishSettings.isEngineEnabled(activity)) {
                            StockfishBridge.clearCache();
                            StockfishBridge.stopSearch();
                            StockfishExtension.triggerPlayAnalysisOnly();
                        }
                    }
                });

        addCardSeparator(arrowCard, density);

        final CyberSwitchView oppArrowsSwitch = addCyberSwitchRow(arrowCard,
                I18n.get(activity, "opponent_arrows"),
                I18n.get(activity, "opponent_arrows_hint"),
                !StockfishSettings.isMySideOnly(activity),
                density, activity);

        oppArrowsSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            StockfishSettings.setMySideOnly(activity, !isChecked);
            if (StockfishSettings.isEngineEnabled(activity)) {
                StockfishExtension.triggerPlayAnalysisOnly();
            }
        });

        addCardSeparator(arrowCard, density);

        final CyberSwitchView threatSwitch = addCyberSwitchRow(arrowCard,
                I18n.get(activity, "threat_arrow"),
                I18n.get(activity, "threat_arrow_hint"),
                StockfishSettings.isThreatArrowsEnabled(activity),
                density, activity);

        final LinearLayout threatOptionsLayout = new LinearLayout(activity);
        threatOptionsLayout.setOrientation(LinearLayout.VERTICAL);
        threatOptionsLayout.setVisibility(StockfishSettings.isThreatArrowsEnabled(activity) ? View.VISIBLE : View.GONE);
        arrowCard.addView(threatOptionsLayout);

        addCardSeparator(threatOptionsLayout, density);

        // 1. Chế độ hiển thị đe dọa (Threat Mode: both, enemy, player)
        final String[] curThreatMode = {StockfishSettings.getThreatMode(activity)};
        final String threatModeLabel = StockfishSettings.THREAT_MODE_ENEMY.equals(curThreatMode[0])
                ? (I18n.isVi(activity) ? "Chỉ Đối Thủ" : "Enemy Only")
                : StockfishSettings.THREAT_MODE_PLAYER.equals(curThreatMode[0])
                ? (I18n.isVi(activity) ? "Chỉ Phe Mình" : "Player Only")
                : (I18n.isVi(activity) ? "Cả Hai Bên" : "Both Sides");

        final String threatModeDesc = StockfishSettings.THREAT_MODE_ENEMY.equals(curThreatMode[0])
                ? (I18n.isVi(activity) ? "Chỉ cảnh báo quân cờ đối thủ đang uy hiếp bạn." : "Only warn when opponent pieces threaten yours.")
                : StockfishSettings.THREAT_MODE_PLAYER.equals(curThreatMode[0])
                ? (I18n.isVi(activity) ? "Chỉ hiển thị cơ hội tấn công của bạn." : "Only show your attack opportunities.")
                : (I18n.isVi(activity) ? "Hiển thị đe dọa theo màu quân Trắng & Đen." : "Show threats by White & Black piece colors.");

        final CyberSelectRow[] threatModeRow = new CyberSelectRow[1];
        threatModeRow[0] = addCyberSelectRow(
                threatOptionsLayout,
                I18n.get(activity, "threat_mode"),
                threatModeDesc,
                threatModeLabel,
                density,
                v -> {
                    HapticHelper.pop(activity, v);
                    PickerOption[] modeOptions = new PickerOption[]{
                            new PickerOption(StockfishSettings.THREAT_MODE_BOTH,
                                    I18n.isVi(activity) ? "Cả Hai Bên" : "Both Sides",
                                    I18n.isVi(activity) ? "Hiển thị đe dọa theo màu quân Trắng & Đen." : "Show threats by White & Black piece colors."),
                            new PickerOption(StockfishSettings.THREAT_MODE_ENEMY,
                                    I18n.isVi(activity) ? "Chỉ Đối Thủ" : "Enemy Only",
                                    I18n.isVi(activity) ? "Chỉ cảnh báo quân cờ đối thủ đang uy hiếp bạn." : "Only warn when opponent pieces threaten yours."),
                            new PickerOption(StockfishSettings.THREAT_MODE_PLAYER,
                                    I18n.isVi(activity) ? "Chỉ Phe Mình" : "Player Only",
                                    I18n.isVi(activity) ? "Chỉ hiển thị cơ hội tấn công của bạn." : "Only show your attack opportunities.")
                    };

                    showCyberPickerDialog(activity, density,
                            I18n.isVi(activity) ? "CHẾ ĐỘ ĐE DỌA" : "THREAT MODE",
                            modeOptions, curThreatMode[0],
                            opt -> {
                                curThreatMode[0] = opt.id;
                                StockfishSettings.setThreatMode(activity, curThreatMode[0]);
                                threatModeRow[0].selectBtn.setText(opt.label + "  ▾");
                                threatModeRow[0].subtitle.setText(opt.desc);
                                if (StockfishSettings.isEngineEnabled(activity)) {
                                    StockfishExtension.triggerPlayAnalysisOnly();
                                }
                            });
                }
        );

        threatSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            StockfishSettings.setThreatArrowsEnabled(activity, isChecked);
            threatOptionsLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (StockfishSettings.isEngineEnabled(activity)) {
                StockfishExtension.triggerPlayAnalysisOnly();
            }
        });

        addCardSeparator(arrowCard, density);

        final CyberSwitchView premoveSwitch = addCyberSwitchRow(arrowCard,
                I18n.get(activity, "premove_arrow"),
                I18n.get(activity, "premove_arrow_hint"),
                StockfishSettings.isPremoveArrowsEnabled(activity),
                density, activity);

        premoveSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            StockfishSettings.setPremoveArrowsEnabled(activity, isChecked);
            if (StockfishSettings.isEngineEnabled(activity)) {
                StockfishExtension.triggerPlayAnalysisOnly();
            }
        });

        LinearLayout paletteCard = createGlassCard(activity, density);
        panelArrows.addView(paletteCard);
        addArrowPaletteSection(paletteCard, density, activity);

        // Tab Switching Click Listeners kèm Hoạt ảnh Fade & Slide mượt mà + Phản hồi rung
        View.OnClickListener tabListener = v -> {
            HapticHelper.tick(activity, v);
            boolean isL = (v == tabLive);
            boolean isE = (v == tabEngine);
            boolean isC = (v == tabCoach);
            boolean isV = (v == tabVisual);
            boolean isA = (v == tabArrows);

            if (isL) currentTabIndex[0] = 0;
            else if (isE) currentTabIndex[0] = 1;
            else if (isC) currentTabIndex[0] = 2;
            else if (isV) currentTabIndex[0] = 3;
            else if (isA) currentTabIndex[0] = 4;

            updateTabStyle(tabLive, isL, density);
            updateTabStyle(tabEngine, isE, density);
            updateTabStyle(tabCoach, isC, density);
            updateTabStyle(tabVisual, isV, density);
            updateTabStyle(tabArrows, isA, density);

            if (isL) {
                switchTabWithAnim(panelLive, panelEngine, panelCoach, panelVisual, panelArrows);
            } else if (isE) {
                switchTabWithAnim(panelEngine, panelLive, panelCoach, panelVisual, panelArrows);
            } else if (isC) {
                switchTabWithAnim(panelCoach, panelLive, panelEngine, panelVisual, panelArrows);
            } else if (isV) {
                switchTabWithAnim(panelVisual, panelLive, panelEngine, panelCoach, panelArrows);
            } else {
                switchTabWithAnim(panelArrows, panelLive, panelEngine, panelCoach, panelVisual);
            }
        };

        tabLive.setOnClickListener(tabListener);
        tabEngine.setOnClickListener(tabListener);
        tabCoach.setOnClickListener(tabListener);
        tabVisual.setOnClickListener(tabListener);
        tabArrows.setOnClickListener(tabListener);

        windowRoot.addView(scrollView);

        // ══════════════════════════════════════════════════════════════════════
        // 4. FOOTER CREDITS & ACTIONS
        // ══════════════════════════════════════════════════════════════════════
        LinearLayout footerLayout = new LinearLayout(activity);
        footerLayout.setOrientation(LinearLayout.VERTICAL);
        footerLayout.setPadding((int) (14 * density), (int) (8 * density), (int) (14 * density), (int) (12 * density));

        // Row: Telegram Button (Centered)
        LinearLayout socialRow = new LinearLayout(activity);
        socialRow.setOrientation(LinearLayout.HORIZONTAL);
        socialRow.setGravity(Gravity.CENTER);

        // Telegram Button (.hdr-btn style)
        LinearLayout telegramBtn = new LinearLayout(activity);
        telegramBtn.setOrientation(LinearLayout.HORIZONTAL);
        telegramBtn.setGravity(Gravity.CENTER);
        int tgPadH = (int) (18 * density);
        int tgPadV = (int) (8 * density);
        telegramBtn.setPadding(tgPadH, tgPadV, tgPadH, tgPadV);
        LinearLayout.LayoutParams tgParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        telegramBtn.setLayoutParams(tgParams);

        GradientDrawable tgBg = new GradientDrawable();
        tgBg.setColor(0xCC0A84FF); // 80% Cyber Blue
        tgBg.setCornerRadius(14 * density);
        tgBg.setStroke((int) (1.2f * density), COLOR_ACCENT_CYAN);
        telegramBtn.setBackground(tgBg);

        ImageView tgIcon = TelegramIconHelper.createTelegramLogoView(activity, (int) (16 * density));
        LinearLayout.LayoutParams tgIconParams = new LinearLayout.LayoutParams((int) (16 * density), (int) (16 * density));
        tgIconParams.rightMargin = (int) (6 * density);
        tgIcon.setLayoutParams(tgIconParams);
        telegramBtn.addView(tgIcon);

        TextView tgText = new TextView(activity);
        tgText.setText(I18n.get(activity, "contact_telegram"));
        tgText.setTextColor(COLOR_TEXT_PRIMARY);
        tgText.setTextSize(11.5f);
        tgText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        telegramBtn.addView(tgText);

        telegramBtn.setOnClickListener(v -> {
            HapticHelper.pop(activity, v);
            try {
                android.content.Intent intent = new android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://t.me/nncutett")
                );
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (Throwable t) {
                Toast.makeText(activity, "Telegram: @nncutett", Toast.LENGTH_LONG).show();
            }
        });
        socialRow.addView(telegramBtn);

        footerLayout.addView(socialRow);

        addDialogSpacer(footerLayout, 6, density);

        // Version / author metadata with restored version display
        TextView patchTv = new TextView(activity);
        String versionStr = "1.0.0";
        try {
            versionStr = BuildConfig.VERSION_NAME;
        } catch (Throwable ignored) {}
        patchTv.setText("NNVC v" + versionStr + " · by @nncutett");
        patchTv.setTextColor(COLOR_TEXT_MUTED);
        patchTv.setTextSize(10.5f);
        patchTv.setGravity(Gravity.CENTER);
        int patchPad = (int) (6 * density);
        patchTv.setPadding(patchPad, patchPad, patchPad, patchPad);

        final int[] tapCount = { 0 };
        final long[] lastTapTime = { 0L };
        patchTv.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastTapTime[0] > 1800) {
                tapCount[0] = 0;
            }
            lastTapTime[0] = now;
            tapCount[0]++;
            HapticHelper.pop(activity, v);
            if (tapCount[0] >= 5) {
                tapCount[0] = 0;
                Toast.makeText(activity, "🔓 NNVC Secret Debug Log", Toast.LENGTH_SHORT).show();
                NnvcLogViewerDialog.show(activity);
            }
        });
        footerLayout.addView(patchTv);

        windowRoot.addView(footerLayout);
        return windowRoot;
    }

    private static boolean evalBarCbChecked(CyberSwitchView s) { return s != null && s.isChecked(); }

    // ══ UI Helpers matching NNVC Cyber Glass Architecture ══════════════════════

    private static void switchTabWithAnim(View showView, View... hideViews) {
        for (View v : hideViews) {
            v.setVisibility(View.GONE);
        }
        showView.setVisibility(View.VISIBLE);
        showView.setAlpha(0f);
        showView.setTranslationY(18f);
        showView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(190)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private static LinearLayout createGlassCard(Activity activity, float density) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int padH = (int) (14 * density);
        int padV = (int) (12 * density);
        card.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_CARD_BG);
        bg.setCornerRadius(14 * density);
        bg.setStroke((int) (1.2f * density), COLOR_CARD_BORDER);
        card.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = (int) (8 * density);
        card.setLayoutParams(params);
        return card;
    }

    private static TextView createTabButton(Activity activity, String text, boolean active, float density) {
        TextView tab = new TextView(activity);
        tab.setText(text);
        tab.setTextSize(11f);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(0, (int) (7 * density), 0, (int) (7 * density));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        tab.setLayoutParams(lp);
        updateTabStyle(tab, active, density);
        return tab;
    }

    private static void updateTabStyle(TextView tab, boolean active, float density) {
        tab.setTextColor(active ? COLOR_TEXT_PRIMARY : COLOR_TEXT_MUTED);
        tab.setTypeface(Typeface.create("sans-serif-medium", active ? Typeface.BOLD : Typeface.NORMAL));

        GradientDrawable bg = new GradientDrawable();
        if (active) {
            bg.setColor(0x550A84FF); // Active Cyber Glow
            bg.setStroke((int) (1.2f * density), COLOR_ACCENT_CYAN);
        } else {
            bg.setColor(Color.TRANSPARENT);
        }
        bg.setCornerRadius(10 * density);
        tab.setBackground(bg);
    }

    private static CyberSwitchView addCyberSwitchRow(LinearLayout container, String title, String subtitle, boolean initialChecked, float density, Activity activity) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (4 * density), (int) (8 * density), (int) (4 * density), (int) (8 * density));

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textColParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textColParams.rightMargin = (int) (10 * density);
        textCol.setLayoutParams(textColParams);

        TextView titleTv = new TextView(activity);
        titleTv.setText(title);
        titleTv.setTextColor(COLOR_TEXT_PRIMARY);
        titleTv.setTextSize(13.5f);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textCol.addView(titleTv);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subTv = new TextView(activity);
            subTv.setText(subtitle);
            subTv.setTextColor(COLOR_TEXT_MUTED);
            subTv.setTextSize(10.5f);
            subTv.setLineSpacing(1.2f, 1.1f);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            subParams.topMargin = (int) (2 * density);
            subTv.setLayoutParams(subParams);
            textCol.addView(subTv);
        }

        row.addView(textCol);

        CyberSwitchView cyberSwitch = new CyberSwitchView(activity);
        cyberSwitch.setChecked(initialChecked, false);
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                (int) (48 * density), (int) (27 * density)
        );
        cyberSwitch.setLayoutParams(switchParams);
        row.addView(cyberSwitch);

        // Click row to toggle with feedback
        row.setClickable(true);
        row.setOnClickListener(v -> cyberSwitch.toggleWithFeedback());

        container.addView(row);
        return cyberSwitch;
    }

    public interface OnValueChangeListener {
        void onValueChanged(int newValue);
        default void onValueCommitted(int finalValue) {}
    }

    private static SeekBar addGlassSeekBarWithBadge(LinearLayout layout, final String labelPrefix, String initialBadge, int progress, int max, final int minVal, String hint, float density, Activity activity) {
        return addGlassSeekBarWithBadge(layout, labelPrefix, initialBadge, progress, max, minVal, hint, density, activity, null);
    }

    private static SeekBar addGlassSeekBarWithBadge(LinearLayout layout, final String labelPrefix, String initialBadge, int progress, int max, final int minVal, String hint, float density, Activity activity, OnValueChangeListener listener) {
        // Label & Badge Header
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, (int) (4 * density), 0, (int) (4 * density));

        TextView labelTv = new TextView(activity);
        labelTv.setText(labelPrefix);
        labelTv.setTextColor(COLOR_TEXT_SECONDARY);
        labelTv.setTextSize(13);
        labelTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        labelTv.setLayoutParams(labelParams);
        headerRow.addView(labelTv);

        // Amber Gold Value Badge
        final TextView valBadge = new TextView(activity);
        valBadge.setText(initialBadge);
        valBadge.setTextColor(COLOR_ACCENT_GOLD);
        valBadge.setTextSize(11);
        valBadge.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        valBadge.setPadding((int) (8 * density), (int) (2 * density), (int) (8 * density), (int) (2 * density));

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0x33FFD760);
        badgeBg.setCornerRadius(6 * density);
        badgeBg.setStroke((int) (1 * density), COLOR_ACCENT_GOLD);
        valBadge.setBackground(badgeBg);
        headerRow.addView(valBadge);

        layout.addView(headerRow);

        // SeekBar với padding rộng và thumbOffset để núm tròn KHÔNG BAO GIỜ bị lòi dính ra ngoài viền
        final SeekBar seekBar = new SeekBar(activity);
        seekBar.setMax(max);
        seekBar.setProgress(progress);
        if (Build.VERSION.SDK_INT >= 21) {
            seekBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT_BLUE));
            seekBar.setThumbTintList(ColorStateList.valueOf(COLOR_ACCENT_CYAN));
        }

        // Padding ngang 16dp và thumbOffset 16dp triệt tiêu hoàn toàn hiện tượng dính mép viền
        int padH = (int) (16 * density);
        int padV = (int) (8 * density);
        seekBar.setPadding(padH, padV, padH, padV);
        seekBar.setThumbOffset(padH);

        LinearLayout.LayoutParams sbParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sbParams.setMargins(0, (int) (2 * density), 0, (int) (4 * density));
        seekBar.setLayoutParams(sbParams);

        final int[] lastVal = { Math.max(minVal, progress + minVal) };

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
                int val = Math.max(minVal, prog + minVal);
                if (labelPrefix.toLowerCase().contains("elo")) {
                    valBadge.setText(val + " ELO");
                } else if (labelPrefix.toLowerCase().contains("mũi tên") || labelPrefix.toLowerCase().contains("arrow")) {
                    valBadge.setText(val + " PV");
                } else {
                    valBadge.setText(String.valueOf(val));
                }

                if (listener != null) {
                    listener.onValueChanged(val);
                }

                // Rung xúc giác nhẹ mỗi nấc giá trị thay đổi khi người dùng kéo
                if (fromUser && val != lastVal[0]) {
                    lastVal[0] = val;
                    HapticHelper.tick(activity, sb);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                HapticHelper.tick(activity, sb);
            }
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                HapticHelper.pop(activity, sb);
                int val = Math.max(minVal, sb.getProgress() + minVal);
                if (listener != null) {
                    listener.onValueCommitted(val);
                }
            }
        });

        layout.addView(seekBar);

        if (hint != null && !hint.isEmpty()) {
            TextView hintTv = new TextView(activity);
            hintTv.setText(hint);
            hintTv.setTextColor(COLOR_TEXT_MUTED);
            hintTv.setTextSize(10.5f);
            hintTv.setPadding((int) (4 * density), 0, (int) (4 * density), (int) (4 * density));
            layout.addView(hintTv);
        }

        return seekBar;
    }

    private static void addCardSeparator(LinearLayout layout, float density) {
        View sep = new View(layout.getContext());
        sep.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density)
        ));
        GradientDivider(sep, 0x1AFFFFFF);
        layout.addView(sep);
    }

    private static void GradientDivider(View view, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        view.setBackground(d);
    }

    private static void addDialogSpacer(LinearLayout layout, int dpHeight, float density) {
        View spacer = new View(layout.getContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (dpHeight * density)
        ));
        layout.addView(spacer);
    }

    private static int blendColor(int from, int to, float ratio) {
        float inverseRatio = 1f - ratio;
        float a = Color.alpha(from) * inverseRatio + Color.alpha(to) * ratio;
        float r = Color.red(from) * inverseRatio + Color.red(to) * ratio;
        float g = Color.green(from) * inverseRatio + Color.green(to) * ratio;
        float b = Color.blue(from) * inverseRatio + Color.blue(to) * ratio;
        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }

    private static void addArrowPaletteSection(LinearLayout layout, float density, Activity activity) {
        // Title Row
        TextView titleTv = new TextView(activity);
        titleTv.setText(I18n.get(activity, "arrow_palette_title"));
        titleTv.setTextColor(COLOR_TEXT_PRIMARY);
        titleTv.setTextSize(13.5f);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleTv.setPadding((int) (4 * density), (int) (2 * density), (int) (4 * density), (int) (2 * density));
        layout.addView(titleTv);

        TextView hintTv = new TextView(activity);
        hintTv.setText(I18n.get(activity, "arrow_palette_hint"));
        hintTv.setTextColor(COLOR_TEXT_MUTED);
        hintTv.setTextSize(10.5f);
        hintTv.setPadding((int) (4 * density), 0, (int) (4 * density), (int) (8 * density));
        layout.addView(hintTv);

        // 5 Tiers Color Rows
        for (int t = 1; t <= 5; t++) {
            final int tier = t;
            final int currentColor = StockfishSettings.getArrowTierColor(activity, tier);

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (4 * density), (int) (6 * density), (int) (4 * density), (int) (6 * density));

            TextView tierLabel = new TextView(activity);
            tierLabel.setText(I18n.get(activity, "tier_" + tier));
            tierLabel.setTextColor(COLOR_TEXT_SECONDARY);
            tierLabel.setTextSize(12.5f);
            tierLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            LinearLayout.LayoutParams lpText = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            tierLabel.setLayoutParams(lpText);
            row.addView(tierLabel);

            // Cyber Color Swatch Box
            final View colorBox = new View(activity);
            int boxSize = (int) (28 * density);
            LinearLayout.LayoutParams lpBox = new LinearLayout.LayoutParams(boxSize, boxSize);
            colorBox.setLayoutParams(lpBox);

            final GradientDrawable boxBg = new GradientDrawable();
            boxBg.setColor(currentColor);
            boxBg.setCornerRadius(8 * density);
            boxBg.setStroke((int) (1.5f * density), 0xFFFFFFFF);
            colorBox.setBackground(boxBg);
            row.addView(colorBox);

            row.setClickable(true);
            row.setOnClickListener(v -> {
                HapticHelper.pop(activity, v);
                showColorPickerDialog(activity, tier, colorBox, boxBg, density);
            });

            layout.addView(row);
            if (t < 5) {
                addCardSeparator(layout, density);
            }
        }
    }

    private static void showColorPickerDialog(Activity activity, int tier, View colorBox, GradientDrawable boxBg, float density) {
        final Dialog pickerDialog = new Dialog(activity);
        pickerDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_BG_PANEL);
        bg.setCornerRadius(18 * density);
        bg.setStroke((int) (1.2f * density), COLOR_ACCENT_BLUE);
        pickerDialog.getWindow().setBackgroundDrawable(bg);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (18 * density), (int) (16 * density), (int) (18 * density), (int) (16 * density));

        TextView title = new TextView(activity);
        title.setText(I18n.get(activity, "choose_color") + " - " + I18n.get(activity, "tier_" + tier));
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(14.5f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        addDialogSpacer(root, 14, density);

        // Predefined Cyber Palette Swatches
        final int[][] presetColors = {
                {0xFFF0B84B, 0xFFFFB300, 0xFFFF7043, 0xFFFF5252}, // Warm: Gold, Amber, Orange, Coral
                {0xFF58B8FF, 0xFF00E5FF, 0xFF2979FF, 0xFF00B0FF}, // Cyber Blue & Cyan
                {0xFF34D399, 0xFF00E676, 0xFF76FF03, 0xFF1DE9B6}, // Neon Green & Mint
                {0xFFC084FC, 0xFFE040FB, 0xFFFF4081, 0xFFD500F9}, // Cyber Purple & Magenta
                {0xFFD9DDE6, 0xFFB0BEC5, 0xFFECEFF1, 0xFFFFFFFF}  // Silver & Platinum White
        };

        for (int[] rowColors : presetColors) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setPadding(0, (int) (4 * density), 0, (int) (4 * density));

            for (final int c : rowColors) {
                View swatch = new View(activity);
                int size = (int) (36 * density);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMargins((int) (5 * density), 0, (int) (5 * density), 0);
                swatch.setLayoutParams(lp);

                GradientDrawable sBg = new GradientDrawable();
                sBg.setColor(c);
                sBg.setCornerRadius(999 * density);
                sBg.setStroke((int) (1.2f * density), 0x55FFFFFF);
                swatch.setBackground(sBg);

                swatch.setOnClickListener(v -> {
                    HapticHelper.pop(activity, v);
                    StockfishSettings.setArrowTierColor(activity, tier, c);
                    boxBg.setColor(c);
                    colorBox.invalidate();
                    StockfishExtension.resetArrowSignature();
                    OverlayManager.invalidateArrowOverlay();
                    StockfishExtension.invalidateAllBoards();
                    StockfishExtension.triggerPlayAnalysisOnly();
                    pickerDialog.dismiss();
                });

                row.addView(swatch);
            }
            root.addView(row);
        }

        addDialogSpacer(root, 10, density);

        TextView cancelBtn = new TextView(activity);
        cancelBtn.setText(I18n.get(activity, "cancel"));
        cancelBtn.setTextColor(COLOR_TEXT_MUTED);
        cancelBtn.setTextSize(13);
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setPadding(0, (int) (8 * density), 0, (int) (8 * density));
        cancelBtn.setOnClickListener(v -> pickerDialog.dismiss());
        root.addView(cancelBtn);

        pickerDialog.setContentView(root);
        pickerDialog.show();
    }

    public interface OnEloChangeListener {
        void onEloChanged(int newElo);
        default void onEloCommitted(int finalElo) {}
    }

    private static SeekBar addEloSeekBarWithBadge(LinearLayout layout, final String labelPrefix, int currentElo, int progress, int max, String hint, float density, Activity activity, OnEloChangeListener listener) {
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, (int) (4 * density), 0, (int) (4 * density));

        TextView labelTv = new TextView(activity);
        labelTv.setText(labelPrefix);
        labelTv.setTextColor(COLOR_TEXT_SECONDARY);
        labelTv.setTextSize(13);
        labelTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        labelTv.setLayoutParams(labelParams);
        headerRow.addView(labelTv);

        final TextView valBadge = new TextView(activity);
        valBadge.setText(currentElo + " ELO · " + StockfishSettings.getEloTier(currentElo));
        valBadge.setTextColor(COLOR_ACCENT_GOLD);
        valBadge.setTextSize(11);
        valBadge.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        valBadge.setPadding((int) (8 * density), (int) (2 * density), (int) (8 * density), (int) (2 * density));

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0x33FFD760);
        badgeBg.setCornerRadius(6 * density);
        badgeBg.setStroke((int) (1 * density), COLOR_ACCENT_GOLD);
        valBadge.setBackground(badgeBg);
        headerRow.addView(valBadge);

        layout.addView(headerRow);

        final SeekBar seekBar = new SeekBar(activity);
        seekBar.setMax(max);
        seekBar.setProgress(progress);
        if (Build.VERSION.SDK_INT >= 21) {
            seekBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT_BLUE));
            seekBar.setThumbTintList(ColorStateList.valueOf(COLOR_ACCENT_CYAN));
        }

        int padH = (int) (16 * density);
        int padV = (int) (8 * density);
        seekBar.setPadding(padH, padV, padH, padV);
        seekBar.setThumbOffset(padH);

        LinearLayout.LayoutParams sbParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sbParams.setMargins(0, (int) (2 * density), 0, (int) (4 * density));
        seekBar.setLayoutParams(sbParams);

        final int[] lastElo = { currentElo };

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
                int elo = StockfishSettings.MIN_ELO + prog * StockfishSettings.ELO_STEP;
                valBadge.setText(elo + " ELO · " + StockfishSettings.getEloTier(elo));
                if (listener != null) listener.onEloChanged(elo);

                if (fromUser && elo != lastElo[0]) {
                    lastElo[0] = elo;
                    HapticHelper.tick(activity, sb);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                HapticHelper.tick(activity, sb);
            }
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                HapticHelper.pop(activity, sb);
                int elo = StockfishSettings.MIN_ELO + sb.getProgress() * StockfishSettings.ELO_STEP;
                if (listener != null) listener.onEloCommitted(elo);
            }
        });

        layout.addView(seekBar);

        if (hint != null && !hint.isEmpty()) {
            TextView hintTv = new TextView(activity);
            hintTv.setText(hint);
            hintTv.setTextColor(COLOR_TEXT_MUTED);
            hintTv.setTextSize(10.5f);
            hintTv.setPadding((int) (4 * density), 0, (int) (4 * density), (int) (4 * density));
            layout.addView(hintTv);
        }

        return seekBar;
    }

    private static SeekBar addDepthSeekBarWithBadge(LinearLayout layout, final String labelPrefix, String initialBadge, int progress, int max, final int minVal, String hint, float density, Activity activity, TextView[] badgeRef) {
        return addDepthSeekBarWithBadge(layout, labelPrefix, initialBadge, progress, max, minVal, hint, density, activity, badgeRef, null);
    }

    private static SeekBar addDepthSeekBarWithBadge(LinearLayout layout, final String labelPrefix, String initialBadge, int progress, int max, final int minVal, String hint, float density, Activity activity, TextView[] badgeRef, OnValueChangeListener listener) {
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, (int) (4 * density), 0, (int) (4 * density));

        TextView labelTv = new TextView(activity);
        labelTv.setText(labelPrefix);
        labelTv.setTextColor(COLOR_TEXT_SECONDARY);
        labelTv.setTextSize(13);
        labelTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        labelTv.setLayoutParams(labelParams);
        headerRow.addView(labelTv);

        final TextView valBadge = new TextView(activity);
        valBadge.setText(initialBadge);
        valBadge.setTextColor(COLOR_ACCENT_GOLD);
        valBadge.setTextSize(11);
        valBadge.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        valBadge.setPadding((int) (8 * density), (int) (2 * density), (int) (8 * density), (int) (2 * density));

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0x33FFD760);
        badgeBg.setCornerRadius(6 * density);
        badgeBg.setStroke((int) (1 * density), COLOR_ACCENT_GOLD);
        valBadge.setBackground(badgeBg);
        headerRow.addView(valBadge);

        if (badgeRef != null && badgeRef.length > 0) {
            badgeRef[0] = valBadge;
        }

        layout.addView(headerRow);

        final SeekBar seekBar = new SeekBar(activity);
        seekBar.setMax(max);
        seekBar.setProgress(progress);
        if (Build.VERSION.SDK_INT >= 21) {
            seekBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT_BLUE));
            seekBar.setThumbTintList(ColorStateList.valueOf(COLOR_ACCENT_CYAN));
        }

        int padH = (int) (16 * density);
        int padV = (int) (8 * density);
        seekBar.setPadding(padH, padV, padH, padV);
        seekBar.setThumbOffset(padH);

        LinearLayout.LayoutParams sbParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sbParams.setMargins(0, (int) (2 * density), 0, (int) (4 * density));
        seekBar.setLayoutParams(sbParams);

        final int[] lastVal = { Math.max(minVal, progress + minVal) };

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
                int val = Math.max(minVal, prog + minVal);
                if (valBadge.getText().toString().contains("Auto")) return;
                valBadge.setText(String.valueOf(val));

                if (listener != null) {
                    listener.onValueChanged(val);
                }

                if (fromUser && val != lastVal[0]) {
                    lastVal[0] = val;
                    HapticHelper.tick(activity, sb);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                HapticHelper.tick(activity, sb);
            }
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                HapticHelper.pop(activity, sb);
                int val = Math.max(minVal, sb.getProgress() + minVal);
                if (listener != null) {
                    listener.onValueCommitted(val);
                }
            }
        });

        layout.addView(seekBar);

        if (hint != null && !hint.isEmpty()) {
            TextView hintTv = new TextView(activity);
            hintTv.setText(hint);
            hintTv.setTextColor(COLOR_TEXT_MUTED);
            hintTv.setTextSize(10.5f);
            hintTv.setPadding((int) (4 * density), 0, (int) (4 * density), (int) (4 * density));
            layout.addView(hintTv);
        }

        return seekBar;
    }

    public static class CyberSelectRow {
        public final LinearLayout row;
        public final TextView label;
        public final TextView subtitle;
        public final TextView selectBtn;

        public CyberSelectRow(LinearLayout row, TextView label, TextView subtitle, TextView selectBtn) {
            this.row = row;
            this.label = label;
            this.subtitle = subtitle;
            this.selectBtn = selectBtn;
        }
    }

    private static CyberSelectRow addCyberSelectRow(
            LinearLayout container,
            String title,
            String subtitle,
            String initialValue,
            float density,
            View.OnClickListener onClickListener) {

        LinearLayout row = new LinearLayout(container.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

        LinearLayout textCol = new LinearLayout(container.getContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textParams.rightMargin = (int) (12 * density);
        textCol.setLayoutParams(textParams);

        TextView labelTv = new TextView(container.getContext());
        labelTv.setText(title);
        labelTv.setTextColor(COLOR_TEXT_PRIMARY);
        labelTv.setTextSize(13f);
        labelTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textCol.addView(labelTv);

        TextView subTv = new TextView(container.getContext());
        subTv.setText(subtitle != null ? subtitle : "");
        subTv.setTextColor(COLOR_TEXT_MUTED);
        subTv.setTextSize(10.5f);
        subTv.setPadding(0, (int) (2 * density), 0, 0);
        if (subtitle == null || subtitle.isEmpty()) {
            subTv.setVisibility(View.GONE);
        }
        textCol.addView(subTv);

        row.addView(textCol);

        // Cyber Dropdown Select Button (nncv-select design)
        TextView selectBtn = new TextView(container.getContext());
        selectBtn.setText(initialValue + "  ▾");
        selectBtn.setTextColor(COLOR_TEXT_PRIMARY);
        selectBtn.setTextSize(12.5f);
        selectBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        selectBtn.setGravity(Gravity.CENTER);
        selectBtn.setPadding((int) (14 * density), (int) (7 * density), (int) (14 * density), (int) (7 * density));

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0x1FFFFFFF); // Dark frosted glass
        btnBg.setCornerRadius(10 * density);
        btnBg.setStroke((int) (1.2f * density), COLOR_CARD_BORDER);
        selectBtn.setBackground(btnBg);

        selectBtn.setOnClickListener(onClickListener);
        row.addView(selectBtn);

        container.addView(row);
        return new CyberSelectRow(row, labelTv, subTv, selectBtn);
    }

    public static class PickerOption {
        public final String id;
        public final String label;
        public final String desc;
        public final String badge;

        public PickerOption(String id, String label, String desc) {
            this(id, label, desc, null);
        }

        public PickerOption(String id, String label, String desc, String badge) {
            this.id = id;
            this.label = label;
            this.desc = desc;
            this.badge = badge;
        }
    }

    public interface OnOptionSelectedListener {
        void onSelected(PickerOption option);
    }

    private static void showCyberPickerDialog(
            final Activity activity,
            final float density,
            final String title,
            final PickerOption[] options,
            final String currentId,
            final OnOptionSelectedListener listener) {

        final Dialog pickerDialog = new Dialog(activity);
        pickerDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int pickerWidth = (int) (dm.widthPixels * 0.85f);

        GradientDrawable pickerBg = new GradientDrawable();
        pickerBg.setColor(0xF50C0F16);
        pickerBg.setCornerRadius(18 * density);
        pickerBg.setStroke((int) (1.4f * density), COLOR_ACCENT_BLUE);
        pickerDialog.getWindow().setBackgroundDrawable(pickerBg);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (18 * density), (int) (16 * density), (int) (18 * density), (int) (16 * density));

        TextView header = new TextView(activity);
        header.setText(title);
        header.setTextColor(COLOR_TEXT_PRIMARY);
        header.setTextSize(14f);
        header.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        header.setGravity(Gravity.CENTER);
        root.addView(header);

        View div = new View(activity);
        div.setBackgroundColor(0x3364D2FF);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density));
        divParams.topMargin = (int) (10 * density);
        divParams.bottomMargin = (int) (10 * density);
        div.setLayoutParams(divParams);
        root.addView(div);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        for (final PickerOption opt : options) {
            final boolean isSelected = opt.id.equalsIgnoreCase(currentId);

            LinearLayout itemRow = new LinearLayout(activity);
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(isSelected ? 0x2A0A84FF : 0x0AFFFFFF);
            rowBg.setCornerRadius(10 * density);
            if (isSelected) {
                rowBg.setStroke((int) (1.2f * density), COLOR_ACCENT_BLUE);
            }
            itemRow.setBackground(rowBg);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = (int) (6 * density);
            itemRow.setLayoutParams(rowParams);

            if (opt.badge != null && !opt.badge.isEmpty()) {
                TextView badgeTv = new TextView(activity);
                badgeTv.setText(opt.badge);
                badgeTv.setTextColor(0xFFFFFFFF);
                badgeTv.setTextSize(11f);
                badgeTv.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                badgeTv.setGravity(Gravity.CENTER);
                badgeTv.setPadding((int) (6 * density), (int) (2 * density), (int) (6 * density), (int) (2 * density));

                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setColor(COLOR_CHESS_TITLE_BG);
                badgeBg.setCornerRadius(3 * density);
                badgeTv.setBackground(badgeBg);
                itemRow.addView(badgeTv);
            }

            LinearLayout textContainer = new LinearLayout(activity);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tcParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (opt.badge != null && !opt.badge.isEmpty()) {
                tcParams.leftMargin = (int) (8 * density);
            }
            textContainer.setLayoutParams(tcParams);

            TextView labelTv = new TextView(activity);
            labelTv.setText(opt.label);
            labelTv.setTextColor(isSelected ? COLOR_TEXT_PRIMARY : COLOR_TEXT_SECONDARY);
            labelTv.setTextSize(13f);
            labelTv.setTypeface(Typeface.create("sans-serif-medium", isSelected ? Typeface.BOLD : Typeface.NORMAL));
            textContainer.addView(labelTv);

            if (opt.desc != null && !opt.desc.isEmpty()) {
                TextView descTv = new TextView(activity);
                descTv.setText(opt.desc);
                descTv.setTextColor(COLOR_TEXT_MUTED);
                descTv.setTextSize(10.5f);
                descTv.setPadding(0, (int) (2 * density), 0, 0);
                textContainer.addView(descTv);
            }

            itemRow.addView(textContainer);

            if (isSelected) {
                TextView check = new TextView(activity);
                check.setText("✓");
                check.setTextColor(COLOR_GREEN_READY);
                check.setTextSize(14f);
                check.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                itemRow.addView(check);
            }

            itemRow.setOnClickListener(v -> {
                HapticHelper.tick(activity, v);
                if (listener != null) {
                    listener.onSelected(opt);
                }
                pickerDialog.dismiss();
            });

            list.addView(itemRow);
        }

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (dm.heightPixels * 0.45f));
        scroll.setLayoutParams(scrollParams);
        root.addView(scroll);

        pickerDialog.setContentView(root);
        pickerDialog.show();
        if (pickerDialog.getWindow() != null) {
            pickerDialog.getWindow().setLayout(pickerWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static final String[][] FIDE_TITLES = {
        {"GM", "Grandmaster", "Đại Kiện Tướng"},
        {"IM", "International Master", "Kiện Tướng Quốc Tế"},
        {"FM", "FIDE Master", "Kiện Tướng FIDE"},
        {"CM", "Candidate Master", "Dự Bị Kiện Tướng"},
        {"NM", "National Master", "Kiện Tướng Quốc Gia"},
        {"WGM", "Woman Grandmaster", "Nữ Đại Kiện Tướng"},
        {"WIM", "Woman International Master", "Nữ Kiện Tướng Quốc Tế"},
        {"WFM", "Woman FIDE Master", "Nữ Kiện Tướng FIDE"},
        {"WCM", "Woman Candidate Master", "Nữ Dự Bị Kiện Tướng"},
        {"WNM", "Woman National Master", "Nữ Kiện Tướng Quốc Gia"}
    };

    public static String getTitleDisplayName(Activity activity, String code) {
        if (code == null) return "";
        for (String[] t : FIDE_TITLES) {
            if (t[0].equalsIgnoreCase(code)) {
                return I18n.isVi(activity) ? t[2] : t[1];
            }
        }
        return code;
    }

    private static void showTitlePickerDialog(final Activity activity, final float density, final TextView selectorBtn, final TextView subtitleView, final boolean[] hasTitleChanged) {
        final Dialog pickerDialog = new Dialog(activity);
        pickerDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int pickerWidth = (int) (dm.widthPixels * 0.85f);

        GradientDrawable pickerBg = new GradientDrawable();
        pickerBg.setColor(0xF50C0F16);
        pickerBg.setCornerRadius(18 * density);
        pickerBg.setStroke((int) (1.4f * density), COLOR_CHESS_TITLE_STROKE);
        pickerDialog.getWindow().setBackgroundDrawable(pickerBg);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (18 * density), (int) (16 * density), (int) (18 * density), (int) (16 * density));

        // Header Title
        TextView header = new TextView(activity);
        header.setText(I18n.isVi(activity) ? "CHỌN DANH HIỆU" : "SELECT TITLE");
        header.setTextColor(COLOR_TEXT_PRIMARY);
        header.setTextSize(14f);
        header.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        header.setGravity(Gravity.CENTER);
        root.addView(header);

        View div = new View(activity);
        div.setBackgroundColor(0x447C2929);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density));
        divParams.topMargin = (int) (10 * density);
        divParams.bottomMargin = (int) (10 * density);
        div.setLayoutParams(divParams);
        root.addView(div);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        String curCode = StockfishSettings.getCustomTitleCode(activity);

        for (final String[] t : FIDE_TITLES) {
            final String code = t[0];
            String nameEn = t[1];
            String nameVi = t[2];
            final boolean isSelected = code.equalsIgnoreCase(curCode);

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (10 * density), (int) (8 * density), (int) (10 * density), (int) (8 * density));

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(isSelected ? 0x2A7C2929 : 0x0AFFFFFF);
            rowBg.setCornerRadius(10 * density);
            if (isSelected) {
                rowBg.setStroke((int) (1.2f * density), COLOR_CHESS_TITLE_STROKE);
            }
            row.setBackground(rowBg);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = (int) (6 * density);
            row.setLayoutParams(rowParams);

            // Red Title Badge (Chuẩn 100% Design System Chess.com: bgChesstitle 0xFF7C2929, corner radius 3dp)
            TextView badge = new TextView(activity);
            badge.setText(code);
            badge.setTextColor(0xFFFFFFFF);
            badge.setTextSize(11f);
            badge.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            badge.setGravity(Gravity.CENTER);
            badge.setPadding((int) (6 * density), (int) (2 * density), (int) (6 * density), (int) (2 * density));

            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(COLOR_CHESS_TITLE_BG); // 0xFF7C2929 (bgChesstitle chuẩn Chess.com)
            badgeBg.setCornerRadius(3 * density); // tokens.k.size3 (3dp)
            badge.setBackground(badgeBg);
            row.addView(badge);

            // Description Label
            TextView desc = new TextView(activity);
            desc.setText("  " + (I18n.isVi(activity) ? nameVi : nameEn));
            desc.setTextColor(isSelected ? COLOR_TEXT_PRIMARY : COLOR_TEXT_SECONDARY);
            desc.setTextSize(12.5f);
            desc.setTypeface(Typeface.create("sans-serif", isSelected ? Typeface.BOLD : Typeface.NORMAL));
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            desc.setLayoutParams(descParams);
            row.addView(desc);

            // Checkmark if selected
            if (isSelected) {
                TextView check = new TextView(activity);
                check.setText("✓");
                check.setTextColor(COLOR_GREEN_READY);
                check.setTextSize(14f);
                check.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                row.addView(check);
            }

            final String descText = I18n.isVi(activity) ? nameVi : nameEn;

            row.setOnClickListener(v -> {
                HapticHelper.tick(activity, v);
                StockfishSettings.setCustomTitleCode(activity, code);
                selectorBtn.setText(code + "  ▾");
                if (subtitleView != null) {
                    subtitleView.setText(descText);
                    subtitleView.setVisibility(View.VISIBLE);
                }
                hasTitleChanged[0] = true;
                try {
                    activity.getWindow().getDecorView().invalidate();
                    activity.getWindow().getDecorView().requestLayout();
                } catch (Throwable ignored) {}
                pickerDialog.dismiss();
            });

            list.addView(row);
        }

        // Set max height for ScrollView (300dp or 45% screen height)
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (dm.heightPixels * 0.45f));
        scroll.setLayoutParams(scrollParams);
        root.addView(scroll);

        pickerDialog.setContentView(root);
        pickerDialog.show();
        if (pickerDialog.getWindow() != null) {
            pickerDialog.getWindow().setLayout(pickerWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}

