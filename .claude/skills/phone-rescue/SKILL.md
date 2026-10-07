---
name: phone-rescue
description: Diagnose and recover a Galaxy Z Flip that is stuck on the cover screen, shows a black main screen, or where Screens says Shizuku isn't running. Use when the user reports the phone is stuck, blank, or the buttons stopped working.
---

# Getting a phone back to normal

Fix first, investigate second: the user can't use their phone until the main screen is back.

## Stuck on the cover screen

- Over adb: `./flip-ctl main`, or without the repo:
  `adb shell "pkill -l KILL -f '[f]lipctl.sh'; cmd device_state state reset; cmd device_state base-state reset"`
- Without a PC: the blue floating button on the cover → *Main screen*.
- Always works: hold Side key + Volume down about 7 s (reboot). Then the user must PIN-unlock and
  start Shizuku again.

If the phone isn't reachable (unplugged), tell the user those options and wait with
`adb wait-for-device` before resetting.

## Main screen black but backlit

Check before rebooting (the lock screen screenshots black anyway, so use the dumps):

```sh
adb shell dumpsys SurfaceFlinger | grep -A2 '(physical'     # layerFilter={layerStack=4294967295 ...} = unmapped
adb shell dumpsys SurfaceFlinger | sed -n '/(active) HWC layers:/,/(inactive)/p'   # no layers = nothing composed
adb shell dumpsys SurfaceFlinger | grep -c 'Transition Root' # stale transitions
```

Seen once after Camera Dual preview + dual mode + an app reinstall mid-switch. Cycling the device
state (`cmd device_state state 4`, then `state reset`) plus screen off/on re-mapped the layer stack,
but composition only came back after `adb reboot`. Say so before rebooting: it's the user's phone.

## "Shizuku isn't running" / buttons do nothing

```sh
adb shell pidof shizuku_server
adb shell dumpsys usb | grep screen_unlocked_functions   # any output = Default USB configuration isn't "No data transfer"
```

adbd restarts kill Shizuku and every adb-started process. The usual cause is that USB setting
(each lock while unplugged switches USB functions), also toggling USB debugging or a reboot. Fix the
setting in Developer options, then restart Shizuku (USB command in the `install-app` skill, or
Shizuku → Start over Wireless debugging).

## USB keeps dropping when switching to cover mode

Same USB-function switch on lock: the folded state locks the phone. Background jobs are detached and
carry on; wait with `adb wait-for-device`.

## Notes

- Samsung's cover screen hides notification buttons; a notification can't get the user back.
- In `dumpsys notification` look only at this package's records; other apps' history is private.
