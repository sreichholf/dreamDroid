#!/usr/bin/env bash
# Shared emulator helpers for the dreamDroid Cloud Agent environment.
#
# Nested KVM guest execution hangs on Cursor Cloud VMs (the vCPU never runs even
# though /dev/kvm exists and kvm-ok passes), so the emulator is launched under
# software (TCG) emulation by default. Override with DREAMDROID_EMU_ACCEL=auto on
# a host with working nested virtualization.
set -euo pipefail

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
AVD_NAME="${DREAMDROID_AVD:-dreamdroid-verify}"
EMU_ACCEL="${DREAMDROID_EMU_ACCEL:-off}"
EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
SERIAL="emulator-5554"
LOG_DIR="$HOME/.cursor/dreamdroid"
EMU_LOG="$LOG_DIR/emulator.log"
# Whole wait (device online + boot_completed) must finish within this budget.
BOOT_TIMEOUT_SECS="${DREAMDROID_BOOT_TIMEOUT:-600}"
mkdir -p "$LOG_DIR"

emu_kvm_perms() {
  if [ -e /dev/kvm ] && [ ! -w /dev/kvm ]; then
    sudo chmod 666 /dev/kvm 2>/dev/null || true
  fi
}

emu_is_online() {
  "$ADB" devices 2>/dev/null | grep -q "^${SERIAL}[[:space:]]\+device$"
}

emu_is_booted() {
  [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]
}

# Launch the emulator in the background unless one is already booted.
# $1 (optional): "cold" to force a full boot ignoring any saved quickboot snapshot.
emu_launch() {
  local mode="${1:-quickboot}"
  emu_kvm_perms
  "$ADB" start-server >/dev/null 2>&1 || true
  if emu_is_booted; then
    echo "emulator: already booted"
    return 0
  fi

  local snap_flag="-no-snapshot-save"
  if [ "$mode" = "cold" ]; then
    snap_flag="-no-snapshot"
  fi

  local accel_flags="-accel off"
  [ "$EMU_ACCEL" = "auto" ] && accel_flags="-accel auto"

  echo "emulator: launching '$AVD_NAME' (accel=$EMU_ACCEL, mode=$mode)"
  nohup "$EMULATOR" -avd "$AVD_NAME" \
    -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect \
    $accel_flags $snap_flag \
    > "$EMU_LOG" 2>&1 &
}

# Wait until the device reports boot completed. Never blocks forever:
# polls for device online and boot_completed under BOOT_TIMEOUT_SECS.
emu_wait_boot() {
  echo "emulator: waiting for device + boot (timeout ${BOOT_TIMEOUT_SECS}s)"
  local deadline=$(( $(date +%s) + BOOT_TIMEOUT_SECS ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if emu_is_online && emu_is_booted; then
      "$ADB" -s "$SERIAL" shell input keyevent 82 >/dev/null 2>&1 || true
      echo "emulator: boot completed"
      "$ADB" devices
      return 0
    fi
    sleep 5
  done
  echo "emulator: boot did not complete in ${BOOT_TIMEOUT_SECS}s; see $EMU_LOG" >&2
  return 1
}
