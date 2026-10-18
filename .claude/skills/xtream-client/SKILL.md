---
name: xtream-client
description: Use when touching the Xtream API layer in portalcast-tv-android — data/XtreamApi.kt, data/XtreamModels.kt, data/ApiClient.kt, or data/CredentialStore.kt.
---

This repo has no deep reference doc yet (unlike `portalcast-server`/`portalcast-webui`) — this skill is primary source, grounded directly in the current code, not a compressed pointer to something longer.

1. **`XtreamModels.kt` must mirror `portalcast-server`'s `src/routes/xtream/` field shapes exactly** — nothing enforces this across repos; a server-side Xtream response change (a renamed/added field) needs a matching update here, checked by hand.
2. **`UserInfo` doesn't capture `user_info.password` at all, and `AppViewModel.login()` stores the literal password the user typed** (`Credentials(serverUrl, username, password)`), reusing it directly in every `liveStreamUrl()`/`vodStreamUrl()` call. Server-side, `user_info.password` in the real login response is actually a 30-day stream-token JWT specifically so players never have to carry the real password in stream URLs (see `portalcast-server`'s `xtream-provider` skill, "Stream Token Substitution") — this app isn't using that mechanism, so the real account password ends up in every stream URL (and whatever logs/analytics see those URLs). Worth fixing by adding a `password` field to `UserInfo` and using it (falling back to the real password only if a token isn't returned), not by design.
3. **Category → content fetch is two round-trips per tab** (`getXCategories` then, per category, `getXStreams`) — `loadLive`/`loadVod`/`loadSeries` in `AppViewModel.kt` swallow a per-category fetch failure into an empty list rather than failing the whole tab, but a whole-category-list failure (the first call) clears that tab's rows entirely.
4. **Credentials persist via `CredentialStore` (DataStore-backed) and auto-login on app start** — `AppViewModel`'s `init` block re-calls `login(..., persist = false)` if a saved credential exists and the screen is still on `Login`. Any change to the login flow needs to keep this silent-relogin path working, not just the interactive one.

No full reference doc beyond this file yet — if this subsystem grows, promote the detail above into a `docs/skill-xtream-client.md` and shrink this back to a pointer, matching the other two repos' pattern.
