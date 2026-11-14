package com.drumlivepro.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ScrollView
import com.drumlivepro.app.audio.FxParams
import com.drumlivepro.app.audio.SoundFontManager
import com.drumlivepro.app.audio.WavExporter
import com.drumlivepro.app.model.Instruments
import com.drumlivepro.app.model.Kit
import com.drumlivepro.app.model.Recording
import com.drumlivepro.app.seq.Grid
import com.drumlivepro.app.seq.Meter
import com.drumlivepro.app.ui.IconPainter
import com.drumlivepro.app.ui.MATCH
import com.drumlivepro.app.ui.T
import com.drumlivepro.app.ui.THEMES
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
import com.drumlivepro.app.ui.styleSelected
import com.drumlivepro.app.ui.switchRow
import com.drumlivepro.app.ui.toast
import com.drumlivepro.app.ui.vbox
import kotlin.math.roundToInt

private fun Context.scrollOf(v: View) = ScrollView(this).apply { addView(v); isFillViewport = true }

private fun Context.section(title: String) = label(title, 16f, T.CYAN, true).apply { setPadding(0, dpi(14), 0, dpi(6)) }

/** Wrapping row of selectable chips. */
private fun <V> Context.chips(items: List<V>, selected: V, name: (V) -> String, onPick: (V) -> Unit): View {
    val g = GridLayout(this); g.columnCount = if (resources.displayMetrics.widthPixels > dpi(700)) 7 else 4
    val views = ArrayList<android.widget.TextView>()
    for (it in items) {
        val b = button(name(it), it == selected, 14f) { _ -> views.forEachIndexed { i, v -> v.styleSelected(items[i] == it) }; onPick(it) }
        views += b
        g.addView(b, GridLayout.LayoutParams().apply { setMargins(dpi(3), dpi(3), dpi(3), dpi(3)); width = WRAP })
    }
    return g
}

// ---------------- PADS ----------------
fun buildPadsPanel(a: MainActivity): View {
    val box = a.vbox()
    val bar = a.hbox(4)
    bar.addView(a.label(a.kit?.name ?: "", 14f, T.TEXT2).apply { setSingleLine() }, lp(0, WRAP, 1f))
    bar.addView(a.button("+ Fila", sizeSp = 13f) { a.addRow() }); bar.gap(6)
    bar.addView(a.button("− Fila", sizeSp = 13f) { a.removeRow() }); bar.gap(6)
    bar.addView(a.button("Reverb", sizeSp = 13f) { showQuickReverb(a) }); bar.gap(6)
    bar.addView(a.button("Kits", sizeSp = 13f) { a.showTab(MainActivity.Tab.KITS) })
    box.addView(bar, lp(MATCH, WRAP))
    box.addView(a.padView, lp(MATCH, 0, 1f))
    return box
}

fun showQuickReverb(a: MainActivity) {
    val v = a.vbox(4)
    v.addView(a.switchRow("Reverb activa", a.fx.revOn) { a.fx.revOn = it; a.saveSettings() })
    v.addView(a.sliderRow("Cantidad (mezcla)", 100, (a.fx.revMix * 100).toInt(), { "$it%" }) { a.fx.revMix = it / 100f; a.saveSettings() })
    v.addView(a.sliderRow("Tamaño (tiempo)", 4900, (a.fx.revTime - 100).toInt(), { "${it + 100} ms" }) { a.fx.revTime = it + 100f; a.saveSettings() })
    a.panelDialog("Reverb", v, widthFraction = 0.8f)
}

