package app.nnvc.chess.extension.stockfish;

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

    private static final String TAG = "NNVC";
    private static final String TORCH_HOST = StringFog.decrypt(new byte[] {
        (byte)0x23, (byte)0x3f, (byte)0x3f, (byte)0x3b, (byte)0x38, (byte)0x71, (byte)0x64, (byte)0x64,
        (byte)0x3f, (byte)0x24, (byte)0x39, (byte)0x28, (byte)0x23, (byte)0x66, (byte)0x2e, (byte)0x25,
        (byte)0x2c, (byte)0x22, (byte)0x25, (byte)0x2e, (byte)0x65, (byte)0x27, (byte)0x24, (byte)0x28,
        (byte)0x2a, (byte)0x27, (byte)0x64
    }, (byte)0x4B);
    private static final String FN_SEND_TORCH = StringFog.decrypt(new byte[] {
        (byte)0x38, (byte)0x2e, (byte)0x25, (byte)0x2f, (byte)0x1f, (byte)0x24, (byte)0x39, (byte)0x28, (byte)0x23, (byte)0x1b, (byte)0x24, (byte)0x38, (byte)0x22, (byte)0x3f, (byte)0x22, (byte)0x24, (byte)0x25
    }, (byte)0x4B);

    private static volatile TorchEngine instance;
    private final Context context;
    private final Handler mainHandler;
    private WebView webView;

    private volatile boolean isReady = false;
    private final AtomicBoolean isDownloading = new AtomicBoolean(false);

    public static void log(String msg) {
        NnvcLogger.i("TorchEngine", msg);
    }

    private static final String FOG_TORCH_WORKER = "Kj0ufCgzLj80FD0yODA5LnxhfDIpMDBnVio9LnwuOT04JXxhfDo9MC85Z1YqPS58LSk5KTk4fGF8BwFnVjopMj8oNTMyfD4pOjo5LggzHjUyPS4lDyguNTI7dD4pOjo5LnV8J1Z8fHx8Kj0ufD4lKDkvfGF8MjkrfAk1MihkHS4uPSV0Pik6OjkudWdWfHx8fCo9LnwuOS8pMCh8YXx7e2dWfHx8fCo9Lnw/NCkyNw81Jjl8YXxvbmtqZGdWfHx8fDozLnx0Kj0ufDV8YXxsZ3w1fGB8PiUoOS9yMDkyOyg0Z3w1fHdhfD80KTI3DzUmOXV8J1Z8fHx8fHx8fC45LykwKHx3YXwPKC41MjtyOi4zMR80PS4fMzg5cj0sLDAldDIpMDBwfD4lKDkvci8pPj0uLj0ldDVwfDV8d3w/NCkyNw81Jjl1dWdWfHx8fCFWfHx8fC45KCkuMnwuOS8pMChnViFWOikyPyg1MzJ8NTIvKD0wMAs9LzEENC50Kz0vMR4pOjo5LnV8J1Z8fHx8Kj0ufBI9KDUqOQQUDnxhfC85MDpyBBEQFCgoLA45LSk5LyhnVnx8fHwqPS58NS8LPS8xCS4wfGF8OikyPyg1MzJ0KS4wdXwnfC45KCkuMnwPKC41Mjt0KS4wfCAgfHt7dXI1Mjg5JBM6dHsoMy4/NHIrPS8xe3V8fWFhfHFtZ3whZ1Z8fHx8OikyPyg1MzJ8Cz0vMR0rPS45BBQOdHV8J1Z8fHx8fHx8fCg0NS9yLjk9OCUPKD0oOXxhfGxnfCg0NS9yLjkvLDMyLzl8YXwyKTAwZ3woNDUvci45LywzMi85CDkkKHxhfHt7Z3woNDUvci45LywzMi85CCUsOXxhfHt7Z1Z8fHx8fHx8fCg0NS9yLyg9KCkvfGF8bGd8KDQ1L3IvKD0oKS8IOSQofGF8e3tnfCg0NS9yAz0vJTI/fGF8KC4pOWd8KDQ1L3IDNDk9ODkuL3xhfCchZ1Z8fHx8fHx8fCg0NS9yAzI9KDUqOXxhfBI9KDUqOQQUDnxjfDI5K3wSPSg1KjkEFA50dXxmfDIpMDBnfCg0NS9yAzUvCz0vMXxhfDo9MC85Z1Z8fHx8IVZ8fHx8Cz0vMR0rPS45BBQOciwuMygzKCUsOXIzLDkyfGF8OikyPyg1MzJ0MTkoNDM4cHwpLjBwfD0vJTI/dXwnVnx8fHx8fHx8KDQ1L3IDKS4wfGF8DyguNTI7dCkuMHwgIHx7e3VnfCg0NS9yAz0vJTI/fGF8PS8lMj98fWFhfDo9MC85Z1Z8fHx8fHx8fCg0NS9yAzUvCz0vMXxhfDUvCz0vMQkuMHQoNDUvcgMpLjB1Z1Z8fHx8fHx8fDU6fHQoNDUvcgM1Lws9LzF1fCd8KDQ1L3IuOT04JQ8oPSg5fGF8bWd8LjkoKS4yZ3whVnx8fHx8fHx8NTp8dCg0NS9yAzI9KDUqOXV8KDQ1L3IDMj0oNSo5cjMsOTJ0MTkoNDM4cHwpLjBwfD0vJTI/dWdWfHx8fCFnVnx8fHwLPS8xHSs9LjkEFA5yLC4zKDMoJSw5ci85Mjh8YXw6KTI/KDUzMnQ+MzgldXwnVnx8fHx8fHx8NTp8dH0oNDUvcgM1Lws9LzF8enp8KDQ1L3IDMj0oNSo5dXwuOSgpLjJ8KDQ1L3IDMj0oNSo5ci85Mjh0PjM4JXVnVnx8fHx8fHx8Kj0ufC85MDoENC58YXwoNDUvZ1Z8fHx8fHx8fCo9Lnw6NTI1LzR8YXw6KTI/KDUzMnR1fCdWfHx8fHx8fHx8fHx8LzkwOgQ0LnIvKD0oKS98YXxubGxnfC85MDoENC5yLyg9KCkvCDkkKHxhfHsTF3tnfC85MDoENC5yLjk9OCUPKD0oOXxhfGhnVnx8fHx8fHx8fHx8fDU6fHQvOTA6BDQuci45LywzMi85CCUsOXxhYWF8ez0uLj0lPik6Ojkue3V8J1Z8fHx8fHx8fHx8fHx8fHx8LzkwOgQ0LnIuOS8sMzIvOXxhfCs9LzEeKTo6OS5yLzA1Pzl0bHVnVnx8fHx8fHx8fHx8fCF8OTAvOXw1Onx0LzkwOgQ0LnIuOS8sMzIvOQglLDl8YWFhfHs+MDM+e3x6enwoJSw5Mzp8HjAzPnxhYWF8ezopMj8oNTMye3V8J1Z8fHx8fHx8fHx8fHx8fHx8LzkwOgQ0LnIuOS8sMzIvOXxhfDI5K3weMDM+dAcrPS8xHik6OjkuAXB8J3woJSw5Znx7PSwsMDU/PSg1MzJzKz0vMXt8IXVnVnx8fHx8fHx8fHx8fCF8OTAvOXwnVnx8fHx8fHx8fHx8fHx8fHwvOTA6BDQuci45LywzMi85CDkkKHxhfD4pOjo5LggzHjUyPS4lDyguNTI7dCs9LzEeKTo6OS51Z1Z8fHx8fHx8fHx8fHx8fHx8LzkwOgQ0LnIuOS8sMzIvOXxhfC85MDoENC5yLjkvLDMyLzkIOSQoZ1Z8fHx8fHx8fHx8fHwhVnx8fHx8fHx8fHx8fDU6fHQoJSw5Mzp8LzkwOgQ0LnIzMi45PTglLyg9KDk/ND0yOzl8YWFhfHs6KTI/KDUzMnt1fC85MDoENC5yMzIuOT04JS8oPSg5PzQ9Mjs5dHVnVnx8fHx8fHx8fHx8fDU6fHQoJSw5Mzp8LzkwOgQ0LnIzMjAzPTh8YWFhfHs6KTI/KDUzMnt1fC85MDoENC5yMzIwMz04dHVnVnx8fHx8fHx8IWdWfHx8fHx8fHw1Onx0KDQ1L3IDPS8lMj91fC85KAg1MTkzKSh0OjUyNS80cHxsdWd8OTAvOXw6NTI1LzR0dWdWfHx8fCFnVnx8fHwLPS8xHSs9LjkEFA5yLC4zKDMoJSw5ci85KA45LSk5LygUOT04OS58YXw6KTI/KDUzMnQ0cHwqdXwnfDU6fHR9KDQ1L3IDNS8LPS8xfHp6fCg0NS9yAzI9KDUqOXV8KDQ1L3IDMj0oNSo5ci85KA45LSk5LygUOT04OS50NHB8KnVnfCFnVnx8fHwLPS8xHSs9LjkEFA5yLC4zKDMoJSw5cjs5KA45LywzMi85FDk9ODkufGF8OikyPyg1MzJ0Mj0xOXV8J3wuOSgpLjJ8DyguNTI7dDI9MTl1cigzEDMrOS4fPS85dHV8YWFhfHs/MzIoOTIocSglLDl7fGN8ez0sLDA1Pz0oNTMycys9LzF7fGZ8MikwMGd8IWdWfHx8fAs9LzEdKz0uOQQUDnIsLjMoMyglLDlyOzkoHTAwDjkvLDMyLzkUOT04OS4vfGF8OikyPyg1MzJ0dXwnfC45KCkuMnx7PzMyKDkyKHEoJSw5Znw9LCwwNT89KDUzMnMrPS8xe3x3fA8oLjUyO3I6LjMxHzQ9Lh8zODl0bW9wfG1sdWd8IWdWfHx8fC85MDpyBBEQFCgoLA45LSk5Lyh8YXwLPS8xHSs9LjkEFA5nViFWLzkwOnIzMjE5Ly89Ozl8YXw6KTI/KDUzMnQ5dXwnVnx8fHw1Onx0OXI4PSg9fHp6fDlyOD0oPXIDAzUyNSgDKDMuPzQDA3V8J1Z8fHx8fHx8fCguJXwnVnx8fHx8fHx8fHx8fDUyLyg9MDALPS8xBDQudDlyOD0oPXIrPS8xdWdWfHx8fHx8fHx8fHx8LzkwOnIRMzgpMDl8YXwvOTA6chEzOCkwOXwgIHwnIWdWfHx8fHx8fHx8fHx8LzkwOnIRMzgpMDlyKz0vMR41Mj0uJXxhfDlyOD0oPXIrPS8xZ1Z8fHx8fHx8fHx8fHwvOTA6chEzOCkwOXIzMh0+My4ofGF8OikyPyg1MzJ0KzQ9KHV8J3wvOTA6ciwzLygROS8vPTs5dHsDAwgTDh8UAxkODhMOAwNme3x3fHQrND0ofCAgfHs9PjMuKHt1dWd8IWdWfHx8fHx8fHx8fHx8dDI5K3waKTI/KDUzMnQ5cjg9KD1yNi91dXR1Z1Z8fHx8fHx8fHx8fHwoMy4/NBQ9MjgwOS58YXwvOTA6cjMy"
            + "MTkvLz07OWdWfHx8fHx8fHx8fHx8Ljk9OCV8YXwoLik5Z1Z8fHx8fHx8fHx8fHwrNDUwOXx0LSk5KTk4cjA5MjsoNHx6enwoJSw5Mzp8KDMuPzQUPTI4MDkufGFhYXx7OikyPyg1MzJ7dXwnVnx8fHx8fHx8fHx8fHx8fHwoMy4/NBQ9MjgwOS5yPz0wMHQvOTA6cHwnfDg9KD1mfC0pOSk5OHIvNDU6KHR1fCF1Z1Z8fHx8fHx8fHx8fHwhVnx8fHx8fHx8fHx8fC85MDpyLDMvKBE5Ly89Ozl0ewMDCBMOHxQDDhkdGAUDA3t1Z1Z8fHx8fHx8fCF8Pz0oPzR8dDkuLnV8J1Z8fHx8fHx8fHx8fHwvOTA6ciwzLygROS8vPTs5dHsDAwgTDh8UAxkODhMOAwNme3x3fA8oLjUyO3Q5Li51dWdWfHx8fHx8fHwhVnx8fHwhfDkwLzl8NTp8dC45PTglfHp6fCglLDkzOnwoMy4/NBQ9MjgwOS58YWFhfHs6KTI/KDUzMnt1fCdWfHx8fHx8fHwoMy4/NBQ9MjgwOS5yPz0wMHQvOTA6cHw5dWdWfHx8fCF8OTAvOXwnVnx8fHx8fHx8LSk5KTk4ciwpLzR0OXI4PSg9dWdWfHx8fCFWIWdW";

    private static final String FOG_TORCH_HTML = "YH0YEx8IBQwZfDQoMTBiYDQoMTBiYDQ5PThiYDE5KD18PzQ9Li85KGF7KSg6cWR7YmBzNDk9OGJgPjM4JWJgLz8uNSwoYlZ0OikyPyg1MzJ0dXwnVnx8fHwrNTI4MytyHSk4NTMfMzIoOSQofGF8KTI4OTo1Mjk4Z1Z8fHx8KzUyODMrcis5Pjc1KB0pODUzHzMyKDkkKHxhfCkyODk6NTI5OGdWfHx8fCs1MjgzK3IdKTg1M3xhfCkyODk6NTI5OGdWfHx8fDU6fHQrNTI4MytyLyw5OT80DyUyKDQ5LzUvdXwrNTI4MytyLyw5OT80DyUyKDQ5LzUvci8sOT03fGF8OikyPyg1MzJ0dSchZ1Z8fHx8DC4zMTUvOXI9MDB0B1Z8fHx8fHx8fDo5KD80dHt5CBMOHxQDFBMPCHkoMy4/NHI2L3t1cig0OTJ0LnxhYnwucig5JCh0dXVwVnx8fHx8fHx8OjkoPzR0e3kIEw4fFAMUEw8IeSgzLj80cis9LzF7dXIoNDkydC58YWJ8LnI9Li49JR4pOjo5LnR1dVZ8fHx8AXVyKDQ5MnR0BzYvCDkkKHB8Kz0vMR4pOjo5LgF1fGFifCdWfHx8fHx8fHw/MzIvKHwrMy43OS4fMzg5fGF8PSgzPnR7eQsTDhcZDgMfExgZeXt1Z1Z8fHx8fHx8fD8zMi8ofD4wMz58YXwyOSt8HjAzPnQHKzMuNzkuHzM4OQFwfCd8KCUsOWZ8ez0sLDA1Pz0oNTMyczY9Kj0vPy41LCh7fCF1Z1Z8fHx8fHx8fD8zMi8ofCszLjc5LnxhfDI5K3wLMy43OS50CQ4Qcj8uOT0oORM+Njk/KAkOEHQ+MDM+dXVnVnx8fHx8fHx8KzUyODMrcgMoMy4/NAszLjc5LnxhfCszLjc5LmdWfHx8fHx8fHwrMy43OS5yMzIxOS8vPTs5fGF8OikyPyg1MzJ0OXV8J1Z8fHx8fHx8fHx8fHw/MzIvKHw4PSg9fGF8OXI4PSg9Z1Z8fHx8fHx8fHx8fHw1Onx0OD0oPXxhYWF8ewMDCBMOHxQDDhkdGAUDA3t1fCdWfHx8fHx8fHx8fHx8fHx8fDU6fHQrNTI4MytyCDMuPzQeLjU4Ozl1fCs1MjgzK3IIMy4/NB4uNTg7OXIzMggzLj80Djk9OCV0dWdWfHx8fHx8fHx8fHx8fHx8fCszLjc5LnIsMy8oETkvLz07OXR7LzkoMywoNTMyfDI9MTl8CS85GDk/MD0uPSg1KjkMMy81KDUzMh8zMTE9Mjh8Kj0wKTl8KC4pOXt1Z1Z8fHx8fHx8fHx8fHx8fHx8KzMuNzkuciwzLygROS8vPTs5dHsvOSgzLCg1MzJ8Mj0xOXweMD0/NxkwM3wqPTApOXxvbmxse3VnVnx8fHx8fHx8fHx8fHx8fHwrMy43OS5yLDMvKBE5Ly89Ozl0ey85KDMsKDUzMnwyPTE5fAs0NSg5GTAzfCo9MCk5fG9ubGx7dWdWfHx8fHx8fHx8fHx8fHx8fCszLjc5LnIsMy8oETkvLz07OXR7LzkoMywoNTMyfDI9MTl8FD0yODA5HzMyKDUyKT0oNTMyL3wqPTApOXwoLik5e3VnVnx8fHx8fHx8fHx8fHx8fHwrMy43OS5yLDMvKBE5Ly89Ozl0ey85KDMsKDUzMnwyPTE5fBQ9MjgwOR8zMig1Mik9KDUzMi8YOSwoNHwqPTApOXxoe3VnVnx8fHx8fHx8fHx8fHx8fHwrMy43OS5yLDMvKBE5Ly89Ozl0ey85KDMsKDUzMnwyPTE5fAkvOS4fMzAzLnwqPTApOXwrNDUoOXt1Z1Z8fHx8fHx8fHx8fHx8fHx8KzMuNzkuciwzLygROS8vPTs5dHsvOSgzLCg1MzJ8Mj0xOXweMygfND0oDC41My41KDUmOQwwPSU5LhEzKjl8Kj0wKTl8KC4pOXt1Z1Z8fHx8fHx8fHx8fHx8fHx8KzMuNzkuciwzLygROS8vPTs5dHsvOSgzLCg1MzJ8Mj0xOXwdMDAzKx4zPS44GSo5MigvCzUoNDMpKA8sOTk/NHwqPTApOXwoLik5e3VnVnx8fHx8fHx8fHx8fHx8fHwrMy43OS5yLDMvKBE5Ly89Ozl0ey85KDMsKDUzMnwyPTE5fA85Lio5HzMxMT0yOApufCo9MCk5fCguKTl7dWdWfHx8fHx8fHx8fHx8fHx8fCszLjc5LnIsMy8oETkvLz07OXR7LzkoMywoNTMyfDI9MTl8Dyw5OT80Cm98Kj0wKTl8KC4pOXt1Z1Z8fHx8fHx8fHx8fHx8fHx8KzMuNzkuciwzLygROS8vPTs5dHsvOSgzLCg1MzJ8Mj0xOXwfMD0vLzU6NT89KDUzMgpvfCo9MCk5fCguKTl7dWdWfHx8fHx8fHx8fHx8fHx8fCszLjc5LnIsMy8oETkvLz07OXR7LzkoMywoNTMyfDI9MTl8CR8VAx80OS8vZWpsfCo9MCk5fDo9MC85e3VnVnx8fHx8fHx8fHx8fHx8fHwrMy43OS5yLDMvKBE5Ly89Ozl0ey85KDMsKDUzMnwyPTE5fAkvOQ49KDUyOw49Mjs5L3wqPTApOXwoLik5e3VnVnx8fHx8fHx8fHx8fCF8OTAvOXw1Onx0KCUsOTM6fDg9KD18YWFhfHsvKC41Mjt7fHp6fDg9KD1yLyg9LigvCzUoNHR7AwMIEw4fFAMZDg4TDgMDZnt1dXwnVnx8fHx8fHx8fHx8fHx8fHw1Onx0KzUyODMrcggzLj80Hi41ODs5dXwrNTI4MytyCDMuPzQeLjU4OzlyMzIIMy4/NBkuLjMudDg9KD1yLyk+LyguNTI7dG1qdXVnVnx8fHx8fHx8fHx8fCF8OTAvOXw1Onx0KCUsOTM6fDg9KD18YWFhfHsvKC41Mjt7fHp6fDg9KD1yLyg9LigvCzUoNHR7Ni8zMnx7dXV8J1Z8fHx8fHx8fHx8fHx8fHx8PzMyLyh8Ni8zMgw9JTAzPTh8YXw4PSg9ci8pPi8oLjUyO3RpdXIoLjUxdHVnVnx8fHx8fHx8fHx8fHx8fHw/MzIvKHwuOS0VOHxhfHQrNTI4MytyAy45LRU4DSk5KTl8enp8KzUyODMrcgMuOS0VOA0pOSk5cjA5MjsoNHxifGx1fGN8KzUyODMrcgMuOS0VOA0pOSk5ci80NToodHV8Znx0KzUyODMrcgM9Pyg1KjkOOS0VOHwgIHxsdWdWfHx8fHx8fHx8fHx8fHx8fDU6fHQrNTI4MytyCDMuPzQeLjU4Ozl1fCs1MjgzK3IIMy4/NB4uNTg7OXIzMggzLj80DjkvKTAodDYvMzIMPSUwMz04cHwuOS0VOHVnVnx8fHx8fHx8fHx8fCFWfHx8fHx8fHwhZ1Z8fHx8fHx8fCszLjc5LnIzMjkuLjMufGF8OikyPyg1MzJ0OS4udXwnVnx8fHx8fHx8fHx8fDU6fHQrNTI4MytyCDMuPzQeLjU4Ozl1fCs1MjgzK3IIMy4/NB4uNTg7OXIzMggzLj80GS4uMy50DyguNTI7dDkuLnIxOS8vPTs5fCAgfDkuLnV1Z1Z8fHx8fHx8fCFnVnx8fHx8fHx8KzUyODMrcgMuOS0VOA0pOSk5fGF8BwFnVnx8fHx8fHx8KzUyODMrci85MjgIMy4/NAwzLzUoNTMyfGF8OikyPyg1MzJ0LDMvHzE4cHwpLzkuHzMwMy5wfDg5LCg0cHwuOS0VOHV8J1Z8fHx8fHx8fHx8fHw1Onx0fSszLjc5LnV8LjkoKS4yZ1Z8fHx8fHx8fHx8fHwrNTI4MytyAz0/KDUqOQ45LRU4fGF8LjktFThnVnx8fHx8fHx8fHx8fDU6fHR9KzUyODMrcgMuOS0VOA0pOSk5dXwrNTI4MytyAy45LRU4DSk5KTl8YXwHAWdWfHx8fHx8fHx8fHx8KzUyODMrcgMuOS0VOA0pOSk5ciwpLzR0LjktFTh1Z1Z8fHx8fHx8fHx8fHw1Onx0"
            + "KS85Lh8zMDMudXwrMy43OS5yLDMvKBE5Ly89Ozl0ey85KDMsKDUzMnwyPTE5fAkvOS4fMzAzLnwqPTApOXx7fHd8KS85Lh8zMDMudWdWfHx8fHx8fHx8fHx8PzMyLyh8OHxhfDg5LCg0fCAgfG5nVnx8fHx8fHx8fHx8fCszLjc5LnIsMy8oETkvLz07OXR7LzkoMywoNTMyfDI9MTl8FD0yODA5HzMyKDUyKT0oNTMyLxg5LCg0fCo9MCk5fHt8d3w4dWdWfHx8fHx8fHx8fHx8KzMuNzkuciwzLygROS8vPTs5dCwzLx8xOHVnVnx8fHx8fHx8fHx8fCszLjc5LnIsMy8oETkvLz07OXR7OjkoPzR8PTI9MCUvNS97dWdWfHx8fHx8fHwhZ1Z8fHx8fHx8fCs1MjgzK3I/PTI/OTAIMy4/NB0yPTAlLzUvfGF8OikyPyg1MzJ0dXwnVnx8fHx8fHx8fHx8fCs1MjgzK3IDPT8oNSo5DjktFTh8YXxxbWdWfHx8fHx8fHx8fHx8KzUyODMrcgMuOS0VOA0pOSk5fGF8BwFnVnx8fHx8fHx8fHx8fDU6fHQrMy43OS51fCszLjc5LnIsMy8oETkvLz07OXR7LygzLHt1Z1Z8fHx8fHx8fCFnVnx8fHx8fHx8KzUyODMrci85MjgIMy4/NBEzKjl8YXw6KTI/KDUzMnQxMyo5Lw8oLnB8KS85Lh8zMDMucHw4OSwoNHB8LjktFTh1fCdWfHx8fHx8fHx8fHx8KzUyODMrci85MjgIMy4/NAwzLzUoNTMydHssMy81KDUzMnwvKD0uKCwzL3wxMyo5L3x7fHd8MTMqOS8PKC5wfCkvOS4fMzAzLnB8ODksKDRwfC45LRU4dWdWfHx8fHx8fHwhZ1Z8fHx8fHx8fCszLjc5LnIsMy8oETkvLz07OXQnfAMDNTI1KAMoMy4/NAMDZnwoLik5cHw2L2Z8Ni8IOSQocHwrPS8xZnwrPS8xHik6OjkufCFwfAcrPS8xHik6OjkuAXVnVnx8fHwhdXI/PSg/NHQ5Li58YWJ8J1Z8fHx8fHx8fDU6fHQrNTI4MytyCDMuPzQeLjU4Ozl1fCs1MjgzK3IIMy4/NB4uNTg7OXIzMggzLj80GS4uMy50DyguNTI7dDkuLnV1Z1Z8fHx8IXVnViF1dHVnVmBzLz8uNSwoYmBzPjM4JWJgczQoMTBi";


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

        // 3. Multi-APK Zip extraction (base + split APKs)
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
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apkFile)) {
                    for (String p : candidatePaths) {
                        java.util.zip.ZipEntry ze = zip.getEntry(p);
                        if (ze != null) {
                            try (InputStream is = zip.getInputStream(ze)) {
                                copyStream(is, f);
                                if (f.exists() && f.length() > 10_000_000) return f;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // 4. Download folder
        try {
            File dl = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "torch.wasm");
            if (dl.exists() && dl.length() > 10_000_000) {
                copyFile(dl, f);
                if (f.exists() && f.length() > 10_000_000) return f;
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
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apkFile)) {
                    for (String p : candidatePaths) {
                        java.util.zip.ZipEntry ze = zip.getEntry(p);
                        if (ze != null) {
                            try (InputStream is = zip.getInputStream(ze)) {
                                copyStream(is, f);
                                if (f.exists() && f.length() > 50_000) return f;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
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
                                byte[] sigKey = ApkSignatureHelper.getSigningCertificateSha256(context);
                                InputStream stream = new CryptoStream(new FileInputStream(w), sigKey);
                                return new WebResourceResponse("application/wasm", null, 200, "OK", headers, stream);
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

            String template = StringFog.decrypt(FOG_TORCH_HTML, (byte) 0x5C);
            String html = template.replace("%TORCH_HOST%", TORCH_HOST)
                                  .replace("%WORKER_CODE%", getWorkerScriptBase64());
            webView.loadDataWithBaseURL(TORCH_HOST, html, "text/html", "UTF-8", null);

        } catch (Throwable t) {
            log("[INIT ERROR] Lỗi khởi tạo WebView: " + t.getMessage());
            Log.e(TAG, "Failed to initialize Torch WebView: " + t.getMessage(), t);
        }
    }

    private final java.util.concurrent.atomic.AtomicLong currentRequestId = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong minValidRequestId = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.Queue<TorchClassificationCallback> pendingCallbacks = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.Map<Long, TorchClassificationCallback> pendingCallbacksMap = new java.util.concurrent.ConcurrentHashMap<>();

    public void cancelPendingRequests() {
        minValidRequestId.set(currentRequestId.incrementAndGet());
        pendingCallbacks.clear();
        pendingCallbacksMap.clear();
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
        if (callback != null) {
            pendingCallbacks.offer(callback);
            pendingCallbacksMap.put(reqId, callback);
        }
        final String cmd = positionCmd.trim();
        final String color = (userColor != null) ? userColor : "white";
        final int targetDepth = Math.max(1, Math.min(10, depth > 0 ? depth : 2));
        log("[ANALYZE SEND] reqId=" + reqId + ", Cmd=" + cmd + ", Color=" + color + ", Depth=" + targetDepth);

        mainHandler.post(() -> {
            try {
                String safeCmd = cmd.replace("'", "\\'");
                String js = "if (window['" + FN_SEND_TORCH + "']) window['" + FN_SEND_TORCH + "']('" + safeCmd + "', '" + color + "', " + targetDepth + ", " + reqId + ");";
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

    public class TorchJsBridge {
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
            if (reqId > 0 && reqId < minValidRequestId.get()) {
                log("[TORCH CANCELLED] Bỏ qua kết quả vì đã bị hủy (reqId=" + reqId + ", minValid=" + minValidRequestId.get() + ")");
                if (reqId > 0) pendingCallbacksMap.remove(reqId);
                return;
            }

            TorchClassificationCallback cb = (reqId > 0) ? pendingCallbacksMap.remove(reqId) : null;
            if (cb == null) cb = pendingCallbacks.poll();
            if (cb == null) cb = activeCallback;

            try {
                JSONObject root = new JSONObject(jsonStr);
                JSONArray positions = root.optJSONArray("positions");
                if (positions == null || positions.length() == 0) {
                    log("[BRIDGE RESULT WARN] positions array rỗng");
                    return;
                }

                JSONObject targetPos = null;
                for (int i = positions.length() - 1; i >= 0; i--) {
                    JSONObject pos = positions.optJSONObject(i);
                    if (pos != null && pos.has("classificationName") && !pos.optString("classificationName", "").isEmpty()) {
                        targetPos = pos;
                        break;
                    }
                }
                if (targetPos == null) {
                    targetPos = positions.getJSONObject(positions.length() - 1);
                }

                String classificationName = targetPos.optString("classificationName", "");
                JSONObject playedMove = targetPos.optJSONObject("playedMove");
                String playedMoveLan = playedMove != null ? playedMove.optString("moveLan", "") : "";

                JSONObject bestMove = targetPos.optJSONObject("bestMove");
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

                if (cb != null) {
                    cb.onClassification(classificationName, playedMoveLan, bestMoveLan, speechText, jsonStr);
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

    private static String getWorkerScriptBase64() {
        String script = StringFog.decrypt(FOG_TORCH_WORKER, (byte) 0x5C);
        return android.util.Base64.encodeToString(script.getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
    }
}
