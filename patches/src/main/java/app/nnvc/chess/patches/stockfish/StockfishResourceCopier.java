package app.nnvc.chess.patches.stockfish;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * StockfishResourceCopier – Runs during Morphe Patcher execution.
 * Extracts all native engine libraries and WebAssembly engine files
 * from the patch bundle into the target APK workspace, guaranteeing
 * 100% offline local extraction and zero network calls at runtime.
 */
public class StockfishResourceCopier {

    private static final String[][] ASSETS_TO_COPY = {
        // Native Stockfish binaries (compatibility)
        {"lib/arm64-v8a/libstockfish.so", "stockfish/arm64-v8a/stockfish"},
        {"lib/armeabi-v7a/libstockfish.so", "stockfish/armeabi-v7a/stockfish"},

        // Komodo Dragon 3.3 WASM Engine (Default)
        {"assets/komodo/dragon3.3.wasm", "komodo/dragon3.3.wasm"},
        {"assets/komodo/komodo.js", "komodo/komodo.js"},
        {"assets/komodo/book.bin", "komodo/book.bin"},

        // Stockfish 18 WASM Engine (Play / Classify)
        {"assets/stockfish18/stockfish-18-lite-single.wasm", "stockfish18/stockfish-18-lite-single.wasm"},
        {"assets/stockfish18/stockfish-18-lite-single.js", "stockfish18/stockfish-18-lite-single.js"},

        // Torch WASM Engine (CEE Move Classification)
        {"assets/torch/torch.wasm", "torch/torch.wasm"},
        {"assets/torch/torch.js", "torch/torch.js"}
    };

    private static long getMinExpectedSize(String path) {
        if (path.endsWith("dragon3.3.wasm")) return 1_000_000L;
        if (path.endsWith("komodo.js")) return 50_000L;
        if (path.endsWith("book.bin")) return 500_000L;
        if (path.endsWith("stockfish-18-lite-single.wasm")) return 1_000_000L;
        if (path.endsWith("stockfish-18-lite-single.js")) return 10_000L;
        if (path.endsWith("torch.wasm")) return 10_000_000L;
        if (path.endsWith("torch.js")) return 50_000L;
        if (path.endsWith("libstockfish.so")) return 1_000_000L;
        return 1L;
    }