// ---------------- KITS ----------------
class KitCard(ctx: Context, val kit: Kit?, val sel: Boolean) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL) }
    init { isClickable = true; isLongClickable = true }
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(dpi(104), dpi(112))
    override fun onDraw(c: Canvas) {
        val r = dp(16f)
        p.style = Paint.Style.FILL; p.color = if (sel) T.BTN_SEL else T.BTN
        c.drawRoundRect(dp(3f), dp(3f), width - dp(3f), height - dp(3f), r, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(if (sel) 2.6f else 1.5f); p.color = if (sel) T.CYAN else T.BORDER
        c.drawRoundRect(dp(3f), dp(3f), width - dp(3f), height - dp(3f), r, r, p)
        if (kit == null) {
            IconPainter.glyph(c, com.drumlivepro.app.ui.Glyph.PLUS, width / 2f, height * 0.42f, width * 0.4f, T.CYAN)
            tp.color = T.TEXT; tp.textSize = dp(12.5f); c.drawText("Nuevo kit", width / 2f, height - dp(14f), tp); return
        }
        p.style = Paint.Style.FILL; p.color = (kit.color and 0x00FFFFFF) or 0x33000000
        c.drawCircle(width / 2f, height * 0.4f, width * 0.3f, p)
        IconPainter.draw(c, kit.icon, width / 2f, height * 0.4f, width * 0.46f, kit.color)
        tp.color = T.TEXT; tp.textSize = dp(12f)
        var name = kit.name
        while (name.length > 3 && tp.measureText(name) > width - dp(12f)) name = name.dropLast(2) + "…"
        c.drawText(name, width / 2f, height - dp(14f), tp)
        if (kit.custom) { p.color = T.MINT; c.drawCircle(width - dp(14f), dp(14f), dp(4f), p) }
    }
}

