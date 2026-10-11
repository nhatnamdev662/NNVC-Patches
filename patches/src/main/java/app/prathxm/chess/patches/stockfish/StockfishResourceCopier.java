package app.prathxm.chess.patches.stockfish;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

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

    public static void copyAll(Object patchContext) {
        if (patchContext == null) return;
        try {
            Method getMethod = null;
            for (Method m : patchContext.getClass().getMethods()) {
                if (m.getName().equals("get") && m.getParameterCount() >= 1 && m.getParameterTypes()[0] == String.class) {
                    getMethod = m;
                    break;
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

                try {
                    File destFile = null;
                    if (getMethod.getParameterCount() == 1) {
                        destFile = (File) getMethod.invoke(patchContext, targetPath);
                    } else if (getMethod.getParameterCount() == 2) {
                        destFile = (File) getMethod.invoke(patchContext, targetPath, false);
                    }
                    if (destFile == null) continue;

                    if (destFile.exists() && destFile.length() > 0) {
                        continue;
                    }

                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    InputStream is = cl.getResourceAsStream(resourcePath);
                    if (is == null) {
                        is = cl.getResourceAsStream("assets/" + resourcePath);
                    }
                    if (is != null) {
                        try (InputStream in = is; OutputStream out = new FileOutputStream(destFile)) {
                            byte[] buf = new byte[65536];
                            int r;
                            while ((r = in.read(buf)) != -1) {
                                out.write(buf, 0, r);
                            }
                        }
                        System.out.println("[ResourceCopier] Copied: " + resourcePath + " -> " + targetPath + " (" + destFile.length() + " bytes)");
                    } else {
                        System.err.println("[ResourceCopier] Resource not found: " + resourcePath);
                    }
                } catch (Throwable itemErr) {
                    System.err.println("[ResourceCopier] Error copying " + targetPath + ": " + itemErr.getMessage());
                }
            }
        } catch (Throwable t) {
            System.err.println("[ResourceCopier] Fatal error in copyAll: " + t.getMessage());
        }
    }
}
