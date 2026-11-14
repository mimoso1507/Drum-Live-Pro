package com.drumlivepro.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process

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
