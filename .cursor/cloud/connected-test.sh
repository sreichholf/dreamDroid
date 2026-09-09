#!/usr/bin/env bash
# Reliable instrumented-test runner for the Cloud Agent (software/TCG) emulator.
#
# The stock `:app:connectedGoogleDebugAndroidTest` pushes the ~196 MB universal
# debug APK over ddmlib's sync protocol; under TCG that read times out inside
# UTP's device controller (which ignores adbOptions.timeOutInMs). This helper
# uses a streamed `adb install` of the standalone x86_64 APK plus `am instrument`
# (the path AGENTS.md sanctions) so tests run green on the slow emulator.
#
# Usage: bash .cursor/cloud/connected-test.sh [testClassOrPackage]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_DIR="$REPO_ROOT/.cursor/cloud"
ENV_FILE="$HOME/.cursor/dreamdroid/env.sh"
if [ -f "$ENV_FILE" ]; then
  # shellcheck source=/dev/null
  source "$ENV_FILE"
fi
# shellcheck source=/dev/null
source "$SCRIPT_DIR/emulator.sh"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"
APP_APK="$REPO_ROOT/app/build/outputs/apk/google/debug/app-google-x86_64-debug.apk"
TEST_APK="$REPO_ROOT/app/build/outputs/apk/androidTest/google/debug/app-google-debug-androidTest.apk"
TEST_RUNNER="net.reichholf.dreamdroid.debug.test/androidx.test.runner.AndroidJUnitRunner"
FILTER="${1:-}"

if [ ! -f "$APP_APK" ] || [ ! -f "$TEST_APK" ]; then
  echo "== connected-test: building debug + androidTest APKs =="
  (cd "$REPO_ROOT" && ./gradlew --no-daemon :app:assembleGoogleDebug :app:assembleGoogleDebugAndroidTest)
fi

emu_launch quickboot
emu_wait_boot

echo "== connected-test: installing app + test APKs (streamed) =="
# The TCG emulator occasionally drops the package-service socket mid-install
# ("Broken pipe"); retry rather than fail the whole run.
adb_install() {
  local apk="$1" attempt
  for attempt in 1 2 3; do
    if "$ADB" -s "$SERIAL" install -r -t "$apk"; then
      return 0
    fi
    echo "connected-test: install attempt $attempt failed, retrying..." >&2
    "$ADB" -s "$SERIAL" wait-for-device || true
    sleep 10
  done
  echo "connected-test: failed to install $apk after 3 attempts" >&2
  return 1
}
adb_install "$APP_APK"
adb_install "$TEST_APK"

echo "== connected-test: running instrumentation =="
INSTR_LOG="$LOG_DIR/instrumentation.log"
if [ -n "$FILTER" ]; then
  "$ADB" -s "$SERIAL" shell am instrument -w -r -e class "$FILTER" "$TEST_RUNNER" | tee "$INSTR_LOG"
else
  "$ADB" -s "$SERIAL" shell am instrument -w -r "$TEST_RUNNER" | tee "$INSTR_LOG"
fi

# `am instrument` exits 0 even when tests fail, so inspect the summary.
if grep -qaE "^OK \([0-9]+ test" "$INSTR_LOG" && ! grep -qaE "^FAILURES!!!" "$INSTR_LOG"; then
  echo "== connected-test: PASSED =="
  exit 0
fi
echo "== connected-test: FAILED (see $INSTR_LOG) ==" >&2
exit 1
