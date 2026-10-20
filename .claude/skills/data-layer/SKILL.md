---
name: data-layer
description: Use when touching the /api/v2 data layer in portalcast-tv-android — data/net (ApiService, interceptors, token refresh, ApiError), data/model, data/store (SessionStore), or data/repo repositories and their MockWebServer tests.
---

Primary source: `docs/api-contract.md` (field-level server contract) and `CLAUDE.md`. This file is the gotcha list.

1. **Models must match `docs/api-contract.md`.** Every field nullable, ids as `LenientStr`/`LenientInt` (the server mixes strings and numbers). Add a fixture under `app/src/test/resources/fixtures/` and a test whenever a model or route changes.
2. **`AppJson` has `encodeDefaults = false`.** A request field that must always be sent needs `@EncodeDefault` (`LoginRequest.clientType = "tv"` was silently dropped before that fix, which would have issued a 30-day web refresh token instead of 180 days).
3. **Interceptors may only throw `IOException`.** OkHttp replaces anything else with an opaque "canceled" error; that is why `NoServerConfiguredException` exists.
4. **Refresh is single-flight in `TokenRefresher`**, used both proactively (`AuthInterceptor`, exp within 5 min) and reactively (`TokenAuthenticator`, on 401). A 400/401 from refresh clears the session; a network error does not.
5. **The JSON client does not follow redirects**, so the cold-cache 302 on groups/channels/movie-groups/series-groups surfaces as `ApiError.ColdCache`. Route paths for that mapping live in `data/repo/ApiPaths.kt`.
6. **Caches are per session** (`SessionCache`), cleared on logout or server change. `clearCaches()` on the user and live repositories is the hook for the socket `config_changed` event.
7. **Never log tokens or `cmd` values**, and never play a `cmd` from a list or detail response. A `cmd` can be sealed (`sealed:…`); send it back unchanged.
8. **New optional fields** (`network`, `network_logo`, `title_key`, `clean_name`, `description` on Discover cards, `networks` facet) are listed in `docs/api-contract.md` §3.4b; keep them nullable.
