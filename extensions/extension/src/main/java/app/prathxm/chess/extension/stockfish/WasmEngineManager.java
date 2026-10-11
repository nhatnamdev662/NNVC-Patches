package app.prathxm.chess.extension.stockfish;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * WasmEngineManager – Manages Stockfish 18 WASM and Komodo Dragon 3.3 WASM
 * inside a headless background WebView running dedicated Web Workers.
 *
 * 100% WebAssembly Engine Parity with NNVC Extension.
 * Zero Native Subprocess Fallbacks.
 */
public class WasmEngineManager {

    private static final String TAG = "WasmEngineManager";
    private static final String HOST = "https://nnvc-engine.local/";

    private static volatile WasmEngineManager instance;

    private final Context context;
    private final Handler mainHandler;
    private WebView webView;

    private final Object playReadyLock = new Object();
    private final Object classifyReadyLock = new Object();

    private volatile boolean isReady = false;
    private volatile boolean isPlayReady = false;
    private volatile boolean isClassifyReady = false;
    private volatile String currentPlayEngineKey = StockfishSettings.ENGINE_KOMODO;

    private final AtomicBoolean isDownloading = new AtomicBoolean(false);

    // UCI Line queues for synchronous readSearchOutput
    private final BlockingQueue<String> playUciOutputQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> classifyUciOutputQueue = new LinkedBlockingQueue<>();

    // Listeners for async/intermediate monitoring
    public interface UciLineListener {
        void onLine(String line);
    }
    private volatile UciLineListener playLineListener;
    private volatile UciLineListener classifyLineListener;

    // Track active position & evaluation epoch for classifyWorker
    private final java.util.concurrent.atomic.AtomicLong classifyEvalEpochCounter = new java.util.concurrent.atomic.AtomicLong(0);
    private volatile long currentClassifyEpoch = 0L;
    private volatile String currentClassifyFen = null;
    private volatile boolean currentClassifyWhiteToMove = true;

    private static final java.util.Set<String> STARTPOS_LEGAL_FIRST_MOVES = new java.util.HashSet<>(java.util.Arrays.asList(
        "a2a3", "a2a4", "b2b3", "b2b4", "c2c3", "c2c4", "d2d3", "d2d4",
        "e2e3", "e2e4", "f2f3", "f2f4", "g2g3", "g2g4", "h2h3", "h2h4",
        "b1a3", "b1c3", "g1f3", "g1h3"
    ));

    public static boolean isStandardStartposMoves(List<String> moves) {
        if (moves == null || moves.isEmpty()) return false;
        String first = moves.get(0);
        return first != null && STARTPOS_LEGAL_FIRST_MOVES.contains(first.trim().toLowerCase(java.util.Locale.US));
    }

