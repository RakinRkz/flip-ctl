---
name: install-app
description: Build the Screens APK and install it on the connected phone without breaking a running mode or the user's Shizuku setup; also covers starting Shizuku over USB. Use when the app or the bundled flipctl.sh changed and needs to reach the phone.
---

# Building and installing Screens

1. **Build.** `app/build.sh` (run `app/setup-toolchain.sh` once if it reports a missing toolchain).
   Script-only changes still need a rebuild for the app: it ships its own copy of `flipctl.sh`.
   `./flip-ctl` alone is enough to test script changes from the PC.

2. **Make sure it's the right phone and nothing is running** before replacing the app:
   ```sh
   adb devices -l                                                  # the intended Flip (model/serial)?
   adb shell cmd device_state print-state                          # must be normal (3)
   adb shell 'ps -A -o ARGS | grep -E "[k]eep-loop|[r]evert-after"' # must be empty
   ```
   Otherwise run `./flip-ctl main` first. Replacing the app mid-mode once broke display composition.

3. **Install.** `adb install -r app/build/Screens.apk`.
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE` means the installed copy is signed with another key (debug vs
   release). Uninstalling fixes it but loses the app's Shizuku approval, so **ask the user first**;
   afterwards they must open Screens, tap "Tap here to allow Screens in Shizuku" and choose Allow.

4. **Permissions.** Notifications: `adb shell pm grant io.github.rakinrkz.flipctl android.permission.POST_NOTIFICATIONS`.
   "Display over other apps" is granted by the app itself through Shizuku on the first cover/dual
   switch; don't pre-grant it when testing that path.

5. **Shizuku.** If `adb shell pidof shizuku_server` is empty, start it over USB:
   ```sh
   adb shell 'd=$(dirname $(pm path moe.shizuku.privileged.api | cut -d: -f2)); $d/lib/arm64/libshizuku.so'
   ```
   Without a PC the user starts it from the Shizuku app (paired via Wireless debugging). It dies on
   every reboot and on every adbd restart.

6. Check the result with `adb shell sh /data/local/tmp/flipctl.sh doctor` (prints nothing when fine)
   and, for app changes, a short run through the `device-test` skill.
