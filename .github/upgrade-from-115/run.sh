#!/usr/bin/env bash
# Real 1.15 → 2.0 upgrade on a booted emulator.
#
# For each scenario: install 1.15 (v1.15.460), cold start it so its own code creates
# `dreambox` (Room v1), `dreamdroid` (SQLite v14) and the prefs file, seed
# seed-profile.sql and the global online picon prefs, `adb install -r` the 2.0 APK
# over it, cold start 2.0 from the launcher, then run Upgrade115Test against the
# upgraded data.
#
# Usage: run.sh <1.15 apk> <2.0 apk> <2.0 androidTest apk> <log dir>
set -euo pipefail

OLD_APK=$1
NEW_APK=$2
TEST_APK=$3
LOG_DIR=$4

PKG=net.reichholf.dreamdroid.debug
TEST_PKG=$PKG.test
RUNNER=$TEST_PKG/net.reichholf.dreamdroid.testutil.HiltTestRunner
PREFS=shared_prefs/${PKG}_preferences.xml
HERE=$(cd "$(dirname "$0")" && pwd)

mkdir -p "$LOG_DIR"

# adb shell joins its arguments into one remote command line, so callers pass
# a single string that is already quoted for the device shell.
as_app() {
  adb shell "run-as $PKG $1"
}

seed_sql() {
  local db=$1 table=$2
  sed "s/__TABLE__/$table/" "$HERE/seed-profile.sql" \
    | adb shell run-as "$PKG" sqlite3 "databases/$db"
  local n
  n=$(as_app "sqlite3 databases/$db 'SELECT count(*) FROM $table WHERE _id = 42'" | tr -d '\r')
  [[ "$n" == 1 ]] || { echo "seed into $db.$table failed (count=$n)" >&2; exit 1; }
}

wait_for() {
  local what=$1; shift
  for _ in $(seq 1 60); do
    if "$@" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "timed out waiting for $what" >&2
  exit 1
}

launch() {
  adb logcat -c
  adb logcat -b crash -c || true
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
}

no_crash() {
  local scenario=$1 label=$2
  adb logcat -d > "$LOG_DIR/$scenario-$label.logcat.txt" || true
  local crash="$LOG_DIR/$scenario-$label.crash.txt"
  adb logcat -b crash -d > "$crash" || true
  if grep -q "Process: $PKG" "$crash"; then
    cat "$crash" >&2
    echo "$label crashed" >&2
    exit 1
  fi
  adb shell pidof "$PKG" >/dev/null || { echo "$label is not running" >&2; exit 1; }
}

run_scenario() {
  local scenario=$1
  echo "::group::scenario $scenario"
  adb uninstall "$TEST_PKG" >/dev/null 2>&1 || true
  adb uninstall "$PKG" >/dev/null 2>&1 || true

  adb install "$OLD_APK"
  adb shell "dumpsys package $PKG | grep versionName"
  launch
  # 1.15 DreamDroid.onCreate: Room `dreambox` gets a Demo profile, the empty
  # `dreamdroid` file is created by DatabaseHelper, currentProfile is written.
  wait_for "1.15 currentProfile pref" as_app "grep -q currentProfile $PREFS"
  wait_for "1.15 dreamdroid file" as_app "ls databases/dreamdroid"
  sleep 5
  no_crash "$scenario" 1.15
  adb shell am force-stop "$PKG"

  case "$scenario" in
    room_v1)
      seed_sql dreambox profile
      ;;
    legacy_sqlite)
      # A pre-1.15 install: profiles only in the v14 SQLite file, no Room yet.
      as_app "rm -f databases/dreambox databases/dreambox-wal databases/dreambox-shm"
      seed_sql dreamdroid profiles
      ;;
  esac
  as_app "sed -i 's/name=\"currentProfile\" value=\"[0-9]*\"/name=\"currentProfile\" value=\"42\"/' $PREFS"
  # Global online picon settings of 1.15; 2.0 must carry them onto the upgraded profile.
  for key in picons_online use_name_as_picon_filename sync_picons_path; do
    as_app "sed -i '/name=\"$key\"/d' $PREFS"
  done
  as_app "sed -i 's#</map>#<boolean name=\"picons_online\" value=\"true\" /><boolean name=\"use_name_as_picon_filename\" value=\"true\" /><string name=\"sync_picons_path\">/media/hdd/picon</string></map>#' $PREFS"
  as_app "cat $PREFS" > "$LOG_DIR/$scenario-1.15-prefs.xml"
  grep -q 'name="currentProfile" value="42"' "$LOG_DIR/$scenario-1.15-prefs.xml" \
    || { echo "could not point currentProfile at 42" >&2; exit 1; }
  grep -q 'name="picons_online" value="true"' "$LOG_DIR/$scenario-1.15-prefs.xml" \
    || { echo "could not turn on online picons" >&2; exit 1; }

  adb install -r "$NEW_APK"
  adb shell "dumpsys package $PKG | grep versionName"
  launch
  sleep 15
  no_crash "$scenario" 2.0
  adb shell am force-stop "$PKG"

  adb install "$TEST_APK"
  local out="$LOG_DIR/$scenario-instrument.txt"
  adb shell am instrument -w -r \
    -e upgradeFrom115 "$scenario" \
    -e class net.reichholf.dreamdroid.Upgrade115Test \
    "$RUNNER" | tee "$out"
  echo "::endgroup::"
  # Each scenario passes one test (code 0) and skips the other (code -4).
  if grep -qE "INSTRUMENTATION_STATUS_CODE: -2|FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" "$out"; then
    echo "Upgrade115Test failed for $scenario" >&2
    exit 1
  fi
  if ! grep -q "INSTRUMENTATION_STATUS_CODE: 0" "$out"; then
    echo "Upgrade115Test ran no test for $scenario" >&2
    exit 1
  fi
}

adb wait-for-device
# A snapshot restore reports boot_completed before the framework is up.
wait_for "package manager" sh -c 'adb shell pm path android | grep -q package:'
adb shell command -v sqlite3 >/dev/null || { echo "no sqlite3 on the emulator" >&2; exit 1; }

run_scenario room_v1
run_scenario legacy_sqlite
echo "1.15 → 2.0 upgrade: both scenarios passed"
