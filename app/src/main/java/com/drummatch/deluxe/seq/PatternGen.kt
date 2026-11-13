package com.drummatch.deluxe.seq

import com.drummatch.deluxe.model.Role
import kotlin.random.Random

/**
 * Generates one-bar AutoPlay patterns that follow the selected time signature.
 * Result is a flat array [tick0, vel0, tick1, vel1, ...] sorted by tick.
 */
object PatternGen {
    const val MAX_LEVEL = 6

    fun generate(role: Role, level: Int, meter: Meter, seed: Int): IntArray {
        if (level <= 0) return IntArray(0)
        val bar = meter.barTicks
        val pulse = meter.pulseTicks
        val pulses = meter.pulses.coerceAtLeast(1)
        val e8 = PPQ / 2
        val s16 = PPQ / 4
        val rnd = Random(seed * 31 + level * 7 + role.ordinal)
        val m = sortedMapOf<Int, Int>()
        fun hit(t: Int, v: Int) { if (t in 0 until bar) m[t] = maxOf(m[t] ?: 0, v.coerceIn(1, 127)) }
        fun pulseStart(i: Int) = i * pulse
        fun every(step: Int, from: Int = 0, to: Int = bar, vel: (Int) -> Int) { var t = from; while (t < to) { hit(t, vel(t)); t += step } }
        fun accent(t: Int, strong: Int, weak: Int) = if (t % pulse == 0) strong else weak
        val backbeats: List<Int> = when {
            pulses == 1 -> listOf(bar / 2)
            pulses == 3 && !meter.compound -> listOf(1, 2)
            pulses == 5 -> listOf(1, 3)
            pulses == 7 -> listOf(2, 4, 6)
            else -> (0 until pulses).filter { it % 2 == 1 }
        }.map { if (pulses == 1) it else pulseStart(it) }
        val lastPulse = pulseStart(pulses - 1)

        when (role) {
            Role.KICK -> {
                hit(0, 120)
                if (level >= 2) (0 until pulses).filter { it % 2 == 0 }.forEach { hit(pulseStart(it), 112) }
                if (level >= 3) (0 until pulses).forEach { hit(pulseStart(it), 105) }
                if (level >= 4) (0 until pulses).filter { it % 2 == 1 }.forEach { hit(pulseStart(it) - e8, 92) }
                if (level >= 5) repeat(pulses) { if (rnd.nextFloat() < 0.6f) hit(pulseStart(it) + pulse - s16, 85) }
                if (level >= 6) repeat(pulses) { hit(pulseStart(it) + e8, 88); if (rnd.nextBoolean()) hit(pulseStart(it) + e8 + s16, 78) }
            }
            Role.SNARE -> {
                if (level == 1) hit(backbeats.last(), 115) else backbeats.forEach { hit(it, 118) }
                if (level >= 3) repeat(pulses) { if (rnd.nextFloat() < 0.5f) hit(pulseStart(it) + pulse - s16, 40) }
                if (level >= 4) repeat(pulses) { hit(pulseStart(it) + s16 * 3 % pulse, 35); if (rnd.nextBoolean()) hit(pulseStart(it) + e8, 45) }
                if (level >= 5) every(s16, lastPulse) { 60 + (it - lastPulse) / s16 * 12 }
                if (level >= 6) { every(s16 / 2, lastPulse + e8) { 70 + rnd.nextInt(40) }; hit(0, 100) }
            }
            Role.HAT_CLOSED, Role.RIDE, Role.PERC_SHAKE -> {
                val strong = if (role == Role.RIDE) 110 else 105
                when (level) {
                    1 -> every(pulse) { strong }
                    2 -> every(meter.beatTicks.coerceAtMost(e8 * 2).let { if (meter.compound) e8 else it.coerceAtMost(e8) }) { accent(it, strong, 80) }
                    3 -> every(e8) { if (it % pulse == 0) strong else if (it % PPQ == 0) 95 else 72 }
                    4 -> every(s16) { if (it % pulse == 0) strong else if (it % e8 == 0) 85 else 55 }
                    5 -> every(s16) { if (it % e8 != 0) 95 else accent(it, strong, 65) }
                    else -> { every(s16) { accent(it, strong, 70 + rnd.nextInt(25)) }; repeat(pulses) { hit(pulseStart(it) + pulse - s16 / 2, 60) } }
                }
                if (role == Role.RIDE && level >= 3) backbeats.forEach { hit(it + e8 + s16, 80) }
            }
            Role.HAT_OPEN -> {
                when (level) {
                    1 -> hit(bar - e8, 100)
                    2 -> hit(lastPulse + e8, 100).also { if (pulses > 2) hit(pulseStart(1) + e8, 95) }
                    3 -> repeat(pulses) { hit(pulseStart(it) + e8, 100) }
                    4 -> repeat(pulses) { hit(pulseStart(it) + e8, 105); if (meter.compound) hit(pulseStart(it) + e8 * 2, 80) }
                    5 -> every(e8) { if (it % pulse == 0) 70 else 100 }
                    else -> every(s16) { if (it % e8 == 0) 100 else 75 }
                }
            }
            Role.HAT_PEDAL -> when (level) {
                1 -> backbeats.forEach { hit(it, 100) }
                2 -> every(pulse) { 100 }
                3 -> every(pulse) { 100 }.also { repeat(pulses) { hit(pulseStart(it) + e8, 85) } }
                4 -> every(e8) { 95 }
                5 -> every(e8) { accent(it, 110, 85) }.also { hit(lastPulse + e8 + s16, 70) }
                else -> every(s16) { accent(it, 105, 75) }
            }
            Role.CRASH -> when (level) {
                1 -> hit(0, 115)
                2 -> { hit(0, 118); if (pulses >= 4) hit(pulseStart(pulses / 2), 100) }
                3 -> every(pulse) { if (it == 0) 120 else 95 }
                4 -> { every(pulse) { 105 }; backbeats.forEach { hit(it + e8, 85) } }
                5 -> every(e8) { accent(it, 112, 80) }
                else -> { every(e8) { accent(it, 115, 85) }; every(s16, lastPulse) { 90 } }
            }
            Role.TOM_HIGH, Role.TOM_MID, Role.TOM_LOW, Role.TOM_FLOOR -> {
                // Fills at the end of the bar; different toms take different slots so several tom pads make a roll.
                val slot = when (role) { Role.TOM_HIGH -> 0; Role.TOM_MID -> 1; Role.TOM_LOW -> 2; else -> 3 }
                val fillStart = when (level) { 1, 2 -> lastPulse; 3, 4 -> pulseStart((pulses - 2).coerceAtLeast(0)); else -> 0 }
                val step = if (level % 2 == 1) e8 else s16
                val steps = (bar - fillStart) / step
                for (i in 0 until steps) {
                    val t = fillStart + i * step
                    val portion = i * 4 / steps.coerceAtLeast(1)
                    if (portion == slot || (level >= 5 && rnd.nextFloat() < 0.25f)) hit(t, 85 + i * 30 / steps.coerceAtLeast(1))
                }
                if (level == 6 && slot == 3) every(pulse) { 100 }
            }
            Role.PERC_HAND, Role.PERC_BELL, Role.PERC_WOOD, Role.FX -> {
                fun frac(vararg fr: Double, v: Int) = fr.forEach { hit(((it * bar) / s16).toInt() * s16, v) }
                when (level) {
                    1 -> repeat(pulses) { hit(pulseStart(it) + if (role == Role.PERC_BELL) 0 else e8, 95) }
                    2 -> frac(0.0, 3.0 / 8, 6.0 / 8, v = 105)                                // tresillo
                    3 -> frac(0.0, 3.0 / 16, 6.0 / 16, 10.0 / 16, 12.0 / 16, v = 105)       // clave feel
                    4 -> every(e8) { if ((it / e8) % 3 == 0) 110 else 80 }
                    5 -> every(s16) { when ((it / s16) % 4) { 0 -> 105; 2 -> 90; 3 -> 75; else -> 50 } }
                    else -> every(s16) { if (rnd.nextFloat() < 0.75f) 60 + rnd.nextInt(60) else 1 }.also {
                        m.entries.removeAll { e -> e.value <= 1 }
                    }
                }
            }
        }
        val out = IntArray(m.size * 2)
        var i = 0
        for ((t, v) in m) { out[i++] = t; out[i++] = v }
        return out
    }
}
