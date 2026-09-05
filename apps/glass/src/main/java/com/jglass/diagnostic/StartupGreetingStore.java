package com.jglass.diagnostic;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

final class StartupGreetingStore {
    private static final String PREFERENCES = "startup_greeting";
    private static final String KEY_BRIDGE_URL = "bridge_url";
    private static final String KEY_DEVICE_TOKEN = "device_token";
    private static final String KEY_DEVICE_CONFIG = "device_config";
    private static final String KEY_LAST_DATE = "last_greeting_date";
    private static final String KEY_LEGACY_FALLBACK_VERSION = "fallback_version";
    private static final String KEY_DEBUG_REPLAY_MODE = "debug_replay_mode";
    private static final String KEY_APPROXIMATE_BOOT_TIME = "approximate_boot_time";
    private static final long BOOT_TIME_TOLERANCE_MILLIS = 60_000L;
    private static final long FIRST_LAUNCH_BOOT_WINDOW_MILLIS = 5 * 60_000L;
    static final String REPLAY_DAILY = "daily";
    static final String REPLAY_ALWAYS = "always";

    private final Context context;
    private final SharedPreferences preferences;
    private final File directory;

    StartupGreetingStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        directory = new File(context.getFilesDir(), "startup-greeting");
        discardLegacyFallbacks();
    }

    boolean saveConfiguration(String bridgeUrl, String deviceToken) {
        if (!StartupGreetingClient.isSafeBridgeBaseUrl(bridgeUrl)
                || !BridgeJsonClient.isSafeToken(deviceToken)) {
            return false;
        }
        SharedPreferences.Editor editor = preferences.edit();
        if (!bridgeUrl.equals(bridgeUrl()) || !deviceToken.equals(deviceToken())) {
            editor.remove(KEY_DEVICE_CONFIG);
        }
        editor
                .putString(KEY_BRIDGE_URL, bridgeUrl)
                .putString(KEY_DEVICE_TOKEN, deviceToken)
                .apply();
        return true;
    }

    DeviceConfigCache deviceConfigCache() {
        return new DeviceConfigCache(new DeviceConfigCache.Storage() {
            private final Charset utf8 = Charset.forName("UTF-8");

            @Override public byte[] read() {
                String value = preferences.getString(KEY_DEVICE_CONFIG, null);
                return value == null ? null : value.getBytes(utf8);
            }

            @Override public boolean write(byte[] json) {
                // Same atomic preference file as enrollment; never persist the raw response.
                preferences.edit().putString(KEY_DEVICE_CONFIG, new String(json, utf8)).apply();
                return true;
            }
        });
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
        return StartupGreetingRules.BUNDLED_FALLBACK_VERSION;
    }

    boolean saveDebugReplayMode(String mode) {
        if (!REPLAY_DAILY.equals(mode) && !REPLAY_ALWAYS.equals(mode)) return false;
        preferences.edit().putString(KEY_DEBUG_REPLAY_MODE, mode).apply();
        return true;
    }

    boolean debugReplayAlways() {
        return REPLAY_ALWAYS.equals(preferences.getString(KEY_DEBUG_REPLAY_MODE, REPLAY_DAILY));
    }

    boolean claimNewBootSession() {
        long elapsed = SystemClock.elapsedRealtime();
        long approximateBootTime = System.currentTimeMillis() - elapsed;
        long previous = preferences.getLong(KEY_APPROXIMATE_BOOT_TIME, Long.MIN_VALUE);
        preferences.edit().putLong(KEY_APPROXIMATE_BOOT_TIME, approximateBootTime).apply();
        if (previous == Long.MIN_VALUE) return elapsed <= FIRST_LAUNCH_BOOT_WINDOW_MILLIS;
        return Math.abs(previous - approximateBootTime) > BOOT_TIME_TOLERANCE_MILLIS;
    }

    File bundledFallbackFile(StartupGreetingRules.Period period,
            StartupGreetingRules.FallbackCause cause) throws IOException {
        String resourceName = StartupGreetingRules.fallbackResourceName(period, cause);
        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier(resourceName, "raw", context.getPackageName());
        if (resourceId == 0) throw new IOException("Bundled startup audio is missing");
        ensureDirectory();
        File target = new File(directory, resourceName + "-"
                + StartupGreetingRules.BUNDLED_FALLBACK_VERSION + ".mp3");
        if (!target.isFile()) {
            InputStream input = resources.openRawResource(resourceId);
            try {
                writeAtomically(target, readBounded(input, 128 * 1024));
            } finally {
                input.close();
            }
        }
        return target;
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

    private void discardLegacyFallbacks() {
        for (StartupGreetingRules.Period period : StartupGreetingRules.Period.values()) {
            File legacy = new File(directory, "fallback-" + period.value + ".mp3");
            if (legacy.exists()) legacy.delete();
        }
        preferences.edit().remove(KEY_LEGACY_FALLBACK_VERSION).apply();
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

    private static byte[] readBounded(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > maximum) {
                throw new IOException("Bundled startup audio exceeds its size limit");
            }
            output.write(buffer, 0, count);
        }
        byte[] bytes = output.toByteArray();
        boolean mp3 = bytes.length >= 3
                && ((bytes[0] == 0x49 && bytes[1] == 0x44 && bytes[2] == 0x33)
                || ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xe0) == 0xe0));
        if (!mp3) throw new IOException("Bundled startup audio is not MP3");
        return bytes;
    }
}
