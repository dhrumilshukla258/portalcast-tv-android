# Server contract for the Android TV client

What `portalcast-server` requires of a first-party client on the `/api/v2/*`
surface. Derived 2026-10-18 from the server code and from the webui, which is
the reference client. Where the server docs and code disagreed, this file
follows the **code**. File references are into `portalcast-server/src` (S) and
`portalcast-webui/src` (W).

Read this before touching anything under `data/`. The Kotlin models in
`data/model/` must match the shapes here; nothing enforces that across repos.

---

## 1. Transport

- Base URL = user-entered server origin, e.g. `https://iptv.example.com` or
  `http://192.168.1.10:3000`. REST paths below are relative to it.
- JSON over HTTPS/HTTP. Send `Accept-Encoding: gzip` on JSON calls (OkHttp
  does this by default); the `/api/v2/epg` and `/api/v2/channels` bodies are
  large. Media requests must keep `Accept-Encoding: identity` (ExoPlayer
  default) so Range and Content-Length survive.
- Error body is `{ "error": string }`, sometimes `{ "success": false, "error" }`.
- No custom headers besides `Authorization`. Set a **User-Agent that does not
  contain** `Tizen`, `SMART-TV`, `SmartTV`, `Web0S` or `WebOS` (case-insensitive)
  on both OkHttp and the ExoPlayer data source. Those tokens make the server
  ignore `proxy=1` on live streams (S `routes/streaming/live.ts:316-318`).
  Use `PortalcastTV/<version> (Android <sdk>)`.
- Response headers from servers since 2026-10-21 (S `infra/httpCaching.ts`):
  every `/api` answer carries `X-IPTV: online|offline` (read in a network
  interceptor in `AppContainer`, `IptvStatus.report`; `/api/v2/health/iptv` is
  asked only after 5 quiet minutes). Stable reads (facets, details, Discover
  rows, catalog pages, groups) carry `ETag`, `Cache-Control: private,
  max-age=N, must-revalidate` and `Vary: Authorization`; the API OkHttp client
  has a 50 MB disk cache (`cacheDir/http-api`) that honours them, so an
  unchanged answer is a bodyless 304 and answers survive app restarts.
- Keep every model lenient: unknown keys ignored, every field nullable, and
  ids parsed from either string or number (`stream_id` is a number,
  `id` is a string, portal items mix both).

## 2. Auth

### 2.1 Gate and roles (S `server.ts:185-239`)

- Every path under `/api/` needs `Authorization: Bearer <accessToken>` except:
  `/api/auth/*`, `/api/images/*`, `/api/proxy*`, `/api/media/*`, `/api/vod/*`,
  `/api/v2/download*`, plus non-`/api` paths (`/live.m3u8`, `/player/*`,
  `/socket.io/*`, `/uploads/*`).
- Any failure is **401 `{"error":"Unauthorized"}`**; the server does not
  distinguish expired from invalid. Treat any 401 as "refresh once, retry once".
- Non-admin users get **403** on every POST/PUT/DELETE/PATCH except under
  `/api/user/*` and `/api/auth/device/authorize`, and on admin paths
  (`/api/admin/*`, `/api/config`, `/api/profiles`, `/api/v2/refresh-*`).
  All browse routes are GET, so this only matters if you add a mutation.

### 2.2 Tokens (S `routes/account/auth.ts`)

| Token | Payload | Lifetime |
|---|---|---|
| access | `{ userId, email, role, exp }` | 1 h |
| refresh, `clientType:"tv"` | `{ userId, type:"refresh", clientType:"tv", exp }` | 180 days |
| refresh, web | same with `clientType:"web"` | 30 days |

- `POST /api/auth/refresh` body `{ refreshToken }` → `{ accessToken, refreshToken }`.
  The refresh token is **echoed back unchanged** (no rotation, no sliding
  expiry). After 180 days the device must pair again.
- Refresh errors: 400 missing, 401 `Invalid or expired refresh token` or
  `User is disabled or does not exist`. Not rate limited.
