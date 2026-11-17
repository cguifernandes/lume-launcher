# Lume

**Português** · [English](README.en.md)

Launcher leve para projetores Android baratos (os "HY300" e parecidos). Ele substitui a tela inicial de fábrica e
vem com os ajustes que deixam o aparelho mais rápido. É Kotlin puro, sem bibliotecas: o APK tem cerca de 115 KB e
ocupa uns 20 MB de RAM.

> **Antes de tudo:** o Lume foi feito e testado em **um** aparelho só (veja [Compatibilidade](#compatibilidade)) e
> mexe em configurações de sistema. Todo passo daqui tem como desfazer, mas use por sua conta e risco.

## O que ele faz

- **Tela inicial:** relógio, seus apps favoritos em cards grandes e a lista de todos os apps. Dá para reordenar,
  fixar e remover favoritos pelo controle.
- **Atalhos no topo:** Imagem, HDMI, ajuste de tela (keystone) e Configurações.
- **Imagem:** brilho, contraste, saturação e temperatura de cor, com modos prontos (Padrão, Vívido, Cinema, Noite).
  Vale para os menus e para o vídeo, e fica salvo.
- **Configurações no mesmo visual:** Wi-Fi, Bluetooth, aplicativos, desligar sozinho depois de um tempo sem uso,
  protetor de tela e um interruptor para o ADB.
- **Botão power:** abre um menu com Desligar (standby, liga de novo em segundos), Timer de desligar e Reiniciar.
- **Boot:** abre sozinho ao ligar e impede que os apps instalados iniciem em segundo plano sem você pedir.

## Resultado no aparelho de teste

| | Antes | Depois |
|---|---|---|
| RAM disponível na tela inicial | 349 MB | 468 MB |
| Apps em segundo plano na hora da medição | 7 (cerca de 360 MB) | nenhum |
| RAM do launcher | 31 a 39 MB (o de fábrica, em 2 processos) | 21 MB (Lume) |
| Armazenamento livre | 684 MB | 1,2 GB |

O aparelho tem 958 MB de RAM e 4,4 GB de armazenamento para apps e dados. Como ler esses números:

- São medições únicas, não médias. A RAM disponível é o `MemAvailable` de `/proc/meminfo`, e a RAM de cada app é
  o PSS do `dumpsys meminfo`.
- **Antes:** o aparelho como estava em uso, na tela inicial de fábrica.
- **Depois:** 16 minutos após ligar, na tela inicial do Lume, sem abrir nenhum app.
- Nos primeiros minutos depois de ligar, a RAM disponível fica perto de 350 MB, porque o Google Play Services e a
  Play Store trabalham no boot. Aos 16 minutos já estava no valor da tabela.
- O armazenamento veio de desinstalar 8 apps que não eram usados, então depende do que você tem instalado.

## Compatibilidade

Testado em um projetor vendido como "HY300" que por dentro é um **Amlogic T950S** (`t950s_be311`), com Android 13.
Muitos aparelhos com o mesmo nome usam outro chip (Allwinner H713) e outra firmware.

O Lume precisa rodar como app de sistema. Isso só é possível porque a firmware desse aparelho foi assinada com as
chaves de teste públicas do Android (*test-keys*). Confira o seu:

```sh
adb connect IP_DO_PROJETOR:5555
adb shell getprop ro.build.tags       # precisa responder: test-keys
adb shell getprop ro.board.platform   # no aparelho testado: txhd2
```

Se a resposta não for `test-keys`, a instalação falha com `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`. Nesse caso o
Lume só serve com mudanças no código: tirar o `sharedUserId` do manifesto e abrir mão das funções de sistema
(imagem, Wi-Fi, Bluetooth, standby, bloqueio de auto-início).

Em outro aparelho com *test-keys* o launcher em si deve funcionar, mas estas partes são específicas do T950S e
precisam ser adaptadas:

| Parte | Onde está | O que é específico |
|---|---|---|
| Ajuste de tela (keystone) | `keystoneIntent` em `Ui.kt` | app `com.softwinner.tcorrection` |
| HDMI | `hdmiIntent` em `Ui.kt` | tela de entradas da Amlogic |
| Ajuste de imagem do vídeo | `Pq.kt` | `SystemControlManager` da Amlogic |
| Botão power | `scripts/power-key.sh` | nome do keylayout do controle |
| Virar a tela inicial | passo 4 da instalação | launcher de fábrica e o trampolim `com.jm.jmlauncher` |

A interface está só em português e foi desenhada para 800x480. Requer Android 11 ou mais novo.

## Instalação

Você precisa de um PC na mesma rede do projetor, com o
[adb](https://developer.android.com/tools/releases/platform-tools), JDK 17 e o Android SDK (platform 34 e
build-tools 34). No projetor, a depuração ADB pela rede tem que estar ligada; nesse modelo ela já vem ligada.

1. **Conectar:**

   ```sh
   adb connect IP_DO_PROJETOR:5555
   ```

2. **Compilar.** Baixe este repositório e, na pasta dele:

   ```sh
   ./gradlew assembleRelease        # no Windows: gradlew.bat assembleRelease
   ```

   O Gradle precisa saber onde está o SDK: defina a variável `ANDROID_HOME` ou crie um `local.properties` com
   `sdk.dir=/caminho/do/Android/Sdk`.

3. **Instalar e abrir:**

   ```sh
   adb install -r app/build/outputs/apk/release/app-release.apk
   adb shell am start -n com.lume.launcher/.HomeActivity
   ```

   Confira que o Lume abre e que os botões de HDMI e de ajuste de tela funcionam **antes** do próximo passo.

4. **Tornar a tela inicial.** Nessa firmware o botão Home sempre abre o único launcher ativo, então basta
   desativar o de fábrica:

   ```sh
   adb shell pm disable-user --user 0 com.htc.htclauncherhighenglishd08
   ```

   Num Android TV comum o comando é outro:
   `adb shell cmd package set-home-activity com.lume.launcher/.HomeActivity`.

5. **Botão power (opcional).** Faz o power do controle abrir o menu do Lume e troca o desligamento total por
   standby. Precisa de adb com root, que essa firmware já dá.

   ```sh
   DEV=IP_DO_PROJETOR:5555 bash scripts/power-key.sh
   adb reboot
   ```

6. **Desempenho (opcional).** Reduz as animações, congela apps que ficam parados na memória e proíbe os apps de
   streaming de rodar em segundo plano. Antes, troque a lista `BG_APPS` no script pelos seus apps.

   ```sh
   DEV=IP_DO_PROJETOR:5555 bash scripts/perf.sh
   adb reboot
   ```

No Windows, rode os scripts no Git Bash.

### Limpando os apps de fábrica

`scripts/removed-packages.txt` não é um script: é o registro dos pacotes de fábrica desativados no aparelho de
teste, com o motivo e o comando para voltar atrás. Use como referência e aplique só o que fizer sentido no seu:

```sh
adb shell pm disable-user --user 0 PACOTE     # desfaz: adb shell pm enable PACOTE
```

**Não remova nem desative** o keystone (`com.softwinner.tcorrection`), a entrada HDMI
(`com.droidlogic.tv.settings` e `com.droidlogic.android.tv`), o `com.jm.jmlauncher` (é ele que leva o botão Home
até o Lume) nem as telas de configuração (`com.htc.htcsettings` e `com.android.tv.settings`).

## Como usar

| Onde | Ação no controle | Resultado |
|---|---|---|
| Tela inicial | ▲ a partir dos favoritos | vai para os botões do topo; o nome aparece embaixo do botão |
| Tela inicial | segurar OK num favorito | modo de mover: ◀ ▶ muda a posição, ▼ tira dos favoritos, OK conclui |
| Tela inicial | OK no card **+** | todos os apps; segurar OK num app fixa ou remove dos favoritos |
| Imagem | ▲ ▼ escolhe, ◀ ▶ ajusta | muda na hora e fica salvo |
| Configurações | OK num card com opções | abre um painel à direita; OK escolhe, Voltar cancela |
| Qualquer tela | power | menu Desligar / Timer / Reiniciar; power de novo desliga |

## Segurança

Essas firmwares vêm com o ADB aberto na rede, com acesso root e sem senha: qualquer aparelho no mesmo Wi-Fi
controla o projetor por completo. Depois de instalar, desligue em **Configurações → Depuração (ADB)** e religue
só quando for mexer no projetor pelo PC.

## Como desfazer

Nesta ordem:

```sh
DEV=IP_DO_PROJETOR:5555 bash scripts/power-key.sh revert       # o botão power volta ao normal
DEV=IP_DO_PROJETOR:5555 bash scripts/perf-revert.sh
adb shell pm enable com.htc.htclauncherhighenglishd08          # launcher de fábrica de volta
adb uninstall com.lume.launcher
adb reboot
```

Reative o launcher de fábrica e desfaça o botão power **antes** de desinstalar o Lume. Sem o Lume, o botão
remapeado não faz nada até o projetor reiniciar, e sem nenhum launcher ativo a tela inicial não abre.

## O que não foi testado

- **Wi-Fi:** conectar numa rede nova digitando a senha e esquecer uma rede.
- **Bluetooth:** parear e conectar um aparelho (ligar, desligar e procurar foram testados).
- **ADB:** religar pelo card depois de reiniciar o projetor com ele desligado.
- **Imagem no vídeo:** as duas pontas foram testadas em separado (o painel muda o ajuste da Amlogic, e esse ajuste
  muda o vídeo), mas não o painel aberto com um vídeo tocando.
- **Scripts de desfazer:** `power-key.sh revert` e `perf-revert.sh` não chegaram a ser executados no aparelho de
  teste.
- **Instalação do zero:** o Lume liga sozinho o serviço que captura o botão power. No aparelho de teste esse
  serviço já estava ligado, então a primeira ativação numa instalação nova não foi exercitada.
- Qualquer aparelho além do T950S descrito acima.

As telas de Wi-Fi e Bluetooth têm o card **Tela original**, que abre a tela de fábrica caso algo não funcione.

## Para quem quer mexer no código

- **App de sistema:** `sharedUserId="android.uid.system"` mais a assinatura com a chave de plataforma de teste. Com
  isso o app passa nas checagens de permissão e usa APIs ocultas por reflexão.
- **Imagem:** os menus e apps mudam pela matriz de cor do SurfaceFlinger; o vídeo é uma camada de hardware à
  parte e só responde ao ajuste de imagem da Amlogic.
- **Botão power:** o Android trata essa tecla antes de qualquer app. O script remapeia o botão do controle para
  F11 e um serviço de acessibilidade do Lume captura o F11 e abre o menu.
- **Auto-início:** um receiver de prioridade máxima recebe os avisos de boot e de ajuste de relógio antes dos
  outros apps e para os que não estão em uso, o que os tira da fila.

O código fica em `app/src/main/java/com/lume/launcher`. Os comentários nele registram o que foi descoberto testando
nesse aparelho, inclusive o que não funciona.

## Sobre a chave em `keys/`

Os arquivos em `keys/` são a chave de plataforma **de teste** do Android Open Source Project, pública há anos
(`build/make/target/product/security` no AOSP). Não é um segredo do autor. É justamente por ela ser pública que
firmwares assinadas com *test-keys* são inseguras: qualquer app assinado com essa chave vira app de sistema.

## Licença

[Apache License 2.0](LICENSE). Copyright 2026 Guilherme Fernandes.

Incluídos de terceiros, também sob a Apache 2.0: a chave de teste em `keys/` e o ícone de engrenagem (Material
Icons), do Android Open Source Project, e o Gradle Wrapper.
