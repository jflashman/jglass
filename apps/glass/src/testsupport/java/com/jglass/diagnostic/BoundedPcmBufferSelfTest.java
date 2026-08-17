package com.jglass.diagnostic;

import java.util.Arrays;

public final class BoundedPcmBufferSelfTest {
    public static void main(String[] args) {
        rejectsInvalidCapacity();
        boundsWrites();
        snapshotsAndClears();
        System.out.println("BoundedPcmBufferSelfTest: PASS");
    }

    private static void rejectsInvalidCapacity() {
        try {
            new BoundedPcmBuffer(0);
            fail("zero capacity should fail");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void boundsWrites() {
        BoundedPcmBuffer buffer = new BoundedPcmBuffer(4);
        byte[] input = new byte[]{1, 2, 3, 4, 5};
        require(buffer.append(input, 0, input.length) == 4, "write should be bounded");
        require(buffer.size() == 4, "size should equal capacity");
        require(buffer.remaining() == 0, "remaining should be zero");
        require(buffer.isFull(), "buffer should be full");
        require(buffer.append(input, 0, 1) == 0, "full buffer should reject more bytes");
    }

    private static void snapshotsAndClears() {
        BoundedPcmBuffer buffer = new BoundedPcmBuffer(4);
        buffer.append(new byte[]{9, 8}, 0, 2);
        require(Arrays.equals(buffer.snapshot(), new byte[]{9, 8}), "snapshot mismatch");
        buffer.clear();
        require(buffer.size() == 0, "clear should reset the buffer");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            fail(message);
        }
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }
}
