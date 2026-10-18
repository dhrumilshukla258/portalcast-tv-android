---
name: app-structure
description: Use when touching navigation (ui/nav), the DI container (di/AppContainer), session gating, the Compose-for-TV screens under ui/, or the playback hand-off (playback/PlaybackHolder) in portalcast-tv-android.
---

Primary source for this subsystem is `CLAUDE.md` plus `docs/architecture-plan.md`; this file is the gotcha list.

1. **Navigation is session-gated in `ui/nav/AppNavHost.kt`.** Start destination is chosen after `SessionStore.isLoaded`; a session appearing or vanishing later calls `resetTo(...)` and wipes the back stack. Don't navigate to `home` from a login screen yourself — just persist the session (`AuthRepository.acceptTokens`) and the gate moves you.
2. **The player gets its input from `PlaybackHolder.start(session, resumeFromMs)`, not route args.** Sessions carry channel lists and tokens; they must not be serialised into the back stack. Call `PlaybackHolder.start` then `nav.navigate(Routes.PLAYER)`.
3. **Manual DI, one `AppContainer` per process** (`di/AppContainer.kt`, reachable as `context.appContainer`). Construction order matters: stores → `TokenRefresher` → clients → `ApiService` → repositories. Add new repositories at the bottom and keep constructor signatures stable, Phase agents wire against them.
4. **Three OkHttp clients, pick the right one.** `apiClient` (JSON: dynamic base URL, Bearer, refresh, redirects OFF so cold-cache 302s are visible). `mediaClient` (ExoPlayer + Coil: no Bearer, redirects ON, our User-Agent). `bareClient` (refresh only). Never give ExoPlayer the `apiClient`.
5. **Screens are Compose-for-TV (`androidx.tv.material3`).** Use its `Card`/`Surface`/`Button`; the only `androidx.compose.material3` import allowed is `OutlinedTextField` (no TV equivalent). Standard `LazyRow`/`LazyVerticalGrid` with `focusRestorer()` per tv-material 1.0 guidance; `tv-foundation`'s `TvLazy*` are deprecated.
6. **Every screen must be fully d-pad operable** and still work with touch (phone testing). No hover states, no gestures as the only path.
7. **Back handling** is Compose `BackHandler` inside the screen that needs it (player → pop), never an Activity `onBackPressed` override.
8. **`/*` inside a KDoc opens a nested comment in Kotlin.** Write `/api/auth/` not `/api/auth/*`.

No `docs/skill-app-structure.md` yet; promote this file when the UI grows past Phase 2.
