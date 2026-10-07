#!/system/bin/sh
# flipctl.sh: choose which Galaxy Z Flip screen is on by overriding Android's
# device state, the value the hinge sensor normally drives.
#
# Runs on the phone as the shell user, either from a PC:
#   adb shell sh /data/local/tmp/flipctl.sh <command>
# or on the phone itself through Shizuku's rish:
#   rish -c 'sh /sdcard/Download/flipctl.sh <command>'
#
# Overrides live in memory only: `main` or a reboot always restores normal behaviour.

COVER_DISPLAY=${FLIPCTL_COVER_DISPLAY:-1}
KEEP_PIDFILE=/data/local/tmp/flipctl.keep.pid
REVERT_PIDFILE=/data/local/tmp/flipctl.revert.pid

usage() {
    cat <<'EOF'
usage: flipctl.sh <command> [args]

  status              model, current/base/override state, supported states, displays
  mode [--doctor]     print the current mode: main, cover, dual or other
                      --doctor also prints doctor's warnings after it
  doctor              print a "warn: ..." line for each known problem (nothing if all is well)
  cover [--base] [pkg]
                      cover screen only: the system believes the phone is folded
                      --base fakes the hinge reading itself instead of overriding the state
                      pkg opens that app on the cover screen
  dual [--keep] [pkg] both screens on: main stays primary, cover becomes display 1
                      pkg opens that app on the cover screen
                      --keep turns dual back on (and reopens pkg) after every
                      wake + unlock, since Samsung drops it when the screen goes off
  rear                rear-display mode, if this firmware has one
  main                drop all overrides and stop --keep; back to normal (alias: reset)
  set <id|NAME>       request any supported state by id or name
  launch <pkg|pkg/activity>
                      open an app on the cover display (use after `dual`)
  try <mode> [secs]   apply cover, dual or rear, then revert after secs (default 15)
EOF
}

die() {
    echo "flipctl: $*" >&2
    exit 1
}

# One "<id> <NAME>" line per supported device state.
list_states() {
    cmd device_state print-states 2>&1 | tr '}' '\n' |
        sed -n "s/.*identifier=\([0-9][0-9]*\), name='\([^']*\)'.*/\1 \2/p"
}

state_name() {
    list_states | grep "^$1 " | cut -d ' ' -f 2
}

current_state() {
    cmd device_state print-state 2>/dev/null
}

# Prints the id of the first supported state whose name fully matches one of the
# extended regexes, trying them in order.
find_state() {
    states=$(list_states)
    for pattern in "$@"; do
        id=$(echo "$states" | grep -E "^[0-9]+ ($pattern)\$" | head -n 1 | cut -d ' ' -f 1)
        if [ -n "$id" ]; then
            echo "$id"
            return 0
        fi
    done
    return 1
}

folded_state() {
    find_state 'FOLDED|CLOSED' && return 0
    # Unfamiliar naming: anything folded or closed that isn't a half-open posture or a display mode.
    list_states | grep -E 'FOLD|CLOS' | grep -vE 'HALF|UNFOLD|OPEN|CONCURRENT|REAR|DUAL' |
        head -n 1 | cut -d ' ' -f 1 | grep .
}

mode_state() {
    case $1 in
        cover) folded_state || die "no folded/closed state on this firmware; run: status" ;;
        dual) find_state CONCURRENT_INNER_DEFAULT 'CONCURRENT[A-Z_]*INNER[A-Z_]*' ||
            die "no concurrent (dual-screen) state on this firmware; run: status" ;;
        rear) find_state REAR_DISPLAY 'REAR[A-Z_]*' ||
            die "no rear-display state on this firmware; run: status" ;;
        *) die "unknown mode '$1' (expected cover, dual or rear)" ;;
    esac
}

