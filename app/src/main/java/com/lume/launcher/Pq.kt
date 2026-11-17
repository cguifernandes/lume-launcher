package com.lume.launcher

import android.util.Log

/**
 * Ajuste da camada de VÍDEO via com.droidlogic.app.SystemControlManager (Amlogic).
 * Só tem efeito com vídeo tocando; menus e apps são ajustados pela matriz de cor (Picture).
 * Os Set*(valor, 1) salvam no pq.db, então valem depois de reiniciar.
 * Reflexão: a classe vem de /vendor/framework/droidlogic.software.core.jar (uses-library)
 * e só responde porque o app roda como android.uid.system.
 */
object Pq {
    private val scm: Any? = try {
        Class.forName("com.droidlogic.app.SystemControlManager").getMethod("getInstance").invoke(null)
    } catch (t: Throwable) {
        Log.w("Pq", "SystemControlManager indisponível", t); null
    }

    /** Leva os valores do Lume para a escala do PQ (0..100, 50 = neutro). */
    fun video(v: Picture.V) {
        if (scm == null) return
        call("SetBrightness", 50 + v.bright, 1)
        call("SetContrast", 50 + v.contrast, 1)
        call("SetSaturation", v.sat, 1)
        // 0 padrão, 1 quente, 2 frio (enum color_temperature da Amlogic)
        call("SetColorTemperature", if (v.temp < -15) 1 else if (v.temp > 15) 2 else 0, 1)
    }

    private fun call(name: String, vararg args: Int) {
        try {
            val types = Array(args.size) { Int::class.javaPrimitiveType!! }
            scm!!.javaClass.getMethod(name, *types).invoke(scm, *args.toTypedArray())
        } catch (t: Throwable) {
            Log.w("Pq", name, t)
        }
    }
}
