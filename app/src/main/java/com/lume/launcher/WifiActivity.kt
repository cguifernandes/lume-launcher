package com.lume.launcher

import android.app.Activity
import android.content.Intent
import android.net.wifi.SupplicantState
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.format.Formatter

/**
 * Wi-Fi no visual do Lume: redes visíveis, conectar (com senha) e esquecer.
 * Usa as APIs antigas do WifiManager (addNetwork/enableNetwork/removeNetwork), que o Android só libera
 * para apps de sistema; funciona porque o Lume roda como android.uid.system.
 */
@Suppress("DEPRECATION")
class WifiActivity : Activity() {
    private class Net(val ssid: String, val level: Int, val caps: String, val netId: Int, val connected: Boolean) {
        val saved get() = netId >= 0
        val open get() = SECURED.none { it in caps }
        val supported get() = open || "PSK" in caps || "SAE" in caps
    }

    private val wifi by lazy { getSystemService(WifiManager::class.java) }
    private lateinit var screen: Screen
    private var shown: String? = null   // redes exibidas; a grade só é refeita quando isso muda
    private var ticks = 0
    private var connecting: String? = null
    private var deadline = 0L
    private var addedId = -1            // rede criada nesta tentativa; apagada se a conexão falhar

    private val tick = object : Runnable {
        override fun run() {
            if (ticks++ % 5 == 0) wifi.startScan() // a cada 10 s
            refresh()
            screen.grid.postDelayed(this, 2000)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        screen = Screen(this, "Wi-Fi", "Procurando redes…", columns = 3, tileW = 276)
    }

    override fun onResume() {
        super.onResume()
        ticks = 0
        tick.run()
    }

    override fun onPause() {
        super.onPause()
        screen.grid.removeCallbacks(tick)
    }

    @Deprecated("Android 13: ainda é o caminho mais simples para o botão Voltar")
    override fun onBackPressed() {
        if (!screen.panel.close()) finish()
    }

    private fun refresh() {
        val info = wifi.connectionInfo
        val current = info?.takeIf { it.supplicantState == SupplicantState.COMPLETED }?.ssid?.unquote()
        checkAttempt(current)
        screen.status.text = when {
            connecting != null -> "Conectando a $connecting…"
            current != null -> "Conectado a $current  ·  IP ${Formatter.formatIpAddress(info.ipAddress)}"
            !wifi.isWifiEnabled -> "Wi-Fi desligado"
            else -> "Desconectado"
        }
        if (screen.panel.isOpen) return // não refaz a grade com o painel aberto: o foco de volta se perderia

        val saved = wifi.configuredNetworks.orEmpty().associate { it.SSID.unquote() to it.networkId }
        val nets = wifi.scanResults.orEmpty().filter { it.SSID.isNotEmpty() }
            .groupBy { it.SSID }
            .map { (ssid, aps) -> aps.maxBy { it.level }.let { Net(ssid, it.level, it.capabilities, saved[ssid] ?: -1, ssid == current) } }
            .sortedWith(compareByDescending<Net> { it.connected }.thenByDescending { it.saved }.thenByDescending { it.level })
            .take(MAX)
        // Assinatura sem a ordem: o sinal oscila o tempo todo, e reordenar os cards com a pessoa navegando atrapalha.
        val key = nets.map { "${it.ssid}/${it.connected}/${it.saved}" }.sorted().joinToString("|")
        if (key != shown) {
            shown = key
            val focus = screen.focusedKey
            screen.clear()
            for (n in nets) screen.tile(n.ssid, getDrawable(R.drawable.ic_wifi), n.ssid) { click(n) }
            screen.tile(ORIGINAL, getDrawable(R.drawable.ic_settings), "Tela original", "Ponto de acesso e mais") {
                open(htcSettings("NetWorkActivity"), Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            screen.focus(focus)
        }
        for (n in nets) screen.sub(n.ssid, describe(n)) // estado e sinal mudam sem refazer a grade
    }

    private fun describe(n: Net): String {
        val state = when {
            n.connected -> "Conectada"
            n.ssid == connecting -> "Conectando…"
            n.saved -> "Salva"
            n.open -> "Aberta"
            else -> "Com senha"
        }
        return "$state  ·  sinal ${SIGNAL[WifiManager.calculateSignalLevel(n.level, SIGNAL.size)]}"
    }

    private fun click(n: Net) = when {
        n.connected -> screen.panel.choose(n.ssid, "Rede em uso.", listOf("Esquecer rede"), -1) { forget(n) }
        n.saved -> screen.panel.choose(n.ssid, "Rede salva.", listOf("Conectar", "Esquecer rede"), -1) {
            if (it == 0) connect(n.netId, n.ssid) else forget(n)
        }
        !n.supported -> toast("Tipo de rede não suportado aqui. Use a Tela original.")
        n.open -> connectNew(n, null)
        else -> screen.panel.ask(n.ssid, "OK no campo abre o teclado.", "Senha", "Conectar") { pass ->
            if (pass.length < 8) toast("A senha precisa ter pelo menos 8 caracteres") else connectNew(n, pass)
        }
    }

    private fun connect(netId: Int, ssid: String) {
        connecting = ssid
        deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        if (!wifi.enableNetwork(netId, true)) fail() // true: troca a rede atual por esta
        refresh()
    }

    private fun connectNew(n: Net, pass: String?) {
        val config = WifiConfiguration().apply {
            SSID = "\"${n.ssid}\""
            when {
                pass == null -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                "PSK" !in n.caps -> { // rede só WPA3
                    setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE)
                    preSharedKey = "\"$pass\""
                }
                else -> preSharedKey = "\"$pass\""
            }
        }
        addedId = wifi.addNetwork(config)
        if (addedId < 0) toast("Não foi possível salvar a rede") else connect(addedId, n.ssid)
    }

    private fun forget(n: Net) {
        if (!wifi.removeNetwork(n.netId)) toast("Não foi possível esquecer a rede")
        refresh()
    }

    /** Acompanha a tentativa de conexão: deu certo quando o SSID atual é o pedido; falhou depois de 20 s. */
    private fun checkAttempt(current: String?) {
        val target = connecting ?: return
        if (current == target) {
            connecting = null
            addedId = -1
        } else if (SystemClock.elapsedRealtime() > deadline) fail()
    }

    private fun fail() {
        toast("Não conectou a $connecting. Confira a senha.")
        if (addedId >= 0) wifi.removeNetwork(addedId) // senha errada não fica salva: da próxima vez pede de novo
        connecting = null
        addedId = -1
    }

    private fun String.unquote() = removeSurrounding("\"")

    private companion object {
        const val ORIGINAL = "\u0000original" // chave que nunca bate com um SSID
        const val MAX = 20
        const val TIMEOUT_MS = 20_000L
        val SECURED = listOf("PSK", "SAE", "WEP", "EAP", "OWE")
        val SIGNAL = listOf("fraco", "médio", "bom", "ótimo")
    }
}
