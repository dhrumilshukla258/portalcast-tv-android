# Project ecosystem

**This repo:** `portalcast-tv-android` — native Kotlin/Compose-for-TV
Android TV app, package `com.portalcast.tv`. Its models (`data/model/`)
follow `docs/api-contract.md`.

**How it interacts with sibling projects:**
- Talks to `portalcast-server` over `/api/v2/*` and signs in with the
  server's own user system (device-code pairing, or email/password with
  `clientType:"tv"`). The server's Xtream Codes emulation was removed on
  2026-10-18 and this app no longer uses it. Any server-side change to an
  `/api/v2` shape must be checked against `docs/api-contract.md` and
  `data/model/`, since nothing enforces it across repos.
- Shares the API with `portalcast-webui` but not its UI: that repo's
  components are not reused here. This app is a purpose-built 10-foot UI
  (large focusable tiles, d-pad navigation, no hover/mouse assumptions).
  Behaviour that must match across devices (progress rows, preferences)
  mirrors the webui exactly; see the contract §6.

See `../../docs/project_ecosystem.md` (shared across all portalcast-* repos)
for the full multi-repo product ecosystem, planned future clients, and
cross-cutting design decisions.
