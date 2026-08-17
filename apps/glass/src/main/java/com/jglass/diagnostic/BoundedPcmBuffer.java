package com.jglass.diagnostic;

import java.io.ByteArrayOutputStream;

/** A fixed-capacity, in-memory PCM buffer that never writes captured audio to disk. */
final class BoundedPcmBuffer {
    private final int capacityBytes;
    private final ByteArrayOutputStream bytes;

    BoundedPcmBuffer(int capacityBytes) {
        if (capacityBytes <= 0) {
            throw new IllegalArgumentException("capacityBytes must be positive");
        }
        this.capacityBytes = capacityBytes;
        this.bytes = new ByteArrayOutputStream(capacityBytes);
    }

    int append(byte[] source, int offset, int length) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (offset < 0 || length < 0 || offset + length > source.length) {
            throw new IndexOutOfBoundsException("invalid offset or length");
        }

        int writable = Math.min(length, remaining());
        if (writable > 0) {
            bytes.write(source, offset, writable);
        }
        return writable;
    }

    int size() {
        return bytes.size();
    }

    int remaining() {
        return capacityBytes - bytes.size();
    }

    boolean isFull() {
        return remaining() == 0;
    }

    byte[] snapshot() {
        return bytes.toByteArray();
    }

    void clear() {
        bytes.reset();
    }
}
