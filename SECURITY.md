# Security policy

JGlass is currently a private personal experiment, not a supported public product.

## Sensitive material

Never commit or publish:

- OpenAI, ElevenLabs, GitHub, relay, or cloud credentials;
- device tokens, private keys, signing keys, or provisioning profiles;
- Wi-Fi credentials or personal network details;
- notification bodies, transcripts, recordings, camera captures, or computer context;
- the Glass device serial number.

If a secret is committed, treat it as compromised: revoke or rotate it first, then remove it from history as a separate cleanup step.

## Remote access

Do not expose raw SSH, VNC, ADB, the bridge development server, or a Codex worker directly to the public internet. Remote designs require authentication, encryption, revocation, rate limiting, replay protection, scoped workspaces, explicit action approval, and a documented threat model.

## Device safety

Firmware flashing, partition erasure, rooting, recovery replacement, and bootloader operations can brick or wipe irreplaceable hardware. They require explicit approval and a verified recovery path.

## Reporting

While the repository is private, document suspected vulnerabilities in a private GitHub issue without including live secrets or personal captured data.
