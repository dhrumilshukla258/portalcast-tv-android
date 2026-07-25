# Project ecosystem

**This repo:** `portalcast-tv-android` — native Kotlin/Compose-for-TV
Android TV app, package `com.portalcast.tv`. Its Retrofit models
(`data/XtreamModels.kt`) mirror the Xtream response shapes.

**How it interacts with sibling projects:**
- Talks to `portalcast-server` via the Xtream Codes API emulation
  (`/player_api.php` etc.), not `/api/v2/*` — an earlier/different design
  choice, not necessarily one to repeat for future clients. Any server-side
  change to login, categories, streams, or EPG request/response shapes must
  be verified against this repo's models, since they'll drift silently
  otherwise.
- Not related to `portalcast-webui` — that repo's components are not reused
  here; this app is a purpose-built 10-foot UI (large focusable tiles, d-pad
  navigation, no hover/mouse assumptions).

See `../../docs/project_ecosystem.md` (shared across all portalcast-* repos)
for the full multi-repo product ecosystem, planned future clients, and
cross-cutting design decisions.
