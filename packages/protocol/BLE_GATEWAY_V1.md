# JGlass BLE gateway profile, draft 1

This document defines the minimum interoperability contract for the iPhone
gateway feasibility spike. It is not yet the production security or streaming
protocol.

## Roles

- Glass XE24: BLE central and GATT client.
- JGlass iPhone companion: BLE peripheral and GATT server.
- JGlass bridge: internet service reached only by the companion.

Initial discovery and enrollment occur while the iPhone companion is in the
foreground. After connection, the Glass client remembers the enrolled logical
device but must not persist raw Bluetooth addresses in normal logs or committed
configuration.

## GATT identifiers

| Purpose | UUID | Properties |
|---|---|---|
| JGlass gateway service | `c91249f4-d2a1-4d93-8f66-ce0a24753b9d` | Primary service |
| Control uplink | `fe398f65-82ad-4e3e-b46b-5978725413c1` | Write with response |
| Control downlink | `1d40843f-bab8-4cec-a70e-72e839205998` | Indicate, notify fallback |
| Compressed audio uplink | `3c86b2bd-f7ad-4dd2-805f-9312e247d25d` | Write without response after authorization |
| Compressed audio downlink | `7fbd16d7-654c-4424-97c2-f973a1dcef0a` | Notify; fallback only |

The UUIDs identify JGlass protocol surfaces; they are not secrets.

## Feasibility-stage messages

Control messages use UTF-8 JSON matching the canonical fixtures in
`fixtures/ble-gateway/`. The first spike permits only:

- `gateway.hello` and `gateway.hello_ack` for version negotiation;
- `gateway.ping` and `gateway.pong` for a non-sensitive round trip;
- `error` with a stable, non-sensitive code.

Every control message includes:

- `protocol`: currently `jglass/1`;
- `type`;
- `sessionId` and `requestId` opaque values;
- a monotonically increasing per-session `sequence`;
- a bounded `payload` object.

No diagnostic message may contain microphone data, notification content,
credentials, contact data, network identifiers, or computer context.

## Fragmentation and flow control

The implementation must work with a 20-byte ATT application payload until the
physical Glass/iPhone pair proves a larger negotiated MTU. Control JSON and
binary audio therefore require fragmentation.

The Xcode-backed diagnostic uses this 10-byte header. All multi-byte integers
are unsigned and big-endian.

| Byte(s) | Field | Meaning |
|---|---|---|
| 0 | Magic | `0x4a` (`J`) |
| 1 | Framing version | `0x01` |
| 2 | Kind | `0x01` control, `0x02` cancel |
| 3 | Flags | bit 0 first, bit 1 final; all other bits must be zero |
| 4-5 | Stream ID | Non-persistent identifier selected by the sender |
| 6-7 | Sequence | Zero-based fragment number within the stream |
| 8-9 | Total length | Complete payload size on the first frame; zero otherwise |
| 10+ | Payload | Fragment of the UTF-8 JSON control message |

A sender splits control data into packets no larger than 20 bytes until a
larger physical MTU is measured. The first fragment has sequence zero and the
first flag; the last has the final flag. A single-frame message has both flags.
A cancel frame has no payload, a zero total length, and the final flag.

Each diagnostic receiver permits one incomplete inbound control stream, rejects
out-of-order fragments, ignores already-accepted sequence numbers as
duplicates, and expires an incomplete stream after five seconds. Reassembly is
bounded to 4 KiB. Downlink indications are sent only while subscribed and pause
when Core Bluetooth reports backpressure. This framing is versioned separately
from the inner `jglass/1` JSON envelope and may change before sensitive traffic
is enabled.

The initial Glass client performs a one-shot exchange: it subscribes, sends
`gateway.hello`, validates the matching acknowledgement, sends `gateway.ping`,
validates the matching pong, reports the companion's network-reachability bit,
and closes the GATT connection. It never sends credentials or user content.
This exact 20-byte fragmented exchange was verified on the physical stock-XE24
Glass and iPhone companion pair on 2026-08-17.

The v0.4 reliability diagnostic reuses these exact messages in two bounded test
modes; it does not expand the protocol allowlist:

- `connected-wake`: one hello/ping exchange followed by five pings at 15-second
  intervals on the same subscribed GATT connection;
- `reconnect`: one initial exchange followed by five cycles that close the GATT
  connection, wait 10 seconds, rediscover, reconnect, subscribe, and exchange a
  new hello/ping pair.

Each connection cycle uses a fresh ephemeral session ID. Each message within a
maintained connection has a new request ID and monotonically increasing
sequence. Unknown probe modes fall back to the one-shot behavior. These modes
remain non-sensitive diagnostics and confer no authorization for audio, actions,
or credentials.

Physical testing on 2026-08-18 completed connected-wake `6/6` while the iPhone
remained locked. Reconnect under lock completed `1/6`, with only the initial
foreground cycle succeeding; a run begun while the companion was ordinarily
backgrounded completed `0/6`. Every failed cycle ended `not_found` before GATT
subscription, so these results concern discovery/reconnection rather than JSON
or fragmentation failure.

The initial limits are:

- control message: 4 KiB maximum after reassembly;
- compressed microphone request: 30 seconds or 128 KiB, whichever comes first;
- synthesized-audio fallback: 512 KiB per response;
- no more than one microphone upload and one response stream concurrently.

These are safety bounds, not performance targets.

## Audio policy

Physical testing found that stock XE24 is selectable as an iPhone call-audio
device but not as an ordinary A2DP media output. A companion-owned HFP spike
received no input buffers while the HFP route was selected; actively rendering
output caused iOS to fall back to its built-in microphone and receiver. The
compressed-audio GATT characteristics are therefore the selected application
speech path in both directions.

Microphone audio must be compressed before BLE transmission. The first device
spike should test API 19's built-in AMR-WB and AAC encoders. Raw 16-bit PCM is
diagnostic-only and must not be sent over BLE as the production format.

## Security boundary

The ping spike is intentionally non-sensitive and is not authorization to send
real user data. Before audio or actions are enabled, the protocol needs:

- explicit foreground enrollment;
- a per-installation device identity stored in iOS Keychain and protected local
  storage on Glass;
- mutual proof of possession using a reviewed standard cryptographic design;
- fresh session nonces, replay rejection, and token rotation;
- authenticated bridge requests scoped to the enrolled companion;
- redacted logs and ephemeral audio by default.

Do not design custom encryption primitives. The production construction must be
selected only after verifying the exact cryptographic providers available on
API 19 and iOS, then recorded in a separate ADR.
