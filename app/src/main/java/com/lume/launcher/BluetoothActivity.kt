package com.lume.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.util.Log

/**
 * Bluetooth no visual do Lume: ligar/desligar, procurar, parear, conectar e esquecer.
 * connect/disconnect/removeBond/isConnected do BluetoothDevice são APIs ocultas, e enable/disable do
 * adaptador só valem para apps de sistema; funciona porque o Lume roda como android.uid.system.
 */
@SuppressLint("MissingPermission") // uid system: as permissões de Bluetooth já estão concedidas
@Suppress("DEPRECATION")
class BluetoothActivity : Activity() {
    private val bt: BluetoothAdapter? by lazy { getSystemService(BluetoothManager::class.java).adapter }
    private lateinit var screen: Screen
    private val found = LinkedHashMap<String, BluetoothDevice>() // achados na busca, por endereço

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val device = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            when (i.action) {
                // sem nome é ruído (beacons, TVs vizinhas); repetido não muda a tela
                BluetoothDevice.ACTION_FOUND ->
                    if (device?.name == null || found.put(device.address, device) != null) return
                // recém-pareado: conecta em seguida, como faz a tela de Bluetooth do Android
                BluetoothDevice.ACTION_BOND_STATE_CHANGED ->
                    if (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, 0) == BluetoothDevice.BOND_BONDED) {
                        device?.hidden("connect")
                    }
            }
            render()
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        screen = Screen(this, "Bluetooth", " ", columns = 3, tileW = 276)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        })
        render()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
        bt?.cancelDiscovery()
    }

    @Deprecated("Android 13: ainda é o caminho mais simples para o botão Voltar")
    override fun onBackPressed() {
        if (!screen.panel.close()) finish()
    }

    private fun render() {
        if (screen.panel.isOpen) return // não refaz a grade com o painel aberto: o foco de volta se perderia
        val adapter = bt
        val on = adapter?.isEnabled == true
        if (!on) found.clear() // desligado, o Android esquece os nomes dos achados; sobrariam só endereços
        val bonded = if (on) adapter!!.bondedDevices.orEmpty().toList() else emptyList()
        val searching = on && adapter!!.isDiscovering
        screen.status.text = when {
            adapter == null -> "Este aparelho não tem Bluetooth"
            !on -> "Desligado"
            searching -> "Procurando dispositivos…"
            bonded.isEmpty() -> "Nenhum dispositivo pareado"
            else -> "${bonded.size} pareado(s)"
        }

        val focus = screen.focusedKey
        screen.clear()
        screen.tile(POWER, getDrawable(R.drawable.ic_power), "Bluetooth", if (on) "Ligado" else "Desligado") {
            screen.panel.choose(
                "Bluetooth", "Desligado libera um pouco de memória.",
                listOf("Ligado", "Desligado"), if (on) 0 else 1,
            ) { if (it == 0) adapter?.enable() else adapter?.disable() }
        }
        if (on) {
            screen.tile(
                SCAN, getDrawable(R.drawable.ic_restart), "Procurar dispositivos",
                if (searching) "Procurando…" else "Deixe o aparelho em modo de pareamento",
            ) {
                found.clear()
                adapter!!.startDiscovery()
            }
            for (d in bonded) {
                val name = d.name ?: d.address
                val connected = d.hidden("isConnected") == true
                screen.tile(d.address, getDrawable(R.drawable.ic_bluetooth), name, if (connected) "Conectado" else "Pareado") {
                    screen.panel.choose(
                        name, if (connected) "Conectado." else "Pareado, sem conexão.",
                        listOf(if (connected) "Desconectar" else "Conectar", "Esquecer"), -1,
                    ) { d.hidden(if (it == 1) "removeBond" else if (connected) "disconnect" else "connect") }
                }
            }
            for (d in found.values) {
                val name = d.name ?: d.address
                when (d.bondState) {
                    BluetoothDevice.BOND_NONE ->
                        screen.tile(d.address, getDrawable(R.drawable.ic_bluetooth), name, "Disponível: OK para parear") {
                            adapter!!.cancelDiscovery() // parear durante a busca costuma falhar
                            d.createBond()
                        }
                    BluetoothDevice.BOND_BONDING ->
                        screen.tile(d.address, getDrawable(R.drawable.ic_bluetooth), name, "Pareando…") {}
                }
            }
        }
        screen.tile(ORIGINAL, getDrawable(R.drawable.ic_settings), "Tela original") {
            open(htcSettings("BluetoothSettingActivity"), Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
        screen.focus(focus)
    }

    /** Chama um método oculto sem parâmetros do BluetoothDevice; null se falhar. */
    private fun BluetoothDevice.hidden(method: String): Any? = try {
        BluetoothDevice::class.java.getMethod(method).invoke(this)
    } catch (t: Throwable) {
        Log.w("Lume", "BluetoothDevice.$method falhou", t)
        null
    }

    private companion object {
        // chaves que nunca batem com um endereço Bluetooth
        const val POWER = "power"
        const val SCAN = "scan"
        const val ORIGINAL = "original"
    }
}
