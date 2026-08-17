# JGlass protocol

Future home of versioned message schemas, compatibility fixtures, and protocol documentation.

The Glass implementation must remain compatible with Android API 19, so shared schemas do not imply a shared runtime package. Keep canonical, language-neutral fixtures and generate or hand-maintain small platform-specific models as appropriate.

Requirements:

- explicit protocol version;
- request, session, and action IDs;
- idempotent handling of retried side effects;
- forward-compatible unknown-field behavior;
- bounded payload sizes;
- cancellation and interruption events;
- no secrets in normal logs.
