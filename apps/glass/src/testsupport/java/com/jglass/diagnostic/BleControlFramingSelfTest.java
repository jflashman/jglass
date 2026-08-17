package com.jglass.diagnostic;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class BleControlFramingSelfTest {
    public static void main(String[] args) {
        roundTripsFragmentedMessage();
        rejectsOutOfOrderFragment();
        expiresIncompleteStream();
        rejectsOversizedMessage();
        System.out.println("BleControlFramingSelfTest: PASS");
    }

    private static void roundTripsFragmentedMessage() {
        byte[] message = ("{\"protocol\":\"jglass/1\",\"type\":\"gateway.hello\","
                + "\"payload\":{\"transport\":\"ble-gatt\"}}")
                .getBytes(StandardCharsets.UTF_8);
        List<byte[]> packets = BleControlFraming.fragment(message, 7, 20);
        assertTrue(packets.size() > 1, "expected fragmentation");

        BleControlFraming.Reassembler reassembler = new BleControlFraming.Reassembler();
        byte[] completed = null;
        long now = 1_000L;
        for (byte[] packet : packets) {
            completed = reassembler.accept(packet, now++);
        }
        assertTrue(Arrays.equals(message, completed), "round trip differs");
    }

    private static void rejectsOutOfOrderFragment() {
        List<byte[]> packets = BleControlFraming.fragment(
                "twenty-one-byte-value".getBytes(StandardCharsets.UTF_8), 9, 20);
        BleControlFraming.Reassembler reassembler = new BleControlFraming.Reassembler();
        reassembler.accept(packets.get(0), 1_000L);
        expectFailure(new Runnable() {
            @Override
            public void run() {
                reassembler.accept(packets.get(2), 1_001L);
            }
        });
    }

    private static void expiresIncompleteStream() {
        List<byte[]> packets = BleControlFraming.fragment(
                "twenty-one-byte-value".getBytes(StandardCharsets.UTF_8), 11, 20);
        BleControlFraming.Reassembler reassembler = new BleControlFraming.Reassembler();
        reassembler.accept(packets.get(0), 1_000L);
        expectFailure(new Runnable() {
            @Override
            public void run() {
                reassembler.accept(
                        packets.get(1),
                        1_000L + BleControlFraming.ASSEMBLY_TIMEOUT_MILLIS + 1);
            }
        });
    }

    private static void rejectsOversizedMessage() {
        expectFailure(new Runnable() {
            @Override
            public void run() {
                BleControlFraming.fragment(
                        new byte[BleControlFraming.MAXIMUM_CONTROL_BYTES + 1], 1, 20);
            }
        });
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected failure");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
