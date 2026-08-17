package com.jglass.diagnostic;

import java.util.UUID;

final class BleAdvertisementMatcher {
    private static final int TYPE_INCOMPLETE_128_BIT_SERVICE_UUIDS = 0x06;
    private static final int TYPE_COMPLETE_128_BIT_SERVICE_UUIDS = 0x07;
    private static final int UUID_BYTES = 16;

    private BleAdvertisementMatcher() {
    }

    static boolean containsServiceUuid(byte[] scanRecord, UUID serviceUuid) {
        if (scanRecord == null || serviceUuid == null) {
            return false;
        }

        byte[] expected = littleEndianBytes(serviceUuid);
        int offset = 0;
        while (offset < scanRecord.length) {
            int fieldLength = scanRecord[offset] & 0xff;
            if (fieldLength == 0) {
                return false;
            }

            int nextField = offset + fieldLength + 1;
            if (fieldLength < 1 || nextField > scanRecord.length) {
                return false;
            }

            int fieldType = scanRecord[offset + 1] & 0xff;
            if (fieldType == TYPE_INCOMPLETE_128_BIT_SERVICE_UUIDS
                    || fieldType == TYPE_COMPLETE_128_BIT_SERVICE_UUIDS) {
                int dataStart = offset + 2;
                int dataLength = fieldLength - 1;
                for (int uuidOffset = 0;
                     uuidOffset + UUID_BYTES <= dataLength;
                     uuidOffset += UUID_BYTES) {
                    if (matches(scanRecord, dataStart + uuidOffset, expected)) {
                        return true;
                    }
                }
            }

            offset = nextField;
        }
        return false;
    }

    private static byte[] littleEndianBytes(UUID uuid) {
        byte[] bytes = new byte[UUID_BYTES];
        writeLongLittleEndian(uuid.getLeastSignificantBits(), bytes, 0);
        writeLongLittleEndian(uuid.getMostSignificantBits(), bytes, 8);
        return bytes;
    }

    private static void writeLongLittleEndian(long value, byte[] destination, int offset) {
        for (int index = 0; index < 8; index++) {
            destination[offset + index] = (byte) (value >>> (index * 8));
        }
    }

    private static boolean matches(byte[] source, int offset, byte[] expected) {
        for (int index = 0; index < expected.length; index++) {
            if (source[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }
}
