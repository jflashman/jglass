package com.jglass.diagnostic;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public final class DeviceConfigSelfTest {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static void main(String[] args) throws Exception {
        Map<?, ?> fixture = (Map<?, ?>) StrictJson.parse(new String(Files.readAllBytes(Paths.get(args[0])), UTF8));
        for (Object entry : (List<?>) fixture.get("valid")) {
            Map<?, ?> item = (Map<?, ?>) entry;
            require(item.get("expected").equals(DeviceConfigProtocol.normalizeWakePhrase(item.get("input"))), "normalization parity");
        }
        for (Object invalid : (List<?>) fixture.get("invalid")) {
            try {
                DeviceConfigProtocol.normalizeWakePhrase(invalid);
                throw new AssertionError("accepted invalid phrase");
            } catch (IllegalArgumentException expected) { }
        }
        Map<?, ?> response = (Map<?, ?>) fixture.get("response");
        require(DeviceConfigProtocol.PROTOCOL.equals(response.get("protocol")), "shared protocol");
        require(response.get("wakePhrase").equals(DeviceConfigProtocol.parse(DeviceConfigProtocol.encode("Jarvis"))), "shared phrase");
        require("JARVIS".equals(DeviceConfigProtocol.parse(bytes("{\"protocol\":\"jglass/device-config/1\",\"wakePhrase\":\"Jarvis\",\"future\":{\"enabled\":true}}"))), "unknown fields");
        MemoryStorage storage = new MemoryStorage();
        DeviceConfigCache cache = new DeviceConfigCache(storage);
        require(cache.wakePhrase() == null, "no implicit enrollment on first boot");
        require(cache.accept(DeviceConfigProtocol.encode("Jarvis")), "valid cache write");
        require("JARVIS".equals(new DeviceConfigCache(storage).wakePhrase()), "offline reload");
        for (String invalid : new String[] {
                "{}", "[]", "null", "{", "{\"protocol\":2,\"wakePhrase\":\"Jarvis\"}",
                "{\"protocol\":\"jglass/device-config/2\",\"wakePhrase\":\"Jarvis\"}",
                "{\"protocol\":\"jglass/device-config/1\",\"wakePhrase\":null}",
                "{\"protocol\":\"jglass/device-config/1\",\"wakePhrase\":\"Jarvis\",\"wakePhrase\":\"other\"}" }) {
            require(!cache.accept(bytes(invalid)), "reject invalid response");
            require("JARVIS".equals(cache.wakePhrase()), "preserve cached phrase");
            require("JARVIS".equals(new DeviceConfigCache(storage).wakePhrase()), "preserve persisted phrase");
        }
        require(!cache.accept(new byte[1025]), "response size limit");
        byte[] malformedUtf8 = bytes("{\"protocol\":\"jglass/device-config/1\",\"wakePhrase\":\"Jarvis\",\"extra\":\"x\"}");
        malformedUtf8[malformedUtf8.length - 3] = (byte) 0xff;
        require(!cache.accept(malformedUtf8), "invalid UTF-8 in ignored field");
        storage.fail = true;
        require(!cache.accept(DeviceConfigProtocol.encode("Hello Jarvis")), "failed write");
        require("JARVIS".equals(cache.wakePhrase()), "failed write rollback");
        storage.fail = false;
        require(cache.accept(DeviceConfigProtocol.encode("Hello Jarvis")), "phrase change");
        require("HELLO JARVIS".equals(new DeviceConfigCache(storage).wakePhrase()), "changed offline phrase");
        storage.json = bytes("corrupt");
        require(new DeviceConfigCache(storage).wakePhrase() == null, "corrupt cache disables phrase");
        StringBuilder deep = new StringBuilder();
        for (int index = 0; index < 10000; index++) deep.append('[');
        deep.append('0');
        for (int index = 0; index < 10000; index++) deep.append(']');
        try {
            StrictJson.parse(deep.toString());
            throw new AssertionError("unbounded JSON nesting");
        } catch (StrictJson.ParseException expected) { }
        System.out.println("DeviceConfigSelfTest: PASS");
    }

    private static byte[] bytes(String value) { return value.getBytes(UTF8); }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class MemoryStorage implements DeviceConfigCache.Storage {
        byte[] json;
        boolean fail;
        @Override public byte[] read() { return json; }
        @Override public boolean write(byte[] value) {
            if (fail) return false;
            json = value;
            return true;
        }
    }
}
