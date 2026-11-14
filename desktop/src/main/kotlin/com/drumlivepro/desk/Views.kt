package com.drumlivepro.desk

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.drumlivepro.app.model.Icon
import com.drumlivepro.app.model.PadConfig
import com.drumlivepro.app.model.Role
import com.drumlivepro.app.seq.PatternGen
import com.drumlivepro.app.ui.Glyph
import com.drumlivepro.app.ui.IconPainter
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicLongArray
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Base for custom painted views: gives a [Canvas] and a frame timer used while something animates. */
abstract class CanvasView : JComponent() {
    private val anim = Timer(16) { repaint() }.apply { isRepeats = true }
    fun animate(on: Boolean) { if (on && !anim.isRunning) anim.start() else if (!on && anim.isRunning) anim.stop() }
    fun invalidateView() { repaint() }
    abstract fun draw(c: Canvas)
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try { draw(Canvas(g2)) } finally { g2.dispose() }
    }
    override fun removeNotify() { super.removeNotify(); anim.stop() }
}

/** Keys of the PC keyboard that play the pads (row by row, 4 per row). */
val PAD_KEYS = listOf("1", "2", "3", "4", "Q", "W", "E", "R", "A", "S", "D", "F", "Z", "X", "C", "V",
    "5", "6", "7", "8", "T", "Y", "U", "I", "G", "H", "J", "K", "B", "N", "M", ",")

class PadGridView : CanvasView() {
    var pads: List<PadConfig> = emptyList(); set(v) { field = v; repaint() }
    var columns = 4
    var hitTimes: AtomicLongArray? = null
    var autoPlayMode = false; set(v) { field = v; repaint() }
    var learnPad = -1; set(v) { field = v; repaint() }
    var learnWaiting = false; set(v) { field = v; repaint() }
    var velocityByPosition = false
    var showKeys = true
    var keepAnimating: () -> Boolean = { false }
    var onHit: ((pad: Int, vel: Int) -> Unit)? = null
    var onLongPress: ((pad: Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL) }
    private val rect = RectF()
    private val localHit = LongArray(128)
    private val longTimer = Timer(600) { longFired = true; if (downPad >= 0) onLongPress?.invoke(downPad) }.apply { isRepeats = false }
    private var downPad = -1
    private var longFired = false
    private val rows get() = (pads.size + columns - 1) / columns

