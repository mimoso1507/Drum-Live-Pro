package com.drumlivepro.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import com.drumlivepro.app.seq.Grid
import com.drumlivepro.app.seq.Meter
import com.drumlivepro.app.seq.PPQ
import com.drumlivepro.app.seq.Quantizer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

const val MAX_PADS = 64

/** What a pad plays. Built on the UI thread, read by the audio thread. */
class PadSound(val sf: SoundFont?, val presetIndex: Int, val note: Int, val volume: Float, val pan: Float)

/** A recorded hit. [tick] is relative to the start of the loop. */
class NoteEvent(var tick: Int, val pad: Int, val note: Int, val vel: Int, var pass: Long = -1)

private class Voice {
    var active = false
    var data: ShortArray = ShortArray(0)
    var pos = 0.0; var step = 1.0
    var end = 0; var loopS = 0; var loopE = 0; var loopMode = 0
    var gl = 0f; var gr = 0f
    var env = 0f; var stage = 0 // 0 attack,1 hold,2 decay/sustain,3 release
    var attackInc = 1f; var holdLeft = 0; var decayCoef = 0f; var sustain = 1f; var relCoef = 0.999f
    var autoRelease = Int.MAX_VALUE
    var delay = 0
    var excl = 0; var sfKey = 0; var pad = -1
    var lpA = 1f; var lpL = 0f
    var age = 0L
}

/** Pure DSP core: voices + sequencer + FX. Used live by [AudioEngine] and offline for WAV export. */
class EngineCore(val fs: Int, val fx: FxParams) {
    private val voices = Array(72) { Voice() }
    private var ageCounter = 0L
    private val dist = Distortion(fs.toFloat())
    private val echo = Echo(fs.toFloat())
    private val reverb = Reverb(fs.toFloat())
    private val limiter = SoftLimiter()

    @Volatile var pads: Array<PadSound?> = arrayOfNulls(MAX_PADS)
    val hitTimes = AtomicLongArray(MAX_PADS)

    // --- transport (written by UI, read by audio thread) ---
    @Volatile var bpm = 100.0
    @Volatile var meter = Meter(4, 4)
    @Volatile var metronomeOn = false
    @Volatile var metronomeVolume = 0.6f
    @Volatile var autoPatterns: Array<IntArray?> = arrayOfNulls(MAX_PADS)
    @Volatile var loopBars = 2
    @Volatile var grid = Grid.Q16
    @Volatile var swing = 0f
    @Volatile var strength = 1f
    @Volatile var quantizeOnRecord = true
    @Volatile var latencyFrames = 0

    @Volatile var running = false; private set
    @Volatile var recording = false
    @Volatile var playLoop = true
    @Volatile var positionTicks = 0.0; private set
    @Volatile var pass = 0L; private set
    private var recordPassMark = 0L

    val loopLock = Any()
    val loop = ArrayList<NoteEvent>()
    var onRecorded: ((NoteEvent) -> Unit)? = null

    private val inbox = ConcurrentLinkedQueue<IntArray>()
    private val regionCache = HashMap<Long, List<Region>>()

    // metronome click
    private var clickLeft = 0; private var clickPhase = 0.0; private var clickFreq = 1000.0; private var clickAmp = 0f; private var clickDelay = 0; private var clickLen = 0

    val loopTicks get() = (loopBars.coerceAtLeast(1) * meter.barTicks)

    /** Thread-safe: queue a pad hit from UI or MIDI. */
    fun hit(pad: Int, vel: Int) { inbox.add(intArrayOf(0, pad, vel)) }
    fun start(countInBars: Int) { inbox.add(intArrayOf(1, countInBars)) }
    fun stop() { inbox.add(intArrayOf(2)) }
    fun panic() { inbox.add(intArrayOf(3)) }

    private fun handleInbox() {
        while (true) {
            val m = inbox.poll() ?: break
            when (m[0]) {
                0 -> { trigger(m[1], m[2], 0); if (recording && running && positionTicks >= 0) record(m[1], m[2]) }
                1 -> { positionTicks = -m[1].toDouble() * meter.barTicks; pass = 0; running = true }
                2 -> { running = false; recording = false; positionTicks = 0.0 }
                3 -> voices.forEach { it.active = false }
            }
        }
    }

    private fun record(pad: Int, vel: Int) {
        val ps = pads.getOrNull(pad) ?: return
        val lt = loopTicks
        val tpf = bpm * PPQ / (60.0 * fs)
        var t = (positionTicks - latencyFrames * tpf)
        var p = pass
        if (t < 0) { t += lt; p -= 1 }
        var tick = t.toInt() % lt
        if (quantizeOnRecord) {
            val q = Quantizer.quantize(tick, grid, swing, strength)
            if (q >= lt) { tick = q - lt; p += 1 } else tick = q.coerceAtLeast(0)
        }
        val ev = NoteEvent(tick, pad, ps.note, vel, p)
        synchronized(loopLock) { loop.add(ev); loop.sortBy { it.tick } }
        recordPassMark = p
        onRecorded?.invoke(ev)
    }

