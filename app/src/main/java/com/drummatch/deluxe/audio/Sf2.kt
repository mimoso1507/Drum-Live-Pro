package com.drummatch.deluxe.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Header of one preset (program) inside an SF2 file. */
data class PresetInfo(val name: String, val program: Int, val bank: Int, val index: Int) {
    val isDrumBank get() = bank == 128
}

/** One playable zone resolved for a note: sample + all generators already merged. */
class Region(
    val sf: SoundFont,
    val start: Int, val end: Int, val loopStart: Int, val loopEnd: Int,
    val sampleRate: Int, val rootKey: Int, val tuneCents: Int, val scaleTuning: Int,
    val loopMode: Int, val attenuationCb: Float, val pan: Float,
    val exclusiveClass: Int,
    val attackSec: Float, val holdSec: Float, val decaySec: Float, val sustainLevel: Float, val releaseSec: Float,
    val filterHz: Float,
)

/**
 * Minimal SoundFont 2 reader. Parses the preset/instrument hierarchy and keeps the 16-bit sample pool in memory.
 * Modulators are ignored (velocity is applied with a fixed curve by the engine).
 */
class SoundFont private constructor(val file: File) {
    var name: String = file.nameWithoutExtension
        private set
    lateinit var presets: List<PresetInfo>
        private set
    var samples: ShortArray = ShortArray(0)
        private set

    private class Zone(val gens: IntArray, val set: BooleanArray)
    private class Bagged(val global: Zone?, val zones: List<Zone>)

    private lateinit var presetZones: List<Bagged>
    private lateinit var instZones: List<Bagged>
    private lateinit var shStart: IntArray
    private lateinit var shEnd: IntArray
    private lateinit var shLoopS: IntArray
    private lateinit var shLoopE: IntArray
    private lateinit var shRate: IntArray
    private lateinit var shPitch: IntArray
    private lateinit var shCorr: IntArray

    companion object {
        private const val G_START_OFS = 0; private const val G_END_OFS = 1; private const val G_LSTART_OFS = 2
        private const val G_LEND_OFS = 3; private const val G_START_COARSE = 4; private const val G_FILTER_FC = 8
        private const val G_END_COARSE = 12; private const val G_PAN = 17
        private const val G_DELAY_VOL = 33; private const val G_ATTACK = 34; private const val G_HOLD = 35
        private const val G_DECAY = 36; private const val G_SUSTAIN = 37; private const val G_RELEASE = 38
        private const val G_INSTRUMENT = 41; private const val G_KEYRANGE = 43; private const val G_VELRANGE = 44
        private const val G_LSTART_COARSE = 45; private const val G_ATTEN = 48; private const val G_LEND_COARSE = 50
        private const val G_COARSE = 51; private const val G_FINE = 52; private const val G_SAMPLE = 53
        private const val G_MODES = 54; private const val G_SCALE = 56; private const val G_EXCL = 57; private const val G_ROOT = 58
        private const val NGEN = 61

        /** Reads only headers (fast): good for listing presets. */
        fun readHeaders(file: File): SoundFont = SoundFont(file).also { it.parse(false) }
        fun load(file: File): SoundFont = SoundFont(file).also { it.parse(true) }

        private fun defaults(): IntArray = IntArray(NGEN).also {
            it[G_FILTER_FC] = 13500
            it[G_DELAY_VOL] = -12000; it[G_ATTACK] = -12000; it[G_HOLD] = -12000; it[G_DECAY] = -12000; it[G_RELEASE] = -12000
            it[G_KEYRANGE] = 127 shl 8; it[G_VELRANGE] = 127 shl 8
            it[G_SCALE] = 100; it[G_ROOT] = -1; it[G_SAMPLE] = -1; it[G_INSTRUMENT] = -1
        }
    }

