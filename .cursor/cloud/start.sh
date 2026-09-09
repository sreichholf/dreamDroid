#!/usr/bin/env bash
# Cloud Agent start phase for dreamDroid.
# Per-boot: bring up the emulator that the instrumented Compose tests
# (:app:connectedGoogleDebugAndroidTest) require. Loads the quickboot snapshot
# baked in by install.sh so boot is fast. Idempotent: a booted device returns
# immediately. Reaches readiness, then returns.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$HOME/.cursor/dreamdroid/env.sh"
if [ -f "$ENV_FILE" ]; then
  # shellcheck source=/dev/null
  source "$ENV_FILE"
fi
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"

# shellcheck source=/dev/null
source "$SCRIPT_DIR/emulator.sh"

emu_launch quickboot
emu_wait_boot
