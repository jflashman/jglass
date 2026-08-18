package com.jglass.diagnostic;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class StartupGreetingRules {
    static final String BUNDLED_FALLBACK_VERSION = "bundled-en-US-v2";

    enum Period {
        MORNING("morning"),
        AFTERNOON("afternoon"),
        EVENING("evening");

        final String value;

        Period(String value) {
            this.value = value;
        }

        static Period fromValue(String value) {
            for (Period period : values()) {
                if (period.value.equals(value)) return period;
            }
            throw new IllegalArgumentException("Unsupported greeting period");
        }
    }

    enum FallbackCause {
        BLUETOOTH_DISABLED("bluetooth_disabled"),
        ASSISTANT_UNAVAILABLE("assistant_unavailable");

        final String value;

        FallbackCause(String value) {
            this.value = value;
        }
    }

    private StartupGreetingRules() {
    }

    static Period periodForHour(int hour) {
        if (hour >= 5 && hour < 12) return Period.MORNING;
        if (hour >= 12 && hour < 18) return Period.AFTERNOON;
        return Period.EVENING;
    }

    static String localDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    static boolean shouldStart(String lastGreetingDate, String today, boolean force) {
        return force || lastGreetingDate == null || !lastGreetingDate.equals(today);
    }

    static String fallbackText(Period period, FallbackCause cause) {
        String reason = cause == FallbackCause.BLUETOOTH_DISABLED
                ? "Bluetooth appears to be disabled."
                : "The local assistant is unavailable.";
        return "Good " + period.value + ", sir. " + reason;
    }

    static FallbackCause fallbackCause(boolean bluetoothEnabled) {
        return bluetoothEnabled
                ? FallbackCause.ASSISTANT_UNAVAILABLE
                : FallbackCause.BLUETOOTH_DISABLED;
    }

    static String fallbackResourceName(Period period, FallbackCause cause) {
        return "startup_" + period.value + "_" + cause.value;
    }
}
