#!/usr/bin/env bash
# Installs the release APK on a device together with its baseline profile (.dm), so Android
# compiles the app's hot code AT INSTALL. A plain `adb install` (and a sideloaded GitHub APK)
# leaves the profile to the app's first launch and the device's idle maintenance, which on a TV
# can take days: until then the app runs through the JIT, which a trace on the owner's TV showed
# compiling during every slow frame (2026-10-24).
#
# Usage: tools/install-tv.sh [adb-serial]   (after ./gradlew assembleRelease)
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-adb}"
SERIAL=(); [ $# -ge 1 ] && SERIAL=(-s "$1")
APK=app/build/outputs/apk/release/app-release.apk
DM_DIR=app/build/intermediates/dex_metadata_directory/release/compileReleaseArtProfile
SDK=$("$ADB" "${SERIAL[@]}" shell getprop ro.build.version.sdk | tr -d '\r')
# dex-metadata-map.properties maps an API level to the .dm built for it (2147483647 = newest).
DM_REL=$(grep -E "^${SDK}=" "$DM_DIR/dex-metadata-map.properties" | cut -d= -f2 || true)
[ -z "$DM_REL" ] && DM_REL=$(grep -E "^2147483647=" "$DM_DIR/dex-metadata-map.properties" | cut -d= -f2)
TMP=$(mktemp -d)
cp "$APK" "$TMP/app-release.apk"
cp "$DM_DIR/$DM_REL" "$TMP/app-release.dm"   # the .dm must share the APK's base name
"$ADB" "${SERIAL[@]}" install-multiple -r "$TMP/app-release.apk" "$TMP/app-release.dm"
rm -rf "$TMP"
"$ADB" "${SERIAL[@]}" shell dumpsys package dexopt | grep -A2 '\[com.portalcast.tv\]' | tail -1