    init {
        val m = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val pad = padAt(e.x.toFloat(), e.y.toFloat()); if (pad < 0) return
                if (SwingUtilities.isRightMouseButton(e)) { onLongPress?.invoke(pad); return }
                var vel = 112
                if (velocityByPosition) { padRect(pad, rect); vel = (127 - ((e.y - rect.top) / rect.height()) * 87).toInt().coerceIn(30, 127) }
                onHit?.invoke(pad, vel); flash(pad)
                downPad = pad; longFired = false; longTimer.restart()
            }
            override fun mouseDragged(e: MouseEvent) { if (padAt(e.x.toFloat(), e.y.toFloat()) != downPad) longTimer.stop() }
            override fun mouseReleased(e: MouseEvent) { longTimer.stop(); downPad = -1 }
        }
        addMouseListener(m); addMouseMotionListener(m)
    }

    private fun padRect(i: Int, out: RectF) {
        val gap = dp(6f)
        val cw = (width - gap) / columns; val ch = (height - gap) / rows.coerceAtLeast(1)
        val col = i % columns; val row = i / columns
        out.set(gap + col * cw, gap + row * ch, (col + 1) * cw, (row + 1) * ch)
    }

    private fun padAt(x: Float, y: Float): Int {
        for (i in pads.indices) { padRect(i, rect); if (rect.contains(x, y)) return i }
        return -1
    }

    fun flash(i: Int) { if (i in localHit.indices) { localHit[i] = System.nanoTime(); animate(true) } }

    override fun draw(c: Canvas) {
        val now = System.nanoTime()
        var animating = keepAnimating()
        for (i in pads.indices) {
            padRect(i, rect)
            val pad = pads[i]
            val t = maxOf(localHit[i], hitTimes?.get(i) ?: 0L)
            val age = (now - t) / 1e6f
            val flash = if (t == 0L || age > 260f) 0f else 1f - age / 260f
            if (flash > 0f) animating = true
            val r = dp(14f)
            paint.style = Paint.Style.FILL
            if (flash > 0f) { paint.color = blend(T.PAD, T.PAD_HIT, flash); c.drawRoundRect(rect, r, r, paint) }
            else { paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, T.PAD_TOP, T.PAD, Shader.TileMode.CLAMP); c.drawRoundRect(rect, r, r, paint); paint.shader = null }
            paint.style = Paint.Style.STROKE
            val learnSel = learnPad == i
            paint.strokeWidth = dp(if (learnSel || flash > 0.3f) 2.6f else 1.6f)
            paint.color = when {
                learnSel -> if ((now / 300_000_000L) % 2 == 0L) T.CYAN else T.MINT
                flash > 0f -> blend(T.BORDER, T.CYAN, flash)
                pad.autoLevel > 0 && autoPlayMode -> T.MINT
                else -> T.BORDER
            }
            if (learnSel) animating = true
            c.drawRoundRect(rect, r, r, paint)
            val s = minOf(rect.width(), rect.height()) * 0.56f
            val iconColor = if (flash > 0f) blend(T.TEXT2, T.CYAN, flash) else if (pad.autoLevel > 0) T.MINT else 0xFFD2D4EC.toInt()
            IconPainter.draw(c, pad.displayIcon, rect.centerX(), rect.centerY() - rect.height() * 0.04f, s, iconColor)
            text.color = T.TEXT2
            text.textSize = (rect.height() * 0.11f).coerceIn(dp(9f), dp(13f))
            if (rect.height() > dp(70f)) c.drawText(ellipsize(pad.displayName, rect.width() * 0.9f), rect.centerX(), rect.bottom - dp(7f), text)
            if (showKeys && i < PAD_KEYS.size) {
                text.textSize = dp(11f); text.color = (T.TEXT2 and 0xFFFFFF) or 0x99000000.toInt()
                text.textAlign = Paint.Align.LEFT
                c.drawText(PAD_KEYS[i], rect.left + dp(9f), rect.top + dp(17f), text)
                text.textAlign = Paint.Align.CENTER
            }
            if (autoPlayMode) {
                val n = PatternGen.MAX_LEVEL; val dr = dp(3.4f); val sp = dp(10f)
                val x0 = rect.centerX() - sp * (n - 1) / 2; val y = rect.top + dp(10f)
                for (k in 0 until n) {
                    paint.style = Paint.Style.FILL
                    paint.color = if (k < pad.autoLevel) T.MINT else T.DOT_OFF
                    c.drawCircle(x0 + k * sp, y, dr, paint)
                }
            }
        }
        if (learnWaiting) {
            text.textSize = dp(15f); text.color = T.CYAN
            c.drawText(if (learnPad < 0) "Aprender: toca un pad…" else "Ahora presiona una tecla/pad del controlador MIDI", width / 2f, height - dp(4f), text)
        }
        animate(animating)
    }

    private fun ellipsize(s: String, w: Float): String {
        if (text.measureText(s) <= w) return s
        var e = s.length
        while (e > 1 && text.measureText(s.substring(0, e) + "…") > w) e--
        return s.substring(0, e) + "…"
    }
}

/** Top-down drum kit drawn automatically from the pad mapping (same layout as on Android). */
class VirtualKitView : CanvasView() {
    enum class Kind { KICK, DRUM, CYMBAL, HAT, PERC }
    class Piece(val pad: Int, val kind: Kind, val x: Float, val y: Float, val r: Float)

    var pads: List<PadConfig> = emptyList(); set(v) { field = v; layoutPieces(); repaint() }
    var kitColor = 0xFFB3202E.toInt(); set(v) { field = v; repaint() }
    var hitTimes: AtomicLongArray? = null
    var keepAnimating: () -> Boolean = { false }
    var onHit: ((pad: Int, vel: Int) -> Unit)? = null
    var onLongPress: ((pad: Int) -> Unit)? = null

    private var pieces: List<Piece> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
    private val localHit = LongArray(128)
    private val scale get() = minOf(width / W, height / H)
    private val ox get() = (width - W * scale) / 2
    private val oy get() = (height - H * scale) / 2
    private val longTimer = Timer(650) { downPiece?.let { onLongPress?.invoke(it.pad) } }.apply { isRepeats = false }
    private var downPiece: Piece? = null

    companion object { const val W = 1000f; const val H = 560f }

