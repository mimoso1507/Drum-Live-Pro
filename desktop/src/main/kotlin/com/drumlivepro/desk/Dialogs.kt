package com.drumlivepro.desk

import com.drumlivepro.app.audio.SoundFontManager
import com.drumlivepro.app.model.Icon
import com.drumlivepro.app.model.Instruments
import com.drumlivepro.app.model.KIT_COLORS
import com.drumlivepro.app.model.Kit
import com.drumlivepro.app.model.PadConfig
import com.drumlivepro.app.model.SoundRef
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

data class PresetEntry(val ref: SoundRef, val label: String)

fun allPresets(a: App): List<PresetEntry> {
    val files = a.sfm.files()
    return files.flatMap { f ->
        a.sfm.kitPresets(f.name).map { p -> PresetEntry(SoundRef(f.name, p.bank, p.program), if (files.size > 1) "${p.name} · ${f.nameWithoutExtension}" else p.name) }
    }
}

fun pickPreset(a: App, current: SoundRef?, allowKitDefault: Boolean, onPick: (SoundRef?) -> Unit) {
    val v = JPanel(GridLayout(0, 3, dpi(6), dpi(6))).apply { isOpaque = false }
    lateinit var d: Dlg
    if (allowKitDefault) v.add(RButton("Usar el sonido del kit", current == null) { d.dismiss(); onPick(null) }.apply { center = false })
    for (e in allPresets(a)) v.add(RButton(e.label, e.ref == current) { d.dismiss(); onPick(e.ref) }.apply { center = false })
    val box = VBox(); box.add(v)
    d = Dlg(a.stage, "Elegir sonido (SF2)", box, widthFraction = 0.75f)
}

private fun presetName(a: App, r: SoundRef?): String =
    if (r == null) "Sonido del kit" else allPresets(a).firstOrNull { it.ref == r }?.label ?: "${r.sf2} ${r.bank}:${r.program}"

private fun head(t: String) = Label(t, 15f, T.CYAN, true).apply { border = BorderFactory.createEmptyBorder(dpi(12), 0, dpi(4), 0) }

// ---------------- Pad editor ----------------
fun showPadEditor(a: App, index: Int, kit: Kit? = a.kit, onDone: (() -> Unit)? = null) {
    val k = kit ?: return
    val pad = k.pads.getOrNull(index) ?: return
    val w = pad.copy()
    val isCurrent = k === a.kit
    val v = VBox(4)
    val nameIn = Input("Nombre (vacío = automático)", w.name ?: "")
    v.add(Label("Pad ${index + 1}" + (PAD_KEYS.getOrNull(index)?.let { " · tecla $it" } ?: ""), 13f, T.TEXT2)); v.add(nameIn)

    v.add(head("Nota MIDI (0-127)"))
    val noteLabel = JLabel(Instruments.label(w.note), SwingConstants.CENTER).apply { font = serif(16f); foreground = col(T.TEXT); maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height) }
    lateinit var grid: IconGridView
    lateinit var noteSeek: Slider
    fun setNote(n: Int) {
        w.note = n.coerceIn(0, 127); noteLabel.text = Instruments.label(w.note); noteSeek.value = w.note
        if (w.icon == null) { grid.selected = null; grid.repaint() }
    }
    val nr = HBox()
    nr.add(RButton("−") { setNote(w.note - 1) }); nr.gap(8)
    nr.add(noteLabel); nr.gap(8); nr.add(RButton("+") { setNote(w.note + 1) })
    if (isCurrent) { nr.gap(8); nr.add(RButton("Probar") { pad.note = w.note; pad.source = w.source; pad.volume = w.volume; pad.pan = w.pan; a.applyPads(); a.postDelayed(250) { a.engine.core.hit(index, 110) } }) }
    v.add(nr)
    noteSeek = Slider(127, w.note) { setNote(it) }
    v.add(noteSeek)
    val quick = WrapPanel()
    for (n in listOf(36, 38, 42, 46, 44, 49, 51, 50, 47, 43, 39, 56, 54, 63, 60, 70, 75, 81)) {
        quick.add(IconTile(Instruments.forNote(n).icon, 46) { setNote(n) }.apply { toolTipText = Instruments.label(n) })
    }
    v.add(quick)

    v.add(head("Sonido"))
    lateinit var srcBtn: RButton
    srcBtn = RButton(presetName(a, w.source)) { pickPreset(a, w.source, true) { r -> w.source = r; srcBtn.text = presetName(a, r) } }.apply { center = false }
    v.add(srcBtn)
    v.add(sliderRow("Volumen", 150, (w.volume * 100).toInt(), { "$it%" }) { w.volume = it / 100f })
    v.add(sliderRow("Paneo", 200, ((w.pan + 1) * 100).toInt(), { if (it == 100) "Centro" else if (it < 100) "I ${100 - it}" else "D ${it - 100}" }) { w.pan = it / 100f - 1f })

    v.add(head("Ícono (pulsa «Automático» para usar el de la nota)"))
    v.add(RButton("Automático") { w.icon = null; grid.selected = null; grid.repaint() }); v.gap(6)
    grid = IconGridView(Icon.all, w.icon) { w.icon = it }
    v.add(grid)

    Dlg(a.stage, "Editar pad", v, actions = listOf("Guardar" to { d ->
        pad.note = w.note; pad.source = w.source; pad.volume = w.volume; pad.pan = w.pan; pad.icon = w.icon
        pad.name = nameIn.text.trim().ifEmpty { null }
        if (isCurrent) a.applyPads(true)
        onDone?.invoke()
        d.dismiss()
    }))
}

