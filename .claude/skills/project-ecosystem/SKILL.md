---
name: project-ecosystem
description: Use when a change here might need mirroring in portalcast-server, when deciding whether to share code/UI with portalcast-webui, or when asked how this repo relates to its sibling repos.
---

This repo is the native Android TV client of a 3-repo product family — see the top-level `portalcast-ecosystem` skill (in the shared parent folder, `docs/project_ecosystem.md`) for the full picture. This file is just the repo-scoped pointer.

- This app talks to `portalcast-server` via its **Xtream Codes API emulation** (`player_api.php`), not `/api/v2/*` — an earlier/different design choice from `portalcast-webui`, not necessarily one to repeat if this app's data layer is ever reworked.
- `data/XtreamModels.kt` must mirror the server's Xtream response field shapes — see the `xtream-client` skill.
- Not yet using the device-code pairing flow (`POST /api/auth/device/code`) that exists specifically for TV/no-keyboard auth — worth adopting instead of a custom flow if auth here is ever revisited.
- Don't port `portalcast-webui` components/UI into this app — it was explicitly tried on a Hisense Android TV via browser and found unintuitive for d-pad navigation; this app needs its own purpose-built 10-foot Compose-for-TV UI.

Full doc: `../../docs/project_ecosystem.md` (shared across all portalcast-* repos). This repo's other skills: `xtream-client`, `app-structure`.
