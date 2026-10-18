# Manual test checklist

Run after every phase merge, on a phone over USB (`adb install -r app/build/outputs/apk/debug/app-debug.apk`)
and, for focus behaviour, on the Android TV (`adb connect <tv-ip>:5555`).
Automated checks first: `./gradlew assembleDebug testDebugUnitTest`.

Emulator instead of a TV: AVD `pcTV` (Android TV 34). Drive it with
`adb shell input dpad keyevent DPAD_CENTER` etc. — plain `input keyevent` is a
hardware-keyboard event and behaves differently in text fields (it typed a stray
"q"). For quick text entry, `input tap` the field then `input text`.

## Phase 0 (foundation)
- [ ] App launches to the "Server URL" placeholder with no session stored.
- [ ] `adb logcat | grep -i portalcast` shows no crash on start.

## Phase 1 (auth, data, playback)
- [ ] Server URL: `192.168.x.x:3000` (no scheme) is accepted; unreachable host shows a clear error, not a crash.
- [ ] Pairing: QR + code shown; approving from the phone (`/#/verify?code=`) lands on Home within ~4 s.
- [ ] Pairing: letting the code expire (5 min) shows "Generate new code"; new code works.
- [ ] Password login with `clientType:"tv"`; wrong password shows the server's message; 6th attempt in 5 min shows the rate-limit message.
- [ ] Kill and relaunch: still signed in (session restored), profile refetched.
- [ ] Simulate token expiry (edit `exp` is not possible; instead restart the server with a new `JWT_SECRET`): app returns to pairing, not a crash loop.
- [ ] Player: a live channel plays; a progressive movie plays and seeks; an HLS movie plays.
- [ ] Player: restart the server mid-stream → playback recovers by re-resolving (watch logcat for a single re-resolve, no tight loop).
- [ ] Progress: watch a movie > 30 s, back out, Continue Watching on the **webui** shows it at the right position.

## Phase 2 (browse)
- [ ] Live: groups list, channel grid, now/next from EPG, favourite toggle syncs to the webui.
- [ ] Movies: category rail, paging past 14 items, search, sort toggle, detail page, Play and Resume.
- [ ] Series: seasons tabs, episode list with progress bars, Play, next-episode prompt near the end.
- [ ] Discover: Continue Watching, Because You Watched, What's New, genre rows load lazily; filters; variant picker.
- [ ] Cold server cache (fresh profile as a non-admin): "library not synced" panel, no crash.
- [ ] Back navigation: player → where you came from; home → exit prompt/exit.

## TV-only (focus)
- [ ] Every tile reachable with d-pad only; focus visibly highlighted; no focus traps in rows.
- [ ] Focus is restored to the tile you left when returning from detail/player.
- [ ] Long content rows scroll the focused item into view.
