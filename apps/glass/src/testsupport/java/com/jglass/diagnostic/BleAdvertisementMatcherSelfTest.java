package com.jglass.diagnostic;

import java.util.UUID;

public final class BleAdvertisementMatcherSelfTest {
    private static final UUID JGLASS_SERVICE =
            UUID.fromString("c91249f4-d2a1-4d93-8f66-ce0a24753b9d");

    public static void main(String[] args) {
        findsCompleteServiceList();
        findsIncompleteServiceList();
        findsSecondServiceInList();
        rejectsDifferentService();
        rejectsTruncatedField();
        rejectsMissingRecord();
        System.out.println("BleAdvertisementMatcherSelfTest: PASS");
    }

    private static void findsCompleteServiceList() {
        require(BleAdvertisementMatcher.containsServiceUuid(
                record(0x07, jglassBytes()),
                JGLASS_SERVICE), "complete UUID list should match");
    }

    private static void findsIncompleteServiceList() {
        require(BleAdvertisementMatcher.containsServiceUuid(
                record(0x06, jglassBytes()),
                JGLASS_SERVICE), "incomplete UUID list should match");
    }

    private static void findsSecondServiceInList() {
        byte[] services = new byte[32];
        System.arraycopy(jglassBytes(), 0, services, 16, 16);
        require(BleAdvertisementMatcher.containsServiceUuid(
                record(0x07, services),
                JGLASS_SERVICE), "second UUID should match");
    }

    private static void rejectsDifferentService() {
        require(!BleAdvertisementMatcher.containsServiceUuid(
                record(0x07, new byte[16]),
                JGLASS_SERVICE), "different UUID should not match");
    }

    private static void rejectsTruncatedField() {
        require(!BleAdvertisementMatcher.containsServiceUuid(
                new byte[]{0x11, 0x07, 0x01},
                JGLASS_SERVICE), "truncated field should not match");
    }

    private static void rejectsMissingRecord() {
        require(!BleAdvertisementMatcher.containsServiceUuid(null, JGLASS_SERVICE),
                "missing record should not match");
    }

    private static byte[] record(int type, byte[] serviceData) {
        byte[] record = new byte[3 + 2 + serviceData.length + 1];
        record[0] = 0x02;
        record[1] = 0x01;
        record[2] = 0x06;
        record[3] = (byte) (serviceData.length + 1);
        record[4] = (byte) type;
        System.arraycopy(serviceData, 0, record, 5, serviceData.length);
        return record;
    }

    private static byte[] jglassBytes() {
        return new byte[]{
                (byte) 0x9d, 0x3b, 0x75, 0x24,
                0x0a, (byte) 0xce, 0x66, (byte) 0x8f,
                (byte) 0x93, 0x4d, (byte) 0xa1, (byte) 0xd2,
                (byte) 0xf4, 0x49, 0x12, (byte) 0xc9
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
