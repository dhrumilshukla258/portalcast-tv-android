# Architecture plan: portalcast-tv-android v1

Written 2026-10-18. Scope agreed with the owner: **Discover, Movies, Series,
Live TV**, modelled on the webui's user flows but designed for a 10-foot
d-pad UI. No admin surface, no content manager, no casting in v1.

Companion documents:
- `docs/api-contract.md` — every server call the app makes, field for field.
- `../docs/project_ecosystem.md` — why this is a separate native app.

---

## 1. Decisions

| # | Decision | Why |
|---|---|---|
| 1 | **Switch from Xtream emulation to `/api/v2/*`** and delete the Xtream data layer. | Xtream has no Discover, no overrides on detail, no TMDB backdrops, no Continue Watching, and it puts the account password in every stream URL. The ecosystem doc already says Xtream was not a precedent to repeat. |
| 2 | **Device-code pairing is the primary login**; email/password is a secondary screen. | Built for TVs. Password login stays for the emulator, the phone and admins who want it. Google sign-in is out of scope. |
| 3 | **Single Gradle module**, package-per-layer, manual DI through an `AppContainer` on the `Application`. No Hilt. | Nine files today; Hilt adds KSP and build time for no gain at this size. Revisit above ~40 classes. |
| 4 | **Compose for TV** (`androidx.tv:tv-material 1.0.0`, already a dependency) with `navigation-compose` for routing. | Replaces the 3-state `Screen` enum with a real back stack: pairing → home → detail → player. |
| 5 | **kotlinx.serialization** with `ignoreUnknownKeys`, `isLenient`, `coerceInputValues`, plus a `LenientString` serializer for ids that arrive as string or number. Replace Moshi. | The server envelopes are untyped and drift. Moshi reflection chokes on mixed-type ids and the existing `@JsonClass(generateAdapter=true)` annotations do nothing without codegen. |
| 6 | **Media3 ExoPlayer 1.3.1** with HLS, custom User-Agent, `allowCrossProtocolRedirects`, and a `LoadErrorHandlingPolicy` that backs off on 429 and re-resolves on 401/403. | Already a dependency. The contract's rate-limit and token-expiry rules live in one place. |
| 7 | **Coil** for images with the `/api/images` prefix rule in one `ImageUrl.resolve()` helper. | One rule, one place; the webui repeats it in six components. |
| 8 | **DataStore (Preferences)** for server URL, tokens, user. No encryption layer. | TVs are single-user devices; `EncryptedSharedPreferences` is deprecated and adds risk of lockouts. Documented tradeoff. |
| 9 | **Progress and preferences mirror the webui's shapes exactly** (two rows per episode save, `meta.catalogId`, provider-keyed maps). | Continue Watching and Discover recommendations are shared across devices; any divergence shows up as duplicates on the web. |
| 10 | **Verification = `./gradlew assembleDebug` + JVM unit tests** against JSON fixtures captured from a real server, plus manual runs on a phone / TV. | No UI test harness in v1; the data layer is where drift bites. |

## 2. Package layout

