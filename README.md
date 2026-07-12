# StalkerTV (Android TV)

Native Kotlin/Compose-for-TV app that connects to a stalker-m3u-server instance
via its Xtream Codes API emulation (`player_api.php`).

## Status
First working slice: login → live channel categories/rows → ExoPlayer HLS playback.
Not yet done: VOD/series, EPG grid, favorites, search.

## Requirements
- Android Studio (Koala+) with JDK 17 bundled
- An Android TV device/emulator (API 21+)

## Run
1. Open this folder in Android Studio — it will fetch the Gradle wrapper jar and dependencies on first sync (needs network).
2. Run the `app` configuration on an Android TV emulator or device.
3. On the login screen enter your server URL (e.g. `http://your-domain:port`) and Xtream username/password.

## Architecture
- `data/XtreamApi.kt` — Retrofit interface for `player_api.php` (login, get_live_categories, get_live_streams)
- `data/CredentialStore.kt` — persists server URL + credentials via DataStore, auto-login on relaunch
- `ui/login`, `ui/browse`, `ui/player` — Compose screens
- `AppViewModel.kt` — screen state machine (Login → Browse → Player)

## Next steps
- Add VOD (`get_vod_categories`/`get_vod_streams`) and series screens
- Add EPG (`get_short_epg`) row under the player or as a guide grid
- Replicate this pattern to Fire TV (same Android codebase, verify Amazon leanback banner requirements), then Android mobile, then iOS/tvOS (Swift/SwiftUI, separate project)
