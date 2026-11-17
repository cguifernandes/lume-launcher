# Lume

[Português](README.md) · **English**

A lightweight launcher for cheap Android projectors (the "HY300" kind). It replaces the stock home screen and comes
with the system tweaks that make the device faster. It is pure Kotlin with no libraries: the APK is about 115 KB and
uses around 20 MB of RAM.

> **Before anything else:** Lume was built and tested on **one** device only (see [Compatibility](#compatibility))
> and it changes system settings. Every step here can be undone, but use it at your own risk.

The interface is in Portuguese only, so this page quotes the on-screen labels in Portuguese with a translation.

## What it does

- **Home screen:** clock, your favorite apps as large cards, and the list of all apps. You can reorder, pin and
  remove favorites with the remote.
- **Top shortcuts:** picture, HDMI, screen adjustment (keystone) and settings.
- **Picture:** brightness, contrast, saturation and color temperature, with presets (standard, vivid, cinema,
  night). It applies to menus and to video, and it is saved.
- **Settings in the same look:** Wi-Fi, Bluetooth, apps, auto power-off after a period without use, screensaver
  and a switch for ADB.
- **Power button:** opens a menu with power off (standby, back on in seconds), sleep timer and restart.
- **Boot:** opens by itself at power-on and stops installed apps from starting in the background on their own.

## Results on the test device

| | Before | After |
|---|---|---|
| Available RAM on the home screen | 349 MB | 468 MB |
| Apps in the background when measured | 7 (about 360 MB) | none |
| Launcher RAM | 31 to 39 MB (the stock one, in 2 processes) | 21 MB (Lume) |
| Free storage | 684 MB | 1.2 GB |

The device has 958 MB of RAM and 4.4 GB of storage for apps and data. How to read these numbers:

- They are single measurements, not averages. Available RAM is `MemAvailable` from `/proc/meminfo`, and each
  app's RAM is the PSS from `dumpsys meminfo`.
- **Before:** the device as it was being used, on the stock home screen.
- **After:** 16 minutes after power-on, on Lume's home screen, with no app opened.
- In the first minutes after power-on, available RAM stays near 350 MB because Google Play Services and the Play
  Store are busy at boot. At 16 minutes it was already at the value in the table.
- The storage came from uninstalling 8 apps that were not being used, so it depends on what you have installed.

## Compatibility

Tested on a projector sold as "HY300" that is an **Amlogic T950S** (`t950s_be311`) inside, running Android 13.
Many devices sold under the same name use a different chip (Allwinner H713) and different firmware.

Lume has to run as a system app. That is only possible because this device's firmware was signed with Android's
public test keys (*test-keys*). Check yours:

```sh
adb connect PROJECTOR_IP:5555
adb shell getprop ro.build.tags       # must answer: test-keys
adb shell getprop ro.board.platform   # on the tested device: txhd2
```

If the answer is not `test-keys`, installation fails with `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`. In that case
Lume only works with code changes: remove `sharedUserId` from the manifest and give up the system features
(picture, Wi-Fi, Bluetooth, standby, auto-start blocking).

On another *test-keys* device the launcher itself should work, but these parts are specific to the T950S and need
adapting:

| Part | Where it lives | What is device-specific |
|---|---|---|
| Screen adjustment (keystone) | `keystoneIntent` in `Ui.kt` | the `com.softwinner.tcorrection` app |
| HDMI | `hdmiIntent` in `Ui.kt` | Amlogic's input screen |
| Video picture adjustment | `Pq.kt` | Amlogic's `SystemControlManager` |
| Power button | `scripts/power-key.sh` | the remote's keylayout file name |
| Becoming the home screen | installation step 4 | the stock launcher and the `com.jm.jmlauncher` trampoline |

The interface was designed for 800x480. Requires Android 11 or newer.

## Installation

You need a PC on the same network as the projector, with
[adb](https://developer.android.com/tools/releases/platform-tools), JDK 17 and the Android SDK (platform 34 and
build-tools 34). ADB over the network must be enabled on the projector; on this model it is enabled out of the box.

1. **Connect:**

   ```sh
   adb connect PROJECTOR_IP:5555
   ```

2. **Build.** Download this repository and, in its folder:

   ```sh
   ./gradlew assembleRelease        # on Windows: gradlew.bat assembleRelease
   ```

   Gradle needs to know where the SDK is: set the `ANDROID_HOME` variable or create a `local.properties` file with
   `sdk.dir=/path/to/Android/Sdk`.

3. **Install and open:**

   ```sh
   adb install -r app/build/outputs/apk/release/app-release.apk
   adb shell am start -n com.lume.launcher/.HomeActivity
   ```

   Check that Lume opens and that the HDMI and screen adjustment buttons work **before** the next step.

4. **Make it the home screen.** On this firmware the Home button always opens the only enabled launcher, so
   disabling the stock one is enough:

   ```sh
   adb shell pm disable-user --user 0 com.htc.htclauncherhighenglishd08
   ```

   On a regular Android TV the command is different:
   `adb shell cmd package set-home-activity com.lume.launcher/.HomeActivity`.

5. **Power button (optional).** Makes the remote's power button open Lume's menu and replaces full shutdown with
   standby. It needs adb with root, which this firmware already gives you.

   ```sh
   DEV=PROJECTOR_IP:5555 bash scripts/power-key.sh
   adb reboot
   ```

6. **Performance (optional).** Shortens animations, freezes apps sitting idle in memory and forbids the streaming
   apps from running in the background. First replace the `BG_APPS` list in the script with your own apps.

   ```sh
   DEV=PROJECTOR_IP:5555 bash scripts/perf.sh
   adb reboot
   ```

On Windows, run the scripts in Git Bash.

### Cleaning up the stock apps

`scripts/removed-packages.txt` is not a script: it is the record of the stock packages disabled on the test device,
with the reason and the command to bring each one back. Use it as a reference and apply only what makes sense on
yours:

```sh
adb shell pm disable-user --user 0 PACKAGE     # undo: adb shell pm enable PACKAGE
```

**Do not remove or disable** the keystone app (`com.softwinner.tcorrection`), the HDMI input
(`com.droidlogic.tv.settings` and `com.droidlogic.android.tv`), `com.jm.jmlauncher` (it is what takes the Home
button to Lume) or the settings screens (`com.htc.htcsettings` and `com.android.tv.settings`).

## How to use it

| Where | Remote action | Result |
|---|---|---|
| Home screen | ▲ from the favorites row | moves to the top buttons; the name shows under the focused button |
| Home screen | hold OK on a favorite | move mode: ◀ ▶ change the position, ▼ removes it from favorites, OK finishes |
| Home screen | OK on the **+** card | all apps; hold OK on an app to pin it or remove it from favorites |
| Imagem (picture) | ▲ ▼ select, ◀ ▶ adjust | changes live and is saved |
| Configurações (settings) | OK on a card with options | opens a panel on the right; OK picks, Back cancels |
| Any screen | power | menu with Desligar (power off) / Timer / Reiniciar (restart); power again turns it off |

## Security

These firmwares ship with ADB open on the network, with root access and no password: any device on the same Wi-Fi
has full control of the projector. After installing, turn it off in **Configurações → Depuração (ADB)** (settings →
ADB debugging) and turn it back on only when you need to work on the projector from the PC.

## How to undo it

In this order:

```sh
DEV=PROJECTOR_IP:5555 bash scripts/power-key.sh revert         # the power button goes back to normal
DEV=PROJECTOR_IP:5555 bash scripts/perf-revert.sh
adb shell pm enable com.htc.htclauncherhighenglishd08          # stock launcher back
adb uninstall com.lume.launcher
adb reboot
```

Re-enable the stock launcher and revert the power button **before** uninstalling Lume. Without Lume, the remapped
button does nothing until the projector restarts, and with no launcher enabled the home screen does not open.

## What was not tested

- **Wi-Fi:** connecting to a new network by typing the password, and forgetting a network.
- **Bluetooth:** pairing and connecting a device (turning it on and off and scanning were tested).
- **ADB:** turning it back on from the card after restarting the projector with it off.
- **Picture on video:** the two ends were tested separately (the panel changes Amlogic's setting, and that setting
  changes the video), but not the panel open with a video playing.
- **Undo scripts:** `power-key.sh revert` and `perf-revert.sh` were never actually run on the test device.
- **Fresh install:** Lume turns on by itself the service that catches the power button. On the test device that
  service was already on, so its first activation on a fresh install was not exercised.
- Any device other than the T950S described above.

The Wi-Fi and Bluetooth screens have a **Tela original** (original screen) card that opens the stock screen in case
something does not work.

## For people who want to change the code

- **System app:** `sharedUserId="android.uid.system"` plus the signature with the test platform key. With that the
  app passes permission checks and uses hidden APIs through reflection.
- **Picture:** menus and apps change through SurfaceFlinger's color matrix; video is a separate hardware layer and
  only responds to Amlogic's picture setting.
- **Power button:** Android handles this key before any app sees it. The script remaps the remote's button to F11,
  and an accessibility service in Lume catches F11 and opens the menu.
- **Auto-start:** a top-priority receiver gets the boot and clock-change broadcasts before the other apps and stops
  the ones not in use, which removes them from the delivery queue.

The code is in `app/src/main/java/com/lume/launcher`. Its comments (in Portuguese) record what was learned by
testing on this device, including what does not work.

## About the key in `keys/`

The files in `keys/` are the Android Open Source Project's **test** platform key, public for years
(`build/make/target/product/security` in AOSP). It is not anyone's secret. That it is public is exactly why
firmwares signed with *test-keys* are insecure: any app signed with this key becomes a system app.

## License

[Apache License 2.0](LICENSE). Copyright 2026 Guilherme Fernandes.

Third-party material included, also under Apache 2.0: the test key in `keys/` and the gear icon (Material Icons),
from the Android Open Source Project, and the Gradle Wrapper.