// ---------------- Kit editor ----------------
fun showKitEditor(a: App, existing: Kit?, isNew: Boolean = existing == null) {
    val base = a.kit
    val draft = existing ?: a.kits.newCustom("Mi batería", Icon.STAR, KIT_COLORS[0],
        base?.source ?: SoundRef(SoundFontManager.BUNDLED, 128, 0), PadConfig.defaults(12))
    val v = VBox(4)
    val nameIn = Input("Nombre del kit", draft.name)
    v.add(nameIn)
    v.add(head("Sonido base del kit"))
    lateinit var srcBtn: RButton
    srcBtn = RButton(presetName(a, draft.source)) { pickPreset(a, draft.source, false) { r -> if (r != null) { draft.source = r; srcBtn.text = presetName(a, r) } } }.apply { center = false }
    v.add(srcBtn)

    v.add(head("Color"))
    lateinit var iconGrid: IconGridView
    val colors = WrapPanel()
    val swatches = ArrayList<Swatch>()
    for (c in KIT_COLORS) {
        val sw = Swatch(c, draft.color == c) { draft.color = c; swatches.forEach { s -> s.sel = s.color == c; s.repaint() }; iconGrid.repaint() }
        swatches += sw; colors.add(sw)
    }
    v.add(colors)

    v.add(head("Pads y mapeo (haz clic en un pad para elegir nota MIDI, sonido e ícono)"))
    val padBox = VBox()
    fun refreshPads() {
        padBox.removeAll()
        val g = JPanel(GridLayout(0, 4, dpi(6), dpi(6))).apply { isOpaque = false; alignmentX = 0f }
        draft.pads.forEachIndexed { i, p ->
            val cell = Card(T.BTN, T.BORDER, 4, 12f)
            val tile = IconTile(p.displayIcon, 48) { showPadEditor(a, i, draft) { refreshPads() } }
            val row = HBox(); row.glue(); row.add(tile); row.glue()
            cell.add(row)
            cell.add(JLabel("${p.note} · ${p.displayName}", SwingConstants.CENTER).apply { font = serif(11f); foreground = col(T.TEXT2); maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height) })
            g.add(cell)
        }
        padBox.add(g)
        val r = HBox(); r.border = BorderFactory.createEmptyBorder(dpi(6), 0, 0, 0)
        r.add(RButton("+ Fila") {
            if (draft.pads.size < 32) { val used = draft.pads.map { it.note }.toSet(); val c = Instruments.defaultLayout.filter { it !in used } + (27..87).filter { it !in used }; repeat(4) { draft.pads += PadConfig(c.getOrElse(it) { 35 }) }; refreshPads() }
        }); r.gap(8)
        r.add(RButton("− Fila") { if (draft.pads.size > 4) { repeat(4) { draft.pads.removeAt(draft.pads.size - 1) }; refreshPads() } }); r.glue()
        padBox.add(r)
        padBox.revalidate(); padBox.repaint()
    }
    refreshPads()
    v.add(padBox)

    v.add(head("Ícono del kit"))
    iconGrid = IconGridView(Icon.all, draft.icon, { draft.color }) { draft.icon = it }
    v.add(iconGrid)

    Dlg(a.stage, if (isNew) "Nuevo kit" else "Editar kit", v, actions = listOf("Guardar" to { d ->
        draft.name = nameIn.text.trim().ifEmpty { "Mi batería" }
        a.kits.save(draft)
        d.dismiss()
        a.selectKit(draft)
        a.toast("Kit guardado: ${draft.name}")
        a.showTab(App.Tab.KITS)
    }))
}

