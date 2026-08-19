package gj.cloud.ops.global.io;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

// 외부 프로세스가 stdout/stderr를 무제한 출력해 Ops heap을 고갈시키지 못하도록
// 상한을 둔다. 긴 빌드 로그의 최종 실패 원인이 버려지지 않도록 앞·뒷부분을 절반씩 보관한다.
public final class BoundedOutputStream extends OutputStream {

    private final int maxBytes;
    private final int prefixLimit;
    private final ByteArrayOutputStream prefix;
    private final byte[] tail;
    private int tailSize;
    private int tailWritePosition;
    private long totalBytes;

    public BoundedOutputStream(int maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.maxBytes = maxBytes;
        this.prefixLimit = maxBytes / 2;
        this.prefix = new ByteArrayOutputStream(Math.min(prefixLimit, 8192));
        this.tail = new byte[maxBytes - prefixLimit];
    }

    @Override
    public synchronized void write(int value) {
        totalBytes++;
        if (prefix.size() < prefixLimit) {
            prefix.write(value);
        } else {
            appendTail((byte) value);
        }
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        if (offset < 0 || length < 0 || offset + length > bytes.length) {
            throw new IndexOutOfBoundsException();
        }
        if (length == 0) {
            return;
        }

        totalBytes += length;
        int prefixRemaining = prefixLimit - prefix.size();
        int prefixBytes = Math.min(prefixRemaining, length);
        if (prefixBytes > 0) {
            prefix.write(bytes, offset, prefixBytes);
        }
        appendTail(bytes, offset + prefixBytes, length - prefixBytes);
    }

    public synchronized String toString(Charset charset) {
        long discardedBytes = Math.max(0, totalBytes - prefix.size() - tailSize);
        if (discardedBytes == 0) {
            ByteArrayOutputStream complete = new ByteArrayOutputStream((int) totalBytes);
            complete.writeBytes(prefix.toByteArray());
            complete.writeBytes(orderedTail());
            return complete.toString(charset);
        }
        return prefix.toString(charset)
                + "\n...[output truncated: " + discardedBytes + " bytes discarded]...\n"
                + new String(orderedTail(), charset);
    }

    private void appendTail(byte value) {
        tail[tailWritePosition] = value;
        tailWritePosition = (tailWritePosition + 1) % tail.length;
        tailSize = Math.min(tail.length, tailSize + 1);
    }

    private void appendTail(byte[] bytes, int offset, int length) {
        if (length <= 0) {
            return;
        }
        if (length >= tail.length) {
            System.arraycopy(bytes, offset + length - tail.length, tail, 0, tail.length);
            tailSize = tail.length;
            tailWritePosition = 0;
            return;
        }

        int firstCopy = Math.min(length, tail.length - tailWritePosition);
        System.arraycopy(bytes, offset, tail, tailWritePosition, firstCopy);
        int remaining = length - firstCopy;
        if (remaining > 0) {
            System.arraycopy(bytes, offset + firstCopy, tail, 0, remaining);
        }
        tailWritePosition = (tailWritePosition + length) % tail.length;
        tailSize = Math.min(tail.length, tailSize + length);
    }

    private byte[] orderedTail() {
        byte[] ordered = new byte[tailSize];
        if (tailSize == 0) {
            return ordered;
        }
        int start = tailSize == tail.length ? tailWritePosition : 0;
        int firstCopy = Math.min(tailSize, tail.length - start);
        System.arraycopy(tail, start, ordered, 0, firstCopy);
        if (firstCopy < tailSize) {
            System.arraycopy(tail, 0, ordered, firstCopy, tailSize - firstCopy);
        }
        return ordered;
    }
}
