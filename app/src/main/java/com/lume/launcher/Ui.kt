package com.lume.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

const val KEYSTONE_PKG = "com.softwinner.tcorrection"

fun keystoneIntent(): Intent = Intent().setClassName(KEYSTONE_PKG, "$KEYSTONE_PKG.projection.ProjectionSettingActivity")

fun hdmiIntent(): Intent = Intent("com.android.tv.action.VIEW_INPUTS")
    .setClassName("com.droidlogic.tv.settings", "com.droidlogic.tv.settings.TvSourceActivity")

fun htcSettings(activity: String): Intent = Intent().setClassName("com.htc.htcsettings", "com.htc.activity.$activity")

/** Tenta cada intent em ordem; a primeira que abrir vence. */
fun Activity.open(vararg intents: Intent) {
    for (i in intents) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        } catch (_: Exception) {
        }
    }
    toast("Não foi possível abrir")
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

/** Texto de uma linha só, cortado com reticências. */
fun Context.textLine(s: String, size: Float, color: Int) = TextView(this).apply {
    text = s; textSize = size; setTextColor(getColor(color))
    maxLines = 1; ellipsize = TextUtils.TruncateAt.END
}

/** Efeito de foco dos cards: cresce um pouco e fica por cima dos vizinhos. */
fun View.zoomOnFocus(focused: Boolean) {
    val s = if (focused) 1.08f else 1f
    animate().scaleX(s).scaleY(s).setDuration(90).start()
    translationZ = if (focused) 8f else 0f
}

/**
 * Painel lateral de opções. No Lume um card nunca muda o valor direto: ele abre este painel.
 * OK escolhe e fecha. Voltar fecha sem mudar: a Activity chama close() no onBackPressed.
 * [behind] é o conteúdo da tela, que fica sem foco enquanto o painel está aberto.
 */
class ChoicePanel(private val a: Activity, root: FrameLayout, private val behind: ViewGroup) {
    private val box = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = ColorDrawable(0xF0101520.toInt())
        setPadding(a.dp(20), a.dp(24), a.dp(20), a.dp(24))
        visibility = View.GONE
    }
    private var lastFocus: View? = null

    init {
        root.addView(box, FrameLayout.LayoutParams(a.dp(340), MATCH_PARENT, Gravity.END))
    }

    val isOpen get() = box.visibility == View.VISIBLE

    /** Lista de opções; a atual ([current], ou -1 se nenhuma) vem marcada e com foco. */
    fun choose(title: String, hint: String, options: List<String>, current: Int, onPick: (Int) -> Unit) {
        start(title, hint)
        options.forEachIndexed { i, name -> row(name, i == current) { close(); onPick(i) } }
        show(HEADER + current.coerceAtLeast(0))
    }

    /** Campo de texto ([field] é o que aparece nele vazio) com um botão de confirmar. OK no campo abre o teclado. */
    fun ask(title: String, hint: String, field: String, action: String, onDone: (String) -> Unit) {
        start(title, hint)
        val input = EditText(a).apply {
            setSingleLine()
            this.hint = field
            setHintTextColor(a.getColor(R.color.dim))
            // senha visível: digitar às cegas com controle remoto é pior do que mostrar na parede
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
            textSize = 16f
            setTextColor(a.getColor(R.color.fg))
            backgroundTintList = ColorStateList.valueOf(a.getColor(R.color.accent))
        }
        val done = { close(); onDone(input.text.toString()) }
        input.setOnEditorActionListener { _, _, _ -> done(); true }
        box.addView(input, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.dp(8) })
        row(action, false) { done() }
        show(HEADER)
    }

    /** Fecha o painel; devolve false se ele já estava fechado. */
    fun close(): Boolean {
        if (!isOpen) return false
        a.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(box.windowToken, 0)
        box.visibility = View.GONE
        behind.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        lastFocus?.requestFocus()
        return true
    }

    private fun start(title: String, hint: String) {
        lastFocus = a.currentFocus
        box.removeAllViews()
        box.addView(a.textLine(title, 24f, R.color.fg))
        box.addView(a.textLine(hint, 13f, R.color.dim).apply { setPadding(0, a.dp(2), 0, a.dp(14)) })
    }

    private fun row(name: String, marked: Boolean, onClick: () -> Unit) {
        box.addView(LinearLayout(a).apply {
            isFocusable = true
            isClickable = true
            background = a.getDrawable(R.drawable.row)
            setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12))
            addView(a.textLine(name, 16f, R.color.fg), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (marked) addView(a.textLine("●", 14f, R.color.accent))
            setOnClickListener { onClick() }
        })
    }

    private fun show(focusIndex: Int) {
        behind.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS // prende o foco no painel
        box.visibility = View.VISIBLE
        box.getChildAt(focusIndex)?.requestFocus()
    }

    private companion object {
        const val HEADER = 2 // título + dica vêm antes das opções
    }
}

