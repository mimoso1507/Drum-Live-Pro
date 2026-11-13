package com.drumlivepro.app.seq

import kotlin.math.roundToInt

const val PPQ = 480 // ticks per quarter note

data class Meter(val num: Int, val den: Int) {
    val beatTicks get() = PPQ * 4 / den
    val barTicks get() = num * beatTicks
    /** 6/8, 9/8, 12/8... are felt in groups of three eighth notes. */
    val compound get() = den >= 8 && num % 3 == 0 && num > 3
    val pulseTicks get() = if (compound) beatTicks * 3 else beatTicks
    val pulses get() = barTicks / pulseTicks
    override fun toString() = "$num/$den"

    companion object {
        val COMMON = listOf(
            Meter(2, 4), Meter(3, 4), Meter(4, 4), Meter(5, 4), Meter(6, 4), Meter(7, 4),
            Meter(2, 2), Meter(3, 2), Meter(3, 8), Meter(5, 8), Meter(6, 8), Meter(7, 8), Meter(9, 8), Meter(12, 8),
        )
        fun parse(s: String?): Meter = s?.split("/")?.let { p -> runCatching { Meter(p[0].toInt(), p[1].toInt()) }.getOrNull() } ?: Meter(4, 4)
    }
}

/** Quantize grid. Straight values from quarter notes to 1/64 plus triplets. */
enum class Grid(val label: String, val ticks: Int, val triplet: Boolean = false) {
    OFF("Sin cuantizar", 0),
    Q4("1/4 (negra)", PPQ),
    Q8("1/8 (corchea)", PPQ / 2),
    Q8T("1/8 tresillo", PPQ / 3, true),
    Q16("1/16 (semicorchea)", PPQ / 4),
    Q16T("1/16 tresillo", PPQ / 6, true),
    Q32("1/32 (fusa)", PPQ / 8),
    Q32T("1/32 tresillo", PPQ / 12, true),
    Q64("1/64 (semifusa)", PPQ / 16);

    companion object { fun byName(n: String?) = values().firstOrNull { it.name == n } ?: Q16 }
}

object Quantizer {
    /**
     * Moves [tick] towards the nearest grid line. [swing] 0..1 (0 = straight, 1 = full triplet feel) delays every
     * second grid line. [strength] 0..1 controls how far the note is moved.
     */
    fun quantize(tick: Int, grid: Grid, swing: Float, strength: Float): Int {
        if (grid == Grid.OFF) return tick
        val g = grid.ticks
        val idx = (tick.toDouble() / g).roundToInt()
        var target = idx * g
        if (!grid.triplet && swing > 0f && idx % 2 != 0) target += (swing * g / 3f).roundToInt()
        return (tick + (target - tick) * strength).roundToInt()
    }
}
