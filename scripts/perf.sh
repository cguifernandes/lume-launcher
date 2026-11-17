#!/usr/bin/env bash
# Ajustes de desempenho do projetor. Todos reversíveis com scripts/perf-revert.sh.
# Uso: DEV=IP_DO_PROJETOR:5555 bash scripts/perf.sh
set -e
ADB="${ADB:-adb}"
DEV="${DEV:?defina o endereço do projetor, por exemplo: DEV=192.168.0.50:5555 bash $0}"
sh() { "$ADB" -s "$DEV" shell "$@"; }

# Apps proibidos de rodar em segundo plano (continuam funcionando quando abertos).
# Troque pela lista dos seus apps de streaming; os que não estiverem instalados são pulados.
# Fora da lista: YouTube, que precisa ficar escutando o celular para receber transmissão.
BG_APPS="com.amazon.avod.thirdpartyclient com.crunchyroll.crunchyroid tv.twitch.android.app"

for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  sh settings put global "$k" 0.5
done

# Congela apps em cache (não usam CPU até voltarem pra tela). Vale após reiniciar.
sh settings put global cached_apps_freezer enabled

for p in $BG_APPS; do
  sh pm path "$p" >/dev/null 2>&1 || continue
  sh cmd appops set "$p" RUN_ANY_IN_BACKGROUND ignore
  sh am set-standby-bucket "$p" restricted
  sh am force-stop "$p"
done
echo "OK. Reinicie o projetor para ativar o freezer: $ADB -s $DEV reboot"
