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

The exact fragment header and window size remain deliberately undecided until
the first Xcode-backed throughput spike. The chosen framing must provide:

- protocol version and frame kind;
- stream identifier;
- monotonically increasing fragment sequence;
- first and final fragment markers;
- bounded reassembly size and timeout;
- explicit cancellation;
- backpressure rather than unbounded buffering;
- duplicate detection for retried control messages.

The initial limits are:

- control message: 4 KiB maximum after reassembly;
- compressed microphone request: 30 seconds or 128 KiB, whichever comes first;
- synthesized-audio fallback: 512 KiB per response;
- no more than one microphone upload and one response stream concurrently.

These are safety bounds, not performance targets.

## Audio policy

Physical testing found that stock XE24 is selectable as an iPhone call-audio
device but not as an ordinary A2DP media output. The preferred speech path is
therefore a companion-owned full-duplex HFP audio session, if the Xcode spike
confirms that route. The audio-downlink characteristic is the fallback when an
iOS application cannot reliably use HFP.

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