    public static void copyAll(Object patchContext) {
        if (patchContext == null) return;
        try {
            Method getMethod = null;
            // 1. First priority: look for get(String, boolean) returning File
            try {
                Method m = patchContext.getClass().getMethod("get", String.class, boolean.class);
                if (File.class.isAssignableFrom(m.getReturnType())) {
                    getMethod = m;
                }
            } catch (Throwable ignored) {}

            // 2. Second priority: look for get(String) returning File
            if (getMethod == null) {
                try {
                    Method m = patchContext.getClass().getMethod("get", String.class);
                    if (File.class.isAssignableFrom(m.getReturnType())) {
                        getMethod = m;
                    }
                } catch (Throwable ignored) {}
            }

            // 3. Fallback: inspect all methods named "get" returning File
            if (getMethod == null) {
                for (Method m : patchContext.getClass().getMethods()) {
                    if (m.getName().equals("get") && m.getParameterCount() >= 1
                            && m.getParameterTypes()[0] == String.class
                            && File.class.isAssignableFrom(m.getReturnType())) {
                        getMethod = m;
                        break;
                    }
                }
            }

            if (getMethod == null) {
                System.err.println("[ResourceCopier] get method not found on " + patchContext.getClass().getName());
                return;
            }

            ClassLoader cl = StockfishResourceCopier.class.getClassLoader();
            for (String[] pair : ASSETS_TO_COPY) {
                String targetPath = pair[0];
                String resourcePath = pair[1];
                long minSize = getMinExpectedSize(targetPath);

                try {
                    File destFile = null;
                    if (getMethod.getParameterCount() == 1) {
                        destFile = (File) getMethod.invoke(patchContext, targetPath);
                    } else if (getMethod.getParameterCount() == 2) {
                        destFile = (File) getMethod.invoke(patchContext, targetPath, false);
                    }
                    if (destFile == null) continue;

                    if (destFile.exists() && destFile.length() >= minSize) {
                        continue;
                    }

                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    InputStream is = openResourceStream(cl, patchContext, resourcePath);
                    if (is != null) {
                        File tmp = new File(parent, destFile.getName() + ".tmp");
                        try (InputStream in = is; OutputStream out = new FileOutputStream(tmp)) {
                            byte[] buf = new byte[65536];
                            int r;
                            while ((r = in.read(buf)) != -1) {
                                out.write(buf, 0, r);
                            }
                        }
                        if (destFile.exists()) destFile.delete();
                        tmp.renameTo(destFile);
                        System.out.println("[ResourceCopier] Copied: " + resourcePath + " -> " + targetPath + " (" + destFile.length() + " bytes)");
                    } else {
                        System.err.println("[ResourceCopier] Resource not found: " + resourcePath);
                    }
                } catch (Throwable itemErr) {
                    System.err.println("[ResourceCopier] Error copying " + targetPath + ": " + itemErr.getMessage());
                }
            }

            // Copy 58 Coach JSON profiles (0.json .. 57.json)
            for (int cid = 0; cid < 58; cid++) {
                String targetPath = "assets/coaches/" + cid + ".json";
                String resourcePath = "coaches/" + cid + ".json";
                try {
                    File destFile = null;
                    if (getMethod.getParameterCount() == 1) {
                        destFile = (File) getMethod.invoke(patchContext, targetPath);
                    } else if (getMethod.getParameterCount() == 2) {
                        destFile = (File) getMethod.invoke(patchContext, targetPath, false);
                    }
                    if (destFile == null) continue;
                    if (destFile.exists() && destFile.length() > 50) continue;

                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();

                    InputStream is = openResourceStream(cl, patchContext, resourcePath);
                    if (is != null) {
                        File tmp = new File(parent, destFile.getName() + ".tmp");
                        try (InputStream in = is; OutputStream out = new FileOutputStream(tmp)) {
                            byte[] buf = new byte[16384];
                            int r;
                            while ((r = in.read(buf)) != -1) {
                                out.write(buf, 0, r);
                            }
                        }
                        if (destFile.exists()) destFile.delete();
                        tmp.renameTo(destFile);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            System.err.println("[ResourceCopier] Fatal error in copyAll: " + t.getMessage());
        }
    }

    private static InputStream openResourceStream(ClassLoader cl, Object patchContext, String resourcePath) {
        InputStream is = null;
        if (cl != null) {
            is = cl.getResourceAsStream(resourcePath);
            if (is == null) is = cl.getResourceAsStream("assets/" + resourcePath);
            if (is == null) is = cl.getResourceAsStream("/" + resourcePath);
            if (is == null) is = cl.getResourceAsStream("/assets/" + resourcePath);
        }
        if (is == null) {
            ClassLoader ccl = Thread.currentThread().getContextClassLoader();
            if (ccl != null && ccl != cl) {
                is = ccl.getResourceAsStream(resourcePath);
                if (is == null) is = ccl.getResourceAsStream("assets/" + resourcePath);
            }
        }
        if (is == null && patchContext != null) {
            ClassLoader pcl = patchContext.getClass().getClassLoader();
            if (pcl != null && pcl != cl) {
                is = pcl.getResourceAsStream(resourcePath);
                if (is == null) is = pcl.getResourceAsStream("assets/" + resourcePath);
            }
        }
        if (is == null) {
            try {
                java.security.ProtectionDomain pd = StockfishResourceCopier.class.getProtectionDomain();
                if (pd != null && pd.getCodeSource() != null && pd.getCodeSource().getLocation() != null) {
                    String path = pd.getCodeSource().getLocation().getPath();
                    if (path != null) {
                        File mppFile = new File(path);
                        if (mppFile.exists()) {
                            ZipFile zf = new ZipFile(mppFile);
                            ZipEntry ze = zf.getEntry(resourcePath);
                            if (ze == null) ze = zf.getEntry("assets/" + resourcePath);
                            if (ze != null) is = zf.getInputStream(ze);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return is;
    }
}
