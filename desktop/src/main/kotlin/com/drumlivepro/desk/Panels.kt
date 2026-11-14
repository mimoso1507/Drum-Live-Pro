package com.drumlivepro.desk

import com.drumlivepro.app.audio.FxParams
import com.drumlivepro.app.audio.NoteEvent
import com.drumlivepro.app.audio.SoundFontManager
import com.drumlivepro.app.audio.WavExporter
import com.drumlivepro.app.model.Instruments
import com.drumlivepro.app.model.Kit
import com.drumlivepro.app.model.MidiWriter
import com.drumlivepro.app.model.Recording
import com.drumlivepro.app.seq.Grid
import com.drumlivepro.app.seq.Meter
import org.json.JSONObject
import java.awt.Desktop
import java.awt.FlowLayout
import java.awt.GridLayout
import java.io.File
import java.io.OutputStream
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.roundToInt

class RecordingStore(private val dir: File) {
    init { dir.mkdirs() }
    private fun file(name: String) = File(dir, name.replace(Regex("[^A-Za-z0-9 ._-]"), "_") + ".json")
    fun list(): List<Recording> = (dir.listFiles()?.toList() ?: emptyList())
        .mapNotNull { runCatching { Recording.fromJson(JSONObject(it.readText())) }.getOrNull() }
        .sortedByDescending { it.created }
    fun save(r: Recording) = file(r.name).writeText(r.toJson().toString())
    fun delete(r: Recording) = file(r.name).delete()

    /** Writes to Documents\DrumLivePro. Returns the full path. */
    fun exportPublic(fileName: String, write: (OutputStream) -> Unit): String {
        val d = Paths.export.apply { mkdirs() }
        val f = File(d, fileName)
        f.outputStream().buffered().use(write)
        return f.absolutePath
    }
}

private fun grid(cols: Int) = JPanel(GridLayout(0, cols, dpi(6), dpi(6))).apply { isOpaque = false; alignmentX = 0f }

// ---------------- menu ----------------
fun showMainMenu(a: App) {
    val v = VBox(2)
    lateinit var d: Dlg
    v.add(Label(a.statusText, 13f, T.MINT))
    v.add(section("Pantallas"))
    val g = grid(4)
    for (t in App.Tab.values()) g.add(RButton(t.title, t == a.tab) { d.dismiss(); a.showTab(t) }.apply { center = false })
    v.add(g)
    v.add(section("Pads"))
    val r = grid(4)
    fun act(text: String, f: () -> Unit) = r.add(RButton(text, size = 14f) { d.dismiss(); f() }.apply { center = false })
    act("+ Fila") { a.addRow() }
    act("− Fila") { a.removeRow() }
    act("Reverb") { showQuickReverb(a) }
    act("Importar SF2") { a.pickSf2() }
    v.add(r)
    v.add(section("Ventana"))
    val w = grid(4)
    w.add(RButton(if (a.fullScreen) "Salir de pantalla completa (ESC)" else "Pantalla completa (F11)", size = 14f) { d.dismiss(); a.setFullScreen(!a.fullScreen) }.apply { center = false })
    w.add(RButton("Cerrar la app", size = 14f) { a.quit() }.apply { center = false })
    v.add(w)
    v.add(section("Tema de color"))
    v.add(chips(THEMES.indices.toList(), T.index, { THEMES[it].name }) { d.dismiss(); a.applyColorTheme(it) })
    d = Dlg(a.stage, "Drum Live Pro v${a.versionName}", v, widthFraction = 0.7f)
}

fun showQuickReverb(a: App) {
    val v = VBox(4)
    v.add(switchRow("Reverb activa", a.fx.revOn) { a.fx.revOn = it; a.saveSettings() })
    v.add(sliderRow("Cantidad (mezcla)", 100, (a.fx.revMix * 100).toInt(), { "$it%" }) { a.fx.revMix = it / 100f; a.saveSettings() })
    v.add(sliderRow("Tamaño (tiempo)", 4900, (a.fx.revTime - 100).toInt(), { "${it + 100} ms" }) { a.fx.revTime = it + 100f; a.saveSettings() })
    Dlg(a.stage, "Reverb", v, widthFraction = 0.5f)
}

