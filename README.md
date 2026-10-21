<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/brand/portalcast-logo.svg">
    <img src="docs/brand/portalcast-logo-on-light.svg" alt="PortalCast" width="320">
  </picture>
</p>

<p align="center"><b>PortalCast for Android TV</b> is the native 10-foot app for PortalCast: built for the remote, fast on modest TV hardware.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Compose_for_TV-1.0-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Compose for TV">
  <img src="https://img.shields.io/badge/Android-5.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 5.0+">
  <img src="https://img.shields.io/badge/license-MIT-22C55E?style=flat-square" alt="MIT">
</p>

<p align="center">
  <a href="https://github.com/dhrumilshukla258/portalcast-server">Server</a> ·
  <a href="https://github.com/dhrumilshukla258/portalcast-webui">Web app</a> ·
  <b>Android TV app</b>
</p>

---

## Features

- **Discover** — a hero that follows focus, rows by genre and recommendation, Genre / Language / Country / Channel / Mood selectors, channel badges, Continue Watching (hold Center to remove a title).
- **Search** across all movies, series and live channels; versions of a title are one result.
- **Series** laid out like Netflix: show and seasons on the left, episodes with stills and descriptions on the right, opening on the episode you are watching.
- **Live TV** with now/next from the EPG, favourites and recent channels.
- **Player** — resume, next-episode prompt, Jellyfin-style control row (Up/Down) with subtitles (CC) and audio popups and per-title subtitle timing, real resolution and frame rate in the overlay.
- **Built for the remote** — every screen works with the d-pad only, focus is remembered per row, and grids keep your column however fast you scroll.
- Sign in with a pairing code from your phone; progress and favourites sync with the web app.

## Install

1. Run a [PortalCast Server](https://github.com/dhrumilshukla258/portalcast-server).
2. Download `portalcast-tv-<version>.apk` from [Releases](https://github.com/dhrumilshukla258/portalcast-tv-android/releases) and sideload it (for example with the Downloader app, or `adb install`).
3. Open PortalCast, enter your server address (`192.168.1.10:3000` or `https://tv.example.com`) and approve the code it shows from the web app.

Works on Android TV and Google TV devices running Android 5.0 or later; a phone works for testing.

## Build

```bash
./gradlew assembleRelease testReleaseUnitTest   # JDK 17, Android SDK 34
```

`local.properties` points at your SDK (`sdk.dir=C:/path/to/Sdk`, forward slashes). Release signing reads `PORTALCAST_KEYSTORE`, `PORTALCAST_KEYSTORE_PASSWORD`, `PORTALCAST_KEY_ALIAS` and `PORTALCAST_KEY_PASSWORD`; without them the build is debug-signed.

## Docs

- [docs/api-contract.md](docs/api-contract.md) — every server call the app makes, field by field
- [docs/architecture-plan.md](docs/architecture-plan.md) — design and package layout
- [docs/test-checklist.md](docs/test-checklist.md) — manual checks on a TV
- [CLAUDE.md](CLAUDE.md) — rules that are easy to get wrong

## License

MIT — see [LICENSE](LICENSE).
