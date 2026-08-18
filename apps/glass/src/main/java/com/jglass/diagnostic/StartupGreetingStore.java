package com.jglass.diagnostic;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

final class StartupGreetingStore {
    private static final String PREFERENCES = "startup_greeting";
    private static final String KEY_BRIDGE_URL = "bridge_url";
    private static final String KEY_DEVICE_TOKEN = "device_token";
    private static final String KEY_LAST_DATE = "last_greeting_date";
    private static final String KEY_FALLBACK_VERSION = "fallback_version";

    private final SharedPreferences preferences;
    private final File directory;

    StartupGreetingStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        directory = new File(context.getFilesDir(), "startup-greeting");
    }

    boolean saveConfiguration(String bridgeUrl, String deviceToken) {
        if (!StartupGreetingClient.isSafeBridgeBaseUrl(bridgeUrl)
                || deviceToken == null || deviceToken.length() < 32 || deviceToken.length() > 512) {
            return false;
        }
        preferences.edit()
                .putString(KEY_BRIDGE_URL, bridgeUrl)
                .putString(KEY_DEVICE_TOKEN, deviceToken)
                .apply();
        return true;
    }

    String bridgeUrl() {
        return preferences.getString(KEY_BRIDGE_URL, null);
    }

    String deviceToken() {
        return preferences.getString(KEY_DEVICE_TOKEN, null);
    }

    String lastGreetingDate() {
        return preferences.getString(KEY_LAST_DATE, null);
    }

    void recordGreetingDate(String date) {
        preferences.edit().putString(KEY_LAST_DATE, date).apply();
    }

    String fallbackVersion() {
        return preferences.getString(KEY_FALLBACK_VERSION, null);
    }

    void installFallbacks(StartupGreetingProtocol.FallbackAssets assets) throws IOException {
        ensureDirectory();
        writeAtomically(fallbackPath(StartupGreetingRules.Period.MORNING), assets.morning.bytes);
        writeAtomically(fallbackPath(StartupGreetingRules.Period.AFTERNOON), assets.afternoon.bytes);
        writeAtomically(fallbackPath(StartupGreetingRules.Period.EVENING), assets.evening.bytes);
        preferences.edit().putString(KEY_FALLBACK_VERSION, assets.version).commit();
    }

    File fallbackFile(StartupGreetingRules.Period period) {
        File file = fallbackPath(period);
        return file.isFile() ? file : null;
    }

    private File fallbackPath(StartupGreetingRules.Period period) {
        return new File(directory, "fallback-" + period.value + ".mp3");
    }

    File writeDynamic(byte[] audio) throws IOException {
        ensureDirectory();
        File file = new File(directory, "dynamic.mp3");
        writeAtomically(file, audio);
        return file;
    }

    void deleteDynamic() {
        File file = new File(directory, "dynamic.mp3");
        if (file.exists()) file.delete();
        File temporary = new File(directory, "dynamic.mp3.tmp");
        if (temporary.exists()) temporary.delete();
    }

    private void ensureDirectory() throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create startup audio directory");
        }
    }

    private static void writeAtomically(File target, byte[] bytes) throws IOException {
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        FileOutputStream output = new FileOutputStream(temporary, false);
        try {
            output.write(bytes);
            output.getFD().sync();
        } finally {
            output.close();
        }
        if (target.exists() && !target.delete()) throw new IOException("Could not replace startup audio");
        if (!temporary.renameTo(target)) throw new IOException("Could not install startup audio");
    }
}
