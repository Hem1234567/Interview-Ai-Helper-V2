#!/usr/bin/env sh
# The GitHub runner image already ships the Android SDK. This only pre-accepts the licences so Gradle can
# auto-install the pinned NDK and CMake. (android-actions/setup-android is deliberately not used.)
set -eu
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
echo "Android SDK: $SDK"
mkdir -p "$SDK/licenses"
printf '\n24333f8a63b6825ea9c5514f83c2829b004d1fee\n8933bad161af4178b1185d1a37fbf41ea5269c55\nd56f5187479451eabf01fb78af6dfcb131a6481e\n' > "$SDK/licenses/android-sdk-license"
printf '\n84831b9409646a918e30573bab4c9c91346d8abd\n' > "$SDK/licenses/android-sdk-preview-license"