// ---------------- KITS ----------------
fun buildKitsPanel(a: App): JComponent {
    val box = VBox(10)
    val head = HBox()
    head.add(Label("Baterías disponibles", 17f, T.TEXT, true)); head.add(RButton("Importar SF2", size = 13f) { a.pickSf2() })
    box.add(head)
    box.add(Label("Clic para cargar · clic derecho para editar, duplicar o borrar · también puedes arrastrar archivos .sf2 a la ventana", 12.5f, T.TEXT2))
    val cards = WrapPanel(FlowLayout.LEFT)
    box.add(cards)
    box.add(section("Archivos SF2 instalados"))
    val sfList = VBox()
    box.add(sfList)
    a.bg.execute {
        val all = a.kits.all()
        val files = a.sfm.files().map { it.name to a.sfm.kitPresets(it.name).size }
        a.post {
            for (k in all) cards.add(KitCard(k.name, k.icon, k.color, k.custom, k.id == a.kit?.id,
                onClick = { a.bg.execute { val loaded = a.kits.load(k.id) ?: k; a.post { a.selectKit(loaded); a.toast("Kit: ${k.name}") } } },
                onMenu = { kitMenu(a, k) }))
            cards.add(KitCard(null, null, 0, false, false, onClick = { showKitEditor(a, null) }, onMenu = {}))
            for ((name, n) in files) {
                val row = HBox(4)
                row.add(Label("$name  ·  $n kits", 14f, T.TEXT2))
                if (name != SoundFontManager.BUNDLED) row.add(RButton("Quitar", size = 12f) { a.sfm.delete(name); a.toast("SF2 eliminado"); a.showTab(App.Tab.KITS) })
                sfList.add(row)
            }
            sfList.add(Label("El kit incluido usa sonidos de GeneralUser GS (S. Christian Collins).", 11.5f, T.TEXT2))
            box.revalidate(); box.repaint()
        }
    }
    return scrollOf(box)
}

private fun kitMenu(a: App, k: Kit) {
    val v = VBox(4)
    lateinit var d: Dlg
    fun opt(t: String, f: () -> Unit) { v.add(RButton(t) { d.dismiss(); f() }.apply { center = false }); v.gap(8) }
    if (k.custom) {
        opt("Editar kit") { showKitEditor(a, a.kits.load(k.id) ?: k) }
        opt("Borrar kit") { a.kits.delete(k); a.toast("Kit borrado"); a.showTab(App.Tab.KITS) }
    } else {
        opt("Duplicar como kit personalizado") {
            val base = a.kits.load(k.id) ?: k
            showKitEditor(a, a.kits.newCustom(base.name + " (mío)", base.icon, base.color, base.source, base.pads.map { it.copy() }.toMutableList()), isNew = true)
        }
        opt("Restablecer mapeo original") { a.kits.delete(k); if (a.kit?.id == k.id) a.selectKit(a.kits.load(k.id)!!); a.toast("Mapeo restablecido") }
    }
    d = Dlg(a.stage, k.name, v, widthFraction = 0.45f)
}