    fun undoLastTake() {
        synchronized(loopLock) {
            val last = loop.maxOfOrNull { it.pass } ?: return
            loop.removeAll { it.pass == last }
        }
    }

    fun requantize() {
        synchronized(loopLock) {
            val lt = loopTicks
            for (e in loop) e.tick = Math.floorMod(Quantizer.quantize(e.tick, grid, swing, strength), lt)
            loop.sortBy { it.tick }
        }
    }

    /** Starts the voices for a pad. [offset] = frames to wait inside the current block. */
    fun trigger(pad: Int, vel: Int, offset: Int) {
        val ps = pads.getOrNull(pad) ?: return
        val sf = ps.sf ?: return
        hitTimes.set(pad, System.nanoTime())
        val vv = vel.coerceIn(1, 127)
        val sfKey = System.identityHashCode(sf) * 31 + ps.presetIndex
        val ck = (System.identityHashCode(sf).toLong() shl 32) or (ps.presetIndex.toLong() shl 16) or (ps.note.toLong() shl 8) or vv.toLong()
        val regions = regionCache[ck] ?: sf.regions(ps.presetIndex, ps.note, vv).also { if (regionCache.size > 6000) regionCache.clear(); regionCache[ck] = it }
        for (r in regions) {
            if (r.exclusiveClass != 0) for (v in voices) if (v.active && v.excl == r.exclusiveClass && v.sfKey == sfKey && v.stage != 3) {
                v.stage = 3; v.relCoef = exp(-6.9f / (0.012f * fs))
            }
            val v = allocVoice()
            v.data = sf.samples
            if (r.end > v.data.size) continue
            v.pos = r.start.toDouble()
            v.end = r.end; v.loopS = r.loopStart; v.loopE = r.loopEnd; v.loopMode = if (r.loopEnd > r.loopStart + 4) r.loopMode else 0
            val cents = (ps.note - r.rootKey) * r.scaleTuning + r.tuneCents
            v.step = 2.0.pow(cents / 1200.0) * r.sampleRate / fs
            val velGain = (vel / 127f).toDouble().pow(1.7).toFloat()
            val atten = 10.0.pow(-r.attenuationCb * 0.4 / 200.0).toFloat()
            val g = velGain * atten * ps.volume * 1.0f
            val pan = (r.pan + ps.pan).coerceIn(-1f, 1f)
            val a = (pan + 1f) * (PI.toFloat() / 4f)
            v.gl = g * cos(a); v.gr = g * sin(a)
            v.attackInc = if (r.attackSec < 0.002f) 1f else 1f / (r.attackSec * fs)
            v.env = if (v.attackInc >= 1f) 1f else 0f
            v.stage = if (v.env >= 1f) 1 else 0
            v.holdLeft = (r.holdSec * fs).toInt()
            v.sustain = r.sustainLevel
            v.decayCoef = if (r.decaySec < 0.002f) 0f else exp(-6.9f / (r.decaySec * fs))
            v.relCoef = exp(-6.9f / (r.releaseSec.coerceIn(0.03f, 4f) * fs))
            v.autoRelease = if (v.loopMode != 0) (1.6f * fs).toInt() else Int.MAX_VALUE
            v.delay = offset
            v.excl = r.exclusiveClass; v.sfKey = sfKey; v.pad = pad
            v.lpA = if (r.filterHz >= 12000f) 1f else (1f - exp(-2f * PI.toFloat() * r.filterHz / fs)).coerceIn(0.01f, 1f)
            v.lpL = 0f
            v.age = ++ageCounter
            v.active = true
        }
    }

    private fun allocVoice(): Voice {
        var best: Voice? = null; var bestRel: Voice? = null
        for (v in voices) {
            if (!v.active) return v
            if (v.stage == 3 && (bestRel == null || v.age < bestRel.age)) bestRel = v
            if (best == null || v.age < best.age) best = v
        }
        return bestRel ?: best!!
    }

    private fun scheduleClick(offset: Int, accent: Int) {
        clickDelay = offset; clickLen = (0.035 * fs).toInt(); clickLeft = clickLen; clickPhase = 0.0
        clickFreq = when (accent) { 2 -> 1760.0; 1 -> 1320.0; else -> 990.0 }
        clickAmp = metronomeVolume * if (accent > 0) 0.9f else 0.6f
    }