/**
 * Tela padrão do Lume: título, linha de status e grade de cards com rolagem, mais o painel lateral.
 * Cada card tem uma chave, usada para atualizar o subtítulo e devolver o foco depois de redesenhar.
 */
class Screen(
    private val a: Activity, title: String, hint: String = "",
    columns: Int = 4, private val tileW: Int = 200, private val tileH: Int = 76,
) {
    val status = a.textLine(hint, 14f, R.color.dim)
    val grid = GridLayout(a).apply { columnCount = columns; clipChildren = false }
    val panel: ChoicePanel
    private val subs = HashMap<String, TextView>()

    init {
        val scroll = ScrollView(a).apply {
            isFocusable = false // senão o foco escapa do painel lateral para a própria rolagem
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, 0, 0, a.dp(24))
            addView(grid)
        }
        val root = FrameLayout(a)
        root.addView(LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setPadding(a.dp(48), a.dp(32), a.dp(48), 0)
            addView(a.textLine(title, 30f, R.color.fg).apply {
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                setPadding(a.dp(10), 0, 0, if (hint.isEmpty()) a.dp(12) else 0)
            })
            if (hint.isNotEmpty()) addView(status.apply { setPadding(a.dp(10), a.dp(2), 0, a.dp(8)) })
            addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
        panel = ChoicePanel(a, root, scroll)
        a.setContentView(root)
    }

    fun clear() {
        grid.removeAllViews()
        subs.clear()
    }

    /**
     * Card com ícone, título e subtítulo.
     * [stacked]: ícone em cima do texto (Configurações); senão ícone à esquerda (listas).
     * [tint]: pinta o ícone com a cor de destaque (desenhos do Lume); false para ícones de apps.
     */
    fun tile(
        key: String, icon: Drawable?, title: String, sub: String = "",
        stacked: Boolean = false, tint: Boolean = true, onClick: () -> Unit,
    ) {
        val image = ImageView(a).apply {
            setImageDrawable(icon)
            if (tint) imageTintList = ColorStateList.valueOf(a.getColor(R.color.accent))
        }
        val texts = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(a.textLine(title, if (stacked) 16f else 15f, R.color.fg))
            addView(a.textLine(sub, 12f, R.color.dim).also { subs[key] = it })
        }
        val body = LinearLayout(a).apply {
            gravity = Gravity.CENTER_VERTICAL
            if (stacked) {
                orientation = LinearLayout.VERTICAL
                setPadding(a.dp(16), 0, a.dp(12), 0)
                addView(image, LinearLayout.LayoutParams(a.dp(22), a.dp(22)))
                addView(texts.apply { setPadding(0, a.dp(8), 0, 0) })
            } else {
                val size = a.dp(if (tint) 24 else 40) // desenho do Lume é menor que ícone de app
                setPadding(a.dp(12), 0, a.dp(10), 0)
                addView(image, LinearLayout.LayoutParams(size, size))
                addView(texts.apply { setPadding(a.dp(12), 0, 0, 0) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            }
        }
        grid.addView(FrameLayout(a).apply {
            tag = key
            layoutParams = ViewGroup.MarginLayoutParams(a.dp(tileW), a.dp(tileH)).apply {
                val m = a.dp(10)
                setMargins(m, m, m, m)
            }
            background = a.getDrawable(R.drawable.card)
            foreground = a.getDrawable(R.drawable.card_focus)
            isFocusable = true
            isClickable = true
            addView(body, MATCH_PARENT, MATCH_PARENT)
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, f -> v.zoomOnFocus(f) }
        })
    }

    fun sub(key: String, text: String) {
        subs[key]?.text = text
    }

    val focusedKey get() = grid.focusedChild?.tag as? String

    /** Dá o foco ao card [key]; se ele não existe (mais), ao primeiro. */
    fun focus(key: String?) {
        (key?.let { grid.findViewWithTag<View>(it) } ?: grid.getChildAt(0))?.requestFocus()
    }
}
