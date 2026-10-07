#!/usr/bin/env bash
# Builds build/Screens.apk without Gradle: aapt2 + javac + d8 + apksigner.
# Run ./setup-toolchain.sh once first; it fills $FLIPCTL_TOOLCHAIN (default
# ~/.local/share/flip-ctl-toolchain) with JDK 17, Android build-tools 35 and the Shizuku API jars.
#
# Signs with a local debug key unless a release key is given:
#   FLIPCTL_KEYSTORE=release.jks FLIPCTL_KEY_ALIAS=release FLIPCTL_KS_PASS=... ./build.sh
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")"

version_code=1
version_name=1.0

toolchain=${FLIPCTL_TOOLCHAIN:-$HOME/.local/share/flip-ctl-toolchain}
build_tools=$toolchain/sdk/build-tools/35.0.0
android_jar=$toolchain/sdk/platforms/android-35/android.jar
export JAVA_HOME=$toolchain/jdk17
export PATH=$JAVA_HOME/bin:$PATH
[ -x "$build_tools/aapt2" ] || { echo "toolchain missing in $toolchain; run ./setup-toolchain.sh" >&2; exit 1; }
libs=("$toolchain"/libs/*/classes.jar)

rm -rf build
mkdir -p build/assets/licenses build/classes build/dex build/gen
cp ../flipctl.sh build/assets/
# The APK bundles the Shizuku API (MIT), so its notice ships inside it too.
cp ../LICENSE ../THIRD_PARTY_NOTICES.md build/assets/licenses/

"$build_tools"/aapt2 compile --dir res -o build/res.zip
"$build_tools"/aapt2 link -I "$android_jar" --manifest AndroidManifest.xml \
    --min-sdk-version 33 --target-sdk-version 35 \
    --version-code "$version_code" --version-name "$version_name" \
    -A build/assets --java build/gen -o build/unsigned.apk build/res.zip

# --release 8 supplies java.* (android.jar lacks LambdaMetafactory); android.* comes from the classpath.
javac -nowarn --release 8 -cp "$android_jar:$(IFS=:; echo "${libs[*]}")" \
    -d build/classes $(find src build/gen -name '*.java')
jar cf build/classes.jar -C build/classes .
"$build_tools"/d8 --release --min-api 33 --lib "$android_jar" --output build/dex build/classes.jar "${libs[@]}"
(cd build/dex && zip -q ../unsigned.apk classes.dex)

"$build_tools"/zipalign -f -p 4 build/unsigned.apk build/aligned.apk
if [ -n "${FLIPCTL_KEYSTORE:-}" ]; then
    "$build_tools"/apksigner sign --ks "$FLIPCTL_KEYSTORE" --ks-key-alias "${FLIPCTL_KEY_ALIAS:-release}" \
        --ks-pass env:FLIPCTL_KS_PASS --v4-signing-enabled false --out build/Screens.apk build/aligned.apk
else
    keystore=$toolchain/debug.keystore
    [ -f "$keystore" ] || keytool -genkeypair -keystore "$keystore" -storepass android -keypass android \
        -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=flip-ctl debug" >/dev/null 2>&1
    "$build_tools"/apksigner sign --ks "$keystore" --ks-pass pass:android --v4-signing-enabled false \
        --out build/Screens.apk build/aligned.apk
    echo "note: signed with a local debug key; use FLIPCTL_KEYSTORE for anything you publish" >&2
fi
echo "built $(pwd)/build/Screens.apk ($version_name)"
