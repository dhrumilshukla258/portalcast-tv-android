---
name: project-ecosystem
description: Use when a change here might need mirroring in portalcast-server, when deciding whether to share code/UI with portalcast-webui, or when asked how this repo relates to its sibling repos.
---

This repo is the native Android TV client of a 3-repo product family — see the top-level `portalcast-ecosystem` skill (in the shared parent folder, `docs/project_ecosystem.md`) for the full picture. This file is just the repo-scoped pointer.

- This app talks to `portalcast-server` over `/api/v2/*`, the same surface as `portalcast-webui` (the server's Xtream emulation was removed 2026-10-18).
- `data/model/` must match `docs/api-contract.md` and the webui's `src/api/types` — see the `data-layer` skill.
- Sign-in is the server's device-code pairing flow (primary) or email/password with `clientType:"tv"`.
- Features are kept the same as the webui (Search, Discover selectors and badges, episode rows and names, Continue Watching): change one, mirror the other.
- Don't port `portalcast-webui` components/UI into this app — it was explicitly tried on a Hisense Android TV via browser and found unintuitive for d-pad navigation; this app needs its own purpose-built 10-foot Compose-for-TV UI.

Full doc: `../../docs/project_ecosystem.md` (shared across all portalcast-* repos). This repo's other skills: `data-layer`, `app-structure`.
