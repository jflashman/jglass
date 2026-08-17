package com.jglass.diagnostic;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

final class BleControlFraming {
    static final int HEADER_LENGTH = 10;
    static final int MAXIMUM_CONTROL_BYTES = 4 * 1024;
    static final long ASSEMBLY_TIMEOUT_MILLIS = 5_000L;

    private static final int MAGIC = 0x4A;
    private static final int VERSION = 1;
    private static final int KIND_CONTROL = 1;
    private static final int KIND_CANCEL = 2;
    private static final int FLAG_FIRST = 1;
    private static final int FLAG_FINAL = 2;

    private BleControlFraming() {
    }

    static List<byte[]> fragment(byte[] message, int streamId, int maximumPacketLength) {
        if (message == null || message.length > MAXIMUM_CONTROL_BYTES) {
            throw new IllegalArgumentException("message_too_large");
        }
        if (streamId <= 0 || streamId > 0xffff) {
            throw new IllegalArgumentException("invalid_stream");
        }
        if (maximumPacketLength <= HEADER_LENGTH) {
            throw new IllegalArgumentException("packet_too_small");
        }

        int payloadCapacity = maximumPacketLength - HEADER_LENGTH;
        int fragmentCount = Math.max(1,
                (message.length + payloadCapacity - 1) / payloadCapacity);
        List<byte[]> packets = new ArrayList<byte[]>(fragmentCount);
        for (int index = 0; index < fragmentCount; index++) {
            int lower = Math.min(index * payloadCapacity, message.length);
            int upper = Math.min(lower + payloadCapacity, message.length);
            int payloadLength = upper - lower;
            byte[] packet = new byte[HEADER_LENGTH + payloadLength];
            packet[0] = (byte) MAGIC;
            packet[1] = (byte) VERSION;
            packet[2] = (byte) KIND_CONTROL;
            packet[3] = (byte) ((index == 0 ? FLAG_FIRST : 0)
                    | (index == fragmentCount - 1 ? FLAG_FINAL : 0));
            putUnsignedShort(packet, 4, streamId);
            putUnsignedShort(packet, 6, index);
            putUnsignedShort(packet, 8, index == 0 ? message.length : 0);
            System.arraycopy(message, lower, packet, HEADER_LENGTH, payloadLength);
            packets.add(packet);
        }
        return packets;
    }

    static final class Reassembler {
        private int streamId = -1;
        private int expectedLength;
        private int nextSequence;
        private long updatedAtMillis;
        private ByteArrayOutputStream bytes;

        byte[] accept(byte[] packet, long nowMillis) {
            if (bytes != null && nowMillis - updatedAtMillis > ASSEMBLY_TIMEOUT_MILLIS) {
                reset();
            }
            if (packet == null || packet.length < HEADER_LENGTH) {
                throw new IllegalArgumentException("truncated_header");
            }
            if (unsigned(packet[0]) != MAGIC) {
                throw new IllegalArgumentException("invalid_magic");
            }
            if (unsigned(packet[1]) != VERSION) {
                throw new IllegalArgumentException("unsupported_version");
            }

            int kind = unsigned(packet[2]);
            int flags = unsigned(packet[3]);
            if ((flags & ~0x03) != 0) {
                throw new IllegalArgumentException("invalid_flags");
            }
            int incomingStreamId = unsignedShort(packet, 4);
            int sequence = unsignedShort(packet, 6);
            int totalLength = unsignedShort(packet, 8);

            if (kind == KIND_CANCEL) {
                if (packet.length != HEADER_LENGTH || totalLength != 0
                        || (flags & FLAG_FINAL) == 0) {
                    throw new IllegalArgumentException("invalid_cancel");
                }
                reset();
                return null;
            }
            if (kind != KIND_CONTROL) {
                throw new IllegalArgumentException("invalid_kind");
            }

            boolean first = (flags & FLAG_FIRST) != 0;
            boolean last = (flags & FLAG_FINAL) != 0;
            if (first) {
                if (sequence != 0 || totalLength > MAXIMUM_CONTROL_BYTES) {
                    throw new IllegalArgumentException("invalid_first_fragment");
                }
                if (bytes != null) {
                    if (streamId == incomingStreamId && sequence < nextSequence) {
                        return null;
                    }
                    throw new IllegalArgumentException("too_many_streams");
                }
                streamId = incomingStreamId;
                expectedLength = totalLength;
                nextSequence = 0;
                bytes = new ByteArrayOutputStream(totalLength);
            } else if (totalLength != 0) {
                throw new IllegalArgumentException("invalid_length");
            }

            if (bytes == null || streamId != incomingStreamId) {
                throw new IllegalArgumentException("missing_first_fragment");
            }
            if (sequence < nextSequence) {
                return null;
            }
            if (sequence != nextSequence) {
                throw new IllegalArgumentException("invalid_sequence");
            }

            int payloadLength = packet.length - HEADER_LENGTH;
            if (bytes.size() + payloadLength > expectedLength) {
                reset();
                throw new IllegalArgumentException("invalid_length");
            }
            bytes.write(packet, HEADER_LENGTH, payloadLength);
            nextSequence++;
            updatedAtMillis = nowMillis;

            if (!last) {
                return null;
            }
            if (bytes.size() != expectedLength) {
                reset();
                throw new IllegalArgumentException("invalid_length");
            }
            byte[] completed = bytes.toByteArray();
            reset();
            return completed;
        }

        void reset() {
            streamId = -1;
            expectedLength = 0;
            nextSequence = 0;
            updatedAtMillis = 0;
            bytes = null;
        }
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return (unsigned(bytes[offset]) << 8) | unsigned(bytes[offset + 1]);
    }

    private static void putUnsignedShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) ((value >> 8) & 0xff);
        bytes[offset + 1] = (byte) (value & 0xff);
    }
}
