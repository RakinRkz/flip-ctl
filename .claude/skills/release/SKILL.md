---
name: release
description: Publish a new Screens version to GitHub Releases — bump the version, build with the release key, verify the signer, tag, upload and re-download to check. Only when the maintainer asks for a release.
disable-model-invocation: true
---

# Releasing Screens

Publishing is public and permanent. Confirm the version number and that the maintainer wants it out
before pushing anything.

1. **Version.** Raise `version_code` (integer, must increase) and `version_name` at the top of
   `app/build.sh`. Android refuses an update with the same or a lower code.

2. **Build with the release key.** The maintainer keeps the keystore and its password file outside
   the repository; ask where if you don't know. Never print or commit the password.
   ```sh
   FLIPCTL_KEYSTORE=<keystore> FLIPCTL_KEY_ALIAS=release FLIPCTL_KS_PASS="$(cat <password-file>)" app/build.sh
   ```
   The build must not print the "signed with a local debug key" note.

3. **Verify the signer.**
   ```sh
   "$FLIPCTL_TOOLCHAIN"/sdk/build-tools/35.0.0/apksigner verify --print-certs app/build/Screens.apk
   ```
   Signer SHA-256 must be `6ad2516c2ca2b42b89990af821bff07c382193b1d3c14982ab8274ea02f63499`
   (toolchain default `~/.local/share/flip-ctl-toolchain`; run apksigner with that toolchain's
   `jdk17` as `JAVA_HOME`). Any other key can't update existing installs: stop.

4. **Commit and tag** with the maintainer's own git identity. No AI or tool attribution in commit
   messages, tags or release notes.

5. **Publish.**
   ```sh
   sha256sum app/build/Screens.apk
   gh release create vX.Y app/build/Screens.apk --title "Screens X.Y" --notes-file <notes>
   ```
   Notes: what changed, tested phones, the restart escape (Side + Volume down about 7 s), and both
   SHA-256 values (APK and signing certificate).

6. **Check what users will get.** `gh release download vX.Y --dir <tmp>` and compare its SHA-256
   with the local build.
