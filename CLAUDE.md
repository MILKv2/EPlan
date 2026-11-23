# QbitPlan – project rules

Unofficial Android timetable + substitutions app for elektryk.edupage.org (GPL-3.0).
The owner does not write Kotlin; keep the code clean enough to be maintained by tools and reviewers.

## Code
- Idiomatic, well-structured Kotlin: MVVM + Repository, Hilt, Coroutines/Flow, Compose + Material 3.
- Comments only where the code can't say it (EduPage quirks, non-obvious decisions). English. No tutorial comments.
- Code, identifiers, logs, exception messages and commit messages in English; user-facing text in Polish in `strings.xml`.
- Pure logic lives in `:core` (no Android) and is unit-tested against `fixtures/`.
- Any change to parsing or substitution overlay must keep `CurrentTimetableOracleTest` green; when in doubt run `LiveOracleTest` (`QBIT_LIVE=1`).

## Performance – must run well from cheap Xiaomi phones to current flagships
- minSdk 26, low RAM and slow CPUs are the baseline, not an edge case.
- Never parse or compute on the main thread; parse each cached response once and share it (`shareIn`/`stateIn`), never per collector.
- Compose: immutable/stable UI models, `LazyColumn` with stable keys, no work in composition, keep per-second tickers isolated in small composables.
- Minimise network: fetch the timetable only when the version changed or the cache is stale; substitutions on demand.
- Release builds use R8 (minify + resource shrinking).

## Workflow
- Stages are summarised to the owner; wait for "ok" before the next stage unless told otherwise.
- The owner allows merging PRs once tests pass locally (CI on GitHub currently doesn't start: account-level `startup_failure`). Prefer squash merges.
- Secrets never go to git (keystores, `keystore.properties`, `.dev.vars`, service-account keys).
