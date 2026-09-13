# Cloud handoff — 2026-09-13

Local coordinator chat transferred this store and the dirty working tree so a Cloud Agent can continue. Product PRs must not include `.audit/`.

## Repo

- Origin: `sreichholf/dreamDroid`
- Default branch: `main` @ `730630b0`
- Handoff branch: `wip/high-fixes-cloud` (this snapshot)
- f01 already opened: https://github.com/sreichholf/dreamDroid/pull/376 (`fix/http-client-transport`)

## User standing orders

- One PR per fix unit (f01–f07, then remaining high clusters).
- Revalidate cited lines, failing test, then fix, then passing test.
- New code Kotlin, 4 spaces, 100-column, JDK 25. No Java under `app/src`.
- Prove UI with instrumented tests. Cloud: `bash .cursor/cloud/connected-test.sh <class>`. Do not drive the emulator GUI. Do not pass `-Pandroid.testInstrumentationRunnerArguments`.
- Do not merge rewrite work into `master`.

## Review program (done)

Store: `.audit/orchestrate/current-state-review/`
20 slice reports. r16 PASS. Other 19 ISSUES. Coordinator reports are condensations; inbox `*.txt` has more detail.

High user-facing clusters still after f01–f07:

1. Playback: `VLCPlayer.kt` playUri toggles pause; overlay next skips last; D-pad right on recording rewinds; `Stopped` finishes `VideoActivity`.
2. MultiEPG: `EpgDao.replaceChunk` wipes spanning events; duplicate bars; stale bouquet restore; time ruler not sticky.
3. TLS: process-wide trust-all from active profile; widget `setupSsl` mutates `HttpsURLConnection` defaults; stream URLs embed `user:pass` on `http://`.
4. Stuck UI: Signal acoustic beep busy-waits main thread; progress dialog ignores back; bouquet EPG overwrites picked time; drawer highlight ignores Back.

## High-fix program

Store: `.audit/orchestrate/high-fixes/`
Standing orders: `preferences.md`.

| Unit | State at snapshot | Paths |
| --- | --- | --- |
| f01 HTTP transport | Done. PR 376. Do not re-open. | `SimpleHttpClient.kt`, `SimpleHttpClientOkHttpTest.kt`, MockWebServer in `app/build.gradle` |
| f02 EnigmaClient null-on-HTTP-fail | In progress. `getServices`/`getEvents`/`getEpgNowNext` now return null. Loaders and `MultiEpgSync` updated. `EnigmaClientHttpFailTest.kt` added. Finish red/green on device. Do not revert f01. | `EnigmaClient.kt`, `EventListLoad.kt`, `EpgNowNextLoad.kt`, `ServiceListLoad.kt`, `BouquetListLoad.kt`, `MultiEpgSync.kt`, test |
| f03 generation tokens | In progress. Tokens on movie/service/now-playing/current. Finish tests. Key last-good to profile id. | `HubMovieListPage.kt`, `HubServiceListPage.kt`, `HubNowPlaying.kt`, `CurrentServiceDestination.kt`, matching tests |
| f04 timer edit | In progress. Destination/state/screen + `TimerEditScreenTest.kt`. Title must survive service pick. Failed save must show box error. | `ui/timers/` |
| f05 profiles/backup | In progress. Empty-host add, delete current profile, export toast. `BackupService` import-settings may still be open. | `ui/profiles/`, `ui/backup/`, `helpers/backup/` |
| f06 widgets | In progress. PendingIntent identity + missing profile. | `appwidget/` |
| f07 JVM tests on PRs | Not started. `app/src/test` is empty (NO-SOURCE). PR CI should run unit tests. | `app/src/test`, `.github/workflows/android-ci.yml` |

## How to split PRs

Each PR off `main`. Do not put `.audit/` in a product PR.

- f01: already #376. Leave it.
- f02–f06: branch from `main`, checkout only that unit’s files from this snapshot, finish verify, push, `gh pr create`.
- f02 tests need MockWebServer. Depend on #376 or include the one `app/build.gradle` androidTest line if #376 is not merged.
- f07 last among the numbered units so it does not fight `app/build.gradle` casually.
- Then remaining highs, one PR per cluster.

## Cloud verify

```
bash .cursor/cloud/connected-test.sh net.reichholf.dreamdroid.helpers.SimpleHttpClientOkHttpTest
bash .cursor/cloud/connected-test.sh net.reichholf.dreamdroid.enigma.EnigmaClientHttpFailTest
```

Nested KVM hangs. Emulator is TCG. Use the helper, not stock `connectedGoogleDebugAndroidTest`.
