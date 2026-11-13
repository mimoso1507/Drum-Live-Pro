package com.drumlivepro.app

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.drumlivepro.app.model.Icon
import com.drumlivepro.app.model.Instruments
import com.drumlivepro.app.model.KIT_COLORS
import com.drumlivepro.app.model.Kit
import com.drumlivepro.app.model.PadConfig
import com.drumlivepro.app.model.SoundRef
import com.drumlivepro.app.ui.IconPainter
import com.drumlivepro.app.ui.Knob
import com.drumlivepro.app.ui.MATCH
import com.drumlivepro.app.ui.T
import com.drumlivepro.app.ui.WRAP
import com.drumlivepro.app.ui.button
import com.drumlivepro.app.ui.dp
import com.drumlivepro.app.ui.dpi
import com.drumlivepro.app.ui.gap
import com.drumlivepro.app.ui.hbox
import com.drumlivepro.app.ui.input
import com.drumlivepro.app.ui.label
import com.drumlivepro.app.ui.lp
import com.drumlivepro.app.ui.panelDialog
import com.drumlivepro.app.ui.rounded
import com.drumlivepro.app.ui.seek
import com.drumlivepro.app.ui.sliderRow
import com.drumlivepro.app.ui.switchRow
import com.drumlivepro.app.ui.toast
import com.drumlivepro.app.ui.vbox
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.roundToInt

/** Grid of selectable icons. [selected] null = "automatic". */
class IconGridView(ctx: Context, private val icons: List<Icon>, var selected: Icon?, private val color: () -> Int = { T.CYAN }, val onPick: (Icon) -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cell get() = dp(58f)
    private val cols get() = (width / cell).toInt().coerceAtLeast(1)
    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w).coerceAtLeast(dpi(200))
        val c = (width / cell).toInt().coerceAtLeast(1)
        setMeasuredDimension(width, ((icons.size + c - 1) / c * cell).toInt())
    }
    override fun onDraw(c: Canvas) {
        val n = cols; val cw = width.toFloat() / n
        icons.forEachIndexed { i, ic ->
            val x = (i % n) * cw; val y = (i / n) * cell
            val sel = ic == selected
            p.style = Paint.Style.FILL; p.color = if (sel) T.BTN_SEL else T.BTN
            c.drawRoundRect(x + dp(3f), y + dp(3f), x + cw - dp(3f), y + cell - dp(3f), dp(10f), dp(10f), p)
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(if (sel) 2.4f else 1f); p.color = if (sel) T.CYAN else 0x66A4A6C2
            c.drawRoundRect(x + dp(3f), y + dp(3f), x + cw - dp(3f), y + cell - dp(3f), dp(10f), dp(10f), p)
            IconPainter.draw(c, ic, x + cw / 2, y + cell / 2, cell * 0.62f, if (sel) color() else 0xFFD8DAF0.toInt())
        }
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) {
            val n = cols; val cw = width.toFloat() / n
            val i = (e.y / cell).toInt() * n + (e.x / cw).toInt()
            icons.getOrNull(i)?.let { selected = it; invalidate(); onPick(it) }
        }
        return true
    }
}

data class PresetEntry(val ref: SoundRef, val label: String)

fun allPresets(a: MainActivity): List<PresetEntry> = a.sfm.files().flatMap { f ->
    a.sfm.kitPresets(f.name).map { p -> PresetEntry(SoundRef(f.name, p.bank, p.program), if (a.sfm.files().size > 1) "${p.name} · ${f.nameWithoutExtension}" else p.name) }
}

fun pickPreset(a: MainActivity, current: SoundRef?, allowKitDefault: Boolean, onPick: (SoundRef?) -> Unit) {
    val v = a.vbox()
    lateinit var d: Dialog
    if (allowKitDefault) v.addView(a.button("Usar el sonido del kit", current == null) { d.dismiss(); onPick(null) }, lp(MATCH, WRAP).apply { bottomMargin = a.dpi(6) })
    for (e in allPresets(a)) v.addView(a.button(e.label, e.ref == current) { d.dismiss(); onPick(e.ref) }, lp(MATCH, WRAP).apply { bottomMargin = a.dpi(6) })
    d = a.panelDialog("Elegir sonido (SF2)", v, widthFraction = 0.8f)
}

private fun presetName(a: MainActivity, r: SoundRef?): String =
    if (r == null) "Sonido del kit" else allPresets(a).firstOrNull { it.ref == r }?.label ?: "${r.sf2} ${r.bank}:${r.program}"

