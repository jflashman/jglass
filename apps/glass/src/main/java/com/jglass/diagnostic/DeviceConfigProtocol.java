package com.jglass.diagnostic;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;
import java.util.Map;

final class DeviceConfigProtocol {
    static final String PROTOCOL = "jglass/device-config/1";
    static final int MAX_RESPONSE_BYTES = 1024;
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private DeviceConfigProtocol() { }

    static String normalizeWakePhrase(Object value) {
        if (!(value instanceof String)) throw new IllegalArgumentException("Invalid wake phrase");
        String raw = (String) value;
        if (raw.length() > 128 || !raw.matches("[A-Za-z ]+")) {
            throw new IllegalArgumentException("Invalid wake phrase");
        }
        String phrase = raw.trim().replaceAll(" +", " ").toUpperCase(Locale.US);
        if (phrase.length() == 0 || phrase.length() > 32 || phrase.split(" ").length > 4) {
            throw new IllegalArgumentException("Invalid wake phrase");
        }
        return phrase;
    }

    static String parse(byte[] json) throws Exception {
        if (json == null || json.length == 0 || json.length > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("Invalid device configuration size");
        }
        String text = UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(json)).toString();
        Object value = StrictJson.parse(text);
        if (!(value instanceof Map)) throw new IllegalArgumentException("Invalid device configuration");
        Map<?, ?> root = (Map<?, ?>) value;
        if (!PROTOCOL.equals(root.get("protocol"))) {
            throw new IllegalArgumentException("Unsupported device configuration protocol");
        }
        return normalizeWakePhrase(root.get("wakePhrase"));
    }

    static byte[] encode(String wakePhrase) {
        return ("{\"protocol\":\"" + PROTOCOL + "\",\"wakePhrase\":\""
                + normalizeWakePhrase(wakePhrase) + "\"}").getBytes(UTF_8);
    }
}
