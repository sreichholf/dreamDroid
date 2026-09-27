#!/usr/bin/env bash
# SessionStart hook for Claude Code on the web.
# Installs JDK 25 and the Android SDK (no emulator) so Gradle checks run in
# cloud sessions. Idempotent; the container is cached after the first run.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

REPO_ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
JAVA_HOME_25="/usr/lib/jvm/java-25-openjdk-amd64"
CMDLINE_TOOLS_VERSION="11076708"
# Same platforms/build-tools as android-ci.yml.
SDK_PACKAGES=(
  "platform-tools"
  "platforms;android-33"
  "platforms;android-34"
  "platforms;android-36"
  "platforms;android-37.0"
  "build-tools;36.0.0"
)

SUDO=""
if [ "$(id -u)" -ne 0 ]; then
  SUDO="sudo"
fi

if [ ! -x "$JAVA_HOME_25/bin/java" ]; then
  echo "session-start: installing JDK 25" >&2
  $SUDO apt-get update -qq
  $SUDO env DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-25-jdk-headless unzip >/dev/null
fi

SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  echo "session-start: installing Android command-line tools" >&2
  tmp_dir="$(mktemp -d)"
  curl -fsSL -o "$tmp_dir/tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
  unzip -q "$tmp_dir/tools.zip" -d "$tmp_dir"
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  mv "$tmp_dir/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp_dir"
fi

export JAVA_HOME="$JAVA_HOME_25"
export PATH="$JAVA_HOME/bin:$PATH"

missing=()
for pkg in "${SDK_PACKAGES[@]}"; do
  [ -d "$ANDROID_SDK_ROOT/${pkg//;//}" ] || missing+=("$pkg")
done
if [ "${#missing[@]}" -gt 0 ]; then
  echo "session-start: installing SDK packages: ${missing[*]}" >&2
  yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null 2>&1 || true
  "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --install "${missing[@]}" >/dev/null
fi

printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > "$REPO_ROOT/local.properties"

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export JAVA_HOME=\"$JAVA_HOME_25\""
    echo "export PATH=\"$JAVA_HOME_25/bin:$ANDROID_SDK_ROOT/platform-tools:\$PATH\""
    echo "export ANDROID_HOME=\"$ANDROID_SDK_ROOT\""
    echo "export ANDROID_SDK_ROOT=\"$ANDROID_SDK_ROOT\""
  } >> "$CLAUDE_ENV_FILE"
fi
