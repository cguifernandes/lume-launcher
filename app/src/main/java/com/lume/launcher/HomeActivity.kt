package com.lume.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class HomeActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("lume", MODE_PRIVATE) }
    private lateinit var home: View
    private lateinit var favRow: LinearLayout
    private lateinit var allApps: View
    private lateinit var grid: GridLayout
    private lateinit var label: TextView
    private lateinit var tip: TextView
    private var moving: View? = null  // favorito em modo de mover (toque longo)
    private var moveArmed = false     // true depois que o OK que abriu o modo é solto
    private var apps = emptyList<App>()
    private var shownFavs: String? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.home)
        home = findViewById(R.id.home)
        favRow = findViewById(R.id.favRow)
        allApps = findViewById(R.id.allApps)
        grid = findViewById(R.id.grid)
        label = findViewById(R.id.focusedLabel)
        tip = findViewById(R.id.tooltip)

        // botões só com ícone; o nome aparece num tooltip logo abaixo do botão que está com foco
        fun pill(id: Int, name: String, action: () -> Unit) = findViewById<View>(id).apply {
            contentDescription = name
            setOnClickListener { action() }
            setOnFocusChangeListener { v, f -> if (f) showTip(v, name) else tip.visibility = View.INVISIBLE }
        }
        pill(R.id.btnPicture, "Imagem") { startActivity(Intent(this, PictureActivity::class.java)) }
        pill(R.id.btnHdmi, "HDMI") { open(hdmiIntent()) }
        pill(R.id.btnKeystone, "Ajuste de tela") { open(keystoneIntent()) }
        pill(R.id.btnSettings, "Configurações") { startActivity(Intent(this, SettingsActivity::class.java)) }
        // reaplica a imagem salva (a matriz de cor some se o SurfaceFlinger reiniciar)
        Picture.apply(Picture.load(this))
        enableKeyService()
    }

    /**
     * Liga o serviço que captura o botão power (remapeado para F11); sem ele o botão não faria nada.
     * Acrescenta à lista em vez de sobrescrever: quem instala o Lume pode ter outros serviços de
     * acessibilidade ativos, e eles não podem ser desligados por tabela.
     */
    private fun enableKeyService() {
        val service = ComponentName(this, KeyService::class.java)
        val key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        val enabled = Settings.Secure.getString(contentResolver, key).orEmpty()
        // a lista pode guardar o nome curto (pacote/.Classe) ou o completo; comparar o componente, não o texto
        if (enabled.split(':').none { ComponentName.unflattenFromString(it) == service }) {
            val all = listOf(enabled, service.flattenToShortString()).filter { it.isNotEmpty() }
            Settings.Secure.putString(contentResolver, key, all.joinToString(":"))
        }
        Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
    }

    override fun onResume() {
        super.onResume()
        apps = loadApps(packageManager, packageName)
        renderFavs()
    }

    override fun onPause() {
        super.onPause()
        stopMove()
    }

    // Botão Home pressionado com o launcher já aberto.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        closeAll()
    }

    @Deprecated("Android 13: ainda é o caminho mais simples para o botão Voltar")
    override fun onBackPressed() = closeAll()

    private fun favs(): MutableList<String> =
        (prefs.getString("favs", null)?.split(',') ?: DEFAULT_FAVS).filter { it.isNotEmpty() }.toMutableList()

    private fun toggleFav(app: App) {
        val f = favs()
        val added = !f.remove(app.pkg)
        if (added) f.add(app.pkg)
        prefs.edit().putString("favs", f.joinToString(",")).apply()
        toast(if (added) "${app.label} nos favoritos" else "${app.label} removido dos favoritos")
        renderFavs()
    }

    private fun renderFavs() {
        val list = favs().mapNotNull { p -> apps.find { it.pkg == p } }
        val key = list.joinToString(",") { it.pkg }
        if (key == shownFavs) return // nada mudou: não recarrega banners
        shownFavs = key
        val hadFocus = favRow.hasFocus()
        favRow.removeAllViews()
        list.forEach { favRow.addView(card(it, 168, 95, showLabel = false)) }
        favRow.addView(moreCard())
        // os botões ficam no canto direito; sem isso o ▲ dos primeiros cards não acha nada
        for (i in 0 until favRow.childCount) favRow.getChildAt(i).nextFocusUpId = R.id.btnPicture
        if (hadFocus || currentFocus == null) favRow.getChildAt(0).requestFocus()
    }

    private fun openAll() {
        grid.removeAllViews()
        apps.forEach { grid.addView(card(it, 150, 84, showLabel = true)) }
        home.visibility = View.INVISIBLE
        allApps.visibility = View.VISIBLE
        grid.getChildAt(0)?.requestFocus()
    }

    private fun closeAll() {
        if (allApps.visibility != View.VISIBLE) return
        allApps.visibility = View.GONE
        home.visibility = View.VISIBLE
        // ponytail: recria a grade a cada abertura para liberar os bitmaps; trocar por RecyclerView se passar de ~100 apps
        grid.removeAllViews()
        favRow.getChildAt(0)?.requestFocus()
    }

    private fun baseCard(w: Int, h: Int) = FrameLayout(this).apply {
        layoutParams = ViewGroup.MarginLayoutParams(w.dp, h.dp).apply { setMargins(10.dp, 10.dp, 10.dp, 10.dp) }
        background = getDrawable(R.drawable.card)
        foreground = getDrawable(R.drawable.card_focus)
        clipToOutline = true
        isFocusable = true
        isClickable = true
    }

    /**
     * Favoritos: card todo preenchido, sem texto (o nome aparece acima da faixa). Usa o banner de TV do app;
     * se não tiver, pinta o card com a cor de fundo do ícone e põe o ícone na altura toda.
     * Grade "Todos os apps": ícone + nome.
     */
    private fun card(app: App, w: Int, h: Int, showLabel: Boolean) = baseCard(w, h).apply {
        val icon = app.info.loadIcon(packageManager)
        val banner = if (showLabel) null else app.info.loadBanner(packageManager)
        when {
            showLabel -> addView(
                iconAndText(app.label, ImageView(context).apply { setImageDrawable(icon) }), MATCH_PARENT, MATCH_PARENT
            )
            banner != null -> addView(ImageView(context).apply {
                setImageDrawable(banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, MATCH_PARENT, MATCH_PARENT)
            else -> {
                addView(View(context).apply { setBackgroundColor(edgeColor(icon)) }, MATCH_PARENT, MATCH_PARENT)
                addView(ImageView(context).apply { setImageDrawable(icon) }, FrameLayout.LayoutParams(h.dp, h.dp, Gravity.CENTER))
            }
        }
        tag = app
        setOnClickListener { launch(app) }
        if (showLabel) setOnLongClickListener { toggleFav(app); true }
        else {
            setOnLongClickListener { startMove(it); true }
            setOnKeyListener { v, code, ev -> moving === v && moveKey(app, code, ev) }
        }
        onFocusChangeListener = focusFx(app.label)
    }

    private fun startMove(card: View) {
        moving = card
        moveArmed = false
        card.foreground = getDrawable(R.drawable.card_move) // borda na cor de destaque
        label.text = MOVE_HINT
    }

    private fun stopMove() {
        val card = moving ?: return
        moving = null
        card.foreground = getDrawable(R.drawable.card_focus)
        if (card.isFocused) label.text = (card.tag as App).label
    }

    /** Teclas do modo de mover: ◀▶ muda a posição, ▼ tira dos favoritos, OK ou Voltar conclui. */
    private fun moveKey(app: App, code: Int, ev: KeyEvent): Boolean {
        val down = ev.action == KeyEvent.ACTION_DOWN
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (down) moveFav(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (down) moveFav(1)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (down) {
                stopMove()
                toggleFav(app)
            }
            KeyEvent.KEYCODE_DPAD_UP -> {}
            KeyEvent.KEYCODE_BACK -> if (!down) stopMove()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // O primeiro "soltar" é o do toque longo que abriu o modo: a View precisa tratá-lo
                // (para sair do estado pressionado) e só depois um novo OK conclui.
                if (!down && !moveArmed) {
                    moveArmed = true
                    return false
                }
                if (down && ev.repeatCount == 0 && moveArmed) stopMove()
            }
            else -> return false
        }
        return true
    }

    private fun moveFav(d: Int) {
        val card = moving ?: return
        val i = favRow.indexOfChild(card)
        val j = i + d
        if (j < 0 || j >= favRow.childCount - 1) return // o último card é o "+"
        // Troca o vizinho de lado em vez de mexer no card em foco: ele não perde o foco nem pisca.
        val other = favRow.getChildAt(j)
        favRow.removeView(other)
        favRow.addView(other, i)
        val order = (0 until favRow.childCount - 1).joinToString(",") { (favRow.getChildAt(it).tag as App).pkg }
        prefs.edit().putString("favs", order).apply()
        shownFavs = order // a faixa já está nessa ordem: o onResume não precisa refazê-la
        card.post { card.requestRectangleOnScreen(Rect(0, 0, card.width, card.height)) }
    }

    /** Cor da borda superior do ícone, para o card parecer uma extensão dele. */
    private fun edgeColor(d: Drawable): Int {
        val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, 32, 32)
        d.draw(Canvas(b))
        val c = b.getPixel(16, 3)
        b.recycle()
        return if (Color.alpha(c) > 200) c else 0xFF1C2232.toInt() // ícone sem fundo: cor padrão do card
    }

    private fun moreCard() = baseCard(168, 95).apply {
        addView(TextView(context).apply {
            text = "+"; textSize = 34f; gravity = Gravity.CENTER; setTextColor(getColor(R.color.fg))
        }, MATCH_PARENT, MATCH_PARENT)
        setOnClickListener { openAll() }
        onFocusChangeListener = focusFx("Todos os apps")
    }

    private fun iconAndText(text: String, top: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(top, LinearLayout.LayoutParams(44.dp, 44.dp))
        addView(TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(getColor(R.color.fg))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(8.dp, 6.dp, 8.dp, 0)
        })
    }

    private fun focusFx(text: String) = View.OnFocusChangeListener { v, focused ->
        v.zoomOnFocus(focused)
        // a perda de foco chega antes do ganho, então o rótulo só fica vazio quando o foco sai da faixa
        label.text = if (focused) text else ""
    }

    /** Mostra o nome do botão do topo logo abaixo dele, centralizado e sem sair da tela. */
    private fun showTip(anchor: View, name: String) {
        tip.text = name
        tip.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val root = tip.parent as View
        val a = IntArray(2).also(anchor::getLocationInWindow)
        val r = IntArray(2).also(root::getLocationInWindow)
        val w = tip.measuredWidth
        tip.x = (a[0] - r[0] + (anchor.width - w) / 2f)
            .coerceAtMost((root.width - w - 8.dp).toFloat()).coerceAtLeast(8.dp.toFloat())
        tip.y = (a[1] - r[1] + anchor.height + 6.dp).toFloat()
        tip.visibility = View.VISIBLE
    }

    private fun launch(app: App) = open(
        Intent(Intent.ACTION_MAIN).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    )

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val MOVE_HINT = "◀ ▶ mover     ▼ tirar dos favoritos     OK concluir"
        val DEFAULT_FAVS = listOf(
            "com.google.android.youtube.tv",
            "com.amazon.avod.thirdpartyclient",
            "com.crunchyroll.crunchyroid",
            "tv.twitch.android.app",
        )
    }
}
