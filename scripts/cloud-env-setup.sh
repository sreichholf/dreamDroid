#!/usr/bin/env bash
# Setup script for the Claude Code cloud environment (claude.ai/code →
# environment settings → Setup script):
#
#   curl -fsSL https://raw.githubusercontent.com/sreichholf/dreamDroid/main/scripts/cloud-env-setup.sh | bash || true
#
# The environment snapshot keeps what this writes (JDK 25, Android SDK,
# ~/.gradle) for about seven days, so new sessions start with the Gradle
# wrapper and the dependency cache already on disk instead of each one
# downloading them again. Sessions still run .claude/hooks/session-start.sh,
# which is a no-op on a warm snapshot. Never fails the session: a partial
# warm-up is still useful, and a setup script over ~5 minutes is not cached.
set -uo pipefail

dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT

git clone --quiet --depth 1 https://github.com/sreichholf/dreamDroid.git "$dir" || exit 0

CLAUDE_CODE_REMOTE=true CLAUDE_PROJECT_DIR="$dir" CLAUDE_ENV_FILE="" \
  bash "$dir/.claude/hooks/session-start.sh" || exit 0

cd "$dir" || exit 0
# :app:dependencies resolves every app configuration (compile, runtime, unit
# and instrumented test, KSP); spotlessCheck pulls ktlint. Nothing compiles.
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 timeout 240 \
  ./gradlew --no-daemon --no-configuration-cache --quiet -Pci \
  spotlessCheck :app:dependencies >/dev/null || true
exit 0