    init {
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val p = pieceAt(e.x.toFloat(), e.y.toFloat()) ?: return
                if (SwingUtilities.isRightMouseButton(e)) { onLongPress?.invoke(p.pad); return }
                val x = (e.x - ox) / scale; val y = (e.y - oy) / scale
                val d = (hypot(x - p.x, y - p.y) / p.r).coerceIn(0f, 1f)
                if (p.pad in localHit.indices) localHit[p.pad] = System.nanoTime()
                animate(true)
                onHit?.invoke(p.pad, (127 - d * 45).toInt())
                downPiece = p; longTimer.restart()
            }
            override fun mouseReleased(e: MouseEvent) { longTimer.stop(); downPiece = null }
        })
    }

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
        pieces = out.sortedBy { when (it.kind) { Kind.DRUM -> 1; Kind.KICK -> 0; Kind.HAT, Kind.CYMBAL -> 2; Kind.PERC -> 3 } }
    }

    private fun roleKind(r: Role) = when (r) {
        Role.KICK -> "kick"; Role.SNARE -> "snare"
        Role.HAT_CLOSED, Role.HAT_OPEN, Role.HAT_PEDAL -> "hat"
        Role.TOM_HIGH, Role.TOM_MID, Role.TOM_LOW -> "tom"; Role.TOM_FLOOR -> "floor"
        Role.CRASH -> "crash"; Role.RIDE -> "ride"
        else -> "perc"
    }

    override fun draw(c: Canvas) {
        paint.shader = RadialGradient(width / 2f, height * 0.55f, maxOf(width, height) * 0.7f, T.KIT_BG, T.BG_DEEP, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        val now = System.nanoTime()
        var anim = keepAnimating()
        c.save(); c.translate(ox, oy); c.scale(scale, scale)
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
        animate(anim)
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
        text.textSize = r * 0.3f; text.color = 0xFFFFFFFF.toInt()
        c.drawText("DM", x, y + r * 0.11f, text)
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
        paint.color = if (f > 0) T.PAD_HIT else T.PAD; c.drawCircle(p.x, p.y, r, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = if (f > 0) T.CYAN else T.BORDER
        c.drawCircle(p.x, p.y, r, paint)
        val pad = pads.getOrNull(p.pad) ?: return
        IconPainter.draw(c, pad.displayIcon, p.x, p.y - 2, r * 1.25f, if (f > 0) T.CYAN else 0xFFE3E5F5.toInt(), 1.2f)
    }

    private fun pieceAt(sx: Float, sy: Float): Piece? {
        val x = (sx - ox) / scale; val y = (sy - oy) / scale
        return pieces.asReversed().firstOrNull { hypot(x - it.x, y - it.y) <= it.r * 1.05f }
    }

    fun flash(pad: Int) { if (pad in localHit.indices) { localHit[pad] = System.nanoTime(); animate(true) } }
}

/** Rotary knob: drag up/down or use the mouse wheel. Value is normalised 0..1. */
class Knob(val title: String, var value: Float, val format: (Float) -> String, val onChange: (Float) -> Unit) : CanvasView() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL) }
    private val r = RectF()
    private var lastY = 0
    init {
        preferredSize = Dimension(dpi(110), dpi(130)); cursor = Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
        val m = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { lastY = e.y }
            override fun mouseDragged(e: MouseEvent) { val dy = lastY - e.y; lastY = e.y; set(value + dy / dp(180f)); onChange(value) }
        }
        addMouseListener(m); addMouseMotionListener(m)
        addMouseWheelListener { e -> set(value - e.wheelRotation * 0.02f); onChange(value) }
    }
    fun set(v: Float) { value = v.coerceIn(0f, 1f); repaint() }
    override fun draw(c: Canvas) {
        val th = dp(18f)
        tp.textSize = dp(13f); tp.color = T.TEXT2
        c.drawText(title, width / 2f, th - dp(3f), tp)
        val size = minOf(width.toFloat(), height - th * 2) - dp(14f)
        val cx = width / 2f; val cy = th + (height - th * 2) / 2f
        r.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(4f); p.color = 0x55A4A6C2
        c.drawArc(r, 135f, 270f, false, p)
        p.color = T.CYAN
        c.drawArc(r, 135f, 270f * value, false, p)
        val a = Math.toRadians(135.0 + 270.0 * value)
        p.style = Paint.Style.FILL
        c.drawCircle(cx + (size / 2 * cos(a)).toFloat(), cy + (size / 2 * sin(a)).toFloat(), dp(7f), p)
        tp.textSize = dp(14f); tp.color = T.TEXT
        c.drawText(format(value), cx, height - dp(4f), tp)
    }
}

/** Grid of selectable icons. */
class IconGridView(private val icons: List<Icon>, var selected: Icon?, private val color: () -> Int = { T.CYAN }, val onPick: (Icon) -> Unit) : CanvasView() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cell get() = dp(58f)
    private val cols get() = (width / cell).toInt().coerceAtLeast(1)
    init {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                val n = cols; val cw = width.toFloat() / n
                val i = (e.y / cell).toInt() * n + (e.x / cw).toInt()
                icons.getOrNull(i)?.let { selected = it; repaint(); onPick(it) }
            }
        })
    }
    override fun getPreferredSize(): Dimension {
        val w = (parent?.width ?: dpi(600)).coerceAtLeast(dpi(200))
        val c = (w / cell).toInt().coerceAtLeast(1)
        return Dimension(w, ((icons.size + c - 1) / c * cell).toInt())
    }
    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    override fun draw(c: Canvas) {
        val n = cols; val cw = width.toFloat() / n
        icons.forEachIndexed { i, ic ->
            val x = (i % n) * cw; val y = (i / n) * cell
            val sel = ic == selected
            p.style = Paint.Style.FILL; p.color = if (sel) T.BTN_SEL else T.BTN
            c.drawRoundRect(x + dp(3f), y + dp(3f), x + cw - dp(3f), y + cell - dp(3f), dp(10f), dp(10f), p)
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(if (sel) 2.4f else 1f); p.color = if (sel) T.CYAN else 0x66A4A6C2
            c.drawRoundRect(x + dp(3f), y + dp(3f), x + cw - dp(3f), y + cell - dp(3f), dp(10f), dp(10f), p)
            IconPainter.draw(c, ic, x + cw / 2, y + cell / 2, cell * 0.62f, if (sel) color() else 0xFFD8DAF0.toInt())
        }
    }
}

