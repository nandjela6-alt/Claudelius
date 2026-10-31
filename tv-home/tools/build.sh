#!/usr/bin/env bash
# Builds dist/tvhome.apk without the Android SDK, using only artifacts from Maven Central
# (see fetch-toolchain.sh): aapt2 (bundled in apktool-lib), the Android 11 framework classes
# (Robolectric android-all), dx (repackaged) and Google's apksig.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
T="${TVTOOLS:-/opt/tvtools}"
APP="$ROOT/launcher"
B="$ROOT/build"
KEY="${TVHOME_KEYSTORE:-$ROOT/keystore/tvhome.p12}"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0}"

rm -rf "$B" && mkdir -p "$B"/{gen,classes,dex}

echo "== resources"
"$T/aapt2" compile --dir "$APP/res" -o "$B/res.zip"
"$T/aapt2" link -I "$T/android-framework.jar" \
  --manifest "$APP/AndroidManifest.xml" \
  --min-sdk-version 26 --target-sdk-version 30 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$B/gen" -o "$B/resources.apk" "$B/res.zip"

echo "== java"
find "$APP/src" "$B/gen" -name '*.java' > "$B/sources.txt"
javac -nowarn -Xlint:-options --release 8 -encoding UTF-8 \
  -cp "$T/android-all-11-robolectric-6757853.jar" \
  -d "$B/classes" @"$B/sources.txt"

echo "== dex"
java -cp "$T/dalvik-dx-16.0.1.jar" com.android.dx.command.Main --dex \
  --min-sdk-version=26 --output="$B/dex/classes.dex" "$B/classes"

echo "== package + sign"
python3 -I "$HERE/package_apk.py" "$B/resources.apk" "$B/dex/classes.dex" "$B/unsigned.apk"
if [ ! -f "$KEY" ]; then
  mkdir -p "$(dirname "$KEY")"
  keytool -genkeypair -keystore "$KEY" -storetype PKCS12 -storepass tvhome-local -keypass tvhome-local \
    -alias tvhome -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=TV Home (local build)" >/dev/null 2>&1
  echo "created signing key $KEY (keep it to install updates without uninstalling)"
fi
mkdir -p "$B/signer"
javac -nowarn -cp "$T/apksig-2.3.0.jar" -d "$B/signer" "$HERE/Sign.java"
mkdir -p "$ROOT/dist"
java --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.pkcs=ALL-UNNAMED --add-exports java.base/sun.security.util=ALL-UNNAMED \
  -cp "$T/apksig-2.3.0.jar:$B/signer" Sign "$KEY" tvhome-local tvhome "$B/unsigned.apk" "$ROOT/dist/tvhome.apk"
ls -la "$ROOT/dist/tvhome.apk"
