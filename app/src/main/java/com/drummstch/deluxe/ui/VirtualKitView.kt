package com.drummstch.deluxe.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.drummstch.deluxe.model.PadConfig
import com.drummstch.deluxe.model.Role
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Top-down drum kit drawn automatically from the pad mapping. Every pad becomes a piece (drum, cymbal or
 * percussion); extra pads get extra pieces.
 */
class VirtualKitView(ctx: Context) : View(ctx) {
    enum class Kind { KICK, DRUM, CYMBAL, HAT, PERC }
    class Piece(val pad: Int, val kind: Kind, val x: Float, val y: Float, val r: Float)

    var pads: List<PadConfig> = emptyList(); set(v) { field = v; layoutPieces(); invalidate() }
    var kitColor = 0xFFB3202E.toInt(); set(v) { field = v; invalidate() }
    var hitTimes: AtomicLongArray? = null
    var keepAnimating: () -> Boolean = { false }
    var onHit: ((pad: Int, vel: Int) -> Unit)? = null
    var onLongPress: ((pad: Int) -> Unit)? = null

    private var pieces: List<Piece> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = T.TEXT2; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL) }
    private val localHit = LongArray(128)
    private var scale = 1f; private var ox = 0f; private var oy = 0f

    companion object { const val W = 1000f; const val H = 560f }

    private fun layoutPieces() {
        val out = ArrayList<Piece>()
        val byRole = pads.indices.groupBy { roleKind(pads[it].role) }
        val percSlots = ArrayDeque(listOf(
            500f to 60f, 400f to 70f, 600f to 70f, 950f to 120f, 950f to 230f, 950f to 340f, 950f to 450f,
            50f to 120f, 50f to 230f, 50f to 450f, 300f to 60f, 700f to 60f, 820f to 520f, 180f to 520f,
            430f to 160f, 570f to 160f, 50f to 340f, 950f to 530f, 50f to 530f, 640f to 520f, 360f to 520f,
        ))
        fun perc(pad: Int) { val s = percSlots.removeFirstOrNull() ?: (100f + (out.size * 53) % 800 to 520f); out += Piece(pad, Kind.PERC, s.first, s.second, 44f) }
        fun place(list: List<Int>?, slots: List<Triple<Float, Float, Float>>, kind: Kind) {
            list?.forEachIndexed { i, pad -> if (i < slots.size) out += Piece(pad, kind, slots[i].first, slots[i].second, slots[i].third) else perc(pad) }
        }
        val kicks = byRole["kick"]
        place(kicks, if ((kicks?.size ?: 0) >= 2) listOf(Triple(430f, 430f, 105f), Triple(570f, 430f, 105f)) else listOf(Triple(500f, 430f, 120f)), Kind.KICK)
        place(byRole["floor"], listOf(Triple(720f, 440f, 95f), Triple(835f, 360f, 82f), Triple(170f, 470f, 80f)), Kind.DRUM)
        val toms = byRole["tom"] ?: emptyList()
        val tomSlots = (0 until toms.size.coerceAtMost(5)).map { i ->
            val n = toms.size.coerceAtMost(5)
            val a = Math.toRadians(if (n == 1) 270.0 else 228.0 + i * (84.0 / (n - 1)))
            Triple(500f + (225 * cos(a)).toFloat(), 470f + (225 * sin(a)).toFloat(), if (n > 3) 64f else 74f)
        }
        place(toms, tomSlots, Kind.DRUM)
        place(byRole["snare"], listOf(Triple(300f, 420f, 82f), Triple(230f, 300f, 64f), Triple(380f, 520f, 50f)), Kind.DRUM)
        place(byRole["hat"], listOf(Triple(150f, 330f, 78f), Triple(110f, 190f, 64f), Triple(220f, 470f, 56f)), Kind.HAT)
        place(byRole["crash"], listOf(Triple(250f, 140f, 112f), Triple(760f, 120f, 105f), Triple(500f, 90f, 80f), Triple(90f, 90f, 70f)), Kind.CYMBAL)
        place(byRole["ride"], listOf(Triple(860f, 280f, 120f), Triple(900f, 470f, 80f)), Kind.CYMBAL)
        byRole["perc"]?.forEach { perc(it) }
        // draw order: drums, kicks, cymbals, percussion
        pieces = out.sortedBy { when (it.kind) { Kind.DRUM -> 1; Kind.KICK -> 0; Kind.HAT, Kind.CYMBAL -> 2; Kind.PERC -> 3 } }
    }

    private fun roleKind(r: Role) = when (r) {
        Role.KICK -> "kick"; Role.SNARE -> "snare"
        Role.HAT_CLOSED, Role.HAT_OPEN, Role.HAT_PEDAL -> "hat"
        Role.TOM_HIGH, Role.TOM_MID, Role.TOM_LOW -> "tom"; Role.TOM_FLOOR -> "floor"
        Role.CRASH -> "crash"; Role.RIDE -> "ride"
        else -> "perc"
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        scale = minOf(w / W, h / H); ox = (w - W * scale) / 2; oy = (h - H * scale) / 2
    }

    override fun onDraw(c: Canvas) {
        paint.shader = RadialGradient(width / 2f, height * 0.55f, maxOf(width, height) * 0.7f, 0xFF1A2370.toInt(), T.BG_DEEP, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        val now = System.nanoTime()
        var anim = keepAnimating()
        c.save(); c.translate(ox, oy); c.scale(scale, scale)
        // rug
        paint.color = 0x22FFFFFF; c.drawOval(120f, 160f, 880f, 600f, paint)
        for (p in pieces) {
            val t = maxOf(localHit.getOrElse(p.pad) { 0L }, hitTimes?.get(p.pad) ?: 0L)
            val age = (now - t) / 1e6f
            val f = if (t == 0L || age > 300f) 0f else 1f - age / 300f
            if (f > 0f) anim = true
            val rr = p.r * (1f + 0.04f * f)
            when (p.kind) {
                Kind.KICK -> drawKick(c, p.x, p.y, rr, f)
                Kind.DRUM -> drawDrum(c, p.x, p.y, rr, f)
                Kind.CYMBAL, Kind.HAT -> drawCymbal(c, p.x, p.y, rr, f, p.kind == Kind.HAT)
                Kind.PERC -> drawPerc(c, p, rr, f)
            }
        }
        c.restore()
        if (anim) postInvalidateOnAnimation()
    }

    private fun drawDrum(c: Canvas, x: Float, y: Float, r: Float, f: Float) {
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000; c.drawCircle(x + 6, y + 8, r + 4, paint)
        paint.color = kitColor; c.drawCircle(x, y, r + 5, paint)
        paint.color = 0xFFC9CCD8.toInt(); c.drawCircle(x, y, r, paint)
        paint.shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.3f, if (f > 0) 0xFFFFFFFF.toInt() else 0xFFF4F4F2.toInt(), if (f > 0) 0xFF9FE9FF.toInt() else 0xFFD8D8DE.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 0.92f, paint); paint.shader = null
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = 0x33000000
        c.drawCircle(x, y, r * 0.8f, paint)
        paint.style = Paint.Style.FILL; paint.color = 0xFF8D909C.toInt()
        val lugs = if (r > 80) 10 else 8
        for (k in 0 until lugs) { val a = Math.PI * 2 * k / lugs; c.drawCircle(x + ((r + 1) * cos(a)).toFloat(), y + ((r + 1) * sin(a)).toFloat(), 4.5f, paint) }
        if (f > 0) { paint.color = (((f * 120).toInt()) shl 24) or 0x33D6F7; c.drawCircle(x, y, r, paint) }
    }

    private fun drawKick(c: Canvas, x: Float, y: Float, r: Float, f: Float) {
        drawDrum(c, x, y, r, f)
        paint.style = Paint.Style.FILL; paint.color = kitColor
        c.drawCircle(x, y, r * 0.42f, paint)
        text.textSize = r * 0.3f; text.color = 0xFFFFFFFF.toInt(); text.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        c.drawText("DM", x, y + r * 0.11f, text)
        text.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL); text.color = T.TEXT2
        // pedal
        paint.color = 0xFF2A2C38.toInt(); c.drawRoundRect(x - 16, y + r + 2, x + 16, y + r + 50, 6f, 6f, paint)
        paint.color = 0xFF9A9DAA.toInt(); c.drawRoundRect(x - 10, y + r + 8, x + 10, y + r + 44, 4f, 4f, paint)
    }

    private fun drawCymbal(c: Canvas, x: Float, y: Float, r: Float, f: Float, hat: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000; c.drawCircle(x + 7, y + 9, r, paint)
        val light = if (f > 0) 0xFFFFF1B8.toInt() else 0xFFF3D27A.toInt()
        paint.shader = RadialGradient(x, y, r, intArrayOf(light, 0xFFD9A93E.toInt(), 0xFFB98522.toInt(), 0xFFE5C064.toInt()), floatArrayOf(0f, 0.35f, 0.8f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, paint); paint.shader = null
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f; paint.color = 0x30603A00
        var rr = r * 0.32f
        while (rr < r) { c.drawCircle(x, y, rr, paint); rr += 7f }
        paint.style = Paint.Style.FILL
        paint.color = 0xFFE9C66C.toInt(); c.drawCircle(x, y, r * 0.24f, paint)
        paint.color = 0xFF7A7D88.toInt(); c.drawCircle(x, y, 6f, paint)
        if (hat) { paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = 0x66603A00; c.drawCircle(x + 3, y + 4, r * 0.97f, paint) }
        if (f > 0) { paint.style = Paint.Style.FILL; paint.color = (((f * 110).toInt()) shl 24) or 0xFFFFFF; c.drawCircle(x, y, r, paint) }
    }

    private fun drawPerc(c: Canvas, p: Piece, r: Float, f: Float) {
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000; c.drawCircle(p.x + 4, p.y + 6, r, paint)
        paint.color = if (f > 0) T.PAD_HIT else 0xFF0A0D33.toInt(); c.drawCircle(p.x, p.y, r, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = if (f > 0) T.CYAN else T.BORDER
        c.drawCircle(p.x, p.y, r, paint)
        val pad = pads.getOrNull(p.pad) ?: return
        IconPainter.draw(c, pad.displayIcon, p.x, p.y - 2, r * 1.25f, if (f > 0) T.CYAN else 0xFFE3E5F5.toInt(), 1.2f)
    }

    private fun pieceAt(sx: Float, sy: Float): Piece? {
        val x = (sx - ox) / scale; val y = (sy - oy) / scale
        return pieces.asReversed().firstOrNull { hypot(x - it.x, y - it.y) <= it.r * 1.05f }
    }

    private var downPiece: Piece? = null
    private var downTime = 0L
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val p = pieceAt(e.getX(i), e.getY(i)) ?: return true
                // velocity: stronger near the centre of the drum/cymbal
                val x = (e.getX(i) - ox) / scale; val y = (e.getY(i) - oy) / scale
                val d = (hypot(x - p.x, y - p.y) / p.r).coerceIn(0f, 1f)
                val vel = (127 - d * 45).toInt()
                if (p.pad in localHit.indices) localHit[p.pad] = System.nanoTime()
                postInvalidateOnAnimation()
                onHit?.invoke(p.pad, vel)
                if (e.actionMasked == MotionEvent.ACTION_DOWN) { downPiece = p; downTime = System.currentTimeMillis() }
            }
            MotionEvent.ACTION_UP -> {
                val p = downPiece
                if (p != null && System.currentTimeMillis() - downTime > 650 && pieceAt(e.x, e.y) == p) onLongPress?.invoke(p.pad)
                downPiece = null
            }
            MotionEvent.ACTION_CANCEL -> downPiece = null
        }
        return true
    }
}