```
com.portalcast.tv
├── PortalcastApp.kt            Application: builds AppContainer
├── di/AppContainer.kt          wires OkHttp, Retrofit, stores, repositories, player factory
├── data/
│   ├── net/
│   │   ├── ApiService.kt       Retrofit interface, one fun per contract route
│   │   ├── AuthInterceptor.kt  Bearer header + proactive refresh when exp < 5 min
│   │   ├── TokenAuthenticator.kt  reactive single-flight refresh on 401
│   │   ├── UserAgent.kt        "PortalcastTV/<ver> (Android <sdk>)"
│   │   └── Json.kt             kotlinx config + LenientString / LenientInt serializers
│   ├── model/                  one file per domain: Auth, Live, Vod, Series, Discover, Epg, User
│   ├── store/
│   │   ├── SessionStore.kt     DataStore: serverUrl, accessToken, refreshToken, refreshIssuedAt, user
│   │   └── LocalPrefs.kt       DataStore: UI-only state (last tab, proxy mode)
│   └── repo/
│       ├── AuthRepository.kt   pair(), login(), refresh(), logout(), session flow
│       ├── LiveRepository.kt   groups, channels (cached per session), epg, favorites, recents
│       ├── VodRepository.kt    movie groups, paged movies, file resolve, movie-link
│       ├── SeriesRepository.kt series groups, paged series, seasons, episodes, episode file, movie-link
│       ├── DiscoverRepository.kt facets, browse rows, recommendations, variants, open-item resolve
│       └── UserRepository.kt   profile, preferences (shallow-merge aware), progress (webui-shaped)
├── playback/
│   ├── PlayerFactory.kt        ExoPlayer with UA, HLS, cross-protocol redirects, error policy
│   ├── PlaybackSession.kt      what is playing: Live(channel) | Vod(movie,file) | Episode(series,season,ep,file)
│   ├── StreamResolver.kt       session → URL (channel.cmd, or movie-link); re-resolve on 401/403
│   └── ProgressReporter.kt     30 s ticks, 90 % completion, two-row episode writes
├── ui/
│   ├── theme/                  TV MaterialTheme, colours matching the webui's dark palette
│   ├── nav/AppNavHost.kt       routes below
│   ├── components/             MediaCard, PosterRow, FocusableTile, Backdrop, ErrorPanel, LoadingRow
│   ├── auth/                   PairingScreen (QR + code + polling), PasswordLoginScreen, ServerUrlScreen
│   ├── home/                   HomeScreen: left nav rail (Discover, Live, Movies, Series, Settings) + content pane
│   ├── discover/               DiscoverScreen: Continue Watching, Because You Watched, What's New, genre rows, filters
│   ├── live/                   LiveScreen: group list + channel grid, now/next from EPG, favourites
│   ├── movies/                 MoviesScreen (category rail + poster grid, paged 14), MovieDetailScreen
│   ├── series/                 SeriesScreen, SeriesDetailScreen (seasons tabs + episode list)
│   ├── player/                 PlayerScreen (ExoPlayer view, d-pad controls overlay, channel up/down for live, next-episode)
│   └── settings/               server URL, signed-in user, proxy toggle, sign out
└── util/                       ImageUrl, Jwt (exp decode), Result helpers
```

## 3. Screens and navigation

Routes (`navigation-compose`, string routes with typed args):

| Route | Screen | Notes |
|---|---|---|
| `server` | ServerUrlScreen | First run only. Validates by `GET /api/v2/provider-key` without auth → expect 401 (reachable) vs network error. |
| `pair` | PairingScreen | QR + code + "Use password instead". On `authorized` → `home`, clearing back stack. |
| `login` | PasswordLoginScreen | Email/password, `clientType:"tv"`. |
| `home/{tab}` | HomeScreen | Left nav rail; tab is one of `discover`, `live`, `movies`, `series`. Rail collapses when focus moves into content. |
| `movie/{id}?category=` | MovieDetailScreen | Backdrop, title, meta, Play / Resume, variants if any came from Discover. |
| `series/{id}?category=` | SeriesDetailScreen | Season tabs, episode list, per-episode progress bar, Play / Resume. |
| `player` | PlayerScreen | Takes the current `PlaybackSession` from a shared holder, not from route args. |
| `settings` | SettingsScreen | |

Session gate: `AppNavHost` observes `AuthRepository.session`; no session → `server`/`pair`; session → `home/discover`.

### 10-foot rules every screen follows
- Everything focusable is a `tv-material` `Card`/`Surface` with a visible focus scale and border. No hover, no touch assumptions (touch still works for phone testing).
- Rows are `TvLazyRow`, grids are `TvLazyVerticalGrid`; focus restoration on back (`rememberTvLazyListState`).
- Back: player → detail/home, detail → home, home → exit (with the rail focused first).
- Long-press / Menu on a channel tile toggles favourite. Play/Pause key starts playback from a focused tile.
- Text entry only on Server URL and Password screens (uses the system IME; a d-pad keyboard is fine there).

### What each tab shows (modelled on the webui)
- **Discover**: Continue Watching row (progress rows), "Because you watched X", "What's new" (browse page 1 unfiltered), then the top 10 genre rows loaded lazily with at most 2 concurrent fetches. A filter bar (Movies/Series, genre, country, language) opens as an overlay. Items open MovieDetail/SeriesDetail after resolving via the contract's open-item rule.
- **Live**: left column = groups (Recent, Favourites, All, then server groups); right = channel grid with logo, number, now-playing from EPG and a progress bar for the current programme. Enter plays. In the player, channel up/down switches with a 2 s debounce and shows a now/next overlay.
- **Movies**: category rail (All first) + poster grid paged 14 at a time, infinite scroll, sort toggle (Latest / A–Z), search via on-screen keyboard. Enter → MovieDetail.
- **Series**: same as Movies; Enter → SeriesDetail with seasons as tabs and episodes as a vertical list.

## 4. Playback and state rules (summary; full detail in the contract)

