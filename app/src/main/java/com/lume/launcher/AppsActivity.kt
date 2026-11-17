package com.lume.launcher

import android.app.Activity
import android.app.usage.StorageStatsManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import android.text.format.Formatter

/** Lista de apps com ícone e espaço ocupado. OK abre os detalhes do sistema (desinstalar, forçar parada, limpar). */
class AppsActivity : Activity() {
    private lateinit var screen: Screen

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        screen = Screen(this, "Aplicativos", "OK abre os detalhes: desinstalar, forçar parada, limpar dados")
    }

    // Refaz a lista a cada volta: um app desinstalado na tela de detalhes tem que sumir daqui.
    override fun onResume() {
        super.onResume()
        val focus = screen.focusedKey
        val apps = loadApps(packageManager, packageName)
        screen.clear()
        for (app in apps) {
            screen.tile(app.pkg, app.info.loadIcon(packageManager), app.label, "…", tint = false) {
                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
            }
        }
        screen.focus(focus)
        // tamanhos fora da thread principal: a consulta é lenta no armazenamento do projetor
        Thread {
            val stats = getSystemService(StorageStatsManager::class.java)
            for (app in apps) {
                val size = try {
                    stats.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.pkg, Process.myUserHandle())
                        .run { appBytes + dataBytes }
                } catch (_: Exception) {
                    continue
                }
                runOnUiThread { screen.sub(app.pkg, Formatter.formatShortFileSize(this, size)) }
            }
        }.start()
    }
}
