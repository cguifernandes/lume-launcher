package com.lume.launcher

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.UserManager
import android.util.Log

/**
 * Recebe, antes de todo mundo (prioridade 1000 no manifesto), os avisos do sistema que acordam apps:
 * boot e ajuste de relógio. Faz duas coisas:
 *
 * 1. Impede o auto-início dos apps instalados (ver [stopIdleApps]).
 * 2. No boot, abre o Lume: o com.jm.jmlauncher fica parado na tela "SMART PROJECTOR" porque tenta abrir
 *    pelo nome o launcher da HTC, que está desativado.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        stopIdleApps(c)
        if (i.action != Intent.ACTION_LOCKED_BOOT_COMPLETED && i.action != Intent.ACTION_BOOT_COMPLETED) return
        Picture.apply(Picture.load(c)) // a matriz de cor não sobrevive ao reboot
        // Sem senha de tela o usuário desbloqueia logo; se ainda não, o BOOT_COMPLETED abre depois.
        if (!c.getSystemService(UserManager::class.java).isUserUnlocked) return
        c.startActivity(Intent(c, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Dá force-stop nos apps instalados que não estão rodando. O force-stop tira os receivers do pacote da
     * fila do broadcast em andamento; como este receiver é o primeiro da fila, os deles nem chegam a rodar.
     * Quem está rodando (aberto pelo usuário, mesmo em segundo plano) não é tocado.
     *
     * O que não funciona neste firmware, testado:
     * - `pm disable` nos receivers: o WorkManager dos apps reativa sozinho;
     * - só deixar o pacote "parado" no boot: o ajuste de relógio (TIME_SET, que acontece logo depois de
     *   ligar, quando a hora vem da internet) acorda pacotes parados.
     *
     * O que isto não cobre: app acordado por outro app que se liga a um serviço dele. A Play Store sobe no
     * boot assim, chamada pelo Google Play Services; pará-la aqui não adianta (testado) e só cancelaria as
     * atualizações automáticas dela.
     */
    private fun stopIdleApps(c: Context) {
        try {
            val am = c.getSystemService(ActivityManager::class.java)
            val running = am.runningAppProcesses.orEmpty().flatMap { it.pkgList.asList() }.toSet()
            val stop = ActivityManager::class.java.getMethod("forceStopPackage", String::class.java)
            for (app in c.packageManager.getInstalledApplications(0)) {
                val pkg = app.packageName
                val userApp = app.flags and ApplicationInfo.FLAG_SYSTEM == 0 && pkg != c.packageName
                if (userApp && pkg !in running) stop.invoke(am, pkg)
            }
        } catch (t: Throwable) {
            Log.w("Lume", "forceStopPackage falhou", t)
        }
    }
}
