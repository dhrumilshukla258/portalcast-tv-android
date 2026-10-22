# PortalCast design system (2026-10-22)

One design for the TV app and the webui (plan Phase 6). The same text lives in both repos
(`portalcast-tv-android/docs/design-system.md`, `portalcast-webui/docs/design-system.md`);
change both together. Sizes are TV dp and web CSS px: the same numbers (a 1080p TV interface
and a desktop browser show them at about the same physical size). PortalCast keeps its own
logo and accent colour.

## Colours

| Token | Value | Use |
| --- | --- | --- |
| `background` | `#05080F` | Page background, behind everything: black with a blue cast, no pictures (the rotating backdrop was removed 2026-10-22, owner: it did not sit well with the UI); the web adds a faint blue light from the top |
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

- Edge to edge (owner, 2026-10-22): the header, the filter bar and panels run the full width or height of the screen; no small floating rounded boxes for them. TV filter lists are a full-height panel along the left edge; the phone's filters and category list fill the screen.

- Spacing steps: 4, 8, 12, 16, 24, 32, 48. Screen side padding 48 (web: 48 on desktop, 16 on a phone).
- Rows: 24 between rows, 12 between a row title and its cards, 16 between cards.
- Radius: cards 8, buttons and chips 10 (pill for chips), panels 16, cast photos round.

## Navigation (top tabs)

- The same five tabs everywhere (owner, 2026-10-22: "same experience everywhere"): Home, Live TV, Sports, Search, My List. Phone: the bottom bar (icon over label). PC and TV: text tabs across the top after the PortalCast logo, a Settings icon on the right (TV); the current tab in `text` with a 2 high `primary` underline.
- Home's sections, the same everywhere: a chips row Movies · Series · Cast under the tabs (compact chips from the left; the chosen one filled white; choosing it again goes back to Home). It is the only row under the header on phone, PC and TV (owner: two rows at the top, always), with the page's Filters button on the right: a full-screen picker on a phone, a full-height side panel on PC and TV (Show Movies / Series on Home; My library and IPTV categories on Movies / Series; the five selectors). Movies, Series and People count as Home (its tab stays underlined).
- TV: Up from the content reaches the chips row (on Home and its sections) and then the tabs, Down goes back in; resting on a tab for 350 ms shows its screen, Center at once (the last screen is remembered). Back goes to the tabs, then out of the app.
- Web on a phone (under 640 wide): Settings from the profile button. Under 768 wide, Discover's filters are one "Filters" button (Home: a full row; Movies / Series: beside the title) opening a full-screen picker: Show (Movies / Series, on Home) or "Browse by category" (Movies / Series pages), then the five selectors, then a selector's values; the phone's Back steps back a level, then closes it; the row of selectors is for wider screens.
- Order of work: the content tweaks first (cards, hero, rows, detail), then the TV's move from its left rail to top tabs, as a step of its own (it changes how the remote moves).

## Cards

- Poster 2:3, width 130 (web: 150 on desktop, 112 on a phone), radius 8, image cropped to fill.
- Focus / hover: scale 1.06, white ring 2; the title appears under the focused card in `caption` ("Lanterns · S1E3" for an episode).
- Continue Watching (web, the owner's call 2026-10-22): the hero is the one landscape tile (the last title); the row under it (the other titles; the hero's is not repeated) is posters the size of the other rows', in one scrolling row like every other (never wrapping), a 3 high `primary` progress bar along the bottom and "Episode · 23 min left" under it. The TV keeps wide 16:9 cards (300 wide).
- Rows on a phone: left-aligned titles, posters only (the art carries the title), about 3.3 posters a screen; the first prepared row on Home has larger posters (about 2.3 a screen).
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
| Cast | Home's third section (named People before 2026-10-22): cast and crew as posters (photo 2:3, name, "Actor · 112"), most popular first, a chip per role (All, Actors, Actresses, Directors, Creators, Writers; Actors / Actresses once genders are known), a search box on the web (the TV searches people in Search); a person opens their page |
| Live TV | As today (categories, channels, now/next, the guide grid), with these colours; later the layout of the reference: categories left, channels in the middle with what is on, a preview with now/next on the right |
| Search | Search box, people and titles (as today), restyled with these tokens |
| Sports | The next 7 days of the admin's leagues by day ("Today", "Tomorrow", "Sat 24 Oct"), a chip per sport; a row per game: time (or LIVE), the fixture and "League · City", then the channel the guide shows it on (plays it; several: pick one) or the broadcasters and "Find channel" (Search on Live TV). Credit line "Schedule: sporting-events.org". |