/** Small clickable icon tile. */
class IconTile(var icon: Icon, sizeDp: Int, val act: () -> Unit) : CanvasView() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        preferredSize = Dimension(dpi(sizeDp), dpi(sizeDp)); maximumSize = preferredSize; minimumSize = preferredSize
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() { override fun mouseReleased(e: MouseEvent) { if (contains(e.point)) act() } })
    }
    override fun draw(c: Canvas) {
        p.style = Paint.Style.FILL; p.color = T.BTN
        c.drawRoundRect(1f, 1f, width - 1f, height - 1f, dp(10f), dp(10f), p)
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(1f); p.color = T.BORDER
        c.drawRoundRect(1f, 1f, width - 1f, height - 1f, dp(10f), dp(10f), p)
        IconPainter.draw(c, icon, width / 2f, height / 2f, width * 0.7f, 0xFFD8DAF0.toInt())
    }
}

/** Kit card on the Kits screen. kit == null draws the "+ Nuevo kit" card. */
class KitCard(val kitName: String?, val icon: Icon?, val color: Int, val custom: Boolean, val sel: Boolean,
              val onClick: () -> Unit, val onMenu: () -> Unit) : CanvasView() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL) }
    private var hover = false
    init {
        preferredSize = Dimension(dpi(104), dpi(112)); maximumSize = preferredSize
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = if (kitName != null) "Clic: cargar · clic derecho: editar, duplicar o borrar" else null
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
            override fun mouseReleased(e: MouseEvent) {
                if (!contains(e.point)) return
                if (SwingUtilities.isRightMouseButton(e)) onMenu() else onClick()
            }
        })
    }
    override fun draw(c: Canvas) {
        val r = dp(16f)
        p.style = Paint.Style.FILL; p.color = if (sel) T.BTN_SEL else if (hover) blend(T.BTN, T.BORDER, 0.2f) else T.BTN
        c.drawRoundRect(dp(3f), dp(3f), width - dp(3f), height - dp(3f), r, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(if (sel) 2.6f else 1.5f); p.color = if (sel) T.CYAN else T.BORDER
        c.drawRoundRect(dp(3f), dp(3f), width - dp(3f), height - dp(3f), r, r, p)
        if (kitName == null || icon == null) {
            IconPainter.glyph(c, Glyph.PLUS, width / 2f, height * 0.42f, width * 0.4f, T.CYAN)
            tp.color = T.TEXT; tp.textSize = dp(12.5f); c.drawText("Nuevo kit", width / 2f, height - dp(14f), tp); return
        }
        p.style = Paint.Style.FILL; p.color = (color and 0x00FFFFFF) or 0x33000000
        c.drawCircle(width / 2f, height * 0.4f, width * 0.3f, p)
        IconPainter.draw(c, icon, width / 2f, height * 0.4f, width * 0.46f, color)
        tp.color = T.TEXT; tp.textSize = dp(12f)
        var n: String = kitName
        while (n.length > 3 && tp.measureText(n) > width - dp(12f)) n = n.dropLast(2) + "…"
        c.drawText(n, width / 2f, height - dp(14f), tp)
        if (custom) { p.color = T.MINT; c.drawCircle(width - dp(14f), dp(14f), dp(4f), p) }
    }
}

/** Color swatch. */
class Swatch(val color: Int, var sel: Boolean, val onPick: () -> Unit) : CanvasView() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        preferredSize = Dimension(dpi(36), dpi(36)); maximumSize = preferredSize
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() { override fun mouseReleased(e: MouseEvent) { onPick() } })
    }
    override fun draw(c: Canvas) {
        p.style = Paint.Style.FILL; p.color = color
        c.drawRoundRect(dp(2f), dp(2f), width - dp(2f), height - dp(2f), dp(12f), dp(12f), p)
        if (sel) { p.style = Paint.Style.STROKE; p.strokeWidth = dp(3f); p.color = T.TEXT; c.drawRoundRect(dp(2f), dp(2f), width - dp(2f), height - dp(2f), dp(12f), dp(12f), p) }
    }
}
