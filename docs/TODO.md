# TODO

Living list of what's left before the stable release. Remove this file during the post-release cleanup.

## In progress
- [ ] Owner testing the debug APK on a real phone; fix what comes back

## Before stable
- [ ] Final app icon (owner provides)
- [ ] APK file name for releases (e.g. `QbitPlan-<version>.apk`)
- [ ] Worker (`worker/`, TypeScript): cron every 5 min, today + tomorrow substitutions, per class/teacher hash in KV (write on change, TTL 3 days)
- [ ] FCM pushes to topics `klasa-<nazwa>` / `naucz-<imie-nazwisko>`; service-account key only in `wrangler secret`
  - needs: `google-services.json`, KV namespace ID
- [ ] App: subscribe to the profile's topic, respect notification toggle and weekend quiet mode
- [ ] Release signing (keystore + `keystore.properties`, outside git) and GitHub Releases workflow
- [ ] GitHub Actions `startup_failure` (account-level, owner checks on desktop)

## After stable
- [ ] Repo cleanup: drop dev leftovers (this file, unused fixtures/scripts, stale branches), tidy README
