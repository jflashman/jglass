package com.jglass.diagnostic;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class StartupGreetingRules {
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

    static String fallbackText(Period period) {
        return "Good " + period.value
                + ", sir. Nearby weather is unavailable. The Bluetooth assistant connection is not properly configured.";
    }
}
