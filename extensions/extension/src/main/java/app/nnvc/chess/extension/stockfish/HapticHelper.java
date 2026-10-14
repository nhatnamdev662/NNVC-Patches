package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

public class HapticHelper {

    /**
     * Nhẹ nhàng (Tick) - Dùng khi trượt SeekBar hoặc chuyển Tab
     */
    public static void tick(Context context, View view) {
        try {
            if (view != null) {
                view.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                );
            }
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createOneShot(10, 120));
                } else {
                    v.vibrate(10);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Nảy rõ (Pop / Click) - Dùng khi Bật/Tắt Switch hoặc bấm nút
     */
    public static void pop(Context context, View view) {
        try {
            if (view != null) {
                view.performHapticFeedback(
                        HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                );
            }
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createOneShot(22, 180));
                } else {
                    v.vibrate(22);
                }
            }
        } catch (Throwable ignored) {}
    }
}
