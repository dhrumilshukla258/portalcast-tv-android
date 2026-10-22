# PortalCast design system (2026-10-22)

One design for the TV app and the webui (plan Phase 6). The same text lives in both repos
(`portalcast-tv-android/docs/design-system.md`, `portalcast-webui/docs/design-system.md`);
change both together. Sizes are TV dp and web CSS px: the same numbers (a 1080p TV interface
and a desktop browser show them at about the same physical size). PortalCast keeps its own
logo and accent colour.

## Colours

| Token | Value | Use |
| --- | --- | --- |
| `background` | `#0A0F1C` | Page background, behind everything |
| `surface` | `#121A2B` | Cards without art, rail, panels |
| `surfaceRaised` | `#1E293B` | Chips, buttons at rest, the active rail item |
| `surfaceHigh` | `#334155` | Hover / pressed |
| `primary` | `#38BDF8` | Accent: progress bars, active icon, links |
| `onPrimary` | `#082F49` | Text on primary |
| `text` | `#F1F5F9` | Main text |
| `textMuted` | `#94A3B8` | Meta lines, captions |
| `rating` | `#FBBF24` | The star before a rating |
| `focus` | `#FFFFFF` | Focus ring (TV) and keyboard focus (web) |
| `glass` | white 8 % / focused 18 %, border white 10 % | Buttons and panels over art |
| `error` | `#F87171` | Errors |

## Type

| Style | Size / weight | Use |
| --- | --- | --- |
| `display` | 40 / bold | Hero title when there is no logo art |
| `title` | 28 / bold | Detail page title, screen titles |
| `rowTitle` | 20 / semibold | Row headings ("Continue Watching"), with a 13 muted count after them ("1245 titles") |
| `body` | 14 / regular, line 1.5 | Overviews (hero: at most 3 lines, 560 wide; detail: 4 lines, 640 wide) |
| `meta` | 13 / medium | Meta lines: rating, year, age, runtime, genres |
| `caption` | 12 / medium | Card labels, cast roles |

## Spacing and shape

- Spacing steps: 4, 8, 12, 16, 24, 32, 48. Screen side padding 48 (web: 48 on desktop, 16 on a phone).
- Rows: 24 between rows, 12 between a row title and its cards, 16 between cards.
- Radius: cards 8, buttons and chips 10 (pill for chips), panels 16, cast photos round.

## Navigation (top tabs)

- Text tabs across the top, left to right: Home, Live TV, Movies, Series, Search, My List, Settings; the PortalCast logo on the left. The current tab in `text` with a 2 high `primary` underline, the others `textMuted`. Same on the TV and desktop web (the owner's reference: Fjord's TV and iPad layout).
- TV: Up from the first row reaches the tabs; moving across tabs switches the screen (the last tab is remembered). Web: the header already works this way.
- Web on a phone (under 768 wide): a bottom bar: Home, Live TV, Browse (Movies and Series), Search, My List; Settings from the profile button.
- Order of work: the content tweaks first (cards, hero, rows, detail), then the TV's move from its left rail to top tabs, as a step of its own (it changes how the remote moves).

## Cards

- Poster 2:3, width 130 (web: 150 on desktop, 112 on a phone), radius 8, image cropped to fill.
- Focus / hover: scale 1.06, white ring 2; the title appears under the focused card in `caption` ("Lanterns · S1E3" for an episode).
- Continue Watching uses wide cards: 16:9 (the title's backdrop, else its poster), 300 wide (web 320, phone 260), a 3 high `primary` progress bar on a white 20 % track along the bottom, and under it the title and "S1 E3 · 23 min left" (movies: "1h 12m left").
- Rating badge on posters in rows: top right, a dark pill (black 60 %) with the `rating` star and the rating ("★ 8.4"), `caption` size. Only for a real rating (`rating_best`).
- No art: `surface` tile with the title's first letter.
- Images at card size: w342 (web w500 at 2x), see the image sizes in the plan (Phase 4).

## Hero (Home, Movies, Series)

- Height 56 % of the screen. The backdrop sits right, faded into `background` from the left (to 60 % width) and from the bottom.
- Left column, 48 from the edge, 560 wide: the title (`display`; logo art may replace it when present), the episode line for something in progress ("S1 E3 · …And the Bag's in the River"), the year, the overview (3 lines), and for something in progress a 4 high progress bar with "30 min left".
- Follows the focused card (TV) or the hovered one (web), changing after 400 ms of rest so moving fast does not flicker.

## Detail page

- Full-screen backdrop, faded as the hero. Left column, 640 wide: logo art (or `title`), meta line (star + rating, outlined age badge, year, runtime, genres joined by " · "), overview (4 lines), "Directed by" in `textMuted`.
- Buttons in a row, 10 radius, 44 high: **Play** (white fill, dark text; "Resume S1E3" when there is progress), **Trailer** (glass), **Add to List / In My List** (glass, check icon when in the list).
- Then rows: **Cast** (round photos 88, name in `caption` bold, role in `caption` muted, 2 lines; opens the person page), **More Like This** (posters), and for series the seasons and episodes.

## Motion

- Focus scale 120 ms; hero and backdrop crossfade 300 ms; nothing else animates while scrolling (TV hardware drops frames).

## Screens

| Screen | Layout |
| --- | --- |
| Home | Hero (the first Continue Watching title, else the focused card), Continue Watching (wide cards), the server's personal rows (`/api/v2/home`), What's new, genre rows in the viewer's order |
| Movies / Series | A header ("Movies", "N films"), then rows: Trending now, Top rated, the personal rows for this type, genre rows; a "Categories" chip opens the per-category browse |
| Detail | As above |
| My List | Grid of posters, newest added first |
| Live TV | As today (categories, channels, now/next, the guide grid), with these colours; later the layout of the reference: categories left, channels in the middle with what is on, a preview with now/next on the right |
| Search | Search box, people and titles (as today), restyled with these tokens |