    /** Sequencer: fires metronome, AutoPlay and loop events that fall inside this block. */
    private fun sequence(n: Int) {
        if (!running) return
        val tpf = bpm * PPQ / (60.0 * fs)
        val t0 = positionTicks
        val t1 = t0 + n * tpf
        val m = meter
        val bar = m.barTicks
        // metronome: one click per beat unit (accented on bar / group starts)
        if (metronomeOn || t0 < 0) {
            val beat = m.beatTicks
            var k = floor(t0 / beat).toLong(); if (k * beat < t0) k++
            while (k * beat < t1) {
                val tk = k * beat
                val inBar = Math.floorMod(tk, bar.toLong()).toInt()
                val acc = if (inBar == 0) 2 else if (inBar % m.pulseTicks == 0) 1 else 0
                scheduleClick(((tk - t0) / tpf).toInt().coerceIn(0, n - 1), acc)
                k++
            }
        }
        if (t1 > 0) {
            val s0 = maxOf(t0, 0.0)
            // AutoPlay patterns (one bar)
            val pats = autoPatterns
            for (p in pats.indices) {
                val pat = pats[p] ?: continue
                if (pat.isEmpty()) continue
                firePeriodic(pat, bar, s0, t1, t0, tpf, n) { vel, off -> trigger(p, vel, off) }
            }
            // recorded loop
            if (playLoop) {
                val lt = loopTicks
                synchronized(loopLock) {
                    if (loop.isNotEmpty()) {
                        var cycle = floor(s0 / lt).toLong()
                        while (cycle * lt < t1) {
                            val base = cycle * lt
                            for (e in loop) {
                                val at = base + e.tick
                                if (at >= s0 && at < t1 && !(recording && e.pass == cycle)) trigger(e.pad, e.vel, ((at - t0) / tpf).toInt().coerceIn(0, n - 1))
                            }
                            cycle++
                        }
                    }
                }
            }
        }
        positionTicks = t1
        if (t1 >= 0) pass = floor(t1 / loopTicks).toLong()
    }

    private inline fun firePeriodic(pat: IntArray, period: Int, s0: Double, t1: Double, t0: Double, tpf: Double, n: Int, fire: (Int, Int) -> Unit) {
        var cycle = floor(s0 / period).toLong()
        while (cycle * period < t1) {
            val base = cycle * period
            var i = 0
            while (i < pat.size) {
                val at = base + pat[i]
                if (at >= s0 && at < t1) fire(pat[i + 1], ((at - t0) / tpf).toInt().coerceIn(0, n - 1))
                i += 2
            }
            cycle++
        }
    }

    private var bufL = FloatArray(0); private var bufR = FloatArray(0)

    /** Renders [n] stereo frames interleaved into [out]. */
    fun render(out: FloatArray, n: Int) {
        if (bufL.size < n) { bufL = FloatArray(n); bufR = FloatArray(n) }
        val l = bufL; val r = bufR
        java.util.Arrays.fill(l, 0, n, 0f); java.util.Arrays.fill(r, 0, n, 0f)
        handleInbox()
        sequence(n)
        for (v in voices) if (v.active) renderVoice(v, l, r, n)
        if (fx.distOn) dist.process(l, r, n, fx)
        if (fx.echoOn) echo.process(l, r, n, fx)
        if (fx.revOn) reverb.process(l, r, n, fx)
        val mg = fx.master
        for (i in 0 until n) { l[i] *= mg; r[i] *= mg }
        // metronome is added after FX so it stays dry
        if (clickLeft > 0) {
            var i = clickDelay; clickDelay = 0
            val inc = 2 * PI * clickFreq / fs
            while (i < n && clickLeft > 0) {
                val e = clickLeft.toFloat() / clickLen
                val s = (sin(clickPhase) * clickAmp * e * e).toFloat()
                l[i] += s; r[i] += s; clickPhase += inc; clickLeft--; i++
            }
        }
        limiter.process(l, r, n)
        var j = 0
        for (i in 0 until n) { out[j++] = l[i]; out[j++] = r[i] }
    }