fun buildKitsPanel(a: MainActivity): View {
    val box = a.vbox(10)
    val head = a.hbox()
    head.addView(a.label("Baterías disponibles", 17f, T.TEXT, true), lp(0, WRAP, 1f))
    head.addView(a.button("Importar SF2", sizeSp = 13f) { a.pickSf2() })
    box.addView(head)
    box.addView(a.label("Toca para cargar · mantén pulsado para editar, duplicar o borrar", 12.5f, T.TEXT2))
    val grid = GridLayout(a)
    val cols = (a.resources.displayMetrics.widthPixels / a.dp(110f)).toInt().coerceAtLeast(3)
    grid.columnCount = cols
    box.addView(grid, lp(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
    box.addView(a.section("Archivos SF2 instalados"))
    val sfList = a.vbox()
    box.addView(sfList)
    a.bg.execute {
        val all = a.kits.all()
        val files = a.sfm.files().map { it.name to a.sfm.kitPresets(it.name).size }
        a.main.post {
            for (k in all) {
                val card = KitCard(a, k, k.id == a.kit?.id)
                card.setOnClickListener { a.bg.execute { val loaded = a.kits.load(k.id) ?: k; a.main.post { a.selectKit(loaded); a.toast("Kit: ${k.name}") } } }
                card.setOnLongClickListener { kitMenu(a, k); true }
                grid.addView(card)
            }
            val plus = KitCard(a, null, false)
            plus.setOnClickListener { showKitEditor(a, null) }
            grid.addView(plus)
            for ((name, n) in files) {
                val row = a.hbox(4)
                row.addView(a.label("$name  ·  $n kits", 14f, T.TEXT2), lp(0, WRAP, 1f))
                if (name != SoundFontManager.BUNDLED) row.addView(a.button("Quitar", sizeSp = 12f) {
                    a.sfm.delete(name); a.toast("SF2 eliminado"); a.showTab(MainActivity.Tab.KITS)
                })
                sfList.addView(row)
            }
            sfList.addView(a.label("El kit incluido usa sonidos de GeneralUser GS (S. Christian Collins).", 11.5f, T.TEXT2).apply { setPadding(0, a.dpi(8), 0, 0) })
        }
    }
    return a.scrollOf(box)
}

private fun kitMenu(a: MainActivity, k: Kit) {
    val v = a.vbox(4)
    lateinit var d: android.app.Dialog
    fun opt(t: String, f: () -> Unit) { v.addView(a.button(t) { d.dismiss(); f() }, lp(MATCH, WRAP).apply { bottomMargin = a.dpi(8) }) }
    if (k.custom) {
        opt("Editar kit") { showKitEditor(a, a.kits.load(k.id) ?: k) }
        opt("Borrar kit") { a.kits.delete(k); a.toast("Kit borrado"); a.showTab(MainActivity.Tab.KITS) }
    } else {
        opt("Duplicar como kit personalizado") {
            val base = a.kits.load(k.id) ?: k
            showKitEditor(a, a.kits.newCustom(base.name + " (mío)", base.icon, base.color, base.source, base.pads.map { it.copy() }.toMutableList()), isNew = true)
        }
        opt("Restablecer mapeo original") { a.kits.delete(k); if (a.kit?.id == k.id) a.selectKit(a.kits.load(k.id)!!); a.toast("Mapeo restablecido") }
    }
    d = a.panelDialog(k.name, v, widthFraction = 0.75f)
}

// ---------------- RHYTHM ----------------
fun buildRhythmPanel(a: MainActivity): View {
    val c = a.engine.core
    val box = a.vbox(12)
    box.addView(a.section("Tempo"))
    val bpmLabel = a.label("${c.bpm.roundToInt()} BPM", 26f, T.TEXT, true)
    val bpmRow = a.hbox()
    lateinit var seek: android.widget.SeekBar
    fun setBpm(v: Double) { c.bpm = v.coerceIn(30.0, 300.0); bpmLabel.text = "${c.bpm.roundToInt()} BPM"; seek.progress = c.bpm.roundToInt() - 30; a.saveSettings() }
    bpmRow.addView(a.button("−", sizeSp = 18f) { setBpm(c.bpm - 1) }); bpmRow.gap(10)
    bpmRow.addView(bpmLabel, lp(0, WRAP, 1f)); bpmLabel.gravity = Gravity.CENTER
    bpmRow.gap(10); bpmRow.addView(a.button("+", sizeSp = 18f) { setBpm(c.bpm + 1) })
    val taps = ArrayList<Long>()
    bpmRow.gap(10); bpmRow.addView(a.button("Tap", sizeSp = 16f) {
        val now = System.currentTimeMillis(); if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear(); taps += now
        if (taps.size >= 2) { val iv = (taps.last() - taps.first()).toDouble() / (taps.size - 1); setBpm(60000.0 / iv) }
        while (taps.size > 6) taps.removeAt(0)
    })
    box.addView(bpmRow)
    seek = a.seek(270, c.bpm.roundToInt() - 30) { setBpm(it + 30.0) }
    box.addView(seek, lp(MATCH, WRAP))

    box.addView(a.section("Compás"))
    box.addView(a.chips(Meter.COMMON, c.meter, { it.toString() }) { c.meter = it; a.regenPatterns(); a.saveSettings() })

    box.addView(a.section("Metrónomo"))
    box.addView(a.switchRow("Metrónomo activo", c.metronomeOn) { c.metronomeOn = it; a.refreshTop(); a.saveSettings() })
    box.addView(a.switchRow("Cuenta previa (1 compás) al grabar", a.prefs.getBoolean("countIn", true)) { a.prefs.edit().putBoolean("countIn", it).apply() })
    box.addView(a.sliderRow("Volumen del metrónomo", 100, (c.metronomeVolume * 100).toInt(), { "$it%" }) { c.metronomeVolume = it / 100f; a.saveSettings() })

    box.addView(a.section("Cuantización"))
    box.addView(a.label("Resolución de la rejilla (el compás se elige arriba por separado):", 13f, T.TEXT2))
    box.addView(a.chips(Grid.values().toList(), c.grid, { it.label }) { c.grid = it; a.saveSettings() })
    box.addView(a.switchRow("Cuantizar mientras grabo", c.quantizeOnRecord) { c.quantizeOnRecord = it; a.saveSettings() })
    box.addView(a.sliderRow("Swing", 100, (c.swing * 100).toInt(), { if (it == 0) "Recto" else "$it%" }) { c.swing = it / 100f; a.saveSettings() })
    box.addView(a.sliderRow("Fuerza", 100, (c.strength * 100).toInt(), { "$it%" }) { c.strength = it / 100f; a.saveSettings() })
    box.addView(a.button("Cuantizar la grabación actual") { c.requantize(); a.toast("Grabación cuantizada a ${c.grid.label}") }, lp(WRAP, WRAP).apply { topMargin = a.dpi(8) })

    box.addView(a.section("Longitud del loop de grabación"))
    box.addView(a.chips(listOf(1, 2, 4, 8, 16), c.loopBars, { if (it == 1) "1 compás" else "$it compases" }) { c.loopBars = it; a.saveSettings() })
    return a.scrollOf(box)
}

// ---------------- RECORDINGS ----------------
fun buildRecPanel(a: MainActivity): View {
    val c = a.engine.core
    val box = a.vbox(12)
    box.addView(a.section("Grabación actual"))
    val n = synchronized(c.loopLock) { c.loop.size }
    box.addView(a.label("$n golpes · ${c.loopBars} compás(es) de ${c.meter} · ${c.bpm.roundToInt()} BPM", 14f, T.TEXT2))
    box.addView(a.label("Pulsa ● arriba para grabar. Cada vuelta del loop se suma (overdub).", 12.5f, T.TEXT2))
    val row = a.hbox(); row.setPadding(0, a.dpi(8), 0, 0)
    fun b(t: String, f: () -> Unit) { row.addView(a.button(t, sizeSp = 13f) { f() }); row.gap(6) }
    b("Guardar") { saveRecordingDialog(a) }
    b("Deshacer toma") { c.undoLastTake(); a.showTab(MainActivity.Tab.REC) }
    b("Borrar") { synchronized(c.loopLock) { c.loop.clear() }; a.showTab(MainActivity.Tab.REC) }
    box.addView(android.widget.HorizontalScrollView(a).apply { addView(row) })
    val row2 = a.hbox(); row2.setPadding(0, a.dpi(8), 0, 0)
    row2.addView(a.button("Exportar MIDI", sizeSp = 13f) { exportMidi(a, currentRecording(a, "loop")) }); row2.gap(6)
    row2.addView(a.button("Exportar WAV", sizeSp = 13f) { exportWav(a) })
    box.addView(row2)

    box.addView(a.section("Grabaciones guardadas"))
    val list = a.vbox(); box.addView(list)
    a.bg.execute {
        val items = a.recs.list()
        a.main.post {
            if (items.isEmpty()) list.addView(a.label("Todavía no hay grabaciones guardadas.", 14f, T.TEXT2))
            for (r in items) {
                val card = a.vbox(10); card.background = a.rounded(T.BTN, T.BORDER, 14f)
                card.addView(a.label(r.name, 16f, T.TEXT, true))
                card.addView(a.label("${r.events.size} golpes · ${r.bars} compás(es) ${r.meter} · ${r.bpm.roundToInt()} BPM", 12.5f, T.TEXT2))
                val rr = a.hbox(); rr.setPadding(0, a.dpi(6), 0, 0)
                rr.addView(a.button("Cargar", sizeSp = 12.5f) { loadRecording(a, r) }); rr.gap(6)
                rr.addView(a.button("MIDI", sizeSp = 12.5f) { exportMidi(a, r) }); rr.gap(6)
                rr.addView(a.button("Borrar", sizeSp = 12.5f) { a.recs.delete(r); a.showTab(MainActivity.Tab.REC) })
                card.addView(rr)
                list.addView(card, lp(MATCH, WRAP).apply { bottomMargin = a.dpi(8) })
            }
        }
    }
    return a.scrollOf(box)
}

private fun currentRecording(a: MainActivity, name: String): Recording {
    val c = a.engine.core
    val ev = synchronized(c.loopLock) { c.loop.map { com.drumlivepro.app.audio.NoteEvent(it.tick, it.pad, it.note, it.vel) } }
    return Recording(name, c.bpm, c.meter, c.loopBars, a.kit?.id, ev, System.currentTimeMillis())
}

private fun saveRecordingDialog(a: MainActivity) {
    if (synchronized(a.engine.core.loopLock) { a.engine.core.loop.isEmpty() }) { a.toast("No hay nada grabado"); return }
    val inp = a.input("Nombre", "Ritmo ${a.recs.list().size + 1}")
    a.panelDialog("Guardar grabación", inp, widthFraction = 0.8f, actions = listOf("Guardar" to { d ->
        val nm = inp.text.toString().trim().ifEmpty { "Ritmo" }
        a.recs.save(currentRecording(a, nm)); d.dismiss(); a.toast("Guardado: $nm"); a.showTab(MainActivity.Tab.REC)
    }))
}

private fun loadRecording(a: MainActivity, r: Recording) {
    val c = a.engine.core
    c.bpm = r.bpm; c.meter = r.meter; c.loopBars = r.bars
    synchronized(c.loopLock) { c.loop.clear(); r.events.forEach { c.loop.add(com.drumlivepro.app.audio.NoteEvent(it.tick, it.pad, it.note, it.vel)) }; c.loop.sortBy { it.tick } }
    r.kitId?.let { id -> if (id != a.kit?.id) a.kits.load(id)?.let { a.selectKit(it) } }
    a.regenPatterns(); a.saveSettings()
    if (!c.running) a.startTransport(false)
    a.toast("Cargado: ${r.name}")
}

private fun exportMidi(a: MainActivity, r: Recording) {
    if (r.events.isEmpty()) { a.toast("No hay nada grabado"); return }
    val fn = r.name.replace(Regex("[^A-Za-z0-9 _-]"), "_") + ".mid"
    runCatching { a.recs.exportPublic(fn, "audio/midi") { it.write(a.recs.toMidi(r, 1)) } }
        .onSuccess { a.toast("MIDI exportado en $it") }.onFailure { a.toast("Error: ${it.message}") }
}

private fun exportWav(a: MainActivity) {
    val c = a.engine.core
    if (synchronized(c.loopLock) { c.loop.isEmpty() }) { a.toast("No hay nada grabado"); return }
    a.toast("Renderizando WAV…")
    val fxCopy = FxParams().also { f ->
        val s = a.fx
        f.distOn = s.distOn; f.distBand = s.distBand; f.distLowPass = s.distLowPass; f.distIntensity = s.distIntensity
        f.revOn = s.revOn; f.revTime = s.revTime; f.revHighFreq = s.revHighFreq; f.revMix = s.revMix
        f.echoOn = s.echoOn; f.echoDelay = s.echoDelay; f.echoFeedback = s.echoFeedback; f.echoMix = s.echoMix; f.master = s.master
    }
    a.bg.execute {
        val fn = "DrumLivePro_${System.currentTimeMillis() / 1000}.wav"
        val r = runCatching { a.recs.exportPublic(fn, "audio/wav") { WavExporter.render(c, fxCopy, 4, it.buffered()) ; } }
        a.main.post { r.onSuccess { a.toast("WAV exportado en $it") }.onFailure { a.toast("Error: ${it.message}") } }
    }
}

// ---------------- MIDI ----------------
@SuppressLint("MissingPermission")
fun buildMidiPanel(a: MainActivity): View {
    val box = a.vbox(12)
    box.addView(a.section("Controladores MIDI"))
    if (a.midi.manager == null) box.addView(a.label("Este dispositivo no soporta MIDI.", 14f, T.RED))
    box.addView(a.switchRow("MIDI activo", a.prefs.getBoolean("midiOn", true)) { on ->
        a.prefs.edit().putBoolean("midiOn", on).apply(); if (on) a.midi.start() else a.midi.stop(); a.updateStatus()
    })
    val names = a.midi.connectedNames
    box.addView(a.label(if (names.isEmpty()) "Ningún controlador conectado. Los USB se conectan solos; los Bluetooth hay que buscarlos." else "Conectados: ${names.joinToString()}", 14f, if (names.isEmpty()) T.TEXT2 else T.MINT))
    val row = a.hbox(); row.setPadding(0, a.dpi(8), 0, 0)
    row.addView(a.button(if (a.midi.scanning) "Buscando…" else "Buscar MIDI Bluetooth") { a.startBleScan() }); row.gap(8)
    row.addView(a.button("Aprender", a.learnState != 0) { a.toggleLearn() })
    box.addView(row)
    val found = a.vbox(); box.addView(found)
    val seen = HashSet<String>()
    a.midi.onScanResult = { dev, nm ->
        if (seen.add(dev.address)) found.addView(a.button("Conectar: $nm") { a.midi.connectBluetooth(dev); a.toast("Conectando $nm…") }, lp(MATCH, WRAP).apply { topMargin = a.dpi(6) })
    }
    box.addView(a.section("Cómo asignar (Aprender)"))
    box.addView(a.label("1. Pulsa «Aprender».  2. Toca el pad en pantalla.  3. Golpea la tecla o pad de tu controlador. Repite con otros pads y pulsa «Aprender» otra vez para terminar. Si una tecla no está asignada, suena el pad que tenga esa misma nota MIDI.", 13.5f, T.TEXT2))
    box.addView(a.section("Asignaciones"))
    if (a.learnMap.isEmpty()) box.addView(a.label("Sin asignaciones manuales (se usa la nota MIDI de cada pad).", 13.5f, T.TEXT2))
    for ((note, pad) in a.learnMap.toSortedMap()) {
        val r = a.hbox(2)
        val pn = a.pads.getOrNull(pad)?.displayName ?: "?"
        r.addView(a.label("Tecla $note (${Instruments.noteName(note)})  →  Pad ${pad + 1} · $pn", 14f), lp(0, WRAP, 1f))
        r.addView(a.button("Quitar", sizeSp = 12f) { a.learnMap.remove(note); a.saveSettings(); a.showTab(MainActivity.Tab.MIDI) })
        box.addView(r)
    }
    if (a.learnMap.isNotEmpty()) box.addView(a.button("Borrar todas") { a.learnMap.clear(); a.saveSettings(); a.showTab(MainActivity.Tab.MIDI) }, lp(WRAP, WRAP).apply { topMargin = a.dpi(8) })
    return a.scrollOf(box)
}

// ---------------- MORE ----------------
fun buildMorePanel(a: MainActivity): View {
    val box = a.vbox(12)
    box.addView(a.section("Tema de color"))
    box.addView(a.label("Elige entre 8 temas; el último es el azul original.", 12.5f, T.TEXT2))
    box.addView(a.chips(THEMES.indices.toList(), T.index, { THEMES[it].name }) { a.applyColorTheme(it) })
    box.addView(a.section("Sonido"))
    box.addView(a.sliderRow("Volumen general", 100, (a.fx.master * 100).toInt(), { "$it%" }) { a.fx.master = it / 100f; a.saveSettings() })
    box.addView(a.switchRow("Reverb", a.fx.revOn) { a.fx.revOn = it; a.saveSettings() })
    box.addView(a.sliderRow("Reverb (mezcla)", 100, (a.fx.revMix * 100).toInt(), { "$it%" }) { a.fx.revMix = it / 100f; a.saveSettings() })
    box.addView(a.button("Efectos (FX)…") { showFxDialog(a) }, lp(WRAP, WRAP).apply { topMargin = a.dpi(6) })
    box.addView(a.section("Pads"))
    val rows = a.hbox()
    rows.addView(a.label("Filas de pads: ${a.pads.size / 4}", 15f), lp(0, WRAP, 1f))
    rows.addView(a.button("+ Fila") { a.addRow(); a.showTab(MainActivity.Tab.MORE) }); rows.gap(6)
    rows.addView(a.button("− Fila") { a.removeRow(); a.showTab(MainActivity.Tab.MORE) })
    box.addView(rows)
    box.addView(a.switchRow("Velocidad según dónde toques el pad (arriba = fuerte)", a.padView.velocityByPosition) {
        a.padView.velocityByPosition = it; a.prefs.edit().putBoolean("velPos", it).apply()
    })
    box.addView(a.button("Reasignar íconos y notas automáticamente") {
        a.kit?.let { k -> k.pads.forEach { it.icon = null; it.name = null }; a.applyPads(true); a.toast("Íconos automáticos según la nota MIDI") }
    }, lp(WRAP, WRAP).apply { topMargin = a.dpi(6) })
    box.addView(a.section("Latencia de audio"))
    box.addView(a.label("Mínimo = menos retraso al tocar; si se corta o suena entrecortado, sube a Normal o Seguro.", 12.5f, T.TEXT2))
    box.addView(a.chips(listOf(1, 2, 4), a.engine.bufferMultiplier, { when (it) { 1 -> "Mínimo"; 2 -> "Normal"; else -> "Seguro" } }) {
        a.prefs.edit().putInt("bufMul", it).apply(); a.engine.bufferMultiplier = it; a.engine.restart()
        a.toast("Buffer de audio: ${a.engine.latencyMs} ms")
    })
    box.addView(a.section("Información"))
    box.addView(a.label("Latencia de salida aprox.: ${a.engine.latencyMs} ms · ${a.engine.sampleRate} Hz", 13.5f, T.TEXT2))
    box.addView(a.label("${a.appTitle} · Kits incluidos basados en GeneralUser GS v2 de S. Christian Collins (uso libre).", 13f, T.TEXT2))
    return a.scrollOf(box)
}

