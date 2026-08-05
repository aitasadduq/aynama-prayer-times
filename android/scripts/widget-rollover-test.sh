#!/usr/bin/env bash
# Drives the widget across real prayer boundaries on an emulator and asserts the
# rollover chain keeps advancing.
#
# The clock jump is what makes this deterministic — no waiting for a real prayer
# time. `cmd alarm set-time` needs a writable system clock, so this is emulator
# only; a retail phone will refuse it.
#
# Note on verification: the widget countdown is a Chronometer, which keeps the
# launcher window permanently non-idle, so `uiautomator dump` fails with
# "could not get idle state" and cannot be used to read the rendered text. The
# machine-checkable assertions here are therefore alarm- and logcat-level; a
# screenshot is saved per rollover in $OUT for the countdown to be eyeballed.
#
# Usage: scripts/widget-rollover-test.sh [emulator-serial] [rollovers]
set -uo pipefail

SERIAL="${1:-$(adb devices | awk '/^emulator-/ {print $1; exit}')}"
ROLLOVERS="${2:-4}"
SETTLE_SECS="${SETTLE_SECS:-60}"
OUT="${OUT:-/tmp/widget-rollover}"
PKG=com.aynama.prayertimes

[ -n "$SERIAL" ] || { echo "no emulator found; start one first"; exit 1; }
case "$SERIAL" in emulator-*) ;; *) echo "refusing to set the clock on $SERIAL (emulator only)"; exit 1 ;; esac

adb() { command adb -s "$SERIAL" "$@"; }
mkdir -p "$OUT"

# Every pending widget rollover alarm, as epoch-ms, earliest first.
pending_alarms() {
  adb shell dumpsys alarm \
    | grep -B1 'tag=.*PRAYER_WIDGET_UPDATE' \
    | grep -o 'origWhen [0-9]*' | awk '{print $2}' | sort -n
}

now_ms() { echo $(( $(adb shell date +%s) * 1000 )); }

restore_clock() {
  echo "restoring automatic time"
  adb shell settings put global auto_time 1 >/dev/null 2>&1
}
trap restore_clock EXIT

adb shell settings put global auto_time 0 >/dev/null
adb shell media volume --stream 3 --set 0 >/dev/null 2>&1
adb shell input keyevent KEYCODE_HOME >/dev/null
sleep 2

failures=0
for i in $(seq 1 "$ROLLOVERS"); do
  target="$(pending_alarms | head -1)"
  if [ -z "$target" ]; then echo "FAIL: no widget rollover alarm armed"; exit 1; fi

  echo
  echo "--- rollover $i: jumping to 5s before $(date -r $((target / 1000)) '+%F %T') ---"
  adb logcat -c >/dev/null 2>&1
  adb shell cmd alarm set-time $((target - 5000)) >/dev/null

  # Wait until the alarm we targeted is no longer pending, i.e. it fired.
  fired=0
  deadline=$((SECONDS + SETTLE_SECS))
  while [ $SECONDS -lt $deadline ]; do
    sleep 3
    if ! pending_alarms | grep -qx "$target"; then fired=1; break; fi
  done

  pushed=$(adb logcat -d 2>/dev/null | grep -c "notify widget update for package $PKG")
  next="$(pending_alarms | head -1)"
  n="$(now_ms)"
  adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1
  sleep 1
  adb exec-out screencap -p > "$OUT/rollover-$i.png"

  if [ "$fired" -ne 1 ]; then echo "FAIL: alarm never fired"; failures=$((failures + 1))
  else echo "ok: alarm fired"; fi
  if [ "$pushed" -lt 1 ]; then echo "FAIL: no widget update pushed to the host"; failures=$((failures + 1))
  else echo "ok: $pushed widget update(s) pushed"; fi
  if [ -z "$next" ] || [ "$next" -le "$n" ]; then echo "FAIL: no future rollover re-armed"; failures=$((failures + 1))
  else echo "ok: next rollover armed for $(date -r $((next / 1000)) '+%F %T')"; fi
  echo "   screenshot: $OUT/rollover-$i.png"
done

echo
if [ "$failures" -eq 0 ]; then
  echo "PASS: $ROLLOVERS rollovers fired, pushed and re-armed. Check $OUT/*.png for the countdowns."
else
  echo "FAILURES: $failures"; exit 1
fi
