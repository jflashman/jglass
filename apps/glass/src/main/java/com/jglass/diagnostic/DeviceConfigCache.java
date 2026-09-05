package com.jglass.diagnostic;

/** Main-thread-owned cache. Failed validation or persistence preserves the last good value. */
final class DeviceConfigCache {
    interface Storage {
        byte[] read();
        boolean write(byte[] json);
    }

    private final Storage storage;
    private String wakePhrase;

    DeviceConfigCache(Storage storage) {
        this.storage = storage;
        try { wakePhrase = DeviceConfigProtocol.parse(storage.read()); }
        catch (Exception invalid) { wakePhrase = null; }
    }

    String wakePhrase() { return wakePhrase; }

    boolean accept(byte[] json) {
        try {
            String candidate = DeviceConfigProtocol.parse(json);
            if (!storage.write(DeviceConfigProtocol.encode(candidate))) return false;
            wakePhrase = candidate;
            return true;
        } catch (Exception invalid) {
            return false;
        }
    }
}
