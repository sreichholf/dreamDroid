#!/usr/bin/env bash
# Cloud Agent start phase for dreamDroid.
# Per-boot: bring up the emulator that the instrumented Compose tests
# (:app:connectedGoogleDebugAndroidTest) require. Loads the quickboot snapshot
# baked in by install.sh so boot is fast. Idempotent: a booted device returns
# immediately. Reaches readiness, then returns.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/emulator.sh"

emu_launch quickboot
emu_wait_boot
