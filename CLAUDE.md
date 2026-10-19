# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

Native Android TV app (Kotlin, Jetpack Compose for TV, Media3/ExoPlayer) that is a
first-party client of `portalcast-server` over its `/api/v2/*` surface, signed in
through the server's own user system (device-code pairing or email/password).
It does **not** use the Xtream Codes emulation any more (removed 2026-10-18).

Read before changing anything:
- [docs/api-contract.md](docs/api-contract.md) — every server call, field for field.
  `data/model/` must match it; nothing enforces that across repos.
- [docs/architecture-plan.md](docs/architecture-plan.md) — decisions, package layout,
  screens, phase/agent split. Scope for v1: Discover, Live TV, Movies, Series.
- [docs/skill-project-ecosystem.md](docs/skill-project-ecosystem.md) — how this repo
  relates to the backend and the web app; do not port webui components here.
- [docs/test-checklist.md](docs/test-checklist.md) — manual checks per phase.

## Structure (`app/src/main/java/com/portalcast/tv`)

- `PortalcastApp.kt` — Application; owns `di/AppContainer` (manual DI, no Hilt) and the Coil loader
- `data/net/` — `ApiService` (Retrofit, one fun per route), `Json` (kotlinx + lenient
  serializers for mixed-type ids), `DynamicBaseUrlInterceptor` (user's server origin
  swapped in per request), `AuthInterceptor` (Bearer + proactive refresh),
  `TokenAuthenticator` + `TokenRefresher` (reactive single-flight refresh), `ApiError`
- `data/model/` — response/request shapes; everything nullable, `LenientStr`/`LenientInt` for ids
- `data/store/` — `SessionStore` (server URL, tokens, user; DataStore) behind the `SessionSource`
  interface the network layer depends on, and `LocalPrefs` (device-only UI state)
- `data/repo/` — `Repositories.kt` interfaces; one `Default<Name>Repository.kt` per interface;
  `SessionCache` (session-keyed TTL + single-flight cache), `RepoSupport` (category `*`
  fallbacks, synthetic All group), `ApiPaths` (route constants for cold-cache mapping)
- `playback/` — `PlaybackSession`, `StreamResolver`, `ProgressReporter`, `PlaybackHolder`
- `ui/nav/` — `Routes`, `AppNavHost` (session-gated), placeholders until screens land
- `ui/<feature>/` — auth, home, discover, live, movies, series, player, settings
- `util/` — `ImageUrl` (the one `/api/images` rule), `Jwt` (exp decode)

## Rules that are easy to get wrong

- Never play `cmd` from a list/detail response or show `stream_icon`/`cover`.
  Live: play `channel.cmd`. VOD/episode: `/api/v2/movie-link`, prefix the origin.
- Always pass `category` (use `*`) on `/movies` and `/series`; page size is 14, fixed.
- Progress rows and `meta` must mirror the webui (two rows per episode save,
  `catalogId`), or Continue Watching diverges across devices.
- The User-Agent must never contain Tizen/SMART-TV/SmartTV/Web0S/WebOS.
- A 302/403 on the four list routes means the server cache is cold, not a bug.
- Do not use `/api/config`; `provider-key` is the non-admin way to learn the provider.
- `AppJson` has `encodeDefaults = false`: any request field that must always be sent even
  at its default needs `@EncodeDefault` (see `LoginRequest.clientType`, `ProgressRecord`).
- Interceptors may only throw `IOException`s; anything else is swallowed by OkHttp
  (see `NoServerConfiguredException`).
- Always resolve a movie's file (`resolveFile`) before movie-link. Do NOT treat `cmd != null`
  as "already a file": Stalker list items all carry `cmd`, and sending the list id gives
  `nothing_to_play`. Fall back to `item.id` only when resolve fails on a Discover-resolved item.
- Compose key handlers: `onKeyEvent`/`onPreviewKeyEvent` must come BEFORE `focusable()` in
  the chain (after it they are children of the focus target and never fire; this left
  every player key dead once). Request initial focus after a frame and retry.
- Text fields use `ui/components/TvTextField`: read-only while merely focused, editable
  on Center. Compose opens the keyboard on focus, and the open keyboard captures the
  d-pad, which trapped focus in Search and typed stray letters.
- Rows/grids that replace a focused element (Retry → content) must not leave focus
  nowhere; Home recovers it, new screens hosted elsewhere need the same care.

## Build

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew assembleDebug testDebugUnitTest   # → app/build/outputs/apk/debug/app-debug.apk
```

Needs a JDK 17 and an Android SDK with `platforms;android-34`, `build-tools;34.0.0`
and `platform-tools`; point `local.properties` (gitignored) at it with forward
slashes (`sdk.dir=C:/path/to/Sdk`). The Gradle wrapper is committed. Unit tests are
plain JVM: `app/src/test` has a MockWebServer stack wired like `AppContainer`
(`TestStack.kt`) with hand-written fixtures under `src/test/resources/fixtures/`;
every repository, the refresh flow, the stream resolver and the progress reporter
have tests. Add a fixture + test whenever you add a route or change a model.

Run on a phone over USB (`adb install -r …`; sideloading ignores the leanback
requirement) for data/auth/playback checks, and on a real Android TV
(`adb connect <ip>:5555`) for focus behaviour. Kotlin gotcha: `/*` inside a KDoc
opens a nested comment, so write `/api/auth/` not `/api/auth/*` in comments.

## Brand

Logo, icons, banner and colors come from the shared kit in `../portalcast-webui/brand/` (see its README). The product name is written "PortalCast"; never "PortalcastTV". The Android vectors in `res/drawable/` (`ic_launcher_foreground`, `portalcast_logo`, `portalcast_symbol`) are translations of that kit; update them when the kit changes.
