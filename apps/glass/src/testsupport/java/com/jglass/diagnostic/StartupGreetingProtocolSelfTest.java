package com.jglass.diagnostic;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class StartupGreetingProtocolSelfTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    public static void main(String[] args) throws Exception {
        require(args.length == 1, "fixture path is required");
        periodBoundaries();
        onceDailyRules();
        parsesSharedFixture(args[0]);
        rejectsMalformedJsonAndBase64();
        System.out.println("StartupGreetingProtocolSelfTest passed");
    }

    private static void periodBoundaries() {
        require(StartupGreetingRules.periodForHour(4) == StartupGreetingRules.Period.EVENING, "04:59 period");
        require(StartupGreetingRules.periodForHour(5) == StartupGreetingRules.Period.MORNING, "05:00 period");
        require(StartupGreetingRules.periodForHour(11) == StartupGreetingRules.Period.MORNING, "11:59 period");
        require(StartupGreetingRules.periodForHour(12) == StartupGreetingRules.Period.AFTERNOON, "12:00 period");
        require(StartupGreetingRules.periodForHour(17) == StartupGreetingRules.Period.AFTERNOON, "17:59 period");
        require(StartupGreetingRules.periodForHour(18) == StartupGreetingRules.Period.EVENING, "18:00 period");
    }

    private static void onceDailyRules() {
        require(StartupGreetingRules.shouldStart(null, "2026-08-18", false), "first greeting");
        require(!StartupGreetingRules.shouldStart("2026-08-18", "2026-08-18", false), "once per day");
        require(StartupGreetingRules.shouldStart("2026-08-18", "2026-08-18", true), "debug force");
        require(StartupGreetingRules.shouldStart("2026-08-18", "2026-08-19", false), "new day");
    }

    private static void parsesSharedFixture(String fixturePath) throws Exception {
        StartupGreetingProtocol.Response response = StartupGreetingProtocol.parse(
                Files.readAllBytes(Paths.get(fixturePath)));
        require("2026-08-18".equals(response.greetingDate), "greeting date");
        require(response.period == StartupGreetingRules.Period.MORNING, "period");
        require(StartupGreetingProtocol.ATTRIBUTION.equals(response.attribution), "attribution");
        require(response.audio.bytes.length == 4, "dynamic audio");
        require(response.fallbackAssets != null, "fallback assets");
        require("fallback-en-US-v1".equals(response.fallbackAssets.version), "fallback version");
        require(response.fallbackAssets.evening.bytes[3] == 3, "period-specific fallback");
    }

    private static void rejectsMalformedJsonAndBase64() throws Exception {
        expectFailure("{");
        expectFailure(validResponse("%%%="));
        expectFailure(validResponse("SUQ="));
        expectFailure(validResponse("SUQzAQ==").replace("audio/mpeg", "audio/wav"));
        expectFailure(validResponse("SUQzAQ==").replace("jglass/startup/1", "jglass/startup/2"));
    }

    private static String validResponse(String base64) {
        return "{\"protocol\":\"jglass/startup/1\",\"greetingDate\":\"2026-08-18\","
                + "\"period\":\"morning\",\"spokenText\":\"hello\",\"weather\":{"
                + "\"summary\":\"clear\",\"attribution\":\"Weather data by Open-Meteo.com\"},"
                + "\"audio\":{\"mimeType\":\"audio/mpeg\",\"dataBase64\":\"" + base64
                + "\"},\"fallbackAssets\":null}";
    }

    private static void expectFailure(String json) throws Exception {
        try {
            StartupGreetingProtocol.parse(json.getBytes(UTF_8));
            throw new AssertionError("Expected parser failure");
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        } catch (StrictJson.ParseException expected) {
            // Expected syntax failure.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
