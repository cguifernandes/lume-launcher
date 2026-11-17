#!/usr/bin/env bash
# Desfaz scripts/perf.sh.
# Uso: DEV=IP_DO_PROJETOR:5555 bash scripts/perf-revert.sh
set -e
ADB="${ADB:-adb}"
DEV="${DEV:?defina o endereço do projetor, por exemplo: DEV=192.168.0.50:5555 bash $0}"
sh() { "$ADB" -s "$DEV" shell "$@"; }

BG_APPS="com.amazon.avod.thirdpartyclient com.crunchyroll.crunchyroid tv.twitch.android.app"

for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  sh settings put global "$k" 1.0
done
sh settings delete global cached_apps_freezer
for p in $BG_APPS; do
  sh pm path "$p" >/dev/null 2>&1 || continue
  sh cmd appops set "$p" RUN_ANY_IN_BACKGROUND default
  sh am set-standby-bucket "$p" active
done
echo "Revertido. Reinicie o projetor."
