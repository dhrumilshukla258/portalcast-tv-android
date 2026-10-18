---
name: app-structure
description: Use when touching the screen state machine (AppViewModel.kt, sealed Screen class) or the Compose-for-TV screens (ui/login, ui/browse, ui/player) in portalcast-tv-android.
---

This repo has no deep reference doc yet — this skill is primary source, grounded directly in the current code.

1. **The whole app is one `AppViewModel` driving a 3-state `sealed class Screen`** (`Login` / `Browse` / `Player(url)`) — there's no navigation library, no back stack beyond `backToBrowse()` jumping straight to `Screen.Browse`. Don't assume a router exists when adding a new screen; it's a single `when(screen)`-style switch consumed wherever `screen: StateFlow<Screen>` is collected.
2. **Several features are explicitly not built yet** (per this repo's `CLAUDE.md`): series episode picker (`get_series_info`), EPG grid, favorites, search. Check that list before assuming a missing feature is a bug rather than not-yet-implemented scope.
3. **No JDK 17/Gradle in a typical sandboxed dev environment** — this app can only really be built/synced/run via Android Studio on a real machine or emulator. Don't expect a build/test command to succeed as verification here the way `npm run build`/`tsc` do in the other two repos.
4. **`playChannel`/`playVod` build the stream URL client-side from stored credentials** (see `xtream-client` skill for the real-password-in-URL issue this involves) rather than asking the server to resolve/tokenize it — different trust model from `portalcast-webui`, which never builds a stream URL itself and always gets a ready-to-use, pre-tokenized one back from the backend (see that repo's `video-playback` skill). Keep that asymmetry in mind before assuming a webui-side playback fix applies here too.

No full reference doc beyond this file yet — if this subsystem grows past the current 3-screen scope, promote the detail above into a `docs/skill-app-structure.md` and shrink this back to a pointer, matching the other two repos' pattern.