- Live: play `channel.cmd` as HLS. 401/403 → refetch channels, retry once. Never save progress.
- VOD/episode: resolve file id, call movie-link with `title`+`category`, play `origin + cmd`; `m3u8=1` decides HLS vs progressive. 401 mid-play → re-resolve, seek to last position.
- Progress every 30 s, on pause, on stop; completion at 90 %; episode saves write two rows; resume when > 2 s.
- Favourites, recent channels and last-selected group/channel sync through `PUT /api/user/preferences` with whole maps.
- Any `/api/*` 403 on the four list routes = cold server cache → "library not synced" panel.

## 5. Delivery phases and subagent split

Each phase ends with `./gradlew assembleDebug` green, unit tests green, and an
install on the phone. Agents run in isolated git worktrees on feature
branches, each owning disjoint packages, and I merge in order. Shared
interfaces are fixed in Phase 0 so agents can build against stubs.

| Phase | Work | Owner |
|---|---|---|
| **0. Foundation** (serial) | Delete Xtream layer. Add deps (kotlinx.serialization, navigation-compose, coil, zxing-core, mockwebserver, junit). `AppContainer`, `SessionStore`, `Json`, `UserAgent`, `ImageUrl`, `Jwt`. **All `data/model` classes and the `ApiService` interface** written from the contract. Repository *interfaces*. `AppNavHost` skeleton with placeholder screens. Theme. Builds and runs. | me |
| **1a. Data layer** | Repository implementations, `AuthInterceptor`, `TokenAuthenticator`, JSON fixtures captured from the running server (`src/test/resources/fixtures/*.json`), unit tests for parsing, refresh single-flight (MockWebServer), progress-row shaping, preference merge. | agent A |
| **1b. Auth UI** | ServerUrlScreen, PairingScreen (ZXing QR rendered to a Bitmap, 4 s poll, expired → regenerate), PasswordLoginScreen, SettingsScreen (sign out). Against the repository interfaces. | agent B |
| **1c. Playback** | `PlayerFactory`, `PlaybackSession`, `StreamResolver`, `ProgressReporter`, PlayerScreen with d-pad overlay (seek, play/pause, info, channel up/down for live, next episode). Against repository interfaces; tested with a static HLS/MP4 URL. | agent C |
| **2a. Live + Movies + Series** | HomeScreen + nav rail, LiveScreen (groups, grid, EPG now/next, favourites), MoviesScreen + MovieDetailScreen, SeriesScreen + SeriesDetailScreen, paging, search, sort. Shared `components/`. | agent D (after Phase 1 merge) |
| **2b. Discover** | DiscoverScreen rows, filters overlay, variants picker, open-item resolve, Continue Watching row. | agent E (parallel with D; owns only `ui/discover`) |
| **3. Polish** | Socket.IO (`config_changed`, `preferences_changed`), EPG refresh loop, subtitles (OpenSubtitles search/download with Bearer), proxy toggle, catchup for Stalker, Fire TV manifest/banner check. | agents as needed |
| **Integration** | Merge, build, install, run through the manual checklist on the phone and the Hisense TV, commit. | me, after every phase |

Agent prompts will point at `docs/api-contract.md`, this file, the Phase 0
interfaces, and the webui counterpart files for behaviour parity
(`useAppNavigation.ts`, `useProgressTracking.ts`, `useTVDeviceFlow.ts`,
`useDiscover.ts`, `useMediaLibrary.ts`).

## 6. Testing and devices

- **Build**: this laptop (JDK 17 + SDK 34 under `AppData\Local\Android`, see
  `CLAUDE.md`) and the Mac (same `./gradlew assembleDebug`).
- **Phone over USB**: the quickest loop. Sideloading ignores the manifest's
  leanback requirement, the app shows in the phone launcher, and `tv-material`
  cards respond to touch. D-pad behaviour can be driven with
  `adb shell input keyevent DPAD_DOWN|DPAD_CENTER|BACK`. Good for data,
  auth and playback; not a judge of focus UX.
- **Real Android TV (Hisense)**: enable Developer options → USB/Network
  debugging on the TV, then `adb connect <tv-ip>:5555` from the laptop and
  `adb install -r app-debug.apk`. This is the real focus-UX test.
- **Emulator**: TV images exist (`system-images;android-34;android-tv;x86`).
  On this laptop it needs the `emulator` package and Windows Hypervisor
  Platform (admin to enable). On the Mac (Apple Silicon) use the arm64 TV
  image; it is the fastest emulator option.
- **Manual checklist per phase** lives in `docs/test-checklist.md` (added in Phase 0).

## 7. Out of scope for v1

Admin dashboard, content manager, casting sender/receiver, Google sign-in,
downloads, local subtitle files, Jellyfin/.strm, Tizen/webOS, Roku.
