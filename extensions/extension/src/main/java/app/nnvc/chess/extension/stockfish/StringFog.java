/*
 * Copyright 2026 NNVC
 * https://github.com/NNVC/NNVC-Patches
 */

package app.nnvc.chess.extension.stockfish;

import java.nio.charset.StandardCharsets;

public final class StringFog {

    private StringFog() {}

    /**
     * Decodes an XOR-masked byte array at runtime.
     * Prevents static string search from revealing local WebView endpoints
     * and internal architecture in decompiled bytecode.
     */
    public static String decrypt(byte[] data, byte key) {
        if (data == null) return null;
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key);
        }
        return new String(result, StandardCharsets.UTF_8);
    }
}
