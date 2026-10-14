package app.nnvc.chess.extension.stockfish;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * CryptoStream – On-the-fly streaming cipher.
 * Applies continuous cyclic key XOR without buffering entire multi-megabyte streams in memory.
 */
public class CryptoStream extends FilterInputStream {

    private final byte[] key;
    private long position;

    public CryptoStream(InputStream in, byte[] key) {
        this(in, key, 0L);
    }

    public CryptoStream(InputStream in, byte[] key, long startPosition) {
        super(in);
        this.key = (key != null && key.length > 0) ? key : new byte[]{0};
        this.position = Math.max(0L, startPosition);
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b == -1) return -1;
        int k = key[(int) (position % key.length)] & 0xFF;
        position++;
        return (b ^ k) & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int bytesRead = super.read(b, off, len);
        if (bytesRead == -1) return -1;
        int klen = key.length;
        int end = off + bytesRead;
        long pos = position;
        for (int i = off; i < end; i++) {
            b[i] = (byte) (b[i] ^ key[(int) (pos % klen)]);
            pos++;
        }
        position = pos;
        return bytesRead;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(n);
        if (skipped > 0) {
            position += skipped;
        }
        return skipped;
    }

    private long markPosition = 0L;

    @Override
    public synchronized void mark(int readlimit) {
        super.mark(readlimit);
        markPosition = position;
    }

    @Override
    public synchronized void reset() throws IOException {
        super.reset();
        position = markPosition;
    }
}
