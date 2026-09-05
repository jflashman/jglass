# Device configuration v1

`GET /v1/device-config` uses the startup bridge's origin and
`Authorization: Bearer <device token>`. It has no request body and makes no
provider calls. Authentication failure returns 401; other methods return 404.

Successful response (HTTP 200):

```json
{"protocol":"jglass/device-config/1","wakePhrase":"JARVIS"}
```

The protocol string identifies version 1. Responses use UTF-8 JSON,
`Content-Type: application/json`, an exact positive `Content-Length` no greater
than 1024 bytes, and `Cache-Control: no-store`. No coordinates, provider
configuration, credentials, or audio are included. Unknown fields are ignored
after bounded JSON validation; duplicate keys and invalid UTF-8 are rejected.

`JGLASS_WAKE_PHRASE` on the bridge defaults to `JARVIS` only when absent.
An explicitly empty or invalid setting prevents startup. Input is at most 128
ASCII letters/spaces. Trim leading/trailing spaces, collapse repeated spaces,
and uppercase with English casing. The result must contain one to four words
and be at most 32 characters including spaces. Digits, punctuation, tabs,
newlines, and non-ASCII characters are rejected. These are spelling constraints,
not a dictionary or a guarantee that the stock recognizer accepts the phrase.

Glass refreshes once per Home resume using its existing private enrollment.
Each request has a 12-second overall deadline, 4-second connection timeout,
8-second socket-read timeout, no HTTP caching, and no redirect following.
Pause/destruction cancels the request and invalidates queued results. A later
Home resume can retry; there is no background polling loop.

Only validated, canonical protocol/phrase JSON is saved in app-private
preferences. Invalid responses, authentication failures, timeouts, and an older
bridge returning 404 leave the previous configuration intact. Missing or
corrupt configuration yields no configured phrase on first use. Changing the
bridge URL or device token clears the cache in the same preference update;
late responses from the previous enrollment cannot restore it.

Configuration storage does **not** enable recognition. The XE24 recognizer
adapter, awake-only microphone lifecycle, activation card, and physical
recognition/battery tests remain a separate implementation step.

Shared acceptance cases: `fixtures/device-config/cases.json`.
