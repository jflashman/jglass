package com.jglass.diagnostic;

public final class GatewayProbeCoordinatorSelfTest {
    public static void main(String[] args) {
        defaultsUnknownModeToOneShot();
        completesOneShotAfterSuccess();
        schedulesFiveConnectedPings();
        connectedWakeFailureIsTerminal();
        reconnectRetriesAreBounded();
        reconnectCanRecoverOnLaterAttempt();
        System.out.println("GatewayProbeCoordinatorSelfTest: PASS");
    }

    private static void defaultsUnknownModeToOneShot() {
        assertEquals(
                GatewayProbeCoordinator.Mode.ONESHOT,
                GatewayProbeCoordinator.Mode.fromValue("unexpected"),
                "unknown mode");
    }

    private static void completesOneShotAfterSuccess() {
        GatewayProbeCoordinator coordinator = coordinator(GatewayProbeCoordinator.Mode.ONESHOT);
        GatewayProbeCoordinator.Decision decision = coordinator.onExchangeSucceeded();
        assertEquals(GatewayProbeCoordinator.Action.COMPLETE, decision.action, "one-shot action");
        assertCounts(coordinator, 1, 1);
    }

    private static void schedulesFiveConnectedPings() {
        GatewayProbeCoordinator coordinator = coordinator(
                GatewayProbeCoordinator.Mode.CONNECTED_WAKE);
        for (int followUp = 0; followUp < GatewayProbeCoordinator.FOLLOW_UP_EXCHANGES; followUp++) {
            GatewayProbeCoordinator.Decision decision = coordinator.onExchangeSucceeded();
            assertEquals(
                    GatewayProbeCoordinator.Action.SEND_CONNECTED_PING,
                    decision.action,
                    "connected action " + followUp);
            assertEquals(
                    GatewayProbeCoordinator.CONNECTED_PING_DELAY_MILLIS,
                    decision.delayMillis,
                    "connected delay");
            coordinator.onConnectedPingStarted();
        }
        assertEquals(
                GatewayProbeCoordinator.Action.COMPLETE,
                coordinator.onExchangeSucceeded().action,
                "connected terminal action");
        assertCounts(coordinator, 6, 6);
    }

    private static void connectedWakeFailureIsTerminal() {
        GatewayProbeCoordinator coordinator = coordinator(
                GatewayProbeCoordinator.Mode.CONNECTED_WAKE);
        assertEquals(
                GatewayProbeCoordinator.Action.COMPLETE,
                coordinator.onExchangeFailed("timed_out").action,
                "connected failure action");
        assertEquals("timed_out", coordinator.snapshot().lastFailure, "connected failure code");
    }

    private static void reconnectRetriesAreBounded() {
        GatewayProbeCoordinator coordinator = coordinator(GatewayProbeCoordinator.Mode.RECONNECT);
        for (int followUp = 0; followUp < GatewayProbeCoordinator.FOLLOW_UP_EXCHANGES; followUp++) {
            GatewayProbeCoordinator.Decision decision = coordinator.onExchangeFailed("not_found");
            assertEquals(
                    GatewayProbeCoordinator.Action.START_CONNECTION_CYCLE,
                    decision.action,
                    "reconnect action " + followUp);
            assertEquals(
                    GatewayProbeCoordinator.RECONNECT_DELAY_MILLIS,
                    decision.delayMillis,
                    "reconnect delay");
            coordinator.onConnectionCycleStarted();
        }
        assertEquals(
                GatewayProbeCoordinator.Action.COMPLETE,
                coordinator.onExchangeFailed("not_found").action,
                "reconnect terminal action");
        assertCounts(coordinator, 6, 0);
    }

    private static void reconnectCanRecoverOnLaterAttempt() {
        GatewayProbeCoordinator coordinator = coordinator(GatewayProbeCoordinator.Mode.RECONNECT);
        coordinator.onExchangeFailed("bluetooth_off");
        coordinator.onConnectionCycleStarted();
        GatewayProbeCoordinator.Decision recovered = coordinator.onExchangeSucceeded();
        assertEquals(
                GatewayProbeCoordinator.Action.START_CONNECTION_CYCLE,
                recovered.action,
                "recovered action");
        assertCounts(coordinator, 2, 1);
        if (coordinator.snapshot().lastFailure != null) {
            throw new AssertionError("successful exchange should clear last failure");
        }
    }

    private static GatewayProbeCoordinator coordinator(GatewayProbeCoordinator.Mode mode) {
        GatewayProbeCoordinator coordinator = new GatewayProbeCoordinator(mode);
        coordinator.onConnectionCycleStarted();
        return coordinator;
    }

    private static void assertCounts(
            GatewayProbeCoordinator coordinator,
            int attempts,
            int successes) {
        GatewayProbeCoordinator.Snapshot snapshot = coordinator.snapshot();
        assertEquals(attempts, snapshot.attempts, "attempts");
        assertEquals(successes, snapshot.successes, "successes");
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