    private fun renderVoice(v: Voice, l: FloatArray, r: FloatArray, n: Int) {
        var i = 0
        if (v.delay > 0) { i = minOf(v.delay, n); v.delay -= i; if (i >= n) return }
        val d = v.data
        while (i < n) {
            // envelope
            when (v.stage) {
                0 -> { v.env += v.attackInc; if (v.env >= 1f) { v.env = 1f; v.stage = 1 } }
                1 -> { if (v.holdLeft-- <= 0) v.stage = 2 }
                2 -> { v.env = v.sustain + (v.env - v.sustain) * v.decayCoef; if (v.env < 0.0003f) { v.active = false; return } }
                3 -> { v.env *= v.relCoef; if (v.env < 0.0003f) { v.active = false; return } }
            }
            if (v.autoRelease != Int.MAX_VALUE && --v.autoRelease <= 0 && v.stage != 3) { v.stage = 3; v.autoRelease = Int.MAX_VALUE }
            val ip = v.pos.toInt()
            if (ip >= v.end - 1) { v.active = false; return }
            val fr = (v.pos - ip).toFloat()
            var s = (d[ip] + (d[ip + 1] - d[ip]) * fr) / 32768f
            if (v.lpA < 1f) { v.lpL += (s - v.lpL) * v.lpA; s = v.lpL }
            s *= v.env
            l[i] += s * v.gl; r[i] += s * v.gr
            v.pos += v.step
            if (v.loopMode != 0 && v.stage != 3 && v.pos >= v.loopE) v.pos -= (v.loopE - v.loopS)
            i++
        }
    }
}

/** Real-time output through a low latency AudioTrack. */
class AudioEngine(ctx: Context, fx: FxParams) {
    /** 1 = smallest buffer (lowest latency), 2 = normal, 4 = safe. */
    @Volatile var bufferMultiplier = 2
    val sampleRate: Int
    private val burst: Int
    val core: EngineCore
    private var track: AudioTrack? = null
    @Volatile private var alive = false
    private var thread: Thread? = null

    init {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        burst = (am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256).coerceIn(64, 1024)
        core = EngineCore(sampleRate, fx)
    }

    val latencyMs: Int get() = track?.let { (it.bufferSizeInFrames * 1000 / sampleRate) } ?: 0

    fun start() {
        if (alive) return
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(maxOf(minBytes, burst * 8 * 2))
            .build()
        val target = burst * bufferMultiplier.coerceIn(1, 8)
        runCatching { t.bufferSizeInFrames = target }
        val maxFrames = maxOf(target * 3, burst * 6)
        track = t
        alive = true
        t.play()
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val out = FloatArray(burst * 2)
            var underruns = t.underrunCount
            while (alive) {
                core.latencyFrames = t.bufferSizeInFrames
                core.render(out, burst)
                t.write(out, 0, burst * 2, AudioTrack.WRITE_BLOCKING)
                val u = t.underrunCount
                if (u > underruns) { // grow the buffer if the device glitches
                    underruns = u
                    runCatching { if (t.bufferSizeInFrames + burst <= minOf(t.bufferCapacityInFrames, maxFrames)) t.bufferSizeInFrames = t.bufferSizeInFrames + burst }
                }
            }
        }, "DrumAudio").also { it.start() }
    }

    fun restart() { stop(); start() }

    fun stop() {
        alive = false
        thread?.join(500); thread = null
        track?.run { runCatching { stop() }; release() }
        track = null
    }
}

/** Offline rendering of the recorded loop to a 16-bit stereo WAV. */
object WavExporter {
    fun render(src: EngineCore, fxCopy: FxParams, repeats: Int, out: java.io.OutputStream) {
        val core = EngineCore(44100, fxCopy)
        core.pads = src.pads.copyOf(); core.bpm = src.bpm; core.meter = src.meter; core.loopBars = src.loopBars
        synchronized(src.loopLock) { src.loop.forEach { core.loop.add(NoteEvent(it.tick, it.pad, it.note, it.vel)) } }
        core.playLoop = true
        core.start(0)
        val loopSec = core.loopTicks * 60.0 / (core.bpm * PPQ)
        val frames = ((loopSec * repeats + 2.5) * 44100).toInt()
        val block = 512
        val buf = FloatArray(block * 2)
        val bytes = ByteArray(block * 4)
        writeHeader(out, frames)
        var done = 0
        while (done < frames) {
            val n = minOf(block, frames - done)
            if (done >= (loopSec * repeats * 44100).toInt()) core.playLoop = false
            core.render(buf, n)
            for (i in 0 until n * 2) {
                val s = (buf[i].coerceIn(-1f, 1f) * 32767).toInt()
                bytes[i * 2] = s.toByte(); bytes[i * 2 + 1] = (s shr 8).toByte()
            }
            out.write(bytes, 0, n * 4)
            done += n
        }
    }

    private fun writeHeader(o: java.io.OutputStream, frames: Int) {
        val data = frames * 4
        fun i32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        fun i16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
        o.write("RIFF".toByteArray()); o.write(i32(36 + data)); o.write("WAVE".toByteArray())
        o.write("fmt ".toByteArray()); o.write(i32(16)); o.write(i16(1)); o.write(i16(2)); o.write(i32(44100))
        o.write(i32(44100 * 4)); o.write(i16(4)); o.write(i16(16))
        o.write("data".toByteArray()); o.write(i32(data))
    }
}

