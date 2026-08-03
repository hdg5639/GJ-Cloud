package gj.cloud.ops.global.io;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

// 외부 프로세스가 stdout/stderr를 무제한 출력해 Ops heap을 고갈시키지 못하도록
// 앞부분만 보관하고 나머지는 지속적으로 소비하되 메모리에 쌓지 않는다.
public final class BoundedOutputStream extends OutputStream {

    private final ByteArrayOutputStream buffer;
    private final int maxBytes;
    private long discardedBytes;

    public BoundedOutputStream(int maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.maxBytes = maxBytes;
        this.buffer = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
    }

    @Override
    public synchronized void write(int value) {
        if (buffer.size() < maxBytes) {
            buffer.write(value);
        } else {
            discardedBytes++;
        }
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
        int remaining = maxBytes - buffer.size();
        int captured = Math.min(Math.max(remaining, 0), length);
        if (captured > 0) {
            buffer.write(bytes, offset, captured);
        }
        discardedBytes += length - captured;
    }

    public synchronized String toString(Charset charset) {
        String value = buffer.toString(charset);
        if (discardedBytes == 0) {
            return value;
        }
        return value + "\n...[output truncated: " + discardedBytes + " bytes discarded]";
    }
}
