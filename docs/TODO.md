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
- [ ] Short lag on the first role tap in onboarding (option lists are now cached; recheck on the release build)
- [ ] Predictive back gesture: page only shrinks, should slide like system apps (onboarding + detail screens)
- [ ] Settings → profile "Zmień" should open group selection for the current class directly
- [ ] Plan header chip (class + groups): open a dropdown with plan options instead of jumping to Settings; let it grow so text isn't cut ("F-infor…")
- [ ] Plan: after the day's last lesson, open on the next school day by default
- [ ] Detail screen (class/teacher/room plan): no animation when switching days or entering the screen; check every transition, not only tabs
- [ ] Onboarding/"Zmień" flow rework: step counter is wrong (shows "2 z 2" while more steps follow; class and groups share number 2); compute the real step list (role, class, groups if any, notifications if not allowed) and reuse it for editing the profile from Settings
- [ ] Teacher step with the keyboard open: grid shrinks to a strip and the name suggestions card collapses to a thin line; rethink the layout (e.g. name entry as its own step or sheet)
- [ ] Plan block for a cancelled group lesson repeats the group name as a chip (e.g. "Religia · Religia"); hide the chip when it equals the subject
- [ ] Sync line ("Zsynchronizowano …") shows the same grey box on tap as the bottom bar did; check other custom clickables (chips, tiles, rows) for it too
- [ ] Review guessed group section labels (EduPage divisions have no names) and subject names; long ones (e.g. PiKUA) keep the short

## Before stable
- [ ] Final app icon (owner provides)
- [ ] Maybe rename the project (app name, package id, repo) – do it before the first stable release
- [ ] APK file name for releases (e.g. `QbitPlan-<version>.apk`)
- [ ] Worker (`worker/`, TypeScript): cron every 5 min, today + tomorrow substitutions, per class/teacher hash in KV (write on change, TTL 3 days)
- [ ] FCM pushes to topics `klasa-<nazwa>` / `naucz-<imie-nazwisko>`; service-account key only in `wrangler secret`
  - needs: `google-services.json`, KV namespace ID
- [ ] App: subscribe to the profile's topic, respect notification toggle and weekend quiet mode
- [ ] Release signing (keystore + `keystore.properties`, outside git) and GitHub Releases workflow
- [ ] GitHub Actions `startup_failure` (account-level, owner checks on desktop)

## After stable
- [ ] Repo cleanup: drop dev leftovers (this file, unused fixtures/scripts, stale branches), tidy README