# request_state ID [base]: request the override, then report what the system committed.
request_state() {
    sub=state
    [ "$2" = base ] && sub=base-state
    out=$(cmd device_state "$sub" "$1" 2>&1)
    case $? in 0) ;; *) die "device_state refused '$sub $1': $out" ;; esac
    case $out in *Error*|*rror:*|*xception*) die "device_state refused '$sub $1': $out" ;; esac
    sleep 1
    now=$(current_state)
    if [ "$now" = "$1" ]; then
        echo "now in state $1 ($(state_name "$1"))"
    else
        echo "flipctl: asked for state $1 but the system is in state ${now:-?}." >&2
        echo "  Samsung may have refused it (battery saver, heat, lock screen or firmware policy)." >&2
        return 1
    fi
}

reset_states() {
    cmd device_state state reset >/dev/null 2>&1
    # base-state only exists on Android 14+; harmless to try on older builds.
    cmd device_state base-state reset >/dev/null 2>&1
    sleep 1
    now=$(current_state)
    echo "overrides cleared; state ${now:-?} ($(state_name "$now"))"
}

current_mode() {
    case $(state_name "$(current_state)") in
        CONCURRENT*) echo dual ;;
        FOLDED | CLOSED) echo cover ;;
        *) if cmd device_state state | grep -q 'Override state'; then echo other; else echo main; fi ;;
    esac
}

doctor() {
    folded_state >/dev/null || echo "warn: this phone has no folded state, so Cover screen can't work"
    find_state CONCURRENT_INNER_DEFAULT 'CONCURRENT[A-Z_]*INNER[A-Z_]*' >/dev/null ||
        echo "warn: this phone has no dual-screen state, so Both screens can't work"
    model=$(getprop ro.product.model)
    case $model in
        SM-F731*) ;;
        *) echo "warn: untested on $model (only the Galaxy Z Flip 5, SM-F731, has been tested)" ;;
    esac
    # Only listed when not "No data transfer"; then every lock while unplugged switches USB
    # modes, restarts adbd and stops Shizuku.
    dumpsys usb | grep -q 'screen_unlocked_functions=' &&
        echo "warn: set Developer options > Default USB configuration to No data transfer, or Shizuku stops whenever the phone locks"
    return 0
}

show_displays() {
    displays=$(dumpsys display | grep 'mBaseDisplayInfo=' |
        sed -n 's/.*DisplayInfo{"\([^"]*\)", displayId \([0-9]*\).*, state \([A-Z_]*\).*/  display \2: \1, \3/p' |
        sort -u)
    if [ -n "$displays" ]; then
        echo "$displays"
    else
        echo "  (could not parse; run: dumpsys display)"
    fi
}

status() {
    echo "model:  $(getprop ro.product.model), Android $(getprop ro.build.version.release), One UI build $(getprop ro.build.version.oneui)"
    cmd device_state state
    echo "supported states:"
    list_states | sed 's/^/  /'
    echo "displays:"
    show_displays
}

launch() {
    [ -n "$1" ] || die "usage: launch <package|package/activity>"
    case $1 in
        */*) component=$1 ;;
        *) component=$(cmd package resolve-activity --brief \
            -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$1" | tail -n 1) ;;
    esac
    case $component in
        */*) ;;
        *) die "no launchable activity in package '$1'" ;;
    esac
    am start --display "$COVER_DISPLAY" -n "$component"
}

try_mode() {
    id=$(mode_state "$1") || exit 1
    secs=${2:-15}
    # The revert runs detached on the phone so it still fires if USB or the
    # adb/rish session drops while the main screen is off.
    nohup setsid sh "$0" revert-after "$secs" "$id" >/dev/null 2>&1 &
    if ! request_state "$id"; then
        stop_jobs
        exit 1
    fi
    echo "reverting in ${secs}s, even if this session disconnects"
}

# revert-after SECS ID: clear overrides after SECS unless the state was changed meanwhile.
revert_after() {
    echo $$ >"$REVERT_PIDFILE"
    sleep "$1"
    [ "$(current_state)" = "$2" ] && reset_states
    rm -f "$REVERT_PIDFILE"
}

# job_pid PIDFILE ROLE: the recorded pid, if that process is still a flipctl ROLE job.
job_pid() {
    pid=$(cat "$1" 2>/dev/null)
    [ -n "$pid" ] && ps -o ARGS= -p "$pid" 2>/dev/null | grep -q "$2" && echo "$pid"
}

