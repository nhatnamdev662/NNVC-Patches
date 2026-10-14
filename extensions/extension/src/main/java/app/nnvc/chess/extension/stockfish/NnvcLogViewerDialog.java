package app.nnvc.chess.extension.stockfish;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * NnvcLogViewerDialog – In-app real-time viewer and controller for NNVC debug logs.
 *
 * Provides:
 *  - Full log inspection with high-contrast monospace rendering.
 *  - File path display (/sdcard/Android/data/com.chess/files/nnvc_debug.log).
 *  - One-tap clipboard copy.
 *  - One-tap clear.
 *  - Toggle for the Floating Draggable Log Pill on the chessboard.
 */
public class NnvcLogViewerDialog {

    private static final String TAG = "NnvcLogViewerDialog";

    public static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        final float density = activity.getResources().getDisplayMetrics().density;
        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;

        int dialogW = Math.min((int) (screenW * 0.94f), (int) (480 * density));
        int dialogH = Math.min((int) (screenH * 0.88f), (int) (620 * density));

        // Root container
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(dialogW, dialogH));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF00D1117);
        bg.setCornerRadius(18 * density);
        bg.setStroke((int) (1.4f * density), 0xAA64D2FF);
        root.setBackground(bg);
        root.setPadding((int) (14 * density), (int) (14 * density), (int) (14 * density), (int) (12 * density));

        // ── 1. Header ──
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, (int) (8 * density));

        TextView title = new TextView(activity);
        title.setText("📜 NNVC DEBUG LOG");
        title.setTextColor(0xFF64D2FF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        header.addView(title);

        TextView closeBtn = new TextView(activity);
        closeBtn.setText("✕");
        closeBtn.setTextColor(0xFF8B949E);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        closeBtn.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        closeBtn.setPadding((int) (8 * density), (int) (4 * density), (int) (8 * density), (int) (4 * density));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        header.addView(closeBtn);

        root.addView(header);

        // ── 2. File Path Subtitle ──
        TextView pathView = new TextView(activity);
        String logPath = NnvcLogger.getLogFilePath();
        pathView.setText("📁 Tệp: " + logPath);
        pathView.setTextColor(0xFF8B949E);
        pathView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        pathView.setTypeface(Typeface.MONOSPACE);
        pathView.setPadding(0, 0, 0, (int) (8 * density));
        root.addView(pathView);

        // ── 3. Scrollable Log Body ──
        final ScrollView scrollView = new ScrollView(activity);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        scrollParams.bottomMargin = (int) (10 * density);
        scrollView.setLayoutParams(scrollParams);

        GradientDrawable logBoxBg = new GradientDrawable();
        logBoxBg.setColor(0xFF030712);
        logBoxBg.setCornerRadius(10 * density);
        logBoxBg.setStroke((int) (1 * density), 0xFF1F2937);
        scrollView.setBackground(logBoxBg);
        scrollView.setPadding((int) (10 * density), (int) (10 * density), (int) (10 * density), (int) (10 * density));

        final TextView logTextView = new TextView(activity);
        logTextView.setText(NnvcLogger.getFormattedLogs());
        logTextView.setTextColor(0xFF4ADE80); // cyber green
        logTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        logTextView.setTypeface(Typeface.MONOSPACE);
        logTextView.setLineSpacing(2f, 1.15f);
        logTextView.setTextIsSelectable(true);
        scrollView.addView(logTextView);

        root.addView(scrollView);

        // Scroll to bottom after layout
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));

        // ── 4. Action Buttons ──
        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        // Copy Button
        TextView copyBtn = createActionButton(activity, "📋 Sao Chép", 0xFF0A84FF, density);
        copyBtn.setOnClickListener(v -> {
            HapticHelper.pop(activity, v);
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = ClipData.newPlainText("NNVC Debug Log", NnvcLogger.getFormattedLogs());
                cm.setPrimaryClip(clip);
                Toast.makeText(activity, "✓ Đã sao chép toàn bộ nhật ký!", Toast.LENGTH_SHORT).show();
            }
        });
        btnRow.addView(copyBtn);

        // Clear Button
        TextView clearBtn = createActionButton(activity, "🗑️ Xóa Log", 0xFFFA5252, density);
        clearBtn.setOnClickListener(v -> {
            HapticHelper.pop(activity, v);
            NnvcLogger.clear();
            logTextView.setText("Chưa có log nào.\nFile: " + NnvcLogger.getLogFilePath());
            Toast.makeText(activity, "✓ Đã xóa nhật ký!", Toast.LENGTH_SHORT).show();
        });
        btnRow.addView(clearBtn);

        // Floating Pill Toggle Button
        final boolean[] pillActive = { StockfishSettings.isFloatingLogPillEnabled(activity) };
        final TextView pillToggleBtn = createActionButton(activity,
                pillActive[0] ? "📌 Ẩn Pill Nổi" : "📌 Hiện Pill Nổi",
                0xFF8B5CF6, density);
        pillToggleBtn.setOnClickListener(v -> {
            HapticHelper.pop(activity, v);
            pillActive[0] = !pillActive[0];
            StockfishSettings.setFloatingLogPillEnabled(activity, pillActive[0]);
            pillToggleBtn.setText(pillActive[0] ? "📌 Ẩn Pill Nổi" : "📌 Hiện Pill Nổi");
            FloatingLogPillView.updateVisibility(activity);
            Toast.makeText(activity, pillActive[0] ? "✓ Đã bật nút log nổi trên bàn cờ" : "✓ Đã ẩn nút log nổi", Toast.LENGTH_SHORT).show();
        });
        btnRow.addView(pillToggleBtn);

        root.addView(btnRow);

        // ── 5. Real-time updates listener ──
        final NnvcLogger.LogUpdateListener updateListener = new NnvcLogger.LogUpdateListener() {
            @Override
            public void onNewLogLine(String line) {
                activity.runOnUiThread(() -> {
                    logTextView.append(line + "\n");
                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                });
            }

            @Override
            public void onLogsCleared() {
                activity.runOnUiThread(() -> {
                    logTextView.setText("Nhật ký đã xóa.");
                });
            }
        };

        NnvcLogger.addListener(updateListener);
        dialog.setOnDismissListener(d -> NnvcLogger.removeListener(updateListener));

        dialog.setContentView(root);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(dialogW, dialogH);
        }
    }

    private static TextView createActionButton(Context ctx, String text, int strokeColor, float density) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btn.setGravity(Gravity.CENTER);

        int padH = (int) (10 * density);
        int padV = (int) (7 * density);
        btn.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x22FFFFFF);
        bg.setCornerRadius(10 * density);
        bg.setStroke((int) (1.2f * density), strokeColor);
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lp.rightMargin = (int) (4 * density);
        lp.leftMargin = (int) (4 * density);
        btn.setLayoutParams(lp);

        return btn;
    }
}