    public static WasmEngineManager getInstance(Context context) {
        if (instance == null) {
            synchronized (WasmEngineManager.class) {
                if (instance == null) {
                    instance = new WasmEngineManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private WasmEngineManager(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.currentPlayEngineKey = StockfishSettings.getEngineChoice(context);

        // Pre-scan / extract engine files immediately from APK & assets
        extractBundledWasmFiles();

        mainHandler.post(this::initWebView);
    }

    public boolean isReady() {
        return isReady && isPlayReady;
    }

    public boolean isClassifyReady() {
        return isClassifyReady;
    }

    public boolean hasWasmFiles(String engineKey) {
        if (StockfishSettings.ENGINE_KOMODO.equals(engineKey)) {
            File wasm = getKomodoWasm();
            File js = getKomodoJs();
            File book = getKomodoBook();
            return wasm != null && wasm.exists() && wasm.length() > 1_000_000 &&
                   js != null && js.exists() && js.length() > 50_000 &&
                   book != null && book.exists() && book.length() > 500_000;
        } else if (StockfishSettings.ENGINE_STOCKFISH18.equals(engineKey)) {
            File wasm = getStockfishWasm();
            File js = getStockfishJs();
            return wasm != null && wasm.exists() && wasm.length() > 1_000_000 &&
                   js != null && js.exists() && js.length() > 10_000;
        }
        return false;
    }

    public boolean waitForPlayWorkerReady(long timeoutMs) {
        if (!hasWasmFiles(currentPlayEngineKey)) {
            NnvcLogger.w(TAG, "Missing WASM files for " + currentPlayEngineKey + "; triggering download.");
            ensureEngineFilesAvailable(currentPlayEngineKey, null);
            return false;
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (playReadyLock) {
            while (!isPlayReady && System.currentTimeMillis() < deadline) {
                try {
                    long wait = deadline - System.currentTimeMillis();
                    if (wait > 0) playReadyLock.wait(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return isPlayReady;
        }
    }

    public boolean waitForClassifyWorkerReady(long timeoutMs) {
        if (!hasWasmFiles(StockfishSettings.ENGINE_STOCKFISH18)) {
            return false;
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (classifyReadyLock) {
            while (!isClassifyReady && System.currentTimeMillis() < deadline) {
                try {
                    long wait = deadline - System.currentTimeMillis();
                    if (wait > 0) classifyReadyLock.wait(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return isClassifyReady;
        }
    }

    // ── Asset Resolution & Extraction ──────────────────────────────────────────

    private File getWasmDir() {
        File dir = new File(context.getFilesDir(), "wasm_engines");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static long getMinExpectedSize(String fileName) {
        if (fileName.endsWith("dragon3.3.wasm")) return 1_000_000L;
        if (fileName.endsWith("komodo.js")) return 50_000L;
        if (fileName.endsWith("book.bin")) return 500_000L;
        if (fileName.endsWith("stockfish-18-lite-single.wasm")) return 1_000_000L;
        if (fileName.endsWith("stockfish-18-lite-single.js")) return 10_000L;
        if (fileName.endsWith("torch.wasm")) return 10_000_000L;
        if (fileName.endsWith("torch.js")) return 50_000L;
        return 1L;
    }

    public static boolean isValidEngineFile(String fileName, File file) {
        return file != null && file.exists() && file.length() >= getMinExpectedSize(fileName);
    }

    /**
     * Extracts bundled WASM engine files from:
     * 1. Direct APK Zip (context.getPackageResourcePath(), split APKs)
     * 2. Direct App Assets (context.getAssets())
     * 3. External MPP bundle or files in storage
     */
    public void extractBundledWasmFiles() {
        try {
            // First: scan the APK zip for any matching files
            extractFromApkZipIfAvailable();

            // Second: extract via getAssetOrExtract for standard assets paths
            getKomodoWasm();
            getKomodoJs();
            getKomodoBook();
            getStockfishWasm();
            getStockfishJs();

            // Third: scan storage for mpp bundles
            extractEnginesFromMppIfAvailable();
        } catch (Throwable t) {
            NnvcLogger.w(TAG, "extractBundledWasmFiles warning: " + t.getMessage());
        }
    }

    public void extractFromApkZipIfAvailable() {
        try {
            List<String> apkPaths = new java.util.ArrayList<>();
            if (context.getPackageResourcePath() != null) apkPaths.add(context.getPackageResourcePath());
            if (context.getPackageCodePath() != null && !apkPaths.contains(context.getPackageCodePath())) {
                apkPaths.add(context.getPackageCodePath());
            }
            try {
                android.content.pm.ApplicationInfo appInfo = context.getApplicationInfo();
                if (appInfo != null) {
                    if (appInfo.sourceDir != null && !apkPaths.contains(appInfo.sourceDir)) apkPaths.add(appInfo.sourceDir);
                    if (appInfo.publicSourceDir != null && !apkPaths.contains(appInfo.publicSourceDir)) apkPaths.add(appInfo.publicSourceDir);
                    if (appInfo.splitSourceDirs != null) {
                        for (String split : appInfo.splitSourceDirs) {
                            if (split != null && !apkPaths.contains(split)) apkPaths.add(split);
                        }
                    }
                    if (appInfo.splitPublicSourceDirs != null) {
                        for (String split : appInfo.splitPublicSourceDirs) {
                            if (split != null && !apkPaths.contains(split)) apkPaths.add(split);
                        }
                    }
                }
            } catch (Throwable ignored) {}

            File torchDir = new File(context.getFilesDir(), "torch");
            if (!torchDir.exists()) torchDir.mkdirs();

            for (String apkPath : apkPaths) {
                File apkFile = new File(apkPath);
                if (!apkFile.exists() || !apkFile.canRead()) continue;

                try (ZipFile zip = new ZipFile(apkFile)) {
                    java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry ze = entries.nextElement();
                        String name = ze.getName();
                        if (name == null) continue;

                        String targetFileName = null;
                        File targetDir = getWasmDir();

                        if (name.endsWith("dragon3.3.wasm")) targetFileName = "dragon3.3.wasm";
                        else if (name.endsWith("komodo.js")) targetFileName = "komodo.js";
                        else if (name.endsWith("book.bin")) targetFileName = "book.bin";
                        else if (name.endsWith("stockfish-18-lite-single.wasm")) targetFileName = "stockfish-18-lite-single.wasm";
                        else if (name.endsWith("stockfish-18-lite-single.js")) targetFileName = "stockfish-18-lite-single.js";
                        else if (name.endsWith("torch.wasm")) { targetFileName = "torch.wasm"; targetDir = torchDir; }
                        else if (name.endsWith("torch.js")) { targetFileName = "torch.js"; targetDir = torchDir; }

                        if (targetFileName != null) {
                            File dest = new File(targetDir, targetFileName);
                            if (!isValidEngineFile(targetFileName, dest)) {
                                try (InputStream is = zip.getInputStream(ze)) {
                                    copyStream(is, dest);
                                    NnvcLogger.i(TAG, "✓ Extracted from APK [" + name + "] -> " + targetFileName + " (" + dest.length() + " bytes)");
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    NnvcLogger.d(TAG, "Error inspecting APK " + apkPath + ": " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            NnvcLogger.w(TAG, "extractFromApkZip error: " + t.getMessage());
        }
    }

    /**
     * Scans /sdcard/Download/ and external files for any patches-*.mpp bundle
     * and extracts bundled wasm/js/book assets directly.
     */
    public void extractEnginesFromMppIfAvailable() {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File[] searchDirs = {
                    downloadsDir,
                    context.getExternalFilesDir(null),
                    new File("/sdcard/Download"),
                    new File("/sdcard/Android/data/com.chess/files")
            };

            for (File dir : searchDirs) {
                if (dir == null || !dir.exists() || !dir.isDirectory()) continue;
                File[] files = dir.listFiles((d, name) -> name != null && name.endsWith(".mpp"));
                if (files == null) continue;

                for (File mppFile : files) {
                    if (mppFile.length() < 10_000_000) continue; // Must be full bundle
                    try (ZipFile zip = new ZipFile(mppFile)) {
                        extractEntryIfMissing(zip, "stockfish18/stockfish-18-lite-single.wasm", "stockfish-18-lite-single.wasm");
                        extractEntryIfMissing(zip, "stockfish18/stockfish-18-lite-single.js", "stockfish-18-lite-single.js");
                        extractEntryIfMissing(zip, "komodo/dragon3.3.wasm", "dragon3.3.wasm");
                        extractEntryIfMissing(zip, "komodo/komodo.js", "komodo.js");
                        extractEntryIfMissing(zip, "komodo/book.bin", "book.bin");
                    } catch (Throwable t) {
                        NnvcLogger.d(TAG, "Error inspecting mpp bundle " + mppFile.getName() + ": " + t.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            NnvcLogger.w(TAG, "Failed scan for mpp bundle: " + t.getMessage());
        }
    }

    private void extractEntryIfMissing(ZipFile zip, String entryPath, String targetFileName) {
        File targetFile = new File(getWasmDir(), targetFileName);
        if (isValidEngineFile(targetFileName, targetFile)) return;

        String[] candidates = { entryPath, "assets/" + entryPath, targetFileName };
        for (String c : candidates) {
            ZipEntry ze = zip.getEntry(c);
            if (ze != null) {
                try (InputStream is = zip.getInputStream(ze)) {
                    copyStream(is, targetFile);
                    if (isValidEngineFile(targetFileName, targetFile)) {
                        NnvcLogger.i(TAG, "✓ Extracted " + targetFileName + " (" + targetFile.length() + " bytes) from " + zip.getName());
                        return;
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    public File getAssetOrExtract(String assetSubpath, String targetFileName) {
        File targetFile = new File(getWasmDir(), targetFileName);
        if (isValidEngineFile(targetFileName, targetFile)) {
            return targetFile;
        }

        // 1. Try extracting from app Assets
        String[] candidatePaths = {
                assetSubpath,
                "assets/" + assetSubpath,
                targetFileName
        };
        for (String p : candidatePaths) {
            try (InputStream is = context.getAssets().open(p)) {
                copyStream(is, targetFile);
                if (isValidEngineFile(targetFileName, targetFile)) {
                    NnvcLogger.i(TAG, "Extracted from assets: " + p + " -> " + targetFile.getAbsolutePath());
                    return targetFile;
                }
            } catch (Throwable ignored) {}
        }

        // 2. Try extracting from APK Zip (context.getPackageResourcePath(), split APKs)
        try {
            List<String> apkPaths = new java.util.ArrayList<>();
            if (context.getPackageResourcePath() != null) apkPaths.add(context.getPackageResourcePath());
            if (context.getPackageCodePath() != null && !apkPaths.contains(context.getPackageCodePath())) {
                apkPaths.add(context.getPackageCodePath());
            }
            try {
                android.content.pm.ApplicationInfo appInfo = context.getApplicationInfo();
                if (appInfo != null) {
                    if (appInfo.sourceDir != null && !apkPaths.contains(appInfo.sourceDir)) apkPaths.add(appInfo.sourceDir);
                    if (appInfo.publicSourceDir != null && !apkPaths.contains(appInfo.publicSourceDir)) apkPaths.add(appInfo.publicSourceDir);
                    if (appInfo.splitSourceDirs != null) {
                        for (String split : appInfo.splitSourceDirs) {
                            if (split != null && !apkPaths.contains(split)) apkPaths.add(split);
                        }
                    }
                }
            } catch (Throwable ignored) {}

            for (String apkPath : apkPaths) {
                File apkFile = new File(apkPath);
                if (!apkFile.exists() || !apkFile.canRead()) continue;
                try (ZipFile zip = new ZipFile(apkFile)) {
                    for (String p : candidatePaths) {
                        ZipEntry entry = zip.getEntry(p);
                        if (entry != null) {
                            try (InputStream is = zip.getInputStream(entry)) {
                                copyStream(is, targetFile);
                                if (isValidEngineFile(targetFileName, targetFile)) {
                                    NnvcLogger.i(TAG, "Extracted from APK zip: " + p + " -> " + targetFile.getAbsolutePath());
                                    return targetFile;
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // 3. Try checking Downloads folder
        try {
            File dl = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), targetFileName);
            if (isValidEngineFile(targetFileName, dl)) {
                copyFile(dl, targetFile);
                if (isValidEngineFile(targetFileName, targetFile)) {
                    NnvcLogger.i(TAG, "Copied from Downloads: " + dl.getAbsolutePath() + " -> " + targetFile.getAbsolutePath());
                    return targetFile;
                }
            }
        } catch (Throwable ignored) {}

        // 4. Try checking app external files dir
        try {
            File ext = new File(context.getExternalFilesDir(null), targetFileName);
            if (isValidEngineFile(targetFileName, ext)) {
                copyFile(ext, targetFile);
                if (isValidEngineFile(targetFileName, targetFile)) {
                    NnvcLogger.i(TAG, "Copied from external files: " + ext.getAbsolutePath() + " -> " + targetFile.getAbsolutePath());
                    return targetFile;
                }
            }
        } catch (Throwable ignored) {}

        return isValidEngineFile(targetFileName, targetFile) ? targetFile : null;
    }

    public File getStockfishWasm() {
        return getAssetOrExtract("stockfish18/stockfish-18-lite-single.wasm", "stockfish-18-lite-single.wasm");
    }

    public File getStockfishJs() {
        return getAssetOrExtract("stockfish18/stockfish-18-lite-single.js", "stockfish-18-lite-single.js");
    }

    public File getKomodoWasm() {
        return getAssetOrExtract("komodo/dragon3.3.wasm", "dragon3.3.wasm");
    }

    public File getKomodoJs() {
        return getAssetOrExtract("komodo/komodo.js", "komodo.js");
    }

    public File getKomodoBook() {
        return getAssetOrExtract("komodo/book.bin", "book.bin");
    }

    public void ensureEngineFilesAvailable(String engineKey, Runnable onReady) {
        // Step 1: Scan and extract bundled files immediately
        extractBundledWasmFiles();

        if (hasWasmFiles(engineKey)) {
            NnvcLogger.i(TAG, "✓ Tất cả tệp WASM cho " + engineKey + " đã sẵn sàng.");
            mainHandler.post(() -> {
                initWebView();
                if (onReady != null) onReady.run();
            });
            return;
        }

        NnvcLogger.w(TAG, "WASM files for " + engineKey + " not present after extraction scan.");
        EngineStatusHUD.show(context, "⚠️ Không tìm thấy tệp động cơ " + engineKey.toUpperCase() + " trong APK", true, 6000);
    }

    private static void copyFile(File src, File dst) {
        try (FileInputStream in = new FileInputStream(src)) {
            copyStream(in, dst);
        } catch (Throwable ignored) {}
    }

    private static void copyStream(InputStream in, File dst) throws Exception {
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        File tmp = new File(parent, dst.getName() + ".part");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            copyStream(in, out);
        } finally {
            in.close();
        }
        if (tmp.exists() && tmp.length() > 0) {
            if (dst.exists()) dst.delete();
            tmp.renameTo(dst);
        }
    }

    private static void copyStream(InputStream in, FileOutputStream out) throws Exception {
        byte[] buf = new byte[65536];
        int read;
        while ((read = in.read(buf)) != -1) {
            out.write(buf, 0, read);
        }
    }

    private String readStreamToString(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            char[] buf = new char[8192];
            int read;
            while ((read = reader.read(buf)) != -1) {
                sb.append(buf, 0, read);
            }
        }
        return sb.toString();
    }

    // ── Headless WebView Initialization ───────────────────────────────────────

    private WebResourceResponse handleIntercept(String url) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "*");

        if (url.contains("stockfish-18-lite-single.wasm")) {
            try {
                File f = getStockfishWasm();
                if (f != null && f.exists()) {
                    return new WebResourceResponse("application/wasm", null, 200, "OK", headers, new FileInputStream(f));
                }
            } catch (Throwable t) {
                NnvcLogger.e(TAG, "Error streaming stockfish wasm: " + t.getMessage());
            }
        } else if (url.contains("stockfish-18-lite-single.js")) {
            try {
                File f = getStockfishJs();
                if (f != null && f.exists()) {
                    String source = readStreamToString(new FileInputStream(f));
                    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
                    return new WebResourceResponse("application/javascript", "utf-8", 200, "OK", headers, new ByteArrayInputStream(bytes));
                }
            } catch (Throwable t) {
                NnvcLogger.e(TAG, "Error streaming stockfish js: " + t.getMessage());
            }
        } else if (url.contains("dragon3.3.wasm")) {
            try {
                File f = getKomodoWasm();
                if (f != null && f.exists()) {
                    return new WebResourceResponse("application/wasm", null, 200, "OK", headers, new FileInputStream(f));
                }
            } catch (Throwable t) {
                NnvcLogger.e(TAG, "Error streaming komodo wasm: " + t.getMessage());
            }
        } else if (url.contains("komodo.js")) {
            try {
                File f = getKomodoJs();
                if (f != null && f.exists()) {
                    String source = readStreamToString(new FileInputStream(f));
                    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
                    return new WebResourceResponse("application/javascript", "utf-8", 200, "OK", headers, new ByteArrayInputStream(bytes));
                }
            } catch (Throwable t) {
                NnvcLogger.e(TAG, "Error streaming komodo js: " + t.getMessage());
            }
        } else if (url.contains("book.bin")) {
            try {
                File f = getKomodoBook();
                if (f != null && f.exists()) {
                    return new WebResourceResponse("application/octet-stream", null, 200, "OK", headers, new FileInputStream(f));
                }
            } catch (Throwable t) {
                NnvcLogger.e(TAG, "Error streaming komodo book: " + t.getMessage());
            }
        }
        return null;
    }

    private void initWebView() {
        try {
            File sfWasm = getStockfishWasm();
            File kmWasm = getKomodoWasm();

            if (sfWasm == null && kmWasm == null) {
                NnvcLogger.w(TAG, "No WASM binaries present yet on disk. Scanning and extracting bundled files...");
                ensureEngineFilesAvailable(currentPlayEngineKey, null);
                return;
            }

            NnvcLogger.i(TAG, "Starting headless WebView with in-memory WebAssembly workers... (SF=" + (sfWasm != null) + ", KM=" + (kmWasm != null) + ")");

            webView = new WebView(context);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(true);

            webView.setWebChromeClient(new android.webkit.WebChromeClient() {
                @Override
                public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
                    NnvcLogger.d(TAG, "[WASM Console " + cm.messageLevel() + "] " + cm.message() + " (" + cm.sourceId() + ":" + cm.lineNumber() + ")");
                    return true;
                }
            });

            webView.addJavascriptInterface(new WasmEngineJsBridge(), "WasmEngineBridge");

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                        Map<String, String> headers = new HashMap<>();
                        headers.put("Access-Control-Allow-Origin", "*");
                        headers.put("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
                        headers.put("Access-Control-Allow-Headers", "*");
                        return new WebResourceResponse("text/plain", "utf-8", 200, "OK", headers, new ByteArrayInputStream(new byte[0]));
                    }
                    WebResourceResponse resp = handleIntercept(request.getUrl().toString());
                    if (resp != null) return resp;
                    return super.shouldInterceptRequest(view, request);
                }
            });

            // ServiceWorker interceptor for API 24+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    ServiceWorkerController swc = ServiceWorkerController.getInstance();
                    swc.setServiceWorkerClient(new ServiceWorkerClient() {
                        @Override
                        public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                            WebResourceResponse resp = handleIntercept(request.getUrl().toString());
                            if (resp != null) return resp;
                            return super.shouldInterceptRequest(request);
                        }
                    });
                } catch (Throwable ignored) {}
            }

            String html = buildBootstrapHtml();
            webView.loadDataWithBaseURL(HOST, html, "text/html", "UTF-8", null);

        } catch (Throwable t) {
            NnvcLogger.e(TAG, "Failed to initialize WasmEngineManager: " + t.getMessage(), t);
        }
    }

    private String buildBootstrapHtml() {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'></head><body><script>\n" +
                "(function() {\n" +
                "    window.AudioContext = undefined; window.webkitAudioContext = undefined; window.Audio = undefined;\n" +
                "    console.log('[WasmManager] HTML Bootstrap starting...');\n" +
                "    window._playWorker = null;\n" +
                "    window._classifyWorker = null;\n" +
                "    window._playWorkerReady = false;\n" +
                "    window._classifyWorkerReady = false;\n" +
                "    window._playPendingCmds = [];\n" +
                "    window._classifyPendingCmds = [];\n" +
                "    window._playEngineKey = '" + currentPlayEngineKey + "';\n" +
                "\n" +
                "    function createWorkerPreamble(engineKey, jsCode, kind) {\n" +
                "        if (engineKey === 'komodo') {\n" +
                "            return `\n" +
                "var _injectedWasm = null;\n" +
                "var _injectedBook = null;\n" +
                "var _engineKey = 'komodo';\n" +
                "var _origFetch = self.fetch;\n" +
                "\n" +
                "self.importScripts = self.importScripts || function(){};\n" +
                "\n" +
                "self.fetch = function(url) {\n" +
                "    var u = String(url || '').toLowerCase();\n" +
                "    if (u.indexOf('.wasm') !== -1 || u.indexOf('dragon') !== -1 || u.indexOf('komodo_wasm') !== -1) {\n" +
                "        if (_injectedWasm) {\n" +
                "            return Promise.resolve(new Response(_injectedWasm.slice(0), {\n" +
                "                headers: { 'Content-Type': 'application/wasm' }\n" +
                "            }));\n" +
                "        }\n" +
                "    }\n" +
                "    if (u.indexOf('book') !== -1 || u.indexOf('komodo_book') !== -1) {\n" +
                "        if (_injectedBook) {\n" +
                "            return Promise.resolve(new Response(_injectedBook.slice(0), {\n" +
                "                headers: { 'Content-Type': 'application/octet-stream' }\n" +
                "            }));\n" +
                "        }\n" +
                "    }\n" +
                "    if (_origFetch) return _origFetch.apply(this, arguments);\n" +
                "    return Promise.reject(new Error('Unknown url: ' + url));\n" +
                "};\n" +
                "\n" +
                "var _NativeXHR = self.XMLHttpRequest;\n" +
                "function WasmAwareXHR() {\n" +
                "    this.readyState = 0; this.response = null; this.responseText = ''; this.responseType = '';\n" +
                "    this.status = 0; this.statusText = ''; this._async = true; this._headers = {};\n" +
                "    this._url = '';\n" +
                "    this._native = _NativeXHR ? new _NativeXHR() : null;\n" +
                "}\n" +
                "WasmAwareXHR.prototype.open = function(method, url, async) {\n" +
                "    this._url = String(url || ''); this._async = async !== false;\n" +
                "    var u = this._url.toLowerCase();\n" +
                "    if (u.indexOf('.wasm') !== -1 || u.indexOf('dragon') !== -1 || u.indexOf('book') !== -1 || u.indexOf('komodo_') !== -1) {\n" +
                "        this.readyState = 1; return;\n" +
                "    }\n" +
                "    if (this._native) this._native.open(method, url, async);\n" +
                "};\n" +
                "WasmAwareXHR.prototype.send = function(body) {\n" +
                "    var u = this._url.toLowerCase();\n" +
                "    var isWasm = u.indexOf('.wasm') !== -1 || u.indexOf('dragon') !== -1 || u.indexOf('komodo_wasm') !== -1;\n" +
                "    var isBook = u.indexOf('book') !== -1 || u.indexOf('komodo_book') !== -1;\n" +
                "    if (!isWasm && !isBook && this._native) return this._native.send(body);\n" +
                "    var targetBuf = isWasm ? _injectedWasm : _injectedBook;\n" +
                "    this.status = 200; this.statusText = 'OK'; this.readyState = 4;\n" +
                "    if (targetBuf) {\n" +
                "        if (this.responseType === 'arraybuffer' || !this.responseType) {\n" +
                "            this.response = targetBuf.slice(0);\n" +
                "        } else if (this.responseType === 'blob' && typeof Blob === 'function') {\n" +
                "            this.response = new Blob([targetBuf], { type: isWasm ? 'application/wasm' : 'application/octet-stream' });\n" +
                "        } else {\n" +
                "            var bytes = new Uint8Array(targetBuf);\n" +
                "            var res = '';\n" +
                "            for (var i = 0; i < bytes.length; i += 32768) {\n" +
                "                res += String.fromCharCode.apply(null, bytes.subarray(i, i + 32768));\n" +
                "            }\n" +
                "            this.responseText = res;\n" +
                "            this.response = res;\n" +
                "        }\n" +
                "    }\n" +
                "    if (typeof this.onreadystatechange === 'function') this.onreadystatechange();\n" +
                "    if (typeof this.onload === 'function') this.onload();\n" +
                "};\n" +
                "WasmAwareXHR.prototype.setRequestHeader = function(h, v) { if (this._native) this._native.setRequestHeader(h, v); };\n" +
                "WasmAwareXHR.prototype.getResponseHeader = function(n) { return null; };\n" +
                "WasmAwareXHR.prototype.getAllResponseHeaders = function() { return ''; };\n" +
                "self.XMLHttpRequest = WasmAwareXHR;\n" +
                "\n" +
                "var _queuedCmds = [];\n" +
                "var _engineReady = false;\n" +
                "var _engineProcess = null;\n" +
                "\n" +
                "self.onmessage = function(e) {\n" +
                "    if (e.data && e.data.__init__) {\n" +
                "        _injectedWasm = e.data.wasm;\n" +
                "        _injectedBook = e.data.book || null;\n" +
                "        self.location = {\n" +
                "            origin: 'https://nnvc-engine.local',\n" +
                "            pathname: '/komodo.js',\n" +
                "            hash: '#https://nnvc-engine.local/dragon3.3.wasm',\n" +
                "            href: 'https://nnvc-engine.local/komodo.js#https://nnvc-engine.local/dragon3.3.wasm'\n" +
                "        };\n" +
                "        self.onmessage = null;\n" +
                "        try {\n" +
                "            ${jsCode}\n" +
                "            _engineProcess = self.onmessage;\n" +
                "            _engineReady = true;\n" +
                "            self.onmessage = function(ev) {\n" +
                "                if (_engineProcess) _engineProcess(ev);\n" +
                "            };\n" +
                "            while (_queuedCmds.length > 0) {\n" +
                "                var cmd = _queuedCmds.shift();\n" +
                "                if (_engineProcess) _engineProcess({ data: cmd });\n" +
                "            }\n" +
                "            postMessage('__WORKER_READY__');\n" +
                "        } catch(err) {\n" +
                "            postMessage('__ENGINE_ERROR__:' + err);\n" +
                "        }\n" +
                "    } else {\n" +
                "        if (!_engineReady) {\n" +
                "            _queuedCmds.push(e.data);\n" +
                "        } else if (_engineProcess) {\n" +
                "            _engineProcess(e);\n" +
                "        }\n" +
                "    }\n" +
                "};\n" +
                "`;\n" +
                "        } else {\n" +
                "            return `\n" +
                "var _injectedWasm = null;\n" +
                "var _engineKey = 'stockfish18';\n" +
                "var _workerKind = '${kind || \"play\"}';\n" +
                "var _origFetch = self.fetch;\n" +
                "\n" +
                "self.importScripts = self.importScripts || function(){};\n" +
                "\n" +
                "self.fetch = function(url) {\n" +
                "    var u = String(url || '').toLowerCase();\n" +
                "    if (u.indexOf('.wasm') !== -1 || u.indexOf('stockfish') !== -1) {\n" +
                "        if (_injectedWasm) {\n" +
                "            return Promise.resolve(new Response(_injectedWasm.slice(0), {\n" +
                "                headers: { 'Content-Type': 'application/wasm' }\n" +
                "            }));\n" +
                "        }\n" +
                "    }\n" +
                "    if (_origFetch) return _origFetch.apply(this, arguments);\n" +
                "    return Promise.reject(new Error('Unknown url: ' + url));\n" +
                "};\n" +
                "\n" +
                "var _NativeXHR = self.XMLHttpRequest;\n" +
                "function WasmAwareXHR() {\n" +
                "    this.readyState = 0; this.response = null; this.responseText = ''; this.responseType = '';\n" +
                "    this.status = 0; this.statusText = ''; this._async = true; this._headers = {};\n" +
                "    this._url = '';\n" +
                "    this._native = _NativeXHR ? new _NativeXHR() : null;\n" +
                "}\n" +
                "WasmAwareXHR.prototype.open = function(method, url, async) {\n" +
                "    this._url = String(url || ''); this._async = async !== false;\n" +
                "    var u = this._url.toLowerCase();\n" +
                "    if (u.indexOf('.wasm') !== -1 || u.indexOf('stockfish') !== -1) {\n" +
                "        this.readyState = 1; return;\n" +
                "    }\n" +
                "    if (this._native) this._native.open(method, url, async);\n" +
                "};\n" +
                "WasmAwareXHR.prototype.send = function(body) {\n" +
                "    var u = this._url.toLowerCase();\n" +
                "    var isWasm = u.indexOf('.wasm') !== -1 || u.indexOf('stockfish') !== -1;\n" +
                "    if (!isWasm && this._native) return this._native.send(body);\n" +
                "    this.status = 200; this.statusText = 'OK'; this.readyState = 4;\n" +
                "    if (_injectedWasm) {\n" +
                "        if (this.responseType === 'arraybuffer' || !this.responseType) {\n" +
                "            this.response = _injectedWasm.slice(0);\n" +
                "        } else if (this.responseType === 'blob' && typeof Blob === 'function') {\n" +
                "            this.response = new Blob([_injectedWasm], { type: 'application/wasm' });\n" +
                "        } else {\n" +
                "            var bytes = new Uint8Array(_injectedWasm);\n" +
                "            var res = '';\n" +
                "            for (var i = 0; i < bytes.length; i += 32768) {\n" +
                "                res += String.fromCharCode.apply(null, bytes.subarray(i, i + 32768));\n" +
                "            }\n" +
                "            this.responseText = res;\n" +
                "            this.response = res;\n" +
                "        }\n" +
                "    }\n" +
                "    if (typeof this.onreadystatechange === 'function') this.onreadystatechange();\n" +
                "    if (typeof this.onload === 'function') this.onload();\n" +
                "};\n" +
                "WasmAwareXHR.prototype.setRequestHeader = function(h, v) { if (this._native) this._native.setRequestHeader(h, v); };\n" +
                "WasmAwareXHR.prototype.getResponseHeader = function(n) { return null; };\n" +
                "WasmAwareXHR.prototype.getAllResponseHeaders = function() { return ''; };\n" +
                "self.XMLHttpRequest = WasmAwareXHR;\n" +
                "\n" +
                "var _queuedCmds = [];\n" +
                "var _engineReady = false;\n" +
                "var _engineProcess = null;\n" +
                "var _eng = null;\n" +
                "var _cmdQueue = [];\n" +
                "var _isSearching = false;\n" +
                "var _stopRequested = false;\n" +
                "var _isRequestedStop = false;\n" +
                "var _searchWatchdog = null;\n" +
                "var _currentEvalEpoch = 0;\n" +
                "var _currentWhiteToMove = true;\n" +
                "\n" +
                "function clearWatchdog() {\n" +
                "    if (_searchWatchdog) {\n" +
                "        clearTimeout(_searchWatchdog);\n" +
                "        _searchWatchdog = null;\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "function onDoneSearching() {\n" +
                "    _isSearching = false;\n" +
                "    _stopRequested = false;\n" +
                "    _isRequestedStop = false;\n" +
                "    _currentEvalEpoch = 0;\n" +
                "    clearWatchdog();\n" +
                "    setTimeout(drainQueue, 0);\n" +
                "}\n" +
                "\n" +
                "function drainQueue() {\n" +
                "    if (!_eng || _isSearching || _isRequestedStop) return;\n" +
                "    while (_cmdQueue.length > 0) {\n" +
                "        var nextCmd = _cmdQueue.shift();\n" +
                "        if (!nextCmd) continue;\n" +
                "        if (typeof nextCmd === 'object' && nextCmd.type === 'evaluate') {\n" +
                "            _currentEvalEpoch = nextCmd.epoch;\n" +
                "            _currentWhiteToMove = nextCmd.whiteToMove;\n" +
                "            _isSearching = true;\n" +
                "            _stopRequested = false;\n" +
                "            _isRequestedStop = false;\n" +
                "            clearWatchdog();\n" +
                "            _searchWatchdog = setTimeout(function() {\n" +
                "                if (_isSearching) onDoneSearching();\n" +
                "            }, 5000);\n" +
                "            try {\n" +
                "                _eng.ccall('command', null, ['string'], [nextCmd.posCmd]);\n" +
                "                _eng.ccall('command', null, ['string'], [nextCmd.goCmd || 'go depth 10']);\n" +
                "            } catch (err) {\n" +
                "                _isSearching = false;\n" +
                "                postMessage('__ENGINE_ERROR__:' + err);\n" +
                "            }\n" +
                "            break;\n" +
                "        }\n" +
                "        var trimmed = (typeof nextCmd === 'string' ? nextCmd : String(nextCmd || '')).trim();\n" +
                "        if (!trimmed) continue;\n" +
                "        if (trimmed === 'stop') continue;\n" +
                "        if (trimmed.indexOf('go') === 0) {\n" +
                "            _currentEvalEpoch = 0;\n" +
                "            _isSearching = true;\n" +
                "            _stopRequested = false;\n" +
                "            _isRequestedStop = false;\n" +
                "            clearWatchdog();\n" +
                "            _searchWatchdog = setTimeout(function() {\n" +
                "                if (_isSearching) onDoneSearching();\n" +
                "            }, 5000);\n" +
                "            try {\n" +
                "                _eng.ccall('command', null, ['string'], [trimmed]);\n" +
                "            } catch (err) {\n" +
                "                _isSearching = false;\n" +
                "                postMessage('__ENGINE_ERROR__:' + err);\n" +
                "            }\n" +
                "            break;\n" +
                "        } else {\n" +
                "            try {\n" +
                "                _eng.ccall('command', null, ['string'], [trimmed]);\n" +
                "            } catch (err) {\n" +
                "                postMessage('__ENGINE_ERROR__:' + err);\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "self.onmessage = function(e) {\n" +
                "    if (e.data && e.data.__init__) {\n" +
                "        _injectedWasm = e.data.wasm;\n" +
                "        self.location = {\n" +
                "            origin: 'https://nnvc-engine.local',\n" +
                "            pathname: '/stockfish-18-lite-single.js',\n" +
                "            hash: '#https://nnvc-engine.local/stockfish-18-lite-single.wasm',\n" +
                "            href: 'https://nnvc-engine.local/stockfish-18-lite-single.js#https://nnvc-engine.local/stockfish-18-lite-single.wasm'\n" +
                "        };\n" +
                "        try {\n" +
                "            var module = { exports: {} };\n" +
                "            var exports = module.exports;\n" +
                "            var global = undefined;\n" +
                "            var process = undefined;\n" +
                "\n" +
                "            (function(self, module, exports, onmessage) {\n" +
                "                ${jsCode}\n" +
                "            })(self, module, exports, undefined);\n" +
                "\n" +
                "            var _exported = module.exports;\n" +
                "            var _sfInit = (typeof _exported === 'function' && _exported.name === 't') ? _exported() : _exported;\n" +
                "            var _sfInstance = _sfInit({\n" +
                "                wasmBinary: _injectedWasm,\n" +
                "                listener: function(line) {\n" +
                "                    var str = (typeof line === 'string') ? line : String(line || '');\n" +
                "                    if (_isRequestedStop) {\n" +
                "                        if (str.indexOf('bestmove') === 0) {\n" +
                "                            _isRequestedStop = false;\n" +
                "                            onDoneSearching();\n" +
                "                        }\n" +
                "                        return;\n" +
                "                    }\n" +
                "                    if (_currentEvalEpoch > 0) {\n" +
                "                        postMessage({\n" +
                "                            type: 'eval_line',\n" +
                "                            epoch: _currentEvalEpoch,\n" +
                "                            whiteToMove: _currentWhiteToMove,\n" +
                "                            line: str\n" +
                "                        });\n" +
                "                    } else {\n" +
                "                        postMessage(str);\n" +
                "                    }\n" +
                "                    if (str.indexOf('bestmove') === 0) {\n" +
                "                        onDoneSearching();\n" +
                "                    }\n" +
                "                }\n" +
                "            });\n" +
                "            var _promise = (_sfInstance && typeof _sfInstance.then === 'function') ? _sfInstance : (_sfInstance && _sfInstance.ready ? _sfInstance.ready : null);\n" +
                "            if (!_promise) throw new Error('Stockfish 18 init failed to return Promise');\n" +
                "\n" +
                "            _promise.then(function(eng) {\n" +
                "                _eng = eng;\n" +
                "                _engineProcess = function(ev) {\n" +
                "                    var raw = (typeof ev === 'object' && ev && ev.data !== undefined) ? ev.data : ev;\n" +
                "                    if (!raw) return;\n" +
                "\n" +
                "                    if (typeof raw === 'object' && raw.type === 'evaluate') {\n" +
                "                        if (_isSearching) {\n" +
                "                            _isRequestedStop = true;\n" +
                "                            _stopRequested = true;\n" +
                "                            var filtered = [];\n" +
                "                            for (var i = 0; i < _cmdQueue.length; i++) {\n" +
                "                                var item = _cmdQueue[i];\n" +
                "                                if (typeof item === 'object' && item && item.type === 'evaluate') continue;\n" +
                "                                var c = String(item || '').trim();\n" +
                "                                if (c.indexOf('go') !== 0 && c.indexOf('position') !== 0) {\n" +
                "                                    filtered.push(item);\n" +
                "                                }\n" +
                "                            }\n" +
                "                            _cmdQueue = filtered;\n" +
                "                            _cmdQueue.push(raw);\n" +
                "                            try {\n" +
                "                                _eng.ccall('command', null, ['string'], ['stop']);\n" +
                "                            } catch (err) {\n" +
                "                                onDoneSearching();\n" +
                "                            }\n" +
                "                        } else {\n" +
                "                            _currentEvalEpoch = raw.epoch;\n" +
                "                            _currentWhiteToMove = raw.whiteToMove;\n" +
                "                            _isSearching = true;\n" +
                "                            _stopRequested = false;\n" +
                "                            _isRequestedStop = false;\n" +
                "                            clearWatchdog();\n" +
                "                            _searchWatchdog = setTimeout(function() {\n" +
                "                                if (_isSearching) onDoneSearching();\n" +
                "                            }, 5000);\n" +
                "                            try {\n" +
                "                                _eng.ccall('command', null, ['string'], [raw.posCmd]);\n" +
                "                                _eng.ccall('command', null, ['string'], [raw.goCmd || 'go depth 10']);\n" +
                "                            } catch (err) {\n" +
                "                                _isSearching = false;\n" +
                "                                postMessage('__ENGINE_ERROR__:' + err);\n" +
                "                            }\n" +
                "                        }\n" +
                "                        return;\n" +
                "                    }\n" +
                "\n" +
                "                    var cmd = String(raw || '').trim();\n" +
                "                    if (!cmd) return;\n" +
                "\n" +
                "                    if (cmd === 'stop') {\n" +
                "                        if (_workerKind === 'classify') {\n" +
                "                            _isRequestedStop = true;\n" +
                "                        }\n" +
                "                        if (_isSearching) {\n" +
                "                            if (!_stopRequested) {\n" +
                "                                _stopRequested = true;\n" +
                "                                try {\n" +
                "                                    _eng.ccall('command', null, ['string'], ['stop']);\n" +
                "                                } catch (err) {\n" +
                "                                    onDoneSearching();\n" +
                "                                }\n" +
                "                            }\n" +
                "                        }\n" +
                "                        var filtered = [];\n" +
                "                        for (var i = 0; i < _cmdQueue.length; i++) {\n" +
                "                            var item = _cmdQueue[i];\n" +
                "                            if (typeof item === 'object' && item && item.type === 'evaluate') continue;\n" +
                "                            var c = String(item || '').trim();\n" +
                "                            if (c.indexOf('go') !== 0 && c.indexOf('position') !== 0) {\n" +
                "                                filtered.push(item);\n" +
                "                            }\n" +
                "                        }\n" +
                "                        _cmdQueue = filtered;\n" +
                "                        return;\n" +
                "                    }\n" +
                "\n" +
                "                    if (_isSearching) {\n" +
                "                        _cmdQueue.push(cmd);\n" +
                "                    } else {\n" +
                "                        if (cmd.indexOf('go') === 0) {\n" +
                "                            _currentEvalEpoch = 0;\n" +
                "                            _isSearching = true;\n" +
                "                            _stopRequested = false;\n" +
                "                            _isRequestedStop = false;\n" +
                "                            clearWatchdog();\n" +
                "                            _searchWatchdog = setTimeout(function() {\n" +
                "                                if (_isSearching) onDoneSearching();\n" +
                "                            }, 5000);\n" +
                "                            try {\n" +
                "                                _eng.ccall('command', null, ['string'], [cmd]);\n" +
                "                            } catch (err) {\n" +
                "                                _isSearching = false;\n" +
                "                                postMessage('__ENGINE_ERROR__:' + err);\n" +
                "                            }\n" +
                "                        } else {\n" +
                "                            try {\n" +
                "                                _eng.ccall('command', null, ['string'], [cmd]);\n" +
                "                            } catch (err) {\n" +
                "                                postMessage('__ENGINE_ERROR__:' + err);\n" +
                "                            }\n" +
                "                        }\n" +
                "                    }\n" +
                "                };\n" +
                "                _engineReady = true;\n" +
                "                self.onmessage = function(ev) {\n" +
                "                    if (_engineProcess) _engineProcess(ev);\n" +
                "                };\n" +
                "                while (_queuedCmds.length > 0) {\n" +
                "                    var cmd = _queuedCmds.shift();\n" +
                "                    _engineProcess({ data: cmd });\n" +
                "                }\n" +
                "                postMessage('__WORKER_READY__');\n" +
                "            }).catch(function(err) {\n" +
                "                postMessage('__ENGINE_ERROR__:' + err);\n" +
                "            });\n" +
                "        } catch(err) {\n" +
                "            postMessage('__ENGINE_ERROR__:' + err);\n" +
                "        }\n" +
                "    } else {\n" +
                "        if (!_engineReady) {\n" +
                "            _queuedCmds.push(e.data);\n" +
                "        } else if (_engineProcess) {\n" +
                "            _engineProcess(e);\n" +
                "        }\n" +
                "    }\n" +
                "};\n" +
                "`;\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    function startWorker(kind, engineKey, jsCode, wasmBuf, bookBuf) {\n" +
                "        console.log('[WasmManager] Starting worker: kind=' + kind + ', engine=' + engineKey);\n" +
                "        const fullScript = createWorkerPreamble(engineKey, jsCode, kind);\n" +
                "        const blob = new Blob([fullScript], { type: 'application/javascript' });\n" +
                "        const blobUrl = URL.createObjectURL(blob);\n" +
                "        const worker = new Worker(blobUrl);\n" +
                "\n" +
                "        worker.onmessage = function(e) {\n" +
                "            var data = e.data;\n" +
                "            if (data && typeof data === 'object' && data.type === 'eval_line') {\n" +
                "                if (window.WasmEngineBridge && window.WasmEngineBridge.onClassifyEvalOutput) {\n" +
                "                    window.WasmEngineBridge.onClassifyEvalOutput(data.line, data.epoch, data.whiteToMove);\n" +
                "                }\n" +
                "                return;\n" +
                "            }\n" +
                "            const line = (typeof e.data === 'string') ? e.data : String(e.data || '');\n" +
                "            if (line === '__WORKER_READY__') {\n" +
                "                if (kind === 'play') window._playWorkerReady = true;\n" +
                "                else if (kind === 'classify') window._classifyWorkerReady = true;\n" +
                "                if (window.WasmEngineBridge) window.WasmEngineBridge.onEngineWorkerReady(kind, engineKey);\n" +
                "                return;\n" +
                "            }\n" +
                "            if (line.indexOf('__ENGINE_ERROR__:') === 0) {\n" +
                "                if (window.WasmEngineBridge) window.WasmEngineBridge.onEngineError(kind, line.substring(17));\n" +
                "                return;\n" +
                "            }\n" +
                "            if (line === 'uciok' || line === 'readyok') {\n" +
                "                if (kind === 'play') window._playWorkerReady = true;\n" +
                "                else if (kind === 'classify') window._classifyWorkerReady = true;\n" +
                "            }\n" +
                "            if (window.WasmEngineBridge) window.WasmEngineBridge.onEngineOutput(kind, line);\n" +
                "        };\n" +
                "        worker.onerror = function(err) {\n" +
                "            console.error('[Worker Error ' + kind + '] ' + (err.message || err));\n" +
                "            if (window.WasmEngineBridge) window.WasmEngineBridge.onEngineError(kind, String(err.message || err));\n" +
                "        };\n" +
                "\n" +
                "        // Initialize worker with transferred ArrayBuffers\n" +
                "        const transferList = [wasmBuf.slice(0)];\n" +
                "        const initPayload = { __init__: true, wasm: transferList[0] };\n" +
                "        if (bookBuf) {\n" +
                "            const bookCopy = bookBuf.slice(0);\n" +
                "            initPayload.book = bookCopy;\n" +
                "            transferList.push(bookCopy);\n" +
                "        }\n" +
                "        worker.postMessage(initPayload, transferList);\n" +
                "\n" +
                "        if (kind === 'play') {\n" +
                "            if (window._playWorker) { try { window._playWorker.terminate(); } catch(e){} }\n" +
                "            window._playWorker = worker;\n" +
                "            window._playEngineKey = engineKey;\n" +
                "            while (window._playPendingCmds.length > 0) {\n" +
                "                worker.postMessage(window._playPendingCmds.shift());\n" +
                "            }\n" +
                "        } else {\n" +
                "            if (window._classifyWorker) { try { window._classifyWorker.terminate(); } catch(e){} }\n" +
                "            window._classifyWorker = worker;\n" +
                "            worker.postMessage('uci');\n" +
                "            worker.postMessage('setoption name Hash value 64');\n" +
                "            worker.postMessage('setoption name Threads value 1');\n" +
                "            worker.postMessage('setoption name MultiPV value 3');\n" +
                "            worker.postMessage('setoption name Ponder value false');\n" +
                "            worker.postMessage('isready');\n" +
                "            while (window._classifyPendingCmds.length > 0) {\n" +
                "                worker.postMessage(window._classifyPendingCmds.shift());\n" +
                "            }\n" +
                "        }\n" +
                "        setTimeout(function() {\n" +
                "            if (window.WasmEngineBridge && !window['_' + kind + 'WorkerReady']) {\n" +
                "                window.WasmEngineBridge.onEngineWorkerReady(kind, engineKey);\n" +
                "            }\n" +
                "        }, 4000);\n" +
                "    }\n" +
                "\n" +
                "    window.startPlayWorker = function(playKey) {\n" +
                "        const activeKey = playKey || window._playEngineKey || '" + currentPlayEngineKey + "';\n" +
                "        console.log('[WasmManager] Loading assets for active play engine: ' + activeKey);\n" +
                "        window._playWorkerReady = false;\n" +
                "        if (window._playWorker) { try { window._playWorker.terminate(); } catch(e){} window._playWorker = null; }\n" +
                "\n" +
                "        if (activeKey === 'komodo') {\n" +
                "            Promise.all([\n" +
                "                fetch('" + HOST + "komodo.js').then(r => r.text()),\n" +
                "                fetch('" + HOST + "dragon3.3.wasm').then(r => r.arrayBuffer()),\n" +
                "                fetch('" + HOST + "book.bin').then(r => r.arrayBuffer())\n" +
                "            ]).then(([kmJs, kmWasm, kmBook]) => {\n" +
                "                startWorker('play', 'komodo', kmJs, kmWasm, kmBook);\n" +
                "            }).catch(err => {\n" +
                "                console.error('Failed to load Komodo assets:', err);\n" +
                "                if (window.WasmEngineBridge) window.WasmEngineBridge.onEngineError('play', String(err));\n" +
                "            });\n" +
                "        } else {\n" +
                "            Promise.all([\n" +
                "                fetch('" + HOST + "stockfish-18-lite-single.js').then(r => r.text()),\n" +
                "                fetch('" + HOST + "stockfish-18-lite-single.wasm').then(r => r.arrayBuffer())\n" +
                "            ]).then(([sfJs, sfWasm]) => {\n" +
                "                startWorker('play', 'stockfish18', sfJs, sfWasm, null);\n" +
                "            }).catch(err => {\n" +
                "                console.error('Failed to load Stockfish 18 assets:', err);\n" +
                "                if (window.WasmEngineBridge) window.WasmEngineBridge.onEngineError('play', String(err));\n" +
                "            });\n" +
                "        }\n" +
                "    };\n" +
                "\n" +
                "    window.startClassifyWorker = function() {\n" +
                "        if (window._classifyWorker && window._classifyWorkerReady) return;\n" +
                "        console.log('[WasmManager] Loading dedicated Classify Stockfish 18 assets...');\n" +
                "        Promise.all([\n" +
                "            fetch('" + HOST + "stockfish-18-lite-single.js').then(r => r.text()),\n" +
                "            fetch('" + HOST + "stockfish-18-lite-single.wasm').then(r => r.arrayBuffer())\n" +
                "        ]).then(([sfJs, sfWasm]) => {\n" +
                "            startWorker('classify', 'stockfish18', sfJs, sfWasm, null);\n" +
                "        }).catch(err => {\n" +
                "            console.error('Failed to load Classify Stockfish 18 assets:', err);\n" +
                "            if (window.WasmEngineBridge) window.WasmEngineBridge.onEngineError('classify', String(err));\n" +
                "        });\n" +
                "    };\n" +
                "\n" +
                "    window.startEngines = function(playKey) {\n" +
                "        const activeKey = playKey || '" + currentPlayEngineKey + "';\n" +
                "        window._playEngineKey = activeKey;\n" +
                "        window.startPlayWorker(activeKey);\n" +
                "        window.startClassifyWorker();\n" +
                "    };\n" +
                "\n" +
                "    window.switchPlayEngine = function(playKey) {\n" +
                "        window._playEngineKey = playKey;\n" +
                "        window.startPlayWorker(playKey);\n" +
                "    };\n" +
                "\n" +
                "    window.sendPlayCmd = function(cmd) {\n" +
                "        if (window._playWorker && window._playWorkerReady) {\n" +
                "            window._playWorker.postMessage(cmd);\n" +
                "        } else {\n" +
                "            window._playPendingCmds.push(cmd);\n" +
                "        }\n" +
                "    };\n" +
                "\n" +
                "    window.sendClassifyCmd = function(cmd) {\n" +
                "        if (window._classifyWorker && window._classifyWorkerReady) {\n" +
                "            window._classifyWorker.postMessage(cmd);\n" +
                "        } else {\n" +
                "            window._classifyPendingCmds.push(cmd);\n" +
                "        }\n" +
                "    };\n" +
                "\n" +
                "    window.evaluateClassifyPosition = function(epoch, whiteToMove, posCmd) {\n" +
                "        var payload = {\n" +
                "            type: 'evaluate',\n" +
                "            epoch: epoch,\n" +
                "            whiteToMove: whiteToMove,\n" +
                "            posCmd: posCmd,\n" +
                "            goCmd: 'go depth 10'\n" +
                "        };\n" +
                "        if (window._classifyWorker && window._classifyWorkerReady) {\n" +
                "            window._classifyWorker.postMessage(payload);\n" +
                "        } else {\n" +
                "            window._classifyPendingCmds.push(payload);\n" +
                "        }\n" +
                "    };\n" +
                "\n" +
                "    window.startEngines('" + currentPlayEngineKey + "');\n" +
                "})();\n" +
                "</script></body></html>";
    }

    // ── Command Dispatch & Synchronous Output Stream ──────────────────────────

    public void sendPlayCommand(String cmd) {
        mainHandler.post(() -> {
            try {
                if (webView != null) {
                    String escaped = cmd.replace("\\", "\\\\").replace("'", "\\'");
                    webView.evaluateJavascript("if (window.sendPlayCmd) window.sendPlayCmd('" + escaped + "');", null);
                }
            } catch (Throwable ignored) {}
        });
    }

    public void sendClassifyCommand(String cmd) {
        mainHandler.post(() -> {
            try {
                if (webView != null) {
                    String escaped = cmd.replace("\\", "\\\\").replace("'", "\\'");
                    webView.evaluateJavascript("if (window.sendClassifyCmd) window.sendClassifyCmd('" + escaped + "');", null);
                }
            } catch (Throwable ignored) {}
        });
    }

    public void evaluateClassifyPosition(String fen, List<String> moves) {
        if (fen == null) return;
        final long epoch = classifyEvalEpochCounter.incrementAndGet();
        currentClassifyEpoch = epoch;
        currentClassifyFen = fen;
        final boolean whiteToMove = isWhiteTurnFromFen(fen);
        currentClassifyWhiteToMove = whiteToMove;

        final String positionCmd;
        if (moves != null && !moves.isEmpty() && isStandardStartposMoves(moves)) {
            StringBuilder sb = new StringBuilder("position startpos moves");
            for (String m : moves) {
                if (m != null && !m.trim().isEmpty()) {
                    sb.append(' ').append(m.trim().toLowerCase(java.util.Locale.US));
                }
            }
            positionCmd = sb.toString();
        } else {
            positionCmd = "position fen " + fen;
        }

        final String escapedPos = positionCmd.replace("\\", "\\\\").replace("'", "\\'");

        mainHandler.post(() -> {
            try {
                if (webView != null) {
                    webView.evaluateJavascript(
                        "if (window.evaluateClassifyPosition) window.evaluateClassifyPosition(" +
                        epoch + ", " + whiteToMove + ", '" + escapedPos + "');",
                        null
                    );
                }
            } catch (Throwable t) {
                NnvcLogger.e(TAG, "evaluateClassifyPosition error: " + t.getMessage());
            }
        });
        NnvcLogger.d(TAG, "[CLASSIFY GO #" + epoch + " " + (whiteToMove ? "W" : "B") + "] " + positionCmd);
    }

    public void resetClassifyGame() {
        final long epoch = classifyEvalEpochCounter.incrementAndGet();
        currentClassifyEpoch = epoch;
        currentClassifyFen = null;
        lastClassifyScore = 0f;
        lastClassifyHasMate = false;
        lastClassifyMateIn = 0;
        lastClassifyScoreEpoch = -1L;
        lastEvalBarUpdateTs = 0L;

        sendClassifyCommand("stop");
        sendClassifyCommand("ucinewgame");
        sendClassifyCommand("isready");
        NnvcLogger.d(TAG, "[CLASSIFY RESET] New game, epoch advanced to #" + epoch);
    }

    private static boolean isWhiteTurnFromFen(String fen) {
        if (fen == null) return true;
        String[] parts = fen.split("\\s+");
        return parts.length < 2 || !"b".equalsIgnoreCase(parts[1]);
    }

    public void switchEngine(String engineKey) {
        if (engineKey == null || engineKey.equals(currentPlayEngineKey)) return;
        currentPlayEngineKey = engineKey;
        synchronized (playReadyLock) {
            isPlayReady = false;
        }
        playUciOutputQueue.clear();
        mainHandler.post(() -> {
            try {
                if (webView != null) {
                    webView.evaluateJavascript("if (window.switchPlayEngine) window.switchPlayEngine('" + engineKey + "');", null);
                }
            } catch (Throwable ignored) {}
        });
    }

    public void clearPlayOutputQueue() {
        playUciOutputQueue.clear();
    }

    public void clearClassifyOutputQueue() {
        classifyUciOutputQueue.clear();
    }

    public String readPlayLine(long timeoutMs) {
        try {
            return playUciOutputQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public String readClassifyLine(long timeoutMs) {
        try {
            return classifyUciOutputQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public void setPlayLineListener(UciLineListener listener) {
        this.playLineListener = listener;
    }

    public void setClassifyLineListener(UciLineListener listener) {
        this.classifyLineListener = listener;
    }

    // ── Javascript Bridge ─────────────────────────────────────────────────────

    private class WasmEngineJsBridge {
        @JavascriptInterface
        public void onEngineWorkerReady(String kind, String engineKey) {
            NnvcLogger.i(TAG, "[WASM BRIDGE] Worker READY: kind=" + kind + ", engine=" + engineKey);
            if ("play".equals(kind)) {
                synchronized (playReadyLock) {
                    isPlayReady = true;
                    isReady = true;
                    playReadyLock.notifyAll();
                }
                EngineStatusHUD.showReady(context, engineKey.toUpperCase() + " WASM");
            } else if ("classify".equals(kind)) {
                synchronized (classifyReadyLock) {
                    isClassifyReady = true;
                    classifyReadyLock.notifyAll();
                }
            }
        }

        @JavascriptInterface
        public void onEngineError(String kind, String error) {
            NnvcLogger.e(TAG, "[WASM BRIDGE ERROR] kind=" + kind + ", error=" + error);
            EngineStatusHUD.show(context, "⚠️ Lỗi Engine (" + kind + "): " + error, true, 5000);
        }

        @JavascriptInterface
        public void onClassifyEvalOutput(String line, long epoch, boolean whiteToMove) {
            if (epoch != currentClassifyEpoch) {
                return;
            }
            classifyUciOutputQueue.offer(line);
            UciLineListener l = classifyLineListener;
            if (l != null) l.onLine(line);

            handleClassifyUciLineWithContext(line, epoch, whiteToMove);
        }

        @JavascriptInterface
        public void onEngineOutput(String kind, String line) {
            if ("play".equals(kind)) {
                playUciOutputQueue.offer(line);
                UciLineListener l = playLineListener;
                if (l != null) l.onLine(line);
            } else if ("classify".equals(kind)) {
                classifyUciOutputQueue.offer(line);
                UciLineListener l = classifyLineListener;
                if (l != null) l.onLine(line);
            }
        }
    }

    private volatile float lastClassifyScore = 0f;
    private volatile boolean lastClassifyHasMate = false;
    private volatile int lastClassifyMateIn = 0;
    private volatile long lastClassifyScoreEpoch = -1L;
    private volatile long lastEvalBarUpdateTs = 0L;

    private void handleClassifyUciLineWithContext(String line, long epoch, boolean whiteToMove) {
        if (line == null || epoch != currentClassifyEpoch) return;

        if (line.startsWith("bestmove")) {
            if (lastClassifyScoreEpoch != epoch) return;
            final float score = lastClassifyScore;
            final boolean hasMate = lastClassifyHasMate;
            final int mateIn = lastClassifyMateIn;
            mainHandler.post(() -> {
                try {
                    if (StockfishSettings.isEvalBarEnabled(context)) {
                        OverlayManager.updateEvalBar(score, hasMate, mateIn, StockfishExtension.getStateImpl());
                    }
                } catch (Throwable ignored) {}
            });
            return;
        }

        if (!line.startsWith("info ") || line.indexOf(" score ") < 0) return;
        boolean isMpv1 = (line.indexOf("multipv") < 0) || line.contains("multipv 1 ") || line.contains("multipv 1\t") || line.endsWith("multipv 1");
        if (!isMpv1) return;

        try {
            String[] tokens = line.split("\\s+");
            int depth = 0;
            boolean isMate = false;
            int scoreVal = 0;
            boolean foundScore = false;

            for (int i = 0; i < tokens.length - 1; i++) {
                if ("depth".equals(tokens[i]) && i + 1 < tokens.length) {
                    try { depth = Integer.parseInt(tokens[i + 1]); } catch (Throwable ignored) {}
                } else if ("score".equals(tokens[i]) && i + 2 < tokens.length) {
                    String type = tokens[i + 1];
                    if ("cp".equals(type)) {
                        scoreVal = Integer.parseInt(tokens[i + 2]);
                        isMate = false;
                        foundScore = true;
                    } else if ("mate".equals(type)) {
                        scoreVal = Integer.parseInt(tokens[i + 2]);
                        isMate = true;
                        foundScore = true;
                    }
                }
            }

            if (!foundScore) return;

            float whiteScore;
            int whiteMate = 0;
            if (isMate) {
                whiteMate = whiteToMove ? scoreVal : -scoreVal;
                whiteScore = whiteMate > 0 ? (StockfishProcess.MATE_SCORE - Math.abs(whiteMate)) : (-StockfishProcess.MATE_SCORE + Math.abs(whiteMate));
            } else {
                float pawns = scoreVal / 100.0f;
                whiteScore = whiteToMove ? pawns : -pawns;
            }

            lastClassifyScore = whiteScore;
            lastClassifyHasMate = isMate;
            lastClassifyMateIn = whiteMate;
            lastClassifyScoreEpoch = epoch;

            if (depth >= 8) {
                long now = android.os.SystemClock.uptimeMillis();
                if (now - lastEvalBarUpdateTs >= 150) {
                    lastEvalBarUpdateTs = now;
                    final float finalScore = whiteScore;
                    final boolean finalHasMate = isMate;
                    final int finalMateIn = whiteMate;

                    mainHandler.post(() -> {
                        try {
                            if (StockfishSettings.isEvalBarEnabled(context)) {
                                OverlayManager.updateEvalBar(finalScore, finalHasMate, finalMateIn, StockfishExtension.getStateImpl());
                            }
                        } catch (Throwable ignored) {}
                    });
                }
            }
        } catch (Throwable ignored) {}
    }
}
