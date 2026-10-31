#!/usr/bin/env bash
# Shared helpers. Usage: TV=192.168.x.y ./<script>.sh
# Everything this project changes on the TV is appended to state/changes.tsv so restore.sh
# can undo it; state/ also holds the backups and screenshots. Keep that folder.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$HERE")"
STATE="${STATE:-$ROOT/state}"
CHANGES="$STATE/changes.tsv"
SHOTS="$STATE/screenshots"
LAUNCHER_PKG="app.claudelius.tvhome"
LAUNCHER_HOME="$LAUNCHER_PKG/.HomeActivity"
mkdir -p "$STATE" "$SHOTS"

: "${TV:?Set TV to the IP address of the TV, e.g.  TV=192.168.178.42 $0}"
case "$TV" in *:*) SERIAL="$TV" ;; *) SERIAL="$TV:5555" ;; esac

command -v adb >/dev/null || { echo "adb not found. Install Android platform-tools first." >&2; exit 1; }

a()   { adb -s "$SERIAL" "$@" </dev/null; }
tsh() { adb -s "$SERIAL" shell "$@" </dev/null | tr -d '\r'; }  # </dev/null: adb shell would eat a while-read loop's input

connect() {
  adb connect "$SERIAL" >/dev/null || true
  local state; state="$(adb -s "$SERIAL" get-state 2>&1 || true)"
  case "$state" in
    device) ;;
    *unauthorized*) echo "The TV is asking to allow USB debugging: accept it on the TV (tick 'always allow') and re-run." >&2; exit 1 ;;
    *) echo "Cannot reach $SERIAL ($state). Is the TV on, on the same network, with network debugging enabled?" >&2; exit 1 ;;
  esac
}

# shot <name>  -> saves a PNG screenshot from the TV and prints its path
shot() {
  local f="$SHOTS/$(date +%Y%m%d-%H%M%S)-$1.png"
  sleep "${SHOT_DELAY:-2}"
  a exec-out screencap -p > "$f"
  echo "$f"
}

home() { tsh input keyevent KEYCODE_HOME >/dev/null; }

# log_change <kind> <target> <old> <new>
log_change() {
  [ -f "$CHANGES" ] || printf 'time\tkind\ttarget\told\tnew\n' > "$CHANGES"
  printf '%s\t%s\t%s\t%s\t%s\n' "$(date -u +%FT%TZ)" "$1" "$2" "$3" "$4" >> "$CHANGES"
}

current_home() {
  tsh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
}

# Folder of the first backup ever taken (the untouched state), or empty.
original_backup() {
  local b; b="$(cat "$STATE/backup/ORIGINAL" 2>/dev/null || true)"
  [ -n "$b" ] && [ -d "$b" ] && echo "$b"
  return 0
}

pkg_installed() { tsh pm list packages "$1" | grep -qx "package:$1"; }
pkg_disabled()  { tsh pm list packages -d "$1" | grep -qx "package:$1"; }

# ---- protection: packages that must never be disabled -----------------------------------
# Static list (debloat/protected.txt, "re:" lines are regexes) plus what we discover on this
# TV: the app that performs HDMI switching, the packages providing TV inputs, the keyboard.
_PROT=""; _PROT_RE=""
_discover_protected() {
  # app that handles "switch to HDMI" (passthrough channel VIEW intent)
  tsh cmd package resolve-activity --brief -a android.intent.action.VIEW \
    -d content://android.media.tv/passthrough/probe | tail -1 | cut -d/ -f1
  # packages that provide TV inputs (HDMI / tuner services)
  tsh dumpsys tv_input | grep -oE 'ComponentInfo[{][^/}]+' | cut -c15-
  # active keyboard
  tsh settings get secure default_input_method | cut -d/ -f1
  echo "$LAUNCHER_PKG"
}
protected_list() {
  if [ -z "$_PROT" ]; then
    local static dyn
    static="$(sed 's/#.*//; s/[[:space:]]*$//' "$ROOT/debloat/protected.txt" | grep -v '^$' || true)"
    _PROT_RE="$(grep '^re:' <<<"$static" | sed 's/^re://' | paste -sd'|' - || true)"
    dyn="$(_discover_protected)"
    _PROT="$(printf '%s\n%s\n' "$(grep -v '^re:' <<<"$static" || true)" "$dyn" \
      | grep -vE '^(android|null|)$' | grep -v 'No activity' | sort -u)"
  fi
  echo "$_PROT"
  echo "android"
  [ -n "$_PROT_RE" ] && echo "re:$_PROT_RE"
  return 0
}
is_protected() {
  protected_list >/dev/null
  [ "$1" = android ] && return 0
  grep -qxF "$1" <<<"$_PROT" && return 0
  [ -n "$_PROT_RE" ] && grep -qE "^(${_PROT_RE})$" <<<"$1" && return 0
  return 1
}
