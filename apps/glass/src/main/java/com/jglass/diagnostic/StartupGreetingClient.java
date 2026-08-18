package com.jglass.diagnostic;

import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class StartupGreetingClient {
    interface Listener {
        void onSuccess(StartupGreetingProtocol.Response response);

        void onFailure();
    }

    private static final int CONNECT_TIMEOUT_MILLIS = 4000;
    private static final int READ_TIMEOUT_MILLIS = 8000;
    private volatile boolean cancelled;
    private volatile HttpURLConnection activeConnection;
    private Thread worker;

    static boolean isSafeBridgeBaseUrl(String value) {
        if (value == null || value.length() == 0 || value.length() > 256) return false;
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        String path = uri.getPath();
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && uri.getHost() != null && uri.getHost().length() > 0
                && uri.getUserInfo() == null
                && uri.getQuery() == null && uri.getFragment() == null
                && (path == null || path.length() == 0 || "/".equals(path));
    }

    synchronized void request(final String baseUrl, final String token,
            final String fallbackVersion, final Listener listener) {
        cancel();
        cancelled = false;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                perform(baseUrl, token, fallbackVersion, listener);
            }
        }, "jglass-startup-network");
        worker.start();
    }

    synchronized void cancel() {
        cancelled = true;
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
        if (worker != null) worker.interrupt();
        worker = null;
    }

    private void perform(String baseUrl, String token, String fallbackVersion, Listener listener) {
        HttpURLConnection connection = null;
        try {
            if (!isSafeBridgeBaseUrl(baseUrl) || token == null || token.length() < 32) {
                throw new IOException("Startup bridge is not configured");
            }
            String normalized = baseUrl.endsWith("/")
                    ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            connection = (HttpURLConnection) new URL(normalized + "/v1/startup-greeting").openConnection();
            activeConnection = connection;
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            byte[] request = StartupGreetingProtocol.requestBytes(fallbackVersion);
            connection.setFixedLengthStreamingMode(request.length);
            OutputStream output = connection.getOutputStream();
            try {
                output.write(request);
            } finally {
                output.close();
            }
            if (cancelled) return;
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Startup bridge rejected request");
            String contentType = connection.getHeaderField("Content-Type");
            if (contentType == null || !"application/json".equalsIgnoreCase(contentType.split(";", 2)[0].trim())) {
                throw new IOException("Startup bridge returned an unsupported type");
            }
            int contentLength = connection.getContentLength();
            if (contentLength <= 0 || contentLength > StartupGreetingProtocol.MAX_RESPONSE_BYTES) {
                throw new IOException("Startup bridge response size is invalid");
            }
            byte[] responseBytes = readBounded(connection.getInputStream());
            if (!cancelled) listener.onSuccess(StartupGreetingProtocol.parse(responseBytes));
        } catch (Exception ignored) {
            if (!cancelled) listener.onFailure();
        } finally {
            activeConnection = null;
            if (connection != null) connection.disconnect();
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > StartupGreetingProtocol.MAX_RESPONSE_BYTES) {
                    throw new IOException("Startup bridge response exceeded its limit");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
