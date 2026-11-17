package com.lume.launcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

/** Standby e timer de desligar. */
object Power {
    private const val KEY = "timerAt"

    private fun prefs(c: Context) = c.getSharedPreferences("power", Context.MODE_PRIVATE)

    // ONE_SHOT: depois de disparar o PendingIntent deixa de existir, e é assim que se sabe que não há timer.
    // As flags fazem parte da identidade dele, então a consulta (NO_CREATE) precisa usar as mesmas.
    private fun pending(c: Context, extra: Int): PendingIntent? = PendingIntent.getBroadcast(
        c, 0, Intent(c, SleepTimerReceiver::class.java),
        extra or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Standby: o projetor apaga e volta em segundos pelo power. API oculta; funciona porque o app é uid system. */
    fun sleep(c: Context) {
        setTimer(c, 0)
        try {
            val pm = c.getSystemService(PowerManager::class.java)
            PowerManager::class.java.getMethod("goToSleep", Long::class.javaPrimitiveType)
                .invoke(pm, SystemClock.uptimeMillis())
        } catch (t: Throwable) {
            Log.w("Lume", "goToSleep falhou", t)
        }
    }

    /** Agenda o standby para daqui a [minutes]; 0 cancela. O timer some sozinho se o projetor reiniciar. */
    fun setTimer(c: Context, minutes: Int) {
        val alarms = c.getSystemService(AlarmManager::class.java)
        pending(c, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
        if (minutes <= 0) return
        val at = SystemClock.elapsedRealtime() + minutes * 60_000L
        prefs(c).edit().putLong(KEY, at).apply()
        // _WAKEUP: se o projetor já estiver em standby na hora, o alarme é consumido ali, sem religar a tela
        alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(c, 0)!!)
    }

    /** Minutos que faltam para o timer disparar; 0 se não há timer. */
    fun timerMinutesLeft(c: Context): Int {
        if (pending(c, PendingIntent.FLAG_NO_CREATE) == null) return 0
        val left = prefs(c).getLong(KEY, 0) - SystemClock.elapsedRealtime()
        return if (left <= 0) 0 else ((left + 59_999) / 60_000).toInt()
    }
}

class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Power.sleep(c)
}
