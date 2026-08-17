# JGlass bridge

Future home of the modern bridge service that connects Glass to AI, speech, and controlled computer work.

Responsibilities:

- authenticate Glass;
- manage versioned sessions and reconnects;
- transcribe audio;
- call current OpenAI APIs;
- stream text to ElevenLabs and audio back to Glass;
- format concise HUD responses;
- propose actions and collect approvals;
- delegate approved coding work to a separate Codex worker;
- redact sensitive payloads from logs.

Do not choose the runtime or production dependencies solely from this placeholder. Start with a small audio round-trip spike, consult current official provider documentation, record the decision in an ADR, and keep all secrets outside source control.
