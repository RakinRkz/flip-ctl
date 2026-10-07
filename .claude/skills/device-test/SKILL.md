---
name: device-test
description: Safely live-test flipctl.sh or the Screens app on a connected Galaxy Z Flip over adb — escape timer before cover mode, real-unlock waits, screenshots per display, log-based verification and cleanup. Use whenever a change has to be tried on the phone.
---

# Live-testing on the phone

A wrong step here leaves a real person's phone stuck on the 3.4" cover screen or with a black main
screen. Follow every step.

## 1. Preconditions

```sh
adb devices -l                                 # confirm model/serial is the intended Flip, not another
                                               # phone; with several attached, export ANDROID_SERIAL
adb shell cmd device_state print-states        # a Flip lists CLOSED/OPENED/CONCURRENT_*; a single
                                               # "DEFAULT" state means a non-foldable phone: stop
adb shell cmd device_state print-state         # normal (3 on the Flip 5) before you start
adb shell pidof shizuku_server                 # needed for anything that goes through the app
adb shell 'ps -A -o ARGS | grep -E "[k]eep-loop|[r]evert-after"'   # no leftover jobs
adb shell dumpsys window policy | grep -E 'interactiveState|^ +showing='
```

Awake and unlocked means `interactiveState=INTERACTIVE_STATE_AWAKE` and `showing=false`. Wait loops
for an unlock must **abort on timeout**, never carry on: after a reboot the phone is in
before-first-unlock state and the app's activities "do not exist" until the user enters their PIN.

## 2. Before any switch to cover mode (`CLOSED`), arm an escape the code under test can't cancel

`stop_jobs` kills flipctl's own `try` timers, so use a separate one:

```sh
adb shell 'nohup setsid sh -c "sleep 180; [ \$(cmd device_state print-state) = 0 ] && cmd device_state state reset" >/dev/null 2>&1 & echo $! > /data/local/tmp/escape.pid'
# afterwards (it is a group leader, so kill the group):
adb shell 'kill -9 -- -$(cat /data/local/tmp/escape.pid); rm -f /data/local/tmp/escape.pid'
```

Cover mode makes the phone behave as folded: it locks, and USB may drop. Keep the escape short
enough that the user isn't stranded, long enough for the test.

## 3. Never disturb a running mode

Don't reinstall, force-stop or kill the app while cover or dual mode is active or while the user is
mid-test. A reinstall during a display switch once left the main display composing nothing until a
reboot. Switch to `main` first.

## 4. Observe

- Physical display ids (needed for screenshots): `adb shell dumpsys SurfaceFlinger --display-id`.
  Screenshot one display: `adb exec-out screencap -p -d <physical-id> > shot.png`.
- Secure windows (the lock screen) capture as solid black. Don't diagnose a "black screen" from a
  screenshot while `showing=true`; check the active display's "HWC layers" in
  `adb shell dumpsys SurfaceFlinger` instead.
- UI elements: `adb shell uiautomator dump --display <logical-id> /sdcard/Download/flipctl-ui.xml`
  (logical id 0 = main, 1 = cover). Overlay windows such as the floating button are **not** in the
  dump; tap them by position. Taps: `adb shell input -d <logical-id> tap X Y`. Delete dump files
  afterwards.
- What happened: `adb shell logcat -d -v time | grep -E "Committing state|START u0.*flipctl"`.
  A START "from uid 2000" came from the shell (script); from the app's uid it came from the app.
- Don't read other apps' notifications from `dumpsys notification`; filter to this package.

## 5. Let the user drive when they are holding the phone

Scripted taps and finger touches collide (a real touch outside the dialog closes the panel). If the
user is handling the phone, give them short steps and verify from the logs instead.

## 6. Clean up and report

State back to normal, no flipctl jobs, escape timer killed, dump files removed. Report what was
verified on the device versus only reasoned about.
