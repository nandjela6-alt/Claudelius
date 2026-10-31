#!/usr/bin/env bash
# Undo everything this project changed, newest change first, then cross-check against the
# original backup. Safe to run more than once.
#
#   TV=192.168.x.y ./restore.sh               full restore (also uninstalls TV Home)
#   TV=192.168.x.y ./restore.sh --keep-launcher   re-enable apps + settings, keep TV Home installed
source "$(dirname "$0")/lib.sh"
KEEP_LAUNCHER=no; [ "${1:-}" = --keep-launcher ] && KEEP_LAUNCHER=yes
connect
BACKUP="$(original_backup)"
[ -n "$BACKUP" ] || { echo "No backup in $STATE/backup. Was 01-backup.sh run from this folder?" >&2; exit 1; }

orig_home="$(cat "$BACKUP/home-activity.txt")"

# 1) Replay the change log in reverse.
if [ -f "$CHANGES" ]; then
  tail -n +2 "$CHANGES" | tail -r 2>/dev/null > "$STATE/.rev" || tail -n +2 "$CHANGES" | tac > "$STATE/.rev"
  while IFS=$'\t' read -r _ kind target old _new; do
    case "$kind" in
      pkg-disable)
        tsh pm enable --user 0 "$target" >/dev/null 2>&1 || tsh pm enable "$target" >/dev/null
        echo "enabled   $target" ;;
      setting)
        ns="${target%%:*}"; key="${target#*:}"
        if [ "$old" = null ]; then tsh settings delete "$ns" "$key" >/dev/null; else tsh settings put "$ns" "$key" "$old"; fi
        echo "setting   $ns $key = $old" ;;
      home)
        tsh cmd package set-home-activity --user 0 "$old" >/dev/null && echo "home      $old" ;;
      install)
        if [ "$KEEP_LAUNCHER" = no ]; then a uninstall "$target" >/dev/null && echo "removed   $target (TV Home)"; fi ;;
    esac
  done < "$STATE/.rev"
  rm -f "$STATE/.rev"
  mv "$CHANGES" "$STATE/changes.restored-$(date +%Y%m%d-%H%M%S).tsv"
else
  echo "No change log found; checking against the backup only."
fi

# 2) Safety net: anything disabled now that was enabled in the backup gets re-enabled,
#    even if the change was made outside these scripts.
tsh pm list packages -d | sort > "$STATE/.disabled-now"
comm -13 "$BACKUP/packages-disabled.txt" "$STATE/.disabled-now" | sed 's/^package://' | while read -r p; do
  [ -n "$p" ] || continue
  tsh pm enable --user 0 "$p" >/dev/null 2>&1 || tsh pm enable "$p" >/dev/null
  echo "enabled   $p (safety net)"
done
rm -f "$STATE/.disabled-now"

# 2b) Settings this project can change (tune.sh): put back the values from the original backup.
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  orig="$(grep "^$k=" "$BACKUP/settings-global.txt" | cut -d= -f2- || true)"
  now="$(tsh settings get global "$k")"
  if [ -z "$orig" ] && [ "$now" != null ]; then tsh settings delete global "$k" >/dev/null; echo "setting   global $k removed (safety net)"
  elif [ -n "$orig" ] && [ "$now" != "$orig" ]; then tsh settings put global "$k" "$orig"; echo "setting   global $k = $orig (safety net)"; fi
done

# 2c) TV Home was not on the TV originally: remove it (unless --keep-launcher).
if [ "$KEEP_LAUNCHER" = no ] && pkg_installed "$LAUNCHER_PKG" \
   && ! grep -q "package:.*=$LAUNCHER_PKG\b\|package:$LAUNCHER_PKG\b" "$BACKUP/packages-all.txt"; then
  a uninstall "$LAUNCHER_PKG" >/dev/null && echo "removed   $LAUNCHER_PKG (TV Home, safety net)"
fi

# 3) Make sure the original home screen is the default again.
#    Done unconditionally: the stored preference can differ from what resolve-activity reports.
if [ "$KEEP_LAUNCHER" = no ] && [ -n "$orig_home" ]; then
  tsh cmd package set-home-activity --user 0 "$orig_home" >/dev/null || true
fi
home
echo "home is now: $(current_home)  (originally: $orig_home)"
f="$(shot after-restore)"; echo "screenshot: $f"
echo "Compare with the original: $BACKUP/home-before.png"
