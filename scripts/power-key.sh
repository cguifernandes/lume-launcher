#!/usr/bin/env bash
# Faz o botão power (controle e botões do corpo) abrir o menu Desligar/Timer/Reiniciar do Lume.
# - copia o keylayout do fabricante para /data com o nome versionado (tem precedência sobre o de /vendor)
#   trocando POWER por F11 (WAKE = ainda acorda o projetor do standby);
# - persist.sys.power.key.action=0: o power "de verdade" (CEC etc.) entra em standby em vez de desligar.
# O serviço que captura o F11 é ligado pelo próprio Lume quando a tela inicial abre.
# O nome do keylayout vale para o Amlogic T950S. Em outro aparelho, veja em `adb shell dumpsys input` qual é o
# KeyLayoutFile e a "version" do controle (ir_keypad) e ajuste KL e o arquivo de origem abaixo.
# Precisa de adb com root. Uso: DEV=IP_DO_PROJETOR:5555 bash scripts/power-key.sh   (desfazer: ... power-key.sh revert)
set -e
ADB="${ADB:-adb}"
DEV="${DEV:?defina o endereço do projetor, por exemplo: DEV=192.168.0.50:5555 bash $0}"
sh() { "$ADB" -s "$DEV" shell "$@"; }
KL=/data/system/devices/keylayout/Vendor_0001_Product_0001_Version_0100.kl
if [ "$1" = revert ]; then
  sh "rm -f $KL; setprop persist.sys.power.key.action 1"
else
  sh "mkdir -p /data/system/devices/keylayout && sed -E 's/^key 116 +POWER.*/key 116      F11      WAKE/' /vendor/usr/keylayout/Vendor_0001_Product_0001.kl > $KL && chown -R system:system /data/system/devices && chmod 755 /data/system/devices /data/system/devices/keylayout && chmod 644 $KL && grep -n '^key 116' $KL"
  sh "setprop persist.sys.power.key.action 0"
fi
echo "Feito. Reinicie o projetor para o keylayout valer."
