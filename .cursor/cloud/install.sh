#!/usr/bin/env bash
# Cloud Agent install phase for dreamDroid.
# Idempotent: installs JDK 17, the Android SDK, an emulator AVD, and warms the
# Gradle build. Safe to re-run. Heavy stable state that a build snapshot keeps.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
CMDLINE_TOOLS_VERSION="11076708"
AVD_NAME="dreamdroid-verify"
SYSTEM_IMAGE="system-images;android-34;google_apis;x86_64"
SDK_PACKAGES=(
  "platform-tools"
  "platforms;android-34"
  "build-tools;34.0.0"
  "emulator"
  "$SYSTEM_IMAGE"
)

echo "== install: system packages (JDK 17, KVM, tools) =="
sudo apt-get update -qq
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
  openjdk-17-jdk qemu-kvm unzip curl

# AGP 8.2's jlink transform fails on JDK 21, so pin the JVM default to 17.
sudo update-java-alternatives -s java-1.17.0-openjdk-amd64 >/dev/null 2>&1 || true
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
echo "JAVA_HOME=$JAVA_HOME"
java -version

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
