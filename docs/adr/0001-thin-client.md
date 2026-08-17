# ADR 0001: Keep Glass as a thin client

- Status: Accepted
- Date: 2026-08-17

## Context

Google Glass Explorer Edition runs an obsolete Android/API 19 stack with limited memory, CPU, battery capacity, and modern TLS/library compatibility. The desired product requires current AI models, streamed speech, secrets, remote connectivity, and controlled computer access.

## Decision

Glass will own capture, presentation, gestures, and minimal session transport. A modern bridge will own provider SDKs, secrets, AI orchestration, policy, logging, and protocol adaptation. Codex execution will run as a separate, restricted worker behind an approval boundary.

The first implementation will use stock XE24, push-to-talk, a trusted local network, and one-shot camera capture.

## Consequences

Benefits:

- provider credentials never need to reside on Glass;
- AI and speech integrations can use maintained runtimes;
- the Glass APK stays small and compatible;
- computer actions can have stronger policy and audit controls;
- providers can change without reflashing the device.

Costs:

- the assistant needs a reachable bridge;
- transport latency and reconnection become core concerns;
- away-from-home use eventually needs a secure relay;
- offline capability is limited.

## Revisit when

- measured network latency makes the experience unusable;
- an on-device capability provides a clear privacy or reliability improvement;
- XE24 blocks a required feature and an AOSP migration has a tested recovery plan.
