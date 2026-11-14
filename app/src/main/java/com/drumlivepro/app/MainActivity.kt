package com.drumlivepro.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.drumlivepro.app.audio.AudioEngine
import com.drumlivepro.app.audio.FxParams
import com.drumlivepro.app.audio.MAX_PADS
import com.drumlivepro.app.audio.PadSound
import com.drumlivepro.app.audio.SoundFontManager
import com.drumlivepro.app.midi.MidiInput
import com.drumlivepro.app.model.Kit
import com.drumlivepro.app.model.KitStore
import com.drumlivepro.app.model.PadConfig
import com.drumlivepro.app.model.RecordingStore
import com.drumlivepro.app.seq.Grid
import com.drumlivepro.app.seq.Meter
import com.drumlivepro.app.seq.PatternGen
import com.drumlivepro.app.ui.Glyph
import com.drumlivepro.app.ui.GlyphButton
import com.drumlivepro.app.ui.IconPainter
import com.drumlivepro.app.ui.PadGridView
import com.drumlivepro.app.ui.T
import com.drumlivepro.app.ui.VirtualKitView
import com.drumlivepro.app.ui.WRAP
import com.drumlivepro.app.ui.MATCH
import com.drumlivepro.app.ui.dp
import com.drumlivepro.app.ui.dpi
import com.drumlivepro.app.ui.fill
import com.drumlivepro.app.ui.hbox
import com.drumlivepro.app.ui.label
import com.drumlivepro.app.ui.lp
import com.drumlivepro.app.ui.toast
import com.drumlivepro.app.ui.rounded
import com.drumlivepro.app.ui.vbox
import org.json.JSONObject
import java.util.concurrent.Executors

class MainActivity : Activity() {
    val versionName: String by lazy { runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "1.0" }
    val appTitle get() = "Drum Live Pro v$versionName"

    lateinit var prefs: SharedPreferences
    val fx = FxParams()
    lateinit var engine: AudioEngine
    lateinit var sfm: SoundFontManager
    lateinit var kits: KitStore
    lateinit var recs: RecordingStore
    lateinit var midi: MidiInput
    val bg = Executors.newSingleThreadExecutor()
    val main = Handler(Looper.getMainLooper())

    var kit: Kit? = null
    val pads: MutableList<PadConfig> get() = kit?.pads ?: mutableListOf()
    @Volatile private var padNotes = IntArray(0)

    // learn
    val learnMap = HashMap<Int, Int>()   // controller note -> pad
    var learnState = 0                    // 0 off, 1 waiting, 2 pad chosen
    var learnPad = -1
    private var learnPendingNote = -1

    var autoPlayMode = false
    var tab = Tab.PADS

    enum class Tab(val title: String, val glyph: Glyph) {
        PADS("Pads", Glyph.PADS), KIT("Batería", Glyph.DRUMVIEW), KITS("Kits", Glyph.KITS), RHYTHM("Ritmo", Glyph.RHYTHM),
        REC("Grabar", Glyph.FOLDER), MIDI("MIDI", Glyph.MIDI), MORE("Más", Glyph.MORE)
    }

    // views
    lateinit var padView: PadGridView
    lateinit var kitView: VirtualKitView
    private lateinit var content: FrameLayout
    private lateinit var display: TextView
    private lateinit var btnBack: GlyphButton
    var statusText = ""
    private lateinit var btnAuto: GlyphButton
    private lateinit var btnMetro: GlyphButton
    private lateinit var btnRec: GlyphButton
    private lateinit var btnPlay: GlyphButton
    private lateinit var btnLearn: GlyphButton
    private lateinit var btnMidi: GlyphButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        T.apply(prefs.getInt("theme", 0))
        window.statusBarColor = T.BG_DEEP
        window.navigationBarColor = T.BG_DEEP
        sfm = SoundFontManager(this)
        kits = KitStore(filesDir, sfm)
        recs = RecordingStore(this)
        engine = AudioEngine(this, fx)
        engine.bufferMultiplier = prefs.getInt("bufMul", 2)
        midi = MidiInput(this)
        loadSettings()

        padView = PadGridView(this).apply {
            hitTimes = engine.core.hitTimes
            keepAnimating = { engine.core.running }
            velocityByPosition = prefs.getBoolean("velPos", false)
            onHit = { p, v -> onPadHit(p, v) }
            onLongPress = { p -> onPadLong(p) }
        }
        kitView = VirtualKitView(this).apply {
            hitTimes = engine.core.hitTimes
            keepAnimating = { engine.core.running }
            onHit = { p, v -> onPadHit(p, v) }
            onLongPress = { p -> showPadEditor(this@MainActivity, p) }
        }
        setContentView(buildRoot())
        applySystemBars()
        engine.core.onRecorded = null
        midi.onNote = { n, v, _ -> onMidiNote(n, v) }
        midi.onDevicesChanged = { updateStatus(); if (tab == Tab.MIDI) showTab(Tab.MIDI) }
        if (prefs.getBoolean("midiOn", true)) midi.start()

