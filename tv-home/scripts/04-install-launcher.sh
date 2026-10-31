#!/usr/bin/env bash
# Installs TV Home, pushes wallpapers, and makes it the default home screen.
# The stock launcher stays installed; restore.sh switches back to it.
source "$(dirname "$0")/lib.sh"
APK="${APK:-$ROOT/dist/tvhome.apk}"
WALL="${WALL:-$STATE/wallpapers}"
connect
[ -n "$(original_backup)" ] || { echo "No backup found. Run 01-backup.sh first." >&2; exit 1; }

was_installed=no; pkg_installed "$LAUNCHER_PKG" && was_installed=yes
a install -r "$APK"
[ "$was_installed" = yes ] || log_change install "$LAUNCHER_PKG" absent installed

# Start once so the app creates its own wallpaper folder with the right owner.
tsh am start -W -n "$LAUNCHER_HOME" >/dev/null
DEST="/sdcard/Android/data/$LAUNCHER_PKG/files/wallpapers"
if [ -d "$WALL" ] && ls "$WALL"/*.jpg >/dev/null 2>&1; then
  tsh mkdir -p "$DEST"
  a push "$WALL"/. "$DEST/" >/dev/null
  echo "pushed $(ls "$WALL"/*.jpg | wc -l | tr -d ' ') wallpapers"
else
  echo "no wallpapers in $WALL yet (run fetch-wallpapers.py); the gradient fallback is shown"
fi

old="$(current_home)"
if [ "$old" != "$LAUNCHER_HOME" ] && [ "$old" != "$LAUNCHER_PKG/$LAUNCHER_PKG.HomeActivity" ]; then
  tsh cmd package set-home-activity --user 0 "$LAUNCHER_HOME"
  log_change home default "$old" "$LAUNCHER_HOME"
fi
tsh am force-stop "$LAUNCHER_PKG"
home
echo "home is now: $(current_home)"
shot launcher-home
