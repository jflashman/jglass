package com.jglass.diagnostic;

final class StartupGreetingClient {
    interface Listener {
        void onSuccess(StartupGreetingProtocol.Response response);
        void onFailure();
    }

    private final BridgeJsonClient client = new BridgeJsonClient();

    static boolean isSafeBridgeBaseUrl(String value) {
        return BridgeJsonClient.isSafeBaseUrl(value);
    }

    void request(String baseUrl, String token, String fallbackVersion, final Listener listener) {
        client.request(baseUrl, token, "/v1/startup-greeting",
                StartupGreetingProtocol.requestBytes(fallbackVersion),
                StartupGreetingProtocol.MAX_RESPONSE_BYTES, new BridgeJsonClient.Listener() {
                    @Override public void onSuccess(byte[] json) {
                        final StartupGreetingProtocol.Response response;
                        try {
                            response = StartupGreetingProtocol.parse(json);
                        } catch (Exception invalid) {
                            listener.onFailure();
                            return;
                        }
                        listener.onSuccess(response);
                    }
                    @Override public void onFailure() { listener.onFailure(); }
                });
    }

    void cancel() { client.cancel(); }
}
