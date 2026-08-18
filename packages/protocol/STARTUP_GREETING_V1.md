# Startup greeting protocol v1

`POST /v1/startup-greeting` is the authenticated, trusted-LAN startup interface
between the API-19 Glass client and the modern bridge. Both request and response
use `Content-Type: application/json`; the client sends
`Authorization: Bearer <JGLASS_DEVICE_TOKEN>` and refuses redirects.

Request:

```json
{
  "protocol": "jglass/startup/1",
  "fallbackVersion": "fallback-en-US-v1"
}
```

`fallbackVersion` is `null` before provisioning. The bridge includes
`fallbackAssets` only when this value is missing or stale. Each asset is an
`audio/mpeg` Base64 value keyed by `morning`, `afternoon`, or `evening`; the fixed
spoken text remains part of the client and bridge implementations rather than
the public response.

The response contains `protocol`, `greetingDate`, `period`, bounded `spokenText`,
a weather `summary` and the exact `Weather data by Open-Meteo.com` attribution,
an MP3 `audio` object, and either versioned `fallbackAssets` or `null`. See the
canonical fixture in `fixtures/startup-greeting/`.

The complete decoded JSON response is limited to 512 KiB. Dynamic audio is
limited to 256 KiB and each fallback clip to 128 KiB before Base64 encoding.
Glass rejects malformed JSON or Base64, wrong protocol/MIME/attribution, an
unexpected bridge date, redirects, and over-limit bodies. Bridge and Glass use
the same configured/local timezone; v1 intentionally sends no coordinates.
