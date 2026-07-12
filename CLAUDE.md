# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

Native Android TV app (Kotlin, Jetpack Compose for TV, Media3/ExoPlayer) that
connects to a stalker-m3u-server instance via its Xtream Codes API emulation.

See [docs/ECOSYSTEM.md](docs/ECOSYSTEM.md) for how this project relates to the
backend and other planned platform clients — read it before assuming this app
should share code/UI with the web app or other native clients.

## Structure

- `data/XtreamApi.kt` — Retrofit interface for `player_api.php` actions
- `data/XtreamModels.kt` — response models; must mirror `stalker-m3u-server`'s
  `src/routes/xtream.ts` field shapes
- `data/CredentialStore.kt` — DataStore-backed credential persistence, auto-login
- `ui/login`, `ui/browse`, `ui/player` — Compose screens
- `AppViewModel.kt` — screen state machine (Login → Browse → Player) and data loading

## Status / not yet built

- Series episode picker (`get_series_info`)
- EPG grid
- Favorites, search

## Build

No JDK 17/Gradle available in this dev environment — open in Android Studio to
sync and run on an Android TV emulator/device.
