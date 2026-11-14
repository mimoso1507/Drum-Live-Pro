package com.drumlivepro.desk

import com.drumlivepro.app.audio.FxParams
import com.drumlivepro.app.audio.MAX_PADS
import com.drumlivepro.app.audio.PadSound
import com.drumlivepro.app.audio.SoundFontManager
import com.drumlivepro.app.model.Instruments
import com.drumlivepro.app.model.Kit
import com.drumlivepro.app.model.KitStore
import com.drumlivepro.app.model.PadConfig
import com.drumlivepro.app.seq.Grid
import com.drumlivepro.app.seq.Meter
import com.drumlivepro.app.seq.PatternGen
import com.drumlivepro.app.ui.Glyph
import org.json.JSONObject
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.concurrent.Executors
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.text.JTextComponent

class App(val frame: JFrame, val versionName: String) {
    val appTitle get() = "Drum Live Pro v$versionName"

    val prefs = Prefs(File(Paths.data, "settings.json"))
    val fx = FxParams()
    val engine = AudioEngine(fx)
    val sfm = SoundFontManager(Paths.data)
    val kits = KitStore(Paths.data, sfm)
    val recs = RecordingStore(File(Paths.data, "recordings"))
    val midi = MidiInput()
    val bg = Executors.newSingleThreadExecutor { r -> Thread(r, "DrumBg").apply { isDaemon = true } }
    val stage = Stage()

    var kit: Kit? = null
    val pads: MutableList<PadConfig> get() = kit?.pads ?: mutableListOf()
    @Volatile private var padNotes = IntArray(0)

    val learnMap = HashMap<Int, Int>()
    var learnState = 0
    var learnPad = -1
    private var learnPendingNote = -1

    var autoPlayMode = false
    var tab = Tab.PADS

    enum class Tab(val title: String) { PADS("Pads"), KIT("Batería"), KITS("Kits"), RHYTHM("Ritmo"), REC("Grabar"), MIDI("MIDI"), MORE("Más") }

    val padView = PadGridView()
    val kitView = VirtualKitView()
    private val content = JPanel(BorderLayout()).apply { isOpaque = false }
    private lateinit var display: JLabel
    private lateinit var btnBack: GlyphButton
    var statusText = ""
    private lateinit var btnAuto: GlyphButton
    private lateinit var btnMetro: GlyphButton
    private lateinit var btnRec: GlyphButton
    private lateinit var btnPlay: GlyphButton
    private lateinit var btnLearn: GlyphButton
    private lateinit var btnMidi: GlyphButton

    var fullScreen = true; private set

    fun post(r: () -> Unit) = SwingUtilities.invokeLater(r)
    fun postDelayed(ms: Int, r: () -> Unit) = Timer(ms) { r() }.apply { isRepeats = false; start() }
    fun toast(s: String) = stage.toast(s)