// ---------------- RHYTHM ----------------
fun buildRhythmPanel(a: App): JComponent {
    val c = a.engine.core
    val box = VBox(12)
    box.add(section("Tempo"))
    val bpmLabel = javax.swing.JLabel("${c.bpm.roundToInt()} BPM", javax.swing.SwingConstants.CENTER).apply { font = serif(26f, true); foreground = col(T.TEXT) }
    lateinit var seek: Slider
    fun setBpm(v: Double) { c.bpm = v.coerceIn(30.0, 300.0); bpmLabel.text = "${c.bpm.roundToInt()} BPM"; seek.value = c.bpm.roundToInt() - 30; a.saveSettings() }
    val bpmRow = HBox()
    bpmRow.add(RButton("−", size = 18f) { setBpm(c.bpm - 1) }); bpmRow.gap(10)
    bpmRow.add(bpmLabel.apply { maximumSize = java.awt.Dimension(Int.MAX_VALUE, preferredSize.height) }); bpmRow.gap(10)
    bpmRow.add(RButton("+", size = 18f) { setBpm(c.bpm + 1) })
    val taps = ArrayList<Long>()
    bpmRow.gap(10); bpmRow.add(RButton("Tap", size = 16f) {
        val now = System.currentTimeMillis(); if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear(); taps += now
        if (taps.size >= 2) { val iv = (taps.last() - taps.first()).toDouble() / (taps.size - 1); setBpm(60000.0 / iv) }
        while (taps.size > 6) taps.removeAt(0)
    })
    box.add(bpmRow)
    seek = Slider(270, c.bpm.roundToInt() - 30) { setBpm(it + 30.0) }
    box.add(seek)

    box.add(section("Compás"))
    box.add(chips(Meter.COMMON, c.meter, { it.toString() }) { c.meter = it; a.regenPatterns(); a.saveSettings() })

    box.add(section("Metrónomo"))
    box.add(switchRow("Metrónomo activo", c.metronomeOn) { c.metronomeOn = it; a.refreshTop(); a.saveSettings() })
    box.add(switchRow("Cuenta previa (1 compás) al grabar", a.prefs.getBoolean("countIn", true)) { a.prefs.edit().putBoolean("countIn", it).apply() })
    box.add(sliderRow("Volumen del metrónomo", 100, (c.metronomeVolume * 100).toInt(), { "$it%" }) { c.metronomeVolume = it / 100f; a.saveSettings() })

    box.add(section("Cuantización"))
    box.add(Label("Resolución de la rejilla (el compás se elige arriba por separado):", 13f, T.TEXT2))
    box.add(chips(Grid.values().toList(), c.grid, { it.label }) { c.grid = it; a.saveSettings() })
    box.add(switchRow("Cuantizar mientras grabo", c.quantizeOnRecord) { c.quantizeOnRecord = it; a.saveSettings() })
    box.add(sliderRow("Swing", 100, (c.swing * 100).toInt(), { if (it == 0) "Recto" else "$it%" }) { c.swing = it / 100f; a.saveSettings() })
    box.add(sliderRow("Fuerza", 100, (c.strength * 100).toInt(), { "$it%" }) { c.strength = it / 100f; a.saveSettings() })
    box.gap(8)
    box.add(RButton("Cuantizar la grabación actual") { c.requantize(); a.toast("Grabación cuantizada a ${c.grid.label}") })

    box.add(section("Longitud del loop de grabación"))
    box.add(chips(listOf(1, 2, 4, 8, 16), c.loopBars, { if (it == 1) "1 compás" else "$it compases" }) { c.loopBars = it; a.saveSettings() })
    return scrollOf(box)
}

