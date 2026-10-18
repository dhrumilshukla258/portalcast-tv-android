# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

Native Android TV app (Kotlin, Jetpack Compose for TV, Media3/ExoPlayer) that
connects to a portalcast-server instance via its Xtream Codes API emulation.

See [docs/skill-project-ecosystem.md](docs/skill-project-ecosystem.md) for how this project relates to the
backend and other planned platform clients — read it before assuming this app
should share code/UI with the web app or other native clients.

## Structure

- `data/XtreamApi.kt` — Retrofit interface for `player_api.php` actions
- `data/XtreamModels.kt` — response models; must mirror `portalcast-server`'s
  `src/routes/xtream/` field shapes
- `data/CredentialStore.kt` — DataStore-backed credential persistence, auto-login
- `ui/login`, `ui/browse`, `ui/player` — Compose screens
- `AppViewModel.kt` — screen state machine (Login → Browse → Player) and data loading

## Status / not yet built

- Series episode picker (`get_series_info`)
- EPG grid
- Favorites, search

## Build

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew assembleDebug   # → app/build/outputs/apk/debug/app-debug.apk
```

Needs a JDK 17 and an Android SDK with `platforms;android-34`,
`build-tools;34.0.0` and `platform-tools`; point `local.properties`
(gitignored) at the SDK with `sdk.dir=C:/path/to/Sdk` (forward slashes —
the backslash-escaped form gets mangled by the properties loader). The Gradle
wrapper is committed, so no standalone Gradle install is needed. Android
Studio is still the easiest way to run on an emulator/device, but a plain
`./gradlew assembleDebug` is a valid type-check + build verification here, the
same way `npm run build` is in the other two repos. No test suite exists.

Known build warnings: `MainActivity.kt` overrides the deprecated
`onBackPressed()` (should move to `OnBackPressedDispatcher`).