    fun start() {
        T.apply(prefs.getInt("theme", 0))
        engine.bufferMultiplier = prefs.getInt("bufMul", 2)
        loadSettings()
        padView.apply {
            hitTimes = engine.core.hitTimes
            keepAnimating = { engine.core.running }
            velocityByPosition = prefs.getBoolean("velPos", false)
            showKeys = prefs.getBoolean("showKeys", true)
            onHit = { p, v -> onPadHit(p, v) }
            onLongPress = { p -> onPadLong(p) }
        }
        kitView.apply {
            hitTimes = engine.core.hitTimes
            keepAnimating = { engine.core.running }
            onHit = { p, v -> onPadHit(p, v) }
            onLongPress = { p -> showPadEditor(this@App, p) }
        }
        frame.contentPane = stage
        stage.main = buildRoot()
        installKeys()
        installDrop()
        midi.onNote = { n, v, _ -> onMidiNote(n, v) }
        midi.onDevicesChanged = { post { updateStatus(); if (tab == Tab.MIDI) showTab(Tab.MIDI) } }
        if (prefs.getBoolean("midiOn", true)) midi.start()
        engine.start()
        engine.error?.let { postDelayed(800) { toast(it) } }

        bg.execute {
            runCatching { sfm.ensureBundled() }
            val id = prefs.getString("kit", null)
            val k = (id?.let { kits.load(it) }) ?: kits.builtInKits().firstOrNull()
            post { if (k != null) selectKit(k, save = false) else toast("No se encontraron kits") }
        }
        Timer(60) { tick() }.start()
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) { quit() }
        })
        setFullScreen(prefs.getBoolean("fullscreen", true), first = true)
    }

    fun quit() {
        saveSettings(); engine.stop(); midi.stop(); bg.shutdown()
        frame.dispose(); System.exit(0)
    }

    // ---------------- full screen / landscape window ----------------
    private fun screenBounds(): Rectangle {
        val gc = frame.graphicsConfiguration ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        return gc.bounds
    }

    /** Full screen = borderless window covering the monitor. Windowed = normal landscape (16:9) window. */
    fun setFullScreen(on: Boolean, first: Boolean = false) {
        if (!first && on == fullScreen && frame.isVisible) return
        fullScreen = on
        prefs.edit().putBoolean("fullscreen", on).apply()
        val sb = screenBounds()
        if (frame.isDisplayable) frame.dispose()
        frame.isUndecorated = on
        if (on) {
            frame.bounds = sb
        } else {
            val w = (sb.width * 0.8).toInt().coerceAtMost(sb.width)
            val h = minOf((w * 9.0 / 16).toInt(), (sb.height * 0.85).toInt())
            val ww = maxOf(w, (h * 16.0 / 9).toInt()).coerceAtMost(sb.width)
            frame.setBounds(sb.x + (sb.width - ww) / 2, sb.y + (sb.height - h) / 2, ww, h)
        }
        frame.isVisible = true
        frame.toFront(); frame.requestFocus()
        if (!first) toast(if (on) "Pantalla completa · ESC para salir" else "Ventana · F11 para volver a pantalla completa")
        else if (on) postDelayed(1200) { toast("Pantalla completa · ESC para salir · F11 para volver") }
    }

    // ---------------- settings ----------------
    private fun loadSettings() {
        val c = engine.core
        c.bpm = prefs.getFloat("bpm", 100f).toDouble()
        c.meter = Meter.parse(prefs.getString("meter", "4/4"))
        c.metronomeOn = prefs.getBoolean("metro", false)
        c.metronomeVolume = prefs.getFloat("metroVol", 0.6f)
        c.loopBars = prefs.getInt("loopBars", 2)
        c.grid = Grid.byName(prefs.getString("grid", "Q16"))
        c.swing = prefs.getFloat("swing", 0f)
        c.strength = prefs.getFloat("strength", 1f)
        c.quantizeOnRecord = prefs.getBoolean("qRec", true)
        fx.distOn = prefs.getBoolean("distOn", false); fx.distBand = prefs.getFloat("distBand", 2400f)
        fx.distLowPass = prefs.getFloat("distLp", 8000f); fx.distIntensity = prefs.getFloat("distInt", 0.15f)
        fx.revOn = prefs.getBoolean("revOn", true); fx.revTime = prefs.getFloat("revTime", 935f)
        fx.revHighFreq = prefs.getFloat("revHf", 0.338f); fx.revMix = prefs.getFloat("revMix", 0.25f)
        fx.echoOn = prefs.getBoolean("echoOn", false); fx.echoDelay = prefs.getFloat("echoDelay", 350f)
        fx.echoFeedback = prefs.getFloat("echoFb", 0.15f); fx.echoMix = prefs.getFloat("echoMix", 0.2f)
        fx.master = prefs.getFloat("master", 0.9f)
        runCatching { JSONObject(prefs.getString("learn", "{}")!!).let { o -> o.keys().forEach { k -> learnMap[k.toInt()] = o.getInt(k) } } }
    }

    fun saveSettings() {
        val c = engine.core
        prefs.edit().putFloat("bpm", c.bpm.toFloat()).putString("meter", c.meter.toString()).putBoolean("metro", c.metronomeOn)
            .putFloat("metroVol", c.metronomeVolume).putInt("loopBars", c.loopBars).putString("grid", c.grid.name)
            .putFloat("swing", c.swing).putFloat("strength", c.strength).putBoolean("qRec", c.quantizeOnRecord)
            .putBoolean("distOn", fx.distOn).putFloat("distBand", fx.distBand).putFloat("distLp", fx.distLowPass).putFloat("distInt", fx.distIntensity)
            .putBoolean("revOn", fx.revOn).putFloat("revTime", fx.revTime).putFloat("revHf", fx.revHighFreq).putFloat("revMix", fx.revMix)
            .putBoolean("echoOn", fx.echoOn).putFloat("echoDelay", fx.echoDelay).putFloat("echoFb", fx.echoFeedback).putFloat("echoMix", fx.echoMix)
            .putFloat("master", fx.master)
            .putString("learn", JSONObject().also { o -> learnMap.forEach { (k, v) -> o.put(k.toString(), v) } }.toString())
            .apply()
    }

    // ---------------- layout ----------------
    private fun buildRoot(): JComponent {
        val root = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: java.awt.Graphics) { g.color = col(T.BG); g.fillRect(0, 0, width, height) }
        }
        root.border = BorderFactory.createEmptyBorder(dpi(6), dpi(6), dpi(4), dpi(6))
        val top = HBox()
        top.border = BorderFactory.createEmptyBorder(0, 0, dpi(4), 0)
        fun gb(g: Glyph? = null, text: String? = null, tip: String, act: () -> Unit): GlyphButton =
            GlyphButton(g, null, text, act).also { it.tip = tip; top.add(it); top.gap(6) }
        btnBack = gb(Glyph.BACK, tip = "Volver a los pads") { showTab(Tab.PADS) }
        btnAuto = gb(Glyph.AUTOPLAY, tip = "AutoPlay") { toggleAutoPlay() }
        btnMetro = gb(Glyph.METRONOME, tip = "Metrónomo") { engine.core.metronomeOn = !engine.core.metronomeOn; refreshTop(); saveSettings() }
        btnRec = gb(Glyph.RECORD, tip = "Grabar") { toggleRecord() }.also { it.tint = T.RED }
        btnPlay = gb(Glyph.PLAY, tip = "Reproducir / detener (barra espaciadora)") { if (engine.core.running) stopTransport() else startTransport(false) }
        display = object : JLabel("", SwingConstants.CENTER) {
            override fun paintComponent(g: java.awt.Graphics) {
                roundBox(g.hq(), 0f, 0f, width.toFloat(), height.toFloat(), dp(14f), T.BTN, T.BORDER, dp(1.6f)); super.paintComponent(g)
            }
        }.apply {
            font = serif(14f); foreground = col(T.TEXT); border = BorderFactory.createEmptyBorder(0, dpi(14), 0, dpi(14))
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            val d = Dimension(getFontMetrics(font).stringWidth("000|0|000  12/8  300BPM") + dpi(30), dpi(44))
            preferredSize = d; maximumSize = d; minimumSize = d
            addMouseListener(object : MouseAdapter() { override fun mouseClicked(e: MouseEvent) { showTab(Tab.RHYTHM) } })
        }
        top.add(display); top.gap(6)
        gb(text = "FX", tip = "Efectos") { showFxDialog(this) }.also { it.tint = T.CYAN }
        btnLearn = gb(Glyph.LEARN, tip = "Aprender (asignar teclas MIDI)") { toggleLearn() }
        btnMidi = gb(Glyph.MIDI, tip = "MIDI") { showTab(Tab.MIDI) }
        top.glue()
        // quick screen buttons (there is room on a PC screen)
        val quick = ArrayList<GlyphButton>()
        for ((t, g) in listOf(Tab.KIT to Glyph.DRUMVIEW, Tab.KITS to Glyph.KITS, Tab.REC to Glyph.FOLDER, Tab.MORE to Glyph.GEAR)) {
            top.add(GlyphButton(g) { showTab(t) }.also { it.tip = t.title; quick += it }); top.gap(6)
        }
        top.add(GlyphButton(Glyph.MENU) { showMainMenu(this) }.also { it.tip = "Menú" })
        // On narrow windows hide the shortcut buttons so the menu button always stays visible.
        root.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                val need = dpi(60) * 15 + display.preferredSize.width
                quick.forEach { it.isVisible = root.width >= need }
            }
        })
        root.add(top, BorderLayout.NORTH)
        root.add(content, BorderLayout.CENTER)
        showTab(tab)
        refreshTop()
        return root
    }

    fun applyColorTheme(i: Int) {
        prefs.edit().putInt("theme", i).apply()
        T.apply(i)
        stage.dialogs.toList().forEach { it.dismiss() }
        stage.main = buildRoot()
        updateStatus()
    }

    fun showTab(t: Tab) {
        tab = t
        if (::btnBack.isInitialized) btnBack.isVisible = t != Tab.PADS
        content.removeAll()
        val v: JComponent = when (t) {
            Tab.PADS -> padView
            Tab.KIT -> kitView
            Tab.KITS -> buildKitsPanel(this)
            Tab.RHYTHM -> buildRhythmPanel(this)
            Tab.REC -> buildRecPanel(this)
            Tab.MIDI -> buildMidiPanel(this)
            Tab.MORE -> buildMorePanel(this)
        }
        content.add(v, BorderLayout.CENTER)
        content.revalidate(); content.repaint()
        if (v is javax.swing.JScrollPane) post { v.verticalScrollBar.value = 0; post { v.verticalScrollBar.value = 0 } }
    }

    // ---------------- kit / pads ----------------
    fun selectKit(k: Kit, save: Boolean = true) {
        kit = k
        if (save) prefs.edit().putString("kit", k.id).apply()
        applyPads()
        if (tab == Tab.KITS) showTab(Tab.KITS)
    }

    fun applyPads(persist: Boolean = false) {
        val k = kit ?: return
        if (persist) kits.save(k)
        padNotes = IntArray(k.pads.size) { k.pads[it].note }
        padView.pads = k.pads; kitView.pads = k.pads; kitView.kitColor = darken(k.color)
        updateStatus()
        val snapshot = k.pads.map { it.copy() }
        val src = k.source
        bg.execute {
            val arr = arrayOfNulls<PadSound>(MAX_PADS)
            val used = HashSet<String>()
            snapshot.forEachIndexed { i, p ->
                if (i >= MAX_PADS) return@forEachIndexed
                val ref = p.source ?: src
                val sf = sfm.get(ref.sf2)
                used += ref.sf2
                arr[i] = PadSound(sf, sf?.let { sfm.presetIndex(it, ref.bank, ref.program) } ?: -1, p.note, p.volume, p.pan)
            }
            sfm.retainOnly(used)
            engine.core.pads = arr
            post { regenPatterns(); if (arr.any { it?.sf == null && it != null }) toast("Algún SF2 de este kit no se pudo cargar") }
        }
    }

    private fun darken(c: Int): Int {
        val r = ((c shr 16) and 0xff) * 6 / 10; val g = ((c shr 8) and 0xff) * 6 / 10; val b = (c and 0xff) * 6 / 10
        return (0xff shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun addRow() {
        val k = kit ?: return
        if (k.pads.size + 4 > 32) { toast("Máximo 8 filas"); return }
        val used = k.pads.map { it.note }.toSet()
        val candidates = Instruments.defaultLayout.filter { it !in used } + (27..87).filter { it !in used }
        repeat(4) { i -> k.pads += PadConfig(candidates.getOrElse(i) { 35 + i }) }
        applyPads(true)
    }

    fun removeRow() {
        val k = kit ?: return
        if (k.pads.size <= 4) { toast("Debe quedar al menos una fila"); return }
        repeat(4) { k.pads.removeAt(k.pads.size - 1) }
        learnMap.entries.removeAll { it.value >= k.pads.size }
        applyPads(true)
    }

    fun regenPatterns() {
        val arr = arrayOfNulls<IntArray>(MAX_PADS)
        pads.forEachIndexed { i, p -> if (i < MAX_PADS && p.autoLevel > 0) arr[i] = PatternGen.generate(p.role, p.autoLevel, engine.core.meter, i) }
        engine.core.autoPatterns = arr
    }

    // ---------------- interaction ----------------
    private fun onPadHit(pad: Int, vel: Int) {
        if (learnState != 0) {
            if (learnPendingNote >= 0) { assignLearn(learnPendingNote, pad); learnPendingNote = -1 }
            else { learnState = 2; learnPad = pad; padView.learnPad = pad }
            engine.core.hit(pad, vel)
            return
        }
        if (autoPlayMode) {
            val p = pads.getOrNull(pad) ?: return
            p.autoLevel = (p.autoLevel + 1) % (PatternGen.MAX_LEVEL + 1)
            regenPatterns()
            if (p.autoLevel > 0 && !engine.core.running) startTransport(false)
            if (pads.none { it.autoLevel > 0 } && !engine.core.recording && engine.core.loop.isEmpty()) stopTransport()
            padView.repaint()
            return
        }
        engine.core.hit(pad, vel)
    }

    private fun onPadLong(pad: Int) {
        if (autoPlayMode) {
            pads.getOrNull(pad)?.autoLevel = 0; regenPatterns(); padView.repaint()
            if (pads.none { it.autoLevel > 0 } && !engine.core.recording && engine.core.loop.isEmpty()) stopTransport()
            return
        }
        if (learnState == 0) showPadEditor(this, pad)
    }

    /** Called on the MIDI thread: plays immediately (lowest latency), UI work goes to the UI thread. */
    private fun onMidiNote(note: Int, vel: Int) {
        if (learnState != 0) { post { learnNote(note) }; return }
        val notes = padNotes
        val pad = learnMap[note] ?: notes.indexOf(note)
        if (pad in notes.indices) engine.core.hit(pad, vel)
        post { btnMidi.badge = T.MINT; postDelayed(120) { btnMidi.badge = if (midi.connectedNames.isEmpty()) null else T.MINT } }
    }

    private fun learnNote(note: Int) {
        if (learnState == 2 && learnPad >= 0) assignLearn(note, learnPad)
        else { learnPendingNote = note; toast("Tecla MIDI $note recibida: ahora toca el pad a asignar") }
    }

    private fun assignLearn(note: Int, pad: Int) {
        learnMap.entries.removeAll { it.key == note }
        learnMap[note] = pad
        saveSettings()
        toast("Tecla MIDI $note → Pad ${pad + 1} (${pads.getOrNull(pad)?.displayName ?: ""})")
        learnState = 1; learnPad = -1; padView.learnPad = -1
        if (tab == Tab.MIDI) showTab(Tab.MIDI)
    }

    fun toggleLearn() {
        if (learnState == 0) {
            learnState = 1; learnPad = -1; learnPendingNote = -1
            if (tab != Tab.PADS) showTab(Tab.PADS)
            toast("Aprender: toca un pad y luego la tecla del controlador (o al revés). Pulsa de nuevo para terminar.")
        } else { learnState = 0; learnPad = -1; learnPendingNote = -1 }
        padView.learnWaiting = learnState != 0; padView.learnPad = learnPad
        refreshTop()
    }

    fun toggleAutoPlay() {
        autoPlayMode = !autoPlayMode
        padView.autoPlayMode = autoPlayMode
        if (autoPlayMode) {
            if (tab != Tab.PADS) showTab(Tab.PADS)
            toast("AutoPlay: haz clic en un pad para subir su intensidad (6 niveles); clic derecho o mantener para apagarlo")
        } else {
            pads.forEach { it.autoLevel = 0 }; regenPatterns(); padView.repaint()
            if (!engine.core.recording && engine.core.loop.isEmpty()) stopTransport()
        }
        refreshTop()
    }

    fun startTransport(countIn: Boolean) { engine.core.start(if (countIn) 1 else 0); refreshTop() }
    fun stopTransport() { engine.core.stop(); postDelayed(30) { refreshTop() } }

    private fun toggleRecord() {
        val c = engine.core
        if (c.recording) { c.recording = false; toast("Grabación detenida (el loop sigue sonando)") }
        else {
            c.recording = true
            if (!c.running) startTransport(prefs.getBoolean("countIn", true))
            toast("Grabando ${c.loopBars} compás(es) en loop · ${c.grid.label}")
        }
        refreshTop()
    }

    fun refreshTop() {
        val c = engine.core
        btnAuto.selected2 = autoPlayMode
        btnMetro.selected2 = c.metronomeOn
        btnRec.selected2 = c.recording
        btnPlay.glyph = if (c.running) Glyph.STOP else Glyph.PLAY; btnPlay.selected2 = c.running; btnPlay.repaint()
        btnLearn.selected2 = learnState != 0
        updateDisplay()
    }

    private fun updateDisplay() {
        val c = engine.core
        val m = c.meter
        val t = c.positionTicks
        val txt = if (c.running && t >= 0) {
            val bar = (t / m.barTicks).toInt(); val inBar = t - bar * m.barTicks
            val beat = (inBar / m.beatTicks).toInt(); val tk = (inBar - beat * m.beatTicks).toInt() * 1000 / m.beatTicks
            String.format("%03d|%d|%03d  %s  %dBPM", bar % c.loopBars + 1, beat + 1, tk, m, c.bpm.toInt())
        } else if (c.running) "Preparado…  $m  ${c.bpm.toInt()}BPM"
        else String.format("001|1|000  %s  %dBPM", m, c.bpm.toInt())
        if (display.text != txt) display.text = txt
    }

    fun updateStatus() {
        val names = midi.connectedNames
        val k = kit?.name ?: "—"
        statusText = if (names.isEmpty()) "Kit: $k" else "Kit: $k\nMIDI conectado: ${names.joinToString()}"
        btnMidi.badge = if (names.isEmpty()) null else T.MINT
        frame.title = appTitle
    }

    private fun tick() {
        updateDisplay()
        if (btnRec.selected2 != engine.core.recording || (btnPlay.glyph == Glyph.STOP) != engine.core.running) refreshTop()
    }

    // ---------------- keyboard ----------------
    private val keysDown = HashSet<Int>()

    private fun installKeys() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { e ->
            if (!frame.isActive) return@addKeyEventDispatcher false
            val typing = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner is JTextComponent
            when (e.id) {
                KeyEvent.KEY_PRESSED -> {
                    when (e.keyCode) {
                        KeyEvent.VK_ESCAPE -> {
                            if (fullScreen) setFullScreen(false) else stage.topDialog()?.dismiss()
                            return@addKeyEventDispatcher true
                        }
                        KeyEvent.VK_F11 -> { setFullScreen(!fullScreen); return@addKeyEventDispatcher true }
                    }
                    if (typing || stage.topDialog() != null) return@addKeyEventDispatcher false
                    if (!keysDown.add(e.keyCode)) return@addKeyEventDispatcher true   // ignore auto-repeat
                    if (e.keyCode == KeyEvent.VK_SPACE) { if (engine.core.running) stopTransport() else startTransport(false); return@addKeyEventDispatcher true }
                    val idx = padIndexForKey(e)
                    if (idx >= 0 && idx < pads.size) {
                        onPadHit(idx, if (e.isShiftDown) 127 else 105)
                        padView.flash(idx); kitView.flash(idx)
                        return@addKeyEventDispatcher true
                    }
                }
                KeyEvent.KEY_RELEASED -> keysDown.remove(e.keyCode)
            }
            false
        }
    }

    private fun padIndexForKey(e: KeyEvent): Int {
        val name = when (e.keyCode) {
            KeyEvent.VK_COMMA -> ","
            in KeyEvent.VK_0..KeyEvent.VK_9, in KeyEvent.VK_A..KeyEvent.VK_Z -> e.keyCode.toChar().toString()
            in KeyEvent.VK_NUMPAD1..KeyEvent.VK_NUMPAD8 -> ('1' + (e.keyCode - KeyEvent.VK_NUMPAD1)).toString()
            else -> return -1
        }
        return PAD_KEYS.indexOf(name)
    }

    // ---------------- import ----------------
    fun pickSf2() {
        val fd = java.awt.FileDialog(frame, "Importar SF2", java.awt.FileDialog.LOAD)
        fd.file = "*.sf2"
        fd.setFilenameFilter { _, n -> n.endsWith(".sf2", true) }
        fd.isVisible = true
        val f = fd.files.firstOrNull() ?: return
        importSf2(f)
    }

    fun importSf2(f: File) {
        toast("Importando SF2…")
        bg.execute {
            val r = runCatching { sfm.import(f) }
            post {
                r.onSuccess { n -> toast("SF2 instalado: $n (${sfm.kitPresets(n).size} kits)"); if (tab == Tab.KITS) showTab(Tab.KITS) }
                r.onFailure { toast("No se pudo importar: ${it.message}") }
            }
        }
    }

    /** Drop .sf2 files on the window to import them. */
    private fun installDrop() {
        stage.transferHandler = object : javax.swing.TransferHandler() {
            override fun canImport(s: TransferSupport) = s.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)
            override fun importData(s: TransferSupport): Boolean {
                @Suppress("UNCHECKED_CAST")
                val files = runCatching { s.transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<File> }.getOrNull() ?: return false
                val sf = files.filter { it.name.endsWith(".sf2", true) }
                if (sf.isEmpty()) { toast("Solo se pueden importar archivos .sf2"); return false }
                sf.forEach { importSf2(it) }
                return true
            }
        }
    }
}
