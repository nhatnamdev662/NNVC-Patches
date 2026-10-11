package app.prathxm.chess.extension.stockfish;

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

import app.prathxm.chess.extension.BuildConfig;

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

        dialog.setOnDismissListener(d -> borderGlowAnim.cancel());

        final int[] currentTabIndex = { 0 };
        View windowRoot = buildSettingsContentView(activity, dialog, currentTabIndex, targetDialogWidth, targetDialogHeight, density);
        dialog.setContentView(windowRoot);
        dialog.show();

        // Đảm bảo kích thước cửa sổ chuẩn cố định sau khi show
        dialog.getWindow().setLayout(targetDialogWidth, targetDialogHeight);
    }

    private static View buildSettingsContentView(final Activity activity, final Dialog dialog, final int[] currentTabIndex, final int targetDialogWidth, final int targetDialogHeight, final float density) {
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
            View newRoot = buildSettingsContentView(activity, dialog, currentTabIndex, targetDialogWidth, targetDialogHeight, density);
            dialog.setContentView(newRoot);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(targetDialogWidth, targetDialogHeight);
            }
        });
        headerBtns.addView(langBtn);

        // Spacer between Lang button and Log button
        View hdrSpacer1 = new View(activity);
        hdrSpacer1.setLayoutParams(new LinearLayout.LayoutParams((int) (8 * density), 1));
        headerBtns.addView(hdrSpacer1);

        // Debug Log Button (📜)
        TextView logBtn = new TextView(activity);
        logBtn.setText("📜");
        logBtn.setTextColor(COLOR_TEXT_PRIMARY);
        logBtn.setTextSize(12);
        logBtn.setGravity(Gravity.CENTER);
        int logPad = (int) (6 * density);
        logBtn.setPadding(logPad, (int) (3 * density), logPad, (int) (3 * density));

        GradientDrawable logBg = new GradientDrawable();
        logBg.setColor(0x22FFFFFF);
        logBg.setCornerRadius(8 * density);
        logBg.setStroke((int) (1 * density), COLOR_CARD_BORDER);
        logBtn.setBackground(logBg);

        logBtn.setOnClickListener(v -> {
            HapticHelper.pop(activity, v);
            NnvcLogViewerDialog.show(activity);
        });
        headerBtns.addView(logBtn);

        // Spacer between Log button and Close button
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
        statusLabel.setText(StockfishSettings.isEngineEnabled(activity) ? "ENGINE READY" : "ENGINE OFF");
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
            statusLabel.setText(isChecked ? "ENGINE READY" : "ENGINE OFF");
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

        LinearLayout engineBtnRow = new LinearLayout(activity);
        engineBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        engineBtnRow.setPadding(0, (int) (2 * density), 0, (int) (4 * density));
        engineChoiceCard.addView(engineBtnRow);

        final String[] curEngine = { StockfishSettings.getEngineChoice(activity) };
        final TextView btnKomodo = createTabButton(activity, I18n.get(activity, "engine_komodo"), StockfishSettings.ENGINE_KOMODO.equals(curEngine[0]), density);
        final TextView btnStockfish = createTabButton(activity, I18n.get(activity, "engine_stockfish"), StockfishSettings.ENGINE_STOCKFISH18.equals(curEngine[0]), density);
        engineBtnRow.addView(btnKomodo);
        View btnSpacing2 = new View(activity);
        btnSpacing2.setLayoutParams(new LinearLayout.LayoutParams((int) (8 * density), 1));
        engineBtnRow.addView(btnSpacing2);
        engineBtnRow.addView(btnStockfish);

        // Komodo Styles container
        final LinearLayout komodoStyleLayout = new LinearLayout(activity);
        komodoStyleLayout.setOrientation(LinearLayout.VERTICAL);
        komodoStyleLayout.setVisibility(StockfishSettings.ENGINE_KOMODO.equals(curEngine[0]) ? View.VISIBLE : View.GONE);
        komodoStyleLayout.setPadding(0, (int) (8 * density), 0, (int) (2 * density));
        engineChoiceCard.addView(komodoStyleLayout);

        TextView styleTitle = new TextView(activity);
        styleTitle.setText(I18n.get(activity, "komodo_style"));
        styleTitle.setTextColor(COLOR_TEXT_SECONDARY);
        styleTitle.setTextSize(12f);
        styleTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        komodoStyleLayout.addView(styleTitle);

        LinearLayout styleBtnRow = new LinearLayout(activity);
        styleBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        styleBtnRow.setPadding(0, (int) (4 * density), 0, 0);
        komodoStyleLayout.addView(styleBtnRow);

        final String[] curStyle = { StockfishSettings.getKomodoStyle(activity) };
        final TextView btnStyleDef = createTabButton(activity, I18n.get(activity, "style_default"), StockfishSettings.STYLE_DEFAULT.equals(curStyle[0]), density);
        final TextView btnStyleAgg = createTabButton(activity, I18n.get(activity, "style_aggressive"), StockfishSettings.STYLE_AGGRESSIVE.equals(curStyle[0]), density);
        final TextView btnStyleDefens = createTabButton(activity, I18n.get(activity, "style_defensive"), StockfishSettings.STYLE_DEFENSIVE.equals(curStyle[0]), density);
        styleBtnRow.addView(btnStyleDef);
        View sSp1 = new View(activity); sSp1.setLayoutParams(new LinearLayout.LayoutParams((int) (6 * density), 1)); styleBtnRow.addView(sSp1);
        styleBtnRow.addView(btnStyleAgg);
        View sSp2 = new View(activity); sSp2.setLayoutParams(new LinearLayout.LayoutParams((int) (6 * density), 1)); styleBtnRow.addView(sSp2);
        styleBtnRow.addView(btnStyleDefens);

        btnKomodo.setOnClickListener(v -> {
            HapticHelper.tick(activity, v);
            curEngine[0] = StockfishSettings.ENGINE_KOMODO;
            StockfishSettings.setEngineChoice(activity, curEngine[0]);
            updateTabStyle(btnKomodo, true, density);
            updateTabStyle(btnStockfish, false, density);
            komodoStyleLayout.setVisibility(View.VISIBLE);
            if (StockfishSettings.isEngineEnabled(activity)) {
                StockfishExtension.triggerPlayAnalysisOnly();
            }
        });

        btnStockfish.setOnClickListener(v -> {
            HapticHelper.tick(activity, v);
            curEngine[0] = StockfishSettings.ENGINE_STOCKFISH18;
            StockfishSettings.setEngineChoice(activity, curEngine[0]);
            updateTabStyle(btnKomodo, false, density);
            updateTabStyle(btnStockfish, true, density);
            komodoStyleLayout.setVisibility(View.GONE);
            if (StockfishSettings.isEngineEnabled(activity)) {
                StockfishExtension.triggerPlayAnalysisOnly();
            }
        });

        btnStyleDef.setOnClickListener(v -> {
            HapticHelper.tick(activity, v);
            curStyle[0] = StockfishSettings.STYLE_DEFAULT;
            StockfishSettings.setKomodoStyle(activity, curStyle[0]);
            updateTabStyle(btnStyleDef, true, density);
            updateTabStyle(btnStyleAgg, false, density);
            updateTabStyle(btnStyleDefens, false, density);
            if (StockfishSettings.isEngineEnabled(activity)) {
                StockfishExtension.triggerPlayAnalysisOnly();
            }
        });
        btnStyleAgg.setOnClickListener(v -> {
            HapticHelper.tick(activity, v);
            curStyle[0] = StockfishSettings.STYLE_AGGRESSIVE;
            StockfishSettings.setKomodoStyle(activity, curStyle[0]);
            updateTabStyle(btnStyleDef, false, density);
            updateTabStyle(btnStyleAgg, true, density);
            updateTabStyle(btnStyleDefens, false, density);
            if (StockfishSettings.isEngineEnabled(activity)) {
                StockfishExtension.triggerPlayAnalysisOnly();
            }
        });
        btnStyleDefens.setOnClickListener(v -> {
            HapticHelper.tick(activity, v);
            curStyle[0] = StockfishSettings.STYLE_DEFENSIVE;
            StockfishSettings.setKomodoStyle(activity, curStyle[0]);
            updateTabStyle(btnStyleDef, false, density);
            updateTabStyle(btnStyleAgg, false, density);
            updateTabStyle(btnStyleDefens, true, density);
            if (StockfishSettings.isEngineEnabled(activity)) {
                StockfishExtension.triggerPlayAnalysisOnly();
            }
        });

        // Card 1.5: Nhật Ký Hoạt Động (Debug Log) & Floating Pill
        LinearLayout debugCard = createGlassCard(activity, density);
        panelEngine.addView(debugCard);

        TextView debugTitle = new TextView(activity);
        debugTitle.setText("📜 " + I18n.get(activity, "debug_log"));
        debugTitle.setTextColor(COLOR_TEXT_PRIMARY);
        debugTitle.setTextSize(13f);
        debugTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        debugCard.addView(debugTitle);

        TextView debugHint = new TextView(activity);
        debugHint.setText(I18n.get(activity, "debug_log_hint"));
        debugHint.setTextColor(COLOR_TEXT_MUTED);
        debugHint.setTextSize(9.5f);
        debugHint.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams dbhParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dbhParams.setMargins(0, (int) (2 * density), 0, (int) (8 * density));
        debugHint.setLayoutParams(dbhParams);
        debugCard.addView(debugHint);

        LinearLayout dbRow = new LinearLayout(activity);
        dbRow.setOrientation(LinearLayout.HORIZONTAL);
        dbRow.setGravity(Gravity.CENTER_VERTICAL);
        debugCard.addView(dbRow);

        TextView openLogBtn = createTabButton(activity, "📋 " + I18n.get(activity, "view_copy_log"), true, density);
        openLogBtn.setOnClickListener(v -> {
            HapticHelper.pop(activity, v);
            NnvcLogViewerDialog.show(activity);
        });
        dbRow.addView(openLogBtn);

        addCardSeparator(debugCard, density);

        CyberSwitchView pillSwitch = addCyberSwitchRow(debugCard,
                I18n.get(activity, "floating_log_pill"),
                I18n.get(activity, "floating_log_pill_hint"),
                StockfishSettings.isFloatingLogPillEnabled(activity),
                density, activity);
        pillSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            StockfishSettings.setFloatingLogPillEnabled(activity, isChecked);
            FloatingLogPillView.updateVisibility(activity);
        });

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

        TextView coachTitle = new TextView(activity);
        coachTitle.setText(I18n.get(activity, "coach_title"));
        coachTitle.setTextColor(COLOR_TEXT_PRIMARY);
        coachTitle.setTextSize(13.5f);
        coachTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        coachCard.addView(coachTitle);

        TextView coachSub = new TextView(activity);
        coachSub.setText(I18n.get(activity, "coach_sub"));
        coachSub.setTextColor(COLOR_TEXT_MUTED);
        coachSub.setTextSize(10.5f);
        coachSub.setPadding(0, (int) (2 * density), 0, (int) (8 * density));
        coachCard.addView(coachSub);

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

        threatSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            StockfishSettings.setThreatArrowsEnabled(activity, isChecked);
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

        // Version metadata
        TextView patchTv = new TextView(activity);
        patchTv.setText("NNVC v" + BuildConfig.PATCH_VERSION + " · by @nncutett");
        patchTv.setTextColor(COLOR_TEXT_MUTED);
        patchTv.setTextSize(10.5f);
        patchTv.setGravity(Gravity.CENTER);
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

    public static void showDiagnosticLogDialog(Activity activity) {
        if (activity == null) return;
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        float density = dm.density;

        final Dialog logDialog = new Dialog(activity);
        logDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF4080B12); // Deep Cyber Black Glass
        bg.setCornerRadius(20 * density);
        bg.setStroke((int) (1.4f * density), COLOR_ACCENT_BLUE);
        logDialog.getWindow().setBackgroundDrawable(bg);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (14 * density), (int) (14 * density), (int) (14 * density), (int) (14 * density));

        // ── 1. Header: Console Title & System Status Dot ─────────────────────────
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        boolean isVi = "vi".equalsIgnoreCase(StockfishSettings.getLanguage(activity));

        TextView titleTv = new TextView(activity);
        titleTv.setText(I18n.get(activity, "log_title"));
        titleTv.setTextColor(COLOR_ACCENT_CYAN);
        titleTv.setTextSize(13.5f);
        titleTv.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(titleTv, titleLp);

        // Status Badge
        boolean isTorchReady = TorchEngine.getInstance(activity).isReady();
        TextView statusBadge = new TextView(activity);
        statusBadge.setText(isTorchReady ? (isVi ? "● SẴN SÀNG" : "● READY") : (isVi ? "○ ĐANG KHỞI ĐỘNG" : "○ INITIALIZING"));
        statusBadge.setTextColor(isTorchReady ? COLOR_GREEN_READY : COLOR_ACCENT_GOLD);
        statusBadge.setTextSize(10.5f);
        statusBadge.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        statusBadge.setPadding((int) (8 * density), (int) (3 * density), (int) (8 * density), (int) (3 * density));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(isTorchReady ? 0x2230D158 : 0x22FFD760);
        badgeBg.setCornerRadius(6 * density);
        badgeBg.setStroke((int) (1 * density), isTorchReady ? 0x6630D158 : 0x66FFD760);
        statusBadge.setBackground(badgeBg);
        headerRow.addView(statusBadge);

        root.addView(headerRow);

        // Subtitle / Counter
        final TextView subTv = new TextView(activity);
        subTv.setTextColor(COLOR_TEXT_MUTED);
        subTv.setTextSize(10.5f);
        subTv.setPadding(0, (int) (4 * density), 0, (int) (6 * density));
        root.addView(subTv);

        // ── 2. Filter Chips Row (Tất cả / Torch / Stockfish / Elo / Lỗi) ─────────
        HorizontalScrollView filterScroll = new HorizontalScrollView(activity);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow = new LinearLayout(activity);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(0, (int) (2 * density), 0, (int) (8 * density));

        final String[] FILTER_KEYS = {"ALL", "TORCH", "STOCKFISH", "BOARD_ELO", "ERROR"};
        final String[] FILTER_LABELS = {"Tất cả", "Torch / Phân loại", "Stockfish", "Bàn cờ & Elo", "Lỗi / Cảnh báo"};
        final TextView[] chipViews = new TextView[FILTER_KEYS.length];
        final String[] currentFilter = {"ALL"};

        filterScroll.addView(filterRow);
        root.addView(filterScroll);

        // ── 3. Terminal Log Container ───────────────────────────────────────────
        final ScrollView sv = new ScrollView(activity);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (dm.heightPixels * 0.48f)
        ));
        GradientDrawable termBg = new GradientDrawable();
        termBg.setColor(0xFF06090F); // True Midnight Terminal
        termBg.setCornerRadius(12 * density);
        termBg.setStroke((int) (1.2f * density), 0x3338BDF8);
        sv.setBackground(termBg);
        sv.setVerticalScrollBarEnabled(true);
        sv.setScrollbarFadingEnabled(false);
        sv.setPadding((int) (10 * density), (int) (10 * density), (int) (10 * density), (int) (10 * density));

        final TextView logContentTv = new TextView(activity);
        logContentTv.setTextColor(0xFFE2E8F0);
        logContentTv.setTextSize(10.5f);
        logContentTv.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        logContentTv.setLineSpacing(3 * density, 1f);
        logContentTv.setTextIsSelectable(true);
        sv.addView(logContentTv);
        root.addView(sv);

        // Hàm auto-scroll xuống cuối cùng
        final Runnable scrollToBottom = () -> {
            sv.post(() -> sv.fullScroll(ScrollView.FOCUS_DOWN));
        };

        // Hàm cập nhật hiển thị log theo filter
        final Runnable reloadLogs = () -> {
            java.util.List<String> raw = TorchEngine.getRawLogs();
            boolean isViLang = "vi".equalsIgnoreCase(StockfishSettings.getLanguage(activity));
            subTv.setText(isViLang ? ("Tổng cộng: " + raw.size() + "/300 mục nhật ký • Bộ lọc: " + currentFilter[0])
                    : ("Total: " + raw.size() + "/300 entries • Filter: " + currentFilter[0]));
            SpannableStringBuilder spanned = buildHighlightedLogs(raw, currentFilter[0]);
            logContentTv.setText(spanned);
            scrollToBottom.run();
            sv.postDelayed(scrollToBottom, 120);
        };

        // Tạo các filter chips
        for (int i = 0; i < FILTER_KEYS.length; i++) {
            final int idx = i;
            final String key = FILTER_KEYS[i];
            TextView chip = new TextView(activity);
            chip.setText(FILTER_LABELS[i]);
            chip.setTextSize(11f);
            chip.setPadding((int) (12 * density), (int) (5 * density), (int) (12 * density), (int) (5 * density));
            chipViews[i] = chip;

            chip.setOnClickListener(v -> {
                HapticHelper.pop(activity, v);
                currentFilter[0] = key;
                for (int j = 0; j < chipViews.length; j++) {
                    boolean sel = (j == idx);
                    GradientDrawable cBg = new GradientDrawable();
                    cBg.setCornerRadius(14 * density);
                    if (sel) {
                        cBg.setColor(COLOR_ACCENT_BLUE);
                        cBg.setStroke((int) (1 * density), COLOR_ACCENT_CYAN);
                        chipViews[j].setTextColor(COLOR_TEXT_PRIMARY);
                        chipViews[j].setTypeface(Typeface.DEFAULT_BOLD);
                    } else {
                        cBg.setColor(0x441E2536);
                        cBg.setStroke((int) (1 * density), 0x2264D2FF);
                        chipViews[j].setTextColor(0xFFA0B4D2);
                        chipViews[j].setTypeface(Typeface.DEFAULT);
                    }
                    chipViews[j].setBackground(cBg);
                }
                reloadLogs.run();
            });

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            clp.setMargins(0, 0, (int) (6 * density), 0);
            filterRow.addView(chip, clp);
        }

        // Kích hoạt mặc định chip "Tất cả"
        GradientDrawable initSelBg = new GradientDrawable();
        initSelBg.setColor(COLOR_ACCENT_BLUE);
        initSelBg.setCornerRadius(14 * density);
        initSelBg.setStroke((int) (1 * density), COLOR_ACCENT_CYAN);
        chipViews[0].setBackground(initSelBg);
        chipViews[0].setTextColor(COLOR_TEXT_PRIMARY);
        chipViews[0].setTypeface(Typeface.DEFAULT_BOLD);

        for (int j = 1; j < chipViews.length; j++) {
            GradientDrawable unBg = new GradientDrawable();
            unBg.setColor(0x441E2536);
            unBg.setCornerRadius(14 * density);
            unBg.setStroke((int) (1 * density), 0x2264D2FF);
            chipViews[j].setBackground(unBg);
            chipViews[j].setTextColor(0xFFA0B4D2);
        }

        addDialogSpacer(root, 10, density);

        // ── 4. Quick Toolbar & Action Buttons Row ───────────────────────────────
        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);

        // Nút Cuộn xuống cuối (Jump to Bottom)
        TextView jumpBottomBtn = new TextView(activity);
        jumpBottomBtn.setText(I18n.get(activity, "jump_bottom"));
        jumpBottomBtn.setTextColor(0xFF38BDF8);
        jumpBottomBtn.setTextSize(11.5f);
        jumpBottomBtn.setTypeface(Typeface.DEFAULT_BOLD);
        jumpBottomBtn.setPadding((int) (10 * density), (int) (6 * density), (int) (10 * density), (int) (6 * density));
        GradientDrawable jumpBg = new GradientDrawable();
        jumpBg.setColor(0x330284C7);
        jumpBg.setCornerRadius(8 * density);
        jumpBg.setStroke((int) (1 * density), 0x6638BDF8);
        jumpBottomBtn.setBackground(jumpBg);
        jumpBottomBtn.setOnClickListener(v -> {
            HapticHelper.pop(activity, v);
            scrollToBottom.run();
        });
        actionRow.addView(jumpBottomBtn);

        View sp1 = new View(activity);
        sp1.setLayoutParams(new LinearLayout.LayoutParams((int) (6 * density), 1));
        actionRow.addView(sp1);

        // Nút Cuộn lên đầu (Jump to Top)
        TextView jumpTopBtn = new TextView(activity);
        jumpTopBtn.setText(I18n.get(activity, "jump_top"));
        jumpTopBtn.setTextColor(COLOR_TEXT_MUTED);
        jumpTopBtn.setTextSize(11.5f);
        jumpTopBtn.setPadding((int) (10 * density), (int) (6 * density), (int) (10 * density), (int) (6 * density));
        GradientDrawable topBg = new GradientDrawable();
        topBg.setColor(0x221E2536);
        topBg.setCornerRadius(8 * density);
        jumpTopBtn.setBackground(topBg);
        jumpTopBtn.setOnClickListener(v -> {
            HapticHelper.pop(activity, v);
            sv.fullScroll(ScrollView.FOCUS_UP);
        });
        actionRow.addView(jumpTopBtn);

        View sp2 = new View(activity);
        sp2.setLayoutParams(new LinearLayout.LayoutParams((int) (6 * density), 1));
        actionRow.addView(sp2);

        // Nút Xóa Log (Clear)
        TextView clearBtn = new TextView(activity);
        clearBtn.setText(I18n.get(activity, "clear_log"));
        clearBtn.setTextColor(COLOR_DANGER_RED);
        clearBtn.setTextSize(11.5f);
        clearBtn.setPadding((int) (10 * density), (int) (6 * density), (int) (10 * density), (int) (6 * density));
        GradientDrawable clearBg = new GradientDrawable();
        clearBg.setColor(0x22FF453A);
        clearBg.setCornerRadius(8 * density);
        clearBg.setStroke((int) (1 * density), 0x44FF453A);
        clearBtn.setBackground(clearBg);
        clearBtn.setOnClickListener(v -> {
            HapticHelper.pop(activity, v);
            TorchEngine.clearLogs();
            reloadLogs.run();
            Toast.makeText(activity, I18n.get(activity, "cleared_toast"), Toast.LENGTH_SHORT).show();
        });
        actionRow.addView(clearBtn);

        // Spacer đẩy nút sang phải
        View flexSpacer = new View(activity);
        actionRow.addView(flexSpacer, new LinearLayout.LayoutParams(0, 1, 1f));

        // Nút Đóng
        TextView closeBtn = new TextView(activity);
        closeBtn.setText(I18n.get(activity, "close"));
        closeBtn.setTextColor(COLOR_TEXT_MUTED);
        closeBtn.setTextSize(12.5f);
        closeBtn.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));
        closeBtn.setOnClickListener(v -> logDialog.dismiss());
        actionRow.addView(closeBtn);

        View sp3 = new View(activity);
        sp3.setLayoutParams(new LinearLayout.LayoutParams((int) (6 * density), 1));
        actionRow.addView(sp3);

        // Nút Sao chép Log
        TextView copyBtn = new TextView(activity);
        copyBtn.setText(I18n.get(activity, "copy"));
        copyBtn.setTextColor(COLOR_TEXT_PRIMARY);
        copyBtn.setTextSize(12.5f);
        copyBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        copyBtn.setPadding((int) (16 * density), (int) (8 * density), (int) (16 * density), (int) (8 * density));
        GradientDrawable copyBg = new GradientDrawable();
        copyBg.setColor(COLOR_ACCENT_BLUE);
        copyBg.setCornerRadius(10 * density);
        copyBg.setStroke((int) (1 * density), COLOR_ACCENT_CYAN);
        copyBtn.setBackground(copyBg);

        copyBtn.setOnClickListener(v -> {
            HapticHelper.pop(activity, v);
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    CharSequence textToCopy = logContentTv.getText();
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Torch Diagnostics", textToCopy);
                    cm.setPrimaryClip(clip);
                    Toast.makeText(activity, I18n.get(activity, "copied_toast"), Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable t) {
                Toast.makeText(activity, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        actionRow.addView(copyBtn);

        root.addView(actionRow);

        // Tải nội dung log ban đầu và auto-scroll xuống đáy
        reloadLogs.run();

        logDialog.setContentView(root);
        logDialog.show();
        logDialog.getWindow().setLayout((int) (dm.widthPixels * 0.94f), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static SpannableStringBuilder buildHighlightedLogs(java.util.List<String> rawLogs, String filterMode) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (rawLogs == null || rawLogs.isEmpty()) {
            sb.append("Chưa có log nào được ghi nhận.");
            return sb;
        }

        for (String line : rawLogs) {
            if (line == null || line.trim().isEmpty()) continue;

            // Bộ lọc filterMode
            if ("TORCH".equals(filterMode)) {
                if (!line.contains("[TORCH") && !line.contains("[CLASSIFIER") && !line.contains("[BRIDGE") && !line.contains("[CLASSIFIED") && !line.contains("[ANALYZE")) {
                    continue;
                }
            } else if ("STOCKFISH".equals(filterMode)) {
                if (!line.contains("[STOCKFISH")) {
                    continue;
                }
            } else if ("BOARD_ELO".equals(filterMode)) {
                if (!line.contains("[BOARD") && !line.contains("[POSITION") && !line.contains("[ELO")) {
                    continue;
                }
            } else if ("ERROR".equals(filterMode)) {
                if (!line.contains("ERROR") && !line.contains("WARN") && !line.contains("BLOCKED") && !line.contains("Abort") && !line.contains("FAIL")) {
                    continue;
                }
            }

            int lineStart = sb.length();
            sb.append(line).append("\n");

            // 1. Timestamp (ví dụ 13:01:53.306)
            int firstSpace = line.indexOf(' ');
            if (firstSpace > 0 && firstSpace <= 16) {
                sb.setSpan(new ForegroundColorSpan(0xFF64748B), lineStart, lineStart + firstSpace, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // 2. Tag trong dấu [ ... ]
            int openBracket = line.indexOf('[');
            int closeBracket = line.indexOf(']', openBracket);
            if (openBracket >= 0 && closeBracket > openBracket) {
                String tag = line.substring(openBracket, closeBracket + 1);
                int tagStart = lineStart + openBracket;
                int tagEnd = lineStart + closeBracket + 1;

                int tagColor;
                boolean isBold = true;
                if (tag.contains("ERROR") || tag.contains("Abort") || tag.contains("FAIL")) {
                    tagColor = 0xFFFF453A; // Đỏ tươi
                } else if (tag.contains("WARN") || tag.contains("BLOCKED")) {
                    tagColor = 0xFFFF9F0A; // Vàng cam
                } else if (tag.contains("BESTMOVE") || tag.contains("CLASSIFIED")) {
                    tagColor = 0xFF30D158; // Xanh lá
                } else if (tag.contains("PARSED") || tag.contains("RESULT")) {
                    tagColor = 0xFFFFD760; // Vàng kim
                } else if (tag.contains("STOCKFISH GO") || tag.contains("ANALYZE SEND")) {
                    tagColor = 0xFF64D2FF; // Xanh lơ cyan
                } else if (tag.contains("BRIDGE") || tag.contains("TORCH") || tag.contains("INIT") || tag.contains("HTTP")) {
                    tagColor = 0xFFBF5AF2; // Tím neon
                } else if (tag.contains("ELO")) {
                    tagColor = 0xFF34D399; // Xanh ngọc mint
                } else if (tag.contains("BOARD") || tag.contains("POSITION")) {
                    tagColor = 0xFFFF375F; // Hồng rose
                } else {
                    tagColor = 0xFF0A84FF; // Xanh royal
                    isBold = false;
                }

                sb.setSpan(new ForegroundColorSpan(tagColor), tagStart, tagEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (isBold) {
                    sb.setSpan(new StyleSpan(Typeface.BOLD), tagStart, tagEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                // Nội dung tin nhắn sau tag
                if (tagEnd < lineStart + line.length()) {
                    sb.setSpan(new ForegroundColorSpan(0xFFF1F5F9), tagEnd, lineStart + line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                int msgStart = (firstSpace > 0) ? lineStart + firstSpace : lineStart;
                sb.setSpan(new ForegroundColorSpan(0xFFE2E8F0), msgStart, lineStart + line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        if (sb.length() == 0) {
            sb.append("Không có log nào khớp với bộ lọc đã chọn.");
        }
        return sb;
    }
}

