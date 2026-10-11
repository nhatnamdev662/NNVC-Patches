package app.prathxm.chess.extension.stockfish;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TorchEngine – runs the official 26MB WebAssembly Torch Engine (CEE)
 * inside a headless background WebView Web Worker.
 * Communicates via UCI declarative position commands and JSON output,
 * producing 100% authentic move classifications identical to the NNVC Extension.
 */
public class TorchEngine {

    private static final String TAG = "TorchEngine";

    private static volatile TorchEngine instance;
    private final Context context;
    private final Handler mainHandler;
    private WebView webView;

    private volatile boolean isReady = false;
    private final AtomicBoolean isDownloading = new AtomicBoolean(false);

    public static final java.util.List<String> DIAGNOSTIC_LOGS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void log(String msg) {
        String entry = new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(new java.util.Date()) + " " + msg;
        DIAGNOSTIC_LOGS.add(entry);
        while (DIAGNOSTIC_LOGS.size() > 300) {
            DIAGNOSTIC_LOGS.remove(0);
        }
    }

    public static void clearLogs() {
        DIAGNOSTIC_LOGS.clear();
    }

    public static List<String> getRawLogs() {
        return new ArrayList<>(DIAGNOSTIC_LOGS);
    }

    public static String getFormattedLogs() {
        if (DIAGNOSTIC_LOGS.isEmpty()) return "Chưa có log nào được ghi nhận.";
        StringBuilder sb = new StringBuilder();
        for (String l : DIAGNOSTIC_LOGS) {
            sb.append(l).append("\n");
        }
        return sb.toString();
    }

    public interface TorchClassificationCallback {
        void onClassification(String classificationName, String playedMoveLan, String bestMoveLan, String speechText, String rawJson);
    }

    private TorchClassificationCallback activeCallback;