// ---------------- FX ----------------
fun showFxDialog(a: App) {
    val fx = a.fx
    val root = JPanel(GridLayout(1, 3, dpi(8), 0)).apply { isOpaque = false }
    fun logMap(min: Float, max: Float) = { x: Float -> exp(ln(min) + (ln(max) - ln(min)) * x) } to { y: Float -> ((ln(y) - ln(min)) / (ln(max) - ln(min))).coerceIn(0f, 1f) }

    fun column(title: String, on: Boolean, setOn: (Boolean) -> Unit, k1: Knob, k2: Knob, sliderTitle: String, slider: Slider, reset: () -> Unit): Card {
        val col = Card(T.BG_DEEP, T.BORDER, 10)
        col.add(switchRow(title, on) { setOn(it); a.saveSettings() })
        val kr = JPanel(GridLayout(1, 2)).apply { isOpaque = false; alignmentX = 0f; preferredSize = Dimension(dpi(220), dpi(130)); maximumSize = Dimension(Int.MAX_VALUE, dpi(130)) }
        kr.add(k1); kr.add(k2)
        col.add(kr)
        col.add(JLabel(sliderTitle, SwingConstants.CENTER).apply { font = serif(13f); foreground = col(T.TEXT2); maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height) })
        col.add(slider)
        col.gap(8)
        col.add(RButton("RESTABLECER", size = 13f) { reset(); a.saveSettings() }.apply { center = false })
        return col
    }
    val (bwF, bwI) = logMap(200f, 8000f); val (lpF, lpI) = logMap(800f, 16000f)
    val dk1 = Knob("Band Width", bwI(fx.distBand), { "${bwF(it).roundToInt()}Hz" }) { fx.distBand = bwF(it) }
    val dk2 = Knob("Low Pass", lpI(fx.distLowPass), { "${lpF(it).roundToInt()}Hz" }) { fx.distLowPass = lpF(it) }
    val dS = Slider(100, (fx.distIntensity * 100).toInt()) { p -> fx.distIntensity = p / 100f }
    root.add(column("Distorsión", fx.distOn, { fx.distOn = it }, dk1, dk2, "Intensity", dS) { fx.resetDist(); dk1.set(bwI(fx.distBand)); dk2.set(lpI(fx.distLowPass)); dS.value = (fx.distIntensity * 100).toInt() })

    val rk1 = Knob("Time", (fx.revTime - 100f) / 4900f, { "${(100 + it * 4900).roundToInt()}Ms" }) { fx.revTime = 100 + it * 4900 }
    val rk2 = Knob("High Freq", fx.revHighFreq, { String.format("%.3f", it) }) { fx.revHighFreq = it }
    val rS = Slider(100, (fx.revMix * 100).toInt()) { p -> fx.revMix = p / 100f }
    root.add(column("Reverb", fx.revOn, { fx.revOn = it }, rk1, rk2, "Mix", rS) { fx.resetRev(); rk1.set((fx.revTime - 100f) / 4900f); rk2.set(fx.revHighFreq); rS.value = (fx.revMix * 100).toInt() })

    val ek1 = Knob("Delay", (fx.echoDelay - 20f) / 1980f, { "${(20 + it * 1980).roundToInt()}Ms" }) { fx.echoDelay = 20 + it * 1980 }
    val ek2 = Knob("Feedback", fx.echoFeedback / 0.95f, { "${(it * 95).roundToInt()}%" }) { fx.echoFeedback = it * 0.95f }
    val eS = Slider(100, (fx.echoMix * 100).toInt()) { p -> fx.echoMix = p / 100f }
    root.add(column("Eco", fx.echoOn, { fx.echoOn = it }, ek1, ek2, "Dry/Wet", eS) { fx.resetEcho(); ek1.set((fx.echoDelay - 20f) / 1980f); ek2.set(fx.echoFeedback / 0.95f); eS.value = (fx.echoMix * 100).toInt() })

    val box = VBox(); box.add(root)
    val d = Dlg(a.stage, "Efectos", box, widthFraction = 0.9f)
    d.onDismiss = { a.saveSettings() }
}