- There is **no logout endpoint**; logout is clearing local storage.
- Store `accessToken`, `refreshToken`, `refreshIssuedAt`, `user{id,email,name,role,avatarUrl}`.
- Refresh strategy: an OkHttp `Authenticator` that refreshes once on 401
  (single-flight, queue concurrent requests), **plus** proactive refresh when
  the access JWT `exp` is within 5 minutes (decode the payload locally; HS256,
  `exp` in seconds).

### 2.3 Device-code pairing (primary TV login)

1. `POST /api/auth/device/code`, no body, not rate limited →
   `{ deviceCode: uuid, userCode: "ABC-DEF", verificationUrl: "<origin>/#/verify?code=ABC-DEF", expiresIn: 300 }`
2. Show `userCode` large, and a locally rendered QR of `verificationUrl`
   (the webui uses an external QR image service; do not copy that).
   Also show the plain URL under the QR.
3. Poll `POST /api/auth/device/poll` body `{ deviceCode }` every **4 s**,
   **one poll in flight at a time**:
   - `200 { status: "pending" }` keep going
   - `200 { status: "authorized", accessToken, refreshToken, user }` done.
     The server deletes the row on this response, so a duplicate poll gets
     `expired`. Stop polling the instant you get this.
   - `200 { status: "expired" }` unknown, consumed, or past 5 minutes.
     Offer "Generate new code". There is no `denied` state.
   - `403` user inactive. Show the error, offer new code.
4. The verification page is the webui's `/#/verify?code=` hash route. If the
   server has no `PUBLIC_BASE_URL` and the TV reached it by LAN IP, the QR
   contains that LAN IP; that is correct for a phone on the same LAN.

### 2.4 Password login (fallback, emulator and phone testing)

- `POST /api/auth/login` body `{ email, password, clientType: "tv" }` →
  `{ accessToken, refreshToken, user }`. **Always send `clientType:"tv"`** to
  get the 180-day refresh token.
- Errors: 400 missing fields or "Please log in using Google Sign-In",
  401 bad credentials, 403 pending approval or disabled.
- Rate limit: **5 per 5 min per IP**, then 429. Never auto-retry a login.
- Google sign-in is out of scope for v1.

### 2.5 Profile

- `GET /api/user/profile` → `{ id, email, name, role, avatarUrl, preferences }`.
  Call on app start when a token exists; 401 after a failed refresh means
  "go to pairing".

## 3. Browse routes (all GET, all Bearer)

Shared envelope for movies/series/groups:

```
{ success: true, page: number, pageAtaTime: 1, total_items: number,
  actual_length: 14, total_loaded: number, data: Item[], errors: false, isPortal: boolean }
```

`isPortal: true` = Stalker provider, `false` = Xtream. Page size is **fixed at
14** for `/movies` and `/series`; there is no page-size parameter.
Overrides (rename, hide, move, custom sort) are already applied to groups,
channels and list pages. Detail / drill-down responses are **not** renamed.

**Cold cache:** on a freshly activated profile `groups`, `channels`,
`movie-groups` and `series-groups` respond 302 to an admin-only `refresh-*`
route, which a normal user sees as **403**. Show "Server library not synced
yet, ask the admin" instead of retrying. (OkHttp follows the 302 by default;
disable `followRedirects` on the JSON client so the 302 is observable, or
treat 403 on these four routes as "cold".)

### 3.1 Live

- `GET /api/v2/groups` → bare array of `{ id: string, title: string, number?, ... }`.
  Virtual categories are `{ id: "vcat_…", title }` only. Drop portal "all"
  groups (`id === "*"`, `id === "0"`, or title `all`) like the webui does.
- `GET /api/v2/channels` → bare array, whole list, sorted by name. Fields:
  `id: string, name, number?, tv_genre_id: string, logo, screenshot_uri,
  enable_tv_archive?, tv_archive_duration?, isPortal,
  cmd: "<origin>/live.m3u8?t=<token>&id=<id>&proxy=1"` (absolute, pre-tokenized,
  already labelled for the admin view). Filter by `tv_genre_id` client-side.
  The server caches this per user for 4 h; the token inside `cmd` is reusable
  for that long.