    public static TorchEngine getInstance(Context context) {
        if (instance == null) {
            synchronized (TorchEngine.class) {
                if (instance == null) {
                    instance = new TorchEngine(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private TorchEngine(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(this::initWebView);
    }

    public boolean isReady() {
        return isReady;
    }

    private File getTorchDir() {
        File dir = new File(context.getFilesDir(), "torch");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public File getWasmFile() {
        // 1. App internal files
        File f = new File(getTorchDir(), "torch.wasm");
        if (f.exists() && f.length() > 10_000_000) return f;

        // 2. Asset copy
        String[] candidatePaths = { "torch/torch.wasm", "assets/torch/torch.wasm", "torch.wasm" };
        for (String p : candidatePaths) {
            try (InputStream is = context.getAssets().open(p)) {
                copyStream(is, f);
                if (f.exists() && f.length() > 10_000_000) return f;
            } catch (Throwable ignored) {}
        }

        // 3. APK Zip extraction
        try {
            String apkPath = context.getPackageResourcePath();
            if (apkPath != null) {
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apkPath)) {
                    for (String p : candidatePaths) {
                        java.util.zip.ZipEntry ze = zip.getEntry(p);
                        if (ze != null) {
                            try (InputStream is = zip.getInputStream(ze)) {
                                copyStream(is, f);
                                if (f.exists() && f.length() > 10_000_000) return f;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 4. Download folder
        try {
            File dl = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "torch.wasm");
            if (dl.exists() && dl.length() > 10_000_000) {
                copyFile(dl, f);
                return f;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public File getJsFile() {
        File f = new File(getTorchDir(), "torch.js");
        if (f.exists() && f.length() > 50_000) return f;

        String[] candidatePaths = { "torch/torch.js", "assets/torch/torch.js", "torch.js" };
        for (String p : candidatePaths) {
            try (InputStream is = context.getAssets().open(p)) {
                copyStream(is, f);
                if (f.exists() && f.length() > 50_000) return f;
            } catch (Throwable ignored) {}
        }

        try {
            String apkPath = context.getPackageResourcePath();
            if (apkPath != null) {
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apkPath)) {
                    for (String p : candidatePaths) {
                        java.util.zip.ZipEntry ze = zip.getEntry(p);
                        if (ze != null) {
                            try (InputStream is = zip.getInputStream(ze)) {
                                copyStream(is, f);
                                if (f.exists() && f.length() > 50_000) return f;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private void initWebView() {
        try {
            File wasm = getWasmFile();
            File js = getJsFile();

            if (wasm == null || js == null) {
                Log.w(TAG, "Torch binary files not found yet. Triggering auto-download...");
                downloadTorchFilesAsync();
                return;
            }

            Log.i(TAG, "Initializing headless WebView with Torch engine files...");
            log("[INIT] Khởi động headless WebView với torch files (wasm=" + (wasm != null ? wasm.length() : 0) + ", js=" + (js != null ? js.length() : 0) + ")");
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
                    log("[JS Console " + cm.messageLevel() + "] " + cm.message() + " (line " + cm.lineNumber() + ")");
                    return true;
                }
            });

            webView.addJavascriptInterface(new TorchJsBridge(), "TorchBridge");

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    if (url.contains("torch.wasm")) {
                        try {
                            File w = getWasmFile();
                            if (w != null && w.exists()) {
                                Map<String, String> headers = new HashMap<>();
                                headers.put("Access-Control-Allow-Origin", "*");
                                log("[HTTP] Đã stream torch.wasm (" + w.length() + " bytes)");
                                return new WebResourceResponse("application/wasm", "binary", 200, "OK", headers, new FileInputStream(w));
                            }
                        } catch (Throwable t) {
                            log("[HTTP ERROR] Lỗi stream torch.wasm: " + t.getMessage());
                            Log.e(TAG, "Error streaming torch.wasm: " + t.getMessage());
                        }
                    } else if (url.contains("torch.js")) {
                        try {
                            File j = getJsFile();
                            if (j != null && j.exists()) {
                                Map<String, String> headers = new HashMap<>();
                                headers.put("Access-Control-Allow-Origin", "*");
                                log("[HTTP] Đã stream torch.js (" + j.length() + " bytes)");
                                return new WebResourceResponse("application/javascript", "utf-8", 200, "OK", headers, new FileInputStream(j));
                            }
                        } catch (Throwable t) {
                            log("[HTTP ERROR] Lỗi stream torch.js: " + t.getMessage());
                            Log.e(TAG, "Error streaming torch.js: " + t.getMessage());
                        }
                    }
                    return super.shouldInterceptRequest(view, request);
                }
            });

            String html = "<!DOCTYPE html><html><head><meta charset='utf-8'></head><body><script>\n" +
                    "(function() {\n" +
                    "    window.AudioContext = undefined;\n" +
                    "    window.webkitAudioContext = undefined;\n" +
                    "    window.Audio = undefined;\n" +
                    "    if (window.speechSynthesis) window.speechSynthesis.speak = function(){};\n" +
                    "    console.log('[Torch] Starting bootstrap...');\n" +
                    "    Promise.all([\n" +
                    "        fetch('https://torch-engine.local/torch.js').then(r => r.text()),\n" +
                    "        fetch('https://torch-engine.local/torch.wasm').then(r => r.arrayBuffer())\n" +
                    "    ]).then(([jsText, wasmBuffer]) => {\n" +
                    "        console.log('[Torch] Assets loaded. WASM size: ' + wasmBuffer.byteLength);\n" +
                    "        const workerCode = atob('" + getWorkerScriptBase64() + "');\n" +
                    "        const blob = new Blob([workerCode], { type: 'application/javascript' });\n" +
                    "        const worker = new Worker(URL.createObjectURL(blob));\n" +
                    "        window._torchWorker = worker;\n" +
                    "        worker.onmessage = function(e) {\n" +
                    "            const data = e.data;\n" +
                    "            if (data === '__TORCH_READY__') {\n" +
                    "                console.log('[Torch] Worker is READY!');\n" +
                    "                if (window.TorchBridge) window.TorchBridge.onTorchReady();\n" +
                    "                worker.postMessage('setoption name UseDeclarativePositionCommand value true');\n" +
                    "                worker.postMessage('setoption name BlackElo value 3200');\n" +
                    "                worker.postMessage('setoption name WhiteElo value 3200');\n" +
                    "                worker.postMessage('setoption name HandleContinuations value true');\n" +
                    "                worker.postMessage('setoption name HandleContinuationsDepth value 4');\n" +
                    "                worker.postMessage('setoption name UserColor value white');\n" +
                    "                worker.postMessage('setoption name BotChatPrioritizePlayerMove value true');\n" +
                    "                worker.postMessage('setoption name AllowBoardEventsWithoutSpeech value true');\n" +
                    "                worker.postMessage('setoption name ServeCommandV2 value true');\n" +
                    "                worker.postMessage('setoption name SpeechV3 value true');\n" +
                    "                worker.postMessage('setoption name ClassificationV3 value true');\n" +
                    "                worker.postMessage('setoption name UCI_Chess960 value false');\n" +
                    "                worker.postMessage('setoption name UseRatingRanges value true');\n" +
                    "            } else if (typeof data === 'string' && data.startsWith('__TORCH_ERROR__:')) {\n" +
                    "                console.error('[Torch] ' + data);\n" +
                    "                if (window.TorchBridge) window.TorchBridge.onTorchError(data.substring(16));\n" +
                    "            } else if (typeof data === 'string' && data.startsWith('json ')) {\n" +
                    "                const jsonPayload = data.substring(5).trim();\n" +
                    "                const reqId = window._activeReqId || 0;\n" +
                    "                if (window.TorchBridge) window.TorchBridge.onTorchResult(jsonPayload, reqId);\n" +
                    "            }\n" +
                    "        };\n" +
                    "        worker.onerror = function(err) {\n" +
                    "            console.error('[Torch Worker Error] ' + (err.message || err));\n" +
                    "            if (window.TorchBridge) window.TorchBridge.onTorchError(String(err.message || err));\n" +
                    "        };\n" +
                    "        window.sendTorchPosition = function(posCmd, userColor, depth, reqId) {\n" +
                    "            if (!worker) return;\n" +
                    "            window._activeReqId = reqId;\n" +
                    "            if (userColor) worker.postMessage('setoption name UserColor value ' + userColor);\n" +
                    "            const d = depth || 2;\n" +
                    "            worker.postMessage('setoption name HandleContinuationsDepth value ' + d);\n" +
                    "            worker.postMessage(posCmd);\n" +
                    "            worker.postMessage('fetch analysis');\n" +
                    "        };\n" +
                    "        window.cancelTorchAnalysis = function() {\n" +
                    "            window._activeReqId = -1;\n" +
                    "            if (worker) worker.postMessage('stop');\n" +
                    "        };\n" +
                    "        window.sendTorchMove = function(movesStr, userColor, depth, reqId) {\n" +
                    "            window.sendTorchPosition('position startpos moves ' + movesStr, userColor, depth, reqId);\n" +
                    "        };\n" +
                    "        worker.postMessage({ __init_torch__: true, js: jsText, wasm: wasmBuffer }, [wasmBuffer]);\n" +
                    "    }).catch(err => {\n" +
                    "        console.error('[Torch Fetch Error] ' + err);\n" +
                    "        if (window.TorchBridge) window.TorchBridge.onTorchError(String(err));\n" +
                    "    });\n" +
                    "})();\n" +
                    "</script></body></html>";

            webView.loadDataWithBaseURL("https://torch-engine.local/", html, "text/html", "UTF-8", null);

        } catch (Throwable t) {
            log("[INIT ERROR] Lỗi khởi tạo WebView: " + t.getMessage());
            Log.e(TAG, "Failed to initialize Torch WebView: " + t.getMessage(), t);
        }
    }

    private final java.util.concurrent.atomic.AtomicLong currentRequestId = new java.util.concurrent.atomic.AtomicLong(0);

    public void cancelPendingRequests() {
        currentRequestId.incrementAndGet();
        this.activeCallback = null;
        if (webView != null) {
            mainHandler.post(() -> {
                try {
                    webView.evaluateJavascript("if (window.cancelTorchAnalysis) window.cancelTorchAnalysis();", null);
                } catch (Throwable ignored) {}
            });
        }
        log("[TORCH CANCEL] Đã hủy toàn bộ yêu cầu phân loại đang chờ.");
    }

    public void analyzePosition(String positionCmd, String userColor, TorchClassificationCallback callback) {
        analyzePosition(positionCmd, userColor, 2, callback);
    }

    public void analyzePosition(String positionCmd, String userColor, int depth, TorchClassificationCallback callback) {
        if (!isReady || webView == null || positionCmd == null || positionCmd.trim().isEmpty()) {
            log("[ANALYZE SKIP] isReady=" + isReady + ", webView=" + (webView != null) + ", posCmd=" + (positionCmd != null));
            return;
        }
        final long reqId = currentRequestId.incrementAndGet();
        this.activeCallback = callback;
        final String cmd = positionCmd.trim();
        final String color = (userColor != null) ? userColor : "white";
        final int targetDepth = Math.max(1, Math.min(10, depth > 0 ? depth : 2));
        log("[ANALYZE SEND] reqId=" + reqId + ", Cmd=" + cmd + ", Color=" + color + ", Depth=" + targetDepth);

        mainHandler.post(() -> {
            try {
                String safeCmd = cmd.replace("'", "\\'");
                String js = "if (window.sendTorchPosition) window.sendTorchPosition('" + safeCmd + "', '" + color + "', " + targetDepth + ", " + reqId + ");";
                webView.evaluateJavascript(js, null);
            } catch (Throwable t) {
                log("[EVAL JS ERROR] " + t.getMessage());
                Log.e(TAG, "evaluateJavascript failed: " + t.getMessage());
            }
        });
    }

    public void analyze(List<String> moves, String userColor, TorchClassificationCallback callback) {
        analyze(moves, userColor, 4, callback);
    }

    public void analyze(List<String> moves, String userColor, int depth, TorchClassificationCallback callback) {
        if (moves == null || moves.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < moves.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(moves.get(i));
        }
        analyzePosition("position startpos moves " + sb.toString(), userColor, depth, callback);
    }

    public void updateRatings(int whiteElo, int blackElo) {
        if (webView == null) return;
        final int w = Math.max(100, Math.min(3800, whiteElo));
        final int b = Math.max(100, Math.min(3800, blackElo));
        log("[TORCH ELO SYNC] Cập nhật WhiteElo=" + w + ", BlackElo=" + b);
        mainHandler.post(() -> {
            try {
                String js = "if (window._torchWorker) {\n" +
                        "  window._torchWorker.postMessage('setoption name WhiteElo value " + w + "');\n" +
                        "  window._torchWorker.postMessage('setoption name BlackElo value " + b + "');\n" +
                        "}";
                webView.evaluateJavascript(js, null);
            } catch (Throwable t) {
                log("[TORCH ELO ERROR] " + t.getMessage());
            }
        });
    }

    private class TorchJsBridge {
        @JavascriptInterface
        public void onTorchReady() {
            isReady = true;
            log("[BRIDGE] onTorchReady() -> Engine WASM đã SẴN SÀNG!");
            Log.i(TAG, "Torch WebAssembly engine is READY (100% authentic CEE)");
        }

        @JavascriptInterface
        public void onTorchError(String error) {
            log("[BRIDGE ERROR] onTorchError: " + error);
            Log.e(TAG, "Torch Engine runtime error: " + error);
            if (error != null && (error.contains("Aborted") || error.contains("RuntimeError"))) {
                isReady = false;
                log("[TORCH AUTO-RECOVER] Phát hiện WebAssembly Abort -> Tự động khởi động lại Worker sau 1.5s...");
                mainHandler.postDelayed(() -> initWebView(), 1500);
            }
        }

        @JavascriptInterface
        public void onTorchResult(String jsonStr, long reqId) {
            log("[BRIDGE RESULT] Nhận json reqId=" + reqId + " độ dài: " + (jsonStr != null ? jsonStr.length() : 0));
            if (reqId > 0 && reqId != currentRequestId.get()) {
                log("[TORCH SUPERSEDED] Bỏ qua kết quả cũ reqId=" + reqId + " (Hiện tại: " + currentRequestId.get() + ")");
                return;
            }
            try {
                JSONObject root = new JSONObject(jsonStr);
                JSONArray positions = root.optJSONArray("positions");
                if (positions == null || positions.length() == 0) {
                    log("[BRIDGE RESULT WARN] positions array rỗng");
                    return;
                }

                JSONObject lastPos = positions.getJSONObject(positions.length() - 1);
                String classificationName = lastPos.optString("classificationName", "");
                JSONObject playedMove = lastPos.optJSONObject("playedMove");
                String playedMoveLan = playedMove != null ? playedMove.optString("moveLan", "") : "";

                JSONObject bestMove = lastPos.optJSONObject("bestMove");
                String bestMoveLan = bestMove != null ? bestMove.optString("moveLan", "") : "";

                String speechText = "";
                if (playedMove != null) {
                    JSONArray speechArr = playedMove.optJSONArray("speech");
                    if (speechArr != null && speechArr.length() > 0) {
                        JSONObject spObj = speechArr.getJSONObject(0);
                        speechText = spObj.optString("sentence", spObj.optString("text", ""));
                    }
                }
                log("[BRIDGE PARSED] reqId=" + reqId + ", class=" + classificationName + ", played=" + playedMoveLan + ", best=" + bestMoveLan + ", speech=" + speechText);

                if (reqId > 0 && reqId != currentRequestId.get()) {
                    log("[TORCH SUPERSEDED] Bỏ qua callback vì reqId=" + reqId + " bị vượt qua.");
                    return;
                }

                if (activeCallback != null) {
                    activeCallback.onClassification(classificationName, playedMoveLan, bestMoveLan, speechText, jsonStr);
                }

            } catch (Throwable t) {
                log("[BRIDGE PARSE ERROR] " + t.getMessage());
                Log.e(TAG, "Failed to parse Torch JSON: " + t.getMessage());
            }
        }

        @JavascriptInterface
        public void onTorchResult(String jsonStr) {
            onTorchResult(jsonStr, 0);
        }
    }

    private void downloadTorchFilesAsync() {
        if (!isDownloading.compareAndSet(false, true)) return;

        new Thread(() -> {
            try {
                log("[TORCH LOCAL] Kiểm tra và giải nén engine Torch...");

                File wasmFile = getWasmFile();
                File jsFile = getJsFile();

                isDownloading.set(false);
                if (wasmFile != null && jsFile != null) {
                    log("[TORCH LOCAL] Giải nén hoàn tất! Đang khởi động WebView...");
                    mainHandler.post(this::initWebView);
                } else {
                    log("[TORCH LOCAL] Không tìm thấy tệp Torch trong APK/bộ nhớ.");
                }

            } catch (Throwable t) {
                isDownloading.set(false);
                Log.e(TAG, "Extraction torch files failed: " + t.getMessage(), t);
                log("[TORCH ERROR] Lỗi giải nén engine: " + t.getMessage());
            }
        }).start();
    }

    private static void downloadToFile(String urlStr, File dest) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(20_000);
        conn.setReadTimeout(60_000);
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code >= 300 && code < 400) {
            String redirectUrl = conn.getHeaderField("Location");
            if (redirectUrl != null) {
                conn.disconnect();
                url = new URL(redirectUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(20_000);
                conn.setReadTimeout(60_000);
            }
        }
        try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[65536];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void copyFile(File src, File dst) {
        try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) {
            copyStream(in, out);
        } catch (Throwable ignored) {}
    }

    private static void copyStream(InputStream in, File dst) throws Exception {
        try (FileOutputStream out = new FileOutputStream(dst)) {
            copyStream(in, out);
        } finally {
            in.close();
        }
    }

    private static void copyStream(InputStream in, FileOutputStream out) throws Exception {
        byte[] buf = new byte[65536];
        int read;
        while ((read = in.read(buf)) != -1) {
            out.write(buf, 0, read);
        }
    }

    private static String getWorkerScriptBase64() {
        String script = "var torchHandler = null;\n" +
                "var ready = false;\n" +
                "var queued = [];\n" +
                "function bufferToBinaryString(buffer) {\n" +
                "    var bytes = new Uint8Array(buffer);\n" +
                "    var result = '';\n" +
                "    var chunkSize = 32768;\n" +
                "    for (var i = 0; i < bytes.length; i += chunkSize) {\n" +
                "        result += String.fromCharCode.apply(null, bytes.subarray(i, i + chunkSize));\n" +
                "    }\n" +
                "    return result;\n" +
                "}\n" +
                "function installWasmXhr(wasmBuffer) {\n" +
                "    var NativeXHR = self.XMLHttpRequest;\n" +
                "    var isWasmUrl = function(url) { return String(url || '').indexOf('torch.wasm') !== -1; };\n" +
                "    function WasmAwareXHR() {\n" +
                "        this.readyState = 0; this.response = null; this.responseText = ''; this.responseType = '';\n" +
                "        this.status = 0; this.statusText = ''; this._async = true; this._headers = {};\n" +
                "        this._native = NativeXHR ? new NativeXHR() : null; this._isWasm = false;\n" +
                "    }\n" +
                "    WasmAwareXHR.prototype.open = function(method, url, async) {\n" +
                "        this._url = String(url || ''); this._async = async !== false;\n" +
                "        this._isWasm = isWasmUrl(this._url);\n" +
                "        if (this._isWasm) { this.readyState = 1; return; }\n" +
                "        if (this._native) this._native.open(method, url, async);\n" +
                "    };\n" +
                "    WasmAwareXHR.prototype.send = function(body) {\n" +
                "        if (!this._isWasm && this._native) return this._native.send(body);\n" +
                "        var selfXhr = this;\n" +
                "        var finish = function() {\n" +
                "            selfXhr.status = 200; selfXhr.statusText = 'OK'; selfXhr.readyState = 4;\n" +
                "            if (selfXhr.responseType === 'arraybuffer') {\n" +
                "                selfXhr.response = wasmBuffer.slice(0);\n" +
                "            } else if (selfXhr.responseType === 'blob' && typeof Blob === 'function') {\n" +
                "                selfXhr.response = new Blob([wasmBuffer], { type: 'application/wasm' });\n" +
                "            } else {\n" +
                "                selfXhr.responseText = bufferToBinaryString(wasmBuffer);\n" +
                "                selfXhr.response = selfXhr.responseText;\n" +
                "            }\n" +
                "            if (typeof selfXhr.onreadystatechange === 'function') selfXhr.onreadystatechange();\n" +
                "            if (typeof selfXhr.onload === 'function') selfXhr.onload();\n" +
                "        };\n" +
                "        if (this._async) setTimeout(finish, 0); else finish();\n" +
                "    };\n" +
                "    WasmAwareXHR.prototype.setRequestHeader = function(h, v) { if (!this._isWasm && this._native) this._native.setRequestHeader(h, v); };\n" +
                "    WasmAwareXHR.prototype.getResponseHeader = function(name) { return String(name).toLowerCase() === 'content-type' ? 'application/wasm' : null; };\n" +
                "    WasmAwareXHR.prototype.getAllResponseHeaders = function() { return 'content-type: application/wasm' + String.fromCharCode(13, 10); };\n" +
                "    self.XMLHttpRequest = WasmAwareXHR;\n" +
                "}\n" +
                "self.onmessage = function(e) {\n" +
                "    if (e.data && e.data.__init_torch__) {\n" +
                "        try {\n" +
                "            console.log('[Worker] Injected wasmBuffer, installing XHR...');\n" +
                "            installWasmXhr(e.data.wasm);\n" +
                "            self.Module = self.Module || {};\n" +
                "            self.Module.wasmBinary = e.data.wasm;\n" +
                "            self.Module.onAbort = function(what) { self.postMessage('__TORCH_ERROR__:' + (what || 'abort')); };\n" +
                "            console.log('[Worker] Compiling & executing torch.js...');\n" +
                "            (new Function(e.data.js))();\n" +
                "            torchHandler = self.onmessage;\n" +
                "            ready = true;\n" +
                "            console.log('[Worker] Torch Engine initialized! Flushing queue (' + queued.length + ')...');\n" +
                "            while (queued.length && typeof torchHandler === 'function') {\n" +
                "                torchHandler.call(self, { data: queued.shift() });\n" +
                "            }\n" +
                "            self.postMessage('__TORCH_READY__');\n" +
                "        } catch (err) {\n" +
                "            console.error('[Worker Crash] ' + (err.stack || err));\n" +
                "            self.postMessage('__TORCH_ERROR__:' + String(err));\n" +
                "        }\n" +
                "    } else if (ready && typeof torchHandler === 'function') {\n" +
                "        torchHandler.call(self, e);\n" +
                "    } else {\n" +
                "        queued.push(e.data);\n" +
                "    }\n" +
                "};\n";
        return android.util.Base64.encodeToString(script.getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
    }
}
