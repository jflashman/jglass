package com.jglass.diagnostic;

import java.nio.charset.Charset;
import java.util.Map;

final class StartupGreetingProtocol {
    static final String PROTOCOL = "jglass/startup/1";
    static final String ATTRIBUTION = "Weather data by Open-Meteo.com";
    static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final int MAX_AUDIO_BYTES = 256 * 1024;
    private static final int MAX_FALLBACK_AUDIO_BYTES = 128 * 1024;
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    static final class Audio {
        final byte[] bytes;

        Audio(byte[] bytes) {
            this.bytes = bytes;
        }
    }

    static final class FallbackAssets {
        final String version;
        final Audio morning;
        final Audio afternoon;
        final Audio evening;

        FallbackAssets(String version, Audio morning, Audio afternoon, Audio evening) {
            this.version = version;
            this.morning = morning;
            this.afternoon = afternoon;
            this.evening = evening;
        }
    }

    static final class Response {
        final String greetingDate;
        final StartupGreetingRules.Period period;
        final String spokenText;
        final String weatherSummary;
        final String attribution;
        final Audio audio;
        final FallbackAssets fallbackAssets;

        Response(String greetingDate, StartupGreetingRules.Period period, String spokenText,
                String weatherSummary, String attribution, Audio audio,
                FallbackAssets fallbackAssets) {
            this.greetingDate = greetingDate;
            this.period = period;
            this.spokenText = spokenText;
            this.weatherSummary = weatherSummary;
            this.attribution = attribution;
            this.audio = audio;
            this.fallbackAssets = fallbackAssets;
        }
    }

    private StartupGreetingProtocol() {
    }

    static byte[] requestBytes(String fallbackVersion) {
        String version = fallbackVersion == null ? "null" : "\"" + safeVersion(fallbackVersion) + "\"";
        return ("{\"protocol\":\"" + PROTOCOL + "\",\"fallbackVersion\":" + version + "}")
                .getBytes(UTF_8);
    }

    static Response parse(byte[] json) throws Exception {
        if (json == null || json.length == 0 || json.length > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("Startup response is outside its size limit");
        }
        Map<String, Object> root = object(StrictJson.parse(new String(json, UTF_8)), "response");
        if (!PROTOCOL.equals(string(root, "protocol", 64))) {
            throw new IllegalArgumentException("Unsupported startup response protocol");
        }
        String date = string(root, "greetingDate", 10);
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException("Invalid greeting date");
        StartupGreetingRules.Period period = StartupGreetingRules.Period.fromValue(string(root, "period", 16));
        String spokenText = string(root, "spokenText", 768);
        Map<String, Object> weather = object(root.get("weather"), "weather");
        String summary = string(weather, "summary", 256);
        String attribution = string(weather, "attribution", 64);
        if (!ATTRIBUTION.equals(attribution)) throw new IllegalArgumentException("Missing weather attribution");
        Audio audio = audio(root.get("audio"), MAX_AUDIO_BYTES);
        FallbackAssets fallbacks = root.get("fallbackAssets") == null
                ? null : fallbacks(root.get("fallbackAssets"));
        return new Response(date, period, spokenText, summary, attribution, audio, fallbacks);
    }

    private static FallbackAssets fallbacks(Object value) {
        Map<String, Object> assets = object(value, "fallbackAssets");
        String version = safeVersion(string(assets, "version", 64));
        Map<String, Object> clips = object(assets.get("clips"), "clips");
        return new FallbackAssets(
                version,
                audio(clips.get("morning"), MAX_FALLBACK_AUDIO_BYTES),
                audio(clips.get("afternoon"), MAX_FALLBACK_AUDIO_BYTES),
                audio(clips.get("evening"), MAX_FALLBACK_AUDIO_BYTES));
    }

    private static Audio audio(Object value, int maximumBytes) {
        Map<String, Object> audio = object(value, "audio");
        if (!"audio/mpeg".equals(string(audio, "mimeType", 32))) {
            throw new IllegalArgumentException("Unsupported audio MIME type");
        }
        byte[] bytes = Base64Codec.decode(string(audio, "dataBase64", maximumBytes * 2), maximumBytes);
        boolean mp3Header = bytes.length >= 3
                && ((bytes[0] == 0x49 && bytes[1] == 0x44 && bytes[2] == 0x33)
                || ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xe0) == 0xe0));
        if (!mp3Header) throw new IllegalArgumentException("Audio does not have an MP3 header");
        return new Audio(bytes);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String name) {
        if (!(value instanceof Map)) throw new IllegalArgumentException(name + " must be an object");
        return (Map<String, Object>) value;
    }

    private static String string(Map<String, Object> object, String name, int maximumLength) {
        Object value = object.get(name);
        if (!(value instanceof String) || ((String) value).length() == 0
                || ((String) value).length() > maximumLength) {
            throw new IllegalArgumentException(name + " must be a bounded string");
        }
        return (String) value;
    }

    private static String safeVersion(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("Invalid fallback version");
        }
        return value;
    }
}
