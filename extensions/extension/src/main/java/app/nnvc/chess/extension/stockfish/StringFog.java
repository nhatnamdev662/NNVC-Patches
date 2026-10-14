package app.nnvc.chess.extension.stockfish;

import java.nio.charset.StandardCharsets;

public final class StringFog {

    private StringFog() {}

    private static final byte[] DECODE_TABLE = new byte[128];
    static {
        for (int i = 0; i < 128; i++) DECODE_TABLE[i] = -1;
        for (int i = 'A'; i <= 'Z'; i++) DECODE_TABLE[i] = (byte) (i - 'A');
        for (int i = 'a'; i <= 'z'; i++) DECODE_TABLE[i] = (byte) (i - 'a' + 26);
        for (int i = '0'; i <= '9'; i++) DECODE_TABLE[i] = (byte) (i - '0' + 52);
        DECODE_TABLE['+'] = 62;
        DECODE_TABLE['/'] = 63;
    }

    private static byte[] decodeBase64(String s) {
        if (s == null) return new byte[0];
        int len = s.length();
        int pad = 0;
        if (len > 0 && s.charAt(len - 1) == '=') pad++;
        if (len > 1 && s.charAt(len - 2) == '=') pad++;
        int rawLen = (len * 3) / 4 - pad;
        byte[] out = new byte[rawLen];
        int o = 0;
        for (int i = 0; i < len; i += 4) {
            int b0 = (s.charAt(i) < 128) ? DECODE_TABLE[s.charAt(i)] : -1;
            int b1 = (s.charAt(i + 1) < 128) ? DECODE_TABLE[s.charAt(i + 1)] : -1;
            int b2 = (i + 2 < len && s.charAt(i + 2) != '=' && s.charAt(i + 2) < 128) ? DECODE_TABLE[s.charAt(i + 2)] : 0;
            int b3 = (i + 3 < len && s.charAt(i + 3) != '=' && s.charAt(i + 3) < 128) ? DECODE_TABLE[s.charAt(i + 3)] : 0;
            if (o < rawLen) out[o++] = (byte) ((b0 << 2) | (b1 >> 4));
            if (o < rawLen) out[o++] = (byte) (((b1 & 0x0F) << 4) | (b2 >> 2));
            if (o < rawLen) out[o++] = (byte) (((b2 & 0x03) << 6) | b3);
        }
        return out;
    }

    public static String decrypt(String base64Fog, byte key) {
        if (base64Fog == null) return null;
        byte[] bytes = decodeBase64(base64Fog);
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (bytes[i] ^ key);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String decrypt(byte[] data, byte key) {
        if (data == null) return null;
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key);
        }
        return new String(result, StandardCharsets.UTF_8);
    }
}
