# Glass client

Future home of the Google Glass Explorer Edition client.

Baseline constraints:

- stock XE24;
- Android API 19 / GDK;
- 640x360 display;
- no provider API keys;
- push-to-talk before wake word;
- one-shot camera capture before continuous vision.

Before adding code, create a minimal diagnostic project that proves build/install, microphone permission and capture, audio playback, camera invocation, touchpad gestures, and network transport on the physical device. Document the exact build and ADB commands here.

If code is adapted from another project, complete the workflow in `../../docs/OPEN_SOURCE_POLICY.md` and update `../../THIRD_PARTY_NOTICES.md` in the same commit.