// ---------------- Pad editor ----------------
fun showPadEditor(a: MainActivity, index: Int, kit: Kit? = a.kit, onDone: (() -> Unit)? = null) {
    val k = kit ?: return
    val pad = k.pads.getOrNull(index) ?: return
    val w = pad.copy()
    val isCurrent = k === a.kit
    val v = a.vbox(4)
    val nameIn = a.input("Nombre (vacío = automático)", w.name ?: "")
    v.addView(a.label("Pad ${index + 1}", 13f, T.TEXT2)); v.addView(nameIn, lp(MATCH, WRAP))

    v.addView(a.label("Nota MIDI (0-127)", 15f, T.CYAN, true).apply { setPadding(0, a.dpi(12), 0, a.dpi(4)) })
    val noteLabel = a.label(Instruments.label(w.note), 16f, T.TEXT)
    lateinit var grid: IconGridView
    lateinit var noteSeek: android.widget.SeekBar
    fun setNote(n: Int) {
        w.note = n.coerceIn(0, 127); noteLabel.text = Instruments.label(w.note); noteSeek.progress = w.note
        if (w.icon == null) { grid.selected = null; grid.invalidate() }
    }
    val nr = a.hbox()
    nr.addView(a.button("−") { setNote(w.note - 1) }); nr.gap(8)
    nr.addView(noteLabel, lp(0, WRAP, 1f)); noteLabel.gravity = Gravity.CENTER
    nr.gap(8); nr.addView(a.button("+") { setNote(w.note + 1) })
    if (isCurrent) { nr.gap(8); nr.addView(a.button("Probar") { pad.note = w.note; pad.source = w.source; pad.volume = w.volume; pad.pan = w.pan; a.applyPads(); a.main.postDelayed({ a.engine.core.hit(index, 110) }, 250) }) }
    v.addView(nr)
    noteSeek = a.seek(127, w.note) { setNote(it) }
    v.addView(noteSeek, lp(MATCH, WRAP))
    // GM quick picks
    val quick = a.hbox()
    for (n in listOf(36, 38, 42, 46, 44, 49, 51, 50, 47, 43, 39, 56, 54, 63, 60, 70, 75, 81)) {
        val ins = Instruments.forNote(n)
        quick.addView(QuickNote(a, ins.icon, n) { setNote(n) }, lp(a.dpi(46), a.dpi(46)).apply { marginEnd = a.dpi(4) })
    }
    v.addView(HorizontalScrollView(a).apply { addView(quick) }, lp(MATCH, WRAP))

    v.addView(a.label("Sonido", 15f, T.CYAN, true).apply { setPadding(0, a.dpi(12), 0, a.dpi(4)) })
    val srcBtn = a.button(presetName(a, w.source)) {}
    srcBtn.setOnClickListener { pickPreset(a, w.source, true) { r -> w.source = r; srcBtn.text = presetName(a, r) } }
    v.addView(srcBtn, lp(MATCH, WRAP))
    v.addView(a.sliderRow("Volumen", 150, (w.volume * 100).toInt(), { "$it%" }) { w.volume = it / 100f })
    v.addView(a.sliderRow("Paneo", 200, ((w.pan + 1) * 100).toInt(), { if (it == 100) "Centro" else if (it < 100) "I ${100 - it}" else "D ${it - 100}" }) { w.pan = it / 100f - 1f })

    v.addView(a.label("Ícono (toca «Automático» para usar el de la nota)", 15f, T.CYAN, true).apply { setPadding(0, a.dpi(12), 0, a.dpi(4)) })
    v.addView(a.button("Automático") { w.icon = null; grid.selected = null; grid.invalidate() }, lp(WRAP, WRAP).apply { bottomMargin = a.dpi(6) })
    grid = IconGridView(a, Icon.all, w.icon) { w.icon = it }
    v.addView(grid, lp(MATCH, WRAP))

    a.panelDialog("Editar pad", v, actions = listOf("Guardar" to { d ->
        pad.note = w.note; pad.source = w.source; pad.volume = w.volume; pad.pan = w.pan; pad.icon = w.icon
        pad.name = nameIn.text.toString().trim().ifEmpty { null }
        if (isCurrent) a.applyPads(true)
        onDone?.invoke()
        d.dismiss()
    }))
}

private class QuickNote(ctx: Context, val icon: Icon, val note: Int, val act: () -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init { isClickable = true; setOnClickListener { act() }; background = ctx.rounded(T.BTN, T.BORDER, 10f, 1f) }
    override fun onDraw(c: Canvas) { IconPainter.draw(c, icon, width / 2f, height / 2f, width * 0.7f, 0xFFD8DAF0.toInt()) }
}

