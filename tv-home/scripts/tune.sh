#!/usr/bin/env bash
# Optional: halve UI animation durations (feels snappier on slow TVs). Logged; restore.sh undoes it.
source "$(dirname "$0")/lib.sh"
SCALE="${SCALE:-0.5}"
connect
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  old="$(tsh settings get global "$k")"
  tsh settings put global "$k" "$SCALE"
  log_change setting "global:$k" "$old" "$SCALE"
  echo "$k: $old -> $SCALE"
done
