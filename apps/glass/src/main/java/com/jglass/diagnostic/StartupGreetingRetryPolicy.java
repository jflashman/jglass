package com.jglass.diagnostic;

final class StartupGreetingRetryPolicy {
    static final long BOOT_DEADLINE_MILLIS = 30_000L;
    static final long NETWORK_POLL_MILLIS = 1_000L;
    static final long REQUEST_RETRY_MILLIS = 2_000L;
    static final int MAX_REQUEST_ATTEMPTS = 3;

    private StartupGreetingRetryPolicy() {
    }

    static boolean shouldRetryRequest(int completedAttempts, long remainingMillis) {
        return completedAttempts < MAX_REQUEST_ATTEMPTS
                && remainingMillis > REQUEST_RETRY_MILLIS;
    }

    static long networkPollDelay(long remainingMillis) {
        return Math.max(0L, Math.min(NETWORK_POLL_MILLIS, remainingMillis));
    }
}