// ---------------- Kit editor ----------------
fun showKitEditor(a: MainActivity, existing: Kit?, isNew: Boolean = existing == null) {
    val base = a.kit
    val draft = existing ?: a.kits.newCustom("Mi batería", Icon.STAR, KIT_COLORS[0],
        base?.source ?: SoundRef(com.drumlivepro.app.audio.SoundFontManager.BUNDLED, 128, 0),
        PadConfig.defaults(12))
    val v = a.vbox(4)
    val nameIn = a.input("Nombre del kit", draft.name)
    v.addView(nameIn, lp(MATCH, WRAP))
    v.addView(a.label("Sonido base del kit", 15f, T.CYAN, true).apply { setPadding(0, a.dpi(12), 0, a.dpi(4)) })
    val srcBtn = a.button(presetName(a, draft.source)) {}
    srcBtn.setOnClickListener { pickPreset(a, draft.source, false) { r -> if (r != null) { draft.source = r; srcBtn.text = presetName(a, r) } } }
    v.addView(srcBtn, lp(MATCH, WRAP))

    v.addView(a.label("Color", 15f, T.CYAN, true).apply { setPadding(0, a.dpi(12), 0, a.dpi(4)) })
    lateinit var iconGrid: IconGridView
    val colors = a.hbox()
    val swatches = ArrayList<View>()
    for (col in KIT_COLORS) {
        val sw = View(a)
        fun paint() { sw.background = a.rounded(col, if (draft.color == col) T.TEXT else 0x00000000, 20f, 3f) }
        paint(); swatches += sw
        sw.setOnClickListener { draft.color = col; swatches.forEachIndexed { i, s -> s.background = a.rounded(KIT_COLORS[i], if (KIT_COLORS[i] == col) T.TEXT else 0, 20f, 3f) }; iconGrid.invalidate() }
        colors.addView(sw, lp(a.dpi(36), a.dpi(36)).apply { marginEnd = a.dpi(8) })
    }
    v.addView(HorizontalScrollView(a).apply { addView(colors) })

    v.addView(a.label("Pads y mapeo (toca un pad para elegir nota MIDI, sonido e ícono)", 15f, T.CYAN, true).apply { setPadding(0, a.dpi(12), 0, a.dpi(4)) })
    val padBox = a.vbox()
    fun refreshPads() {
        padBox.removeAllViews()
        val g = GridLayout(a); g.columnCount = 4
        draft.pads.forEachIndexed { i, p ->
            val cell = a.vbox(4); cell.gravity = Gravity.CENTER
            cell.background = a.rounded(T.BTN, T.BORDER, 12f, 1.2f)
            cell.addView(QuickNote(a, p.displayIcon, p.note) { showPadEditor(a, i, draft) { refreshPads() } }, lp(a.dpi(48), a.dpi(48)))
            cell.addView(a.label("${p.note} · ${p.displayName}", 10.5f, T.TEXT2).apply { gravity = Gravity.CENTER; maxLines = 2 }, lp(a.dpi(72), WRAP))
            g.addView(cell, GridLayout.LayoutParams().apply { setMargins(a.dpi(3), a.dpi(3), a.dpi(3), a.dpi(3)) })
        }
        padBox.addView(g)
        val r = a.hbox(); r.setPadding(0, a.dpi(6), 0, 0)
        r.addView(a.button("+ Fila") {
            if (draft.pads.size < 32) { val used = draft.pads.map { it.note }.toSet(); val c = Instruments.defaultLayout.filter { it !in used } + (27..87).filter { it !in used }; repeat(4) { draft.pads += PadConfig(c.getOrElse(it) { 35 }) }; refreshPads() }
        }); r.gap(8)
        r.addView(a.button("− Fila") { if (draft.pads.size > 4) { repeat(4) { draft.pads.removeAt(draft.pads.size - 1) }; refreshPads() } })
        padBox.addView(r)
    }
    refreshPads()
    v.addView(padBox, lp(MATCH, WRAP))

    v.addView(a.label("Ícono del kit", 15f, T.CYAN, true).apply { setPadding(0, a.dpi(12), 0, a.dpi(4)) })
    iconGrid = IconGridView(a, Icon.all, draft.icon, { draft.color }) { draft.icon = it }
    v.addView(iconGrid, lp(MATCH, WRAP))

    a.panelDialog(if (isNew) "Nuevo kit" else "Editar kit", v, actions = listOf("Guardar" to { d ->
        draft.name = nameIn.text.toString().trim().ifEmpty { "Mi batería" }
        a.kits.save(draft)
        d.dismiss()
        a.selectKit(draft)
        a.toast("Kit guardado: ${draft.name}")
        a.showTab(MainActivity.Tab.KITS)
    }))
}

