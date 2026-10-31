#!/usr/bin/env bash
# Downloads the build toolchain from Maven Central into $TVTOOLS (default /opt/tvtools).
set -euo pipefail
T="${TVTOOLS:-/opt/tvtools}"; M=https://repo1.maven.org/maven2
mkdir -p "$T"; TMP="$(mktemp -d)"
curl -fsSL -o "$TMP/apktool-lib.jar" "$M/org/apktool/apktool-lib/3.0.3/apktool-lib-3.0.3.jar"
case "$(uname -s)" in Darwin) A=macosx/aapt2;; *) A=linux/aapt2;; esac
unzip -o -q -j "$TMP/apktool-lib.jar" "prebuilt/$A" prebuilt/android-framework.jar -d "$T"
chmod +x "$T/aapt2"
curl -fsSL -o "$T/android-all-11-robolectric-6757853.jar" "$M/org/robolectric/android-all/11-robolectric-6757853/android-all-11-robolectric-6757853.jar"
curl -fsSL -o "$T/dalvik-dx-16.0.1.jar" "$M/com/jakewharton/android/repackaged/dalvik-dx/16.0.1/dalvik-dx-16.0.1.jar"
curl -fsSL -o "$T/apksig-2.3.0.jar" "$M/com/android/tools/build/apksig/2.3.0/apksig-2.3.0.jar"
rm -rf "$TMP"; ls -la "$T"
