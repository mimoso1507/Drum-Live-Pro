package com.drumlivepro.desk

import com.drumlivepro.app.audio.EngineCore
import com.drumlivepro.app.audio.FxParams
import org.json.JSONObject
import java.io.File
import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import javax.sound.midi.ShortMessage
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/** Where the app keeps kits, SF2 files, recordings and settings. */
object Paths {
    val data: File by lazy {
        val appData = System.getenv("APPDATA")
        (if (appData != null) File(appData, "DrumLivePro") else File(System.getProperty("user.home"), ".drumlivepro")).apply { mkdirs() }
    }
    /** Exported MIDI / WAV files: Documents\DrumLivePro (like Descargas/DrumLivePro on Android). */
    val export: File by lazy {
        val home = File(System.getProperty("user.home"))
        val docs = listOf(File(home, "Documents"), File(home, "Documentos")).firstOrNull { it.isDirectory } ?: home
        File(docs, "DrumLivePro")
    }
}

/** SharedPreferences-like settings stored as JSON. */
class Prefs(private val file: File) {
    private val o: JSONObject = runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
    @Synchronized fun getFloat(k: String, d: Float) = o.optDouble(k, d.toDouble()).toFloat()
    @Synchronized fun getInt(k: String, d: Int) = o.optInt(k, d)
    @Synchronized fun getBoolean(k: String, d: Boolean) = o.optBoolean(k, d)
    @Synchronized fun getString(k: String, d: String?): String? = if (o.has(k)) o.optString(k) else d
    fun edit() = this
    @Synchronized fun putFloat(k: String, v: Float) = apply { o.put(k, v.toDouble()) }
    @Synchronized fun putInt(k: String, v: Int) = apply { o.put(k, v) }
    @Synchronized fun putBoolean(k: String, v: Boolean) = apply { o.put(k, v) }
    @Synchronized fun putString(k: String, v: String) = apply { o.put(k, v) }
    @Synchronized fun apply() { runCatching { file.writeText(o.toString(1)) } }
}

/** Real-time output through Java Sound (DirectSound on Windows). */
class AudioEngine(fx: FxParams) {
    /** 1 = smallest buffer (lowest latency), 2 = normal, 4 = safe. */
    @Volatile var bufferMultiplier = 2
    val sampleRate = 48000
    private val block = 128
    val core = EngineCore(sampleRate, fx)
    private var line: SourceDataLine? = null
    @Volatile private var alive = false
    private var thread: Thread? = null
    var error: String? = null; private set

    val latencyMs: Int get() = line?.let { it.bufferSize / 4 * 1000 / sampleRate } ?: 0

    fun start() {
        if (alive) return
        val frames = 512 * bufferMultiplier.coerceIn(1, 8)
        val l = runCatching {
            val fmt = AudioFormat(sampleRate.toFloat(), 16, 2, true, false)
            AudioSystem.getSourceDataLine(fmt).apply { open(fmt, frames * 4); start() }
        }.getOrElse { error = "Sin salida de audio: ${it.message}"; return }
        error = null
        line = l
        alive = true
        thread = Thread({
            val out = FloatArray(block * 2)
            val bytes = ByteArray(block * 4)
            while (alive) {
                core.latencyFrames = l.bufferSize / 4
                core.render(out, block)
                for (i in 0 until block * 2) {
                    val s = (out[i].coerceIn(-1f, 1f) * 32767f).toInt()
                    bytes[i * 2] = s.toByte(); bytes[i * 2 + 1] = (s shr 8).toByte()
                }
                l.write(bytes, 0, bytes.size)
            }
        }, "DrumAudio").apply { priority = Thread.MAX_PRIORITY; isDaemon = true; start() }
    }

    fun restart() { stop(); start() }

    fun stop() {
        alive = false
        thread?.join(500); thread = null
        line?.run { runCatching { stop(); flush() }; close() }
        line = null
    }
}

/** MIDI input from USB controllers (and virtual ports like loopMIDI). Note-ons are reported on the MIDI thread. */
class MidiInput {
    private val open = LinkedHashMap<String, MidiDevice>()
    val connectedNames: List<String> get() = synchronized(open) { open.keys.toList() }
    var onNote: ((note: Int, vel: Int, channel: Int) -> Unit)? = null
    var onDevicesChanged: (() -> Unit)? = null
    @Volatile private var running = false
    private var poller: Thread? = null
    val available = runCatching { MidiSystem.getMidiDeviceInfo(); true }.getOrDefault(false)

    fun start() {
        if (running) return
        running = true
        poller = Thread({ while (running) { rescan(); try { Thread.sleep(3000) } catch (_: InterruptedException) { break } } }, "MidiScan")
            .apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false; poller?.interrupt(); poller = null
        synchronized(open) { open.values.forEach { runCatching { it.close() } }; open.clear() }
        onDevicesChanged?.invoke()
    }

    /** Opens new input devices and forgets unplugged ones. */
    @Synchronized fun rescan() {
        val infos = runCatching { MidiSystem.getMidiDeviceInfo().toList() }.getOrDefault(emptyList())
        val seen = HashSet<String>()
        var changed = false
        for (info in infos) {
            val dev = runCatching { MidiSystem.getMidiDevice(info) }.getOrNull() ?: continue
            if (dev is javax.sound.midi.Sequencer || dev is javax.sound.midi.Synthesizer || dev.maxTransmitters == 0) continue
            val name = info.name.ifBlank { "MIDI" }
            seen += name
            if (synchronized(open) { open.containsKey(name) }) continue
            runCatching {
                dev.open()
                dev.transmitter.receiver = Parser()
                synchronized(open) { open[name] = dev }
                changed = true
            }
        }
        synchronized(open) {
            open.keys.filter { it !in seen }.forEach { k -> runCatching { open[k]?.close() }; open.remove(k); changed = true }
        }
        if (changed) onDevicesChanged?.invoke()
    }

    private inner class Parser : Receiver {
        override fun send(msg: MidiMessage, timeStamp: Long) {
            val m = msg as? ShortMessage ?: return
            if (m.command == ShortMessage.NOTE_ON && m.data2 > 0) onNote?.invoke(m.data1, m.data2, m.channel)
        }
        override fun close() {}
    }
}
