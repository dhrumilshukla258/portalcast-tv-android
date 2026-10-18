# PortalcastTV (Android TV)

Native Kotlin / Compose-for-TV client for a [portalcast-server](https://github.com/dhrumilshukla258/portalcast-server)
instance. Talks to the server's `/api/v2/*` API and signs in with the server's own
user system (pair the TV from your phone, or email + password).

## Status

v1 in progress (see `docs/architecture-plan.md`): Discover, Live TV, Movies, Series.
Phase 0 (foundation: models, network, session, navigation skeleton) is done.

## Requirements

- JDK 17 and an Android SDK with platform 34 (Android Studio bundles both)
- An Android TV device or emulator (API 21+); a phone works for sideload testing

## Build and run

```bash
./gradlew assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Point `local.properties` at your SDK (`sdk.dir=C:/path/to/Sdk`, forward slashes).
On first launch enter your server address (e.g. `192.168.1.10:3000` or
`https://iptv.example.com`), then approve the pairing code from a phone that is
logged into the web UI.

## Docs

- `docs/api-contract.md` — what the app sends and expects from the server
- `docs/architecture-plan.md` — design, package layout, phases
- `docs/test-checklist.md` — manual checks
- `CLAUDE.md` — orientation for Claude Code sessions
