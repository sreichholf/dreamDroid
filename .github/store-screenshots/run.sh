#!/usr/bin/env bash
# Takes the store screenshots of one device type on a booted emulator: installs the app
# split for the emulator's ABI and the androidTest APK, runs StoreScreenshotsTest's flow
# for <device> (phone, tablet or tv), and pulls the PNGs it saved into
# <out dir>/screenshots/<device>. A phone or tablet status bar is in demo mode (device
# clock, full signal and battery, no notifications).
#
# Usage: run.sh <device> <app apk dir> <androidTest apk> <out dir>
set -euo pipefail

DEVICE=$1
APK_DIR=$2
TEST_APK=$3
OUT_DIR=$4

PKG=net.reichholf.dreamdroid.debug
RUNNER=$PKG.test/net.reichholf.dreamdroid.testutil.HiltTestRunner
CLASS=net.reichholf.dreamdroid.screenshots.StoreScreenshotsTest

adb wait-for-device
timeout 60 bash -c 'until adb shell pm path android | grep -q package:; do sleep 2; done'

ABI=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')
adb install -r -g "$APK_DIR/app-google-$ABI-debug.apk"
adb install -r -g "$TEST_APK"

demo() {
  adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null
}
adb shell settings put global sysui_demo_allowed 1
demo enter
# The clock shows the device time the schedule is built from.
demo clock -e hhmm "$(adb shell date +%H%M | tr -d "\r")"
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4
demo network -e mobile show -e datatype none -e level 4
demo notifications -e visible false

mkdir -p "$OUT_DIR"
adb shell am instrument -w -r -e screenshots "$DEVICE" -e class "$CLASS#$DEVICE" "$RUNNER" \
  | tee "$OUT_DIR/instrument.txt"
demo exit

# Internal storage: shell cannot read Android/data without root; run-as can (debug build).
adb exec-out run-as "$PKG" tar -C files -cf - screenshots | tar -xf - -C "$OUT_DIR" || true
ls -lR "$OUT_DIR"
if ! grep -q "OK (1 test)" "$OUT_DIR/instrument.txt"; then
  adb logcat -d > "$OUT_DIR/logcat.txt"
  exit 1
fi