// ---------------- RECORDINGS ----------------
fun buildRecPanel(a: App): JComponent {
    val c = a.engine.core
    val box = VBox(12)
    box.add(section("Grabación actual"))
    val n = synchronized(c.loopLock) { c.loop.size }
    box.add(Label("$n golpes · ${c.loopBars} compás(es) de ${c.meter} · ${c.bpm.roundToInt()} BPM", 14f, T.TEXT2))
    box.add(Label("Pulsa ● arriba para grabar. Cada vuelta del loop se suma (overdub).", 12.5f, T.TEXT2))
    val row = WrapPanel()
    row.add(RButton("Guardar", size = 13f) { saveRecordingDialog(a) })
    row.add(RButton("Deshacer toma", size = 13f) { c.undoLastTake(); a.showTab(App.Tab.REC) })
    row.add(RButton("Borrar", size = 13f) { synchronized(c.loopLock) { c.loop.clear() }; a.showTab(App.Tab.REC) })
    row.add(RButton("Exportar MIDI", size = 13f) { exportMidi(a, currentRecording(a, "loop")) })
    row.add(RButton("Exportar WAV", size = 13f) { exportWav(a) })
    row.add(RButton("Abrir carpeta de exportación", size = 13f) {
        Paths.export.mkdirs(); runCatching { Desktop.getDesktop().open(Paths.export) }.onFailure { a.toast(Paths.export.absolutePath) }
    })
    box.add(row)
    box.add(section("Grabaciones guardadas"))
    val list = VBox(); box.add(list)
    a.bg.execute {
        val items = a.recs.list()
        a.post {
            if (items.isEmpty()) list.add(Label("Todavía no hay grabaciones guardadas.", 14f, T.TEXT2))
            for (r in items) {
                val card = Card(T.BTN, T.BORDER, 10)
                card.add(Label(r.name, 16f, T.TEXT, true))
                card.add(Label("${r.events.size} golpes · ${r.bars} compás(es) ${r.meter} · ${r.bpm.roundToInt()} BPM", 12.5f, T.TEXT2))
                val rr = HBox(); rr.border = javax.swing.BorderFactory.createEmptyBorder(dpi(6), 0, 0, 0)
                rr.add(RButton("Cargar", size = 12.5f) { loadRecording(a, r) }); rr.gap(6)
                rr.add(RButton("MIDI", size = 12.5f) { exportMidi(a, r) }); rr.gap(6)
                rr.add(RButton("Borrar", size = 12.5f) { a.recs.delete(r); a.showTab(App.Tab.REC) }); rr.glue()
                card.add(rr)
                list.add(card); list.gap(8)
            }
            box.revalidate(); box.repaint()
        }
    }
    return scrollOf(box)
}

private fun currentRecording(a: App, name: String): Recording {
    val c = a.engine.core
    val ev = synchronized(c.loopLock) { c.loop.map { NoteEvent(it.tick, it.pad, it.note, it.vel) } }
    return Recording(name, c.bpm, c.meter, c.loopBars, a.kit?.id, ev, System.currentTimeMillis())
}

private fun saveRecordingDialog(a: App) {
    if (synchronized(a.engine.core.loopLock) { a.engine.core.loop.isEmpty() }) { a.toast("No hay nada grabado"); return }
    val inp = Input("Nombre", "Ritmo ${a.recs.list().size + 1}")
    val v = VBox(); v.add(inp)
    Dlg(a.stage, "Guardar grabación", v, widthFraction = 0.5f, actions = listOf("Guardar" to { d ->
        val nm = inp.text.trim().ifEmpty { "Ritmo" }
        a.recs.save(currentRecording(a, nm)); d.dismiss(); a.toast("Guardado: $nm"); a.showTab(App.Tab.REC)
    }))
    a.post { inp.requestFocusInWindow(); inp.selectAll() }
}

private fun loadRecording(a: App, r: Recording) {
    val c = a.engine.core
    c.bpm = r.bpm; c.meter = r.meter; c.loopBars = r.bars
    synchronized(c.loopLock) { c.loop.clear(); r.events.forEach { c.loop.add(NoteEvent(it.tick, it.pad, it.note, it.vel)) }; c.loop.sortBy { it.tick } }
    r.kitId?.let { id -> if (id != a.kit?.id) a.kits.load(id)?.let { a.selectKit(it) } }
    a.regenPatterns(); a.saveSettings()
    if (!c.running) a.startTransport(false)
    a.toast("Cargado: ${r.name}")
}

private fun exportMidi(a: App, r: Recording) {
    if (r.events.isEmpty()) { a.toast("No hay nada grabado"); return }
    val fn = r.name.replace(Regex("[^A-Za-z0-9 _-]"), "_") + ".mid"
    runCatching { a.recs.exportPublic(fn) { it.write(MidiWriter.toMidi(r, 1)) } }
        .onSuccess { a.toast("MIDI exportado en $it") }.onFailure { a.toast("Error: ${it.message}") }
}