- **Play a channel = play `cmd` as HLS.** Do not call `/api/v2/channel-link`.
- Catchup (Stalker only): append `&start_time=<n>&end_time=<n>` to `cmd`
  (server then 302s to the portal). On Xtream the params are ignored. v1
  does not implement catchup.

### 3.2 Movies

- `GET /api/v2/movie-groups` → envelope, `data` = groups as in 3.1.
  Prepend a synthetic `{ id: "*", title: "All" }` like the webui.
- `GET /api/v2/movies?category=<id|*>&page=<1..>&search=<q>&sort=<alphabetic|latest>&pageAtaTime=1`
  → envelope, `data` = list items:
  `id: string, stream_id: number, name, screenshot_uri, year, rating, rating_imdb,
  added, category_id, description, actors, director, genres_str, country, duration,
  backdrop_path?, backdrop_hd_path?, trailer_key?, cmd (DO NOT PLAY), stream_icon (DO NOT USE)`.
  Search is cache-only; empty means no match.
- Detail / playable file: `GET /api/v2/movies?movieId=<id>&category=<category_id or *>`
  → `data[0]` is the file object. Its `name` is often a quality string
  ("Hindi / 1080p"); keep the list item's title for display and labels.
  Not needed when you already have the list item; the webui calls it only to
  obtain a file id, which is the same `id` for movies on Xtream. Call it, take
  `data[0].id`, then go to movie-link.

### 3.3 Series

- `GET /api/v2/series-groups` → as movie-groups.
- `GET /api/v2/series?category=…&page=…&search=…&sort=…&pageAtaTime=1` →
  envelope, `data` = series list items (`id: string, series_id: number, name,
  screenshot_uri, cover (DO NOT USE), description, actors, genres_str, is_series: 1`).
- Seasons: `GET /api/v2/series?movieId=<seriesId>&category=<category_id or *>`
  → `data` = seasons `{ id, name: "Season N", number?, is_season: 1, screenshot_uri? }`.
  **`data[0]` is a season, not the series.** `category` is required; fall back to `*`.
- Episodes: `GET /api/v2/series?movieId=<seriesId>&seasonId=<seasonId>&page=<n>&pageAtaTime=1&category=<category_id or *>`
  → `data` = episodes `{ id, name, series_number (season), episode_num, time (minutes),
  description, screenshot_uri, is_episode: 1, cmd (DO NOT PLAY) }`, 14 per page.
- Episode playable file: `GET /api/v2/movies?movieId=<seriesId>&seasonId=<seasonId>&episodeId=<episodeId>&category=*`
  → `data[0]`. Yes, the **movies** route. Then movie-link with `series=<series_number>`.

### 3.4 Playback URL for VOD and episodes

`GET /api/v2/movie-link?id=<fileId>&series=<series_number or omit>&title=<display title>&category=<genres_str or category_id>`

- Response: `{ js: { cmd: "/api/proxy?t=<token>[&m3u8=1]", ...portal fields } }`.
  `cmd` is **relative**; prefix the server origin.
- `&m3u8=1` present → HLS, else progressive (Range-capable, seekable).
- `title` is what the admin "who's watching" view shows. Send the real movie
  title, or `"<Series> - <Episode title or Episode N>"`, never the file's
  own quality-string name.
- `itemId` / `itemType` (`movie` | `episode`): the browsed item, sent for the server's broken-stream
  tracking (3 failed attempts in 24 h auto-hide a movie; admins review them). Older servers ignore them.
- `series`: omit for movies. For episodes send `series_number`. (On Stalker
  the server passes it to the portal as the episode selector; on Xtream it is
  only used to look up the container extension.)
- 401 if the token is bad; mint a new link on any 401/403 from the stream.

### 3.4c Local library (servers from 2026-10-20)

Local NAS movies come through the same endpoints (server `docs/skill-local-library.md`):
`movie-groups` lists `{ id: "local", title: "My Library" }` first; `movies?category=local` pages
titles with ids `L<n>` (`is_local: 1`, `quality`); `movies?movieId=L<n>` returns the files, ids
`LF<n>`, best first, with `variantLabel`, `audio_tracks`, `subtitle_tracks`; `movie-link?id=LF<n>`
returns `/api/local/stream?t=` (progressive, range requests). Treat these ids as opaque strings.