# Stops a running `dual --keep` loop and a pending `try` revert. Each was started with setsid,
# so its pid is also its process group: killing the group takes along a `cmd device_state`
# it may have just forked, which would otherwise re-apply its state after `main`. SIGKILL,
# because sh defers SIGTERM until its current `sleep` ends.
stop_jobs() {
    pid=$(job_pid "$KEEP_PIDFILE" keep-loop) && kill -9 -- "-$pid"
    pid=$(job_pid "$REVERT_PIDFILE" revert-after) && kill -9 -- "-$pid"
    rm -f "$KEEP_PIDFILE" "$REVERT_PIDFILE"
}

camera_in_front() {
    dumpsys activity activities | grep topResumedActivity | grep -qE 'com\.(sec|samsung)\.android\.(app\.)?camera'
}

# keep-loop ID [PKG]: whenever the phone is awake, unlocked and has no override
# (Samsung cancels dual mode on screen off), request ID again and reopen PKG.
# Exits once the pidfile no longer names this process.
keep_loop() {
    echo $$ >"$KEEP_PIDFILE"
    while [ "$(cat "$KEEP_PIDFILE" 2>/dev/null)" = "$$" ]; do
        # Stand back while the camera is open: its Dual preview drives the cover screen itself,
        # and fighting it once left the main display composing nothing until a reboot.
        if ! cmd device_state state | grep -q 'Override state' && ! camera_in_front; then
            policy=$(dumpsys window policy)
            if echo "$policy" | grep -q 'interactiveState=INTERACTIVE_STATE_AWAKE' &&
                ! echo "$policy" | grep -qE '^[[:space:]]+showing=true[[:space:]]*$'; then
                cmd device_state state "$1" >/dev/null 2>&1
                sleep 1
                if [ "$(current_state)" = "$1" ]; then
                    [ -n "$2" ] && launch "$2" >/dev/null 2>&1
                else
                    sleep 10 # refused (battery saver, heat): back off
                fi
            fi
        fi
        sleep 2
    done
}

action=${1:-status}
[ $# -gt 0 ] && shift
# Any explicit mode change replaces a running `dual --keep` and cancels a pending `try` revert.
case $action in
    cover | dual | rear | main | reset | set | try) stop_jobs ;;
esac
case $action in
    status)
        status
        if pid=$(job_pid "$KEEP_PIDFILE" keep-loop); then echo "dual --keep: running (pid $pid)"; fi
        if pid=$(job_pid "$REVERT_PIDFILE" revert-after); then echo "try: revert pending (pid $pid)"; fi
        ;;
    mode)
        current_mode
        [ "$1" = --doctor ] && doctor
        ;;
    doctor) doctor ;;
    cover)
        id=$(mode_state cover) || exit 1
        if [ "$1" = --base ]; then
            shift
            request_state "$id" base || exit 1
        else
            request_state "$id" || exit 1
        fi
        if [ -n "$1" ]; then launch "$1"; fi
        ;;
    dual)
        keep=
        if [ "$1" = --keep ]; then
            keep=1
            shift
        fi
        id=$(mode_state dual) || exit 1
        request_state "$id" || exit 1
        [ -n "$1" ] && launch "$1"
        if [ -n "$keep" ]; then
            nohup setsid sh "$0" keep-loop "$id" "$1" >/dev/null 2>&1 &
            echo "keeping dual mode across sleep/unlock until: main"
        fi
        ;;
    rear)
        id=$(mode_state rear) || exit 1
        request_state "$id"
        ;;
    main | reset) reset_states ;;
    set)
        [ -n "$1" ] || die "usage: set <id|NAME>"
        case $1 in
            *[!0-9]*) id=$(find_state "$1") || die "no supported state named '$1'" ;;
            *) id=$1 ;;
        esac
        request_state "$id"
        ;;
    launch) launch "$1" ;;
    try) try_mode "$@" ;;
    revert-after) revert_after "$@" ;;
    keep-loop) keep_loop "$@" ;;
    help | -h | --help) usage ;;
    *)
        usage >&2
        exit 2
        ;;
esac
