# flip-ctl

Use the cover screen (Flex Window) of a Samsung Galaxy Z Flip on demand: on its own, or
together with the main screen. No root, no PC needed once it is set up.

It was built for a Flip whose inner screen was replaced with one that can no longer fold. The
hinge then always reports "open", so the phone never switches to the cover screen. It also works
on an intact Flip, for example to keep the cover screen on while the phone is open.

> [!WARNING]
> Experimental. It overrides a system state Android normally takes from the hinge sensor, and it
> has been tested on **one phone** so far (below). If anything ever goes wrong, hold
> **Side key + Volume down for about 7 seconds**: the phone restarts on the main screen, because
> nothing this project changes survives a restart. Use at your own risk.

## What you get

A small app, **Screens**, with three buttons:

| Button | What happens |
|---|---|
| Main screen | Normal: only the main screen. |
| Cover screen | The phone acts as if it were closed: main screen off, Samsung's normal Flex Window on the cover screen (clock, widgets, apps you allowed there). |
| Both screens | Both screens on. The main screen works as usual; the cover screen becomes a second, touch-enabled screen. |

- Open it from the app drawer or from its Quick Settings tile (the tile shows the current mode).
- In *Cover screen* and *Both screens* mode, a small blue **floating button** stays on the cover
  screen, even on the lock screen. Tap it to get the three buttons there, so you can always get
  back to the main screen. Drag it up or down if it is in the way.
- *Both screens* comes back by itself each time you wake and unlock the phone (Samsung switches
  it off whenever the screen turns off).

## Tested phones

| Phone | Software | Result |
|---|---|---|
| Galaxy Z Flip 5 (SM-F731B) | Android 16, One UI 8.0 | Everything above works |

Other Flips may well work: the app looks up the system states by name instead of hard-coding
them, and shows a warning on untested models. Reports from other models are very welcome.

## Install

You need about 10 minutes, Wi-Fi, and nothing else.

1. **Turn on Developer options**: Settings → About phone → Software information → tap *Build
   number* seven times.
2. **Settings → Developer options → Default USB configuration → No data transfer.**
   Important: with *File transfer* here, Android restarts its debugging service every time the
   phone locks while unplugged, which stops Shizuku (next step), and with it the way back from
   the cover screen. Screens warns you if this is set wrong.
3. **Install [Shizuku](https://github.com/RikkaApps/Shizuku)** (Play Store or its GitHub
   release). Screens needs it: switching screens requires a permission that ordinary apps can't
   get, but Android's debugging ("shell") user has. Shizuku runs as that user and passes on only
   what you approve.
4. **Pair Shizuku with the phone, once.** Connect to any Wi-Fi network, open Shizuku and tap
   *Pairing*. Then Developer options → tap the *words* "Wireless debugging" (not only the switch)
   → *Pair device with pairing code*. Type the 6-digit code into Shizuku's notification. The
   phone pairs with itself; no computer is involved.
5. **Start Shizuku**: Shizuku → *Start*. You can turn Wi-Fi off again afterwards.
6. **Install Screens**: download `Screens.apk` from the
   [Releases page](https://github.com/rakinrkz/flip-ctl/releases) on the phone and open it
   (allow installing apps from your browser when asked).
7. **Open Screens** and tap *Tap here to allow Screens in Shizuku* once.

**After every phone restart**, Android forgets Shizuku's rights on purpose: unlock with your PIN,
turn on Wi-Fi, open Shizuku and tap *Start*. Until then Screens shows "Shizuku isn't running".

## Known issues

- **Don't use the Camera's *Dual preview* while *Both screens* is on.** Both drive the cover
  screen, and mixing them once left the main screen black (backlight on, nothing drawn) until a
  restart. Switch to *Main screen* first.
- Samsung's cover screen hides notification buttons, so the "Cover screen only" notification is
  not a way back on its own; use the floating button.
- *Both screens* has no app launcher on the cover screen (Samsung's own launcher refuses to run
  there). In *Cover screen* mode, Samsung's Good Lock → MultiStar launcher widget works as usual.
- Battery Saver or overheating can make Samsung switch *Both screens* off.

## How it works

Android decides which screen is active from a **device state** that normally comes from the
hinge sensor. On the Flip 5 these are `CLOSED` (0), `TENT` (1), `HALF_OPENED` (2), `OPENED` (3)
and `CONCURRENT_INNER_DEFAULT` (4, both screens). The shell user may override that state with
`cmd device_state state <id>`, and that is all this project does: *Cover screen* requests
`CLOSED`, *Both screens* requests `CONCURRENT_INNER_DEFAULT`, and *Main screen* clears the
override. The override lives in memory only.

The logic is one POSIX shell script, [`flipctl.sh`](flipctl.sh), which runs on the phone as the
shell user. The app ships a copy and runs it through Shizuku.

## Command line (with a PC)

With USB debugging on and the phone plugged in, [`flip-ctl`](flip-ctl) runs the same script over
adb:

```sh
./flip-ctl status            # model, current state, supported states, displays
./flip-ctl doctor            # warnings about known problems, if any
./flip-ctl dual              # both screens on
./flip-ctl dual --keep com.sec.android.app.clockpackage   # ...turn it back on after every unlock,
                                                          #    with Clock on the cover screen
./flip-ctl launch com.google.android.youtube              # open an app on the cover screen
./flip-ctl cover             # cover screen only
./flip-ctl try cover 30      # cover screen for 30 s, then back by itself (even if unplugged)
./flip-ctl main              # back to normal; also stops --keep
```

`./flip-ctl help` lists everything. Find package names with
`adb shell pm list packages | grep -i <name>`. Without the wrapper:
`adb push flipctl.sh /data/local/tmp/ && adb shell sh /data/local/tmp/flipctl.sh <command>`.

## Building the app

Linux x86_64, no Android Studio or Gradle needed:

```sh
app/setup-toolchain.sh   # once: JDK 17, Android build-tools 35, Shizuku API (~1 GB)
app/build.sh             # → app/build/Screens.apk
```

`build.sh` signs with a throwaway debug key. To publish, sign with your own release key and keep
it backed up (losing it means nobody can update to your next version):

```sh
keytool -genkeypair -keystore release.jks -alias release -keyalg RSA -keysize 4096 -validity 10000
FLIPCTL_KEYSTORE=release.jks FLIPCTL_KEY_ALIAS=release FLIPCTL_KS_PASS='...' app/build.sh
```

## Recovery

- Restart the phone (Side key + Volume down, about 7 seconds). This always works.
- From a PC: `./flip-ctl main`, or without this repo:

  ```sh
  adb shell "pkill -l KILL -f '[f]lipctl.sh'; cmd device_state state reset; cmd device_state base-state reset"
  ```

## Credits

- [Shizuku](https://github.com/RikkaApps/Shizuku) by RikkaApps, which makes shell access
  possible without root.
- Earlier projects that use Samsung's device states for the cover screen:
  [coverscreen-mirror](https://github.com/FoxxoOwO/coverscreen-mirror),
  [flip8-flexwindow-shizuku](https://github.com/nicolasTdC/flip8-flexwindow-shizuku) and
  [duo-fold-live](https://github.com/joeconsorti/duo-fold-live).

Samsung, Galaxy and Galaxy Z Flip are trademarks of Samsung Electronics. This project is not
affiliated with or endorsed by Samsung.

## License

[MIT](LICENSE). The app bundles the Shizuku API, also MIT; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
