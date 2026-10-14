package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NnvcLogger – Thread-safe diagnostic logger for NNVC.
 *
 * Logs to:
 *  1) Accessible file: /sdcard/Android/data/com.chess/files/nnvc_debug.log (or internal getFilesDir()).
 *  2) In-memory circular buffer (last 1000 lines) for real-time inspection in hidden developer viewer.
 *  3) Logcat via android.util.Log (safely caught for JVM tests).
 *  4) Real-time listener for NnvcLogViewerDialog.
 */
public class NnvcLogger {

    private static final String DEFAULT_TAG = "NNVC";
    private static final int MAX_MEMORY_LINES = 1000;
    private static final long MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB cap

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private static volatile Context appContext = null;
    private static volatile File logFile = null;
    private static final Object fileLock = new Object();

    private static final List<String> memoryLogs = new CopyOnWriteArrayList<>();

    private static final ExecutorService fileExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "nnvc-file-logger");
                t.setDaemon(true);
                return t;
            });

    public interface LogUpdateListener {
        void onNewLogLine(String line);
        void onLogsCleared();
    }

    private static final List<LogUpdateListener> listeners = new CopyOnWriteArrayList<>();
    private static Handler mainHandler = null;

    private static Handler getMainHandler() {
        if (mainHandler == null) {
            try {
                if (Looper.getMainLooper() != null) {
                    mainHandler = new Handler(Looper.getMainLooper());
                }
            } catch (Throwable ignored) {}
        }
        return mainHandler;
    }

    public static void init(Context context) {
        if (context == null) return;
        if (appContext == null) {
            appContext = context.getApplicationContext();
        }
        if (logFile == null) {
            setupLogFile(appContext);
            i("NnvcLogger", "=== NNVC DEBUG LOGGER INITIALIZED ===");
            i("NnvcLogger", "Log path: " + getLogFilePath());
            try {
                i("NnvcLogger", "Package: " + appContext.getPackageName());
            } catch (Throwable ignored) {}
        }
    }

    private static void setupLogFile(Context ctx) {
        try {
            File extDir = ctx.getExternalFilesDir(null);
            if (extDir != null && (extDir.exists() || extDir.mkdirs())) {
                logFile = new File(extDir, "nnvc_debug.log");
            } else {
                File intDir = ctx.getFilesDir();
                if (intDir != null && (intDir.exists() || intDir.mkdirs())) {
                    logFile = new File(intDir, "nnvc_debug.log");
                }
            }
        } catch (Throwable t) {
            try {
                android.util.Log.e(DEFAULT_TAG, "Failed to resolve log file: " + t.getMessage());
            } catch (Throwable ignored) {}
        }
    }

    public static String getLogFilePath() {
        if (logFile != null) {
            return logFile.getAbsolutePath();
        }
        if (appContext != null) {
            try {
                File ext = appContext.getExternalFilesDir(null);
                if (ext != null) {
                    return new File(ext, "nnvc_debug.log").getAbsolutePath();
                }
                return new File(appContext.getFilesDir(), "nnvc_debug.log").getAbsolutePath();
            } catch (Throwable ignored) {}
        }
        return "/sdcard/Android/data/com.chess/files/nnvc_debug.log";
    }

    public static void addListener(LogUpdateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(LogUpdateListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public static void d(String tag, String msg) {
        logLine("DEBUG", tag, msg, null);
        try {
            android.util.Log.d(tag, msg);
        } catch (Throwable ignored) {}
    }

    public static void i(String tag, String msg) {
        logLine("INFO", tag, msg, null);
        try {
            android.util.Log.i(tag, msg);
        } catch (Throwable ignored) {}
    }

    public static void w(String tag, String msg) {
        logLine("WARN", tag, msg, null);
        try {
            android.util.Log.w(tag, msg);
        } catch (Throwable ignored) {}
    }

    public static void e(String tag, String msg) {
        e(tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable tr) {
        logLine("ERROR", tag, msg, tr);
        try {
            if (tr != null) {
                android.util.Log.e(tag, msg, tr);
            } else {
                android.util.Log.e(tag, msg);
            }
        } catch (Throwable ignored) {}
    }

    public static void log(String msg) {
        i(DEFAULT_TAG, msg);
    }

    private static void logLine(String level, String tag, String msg, Throwable tr) {
        String timestamp;
        synchronized (TIME_FORMAT) {
            timestamp = TIME_FORMAT.format(new Date());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestamp).append("] [").append(level).append("] [").append(tag).append("] ");
        sb.append(msg != null ? msg : "null");

        if (tr != null) {
            sb.append("\nException: ").append(tr.getClass().getName()).append(": ").append(tr.getMessage()).append("\n");
            StringWriter sw = new StringWriter();
            tr.printStackTrace(new PrintWriter(sw));
            sb.append(sw.toString());
        }

        final String formattedLine = sb.toString();

        // 1. Add to memory circular buffer
        memoryLogs.add(formattedLine);
        while (memoryLogs.size() > MAX_MEMORY_LINES) {
            memoryLogs.remove(0);
        }

        // 2. Dispatch to UI listeners
        if (!listeners.isEmpty()) {
            Handler h = getMainHandler();
            if (h != null) {
                h.post(() -> {
                    for (LogUpdateListener l : listeners) {
                        try {
                            l.onNewLogLine(formattedLine);
                        } catch (Throwable ignored) {}
                    }
                });
            }
        }

        // 3. Append to disk asynchronously
        fileExecutor.execute(() -> {
            synchronized (fileLock) {
                if (logFile == null && appContext != null) {
                    setupLogFile(appContext);
                }
                if (logFile == null) return;

                try {
                    // Rotate if too large
                    if (logFile.exists() && logFile.length() > MAX_FILE_SIZE_BYTES) {
                        File backup = new File(logFile.getParentFile(), "nnvc_debug.old.log");
                        if (backup.exists()) backup.delete();
                        logFile.renameTo(backup);
                    }

                    try (FileWriter fw = new FileWriter(logFile, true)) {
                        fw.write(formattedLine);
                        fw.write("\n");
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    public static List<String> getRecentLogs() {
        return new ArrayList<>(memoryLogs);
    }

    public static String getFormattedLogs() {
        if (memoryLogs.isEmpty()) {
            return "Chưa có log nào được ghi nhận.\nFile log: " + getLogFilePath();
        }
        StringBuilder sb = new StringBuilder();
        for (String l : memoryLogs) {
            sb.append(l).append("\n");
        }
        return sb.toString();
    }

    public static void clear() {
        memoryLogs.clear();
        fileExecutor.execute(() -> {
            synchronized (fileLock) {
                if (logFile != null && logFile.exists()) {
                    try {
                        logFile.delete();
                    } catch (Throwable ignored) {}
                }
            }
        });
        Handler h = getMainHandler();
        if (h != null) {
            h.post(() -> {
                for (LogUpdateListener l : listeners) {
                    try {
                        l.onLogsCleared();
                    } catch (Throwable ignored) {}
                }
            });
        }
    }
}
