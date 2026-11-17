package com.lume.launcher

import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import java.util.Locale

/**
 * Central de configurações no visual do Lume. Cada card abre uma tela (do Lume ou do sistema);
 * os que têm opções (desligar sozinho, protetor de tela, ADB) abrem o painel lateral para escolher.
 */
class SettingsActivity : Activity() {
    private lateinit var screen: Screen
    private val subs = LinkedHashMap<String, () -> String>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        screen = Screen(this, "Configurações", tileH = 96)

        item("wifi", R.drawable.ic_wifi, "Wi-Fi", ::wifiName) { startActivity(Intent(this, WifiActivity::class.java)) }
        item("bt", R.drawable.ic_bluetooth, "Bluetooth", { if (btOn()) "Ligado" else "Desligado" }) {
            startActivity(Intent(this, BluetoothActivity::class.java))
        }
        item("picture", R.drawable.ic_picture, "Imagem", { Picture.modeName(Picture.load(this)) }) {
            startActivity(Intent(this, PictureActivity::class.java))
        }
        item("keystone", R.drawable.ic_keystone, "Projeção", { "Keystone e zoom" }) { open(keystoneIntent()) }
        item("sound", R.drawable.ic_sound, "Som") {
            open(
                Intent().setClassName("com.droidlogic.tv.settings", "com.droidlogic.tv.settings.SoundActivity"),
                Intent(Settings.ACTION_SOUND_SETTINGS),
            )
        }
        // attentive_timeout: o Android entra em standby após X sem usar o controle, mesmo com vídeo tocando,
        // e mostra o aviso "Em espera" 30 s antes (qualquer botão cancela).
        item("autoOff", R.drawable.ic_power, "Desligar sozinho", ::autoOffLabel) {
            choose(
                "Desligar sozinho", "Tempo sem usar o controle. Avisa 30 s antes.",
                AUTO_OFF_HOURS.map { if (it == 0) "Nunca" else "Após $it h" },
                AUTO_OFF_HOURS.indexOf(autoOffHours()),
            ) {
                val h = AUTO_OFF_HOURS[it]
                Settings.Secure.putLong(contentResolver, ATTENTIVE, if (h == 0) -1 else h * 3_600_000L)
            }
        }
        item("date", R.drawable.ic_clock, "Data e hora") {
            open(htcSettings("DateTimeActivity"), Intent(Settings.ACTION_DATE_SETTINGS))
        }
        item("lang", R.drawable.ic_globe, "Idioma", ::language) {
            open(htcSettings("LanguageActivity"), Intent(Settings.ACTION_LOCALE_SETTINGS))
        }
        item("apps", R.drawable.ic_apps, "Aplicativos", { "Espaço e desinstalação" }) {
            startActivity(Intent(this, AppsActivity::class.java))
        }
        item("dream", R.drawable.ic_moon, "Protetor de tela", { if (screensaverOn()) "Ligado" else "Desligado" }) {
            choose(
                "Protetor de tela", "Aparece quando o projetor fica parado.",
                listOf("Ligado", "Desligado"), if (screensaverOn()) 0 else 1,
            ) { Settings.Secure.putInt(contentResolver, SCREENSAVER, if (it == 0) 1 else 0) }
        }
        item("about", R.drawable.ic_info, "Sobre", { "Android ${Build.VERSION.RELEASE}" }) {
            open(htcSettings("AboutDeviceActivity"), Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        }
        item("advanced", R.drawable.ic_settings, "Avançado", { "Menu completo do Android" }) {
            open(Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings"))
        }
        // Com o ADB ligado, qualquer aparelho na rede entra como root sem senha (firmware userdebug).
        // Desligado, o adbd para e a porta 5555 fecha; é para ligar só quando for mexer no projetor pelo PC.
        item("adb", R.drawable.ic_adb, "Depuração (ADB)", { if (adbOn()) "Ligada" else "Desligada" }) {
            choose(
                "Depuração (ADB)", "Ligada, qualquer um no Wi-Fi controla tudo.",
                listOf("Ligada", "Desligada"), if (adbOn()) 0 else 1,
            ) { Settings.Global.putInt(contentResolver, Settings.Global.ADB_ENABLED, if (it == 0) 1 else 0) }
        }
        screen.focus(null)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    @Deprecated("Android 13: ainda é o caminho mais simples para o botão Voltar")
    override fun onBackPressed() {
        if (!screen.panel.close()) finish()
    }

    private fun item(key: String, icon: Int, label: String, sub: () -> String = { "" }, action: () -> Unit) {
        subs[key] = sub
        screen.tile(key, getDrawable(icon), label, stacked = true, onClick = action)
    }

    private fun refresh() = subs.forEach { (key, text) -> screen.sub(key, text()) }

    private fun choose(title: String, hint: String, options: List<String>, current: Int, onPick: (Int) -> Unit) =
        screen.panel.choose(title, hint, options, current) { onPick(it); refresh() }

    @Suppress("DEPRECATION")
    private fun wifiName(): String {
        val ssid = getSystemService(WifiManager::class.java).connectionInfo?.ssid?.trim('"')
        return if (ssid.isNullOrEmpty() || ssid == "<unknown ssid>") "Desconectado" else ssid
    }

    private fun btOn() = getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true

    private fun language() = Locale.getDefault().let { it.getDisplayLanguage(it) }.replaceFirstChar { it.uppercase() }

    private fun screensaverOn() = Settings.Secure.getInt(contentResolver, SCREENSAVER, 1) == 1

    private fun adbOn() = Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

    private fun autoOffHours() = Settings.Secure.getLong(contentResolver, ATTENTIVE, -1)
        .let { if (it <= 0) 0 else (it / 3_600_000L).toInt() }

    private fun autoOffLabel() = autoOffHours().let { if (it == 0) "Nunca" else "Após $it h sem usar o controle" }

    companion object {
        const val SCREENSAVER = "screensaver_enabled"
        const val ATTENTIVE = "attentive_timeout"
        val AUTO_OFF_HOURS = listOf(0, 1, 2, 3, 4, 6) // 0 = nunca
    }
}