private fun exportWav(a: App) {
    val c = a.engine.core
    if (synchronized(c.loopLock) { c.loop.isEmpty() }) { a.toast("No hay nada grabado"); return }
    a.toast("Renderizando WAV…")
    val s = a.fx
    val fxCopy = FxParams().also { f ->
        f.distOn = s.distOn; f.distBand = s.distBand; f.distLowPass = s.distLowPass; f.distIntensity = s.distIntensity
        f.revOn = s.revOn; f.revTime = s.revTime; f.revHighFreq = s.revHighFreq; f.revMix = s.revMix
        f.echoOn = s.echoOn; f.echoDelay = s.echoDelay; f.echoFeedback = s.echoFeedback; f.echoMix = s.echoMix; f.master = s.master
    }
    a.bg.execute {
        val fn = "DrumLivePro_${System.currentTimeMillis() / 1000}.wav"
        val r = runCatching { a.recs.exportPublic(fn) { WavExporter.render(c, fxCopy, 4, it) } }
        a.post { r.onSuccess { a.toast("WAV exportado en $it") }.onFailure { a.toast("Error: ${it.message}") } }
    }
}

// ---------------- MIDI ----------------
fun buildMidiPanel(a: App): JComponent {
    val box = VBox(12)
    box.add(section("Controladores MIDI"))
    if (!a.midi.available) box.add(Label("MIDI no disponible en este equipo.", 14f, T.RED))
    box.add(switchRow("MIDI activo", a.prefs.getBoolean("midiOn", true)) { on ->
        a.prefs.edit().putBoolean("midiOn", on).apply(); if (on) a.midi.start() else a.midi.stop(); a.updateStatus()
    })
    val names = a.midi.connectedNames
    box.add(Label(if (names.isEmpty()) "Ningún controlador conectado. Los controladores USB se detectan solos al enchufarlos."
        else "Conectados: ${names.joinToString()}", 14f, if (names.isEmpty()) T.TEXT2 else T.MINT))
    val row = HBox(); row.border = javax.swing.BorderFactory.createEmptyBorder(dpi(8), 0, 0, 0)
    row.add(RButton("Buscar de nuevo") { a.bg.execute { a.midi.rescan(); a.post { a.updateStatus(); a.showTab(App.Tab.MIDI) } } }); row.gap(8)
    row.add(RButton("Aprender", a.learnState != 0) { a.toggleLearn() }); row.glue()
    box.add(row)
    box.add(Label("Bluetooth MIDI: en Windows conéctalo con una app puente (por ejemplo MIDIberry + loopMIDI) y aparecerá aquí como un puerto MIDI más.", 12.5f, T.TEXT2))
    box.add(section("Cómo asignar (Aprender)"))
    box.add(Label("1. Pulsa «Aprender».  2. Haz clic en el pad en pantalla (o su tecla).  3. Golpea la tecla o pad de tu controlador. Repite con otros pads y pulsa «Aprender» otra vez para terminar. Si una tecla no está asignada, suena el pad que tenga esa misma nota MIDI.", 13.5f, T.TEXT2))
    box.add(section("Asignaciones"))
    if (a.learnMap.isEmpty()) box.add(Label("Sin asignaciones manuales (se usa la nota MIDI de cada pad).", 13.5f, T.TEXT2))
    for ((note, pad) in a.learnMap.toSortedMap()) {
        val r = HBox(2)
        val pn = a.pads.getOrNull(pad)?.displayName ?: "?"
        r.add(Label("Tecla $note (${Instruments.noteName(note)})  →  Pad ${pad + 1} · $pn", 14f))
        r.add(RButton("Quitar", size = 12f) { a.learnMap.remove(note); a.saveSettings(); a.showTab(App.Tab.MIDI) })
        box.add(r)
    }
    if (a.learnMap.isNotEmpty()) { box.gap(8); box.add(RButton("Borrar todas") { a.learnMap.clear(); a.saveSettings(); a.showTab(App.Tab.MIDI) }) }
    return scrollOf(box)
}

