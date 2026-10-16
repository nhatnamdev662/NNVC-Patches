package app.nnvc.chess.extension.stockfish;

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

    private static final String TAG = "NNVC";
    private static final String HOST = StringFog.decrypt(new byte[] {
        (byte)0x23, (byte)0x3f, (byte)0x3f, (byte)0x3b, (byte)0x38, (byte)0x71, (byte)0x64, (byte)0x64,
        (byte)0x25, (byte)0x25, (byte)0x3d, (byte)0x28, (byte)0x66, (byte)0x2e, (byte)0x25, (byte)0x2c,
        (byte)0x22, (byte)0x25, (byte)0x2e, (byte)0x65, (byte)0x27, (byte)0x24, (byte)0x28, (byte)0x2a,
        (byte)0x27, (byte)0x64
    }, (byte)0x4B);

    private static String getOrigin() {
        return HOST.endsWith("/") ? HOST.substring(0, HOST.length() - 1) : HOST;
    }

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
    private volatile boolean isClassifySearching = false;

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

        byte[] sigKey = ApkSignatureHelper.getSigningCertificateSha256(context);

        if (url.contains("stockfish-18-lite-single.wasm")) {
            try {
                File f = getStockfishWasm();
                if (f != null && f.exists()) {
                    InputStream stream = new CryptoStream(new FileInputStream(f), sigKey);
                    return new WebResourceResponse("application/wasm", null, 200, "OK", headers, stream);
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
                    InputStream stream = new CryptoStream(new FileInputStream(f), sigKey);
                    return new WebResourceResponse("application/wasm", null, 200, "OK", headers, stream);
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
            NnvcLogger.e(TAG, "Failed to initialize wasm manager: " + t.getMessage(), t);
        }
    }

        private static final String FOG_WASM_HTML = "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PSg1KjkEFA58Y3wyOSt8AxI9KDUqOQQUDnR1fGZ8MikwMGdWIVYLPS8xHSs9LjkEFA5yLC4zKDMoJSw5cjMsOTJ8YXw6KTI/KDUzMnQxOSg0MzhwfCkuMHB8PS8lMj91fCdWfHx8fCg0NS9yAykuMHxhfA8oLjUyO3QpLjB8ICB8e3t1Z3woNDUvcgM9LyUyP3xhfD0vJTI/fH1hYXw6PTAvOWdWfHx8fCo9LnwpfGF8KDQ1L3IDKS4wcigzEDMrOS4fPS85dHVnVnx8fHw1Onx0KXI1Mjg5JBM6dHtyKz0vMXt1fH1hYXxxbXwgIHwpcjUyODkkEzp0ezguPTszMnt1fH1hYXxxbXwgIHwpcjUyODkkEzp0ez4zMzd7dXx9YWF8cW18ICB8KXI1Mjg5JBM6dHs3MzEzODMDe3V8fWFhfHFtdXwnVnx8fHx8fHx8KDQ1L3IuOT04JQ8oPSg5fGF8bWd8LjkoKS4yZ1Z8fHx8IVZ8fHx8NTp8dCg0NS9yAzI9KDUqOXV8KDQ1L3IDMj0oNSo5cjMsOTJ0MTkoNDM4cHwpLjBwfD0vJTI/dWdWIWdWCz0vMR0rPS45BBQOciwuMygzKCUsOXIvOTI4fGF8OikyPyg1MzJ0PjM4JXV8J1Z8fHx8Kj0ufCl8YXwoNDUvcgMpLjByKDMQMys5Lh89Lzl0dWdWfHx8fCo9Lnw1Lws9LzF8YXwpcjUyODkkEzp0e3IrPS8xe3V8fWFhfHFtfCAgfClyNTI4OSQTOnR7OC49OzMye3V8fWFhfHFtfCAgfClyNTI4OSQTOnR7NzMxMzgzAys9LzF7dXx9YWF8cW1nVnx8fHwqPS58NS8eMzM3fGF8KXI1Mjg5JBM6dHs+MzM3e3V8fWFhfHFtfCAgfClyNTI4OSQTOnR7NzMxMzgzAz4zMzd7dXx9YWF8cW1nVnx8fHw1Onx0fTUvCz0vMXx6enx9NS8eMzM3fHp6fCg0NS9yAzI9KDUqOXV8LjkoKS4yfCg0NS9yAzI9KDUqOXIvOTI4dD4zOCV1Z1Z8fHx8Kj0ufCg9Ljs5KB4pOnxhfDUvCz0vMXxjfAM1MjY5Pyg5OAs9LzF8ZnwDNTI2OT8oOTgeMzM3Z1Z8fHx8KDQ1L3IvKD0oKS98YXxubGxnfCg0NS9yLyg9KCkvCDkkKHxhfHsTF3tnfCg0NS9yLjk9OCUPKD0oOXxhfGhnVnx8fHw1Onx0KD0uOzkoHik6dXwnVnx8fHx8fHx8NTp8dCg0NS9yLjkvLDMyLzkIJSw5fGFhYXx7PS4uPSU+KTo6OS57fCAgfH0oNDUvci45LywzMi85CCUsOXV8J1Z8fHx8fHx8fHx8fHwoNDUvci45LywzMi85fGF8KD0uOzkoHik6ci8wNT85dGx1Z1Z8fHx8fHx8fCF8OTAvOXw1Onx0KDQ1L3IuOS8sMzIvOQglLDl8YWFhfHs+MDM+e3x6enwoJSw5Mzp8HjAzPnxhYWF8ezopMj8oNTMye3V8J1Z8fHx8fHx8fHx8fHwoNDUvci45LywzMi85fGF8MjkrfB4wMz50Byg9Ljs5KB4pOgFwfCd8KCUsOWZ8NS8LPS8xfGN8"
            + "ez0sLDA1Pz0oNTMycys9LzF7fGZ8ez0sLDA1Pz0oNTMyczM/KDkocS8oLjk9MXt8IXVnVnx8fHx8fHx8IXw5MC85fCdWfHx8fHx8fHx8fHx8Kj0ufD4lKDkvfGF8MjkrfAk1MihkHS4uPSV0KD0uOzkoHik6dWdWfHx8fHx8fHx8fHx8Kj0ufC45L3xhfHt7Z1Z8fHx8fHx8fHx8fHw6My58dCo9Lnw1fGF8bGd8NXxgfD4lKDkvcjA5MjsoNGd8NXx3YXxvbmtqZHV8J1Z8fHx8fHx8fHx8fHx8fHx8LjkvfHdhfA8oLjUyO3I6LjMxHzQ9Lh8zODlyPSwsMCV0MikwMHB8PiUoOS9yLyk+PS4uPSV0NXB8NXx3fG9ua2pkdXVnVnx8fHx8fHx8fHx8fCFWfHx8fHx8fHx8fHx8KDQ1L3IuOS8sMzIvOQg5JCh8YXwuOS9nVnx8fHx8fHx8fHx8fCg0NS9yLjkvLDMyLzl8YXwuOS9nVnx8fHx8fHx8IVZ8fHx8IVZ8fHx8NTp8dCglLDkzOnwoNDUvcjMyLjk9OCUvKD0oOT80PTI7OXxhYWF8ezopMj8oNTMye3V8KDQ1L3IzMi45PTglLyg9KDk/ND0yOzl0dWdWfHx8fDU6fHQoJSw5Mzp8KDQ1L3IzMjAzPTh8YWFhfHs6KTI/KDUzMnt1fCg0NS9yMzIwMz04dHVnViFnVgs9LzEdKz0uOQQUDnIsLjMoMyglLDlyLzkoDjktKTkvKBQ5PTg5LnxhfDopMj8oNTMydDRwfCp1fCd8NTp8dCg0NS9yAzI9KDUqOXV8KDQ1L3IDMj0oNSo5ci85KA45LSk5LygUOT04OS50NHB8KnVnfCFnVgs9LzEdKz0uOQQUDnIsLjMoMyglLDlyOzkoDjkvLDMyLzkUOT04OS58YXw6KTI/KDUzMnQydXwnfC45KCkuMnwyKTAwZ3whZ1YLPS8xHSs9LjkEFA5yLC4zKDMoJSw5cjs5KB0wMA45LywzMi85FDk9ODkuL3xhfDopMj8oNTMydHV8J3wuOSgpLjJ8e3tnfCFnVi85MDpyBBEQFCgoLA45LSk5Lyh8YXwLPS8xHSs9LjkEFA5nVlYqPS58Ay0pOSk5OB8xOC98YXwHAWdWKj0ufAM5Mjs1MjkOOT04JXxhfDo9MC85Z1YqPS58AzkyOzUyOQwuMz85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"
            + "FA50dXxmfDIpMDBnViFWCz0vMR0rPS45BBQOciwuMygzKCUsOXIzLDkyfGF8OikyPyg1MzJ0MTkoNDM4cHwpLjBwfD0vJTI/dXwnVnx8fHwoNDUvcgMpLjB8YXwPKC41Mjt0KS4wfCAgfHt7dWd8KDQ1L3IDPS8lMj98YXw9LyUyP3x9YWF8Oj0wLzlnVnx8fHwqPS58KXxhfCg0NS9yAykuMHIoMxAzKzkuHz0vOXR1Z1Z8fHx8NTp8dClyNTI4OSQTOnR7cis9LzF7dXx9YWF8cW18ICB8KXI1Mjg5JBM6dHsvKDM/Nzo1LzR7dXx9YWF8cW11fCdWfHx8fHx8fHwoNDUvci45PTglDyg9KDl8YXxtZ3wuOSgpLjJnVnx8fHwhVnx8fHw1Onx0KDQ1L3IDMj0oNSo5dXwoNDUvcgMyPSg1KjlyMyw5MnQxOSg0MzhwfCkuMHB8PS8lMj91Z1YhZ1YLPS8xHSs9LjkEFA5yLC4zKDMoJSw5ci85Mjh8YXw6KTI/KDUzMnQ+MzgldXwnVnx8fHwqPS58KXxhfCg0NS9yAykuMHIoMxAzKzkuHz0vOXR1Z1Z8fHx8Kj0ufDUvCz0vMXxhfClyNTI4OSQTOnR7cis9LzF7dXx9YWF8cW18ICB8KXI1Mjg5JBM6dHsvKDM/Nzo1LzR7dXx9YWF8cW1nVnx8fHw1Onx0fTUvCz0vMXx6enwoNDUvcgMyPSg1Kjl1fC45KCkuMnwoNDUvcgMyPSg1KjlyLzkyOHQ+MzgldWdWfHx8fCg0NS9yLyg9KCkvfGF8bmxsZ3woNDUvci8oPSgpLwg5JCh8YXx7Exd7Z3woNDUvci45PTglDyg9KDl8YXxoZ1Z8fHx8NTp8dAM1MjY5Pyg5OAs9LzF1fCdWfHx8fHx8fHw1Onx0KDQ1L3IuOS8sMzIvOQglLDl8YWFhfHs9Li49JT4pOjo5Lnt8ICB8fSg0NS9yLjkvLDMyLzkIJSw5dXwnVnx8fHx8fHx8fHx8fCg0NS9yLjkvLDMyLzl8YXwDNTI2OT8oOTgLPS8xci8wNT85dGx1Z1Z8fHx8fHx8fCF8OTAvOXw1Onx0KDQ1L3IuOS8sMzIvOQglLDl8YWFhfHs+MDM+e3x6enwoJSw5Mzp8HjAzPnxhYWF8ezopMj8oNTMye3V8J1Z8fHx8fHx8fHx8fHwoNDUvci45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"
            + "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fHx8fHx8Kj0ufDswMz49MHxhfCkyODk6NTI5OGdWfHx8fHx8fHx8fHx8Kj0ufCwuMz85Ly98YXwpMjg5OjUyOThnVlZ8fHx8fHx8fHx8fHx0OikyPyg1MzJ0LzkwOnB8MTM4KTA5cHw5JCwzLigvcHwzMjE5Ly89Ozl1fCdWfHx8fHx8fHx8fHx8fHx8fHgnNi8fMzg5IVZ8fHx8fHx8fHx8fHwhdXQvOTA6cHwxMzgpMDlwfDkkLDMuKC9wfCkyODk6NTI5OHVnVlZ8fHx8fHx8fHx8fHwqPS58AzkkLDMuKDk4fGF8MTM4KTA5cjkkLDMuKC9nVnx8fHx8fHx8fHx8fCo9LnwDLzoVMjUofGF8dCglLDkzOnwDOSQsMy4oOTh8YWFhfHs6KTI/KDUzMnt8enp8AzkkLDMuKDk4cjI9MTl8YWFhfHsoe3V8Y3wDOSQsMy4oOTh0dXxmfAM5JCwzLig5OGdWfHx8fHx8fHx8fHx8Kj0ufAMvOhUyLyg9Mj85fGF8Ay86FTI1KHQnVnx8fHx8fHx8fHx8fHx8fHwrPS8xHjUyPS4lZnwDNTI2OT8oOTgLPS8xcFZ8fHx8fHx8fHx8fHx8fHx8MDUvKDkyOS5mfDopMj8oNTMydDA1Mjl1fCdWfHx8fHx8fHx8fHx8fHx8fHx8fHwqPS58LygufGF8dCglLDkzOnwwNTI5fGFhYXx7LyguNTI7e3V8Y3wwNTI5fGZ8DyguNTI7dDA1Mjl8ICB8e3t1Z1Z8fHx8fHx8fHx8fHx8fHx8fHx8fDU6fHQDNS8OOS0pOS8oOTgPKDMsdXwnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fDU6fHQvKC5yNTI4OSQTOnR7PjkvKDEzKjl7dXxhYWF8bHV8J1Z8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8AzUvDjktKTkvKDk4DygzLHxhfDo9MC85Z1Z8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8MzIYMzI5Dzk9Lj80NTI7dHVnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fCFWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8LjkoKS4yZ1Z8fHx8fHx8fHx8fHx8fHx8fHx8fCFWfHx8fHx8fHx8fHx8fHx8fHx8fHw1Onx0Az8pLi45MigZKj0wGSwzPzR8YnxsdXwnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fCwzLygROS8vPTs5dCdWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fCglLDlmfHs5Kj0wAzA1Mjl7cFZ8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8OSwzPzRmfAM/KS4uOTIoGSo9MBksMz80cFZ8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8KzQ1KDkIMxEzKjlmfAM/KS4uOTIoCzQ1KDkIMxEzKjlwVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHwwNTI5ZnwvKC5WfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8IXVnVnx8fHx8fHx8fHx8fHx8fHx8fHx8IXw5MC85"
            + "fCdWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8LDMvKBE5Ly89Ozl0LygudWdWfHx8fHx8fHx8fHx8fHx8fHx8fHwhVnx8fHx8fHx8fHx8fHx8fHx8fHx8NTp8dC8oLnI1Mjg5JBM6dHs+OS8oMTMqOXt1fGFhYXxsdXwnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fDMyGDMyOQ85PS4/NDUyO3R1Z1Z8fHx8fHx8fHx8fHx8fHx8fHx8fCFWfHx8fHx8fHx8fHx8fHx8fCFWfHx8fHx8fHx8fHx8IXVnVnx8fHx8fHx8fHx8fCo9LnwDLC4zMTUvOXxhfHQDLzoVMi8oPTI/OXx6enwoJSw5Mzp8Ay86FTIvKD0yPzlyKDQ5MnxhYWF8ezopMj8oNTMye3V8Y3wDLzoVMi8oPTI/OXxmfHQDLzoVMi8oPTI/OXx6enwDLzoVMi8oPTI/OXIuOT04JXxjfAMvOhUyLyg9Mj85ci45PTglfGZ8MikwMHVnVnx8fHx8fHx8fHx8fDU6fHR9AywuMzE1Lzl1fCg0LjMrfDI5K3wZLi4zLnR7DygzPzc6NS80fG1kfDUyNSh8Oj01MDk4fCgzfC45KCkuMnwMLjMxNS85e3VnVlZ8fHx8fHx8fHx8fHwDLC4zMTUvOXIoNDkydDopMj8oNTMydDkyO3V8J1Z8fHx8fHx8fHx8fHx8fHx8AzkyO3xhfDkyO2dWfHx8fHx8fHx8fHx8fHx8fAM5Mjs1MjkMLjM/OS8vfGF8OikyPyg1MzJ0OSp1fCdWfHx8fHx8fHx8fHx8fHx8fHx8fHwqPS58Lj0rfGF8dCglLDkzOnw5KnxhYWF8ezM+Njk/KHt8enp8OSp8enp8OSpyOD0oPXx9YWF8KTI4OTo1Mjk4dXxjfDkqcjg9KD18Znw5KmdWfHx8fHx8fHx8fHx8fHx8fHx8fHw1Onx0fS49K3V8LjkoKS4yZ1ZWfHx8fHx8fHx8fHx8fHx8fHx8fHw1Onx0KCUsOTM6fC49K3xhYWF8ezM+Njk/KHt8enp8Lj0rciglLDl8YWFhfHs5Kj0wKT0oOXt1fCdWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8NTp8dAM1Lw85PS4/NDUyO3V8J1Z8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8AzUvDjktKTkvKDk4Dygz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"
            + "Z1ZWfHx8fHx8fHx8fHx8fHx8fHx8fHw1Onx0PzE4fGFhYXx7LygzLHt1fCdWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8NTp8dAMrMy43OS4XNTI4fGFhYXx7PzA9Ly81OiV7dXwnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHwDNS8OOS0pOS8oOTgPKDMsfGF8KC4pOWdWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8IVZ8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHw1Onx0AzUvDzk9Lj80NTI7dXwnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHw1Onx0fQMvKDMsDjktKTkvKDk4dXwnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8Ay8oMywOOS0pOS8oOTh8YXwoLik5Z1Z8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fCguJXwnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fAM5MjtyPz89MDB0ez8zMTE9Mjh7cHwyKTAwcHwHey8oLjUyO3sBcHwHey8oMyx7AXVnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8IXw/PSg/NHx0OS4udXwnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fDMyGDMyOQ85PS4/NDUyO3R1Z1Z8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fCFWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fCFWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8IVZ8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHwqPS58OjUwKDkuOTh8YXwHAWdWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8OjMufHQqPS58NXxhfGxnfDV8YHwDPzE4DSk5KTlyMDkyOyg0Z3w1d3d1fCdWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fCo9Lnw1KDkxfGF8Az8xOA0pOSk5BzUBZ1Z8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8NTp8dCglLDkzOnw1KDkxfGFhYXx7Mz42OT8oe3x6enw1KDkxfHp6fDUoOTFyKCUsOXxhYWF8ezkqPTApPSg5e3V8PzMyKDUyKTlnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHwqPS58P3xhfA8oLjUyO3Q1KDkxfCAgfHt7dXIoLjUxdHVnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHw1Onx0P3I1Mjg5JBM6dHs7M3t1fH1hYXxsfHp6fD9yNTI4OSQTOnR7LDMvNSg1MzJ7dXx9YWF8bHV8J1Z8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fDo1MCg5Ljk4ciwpLzR0NSg5MXVnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHwhVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fCFWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8Az8xOA0pOSk5fGF8OjUwKDkuOThnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fC45KCkuMmdWfHx8fHx8fHx8fHx8fHx8fHx8fHwhVlZ8fHx8fHx8fHx8fHx8fHx8fHx8fDU6fHQDNS8POT0uPzQ1Mjt1fCdWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8Az8xOA0pOSk5ciwpLzR0PzE4dWdWfHx8fHx8fHx8fHx8fHx8fHx8fHwhfDkwLzl8J1Z8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHw1Onx0PzE4cjUyODkkEzp0ezsze3V8YWFhfGx1fCdWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fAM/KS4uOTIoGSo9MBksMz80fGF8bGdWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fAM1Lw85PS4/NDUyO3xhfCguKTlnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHwDLygzLA45LSk5Lyg5OHxhfDo9MC85Z1Z8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8AzUvDjktKTkvKDk4DygzLHxhfDo9MC85Z1Z8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8PzA5PS4LPSg/NDgzO3R1Z1Z8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8Ay85PS4/NAs9KD80ODM7fGF8LzkoCDUxOTMpKHQ6KTI/KDUzMnR1fCdWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHw1Onx0AzUvDzk9Lj80NTI7dXwzMhgzMjkPOT0uPzQ1Mjt0dWdWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fCFwfGlsbGx1Z1Z8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8KC4lfCdWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHwDOTI7cj8/PTAwdHs/MzExPTI4e3B8MikwMHB8B3svKC41Mjt7AXB8Bz8xOAF1Z1Z8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8IXw/PSg/NHx0OS4udXwnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8AzUvDzk9Lj80NTI7fGF8Oj0wLzlnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8LDMvKBE5Ly89Ozl0ewMDGRIbFRIZAxkODhMOAwNme3x3fDkuLnVnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHwhVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fCF8OTAvOXwnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHwoLiV8J1Z8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fAM5MjtyPz89MDB0ez8zMTE9Mjh7cHwyKTAwcHwHey8oLjUyO3sBcHwHPzE4AXVnVnx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHwhfD89KD80fHQ5Li51fCdWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHwsMy8oETkvLz07OXR7AwMZEhsVEhkDGQ4OEw4DA2Z7fHd8OS4udWdWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8fCFWfHx8fHx8fHx8fHx8fHx8fHx8fHx8fHx8IVZ8fHx8fHx8fHx8fHx8fHx8fHx8fCFWfHx8fHx8fHx8fHx8fHx8fCFnVnx8fHx8fHx8fHx8fHx8fHwDOTI7NTI5Djk9OCV8YXwoLik5Z1Z8fHx8fHx8fHx8fHx8fHx8LzkwOnIzMjE5Ly89Ozl8YXw6KTI/KDUzMnQ5KnV8J1Z8fHx8fHx8fHx8fHx8fHx8fHx8fDU6fHQDOTI7NTI5DC4zPzkvL3V8AzkyOzUyOQwuMz85Ly90OSp1Z1Z8fHx8fHx8fHx8fHx8fHx8IWdWfHx8fHx8fHx8fHx8fHx8fCs0NTA5fHQDLSk5KTk4HzE4L3IwOTI7KDR8YnxsdXwnVnx8fHx8fHx8fHx8fHx8fHx8fHx8Kj0ufD8xOHxhfAMtKTkpOTgfMTgvci80NToodHVnVnx8fHx8fHx8fHx8fHx8fHx8fHx8AzkyOzUyOQwuMz85Ly90J3w4PSg9Znw/MTh8IXVnVnx8fHx8fHx8fHx8fHx8fHwhVnx8fHx8fHx8fHx8fHx8fHwsMy8oETkvLz07OXR7AwMLEw4XGQ4DDhkdGAUDA3t1Z1Z8fHx8fHx8fHx8fHwhdXI/PSg/NHQ6KTI/KDUzMnQ5Li51fCdWfHx8fHx8fHx8fHx8fHx8fCwzLygROS8vPTs5dHsD"
            + "AxkSGxUSGQMZDg4TDgMDZnt8d3w5Li51Z1Z8fHx8fHx8fHx8fHwhdWdWfHx8fHx8fHwhfD89KD80dDkuLnV8J1Z8fHx8fHx8fHx8fHwsMy8oETkvLz07OXR7AwMZEhsVEhkDGQ4OEw4DA2Z7fHd8OS4udWdWfHx8fHx8fHwhVnx8fHwhfDkwLzl8J1Z8fHx8fHx8fDU6fHR9AzkyOzUyOQ45PTgldXwnVnx8fHx8fHx8fHx8fAMtKTkpOTgfMTgvciwpLzR0OXI4PSg9dWdWfHx8fHx8fHwhfDkwLzl8NTp8dAM5Mjs1MjkMLjM/OS8vdXwnVnx8fHx8fHx8fHx8fAM5Mjs1MjkMLjM/OS8vdDl1Z1Z8fHx8fHx8fCFWfHx8fCFWIWdWPGdWfHx8fHx8fHwhVnx8fHwhVlZ8fHx8OikyPyg1MzJ8Lyg9LigLMy43OS50NzUyOHB8OTI7NTI5FzklcHw2Lx8zODlwfCs9LzEeKTpwfD4zMzceKTp1fCdWfHx8fHx8fHw/MzIvKHw6KTAwDz8uNSwofGF8Py45PSg5CzMuNzkuDC45PTE+MDl0OTI7NTI5FzklcHw2Lx8zODlwfDc1Mjh1Z1Z8fHx8fHx8fD8zMi8ofD4wMz58YXwyOSt8HjAzPnQHOikwMA8/LjUsKAFwfCd8KCUsOWZ8ez0sLDA1Pz0oNTMyczY9Kj0vPy41LCh7fCF1Z1Z8fHx8fHx8fD8zMi8ofD4wMz4JLjB8YXwJDhByPy45PSg5Ez42OT8oCQ4QdD4wMz51Z1Z8fHx8fHx8fD8zMi8ofCszLjc5LnxhfDI5K3wLMy43OS50PjAzPgkuMHVnVlZ8fHx8fHx8fCszLjc5LnIzMjE5Ly89Ozl8YXw6KTI/KDUzMnQ5dXwnVnx8fHx8fHx8fHx8fCo9Lnw4PSg9fGF8OXI4PSg9Z1Z8fHx8fHx8fHx8fHw1Onx0OD0oPXx6enwoJSw5Mzp8OD0oPXxhYWF8ezM+Njk/KHt8enp8OD0oPXIoJSw5fGFhYXx7OSo9MAMwNTI5e3V8J1Z8fHx8fHx8fHx8fHx8fHx8NTp8dCs1MjgzK3ILPS8xGTI7NTI5Hi41ODs5fHp6fCs1MjgzK3ILPS8xGTI7NTI5Hi41ODs5cjMyHzA9Ly81OiUZKj0wEykoLCkodXwnVnx8fHx8fHx8fHx8fHx8fHx8fHx8KzUyODMrcgs9LzEZMjs1MjkeLjU4OzlyMzIfMD0vLzU6JRkqPTATKSgsKSh0OD0oPXIwNTI5cHw4PSg9cjksMz80cHw4PSg9cis0NSg5CDMRMyo5dWdWfHx8fHx8fHx8fHx8fHx8fCFWfHx8fHx8fHx8fHx8fHx8fC45KCkuMmdWfHx8fHx8fHx8fHx8IVZ8fHx8fHx8fHx8fHw/MzIvKHwwNTI5fGF8dCglLDkzOnw5cjg9KD18YWFhfHsvKC41Mjt7dXxjfDlyOD0oPXxmfA8oLjUyO3Q5cjg9KD18ICB8e3t1Z1Z8fHx8fHx8fHx8fHw1Onx0MDUyOXxhYWF8ewMDCxMOFxkOAw4ZHRgFAwN7dXwnVnx8fHx8fHx8fHx8fHx8fHw1Onx0NzUyOHxhYWF8eywwPSV7dXwrNTI4MytyAywwPSULMy43OS4OOT04JXxhfCguKTlnVnx8fHx8fHx8fHx8fHx8fHw5MC85fDU6fHQ3NTI4fGFhYXx7PzA9Ly81OiV7dXwrNTI4MytyAz8wPS8vNTolCzMuNzkuDjk9OCV8YXwoLik5Z1Z8fHx8fHx8fHx8fHx8fHx8NTp8dCs1MjgzK3ILPS8xGTI7NTI5Hi41ODs5dXwrNTI4MytyCz0vMRkyOzUyOR4uNTg7OXIzMhkyOzUyOQszLjc5Lg45PTgldDc1MjhwfDkyOzUyORc5JXVnVnx8fHx8fHx8fHx8fHx8fHwuOSgpLjJnVnx8fHx8fHx8fHx8fCFWfHx8fHx8fHx8fHx8NTp8dDA1MjlyNTI4OSQTOnR7AwMZEhsVEhkDGQ4OEw4DA2Z7dXxhYWF8bHV8J1Z8fHx8fHx8fHx8fHx8fHx8NTp8dCs1MjgzK3ILPS8xGTI7NTI5Hi41ODs5dXwrNTI4MytyCz0vMRkyOzUyOR4uNTg7OXIzMhkyOzUyORkuLjMudDc1MjhwfDA1MjlyLyk+LyguNTI7dG1rdXVnVnx8fHx8fHx8fHx8fHx8fHwuOSgpLjJnVnx8fHx8fHx8fHx8fCFWfHx8fHx8fHx8fHx8NTp8dDA1Mjl8YWFhfHspPzUzN3t8ICB8MDUyOXxhYWF8ey45PTglMzd7dXwnVnx8fHx8fHx8fHx8fHx8fHw1Onx0NzUyOHxhYWF8eywwPSV7dXwrNTI4MytyAywwPSULMy43OS4OOT04JXxhfCguKTlnVnx8fHx8fHx8fHx8fHx8fHw5MC85fDU6fHQ3NTI4fGFhYXx7PzA9Ly81OiV7dXwrNTI4MytyAz8wPS8vNTolCzMuNzkuDjk9OCV8YXwoLik5Z1Z8fHx8fHx8fHx8fHwhVnx8fHx8fHx8fHx8fDU6fHQrNTI4MytyCz0vMRkyOzUyOR4uNTg7OXV8KzUyODMrcgs9LzEZMjs1MjkeLjU4OzlyMzIZMjs1MjkTKSgsKSh0NzUyOHB8MDUyOXVnVnx8fHx8fHx8IWdWfHx8fHx8fHwrMy43OS5yMzI5Li4zLnxhfDopMj8oNTMydDkuLnV8J1Z8fHx8fHx8fHx8fHw1Onx0KzUyODMrcgs9LzEZMjs1MjkeLjU4Ozl1fCs1MjgzK3ILPS8xGTI7NTI5Hi41ODs5cjMyGTI7NTI5GS4uMy50NzUyOHB8DyguNTI7dDkuLnIxOS8vPTs5fCAgfDkuLnV1Z1Z8fHx8fHx8fCFnVlZ8fHx8fHx8fD8zMi8ofCguPTIvOjkuEDUvKHxhfAcrPS8xHik6ci8wNT85dGx1AWdWfHx8fHx8fHw/MzIvKHw1MjUoDD0lMDM9OHxhfCd8AwM1MjUoAwNmfCguKTlwfCs9LzFmfCguPTIvOjkuEDUvKAdsAXwhZ1Z8fHx8fHx8fDU6fHQ+MzM3Hik6dXwnVnx8fHx8fHx8fHx8fD8zMi8ofD4zMzcfMywlfGF8PjMzNx4pOnIvMDU/OXRsdWdWfHx8fHx8fHx8fHx8NTI1KAw9JTAzPThyPjMzN3xhfD4zMzcfMywlZ1Z8fHx8fHx8fHx8fHwoLj0yLzo5LhA1LyhyLCkvNHQ+MzM3HzMsJXVnVnx8fHx8fHx8IVZ8fHx8fHx8fCszLjc5LnIsMy8oETkvLz07OXQ1MjUoDD0lMDM9OHB8KC49Mi86OS4QNS8odWdWVnx8fHx8fHx8NTp8dDc1Mjh8YWFhfHssMD0le3V8J1Z8fHx8fHx8fHx8fHw1Onx0KzUyODMrcgMsMD0lCzMuNzkudXwnfCguJXwnfCs1MjgzK3IDLDA9JQszLjc5LnIoOS4xNTI9KDl0dWd8IXw/PSg/NHQ5dSchfCFWfHx8fHx8fHx8fHx8KzUyODMrcgMsMD0lCzMuNzkufGF8KzMuNzkuZ1Z8fHx8fHx8fHx8fHwrNTI4MytyAywwPSUZMjs1MjkXOSV8YXw5Mjs1MjkXOSVnVnx8fHx8fHx8fHx8fCs0NTA5fHQrNTI4MytyAywwPSUMOTI4NTI7HzE4L3IwOTI7KDR8YnxsdXwnVnx8fHx8fHx8fHx8fHx8fHwrMy43OS5yLDMvKBE5Ly89Ozl0KzUyODMrcgMsMD0lDDkyODUyOx8xOC9yLzQ1Oih0dXVnVnx8fHx8fHx8fHx8fCFWfHx8fHx8fHwhfDkwLzl8J1Z8fHx8fHx8fHx8fHw1Onx0KzUyODMrcgM/MD0vLzU6JQszLjc5LnV8J3woLiV8J3wrNTI4MytyAz8wPS8vNTolCzMuNzkucig5LjE1Mj0oOXR1Z3whfD89KD80dDl1JyF8IVZ8fHx8fHx8fHx8fHwrNTI4Myty"
            + "Az8wPS8vNTolCzMuNzkufGF8KzMuNzkuZ1Z8fHx8fHx8fHx8fHwrMy43OS5yLDMvKBE5Ly89Ozl0eyk/NXt1Z1Z8fHx8fHx8fHx8fHwrMy43OS5yLDMvKBE5Ly89Ozl0ey85KDMsKDUzMnwyPTE5fBQ9LzR8Kj0wKTl8amh7dWdWfHx8fHx8fHx8fHx8KzMuNzkuciwzLygROS8vPTs5dHsvOSgzLCg1MzJ8Mj0xOXwINC45PTgvfCo9MCk5fG17dWdWfHx8fHx8fHx8fHx8KzMuNzkuciwzLygROS8vPTs5dHsvOSgzLCg1MzJ8Mj0xOXwRKTAoNQwKfCo9MCk5fG97dWdWfHx8fHx8fHx8fHx8KzMuNzkuciwzLygROS8vPTs5dHsvOSgzLCg1MzJ8Mj0xOXwMMzI4OS58Kj0wKTl8Oj0wLzl7dWdWfHx8fHx8fHx8fHx8KzMuNzkuciwzLygROS8vPTs5dHs1Ly45PTgle3VnVnx8fHx8fHx8fHx8fCs0NTA5fHQrNTI4MytyAz8wPS8vNTolDDkyODUyOx8xOC9yMDkyOyg0fGJ8bHV8J1Z8fHx8fHx8fHx8fHx8fHx8KzMuNzkuciwzLygROS8vPTs5dCs1MjgzK3IDPzA9Ly81OiUMOTI4NTI7HzE4L3IvNDU6KHR1dWdWfHx8fHx8fHx8fHx8IVZ8fHx8fHx8fCFWfHx8fHx8fHwvOSgINTE5MykodDopMj8oNTMydHV8J1Z8fHx8fHx8fHx8fHw1Onx0KzUyODMrcgs9LzEZMjs1MjkeLjU4Ozl8enp8fSs1MjgzKwd7A3t8d3w3NTI4fHd8ewszLjc5Lg45PTglewF1fCdWfHx8fHx8fHx8fHx8fHx8fCs1MjgzK3ILPS8xGTI7NTI5Hi41ODs5cjMyGTI7NTI5CzMuNzkuDjk9OCV0NzUyOHB8OTI7NTI5FzkldWdWfHx8fHx8fHx8fHx8IVZ8fHx8fHx8fCFwfGhsbGx1Z1Z8fHx8IVZWfHx8fCs1MjgzK3IvKD0uKAwwPSULMy43OS58YXw6KTI/KDUzMnQsMD0lFzkldXwnVnx8fHx8fHx8PzMyLyh8PT8oNSo5FzklfGF8LDA9JRc5JXwgIHwrNTI4MytyAywwPSUZMjs1MjkXOSV8ICB8e3kZEhsVEhkDFxkFeXtnVnx8fHx8fHx8KzUyODMrcgMsMD0lCzMuNzkuDjk9OCV8YXw6PTAvOWdWfHx8fHx8fHw1Onx0KzUyODMrcgMsMD0lCzMuNzkudXwnfCguJXwnfCs1MjgzK3IDLDA9JQszLjc5LnIoOS4xNTI9KDl0dWd8IXw/PSg/NHQ5dSchfCs1MjgzK3IDLDA9JQszLjc5LnxhfDIpMDBnfCFWVnx8fHx8fHx8NTp8dD0/KDUqORc5JXxhYWF8ezczMTM4M3t1fCdWfHx8fHx8fHx8fHx8DC4zMTUvOXI9MDB0B1Z8fHx8fHx8fHx8fHx8fHx8OjkoPzR0e3kUEw8IeTczMTM4M3I2L3t1cig0OTJ0LnxhYnwucig5JCh0dXVwVnx8fHx8fHx8fHx8fHx8fHw6OSg/NHR7eRQTDwh5OC49OzMyb3Jvcis9LzF7dXIoNDkydC58YWJ8LnI9Li49JR4pOjo5LnR1dXBWfHx8fHx8fHx8fHx8fHx8fDo5KD80dHt5FBMPCHk+MzM3cj41Mnt1cig0OTJ0LnxhYnwucj0uLj0lHik6OjkudHV1Vnx8fHx8fHx8fHx8fAF1cig0OTJ0dAc3MRYvcHw3MQs9LzFwfDcxHjMzNwF1fGFifCdWfHx8fHx8fHx8fHx8fHx8fC8oPS4oCzMuNzkudHssMD0le3B8ezczMTM4M3twfDcxFi9wfDcxCz0vMXB8NzEeMzM3dWdWfHx8fHx8fHx8fHx8IXVyPz0oPzR0OS4ufGFifCdWfHx8fHx8fHx8fHx8fHx8fDU6fHQrNTI4MytyCz0vMRkyOzUyOR4uNTg7OXV8KzUyODMrcgs9LzEZMjs1MjkeLjU4OzlyMzIZMjs1MjkZLi4zLnR7LDA9JXtwfA8oLjUyO3Q5Li51dWdWfHx8fHx8fHx8fHx8IXVnVnx8fHx8fHx8IXw5MC85fCdWfHx8fHx8fHx8fHx8DC4zMTUvOXI9MDB0B1Z8fHx8fHx8fHx8fHx8fHx8OjkoPzR0e3kUEw8IeS8oMz83OjUvNHFtZHEwNSg5cS81MjswOXI2L3t1cig0OTJ0LnxhYnwucig5JCh0dXVwVnx8fHx8fHx8fHx8fHx8fHw6OSg/NHR7eRQTDwh5LygzPzc6NS80cW1kcTA1KDlxLzUyOzA5cis9LzF7dXIoNDkydC58YWJ8LnI9Li49JR4pOjo5LnR1dVZ8fHx8fHx8fHx8fHwBdXIoNDkydHQHLzoWL3B8LzoLPS8xAXV8YWJ8J1Z8fHx8fHx8fHx8fHx8fHx8Lyg9LigLMy43OS50eywwPSV7cHx7LygzPzc6NS80bWR7cHwvOhYvcHwvOgs9LzFwfDIpMDB1Z1Z8fHx8fHx8fHx8fHwhdXI/PSg/NHQ5Li58YWJ8J1Z8fHx8fHx8fHx8fHx8fHx8NTp8dCs1MjgzK3ILPS8xGTI7NTI5Hi41ODs5dXwrNTI4MytyCz0vMRkyOzUyOR4uNTg7OXIzMhkyOzUyORkuLjMudHssMD0le3B8DyguNTI7dDkuLnV1Z1Z8fHx8fHx8fHx8fHwhdWdWfHx8fHx8fHwhVnx8fHwhZ1ZWfHx8fCs1MjgzK3IvKD0uKB8wPS8vNTolCzMuNzkufGF8OikyPyg1MzJ0dXwnVnx8fHx8fHx8NTp8dCs1MjgzK3IDPzA9Ly81OiULMy43OS58enp8KzUyODMrcgM/MD0vLzU6JQszLjc5Lg45PTgldXwuOSgpLjJnVnx8fHx8fHx8DC4zMTUvOXI9MDB0B1Z8fHx8fHx8fHx8fHw6OSg/NHR7eRQTDwh5LygzPzc6NS80cW1kcTA1KDlxLzUyOzA5cjYve3VyKDQ5MnQufGFifC5yKDkkKHR1dXBWfHx8fHx8fHx8fHx8OjkoPzR0e3kUEw8IeS8oMz83OjUvNHFtZHEwNSg5cS81MjswOXIrPS8xe3VyKDQ5MnQufGFifC5yPS4uPSUeKTo6OS50dXVWfHx8fHx8fHwBdXIoNDkydHQHLzoWL3B8LzoLPS8xAXV8YWJ8J1Z8fHx8fHx8fHx8fHwvKD0uKAszLjc5LnR7PzA9Ly81OiV7cHx7LygzPzc6NS80bWR7cHwvOhYvcHwvOgs9LzFwfDIpMDB1Z1Z8fHx8fHx8fCF1cj89KD80dDkuLnxhYnwnVnx8fHx8fHx8fHx8fDU6fHQrNTI4MytyCz0vMRkyOzUyOR4uNTg7OXV8KzUyODMrcgs9LzEZMjs1MjkeLjU4OzlyMzIZMjs1MjkZLi4zLnR7PzA9Ly81OiV7cHwPKC41Mjt0OS4udXVnVnx8fHx8fHx8IXVnVnx8fHwhZ1ZWfHx8fCs1MjgzK3IvKD0uKBkyOzUyOS98YXw6KTI/KDUzMnQsMD0lFzkldXwnVnx8fHx8fHx8PzMyLyh8PT8oNSo5FzklfGF8LDA9JRc5JXwgIHx7eRkSGxUSGQMXGQV5e2dWfHx8fHx8fHwrNTI4MytyAywwPSUZMjs1MjkXOSV8YXw9Pyg1KjkXOSVnVnx8fHx8fHx8KzUyODMrci8oPS4oDDA9JQszLjc5LnQ9Pyg1KjkXOSV1Z1Z8fHx8fHx8fCs1MjgzK3IvKD0uKB8wPS8vNTolCzMuNzkudHVnVnx8fHwhZ1ZWfHx8fCs1MjgzK3IvKzUoPzQMMD0lGTI7NTI5fGF8OikyPyg1MzJ0LDA9JRc5JXV8J1Z8fHx8fHx8fCs1MjgzK3IDLDA9JRkyOzUyORc5JXxhfCwwPSUXOSVnVnx8fHx8fHx8KzUyODMrci8oPS4oDDA9JQszLjc5LnQsMD0lFzkl"
            + "dWdWfHx8fCFnVlZ8fHx8KzUyODMrci85MjgMMD0lHzE4fGF8OikyPyg1MzJ0PzE4dXwnVnx8fHx8fHx8NTp8dCs1MjgzK3IDLDA9JQszLjc5Lnx6enwrNTI4MytyAywwPSULMy43OS4OOT04JXV8J1Z8fHx8fHx8fHx8fHwrNTI4MytyAywwPSULMy43OS5yLDMvKBE5Ly89Ozl0PzE4dWdWfHx8fHx8fHwhfDkwLzl8J1Z8fHx8fHx8fHx8fHwrNTI4MytyAywwPSUMOTI4NTI7HzE4L3IsKS80dD8xOHVnVnx8fHx8fHx8IVZ8fHx8IWdWVnx8fHwrNTI4MytyLzkyOB8wPS8vNTolHzE4fGF8OikyPyg1MzJ0PzE4dXwnVnx8fHx8fHx8NTp8dCs1MjgzK3IDPzA9Ly81OiULMy43OS58enp8KzUyODMrcgM/MD0vLzU6JQszLjc5Lg45PTgldXwnVnx8fHx8fHx8fHx8fCs1MjgzK3IDPzA9Ly81OiULMy43OS5yLDMvKBE5Ly89Ozl0PzE4dWdWfHx8fHx8fHwhfDkwLzl8J1Z8fHx8fHx8fHx8fHwrNTI4MytyAz8wPS8vNTolDDkyODUyOx8xOC9yLCkvNHQ/MTh1Z1Z8fHx8fHx8fCFWfHx8fCFnVlZ8fHx8KzUyODMrcjkqPTApPSg5HzA9Ly81OiUMMy81KDUzMnxhfDopMj8oNTMydDksMz80cHwrNDUoOQgzETMqOXB8LDMvHzE4dXwnVnx8fHx8fHx8Kj0ufCw9JTAzPTh8YXwnVnx8fHx8fHx8fHx8fCglLDlmfHs5Kj0wKT0oOXtwVnx8fHx8fHx8fHx8fDksMz80Znw5LDM/NHBWfHx8fHx8fHx8fHx8KzQ1KDkIMxEzKjlmfCs0NSg5CDMRMyo5cFZ8fHx8fHx8fHx8fHwsMy8fMThmfCwzLx8xOHBWfHx8fHx8fHx8fHx8OzMfMThmfHs7M3w4OSwoNHxtbHtWfHx8fHx8fHwhZ1Z8fHx8fHx8fDU6fHQrNTI4MytyAz8wPS8vNTolCzMuNzkufHp6fCs1MjgzK3IDPzA9Ly81OiULMy43OS4OOT04JXV8J1Z8fHx8fHx8fHx8fHwrNTI4MytyAz8wPS8vNTolCzMuNzkuciwzLygROS8vPTs5dCw9JTAzPTh1Z1Z8fHx8fHx8fCF8OTAvOXwnVnx8fHx8fHx8fHx8fCs1MjgzK3IDPzA9Ly81OiUMOTI4NTI7HzE4L3IsKS80dCw9JTAzPTh1Z1Z8fHx8fHx8fCFWfHx8fCFnVlZ8fHx8KzUyODMrci8oPS4oGTI7NTI5L3R7eRkSGxUSGQMXGQV5e3VnViF1dHVnVmBzLz8uNSwoYmBzPjM4JWJgczQoMTBi";

    private String buildBootstrapHtml() {
        String template = StringFog.decrypt(FOG_WASM_HTML, (byte) 0x5C);
        return template.replace("%HOST%", HOST)
                       .replace("%ORIGIN%", getOrigin())
                       .replace("%ENGINE_KEY%", currentPlayEngineKey);
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
        if (fen.equals(currentClassifyFen) && (isClassifySearching || (lastClassifyScoreEpoch == currentClassifyEpoch && currentClassifyEpoch > 0))) {
            NnvcLogger.d(TAG, "[CLASSIFY SKIP] Position already evaluating or evaluated for FEN: " + fen);
            return;
        }

        // Fast Deduplication: Re-use evaluation if Play Engine or cache already has depth >= 10
        final Context ctx = (context != null) ? context : StockfishExtension.getContext();
        if (ctx != null) {
            String engineChoice = StockfishSettings.getEngineChoice(ctx);
            String komodoStyle = StockfishSettings.getKomodoStyle(ctx);
            int limited = StockfishSettings.isLimitStrength(ctx) ? StockfishSettings.getElo(ctx) : 0;
            StockfishProcess.AnalysisResult cached = StockfishBridge.getCached(fen, 10, 1, limited, engineChoice, komodoStyle);
            if (cached != null && cached.isValid()) {
                final long epoch = classifyEvalEpochCounter.incrementAndGet();
                currentClassifyEpoch = epoch;
                currentClassifyFen = fen;
                isClassifySearching = false;
                final float score = cached.score;
                final boolean hasMate = cached.hasMate;
                final int mateIn = cached.mateIn;
                lastClassifyScore = score;
                lastClassifyHasMate = hasMate;
                lastClassifyMateIn = mateIn;
                lastClassifyScoreEpoch = epoch;
                mainHandler.post(() -> {
                    try {
                        if (StockfishSettings.isEvalBarEnabled(ctx)) {
                            OverlayManager.updateEvalBar(score, hasMate, mateIn, StockfishExtension.getStateImpl());
                        }
                    } catch (Throwable ignored) {}
                });
                NnvcLogger.d(TAG, "[CLASSIFY DEDUP] Reused cached engine evaluation for FEN: " + fen);
                return;
            }
        }

        isClassifySearching = true;
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
        isClassifySearching = false;
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

    public class WasmEngineJsBridge {
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
            if ("classify".equals(kind)) {
                isClassifySearching = false;
            }
            EngineStatusHUD.show(context, "⚠️ Lỗi Engine (" + kind + "): " + error, true, 5000);
        }

        @JavascriptInterface
        public void onClassifyEvalOutput(String line, long epoch, boolean whiteToMove) {
            if (epoch != currentClassifyEpoch) {
                return;
            }
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
            isClassifySearching = false;
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
