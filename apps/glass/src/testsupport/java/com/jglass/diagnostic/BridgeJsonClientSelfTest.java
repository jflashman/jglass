package com.jglass.diagnostic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class BridgeJsonClientSelfTest {
    private static final String TOKEN = "synthetic-token-at-least-32-characters";

    public static void main(String[] args) throws Exception {
        validation();
        requestShapes();
        rejection();
        cancelAndRestart();
        deadline();
        if (args.length == 1) liveBridge(args[0]);
        System.out.println("BridgeJsonClientSelfTest: PASS");
    }

    private static void validation() {
        require(BridgeJsonClient.isSafeBaseUrl("http://127.0.0.1:8787/"), "loopback");
        require(BridgeJsonClient.isSafeBaseUrl("https://bridge.example"), "https");
        for (String invalid : new String[] { null, "", "file:///tmp/bridge", "http://u:p@example.com",
                "http://example.com/?token=private", "http://example.com/#secret", "http://example.com/path",
                "http://example.com:0", "http://example.com:65536", "http://exa mple.com", "http://example.com\n" }) {
            require(!BridgeJsonClient.isSafeBaseUrl(invalid), "reject unsafe URL");
        }
        require(BridgeJsonClient.isSafeToken(TOKEN), "valid token");
        require(!BridgeJsonClient.isSafeToken(TOKEN + "\r\nInjected: true"), "header injection");
        require(!BridgeJsonClient.isSafeToken("short"), "short token");
    }

    private static void liveBridge(String baseUrl) throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger valid = new AtomicInteger();
        new BridgeJsonClient().request(baseUrl, TOKEN, "/v1/device-config", null, 1024,
                new BridgeJsonClient.Listener() {
                    @Override public void onSuccess(byte[] json) {
                        try {
                            if ("JARVIS".equals(DeviceConfigProtocol.parse(json))) valid.incrementAndGet();
                        } catch (Exception invalid) { /* Assertion below reports failure. */ }
                        done.countDown();
                    }
                    @Override public void onFailure() { done.countDown(); }
                });
        await(done);
        require(valid.get() == 1, "real Java HTTP to Node configuration round trip");
    }

    private static void requestShapes() throws Exception {
        for (byte[] body : new byte[][] { null, new byte[] { 123, 125 } }) {
            final FakeConnection connection = new FakeConnection();
            Result result = new Result();
            client(connection, 2000).request("http://bridge.example/", TOKEN, "/v1/device-config", body, 1024, result);
            await(result.done);
            require(result.success.get() == 1 && result.failures.get() == 0, "successful response");
            require((body == null ? "GET" : "POST").equals(connection.getRequestMethod()), "method");
            require(("Bearer " + TOKEN).equals(connection.getRequestProperty("Authorization")), "authorization");
            require(!connection.getInstanceFollowRedirects() && !connection.getUseCaches(), "no redirects or HTTP cache");
            require(connection.getConnectTimeout() == 4000 && connection.getReadTimeout() == 8000, "socket timeouts");
            require(connection.output.size() == (body == null ? 0 : body.length), "request body");
        }
    }

    private static void rejection() throws Exception {
        for (int scenario = 0; scenario < 7; scenario++) {
            FakeConnection connection = new FakeConnection();
            switch (scenario) {
                case 0: connection.status = 302; break;
                case 1: connection.status = 401; break;
                case 2: connection.type = "text/html"; break;
                case 3: connection.length = 1025; break;
                case 4: connection.length = -1; break;
                case 5: connection.length = 1; break;
                case 6: connection.length = 3; break;
                default: throw new AssertionError();
            }
            Result result = new Result();
            client(connection, 2000).request("http://bridge.example", TOKEN, "/v1/device-config", null, 1024, result);
            await(result.done);
            require(result.failures.get() == 1 && result.success.get() == 0, "reject malformed transport " + scenario);
        }
    }

    private static void cancelAndRestart() throws Exception {
        final FakeConnection first = new FakeConnection();
        final FakeConnection second = new FakeConnection();
        first.hold = new CountDownLatch(1);
        second.hold = new CountDownLatch(1);
        final AtomicInteger opened = new AtomicInteger();
        BridgeJsonClient client = new BridgeJsonClient(new BridgeJsonClient.ConnectionFactory() {
            @Override public HttpURLConnection open(URL url) {
                return opened.getAndIncrement() == 0 ? first : second;
            }
        }, 2000);
        Result oldResult = new Result();
        Result newResult = new Result();
        client.request("http://bridge.example", TOKEN, "/old", null, 1024, oldResult);
        await(first.entered);
        client.request("http://bridge.example", TOKEN, "/new", null, 1024, newResult);
        await(second.entered);
        first.hold.countDown();
        first.worker.join(1000);
        require(!first.worker.isAlive(), "old request ended");
        require(oldResult.total() == 0, "cancelled request must never deliver a late callback");
        client.cancel();
        require(second.disconnects.get() > 0, "old completion must not clear new connection");
        second.hold.countDown();
        second.worker.join(1000);
        require(!second.worker.isAlive() && newResult.total() == 0, "new cancellation remains effective");
    }

    private static void deadline() throws Exception {
        final FakeConnection connection = new FakeConnection();
        connection.hold = new CountDownLatch(1);
        connection.releaseOnDisconnect = true;
        Result result = new Result();
        client(connection, 100).request("http://bridge.example", TOKEN, "/v1/device-config", null, 1024, result);
        await(result.done);
        require(result.failures.get() == 1 && connection.disconnects.get() > 0, "overall deadline closes request");
    }

    private static BridgeJsonClient client(final FakeConnection connection, long deadline) {
        return new BridgeJsonClient(new BridgeJsonClient.ConnectionFactory() {
            @Override public HttpURLConnection open(URL url) { return connection; }
        }, deadline);
    }

    private static final class Result implements BridgeJsonClient.Listener {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger success = new AtomicInteger();
        final AtomicInteger failures = new AtomicInteger();
        @Override public void onSuccess(byte[] bytes) { success.incrementAndGet(); done.countDown(); }
        @Override public void onFailure() { failures.incrementAndGet(); done.countDown(); }
        int total() { return success.get() + failures.get(); }
    }

    private static final class FakeConnection extends HttpURLConnection {
        int status = 200;
        int length = 2;
        String type = "application/json; charset=utf-8";
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final CountDownLatch entered = new CountDownLatch(1);
        final AtomicInteger disconnects = new AtomicInteger();
        CountDownLatch hold;
        boolean releaseOnDisconnect;
        volatile Thread worker;

        FakeConnection() throws Exception { super(new URL("http://bridge.example")); }
        @Override public void connect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public void disconnect() {
            disconnects.incrementAndGet();
            if (releaseOnDisconnect && hold != null) hold.countDown();
        }
        @Override public int getResponseCode() throws IOException {
            worker = Thread.currentThread();
            entered.countDown();
            if (hold != null) {
                // Simulate old networking code returning late even after interrupt/disconnect.
                boolean complete = false;
                while (!complete) {
                    try { complete = hold.await(2, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { continue; }
                    if (!complete) throw new IOException("test connection deadline");
                }
            }
            return status;
        }
        @Override public String getHeaderField(String name) { return "Content-Type".equals(name) ? type : null; }
        @Override public int getContentLength() { return length; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[] { 123, 125 }); }
        @Override public OutputStream getOutputStream() { return output; }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        require(latch.await(3, TimeUnit.SECONDS), "test timed out");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
