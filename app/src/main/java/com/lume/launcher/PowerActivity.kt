package com.lume.launcher

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.PowerManager
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Tela do botão power: Desligar (na prática standby, liga de novo em segundos), Timer de desligar
 * ou Reiniciar. Voltar cancela.
 */
class PowerActivity : Activity() {
    private lateinit var panel: ChoicePanel
    private lateinit var timerSub: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            clipChildren = false
        }
        tile(row, R.drawable.ic_power, "Desligar", "Liga de novo em segundos") { standby() }
        timerSub = tile(row, R.drawable.ic_clock, "Timer", "") { chooseTimer() }
        tile(row, R.drawable.ic_restart, "Reiniciar", "Desliga e liga o sistema") {
            getSystemService(PowerManager::class.java).reboot(null)
        }
        val root = FrameLayout(this)
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            background = ColorDrawable(0xE60A0D14.toInt())
            addView(row)
            addView(centered("Power de novo = desligar    Voltar cancela", 13f, R.color.dim))
        })
        panel = ChoicePanel(this, root, row)
        setContentView(root)
        row.getChildAt(0).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        val left = Power.timerMinutesLeft(this)
        timerSub.text = if (left > 0) "Desliga em ${minutes(left)}" else "Desligado"
    }

    // Power apertado de novo com o menu aberto: vai direto para o standby.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        standby()
    }

    @Deprecated("Android 13: ainda é o caminho mais simples para o botão Voltar")
    override fun onBackPressed() {
        if (!panel.close()) finish()
    }

    private fun standby() {
        finish()
        Power.sleep(this)
    }

    private fun chooseTimer() {
        val left = Power.timerMinutesLeft(this)
        panel.choose(
            "Timer de desligar",
            if (left > 0) "Faltam ${minutes(left)}." else "Desliga sozinho depois do tempo escolhido.",
            TIMER.map { if (it == 0) "Desligado" else minutes(it) },
            if (left > 0) -1 else 0,
        ) {
            Power.setTimer(this, TIMER[it])
            toast(if (TIMER[it] == 0) "Timer desligado" else "O projetor desliga em ${minutes(TIMER[it])}")
            finish()
        }
    }

    private fun minutes(m: Int) = when {
        m < 60 -> "$m min"
        m % 60 == 0 -> "${m / 60} h"
        else -> "${m / 60} h ${m % 60} min"
    }

    /** Card grande centralizado; devolve o subtítulo, para o Timer mostrar quanto falta. */
    private fun tile(row: LinearLayout, icon: Int, label: String, sub: String, action: () -> Unit): TextView {
        val subView = centered(sub, 12f, R.color.dim)
        row.addView(FrameLayout(this).apply {
            layoutParams = ViewGroup.MarginLayoutParams(dp(220), dp(130)).apply { setMargins(dp(14), dp(22), dp(14), dp(22)) }
            background = getDrawable(R.drawable.card)
            foreground = getDrawable(R.drawable.card_focus)
            isFocusable = true
            isClickable = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(ImageView(context).apply {
                    setImageResource(icon)
                    imageTintList = ColorStateList.valueOf(getColor(R.color.accent))
                }, LinearLayout.LayoutParams(dp(36), dp(36)))
                addView(centered(label, 20f, R.color.fg).apply { setPadding(0, dp(10), 0, 0) })
                addView(subView)
            }, MATCH_PARENT, MATCH_PARENT)
            setOnClickListener { action() }
            setOnFocusChangeListener { v, f -> v.zoomOnFocus(f) }
        })
        return subView
    }

    // Num LinearLayout vertical o TextView ocupa a largura toda; sem isto o texto fica colado à esquerda.
    private fun centered(s: String, size: Float, color: Int) =
        textLine(s, size, color).apply { gravity = Gravity.CENTER }

    private companion object {
        val TIMER = listOf(0, 30, 60, 90, 120, 180) // minutos; 0 = desligado
    }
}
