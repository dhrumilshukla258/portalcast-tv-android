# Ecosystem

`portalcast-tv-android` is the Android TV client. It does not stand alone — it's one
of several projects that make up the same product. When working here, keep the
following in mind:

## Sibling projects

- **`portalcast-server`** (`F:\Projects\stalker-m3u-server`) — the backend.
  Node/TypeScript, Sequelize/SQLite. Exposes the Xtream Codes API emulation
  (`player_api.php`, `/live/`, `/movie/`, `/series/`) that this app talks to.
  Any change to request/response shapes for login, categories, streams, or EPG
  must be verified against `src/routes/xtream/` in that repo — this app's
  Retrofit models (`data/XtreamModels.kt`) mirror those shapes and will drift
  silently if the server changes without a corresponding update here.
- **`portalcast-webui`** (`F:\Projects\stalker-ui`) — the browser-only web UI (React)
  for the same backend. Explicitly *not* reused for TV: it was tried on a
  Hisense Android TV via browser and found unintuitive for remote-control
  navigation. Do not port its components here; TV needs purpose-built
  10-foot UI (large focusable tiles, d-pad navigation, no hover/mouse
  assumptions).

## Planned/future platform clients (not yet started)

Each of these is a **separate project**, not a shared codebase — the team
decided against Kotlin Multiplatform / Compose Multiplatform because the
shared surface (a thin REST client) is too small to justify the tooling cost,
and Compose Multiplatform's tvOS support is weak anyway.

- **Fire TV** — same Android/Kotlin codebase as this repo, different packaging
  for Amazon Appstore submission.
- **Apple TV** — separate Swift/tvOS project, requires a Mac + Xcode.
- **iOS mobile** — separate Swift project.
- **Android mobile** — separate Kotlin project (touch-first UI, not a reuse of
  this TV app's screens).
- **Samsung Tizen / LG webOS / Hisense VIDAA** — these run web-app runtimes,
  but still need dedicated TV-UX codebases (not a repurposed browser UI, and
  not this Kotlin app).
- **Roku** — BrightScript/SceneGraph, entirely separate toolchain.

## Design decisions carried over from prior discussion

- Native per-platform over cross-platform frameworks (KMP, React Native,
  Flutter) — deliberate choice given the app's logic is thin (fetch, list,
  play) and platform-idiomatic UI/performance mattered more than shared code.
- The backend (`portalcast-server`) stays TypeScript/Node — it's I/O-bound,
  not CPU-bound, so a C++ rewrite was considered and rejected.
- Repos are split per platform, not a monorepo — different build toolchains
  (Gradle vs Xcode vs BrightScript) and independent app-store release cycles
  make a monorepo more friction than benefit here.