### 3.4b Fields added 2026-10-19/20 (all optional; older servers omit them)

- Discover cards (`/discover/browse`, `/recommendations`): `name` is the cleaned title (channel
  prefix, language/quality tags removed), `description`, `network` + `network_logo`
  (channel/platform badge; logo is an image path, `ImageUrl.resolve` rules apply),
  `rating_best` + `rating_source` (the one rating to show: "7.8" + "IMDb", else "TMDB", else
  "TVmaze"; shown before the portal's `rating_imdb`), `imdb_id`, and critics' scores
  `rt_rating` ("94%") + `metascore` (0-100) for detail pages. For a series `director` holds
  its creators (shown as "Created by").
- `/discover/facets?type=series`: `networks: [{ value, count, logo? }]` (Channel / Platform
  filter; empty for movies/all). `/discover/browse` takes `network=`.
- Catalog list items (`/movies`, `/series`): `title_key` (the server's grouping key) and
  `clean_name`. Search groups a title's versions by `title_key`.
- Episodes (`/series?movieId=&seasonId=`): TMDB still in `screenshot_uri`, `description`,
  `time` (minutes), and a real title in `name` for placeholder names, when TMDB knows the show.
  Optional `seasonNumber=` helps the server pick the TMDB season.
- `/api/v2/media-info` (POST): the player reports measured width/height/fps/codec per title.

### 3.5 EPG

- Preferred (servers from 2026-10-19): `GET /api/v2/epg/window?hours=6[&ids=1,2]` →
  `{ timestamp, windowStart, windowEnd, data: { [channelId]: EPG_List[] } }` with only programmes
  overlapping the current hour + `hours` (1–24). Precomputed hourly on the server (~1.6 MB gzipped
  on a 10k-channel portal vs ~6.8 MB for the whole guide); weak ETag, `If-None-Match` → 304.
  Channels with nothing in the window are omitted; `data` is `{}` when there is no guide yet.
- Fallback (older servers answer 404): `GET /api/v2/epg` → `{ timestamp, data: { [channelId]: [...] } }`
  or `[]` when the cache is cold/stale (> 12 h). Whole guide (~50 MB raw on big portals).
- Both: timestamps are unix **seconds** as strings. The app stream-parses either body
  (`EpgStream`, keeps up to 4 programmes per channel in now..+6h) and refreshes every 30 min on Live.

### 3.6 Other

- `GET /api/v2/provider-key` → `{ providerKey: "stalker_<host>" | "xtream_<host>" }`.
  Namespaces the preference maps. Exists on servers from 2026-09-21 on;
  tolerate 404 (fall back to `isPortal`-derived `"stalker_unknown"`).
  Never call `/api/config`.
- `GET /api/v2/expiry` → `{ success, expiry: string|null }` (display as-is).
- `GET /api/carousel` → `CarouselSlide[]` (`title, description, imageUrl,
  actionType: none|play|details, mediaType, mediaId, order`). Optional for v1.
- Discover (page size 40):
  - `GET /api/v2/discover/facets?type=movie|series` → `{ genres, countries, themes, languages }`, each `[{ value, count }]`.
  - `GET /api/v2/discover/browse?type=&genre=a,b&country=&language=&theme=&page=` → `{ data, page, total_items }`.
  - `GET /api/v2/discover/recommendations?type=` → `{ data, basedOnTitle? }` (**basedOnTitle may be absent**).
  - **Answer versions** (server Phase 3a): every answer may carry `X-Catalog-Version` / `X-Home-Version`; catalog reads (Discover, people, people search, groups, movies/series lists) and Home then add `v=<version>` and an answer at the current version is cached for a year (`data/net/AnswerVersions.kt`). Never on a title's own answer (`movieId`, `seasonId`, `episodeId`: play links). No header: plain addresses, usual caching.
  - `GET /api/v2/home?type=` → `{ rows: [{ key, kind, title, data }], genreOrder }`: the viewer's personal rows, prepared on the server (Because you watched X, Top picks for you, More with <name>, Popular on this server), shown first, never paged; `genreOrder` (their genres, most watched first) orders the genre rows. A server without it (404) falls back to `discover/recommendations`. ETag/304 when unchanged.
  - `GET /api/v2/discover/variants?id=movie_123` → `{ variants: [{ …item, variantLabel }] }`.
  - Items: `{ id: "movie_<raw>"|"series_<raw>", type, name, title, screenshot_uri,
    backdrop_path?, backdrop_hd_path?, year, genres_str, country, category?, actors?, director? }`.
  - To open one: strip the prefix, call `/movies` or `/series` with
    `movieId=<raw>&category=<item.category ?: *>`; if empty, retry with `*`.
- Subtitles (phase 3): `GET /api/v2/subtitles/search?title&year&season&episode&lang`
  → `{ results: [{ id, fileId, language, releaseName, downloadCount }] }`;
  `GET /api/v2/subtitles/download?fileId=` → `text/vtt`, **needs Bearer**.

## 4. Images

Rule for every `screenshot_uri`, `backdrop_path`, `backdrop_hd_path`, `logo`:

```
if (path.startsWith("http")) path else "$origin/api/images$path"
```

`/api/images/*` is unauthenticated and only serves image extensions. TMDB
URLs come through absolute and load directly. Never use `stream_icon` or
`cover`; they are raw upstream URLs.

## 5. Streaming

### 5.1 Live HLS (`/live.m3u8`, `/player/<token>.ts`)

- Play the channel `cmd` as-is with `MimeTypes.APPLICATION_M3U8`.
- With `proxy=1` and a non-Smart-TV UA the server rewrites the playlist to
  root-relative `/live.m3u8?...&play=1` and `/player/<tok>.ts` entries;
  ExoPlayer resolves them against the playlist URL. Segment tokens are stable
  across polls.
- Errors: `/live.m3u8` 401 (bad/expired token), 404 `Stream Not Found`,
  `Upstream Error <n>`; `/player/` **403 plain text** on bad token, 404 when
  the player fell behind the live edge, 502 upstream.
- Recovery: on 401/403 refetch `/api/v2/channels` (new tokens) and restart.
  On 404/502 retry with backoff (1 s, 2 s, 4 s, cap 5 s, max 10).
- Alternative `proxy=0` (direct 302 to the CDN) saves server bandwidth but
  needs `allowCrossProtocolRedirects` and loses admin tracking. v1 keeps
  `proxy=1`; make it a setting later.

### 5.2 VOD (`/api/proxy?t=`)

- Progressive: bytes piped with Range support, so seeking works and the same
  URL can be reused for the whole session.
- HLS (`m3u8=1`): playlist rewritten; sub-URLs are freshly minted on each
  playlist fetch. Fine for ExoPlayer.
- 401 JSON on expired token: re-call movie-link and resume at the saved
  position.

### 5.3 Stream tokens

In memory on the server, **6 h idle TTL (sliding)**, lost on restart. Any
stream 401/403 means "re-resolve", never "re-login".

### 5.4 Rate limits (per client IP, fixed 60 s window)

- `live` bucket: `/live/*`, `/live.m3u8`, `/player/*` — **120 / min**.
- `vod` bucket: `/movie/*`, `/series/*`, `/api/media/*`, `/api/vod/play` — 120 / min.
- `/api/proxy*`, `/api/images*` and `/api/v2/*` JSON are **not** limited.
- 429 `{ "error": "Too Many Requests" }`, no `Retry-After`.
- Proxied live is roughly one playlist plus one segment per target duration.
  Behind a reverse proxy the whole household shares one bucket. ExoPlayer's
  load error policy must back off on 429 and never tight-loop on `/player/`.

## 6. User state

### 6.1 Preferences

- `PUT /api/user/preferences` body = partial object, **shallow merge at the
  top level**, so send nested maps whole. Allowlisted keys:
  `preferredContentType, favorites, recentChannels, videoFitMode,
  lastSelectedCategory, lastSelectedCategoryTitle, lastSelectedTvGroup,
  lastSelectedTvChannel, recentCategories, pinnedCategories, categoryOrder,
  recentSearches`. Anything else is silently dropped (`volume`/`muted` included).
- Shapes the TV client uses:
  - `favorites: string[]` channel ids
  - `recentChannels: string[]` max 20, newest first
  - `lastSelectedTvGroup: { [providerKey]: groupId }`
  - `lastSelectedTvChannel: { [providerKey]: channelId }`
  - `lastSelectedCategory: { [providerKey + "_movie" | "_series"]: categoryId, lastPlayedTvChannelId: id }`
  - `preferredContentType: "movie" | "series" | "tv"`
- Response `{ success, preferences }`; server then emits `preferences_changed`.

### 6.2 Progress (Continue Watching)

Saved at most every 30 s while playing (`DefaultProgressReporter`), and not when the position moved less than 5 s since the last save (a paused player sends nothing).

- `GET /api/user/progress` → `[{ mediaId, progress (seconds), completed, meta, updatedAt }]`
  scoped to the server's active profile.
- `PUT /api/user/progress` body `{ mediaId, progress, completed, meta }`
  (**PUT**, the docs say POST). `DELETE /api/user/progress/<urlencoded mediaId>`.
- Mirror the webui exactly or Continue Watching will not line up across devices:
  - Movies: `mediaId` = movie id. One row.
  - Episodes: **two rows per save**, one under the episode file id and one
    under the series id. Same `meta`.
  - `meta` = `{ id, playbackFileId, mediaId, itemId, seasonId, categoryId,
    type: "movie"|"series", title, currentTime, duration, progressPercent (int),
    timestamp (ms), name, episodeTitle, screenshot_uri, is_series: 0|1, cmd,
    series_number, catalogId: "movie_<id>"|"series_<id>" }`.
  - Save every **30 s** during playback, on pause, on stop, on back.
  - Skip when `currentTime <= 2`, no duration, or `>= 90 %`.
  - At ≥ 90 % or `ended`: PUT `completed: true` with `progress = duration`.
  - Resume when `progress > 2` and not completed.
  - Never for live TV.
- Continue Watching row = not-completed rows with `meta.type != "tv"`, sorted
  by `meta.timestamp` desc, deduped by `meta.id ?: itemId ?: mediaId`.
  Discover recommendations read `meta.progressPercent`, `meta.duration`,
  `meta.catalogId`, `meta.type`.

### 6.3 Socket.IO (phase 3)

- socket.io-client-java 2.x, default path and namespace, `transports: [websocket]`,
  no handshake auth.
- After every `connect`: emit `join_user_room { token: <accessToken> }`.
  No ack; a bad token fails silently. Re-emit after reconnect and after a
  token refresh.
- Listen for:
  - `preferences_changed { preferences }` → refetch `/api/user/profile`.
  - `config_changed { timestamp, hash }` (broadcast) → the admin switched or
    edited the active profile: re-read provider-key, groups, channels, EPG,
    categories, and drop in-memory caches.
- Casting events exist (`register`, `receive_cast_command`) and are unauthenticated.
  Out of scope until phase 4.

## 7. Known server-side quirks the client must tolerate

1. Movie/series list caches on the server ignore request origin, so image URLs
   can carry whichever origin first populated them. Always treat any absolute
   URL as loadable and any relative one as `/api/images`-prefixed.
2. `expiry` is a server-locale string on Xtream. Display, never parse.
3. `"-"` and `"No description available"` are placeholders, not values.
4. Upstream links in `cmd` reach the client **sealed** (`sealed:<opaque>`, server
   2026-10-20): pass the value back to movie-link/download-link unchanged, never
   play it, never log it. Older servers sent raw upstream URLs there; the same
   rule covers both.
5. The docs call the socket event `config-change`; the code emits `config_changed`.
6. `authCheck` accepts refresh JWTs as Bearer. Never send one as the access token.
