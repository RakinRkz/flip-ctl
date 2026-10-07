# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Switches a Samsung Galaxy Z Flip between the main screen, the cover screen, or both, without root,
by overriding Android's device state (`cmd device_state state <id>`) as the shell user (uid 2000).
Tested only on a Galaxy Z Flip 5 (SM-F731B), Android 16 / One UI 8.0, whose states are
0 `CLOSED` (cover only), 1 `TENT`, 2 `HALF_OPENED`, 3 `OPENED` (normal), 4 `CONCURRENT_INNER_DEFAULT`
(both screens); the cover screen is logical display 1. Overrides live in memory: `main` or a reboot
always restores normal.

## Commands

```sh
sh -n flipctl.sh            # the only static check; there is no test suite
./flip-ctl <command>        # push flipctl.sh to /data/local/tmp and run it over adb
                            # (status, doctor, mode, cover, dual [--keep] [pkg], main, try, launch, set)
app/setup-toolchain.sh      # once: JDK 17, build-tools/platform 35, Shizuku API jars
                            #   into $FLIPCTL_TOOLCHAIN (default ~/.local/share/flip-ctl-toolchain)
app/build.sh                # -> app/build/Screens.apk, debug-signed unless FLIPCTL_KEYSTORE,
                            #   FLIPCTL_KEY_ALIAS and FLIPCTL_KS_PASS are set
```

Everything is verified on a real phone over adb. Use the project skills: `device-test` (live tests
without stranding the user), `install-app`, `phone-rescue` (stuck or black screens), `release`.

## Architecture

**`flipctl.sh` is the only implementation.** It runs on the phone as the shell user, either pushed
by the `flip-ctl` wrapper or bundled into the app (`build.sh` copies it into the APK's assets).
State ids are found by name from `cmd device_state print-states` (`CLOSED`/`FOLDED` → cover,
`CONCURRENT_INNER_DEFAULT` → dual), never hard-coded. Changing the script means rebuilding the app
to ship it there; the app and `flip-ctl` each overwrite `/data/local/tmp/flipctl.sh` with their copy.

**Background jobs** are detached shells (`nohup setsid sh "$0" <job>`) that survive adb/USB loss:
- `revert-after` (from `try`): resets the state after N seconds if it is unchanged.
- `keep-loop` (from `dual --keep`): Samsung drops dual whenever the screen turns off; the loop
  re-requests it (and reopens a component on display 1) after each wake + unlock, and stands back
  while the camera is in front.
Both record pids in `/data/local/tmp/flipctl.{keep,revert}.pid`. Every explicit mode command first
runs `stop_jobs`, which SIGKILLs each job's whole process group (sh defers SIGTERM during `sleep`,
and a killed loop's forked `cmd device_state` would otherwise re-apply its state).

**The Screens app** (`app/src/io/github/rakinrkz/flipctl/`) is a thin UI over the script:
- `FlipShell` runs the bundled script through Shizuku (`Shizuku.newProcess`, private since API 13,
  called by reflection), writing it via a per-call temp file + rename. `switchTo` is the single
  entry point for mode changes: it grants `SYSTEM_ALERT_WINDOW` via `appops` and **refuses cover
  mode if the overlay can't be shown**, because the floating button is the way back.
- `MainActivity` is the three-button panel; `CoverActivity` subclasses it (own task affinity,
  `singleTask`, `setShowWhenLocked`) and is what `cover <component>` and `keep-loop` launch on
  display 1.
- `ModeNotification` starts/stops `CoverButtonService`, a foreground service that owns the ongoing
  notification and draws the draggable overlay button on display 1. `MainActivity.refresh()`
  re-syncs it with the real mode, since the mode can change outside the app.
- `SwitchReceiver` (notification actions) and `ScreensTile` run in processes that may have just
  started, so they `awaitReady()` for Shizuku's binder, which arrives asynchronously.

**No Gradle.** `build.sh` drives aapt2, `javac --release 8` (android.jar on the classpath because it
lacks `LambdaMetafactory`), d8 and apksigner. The Shizuku provider's manifest entries are merged by
hand into `app/AndroidManifest.xml`. The APK must also carry `LICENSE` and
`THIRD_PARTY_NOTICES.md` (Shizuku API is MIT), which `build.sh` copies into its assets.

## Constraints that aren't obvious

- `flipctl.sh` must stay POSIX sh for Android's mksh + toybox: no awk; grep has no `\s` (use
  `[[:space:]]`); use `ps -o ARGS= -p PID`; toybox `pkill -9` kills the calling shell whatever the
  pattern (use `pkill -l KILL` or `kill -9 PID`); `pkill -f` patterns need the `[x]yz` trick so they
  don't match their own command line.
- Samsung's cover screen hides notification action buttons, so a notification is never a way back
  from cover mode; the overlay button and the panel on display 1 are.
- If Developer options → Default USB configuration isn't "No data transfer", every lock while
  unplugged switches USB functions, restarts adbd and kills Shizuku and all adb-started processes
  (`dumpsys usb` then lists `screen_unlocked_functions=`). `doctor` warns about it.
- Camera's Dual preview and dual mode both drive the cover screen; mixing them (plus an app
  reinstall mid-transition) once left the main display composing nothing until a reboot.
- After a reboot the phone is in before-first-unlock state: the app's activities "do not exist"
  until a PIN unlock, and Shizuku must be started again.

## Releasing

The signing key lives outside the repository and is never committed (`.gitignore` blocks
`*.jks`/`*.keystore`). Releases are signed with the certificate whose SHA-256 is
`6ad2516c2ca2b42b89990af821bff07c382193b1d3c14982ab8274ea02f63499`; an APK signed with any other key
can't update existing installs. See the `release` skill.
