package com.lume.launcher

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * Ajuste de imagem em duas camadas, testadas no aparelho:
 * - menus/apps: matriz de cor do SurfaceFlinger (transação 1015, a mesma do `service call SurfaceFlinger 1015`).
 *   Ela se perde no reboot; o BootReceiver e a HomeActivity aplicam de novo.
 * - vídeo: fica numa camada de hardware que a matriz não alcança, então é ajustado pelo PQ da Amlogic (Pq).
 */
object Picture {
    /** brilho/contraste/temperatura: -50..50 (0 = neutro). saturação: 0..100 (50 = neutro). */
    data class V(val bright: Int = 0, val contrast: Int = 0, val sat: Int = 50, val temp: Int = 0)

    val PRESETS = listOf(
        "Padrão" to V(),
        "Vívido" to V(bright = 0, contrast = 12, sat = 72, temp = 5),
        "Cinema" to V(bright = -4, contrast = 8, sat = 48, temp = -12),
        "Noite" to V(bright = -18, contrast = -4, sat = 42, temp = -35),
    )

    fun modeName(v: V) = PRESETS.firstOrNull { it.second == v }?.first ?: "Personalizado"

    // Armazenamento "device protected": dá para ler no boot, antes de desbloquear o usuário.
    private fun prefs(c: Context) =
        c.createDeviceProtectedStorageContext().getSharedPreferences("picture", Context.MODE_PRIVATE)

    fun load(c: Context) = prefs(c).run {
        V(getInt("bright", 0), getInt("contrast", 0), getInt("sat", 50), getInt("temp", 0))
    }

    fun save(c: Context, v: V) {
        prefs(c).edit().putInt("bright", v.bright).putInt("contrast", v.contrast)
            .putInt("sat", v.sat).putInt("temp", v.temp).apply()
        apply(v)
        Pq.video(v)
    }

    fun apply(v: V) = transact(if (v == V()) null else matrix(v))

    /** Matriz 4x4 em ordem de coluna: out = M * (r, g, b, 1). */
    fun matrix(v: V): FloatArray {
        val t = v.temp / 50f // -1 quente .. +1 frio
        val gain = if (t >= 0) floatArrayOf(1 - 0.3f * t, 1 - 0.1f * t, 1f)
        else floatArrayOf(1f, 1 + 0.1f * t, 1 + 0.3f * t)
        val s = v.sat / 50f
        val k = 1 + v.contrast / 100f
        val off = 0.5f * (1 - k) + v.bright / 200f
        val luma = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
        val m = FloatArray(16)
        for (col in 0..2) for (row in 0..2) {
            m[col * 4 + row] = k * ((1 - s) * luma[col] + if (row == col) s else 0f) * gain[col]
        }
        for (row in 0..2) m[12 + row] = off
        m[15] = 1f
        return m
    }

    private fun transact(m: FloatArray?) {
        val data = Parcel.obtain()
        try {
            val sf = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java).invoke(null, "SurfaceFlinger") as IBinder
            data.writeInterfaceToken("android.ui.ISurfaceComposer")
            if (m == null) data.writeInt(0) else { data.writeInt(1); m.forEach(data::writeFloat) }
            sf.transact(1015, data, null, 0)
        } catch (t: Throwable) {
            Log.w("Picture", "falha ao aplicar matriz de cor", t)
        } finally {
            data.recycle()
        }
    }
}