// ---------------- MORE ----------------
fun buildMorePanel(a: App): JComponent {
    val box = VBox(12)
    box.add(section("Tema de color"))
    box.add(Label("Elige entre 8 temas; el último es el azul original.", 12.5f, T.TEXT2))
    box.add(chips(THEMES.indices.toList(), T.index, { THEMES[it].name }) { a.applyColorTheme(it) })
    box.add(section("Pantalla"))
    box.add(switchRow("Pantalla completa (ESC para salir · F11 para volver)", a.fullScreen) { a.setFullScreen(it) })
    box.add(section("Sonido"))
    box.add(sliderRow("Volumen general", 100, (a.fx.master * 100).toInt(), { "$it%" }) { a.fx.master = it / 100f; a.saveSettings() })
    box.add(switchRow("Reverb", a.fx.revOn) { a.fx.revOn = it; a.saveSettings() })
    box.add(sliderRow("Reverb (mezcla)", 100, (a.fx.revMix * 100).toInt(), { "$it%" }) { a.fx.revMix = it / 100f; a.saveSettings() })
    box.gap(6); box.add(RButton("Efectos (FX)…") { showFxDialog(a) })
    box.add(section("Pads"))
    val rows = HBox()
    rows.add(Label("Filas de pads: ${a.pads.size / 4}", 15f))
    rows.add(RButton("+ Fila") { a.addRow(); a.showTab(App.Tab.MORE) }); rows.gap(6)
    rows.add(RButton("− Fila") { a.removeRow(); a.showTab(App.Tab.MORE) })
    box.add(rows)
    box.add(switchRow("Velocidad según dónde hagas clic en el pad (arriba = fuerte)", a.padView.velocityByPosition) {
        a.padView.velocityByPosition = it; a.prefs.edit().putBoolean("velPos", it).apply()
    })
    box.add(switchRow("Mostrar la tecla del teclado en cada pad", a.padView.showKeys) {
        a.padView.showKeys = it; a.padView.repaint(); a.prefs.edit().putBoolean("showKeys", it).apply()
    })
    box.add(Label("Teclado: filas 1 2 3 4 / Q W E R / A S D F / Z X C V (y 5 6 7 8 / T Y U I / G H J K / B N M , para más filas). Mayúsculas = golpe fuerte. Barra espaciadora = reproducir/detener. Clic derecho en un pad = editarlo.", 12.5f, T.TEXT2))
    box.gap(6)
    box.add(RButton("Reasignar íconos y notas automáticamente") {
        a.kit?.let { k -> k.pads.forEach { it.icon = null; it.name = null }; a.applyPads(true); a.toast("Íconos automáticos según la nota MIDI") }
    })
    box.add(section("Latencia de audio"))
    box.add(Label("Mínimo = menos retraso al tocar; si se corta o suena entrecortado, sube a Normal o Seguro.", 12.5f, T.TEXT2))
    box.add(chips(listOf(1, 2, 4), a.engine.bufferMultiplier, { when (it) { 1 -> "Mínimo"; 2 -> "Normal"; else -> "Seguro" } }) {
        a.prefs.edit().putInt("bufMul", it).apply(); a.engine.bufferMultiplier = it; a.engine.restart()
        a.toast(a.engine.error ?: "Buffer de audio: ${a.engine.latencyMs} ms")
    })
    box.add(section("Información"))
    box.add(Label(a.engine.error ?: "Latencia de salida aprox.: ${a.engine.latencyMs} ms · ${a.engine.sampleRate} Hz", 13.5f, T.TEXT2))
    box.add(Label("Datos de la app: ${Paths.data.absolutePath}", 12.5f, T.TEXT2))
    box.add(Label("${a.appTitle} para Windows · Kits incluidos basados en GeneralUser GS v2 de S. Christian Collins (uso libre).", 13f, T.TEXT2))
    return scrollOf(box)
}
