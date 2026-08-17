package com.jglass.diagnostic;

import android.net.Uri;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

final class BridgeHealthProbe {
    interface Listener {
        void onResult(boolean reachable);
    }

    private static final int TIMEOUT_MILLIS = 3000;

    private BridgeHealthProbe() {
    }

    static boolean isSafeHealthUrl(String value) {
        if (value == null || value.length() == 0) {
            return false;
        }

        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        boolean supportedScheme = "http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme);
        return supportedScheme
                && uri.getHost() != null
                && uri.getHost().length() > 0
                && uri.getUserInfo() == null;
    }

    static void check(final String value, final Listener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                listener.onResult(request(value));
            }
        }, "jglass-lan-probe").start();
    }

    private static boolean request(String value) {
        if (!isSafeHealthUrl(value)) {
            return false;
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(value).openConnection();
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.connect();
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException ignored) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
