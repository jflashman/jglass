package com.jglass.diagnostic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

/** Bounded API-19 HTTP transport. Each request owns its cancellation state. */
final class BridgeJsonClient {
    interface Listener {
        void onSuccess(byte[] json);
        void onFailure();
    }

    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    private final ConnectionFactory connections;
    private final long deadlineMillis;
    private Request current;

    BridgeJsonClient() {
        this(new ConnectionFactory() {
            @Override public HttpURLConnection open(URL url) throws IOException {
                return (HttpURLConnection) url.openConnection();
            }
        }, 12_000L);
    }

    BridgeJsonClient(ConnectionFactory connections, long deadlineMillis) {
        this.connections = connections;
        this.deadlineMillis = deadlineMillis;
    }

    static boolean isSafeBaseUrl(String value) {
        if (value == null || value.length() == 0 || value.length() > 256) return false;
        try {
            URI uri = new URI(value);
            String path = uri.getRawPath();
            int port = uri.getPort();
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getHost().length() > 0
                    && uri.getRawUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null && (port == -1 || (port > 0 && port <= 65535))
                    && (path == null || path.length() == 0 || "/".equals(path));
        } catch (Exception invalid) {
            return false;
        }
    }

    static boolean isSafeToken(String value) {
        if (value == null || value.length() < 32 || value.length() > 512) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x21 || character > 0x7e) return false;
        }
        return true;
    }

    synchronized void request(String baseUrl, String token, String path, byte[] body,
            int maximumBytes, Listener listener) {
        cancel();
        current = new Request(baseUrl, token, path, body, maximumBytes, listener);
        current.worker.start();
    }

    synchronized void cancel() {
        Request previous = current;
        current = null;
        if (previous != null) previous.stop();
    }

    private final class Request implements Runnable {
        final String baseUrl;
        final String token;
        final String path;
        final byte[] body;
        final int maximumBytes;
        final Listener listener;
        final Thread worker;
        volatile boolean cancelled;
        volatile boolean timedOut;
        volatile HttpURLConnection connection;

        Request(String baseUrl, String token, String path, byte[] body,
                int maximumBytes, Listener listener) {
            this.baseUrl = baseUrl;
            this.token = token;
            this.path = path;
            this.body = body;
            this.maximumBytes = maximumBytes;
            this.listener = listener;
            worker = new Thread(this, "jglass-bridge-network");
            worker.setDaemon(true);
        }

        void stop() {
            cancelled = true;
            worker.interrupt();
            HttpURLConnection active = connection;
            if (active != null) active.disconnect();
        }

        void checkActive() throws IOException {
            if (cancelled || timedOut) throw new IOException("Bridge request ended");
        }

        @Override public void run() {
            Timer deadline = new Timer("jglass-bridge-deadline", true);
            deadline.schedule(new TimerTask() {
                @Override public void run() {
                    timedOut = true;
                    worker.interrupt();
                    HttpURLConnection active = connection;
                    if (active != null) active.disconnect();
                }
            }, deadlineMillis);
            byte[] result = null;
            try {
                if (!isSafeBaseUrl(baseUrl) || !isSafeToken(token)) {
                    throw new IOException("Bridge is not configured");
                }
                checkActive();
                String normalized = baseUrl.endsWith("/")
                        ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
                connection = connections.open(new URL(normalized + path));
                checkActive();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setRequestMethod(body == null ? "GET" : "POST");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setFixedLengthStreamingMode(body.length);
                    OutputStream output = connection.getOutputStream();
                    try { output.write(body); } finally { output.close(); }
                }
                checkActive();
                if (connection.getResponseCode() != 200) throw new IOException("Bridge rejected request");
                String type = connection.getHeaderField("Content-Type");
                if (type == null || !"application/json".equalsIgnoreCase(type.split(";", 2)[0].trim())) {
                    throw new IOException("Unsupported bridge response type");
                }
                int length = connection.getContentLength();
                if (length <= 0 || length > maximumBytes) throw new IOException("Invalid bridge response size");
                InputStream input = connection.getInputStream();
                try {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[Math.min(4096, maximumBytes)];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        checkActive();
                        if (output.size() + count > length) throw new IOException("Bridge response exceeded its size");
                        output.write(buffer, 0, count);
                    }
                    if (output.size() != length) throw new IOException("Truncated bridge response");
                    checkActive();
                    result = output.toByteArray();
                } finally { input.close(); }
            } catch (Exception ignored) {
                // Never log URLs, credentials, or response payloads.
            } finally {
                deadline.cancel();
                if (connection != null) connection.disconnect();
            }
            synchronized (BridgeJsonClient.this) {
                if (current != this || cancelled) return;
                current = null;
                if (result != null && !timedOut) listener.onSuccess(result);
                else listener.onFailure();
            }
        }
    }
}
