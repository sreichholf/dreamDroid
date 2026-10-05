#!/usr/bin/env bash
# Runs on the first boot of a fresh AVD, before android-emulator-runner saves
# the quickboot snapshot that the androidTest shards and upgrade115 restore.
# Returns only once the framework is up and has settled, so a restore does not
# come back mid-boot: after a restore sys.boot_completed already reads 1, and
# the runner's own `input keyevent 82` fails if the input service is not
# published yet (ReactiveCircus/android-emulator-runner#489).
set -euo pipefail

adb wait-for-device
timeout 300 bash -c 'until adb shell pm path android | grep -q package:; do sleep 2; done' \
  || { echo "package manager not ready" >&2; exit 1; }
# `cmd input` only exists from API 31; `service check` works on API 30.
timeout 300 bash -c 'until adb shell service check input | grep -q ": found"; do sleep 2; done' \
  || { echo "input service not published" >&2; exit 1; }
# Let the work that follows boot_completed (package scans, jobs) finish
# before the snapshot is taken.
sleep 30
echo "Generated settled AVD snapshot"
