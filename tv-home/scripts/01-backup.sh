#!/usr/bin/env bash
# Read-only snapshot of everything we might touch. Changes nothing on the TV.
# Note: without root, app data itself cannot be backed up over ADB on Android 11, which is why
# this project only ever *disables* apps (data stays on the TV) and never uninstalls them.
source "$(dirname "$0")/lib.sh"
connect
B="$STATE/backup/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$B"
tsh getprop                                  > "$B/getprop.txt"
tsh pm list packages -f -i -u                > "$B/packages-all.txt"
tsh pm list packages -e | sort               > "$B/packages-enabled.txt"
tsh pm list packages -d | sort               > "$B/packages-disabled.txt"
tsh pm list packages -s | sort               > "$B/packages-system.txt"
tsh pm list packages -3 | sort               > "$B/packages-thirdparty.txt"
current_home                                 > "$B/home-activity.txt"
tsh cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$B/home-candidates.txt"
for ns in global secure system; do tsh settings list "$ns" | sort > "$B/settings-$ns.txt"; done
tsh dumpsys tv_input                          > "$B/dumpsys-tv_input.txt" 2>&1 || true
tsh ime list -s                               > "$B/ime.txt"
home; shot before-home > "$B/screenshot-path.txt"
cp "$(cat "$B/screenshot-path.txt")" "$B/home-before.png"
# Plain text pointers instead of symlinks (symlinks don't work in Git Bash on Windows).
# ORIGINAL is written once: restore.sh always compares against the state before any change.
[ -f "$STATE/backup/ORIGINAL" ] || echo "$B" > "$STATE/backup/ORIGINAL"
echo "$B" > "$STATE/backup/LATEST"
echo "Backup written to $B"
wc -l "$B"/packages-*.txt
echo "Current home screen: $(cat "$B/home-activity.txt")"
