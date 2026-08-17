# Open-source reuse and attribution

JGlass may incorporate licensed open-source code, but availability on GitHub is not itself permission to copy. This policy applies to code, binaries, images, audio, prompts, models, schemas, and documentation.

## Required workflow

Before importing or adapting anything:

1. Identify the canonical upstream repository and exact commit SHA.
2. Read the license file and relevant per-directory or per-file notices at that commit.
3. Confirm that the intended form of use—copy, modification, linking, distribution, assets, or protocol reimplementation—is permitted.
4. Check dependency and asset licenses separately; a repository license may not cover bundled media or third-party binaries.
5. Ask Joshua before importing GPL, AGPL, LGPL, SSPL, non-commercial, source-available, custom, or otherwise restrictive material.
6. Add a `THIRD_PARTY_NOTICES.md` entry in the same commit as the imported material.
7. Preserve required copyright headers and license texts.
8. Document substantial modifications and keep provenance visible in the affected source file when appropriate.

If no license is declared, do not copy or closely translate the implementation. Ideas, observed behavior, public protocols, and requirements may inform a clean-room implementation, but do not use upstream source while writing the replacement.

## Initial candidate inventory

License metadata below was checked against the GitHub repository API on 2026-08-17. Recheck the exact commit before use; repositories can add or change licensing later.

| Project | Relevant ideas | Verified repository license metadata | Current reuse status |
|---|---|---|---|
| [sbauwow/glass-apps](https://github.com/sbauwow/glass-apps) | Glass launcher, voice/LLM proxy, notifications, camera streaming, SSH/VNC, audio streaming | No declared license detected | Reference behavior only; do not copy until the owner supplies a license |
| [t3mr0i/ChatGPTGlass](https://github.com/t3mr0i/ChatGPTGlass) | Native Glass voice and OpenAI/vision interaction | No repository license detected; README mentions MIT but no complete machine-detected license | Treat as unlicensed until complete license terms are verified or permission is obtained |
| [tujson/GlassEcho](https://github.com/tujson/GlassEcho) | Android notification transport and Glass UI | MIT | Candidate for attributed reuse after reviewing the exact files and dependencies |
| [CatotheCat11/GlassTube](https://github.com/CatotheCat11/GlassTube) | Native voice triggers and modern network/media UX | No declared license detected | Reference behavior only; do not copy |
| [CatotheCat11/GlassNav](https://github.com/CatotheCat11/GlassNav) | Navigation HUD and phone location flow | GPL-3.0 | Do not import without an explicit component-licensing decision |
| [CatotheCat11/GlassSky](https://github.com/CatotheCat11/GlassSky) | Modern Glass cards, voice posting, QR provisioning | GPL-3.0 | Do not import without an explicit component-licensing decision |
| [Zer0xDev/Glassy](https://github.com/Zer0xDev/Glassy) | Flashing, APK management, rooting, mirroring | GPL-2.0 | Tool/reference only unless a deliberate GPL-compatible reuse decision is made |

Official Google Glass GDK documentation remains a primary behavioral reference. Check the license attached to any sample repository or copied snippet; Google's documentation currently states that code samples are Apache 2.0 unless otherwise noted.

## Notice template

Copy this into `THIRD_PARTY_NOTICES.md` for each approved import:

```markdown
## Component name

- Upstream: https://github.com/owner/repository
- Commit: full-40-character-sha
- License: SPDX identifier and link to copied license text
- Copyright: upstream notice
- Used in: repository paths
- Material used: files, classes, assets, or algorithms copied/adapted
- Modifications: concise description
- Verification date: YYYY-MM-DD
```

## Project license

JGlass itself currently has no public distribution license. Choose a project/component license before publishing source or distributing binaries. Copyleft imports may constrain that choice, which is why they require an explicit decision first.

This file is an engineering compliance policy, not legal advice. Escalate ambiguous licenses rather than guessing.
