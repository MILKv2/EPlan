# TODO

Living list of what's left before the stable release. Remove this file during the post-release cleanup.

## In progress
- [ ] Owner testing the APK on a real phone; fix what comes back

## Feedback from phone testing (S25+)
- [x] 4dft: groups showed unused EduPage divisions (1./2. Grupa, B-elektronik/D-automatyk); only divisions used by lessons now
- [x] Onboarding: steps slide instead of jumping; notifications step skipped when already allowed
- [x] Onboarding: teacher name field was cut off under the keyboard
- [x] Bottom bar: tabs slide left/right; no grey box on tap
- [x] Sync on app open when the last refresh is older than 2 min (was 10)
- [x] Short lag on the first role tap in onboarding: debug build only, gone on a normal build
- [x] Predictive back gesture: page only shrinks, should slide like system apps (onboarding + detail screens); now follows the finger, screens opened on top slide over the previous one
- [x] Settings → profile "Zmień" should open group selection for the current class directly (teachers: the name step)
- [x] Plan header chip (class + groups): open a dropdown with plan options instead of jumping to Settings; let it grow so text isn't cut ("F-infor…"). Menu: only my groups, change class/groups (or short/name), Settings
- [x] Plan: after the day's last lesson, open on the next school day by default (Friday evening → next Monday)
- [x] Detail screen (class/teacher/room plan): no animation when switching days or entering the screen; days are a swipeable pager now, content fades in
- [x] Onboarding/"Zmień" flow rework: step counter is wrong (shows "2 z 2" while more steps follow; class and groups share number 2); compute the real step list (role, class, groups if any, notifications if not allowed) and reuse it for editing the profile from Settings
- [x] Teacher step with the keyboard open: grid shrinks to a strip and the name suggestions card collapses to a thin line; name entry is its own step, the heading text and the button fold away while typing a search
- [x] Plan block for a cancelled group lesson repeats the group name as a chip (e.g. "Religia · Religia"); hide the chip when it equals the subject
- [x] Sync line ("Zsynchronizowano …") shows the same grey box on tap as the bottom bar did; check other custom clickables (chips, tiles, rows) for it too (absent-teachers header was the other one)
- [x] Long names cut on lesson tiles ("Aplikacje Interneto…", "1at · Infor…"): tap a tile for a details sheet (full names, old → new, EduPage text); titles wrap in blocks of two periods or more
- [x] Review guessed group section labels (EduPage divisions have no names) and subject names; long ones (e.g. PiKUA) keep the short. All divisions in plan 189 get a label ("grB / grF" = profession)

## Before stable
- [ ] Final app icon (owner provides)
- [ ] Maybe rename the project (app name, package id, repo) – do it before the first stable release
- [ ] APK file name for releases (e.g. `QbitPlan-<version>.apk`)
- [x] Worker (`worker/`, JS + JSDoc): cron every 5 min, today + next 2 school days, per class/teacher hash in KV (write on change, TTL 3 days)
- [x] FCM pushes (date only) to topics `klasa-<nazwa>` / `naucz-<imie-nazwisko>`; the app fetches the day and diffs its own plan
- [x] App: subscribe to the profile's topic, respect notification toggle and weekend quiet mode
- [ ] Worker setup in Cloudflare: KV namespace ID into `wrangler.toml`, Git import (root `worker`), secret `FCM_SERVICE_ACCOUNT`
- [ ] Live test of pushes on the phone (debug build is a separate Firebase app `dev.milkthe.qbitplan.debug`)
- [x] Crash screen ("Coś poszło nie tak") instead of the system dialog
- [ ] Release signing (keystore + `keystore.properties`, outside git) and GitHub Releases workflow
- [ ] GitHub Actions `startup_failure` (account-level, owner checks on desktop)

## After stable
- [ ] Repo cleanup: drop dev leftovers (this file, unused fixtures/scripts, stale branches), tidy README