// ---------------- FX ----------------
fun showFxDialog(a: MainActivity) {
    val fx = a.fx
    val wide = a.resources.displayMetrics.widthPixels > a.dpi(640)
    val root = LinearLayout(a).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
    fun logMap(min: Float, max: Float) = { x: Float -> exp(ln(min) + (ln(max) - ln(min)) * x) } to { y: Float -> ((ln(y) - ln(min)) / (ln(max) - ln(min))).coerceIn(0f, 1f) }

    fun column(title: String, on: Boolean, setOn: (Boolean) -> Unit, k1: Knob, k2: Knob, slider: View, reset: () -> Unit): View {
        val col = a.vbox(10)
        col.background = a.rounded(T.BG_DEEP, T.BORDER, 14f, 1.2f)
        col.addView(a.switchRow(title, on) { setOn(it); a.saveSettings() })
        val kr = a.hbox(); kr.gravity = Gravity.CENTER
        kr.addView(k1, lp(0, a.dpi(130), 1f)); kr.addView(k2, lp(0, a.dpi(130), 1f))
        col.addView(kr)
        col.addView(slider)
        col.addView(a.button("RESTABLECER", sizeSp = 13f) { reset(); a.saveSettings() }, lp(MATCH, WRAP).apply { topMargin = a.dpi(8) })
        return col
    }
    val (bwF, bwI) = logMap(200f, 8000f); val (lpF, lpI) = logMap(800f, 16000f)
    val dk1 = Knob(a, "Band Width", bwI(fx.distBand), { "${bwF(it).roundToInt()}Hz" }) { fx.distBand = bwF(it) }
    val dk2 = Knob(a, "Low Pass", lpI(fx.distLowPass), { "${lpF(it).roundToInt()}Hz" }) { fx.distLowPass = lpF(it) }
    lateinit var dS: android.widget.SeekBar
    val dSl = a.vbox().also { it.addView(a.label("Intensity", 13f, T.TEXT2).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP)); dS = a.seek(100, (fx.distIntensity * 100).toInt()) { p -> fx.distIntensity = p / 100f }; it.addView(dS, lp(MATCH, WRAP)) }
    val c1 = column("Distorsión", fx.distOn, { fx.distOn = it }, dk1, dk2, dSl) { fx.resetDist(); dk1.set(bwI(fx.distBand)); dk2.set(lpI(fx.distLowPass)); dS.progress = (fx.distIntensity * 100).toInt() }

    val rk1 = Knob(a, "Time", (fx.revTime - 100f) / 4900f, { "${(100 + it * 4900).roundToInt()}Ms" }) { fx.revTime = 100 + it * 4900 }
    val rk2 = Knob(a, "High Freq", fx.revHighFreq, { String.format("%.3f", it) }) { fx.revHighFreq = it }
    lateinit var rS: android.widget.SeekBar
    val rSl = a.vbox().also { it.addView(a.label("Mix", 13f, T.TEXT2).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP)); rS = a.seek(100, (fx.revMix * 100).toInt()) { p -> fx.revMix = p / 100f }; it.addView(rS, lp(MATCH, WRAP)) }
    val c2 = column("Reverb", fx.revOn, { fx.revOn = it }, rk1, rk2, rSl) { fx.resetRev(); rk1.set((fx.revTime - 100f) / 4900f); rk2.set(fx.revHighFreq); rS.progress = (fx.revMix * 100).toInt() }

    val ek1 = Knob(a, "Delay", (fx.echoDelay - 20f) / 1980f, { "${(20 + it * 1980).roundToInt()}Ms" }) { fx.echoDelay = 20 + it * 1980 }
    val ek2 = Knob(a, "Feedback", fx.echoFeedback / 0.95f, { "${(it * 95).roundToInt()}%" }) { fx.echoFeedback = it * 0.95f }
    lateinit var eS: android.widget.SeekBar
    val eSl = a.vbox().also { it.addView(a.label("Dry/Wet", 13f, T.TEXT2).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP)); eS = a.seek(100, (fx.echoMix * 100).toInt()) { p -> fx.echoMix = p / 100f }; it.addView(eS, lp(MATCH, WRAP)) }
    val c3 = column("Eco", fx.echoOn, { fx.echoOn = it }, ek1, ek2, eSl) { fx.resetEcho(); ek1.set((fx.echoDelay - 20f) / 1980f); ek2.set(fx.echoFeedback / 0.95f); eS.progress = (fx.echoMix * 100).toInt() }

    for (c in listOf(c1, c2, c3)) {
        if (wide) root.addView(c, lp(0, WRAP, 1f).apply { marginEnd = a.dpi(8) }) else root.addView(c, lp(MATCH, WRAP).apply { bottomMargin = a.dpi(8) })
    }
    val d = a.panelDialog("Efectos", root, widthFraction = 0.96f)
    d.setOnDismissListener { a.saveSettings() }
}

