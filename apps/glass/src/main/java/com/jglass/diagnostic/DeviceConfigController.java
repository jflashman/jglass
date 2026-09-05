package com.jglass.diagnostic;

import android.os.Handler;
import android.os.Looper;

/** Refreshes configuration during Home foreground use; never starts recognition. */
final class DeviceConfigController {
    private final StartupGreetingStore store;
    private final BridgeJsonClient client = new BridgeJsonClient();
    private final Handler main = new Handler(Looper.getMainLooper());
    private long generation;

    DeviceConfigController(StartupGreetingStore store) { this.store = store; }

    void refresh() {
        cancel();
        final String url = store.bridgeUrl();
        final String token = store.deviceToken();
        if (!BridgeJsonClient.isSafeBaseUrl(url) || !BridgeJsonClient.isSafeToken(token)) return;
        final long requestGeneration = generation;
        client.request(url, token, "/v1/device-config", null,
                DeviceConfigProtocol.MAX_RESPONSE_BYTES, new BridgeJsonClient.Listener() {
                    @Override public void onSuccess(final byte[] json) {
                        main.post(new Runnable() {
                            @Override public void run() {
                                if (generation != requestGeneration || !url.equals(store.bridgeUrl())
                                        || !token.equals(store.deviceToken())) return;
                                store.deviceConfigCache().accept(json);
                            }
                        });
                    }
                    @Override public void onFailure() {
                        // Keep the last validated phrase. Retry on the next Home resume.
                    }
                });
    }

    void cancel() {
        generation++;
        client.cancel();
        main.removeCallbacksAndMessages(null);
    }
}