    private fun parse(withSamples: Boolean) {
        RandomAccessFile(file, "r").use { raf ->
            val hdr = ByteArray(12); raf.readFully(hdr)
            require(String(hdr, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(hdr, 8, 4, Charsets.ISO_8859_1) == "sfbk") { "No es un archivo SF2 válido" }
            val total = minOf(raf.length(), le32(hdr, 4).toLong() + 8)
            var pos = 12L
            var smplOfs = -1L; var smplLen = 0L
            val pdta = HashMap<String, ByteArray>()
            while (pos + 8 <= total) {
                raf.seek(pos); val ch = ByteArray(12); raf.readFully(ch, 0, 8)
                val id = String(ch, 0, 4, Charsets.ISO_8859_1); val size = le32(ch, 4).toLong() and 0xffffffffL
                if (id == "LIST") {
                    raf.readFully(ch, 8, 4)
                    val type = String(ch, 8, 4, Charsets.ISO_8859_1)
                    var p = pos + 12; val end = pos + 8 + size
                    while (p + 8 <= end) {
                        raf.seek(p); val sh = ByteArray(8); raf.readFully(sh)
                        val sid = String(sh, 0, 4, Charsets.ISO_8859_1); val ss = le32(sh, 4).toLong() and 0xffffffffL
                        when {
                            type == "sdta" && sid == "smpl" -> { smplOfs = p + 8; smplLen = ss }
                            type == "pdta" -> { val b = ByteArray(ss.toInt()); raf.readFully(b); pdta[sid] = b }
                            type == "INFO" && sid == "INAM" -> { val b = ByteArray(ss.toInt()); raf.readFully(b); name = cstr(b, 0, b.size).ifBlank { name } }
                        }
                        p += 8 + ss + (ss and 1)
                    }
                }
                pos += 8 + size + (size and 1)
            }
            parsePdta(pdta)
            if (withSamples && smplOfs > 0) {
                val n = (smplLen / 2).toInt()
                val bytes = ByteArray(n * 2)
                raf.seek(smplOfs); raf.readFully(bytes)
                val arr = ShortArray(n)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(arr)
                samples = arr
            }
        }
    }

    private fun parsePdta(p: Map<String, ByteArray>) {
        val phdr = p["phdr"] ?: error("SF2 sin presets"); val pbag = p["pbag"]!!; val pgen = p["pgen"]!!
        val inst = p["inst"]!!; val ibag = p["ibag"]!!; val igen = p["igen"]!!; val shdr = p["shdr"]!!
        val nP = phdr.size / 38
        val list = ArrayList<PresetInfo>()
        val pz = ArrayList<Bagged>()
        for (i in 0 until nP - 1) {
            val o = i * 38
            list += PresetInfo(cstr(phdr, o, 20), le16(phdr, o + 20), le16(phdr, o + 22), i)
            pz += bags(pbag, pgen, le16(phdr, o + 24), le16(phdr, o + 38 + 24), G_INSTRUMENT)
        }
        presets = list; presetZones = pz
        val nI = inst.size / 22
        val iz = ArrayList<Bagged>()
        for (i in 0 until nI - 1) iz += bags(ibag, igen, le16(inst, i * 22 + 20), le16(inst, (i + 1) * 22 + 20), G_SAMPLE)
        instZones = iz
        val nS = shdr.size / 46
        shStart = IntArray(nS); shEnd = IntArray(nS); shLoopS = IntArray(nS); shLoopE = IntArray(nS)
        shRate = IntArray(nS); shPitch = IntArray(nS); shCorr = IntArray(nS)
        for (i in 0 until nS) {
            val o = i * 46
            shStart[i] = le32(shdr, o + 20); shEnd[i] = le32(shdr, o + 24); shLoopS[i] = le32(shdr, o + 28)
            shLoopE[i] = le32(shdr, o + 32); shRate[i] = le32(shdr, o + 36)
            shPitch[i] = shdr[o + 40].toInt() and 0xff; shCorr[i] = shdr[o + 41].toInt()
        }
    }

    private fun bags(bag: ByteArray, gen: ByteArray, from: Int, to: Int, terminal: Int): Bagged {
        var global: Zone? = null
        val zones = ArrayList<Zone>()
        for (b in from until to) {
            val g0 = le16(bag, b * 4); val g1 = le16(bag, (b + 1) * 4)
            val gens = IntArray(NGEN); val set = BooleanArray(NGEN)
            var hasTerm = false
            for (g in g0 until g1) {
                val op = le16(gen, g * 4)
                if (op >= NGEN) continue
                val amt = if (op == G_KEYRANGE || op == G_VELRANGE) {
                    (gen[g * 4 + 2].toInt() and 0xff) or ((gen[g * 4 + 3].toInt() and 0xff) shl 8)
                } else if (op == G_INSTRUMENT || op == G_SAMPLE) le16(gen, g * 4 + 2) else s16(gen, g * 4 + 2)
                gens[op] = amt; set[op] = true
                if (op == terminal) hasTerm = true
            }
            val z = Zone(gens, set)
            if (!hasTerm) { if (b == from) global = z } else zones += z
        }
        return Bagged(global, zones)
    }

    private fun inRange(r: Int, v: Int) = v >= (r and 0xff) && v <= ((r shr 8) and 0xff)
    private fun rangeOf(z: Zone?, g: Int, local: Zone): Int = if (local.set[g]) local.gens[g] else if (z != null && z.set[g]) z.gens[g] else (127 shl 8)

    /** Resolves all regions to play for [key]/[vel] on the given preset index. */
    fun regions(presetIndex: Int, key: Int, vel: Int): List<Region> {
        val out = ArrayList<Region>(2)
        val pb = presetZones.getOrNull(presetIndex) ?: return out
        for (pzone in pb.zones) {
            if (!inRange(rangeOf(pb.global, G_KEYRANGE, pzone), key) || !inRange(rangeOf(pb.global, G_VELRANGE, pzone), vel)) continue
            val ib = instZones.getOrNull(pzone.gens[G_INSTRUMENT]) ?: continue
            for (izone in ib.zones) {
                if (!inRange(rangeOf(ib.global, G_KEYRANGE, izone), key) || !inRange(rangeOf(ib.global, G_VELRANGE, izone), vel)) continue
                val g = defaults()
                ib.global?.let { gz -> for (i in 0 until NGEN) if (gz.set[i]) g[i] = gz.gens[i] }
                for (i in 0 until NGEN) if (izone.set[i]) g[i] = izone.gens[i]
                // preset-level generators are additive
                for (i in 0 until NGEN) {
                    if (i == G_INSTRUMENT || i == G_KEYRANGE || i == G_VELRANGE || i == G_SAMPLE || i == G_MODES || i == G_EXCL || i == G_ROOT) continue
                    val add = when {
                        pzone.set[i] -> pzone.gens[i]
                        pb.global?.set?.get(i) == true -> pb.global.gens[i]
                        else -> 0
                    }
                    g[i] += add
                }
                makeRegion(g)?.let { out += it }
            }
        }
        return out
    }

    private fun tc(v: Int): Float = Math.pow(2.0, v / 1200.0).toFloat()

    private fun makeRegion(g: IntArray): Region? {
        val s = g[G_SAMPLE]
        if (s < 0 || s >= shStart.size - 1) return null
        val start = shStart[s] + g[G_START_OFS] + g[G_START_COARSE] * 32768
        val end = shEnd[s] + g[G_END_OFS] + g[G_END_COARSE] * 32768
        val ls = shLoopS[s] + g[G_LSTART_OFS] + g[G_LSTART_COARSE] * 32768
        val le = shLoopE[s] + g[G_LEND_OFS] + g[G_LEND_COARSE] * 32768
        if (end <= start) return null
        val root = if (g[G_ROOT] in 0..127) g[G_ROOT] else if (shPitch[s] in 0..127) shPitch[s] else 60
        val sus = (1f - g[G_SUSTAIN].coerceIn(0, 1440) / 1000f).coerceIn(0f, 1f)
        return Region(
            this, start, end, ls, le, shRate[s].coerceAtLeast(1000), root,
            g[G_COARSE] * 100 + g[G_FINE] + shCorr[s], g[G_SCALE],
            g[G_MODES] and 3, g[G_ATTEN].coerceAtLeast(0).toFloat(), g[G_PAN].coerceIn(-500, 500) / 500f,
            g[G_EXCL],
            tc(g[G_ATTACK]), tc(g[G_HOLD]), tc(g[G_DECAY]), sus, tc(g[G_RELEASE]),
            8.176f * tc(g[G_FILTER_FC].coerceIn(1500, 13500)),
        )
    }

    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)
    private fun s16(b: ByteArray, o: Int) = le16(b, o).toShort().toInt()
    private fun le32(b: ByteArray, o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or
        ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)
    private fun cstr(b: ByteArray, o: Int, n: Int): String {
        var e = o; while (e < o + n && b[e] != 0.toByte()) e++
        return String(b, o, e - o, Charsets.ISO_8859_1).trim()
    }
}