        bg.execute {
            runCatching { sfm.ensureBundled() }
            val id = prefs.getString("kit", null)
            val k = (id?.let { kits.load(it) }) ?: kits.builtInKits().firstOrNull()
            main.post { if (k != null) selectKit(k, save = false) else toast("No se encontraron kits") }
        }
        tick()
    }

    override fun onStart() { super.onStart(); engine.start() }
    override fun onStop() { super.onStop(); saveSettings(); if (!engine.core.running) engine.stop() }
    override fun onDestroy() { super.onDestroy(); engine.stop(); midi.stop(); bg.shutdown() }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); applySystemBars(); showTab(tab) }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) applySystemBars() }

    private val isLandscape get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /** Landscape: hide the status (notification) bar so the app uses that space. Portrait: show it. */
    @Suppress("DEPRECATION")
    fun applySystemBars() {
        val land = isLandscape
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            val c = window.insetsController ?: return
            if (land) {
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsets.Type.statusBars())
            } else c.show(WindowInsets.Type.statusBars())
            window.decorView.requestApplyInsets()
        } else {
            val d = window.decorView
            if (land) {
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                d.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                d.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    /** On Android 11+ the app draws edge-to-edge, so keep the UI clear of visible bars and the camera cutout. */
    private fun fitInsets(root: View) {
        if (Build.VERSION.SDK_INT < 30) return
        root.setOnApplyWindowInsetsListener { v, ins ->
            val b = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            ins
        }
        root.requestApplyInsets()
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
    private fun buildRoot(): View {
        val root = vbox()
        root.setBackgroundColor(T.BG)
        fitInsets(root)
        // top bar
        val top = hbox(6)
        fun gb(g: Glyph? = null, text: String? = null, act: (GlyphButton) -> Unit): GlyphButton =
            GlyphButton(this, g, null, text).also { b -> b.setOnClickListener { act(b) }; top.addView(b, lp(dpi(54), dpi(44)).apply { marginEnd = dpi(6) }) }
        btnBack = gb(Glyph.BACK) { showTab(Tab.PADS) }
        btnAuto = gb(Glyph.AUTOPLAY) { toggleAutoPlay() }
        btnMetro = gb(Glyph.METRONOME) { engine.core.metronomeOn = !engine.core.metronomeOn; refreshTop(); saveSettings() }
        btnRec = gb(Glyph.RECORD) { toggleRecord() }.also { it.tint = T.RED }
        btnPlay = gb(Glyph.PLAY) { if (engine.core.running) stopTransport() else startTransport(false) }
        display = TextView(this).apply {
            textSize = 14f; setTextColor(T.TEXT); gravity = Gravity.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            background = this@MainActivity.rounded(T.BTN, T.BORDER, 14f)
            setPadding(dpi(14), 0, dpi(14), 0)
            setOnClickListener { showTab(Tab.RHYTHM) }
        }
        top.addView(display, lp(WRAP, dpi(44)).apply { marginEnd = dpi(6) })
        gb(text = "FX") { showFxDialog(this) }.also { it.tint = T.CYAN }
        btnLearn = gb(Glyph.LEARN) { toggleLearn() }
        btnMidi = gb(Glyph.MIDI) { showTab(Tab.MIDI) }
        val topScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(top) }
        val topBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        topBar.addView(topScroll, lp(0, WRAP, 1f))
        val menuBtn = GlyphButton(this, Glyph.MENU).also { it.setOnClickListener { showMainMenu(this) } }
        topBar.addView(menuBtn, lp(dpi(54), dpi(44)).apply { marginEnd = dpi(6) })
        root.addView(topBar, lp(MATCH, WRAP))
        content = FrameLayout(this)
        root.addView(content, lp(MATCH, 0, 1f))
        showTab(Tab.PADS)
        refreshTop()
        return root
    }

    /** Applies one of the 8 color themes and rebuilds the screen. */
    fun applyColorTheme(i: Int) {
        val cur = tab
        prefs.edit().putInt("theme", i).apply()
        T.apply(i)
        window.statusBarColor = T.BG_DEEP
        window.navigationBarColor = T.BG_DEEP
        setContentView(buildRoot())
        applySystemBars()
        updateStatus()
        showTab(cur)
    }

    fun showTab(t: Tab) {
        tab = t
        if (::btnBack.isInitialized) btnBack.visibility = if (t == Tab.PADS) View.GONE else View.VISIBLE
        content.removeAllViews()
        (padView.parent as? android.view.ViewGroup)?.removeView(padView)
        (kitView.parent as? android.view.ViewGroup)?.removeView(kitView)
        val v: View = when (t) {
            Tab.PADS -> buildPadsPanel(this)
            Tab.KIT -> kitView
            Tab.KITS -> buildKitsPanel(this)
            Tab.RHYTHM -> buildRhythmPanel(this)
            Tab.REC -> buildRecPanel(this)
            Tab.MIDI -> buildMidiPanel(this)
            Tab.MORE -> buildMorePanel(this)
        }
        content.fill(v)
    }

    // ---------------- kit / pads ----------------
    fun selectKit(k: Kit, save: Boolean = true) {
        kit = k
        if (save) prefs.edit().putString("kit", k.id).apply()
        applyPads()
        if (tab == Tab.KITS) showTab(Tab.KITS)
    }

    /** Rebuilds engine sounds for the current kit (loads SF2 data in background). */
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
            main.post { regenPatterns(); if (arr.any { it?.sf == null && it != null }) toast("Algún SF2 de este kit no se pudo cargar") }
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
        val candidates = com.drumlivepro.app.model.Instruments.defaultLayout.filter { it !in used } + (27..87).filter { it !in used }
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
            padView.invalidate()
            return
        }
        engine.core.hit(pad, vel)
    }

    private fun onPadLong(pad: Int) {
        if (autoPlayMode) {
            pads.getOrNull(pad)?.autoLevel = 0; regenPatterns(); padView.invalidate()
            if (pads.none { it.autoLevel > 0 } && !engine.core.recording && engine.core.loop.isEmpty()) stopTransport()
            return
        }
        if (learnState == 0) showPadEditor(this, pad)
    }

    /** Called on the MIDI thread. */
    private fun onMidiNote(note: Int, vel: Int) {
        if (learnState != 0) { main.post { learnNote(note) }; return }
        val notes = padNotes
        val pad = learnMap[note] ?: notes.indexOf(note)
        if (pad in notes.indices) engine.core.hit(pad, vel)
        main.post { btnMidi.badge = T.MINT; main.postDelayed({ btnMidi.badge = if (midi.connectedNames.isEmpty()) null else T.MINT }, 120) }
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
            toast("AutoPlay: toca un pad para subir su intensidad (6 niveles), mantén pulsado para apagarlo")
        } else {
            pads.forEach { it.autoLevel = 0 }; regenPatterns(); padView.invalidate()
            if (!engine.core.recording && engine.core.loop.isEmpty()) stopTransport()
        }
        refreshTop()
    }

    fun startTransport(countIn: Boolean) {
        engine.core.start(if (countIn) 1 else 0)
        refreshTop()
    }

    fun stopTransport() {
        engine.core.stop()
        main.postDelayed({ refreshTop() }, 30)
    }

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
        btnPlay.glyph = if (c.running) Glyph.STOP else Glyph.PLAY; btnPlay.selected2 = c.running; btnPlay.invalidate()
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
    }

    private fun tick() {
        updateDisplay()
        if (btnRec.selected2 != engine.core.recording || (btnPlay.glyph == Glyph.STOP) != engine.core.running) refreshTop()
        main.postDelayed({ tick() }, 60)
    }

    // ---------------- import / permissions ----------------
    fun pickSf2() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        @Suppress("DEPRECATION") startActivityForResult(i, REQ_SF2)
    }

    @Deprecated("framework callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SF2 && resultCode == RESULT_OK) data?.data?.let { importSf2(it) }
    }

    private fun importSf2(uri: Uri) {
        val name = contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (c.moveToFirst() && i >= 0) c.getString(i) else null
        }
        toast("Importando SF2…")
        bg.execute {
            val r = runCatching { sfm.import(uri, name) }
            main.post {
                r.onSuccess { n -> toast("SF2 instalado: $n (${sfm.kitPresets(n).size} kits)"); if (tab == Tab.KITS) showTab(Tab.KITS) }
                r.onFailure { toast("No se pudo importar: ${it.message}") }
            }
        }
    }

    fun requestBlePermissions(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) return true
        requestPermissions(missing.toTypedArray(), REQ_BLE)
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BLE && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startBleScan()
        else if (requestCode == REQ_BLE) toast("Se necesita permiso de Bluetooth para buscar controladores")
    }

    fun startBleScan() {
        if (!requestBlePermissions()) return
        if (!midi.scan()) { toast("Activa el Bluetooth para buscar controladores MIDI"); return }
        toast("Buscando controladores MIDI Bluetooth…")
        if (tab == Tab.MIDI) showTab(Tab.MIDI)
    }

    companion object { const val REQ_SF2 = 11; const val REQ_BLE = 12 }
}
