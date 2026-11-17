package com.lume.launcher

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.sign

/** Painel lateral transparente de imagem. ▲▼ escolhe, ◀▶ ajusta; muda ao vivo e fica salvo. */
class PictureActivity : Activity() {
    private class Row(
        val label: String,
        val get: (Picture.V) -> Int,
        val set: (Picture.V, Int) -> Picture.V,
        val min: Int = -50,
        val max: Int = 50,
        val format: (Int) -> String = { if (it > 0) "+$it" else "$it" },
    )

    private var v = Picture.V()
    private val refreshers = mutableListOf<() -> Unit>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        v = Picture.load(this)
        Pq.video(v) // mantém o PQ do vídeo alinhado com o que está salvo

        val rows = listOf(
            Row("Brilho", { it.bright }, { v, x -> v.copy(bright = x) }),
            Row("Contraste", { it.contrast }, { v, x -> v.copy(contrast = x) }),
            Row("Saturação", { it.sat }, { v, x -> v.copy(sat = x) }, min = 0, max = 100,
                format = { "${it * 2}%" }),
            Row("Temperatura de cor", { it.temp }, { v, x -> v.copy(temp = x) }, format = {
                when { it < 0 -> "Quente ${abs(it)}"; it > 0 -> "Fria $it"; else -> "Neutra" }
            }),
        )

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 24.dp, 20.dp, 24.dp)
            addView(text("Imagem", 24f, R.color.fg))
            addView(text("▲▼ escolher    ◀▶ ajustar    Voltar sai", 13f, R.color.dim).apply {
                setPadding(0, 2.dp, 0, 14.dp)
            })
            addView(modeRow())
            rows.forEach { addView(rowView(it)) }
            addView(resetRow())
        }
        setContentView(FrameLayout(this).apply {
            addView(ScrollView(context).apply {
                background = ColorDrawable(0xF0101520.toInt())
                isVerticalScrollBarEnabled = false
                addView(list)
            }, FrameLayout.LayoutParams(340.dp, MATCH_PARENT, Gravity.END))
        })
        refreshers.forEach { it() }
        list.getChildAt(2).requestFocus()
    }

    private fun update(nv: Picture.V) {
        v = nv
        Picture.save(this, v)
        refreshers.forEach { it() }
    }

    private fun modeRow(): View {
        val value = text("", 16f, R.color.accent)
        refreshers += { value.text = "‹ ${Picture.modeName(v)} ›" }
        return row("Modo", value, null) { d ->
            val p = Picture.PRESETS
            val i = p.indexOfFirst { it.second == v } // -1 = personalizado
            val next = if (i < 0) (if (d > 0) 0 else p.lastIndex) else (i + d.sign).coerceIn(0, p.lastIndex)
            update(p[next].second)
        }
    }

    private fun rowView(r: Row): View {
        val value = text("", 16f, R.color.accent)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = r.max - r.min
            progressDrawable = getDrawable(R.drawable.bar)
        }
        refreshers += {
            val x = r.get(v)
            value.text = r.format(x)
            bar.progress = x - r.min
        }
        return row(r.label, value, bar) { d -> update(r.set(v, (r.get(v) + d).coerceIn(r.min, r.max))) }
    }

    private fun resetRow() = row("Restaurar padrão", text("OK", 14f, R.color.dim), null) {}.apply {
        setOnClickListener { update(Picture.V()) }
    }

    /** Linha focável; ◀▶ chamam [onStep] com -1/+1 (ou ±5 segurando a tecla). */
    private fun row(label: String, value: TextView, bar: ProgressBar?, onStep: (Int) -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isClickable = true
            background = getDrawable(R.drawable.row)
            setPadding(14.dp, 10.dp, 14.dp, 10.dp)
            addView(LinearLayout(context).apply {
                addView(text(label, 16f, R.color.fg), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(value)
            })
            bar?.let { addView(it, LinearLayout.LayoutParams(MATCH_PARENT, 4.dp).apply { topMargin = 8.dp }) }
            setOnKeyListener { _, code, ev ->
                val d = when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> -1
                    KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                    else -> return@setOnKeyListener false
                }
                if (ev.action == KeyEvent.ACTION_DOWN) onStep(d * if (ev.repeatCount > 3) 5 else 1)
                true
            }
        }

    private fun text(s: String, size: Float, color: Int) = TextView(this).apply {
        text = s; textSize = size; setTextColor(getColor(color))
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
