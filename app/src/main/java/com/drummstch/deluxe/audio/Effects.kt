package com.drummstch.deluxe.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/** User-facing FX parameters (all read by the audio thread every block). */
class FxParams {
    @Volatile var distOn = false
    @Volatile var distBand = 2400f      // Hz, centre of the band that gets driven
    @Volatile var distLowPass = 8000f   // Hz
    @Volatile var distIntensity = 0.15f // 0..1

    @Volatile var revOn = true
    @Volatile var revTime = 935f        // ms
    @Volatile var revHighFreq = 0.338f  // 0..1 (brightness of the tail)
    @Volatile var revMix = 0.25f        // 0..1

    @Volatile var echoOn = false
    @Volatile var echoDelay = 350f      // ms
    @Volatile var echoFeedback = 0.15f  // 0..0.95
    @Volatile var echoMix = 0.2f        // 0..1

    @Volatile var master = 0.9f

    fun resetDist() { distBand = 2400f; distLowPass = 8000f; distIntensity = 0.15f }
    fun resetRev() { revTime = 935f; revHighFreq = 0.338f; revMix = 0.25f }
    fun resetEcho() { echoDelay = 350f; echoFeedback = 0.15f; echoMix = 0.2f }
}

class Biquad {
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f; private var a1 = 0f; private var a2 = 0f
    private var x1 = 0f; private var x2 = 0f; private var y1 = 0f; private var y2 = 0f
    fun lowPass(fs: Float, f: Float, q: Float = 0.707f) {
        val w = 2 * PI.toFloat() * (f.coerceIn(20f, fs * 0.45f)) / fs; val al = sin(w) / (2 * q); val c = cos(w); val a0 = 1 + al
        b0 = (1 - c) / 2 / a0; b1 = (1 - c) / a0; b2 = b0; a1 = -2 * c / a0; a2 = (1 - al) / a0
    }
    fun bandPass(fs: Float, f: Float, q: Float) {
        val w = 2 * PI.toFloat() * (f.coerceIn(20f, fs * 0.45f)) / fs; val al = sin(w) / (2 * q); val c = cos(w); val a0 = 1 + al
        b0 = al / a0; b1 = 0f; b2 = -al / a0; a1 = -2 * c / a0; a2 = (1 - al) / a0
    }
    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = if (y > -1e-15f && y < 1e-15f) 0f else y
        return y1
    }
}

class Distortion(private val fs: Float) {
    private val bpL = Biquad(); private val bpR = Biquad(); private val lpL = Biquad(); private val lpR = Biquad()
    private var lastBand = -1f; private var lastLp = -1f
    fun process(l: FloatArray, r: FloatArray, n: Int, p: FxParams) {
        if (p.distBand != lastBand) { lastBand = p.distBand; bpL.bandPass(fs, lastBand, 0.8f); bpR.bandPass(fs, lastBand, 0.8f) }
        if (p.distLowPass != lastLp) { lastLp = p.distLowPass; lpL.lowPass(fs, lastLp); lpR.lowPass(fs, lastLp) }
        val drive = 1f + p.distIntensity * 60f
        val makeup = 1f / tanh(drive * 0.5f).coerceAtLeast(0.2f) * 0.5f
        for (i in 0 until n) {
            val xl = l[i] + bpL.process(l[i]) * p.distIntensity * 2f
            val xr = r[i] + bpR.process(r[i]) * p.distIntensity * 2f
            l[i] = lpL.process(tanh(xl * drive) * makeup)
            r[i] = lpR.process(tanh(xr * drive) * makeup)
        }
    }
}

class Echo(private val fs: Float) {
    private val size = (fs * 2.1f).toInt()
    private val bl = FloatArray(size); private val br = FloatArray(size)
    private var w = 0
    private var curDelay = -1f
    fun process(l: FloatArray, r: FloatArray, n: Int, p: FxParams) {
        val target = (p.echoDelay / 1000f * fs).coerceIn(1f, size - 2f)
        if (curDelay < 0) curDelay = target
        val fb = p.echoFeedback.coerceIn(0f, 0.95f); val wet = p.echoMix.coerceIn(0f, 1f); val dry = 1f - wet * 0.5f
        for (i in 0 until n) {
            curDelay += (target - curDelay) * 0.0005f
            var rp = w - curDelay.toInt(); if (rp < 0) rp += size
            val dl = bl[rp]; val dr = br[rp]
            bl[w] = l[i] + dr * fb   // light ping-pong
            br[w] = r[i] + dl * fb
            l[i] = l[i] * dry + dl * wet
            r[i] = r[i] * dry + dr * wet
            if (++w >= size) w = 0
        }
    }
}

/** Freeverb-style reverb (Schroeder/Moorer). */
class Reverb(fs: Float) {
    private class Comb(n: Int) { val buf = FloatArray(n); var i = 0; var store = 0f
        fun p(x: Float, fb: Float, damp: Float): Float { val o = buf[i]; store = o * (1 - damp) + store * damp; buf[i] = x + store * fb; if (++i >= buf.size) i = 0; return o } }
    private class AllPass(n: Int) { val buf = FloatArray(n); var i = 0
        fun p(x: Float): Float { val b = buf[i]; val o = -x + b; buf[i] = x + b * 0.5f; if (++i >= buf.size) i = 0; return o } }
    private val sc = fs / 44100f
    private val combL = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617).map { Comb((it * sc).toInt()) }
    private val combR = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617).map { Comb(((it + 23) * sc).toInt()) }
    private val apL = intArrayOf(556, 441, 341, 225).map { AllPass((it * sc).toInt()) }
    private val apR = intArrayOf(556, 441, 341, 225).map { AllPass(((it + 23) * sc).toInt()) }
    fun process(l: FloatArray, r: FloatArray, n: Int, p: FxParams) {
        // map time (100..5000 ms) to feedback 0.70..0.985
        val t = ((p.revTime - 100f) / 4900f).coerceIn(0f, 1f)
        val fb = 0.70f + 0.285f * (1f - exp(-3f * t)) / (1f - exp(-3f))
        val damp = (1f - p.revHighFreq).coerceIn(0f, 0.95f) * 0.6f
        val wet = p.revMix.coerceIn(0f, 1f); val dry = 1f - wet * 0.4f
        for (i in 0 until n) {
            val input = (l[i] + r[i]) * 0.015f
            var ol = 0f; var or = 0f
            for (c in combL) ol += c.p(input, fb, damp)
            for (c in combR) or += c.p(input, fb, damp)
            for (a in apL) ol = a.p(ol)
            for (a in apR) or = a.p(or)
            l[i] = l[i] * dry + ol * wet * 1.5f
            r[i] = r[i] * dry + or * wet * 1.5f
        }
    }
}

/** Gentle look-ahead-free limiter to avoid clipping when many pads hit at once. */
class SoftLimiter {
    private var env = 1f
    fun process(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            val pk = maxOf(kotlin.math.abs(l[i]), kotlin.math.abs(r[i]))
            val target = if (pk > 0.95f) 0.95f / pk else 1f
            env = if (target < env) target else env + (target - env) * 0.0008f
            l[i] = tanh(l[i] * env)
            r[i] = tanh(r[i] * env)
        }
    }
}
