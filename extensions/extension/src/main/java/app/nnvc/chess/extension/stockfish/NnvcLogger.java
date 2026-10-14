package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/**
 * NnvcLogger – No-op production logger.
 * Completely eliminates file I/O, disk writes, and memory log buffers for release builds.
 */
public class NnvcLogger {

    public interface LogUpdateListener {
        void onNewLogLine(String line);
        void onLogsCleared();
    }

    public static void init(Context context) {
        // No-op in release
    }

    public static String getLogFilePath() {
        return "";
    }

    public static void addListener(LogUpdateListener listener) {}

    public static void removeListener(LogUpdateListener listener) {}

    public static void d(String tag, String msg) {}

    public static void i(String tag, String msg) {}

    public static void w(String tag, String msg) {}

    public static void e(String tag, String msg) {}

    public static void e(String tag, String msg, Throwable tr) {}

    public static void log(String msg) {}

    public static List<String> getRecentLogs() {
        return new ArrayList<>();
    }

    public static String getFormattedLogs() {
        return "";
    }

    public static void clear() {}
}
