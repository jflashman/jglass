package com.jglass.diagnostic;

import java.util.Locale;

/** Pure state machine for bounded BLE reliability probes. */
final class GatewayProbeCoordinator {
    static final int FOLLOW_UP_EXCHANGES = 5;
    static final long CONNECTED_PING_DELAY_MILLIS = 15_000L;
    static final long RECONNECT_DELAY_MILLIS = 10_000L;

    enum Mode {
        ONESHOT("oneshot"),
        CONNECTED_WAKE("connected-wake"),
        RECONNECT("reconnect");

        private final String value;

        Mode(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }

        static Mode fromValue(String value) {
            if (CONNECTED_WAKE.value.equals(value)) {
                return CONNECTED_WAKE;
            }
            if (RECONNECT.value.equals(value)) {
                return RECONNECT;
            }
            return ONESHOT;
        }
    }

    enum Action {
        COMPLETE,
        SEND_CONNECTED_PING,
        START_CONNECTION_CYCLE
    }

    static final class Decision {
        final Action action;
        final long delayMillis;

        Decision(Action action, long delayMillis) {
            this.action = action;
            this.delayMillis = delayMillis;
        }
    }

    static final class Snapshot {
        final Mode mode;
        final int attempts;
        final int successes;
        final String lastFailure;

        Snapshot(Mode mode, int attempts, int successes, String lastFailure) {
            this.mode = mode;
            this.attempts = attempts;
            this.successes = successes;
            this.lastFailure = lastFailure;
        }

        String summary() {
            String result = String.format(
                    Locale.US,
                    "%s %d/%d",
                    mode.value(),
                    successes,
                    attempts);
            return lastFailure == null ? result : result + " last=" + lastFailure;
        }
    }

    private final Mode mode;
    private int connectionCycles;
    private int attempts;
    private int successes;
    private String lastFailure;

    GatewayProbeCoordinator(Mode mode) {
        this.mode = mode;
    }

    synchronized void onConnectionCycleStarted() {
        connectionCycles++;
        attempts++;
    }

    synchronized void onConnectedPingStarted() {
        attempts++;
    }

    synchronized Decision onExchangeSucceeded() {
        successes++;
        lastFailure = null;
        if (mode == Mode.CONNECTED_WAKE && attempts <= FOLLOW_UP_EXCHANGES) {
            return new Decision(Action.SEND_CONNECTED_PING, CONNECTED_PING_DELAY_MILLIS);
        }
        if (mode == Mode.RECONNECT && connectionCycles <= FOLLOW_UP_EXCHANGES) {
            return new Decision(Action.START_CONNECTION_CYCLE, RECONNECT_DELAY_MILLIS);
        }
        return new Decision(Action.COMPLETE, 0L);
    }

    synchronized Decision onExchangeFailed(String failureCode) {
        lastFailure = failureCode;
        if (mode == Mode.RECONNECT && connectionCycles <= FOLLOW_UP_EXCHANGES) {
            return new Decision(Action.START_CONNECTION_CYCLE, RECONNECT_DELAY_MILLIS);
        }
        return new Decision(Action.COMPLETE, 0L);
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(mode, attempts, successes, lastFailure);
    }
}
