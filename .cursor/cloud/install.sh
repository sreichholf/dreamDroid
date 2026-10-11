#!/usr/bin/env bash
# Cloud Agent install phase for dreamDroid.
# Idempotent: installs the CI JDK, the Android SDK, an emulator AVD, and warms the
# Gradle build. Safe to re-run. Heavy stable state that a build snapshot keeps.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
# shellcheck source=../../scripts/ci-jdk.sh
source "$REPO_ROOT/scripts/ci-jdk.sh"
CMDLINE_TOOLS_VERSION="11076708"
AVD_NAME="dreamdroid-verify"
SYSTEM_IMAGE="system-images;android-34;google_apis;x86_64"
mapfile -t SDK_PACKAGES < <(grep -Ev '^[[:space:]]*(#|$)' "$REPO_ROOT/scripts/android-sdk-packages.txt")
SDK_PACKAGES+=("platform-tools" "emulator" "$SYSTEM_IMAGE")
ENV_FILE="$HOME/.cursor/dreamdroid/env.sh"

echo "== install: system packages (JDK $CI_JDK_VERSION, KVM, tools) =="
sudo apt-get update -qq
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
  "openjdk-${CI_JDK_VERSION}-jdk" qemu-kvm unzip curl

# Pin the Gradle JVM to the CI JDK. Fail if missing.
# Also write env.sh so later shells (start/tests) inherit it.
if [ ! -x "$CI_JDK_HOME/bin/java" ]; then
  echo "install: JDK $CI_JDK_VERSION missing at $CI_JDK_HOME" >&2
  exit 1
fi
sudo update-java-alternatives -s "java-1.${CI_JDK_VERSION}.0-openjdk-amd64" >/dev/null 2>&1 || \
  echo "install: update-java-alternatives failed; relying on JAVA_HOME=$CI_JDK_HOME" >&2
export JAVA_HOME="$CI_JDK_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p "$(dirname "$ENV_FILE")"
cat > "$ENV_FILE" <<EOF
export JAVA_HOME="$CI_JDK_HOME"
export PATH="\$JAVA_HOME/bin:\$PATH"
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
EOF
echo "JAVA_HOME=$JAVA_HOME"
java -version
java -version 2>&1 | grep -q "version \"${CI_JDK_VERSION}\\." || {
  echo "install: expected JDK $CI_JDK_VERSION on PATH, got:" >&2
  java -version >&2
  exit 1
}

echo "== install: Android command-line tools =="
mkdir -p "$ANDROID_SDK_ROOT"
if [ ! -x "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp_zip="$(mktemp /tmp/cmdline-tools.XXXXXX.zip)"
  curl -fsSL -o "$tmp_zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/tmp"
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools/tmp"
  unzip -q "$tmp_zip" -d "$ANDROID_SDK_ROOT/cmdline-tools/tmp"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  mv "$ANDROID_SDK_ROOT/cmdline-tools/tmp/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/tmp" "$tmp_zip"
fi

export ANDROID_SDK_ROOT ANDROID_HOME="$ANDROID_SDK_ROOT"
SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"

echo "== install: accept licenses and install SDK packages =="
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" --install "${SDK_PACKAGES[@]}"

echo "== install: create AVD '$AVD_NAME' =="
if ! "$AVDMANAGER" list avd 2>/dev/null | grep -q "Name: $AVD_NAME"; then
  echo "no" | "$AVDMANAGER" create avd \
    --name "$AVD_NAME" \
    --package "$SYSTEM_IMAGE" \
    --device "pixel" \
    --force
fi

echo "== install: point Gradle at the SDK (local.properties) =="
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > "$REPO_ROOT/local.properties"

echo "== install: warm Gradle build (assembleGoogleDebug + test APK) =="
cd "$REPO_ROOT"
JAVA_HOME="$JAVA_HOME" ./gradlew --no-daemon \
  :app:assembleGoogleDebug :app:assembleGoogleDebugAndroidTest

echo "== install: bake a booted quickboot snapshot into the base image =="
# Boot the emulator once now so a fresh agent's start.sh loads a warm snapshot
# instead of a ~10 min cold TCG boot. Best effort: never fail install on this.
# emu_wait_boot has a hard timeout so a stuck emulator cannot hang install forever.
if [ -w /dev/kvm ] || sudo chmod 666 /dev/kvm 2>/dev/null; then :; fi
# shellcheck source=/dev/null
source "$REPO_ROOT/.cursor/cloud/emulator.sh"
if emu_launch cold && emu_wait_boot; then
  echo "== install: saving quickboot snapshot 'default_boot' =="
  "$ANDROID_SDK_ROOT/platform-tools/adb" -s emulator-5554 emu avd snapshot save default_boot || true
  "$ANDROID_SDK_ROOT/platform-tools/adb" -s emulator-5554 emu kill || true
  sleep 3
else
  echo "install: emulator did not boot during install; start.sh will cold boot" >&2
fi

echo "== install: done =="
