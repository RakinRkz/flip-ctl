#!/usr/bin/env bash
# Downloads what build.sh needs into $FLIPCTL_TOOLCHAIN (default ~/.local/share/flip-ctl-toolchain):
# JDK 17 (Adoptium), Android SDK platform 35 + build-tools 35.0.0, and the Shizuku API jars.
# Linux x86_64 only. Safe to re-run; finished parts are skipped. Accepts the Android SDK licenses.
set -euo pipefail

shizuku_api=13.1.5
toolchain=${FLIPCTL_TOOLCHAIN:-$HOME/.local/share/flip-ctl-toolchain}
mkdir -p "$toolchain"
cd "$toolchain"

if [ ! -x jdk17/bin/java ]; then
    echo "downloading JDK 17..."
    curl -fsSL -o jdk17.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
    mkdir -p jdk17 && tar -xzf jdk17.tar.gz -C jdk17 --strip-components=1 && rm jdk17.tar.gz
fi
export JAVA_HOME=$toolchain/jdk17

if [ ! -x sdk/build-tools/35.0.0/aapt2 ] || [ ! -f sdk/platforms/android-35/android.jar ]; then
    if [ ! -x sdk/cmdline-tools/latest/bin/sdkmanager ]; then
        echo "downloading Android command-line tools..."
        curl -fsSL -o cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
        rm -rf sdk/cmdline-tools && mkdir -p sdk/cmdline-tools
        unzip -q cmdline-tools.zip -d sdk/cmdline-tools && mv sdk/cmdline-tools/cmdline-tools sdk/cmdline-tools/latest
        rm cmdline-tools.zip
    fi
    echo "installing Android platform 35 and build-tools 35.0.0..."
    # `yes` dies of SIGPIPE once sdkmanager stops reading; don't let pipefail treat that as failure.
    { yes || true; } | sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root="$toolchain/sdk" \
        "platforms;android-35" "build-tools;35.0.0" >sdkmanager.log
fi

for lib in api provider aidl shared; do
    [ -f "libs/$lib/classes.jar" ] && continue
    echo "downloading Shizuku $lib $shizuku_api..."
    base=https://repo1.maven.org/maven2/dev/rikka/shizuku/$lib/$shizuku_api/$lib-$shizuku_api.aar
    mkdir -p "libs/$lib"
    curl -fsSL -o "libs/$lib.aar" "$base"
    # Maven Central publishes a SHA-1 next to every artifact.
    echo "$(curl -fsSL "$base.sha1")  libs/$lib.aar" | sha1sum -c --quiet
    unzip -q -o "libs/$lib.aar" classes.jar -d "libs/$lib" && rm "libs/$lib.aar"
done

echo "toolchain ready in $toolchain"
