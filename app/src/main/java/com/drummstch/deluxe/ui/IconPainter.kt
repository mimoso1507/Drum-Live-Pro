package com.drummstch.deluxe.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.drummstch.deluxe.model.Icon
import kotlin.math.cos
import kotlin.math.sin

/** Small toolbar / UI glyphs. */
enum class Glyph { PLAY, STOP, RECORD, METRONOME, AUTOPLAY, MENU, KITS, PADS, DRUMVIEW, MIDI, LEARN, PLUS, MINUS, TRASH, UNDO, SAVE, EXPORT, FOLDER, GEAR, BACK, RHYTHM, CHECK, EDIT, QUANTIZE, BLUETOOTH, MORE }

/**
 * Original line-art icon set, drawn with Canvas primitives in a 100x100 design box.
 * Strokes use [color]; fills use the same color with transparency.
 */
object IconPainter {
    private val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fl = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val r = RectF()
    private val p = Path()
    private lateinit var c: Canvas

    fun draw(canvas: Canvas, icon: Icon, cx: Float, cy: Float, size: Float, color: Int, strokeScale: Float = 1f) {
        begin(canvas, cx, cy, size, color, strokeScale)
        drawIcon(icon)
        canvas.restore()
    }

    fun glyph(canvas: Canvas, g: Glyph, cx: Float, cy: Float, size: Float, color: Int) {
        begin(canvas, cx, cy, size, color, 1.1f)
        drawGlyph(g)
        canvas.restore()
    }

    private fun begin(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int, strokeScale: Float) {
        c = canvas
        canvas.save()
        canvas.translate(cx - size / 2, cy - size / 2)
        canvas.scale(size / 100f, size / 100f)
        st.color = color; st.strokeWidth = 4.2f * strokeScale
        fl.color = (color and 0x00FFFFFF) or 0x40000000
        solid.color = color
    }

    // ---------- primitives ----------
    private fun ln(x1: Float, y1: Float, x2: Float, y2: Float) = c.drawLine(x1, y1, x2, y2, st)
    private fun ov(l: Float, t: Float, rr: Float, b: Float, fill: Paint? = null) { r.set(l, t, rr, b); fill?.let { c.drawOval(r, it) }; c.drawOval(r, st) }
    private fun ci(x: Float, y: Float, rad: Float, fill: Paint? = null) { fill?.let { c.drawCircle(x, y, rad, it) }; c.drawCircle(x, y, rad, st) }
    private fun rc(l: Float, t: Float, rr: Float, b: Float, rad: Float = 3f, fill: Paint? = null) { r.set(l, t, rr, b); fill?.let { c.drawRoundRect(r, rad, rad, it) }; c.drawRoundRect(r, rad, rad, st) }
    private fun arc(l: Float, t: Float, rr: Float, b: Float, start: Float, sweep: Float) { r.set(l, t, rr, b); c.drawArc(r, start, sweep, false, st) }
    private fun poly(close: Boolean, fill: Paint?, vararg pts: Float) {
        p.reset(); p.moveTo(pts[0], pts[1]); var i = 2
        while (i < pts.size) { p.lineTo(pts[i], pts[i + 1]); i += 2 }
        if (close) p.close()
        fill?.let { c.drawPath(p, it) }
        c.drawPath(p, st)
    }
    private fun dot(x: Float, y: Float, rad: Float) = c.drawCircle(x, y, rad, solid)

    /** Drum seen from the front-side: top ellipse + shell + lugs. */
    private fun drum(cx: Float, top: Float, w: Float, h: Float, zig: Boolean = true) {
        val e = w * 0.28f
        val l = cx - w / 2; val rr = cx + w / 2
        p.reset(); p.moveTo(l, top + e / 2); p.lineTo(l, top + h); r.set(l, top + h - e / 2, rr, top + h + e / 2); p.arcTo(r, 180f, -180f); p.lineTo(rr, top + e / 2)
        c.drawPath(p, fl); c.drawPath(p, st)
        ov(l, top, rr, top + e)
        if (zig) {
            val n = (w / 14).toInt().coerceAtLeast(3)
            val dx = w / n
            p.reset(); p.moveTo(l + 2, top + e)
            for (i in 0 until n) { p.lineTo(l + dx * i + dx / 2, top + h + e / 2 - 3); p.lineTo(l + dx * (i + 1), top + e + 1) }
            c.drawPath(p, st)
        }
    }

    private fun cymbal(cx: Float, cy: Float, w: Float, tilt: Float = 0.26f, fill: Boolean = true) {
        ov(cx - w / 2, cy - w * tilt / 2, cx + w / 2, cy + w * tilt / 2, if (fill) fl else null)
        ov(cx - w * 0.13f, cy - w * 0.08f - w * tilt / 4, cx + w * 0.13f, cy + w * 0.05f - w * tilt / 4)
    }

    private fun tripod(x: Float, y: Float, w: Float = 16f) { ln(x, y, x - w, y + 12); ln(x, y, x + w, y + 12); ln(x, y, x, y + 12) }

    private fun hand(cx: Float, cy: Float, s: Float, ang: Float) {
        c.save(); c.translate(cx, cy); c.rotate(ang); c.scale(s, s)
        rc(-12f, -4f, 12f, 22f, 9f, fl)
        for (i in 0..3) rc(-12f + i * 6.4f, -24f + (if (i == 0 || i == 3) 6 else 0), -7f + i * 6.4f, 0f, 3f)
        p.reset(); p.moveTo(-12f, 8f); p.quadTo(-26f, -2f, -22f, -10f); c.drawPath(p, st)
        c.restore()
    }

    private fun stick(x1: Float, y1: Float, x2: Float, y2: Float) {
        val w = st.strokeWidth; st.strokeWidth = w * 1.5f; ln(x1, y1, x2, y2); st.strokeWidth = w; dot(x2, y2, w * 1.1f)
    }

    private fun star(cx: Float, cy: Float, ro: Float, ri: Float, n: Int) {
        p.reset()
        for (i in 0 until n * 2) {
            val a = Math.PI * i / n - Math.PI / 2; val rad = if (i % 2 == 0) ro else ri
            val x = cx + (rad * cos(a)).toFloat(); val y = cy + (rad * sin(a)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close(); c.drawPath(p, fl); c.drawPath(p, st)
    }

    private fun bell(cx: Float, top: Float, w: Float, h: Float) {
        p.reset(); p.moveTo(cx - w * 0.25f, top); p.cubicTo(cx - w * 0.3f, top + h * 0.6f, cx - w / 2, top + h * 0.8f, cx - w / 2, top + h)
        p.lineTo(cx + w / 2, top + h); p.cubicTo(cx + w / 2, top + h * 0.8f, cx + w * 0.3f, top + h * 0.6f, cx + w * 0.25f, top); p.close()
        c.drawPath(p, fl); c.drawPath(p, st)
    }

    private fun drawIcon(i: Icon) {
        when (i) {
            Icon.KICK -> { ci(50f, 48f, 34f, fl); ci(50f, 48f, 25f); ci(50f, 48f, 6f); ln(26f, 76f, 20f, 90f); ln(74f, 76f, 80f, 90f); rc(44f, 82f, 56f, 94f, 2f) }
            Icon.SNARE -> { drum(50f, 22f, 62f, 26f); ln(50f, 62f, 50f, 74f); tripod(50f, 74f) }
            Icon.SNARE_ELEC -> { ov(16f, 30f, 84f, 56f, fl); ov(26f, 35f, 74f, 51f); poly(false, null, 52f, 14f, 44f, 30f, 56f, 30f, 48f, 46f); ln(50f, 56f, 50f, 74f); tripod(50f, 74f) }
            Icon.SIDE_STICK -> { drum(50f, 34f, 62f, 26f); stick(12f, 30f, 88f, 46f) }
            Icon.CLAP -> { hand(38f, 52f, 1.05f, -20f); hand(62f, 52f, 1.05f, 20f); ln(50f, 10f, 50f, 18f); ln(36f, 14f, 40f, 20f); ln(64f, 14f, 60f, 20f) }
            Icon.SNAP -> { hand(46f, 58f, 1.15f, -10f); ln(70f, 18f, 76f, 10f); ln(76f, 26f, 86f, 24f); ln(66f, 12f, 66f, 4f) }
            Icon.HIHAT_CLOSED -> { cymbal(50f, 30f, 66f); cymbal(50f, 36f, 66f, 0.22f, false); ln(50f, 40f, 50f, 78f); tripod(50f, 78f) }
            Icon.HIHAT_OPEN -> { cymbal(50f, 22f, 66f); cymbal(50f, 40f, 66f, 0.22f, false); ln(50f, 44f, 50f, 78f); tripod(50f, 78f); ln(16f, 30f, 10f, 32f); ln(84f, 30f, 90f, 32f) }
            Icon.HIHAT_PEDAL -> { cymbal(50f, 20f, 56f); cymbal(50f, 26f, 56f, 0.22f, false); ln(50f, 30f, 50f, 64f); poly(true, fl, 36f, 90f, 64f, 90f, 60f, 70f, 40f, 70f); ln(40f, 70f, 50f, 64f) }
            Icon.TOM_HIGH -> { drum(50f, 30f, 48f, 26f); ln(50f, 70f, 50f, 84f); ln(40f, 84f, 60f, 84f) }
            Icon.TOM_MID -> { drum(50f, 26f, 60f, 32f); ln(50f, 72f, 50f, 86f); ln(40f, 86f, 60f, 86f) }
            Icon.TOM_LOW -> { drum(50f, 22f, 72f, 38f); ln(50f, 74f, 50f, 88f); ln(38f, 88f, 62f, 88f) }
            Icon.TOM_FLOOR -> { drum(50f, 16f, 64f, 52f); ln(20f, 70f, 14f, 92f); ln(80f, 70f, 86f, 92f); ln(50f, 82f, 50f, 92f) }
            Icon.CRASH -> { cymbal(50f, 34f, 82f, 0.3f); ln(50f, 42f, 50f, 80f); tripod(50f, 80f) }
            Icon.RIDE -> { cymbal(50f, 34f, 88f, 0.26f); ln(50f, 42f, 50f, 80f); tripod(50f, 80f); stick(80f, 12f, 62f, 28f) }
            Icon.RIDE_BELL -> { cymbal(50f, 40f, 84f, 0.26f); ci(50f, 34f, 9f, solid); ln(50f, 46f, 50f, 82f); stick(84f, 14f, 58f, 28f) }
            Icon.CHINA -> { poly(false, null, 8f, 30f, 18f, 40f, 82f, 40f, 92f, 30f); ov(18f, 34f, 82f, 46f, fl); ov(42f, 30f, 58f, 38f); ln(50f, 46f, 50f, 80f); tripod(50f, 80f) }
            Icon.SPLASH -> { cymbal(50f, 44f, 52f, 0.3f); ln(50f, 52f, 50f, 82f); tripod(50f, 82f, 12f); ln(18f, 26f, 24f, 34f); ln(50f, 16f, 50f, 26f); ln(82f, 26f, 76f, 34f) }
            Icon.STACK -> { cymbal(50f, 30f, 70f); cymbal(50f, 38f, 60f, 0.2f, false); cymbal(50f, 22f, 50f, 0.2f, false); ln(50f, 42f, 50f, 80f); tripod(50f, 80f) }
            Icon.TAMBOURINE -> { ci(50f, 50f, 32f, fl); ci(50f, 50f, 24f); for (k in 0 until 6) { val a = Math.PI * k / 3; val x = 50f + (28 * cos(a)).toFloat(); val y = 50f + (28 * sin(a)).toFloat(); rc(x - 6, y - 4, x + 6, y + 4, 2f, solid) } }
            Icon.COWBELL -> { poly(true, fl, 32f, 30f, 68f, 30f, 82f, 82f, 18f, 82f); ln(40f, 30f, 40f, 16f); ln(60f, 30f, 60f, 16f); ln(40f, 16f, 60f, 16f); ln(30f, 70f, 70f, 70f) }
            Icon.VIBRASLAP -> { rc(64f, 62f, 90f, 84f, 3f, fl); p.reset(); p.moveTo(70f, 66f); p.cubicTo(40f, 90f, 10f, 70f, 30f, 30f); c.drawPath(p, st); ci(28f, 24f, 9f, solid) }
            Icon.BONGO -> { drum(32f, 34f, 38f, 34f, false); drum(70f, 40f, 30f, 28f, false); rc(46f, 58f, 56f, 64f, 2f) }
            Icon.CONGA -> { p.reset(); p.moveTo(34f, 18f); p.cubicTo(18f, 50f, 30f, 80f, 38f, 92f); p.lineTo(62f, 92f); p.cubicTo(70f, 80f, 82f, 50f, 66f, 18f); c.drawPath(p, fl); c.drawPath(p, st); ov(34f, 12f, 66f, 24f); ln(30f, 44f, 70f, 44f) }
            Icon.TIMBALE -> { drum(38f, 40f, 46f, 20f, false); drum(68f, 46f, 36f, 18f, false); stick(10f, 22f, 36f, 40f); ln(52f, 70f, 52f, 90f) }
            Icon.AGOGO -> { poly(true, fl, 26f, 30f, 36f, 30f, 42f, 74f, 20f, 74f); poly(true, fl, 64f, 40f, 74f, 40f, 80f, 74f, 58f, 74f); p.reset(); p.moveTo(31f, 30f); p.cubicTo(31f, 6f, 69f, 10f, 69f, 40f); c.drawPath(p, st); ln(50f, 18f, 50f, 92f) }
            Icon.CABASA -> { rc(28f, 18f, 72f, 58f, 10f, fl); for (k in 0..3) ln(30f, 26f + k * 9, 70f, 26f + k * 9); for (k in 0..4) dot(34f + k * 8, 30f + (k % 2) * 9, 2.4f); rc(45f, 58f, 55f, 92f, 4f) }
            Icon.MARACAS -> { ov(14f, 10f, 46f, 44f, fl); stick(36f, 40f, 58f, 90f); ov(54f, 10f, 86f, 44f, fl); stick(64f, 40f, 42f, 90f); ln(20f, 26f, 40f, 26f); ln(60f, 26f, 80f, 26f) }
            Icon.WHISTLE -> { ci(40f, 56f, 22f, fl); rc(48f, 30f, 92f, 46f, 4f, fl); rc(56f, 34f, 66f, 42f, 1f); ci(18f, 32f, 8f) }
            Icon.GUIRO -> { c.save(); c.rotate(-25f, 50f, 50f); ov(14f, 34f, 86f, 66f, fl); for (k in 0..7) ln(28f + k * 6, 38f, 28f + k * 6, 62f); c.restore(); stick(60f, 90f, 88f, 60f) }
            Icon.CLAVES -> { val w = st.strokeWidth; st.strokeWidth = w * 2.6f; ln(16f, 72f, 84f, 40f); ln(22f, 30f, 78f, 86f); st.strokeWidth = w }
            Icon.WOODBLOCK -> { rc(14f, 40f, 72f, 74f, 6f, fl); ln(22f, 52f, 64f, 52f); stick(90f, 12f, 66f, 34f); ci(64f, 34f, 6f, solid) }
            Icon.CUICA -> { drum(50f, 24f, 56f, 52f, false); ln(50f, 30f, 50f, 70f); hand(78f, 72f, 0.7f, 40f) }
            Icon.TRIANGLE -> { poly(false, null, 44f, 24f, 18f, 84f, 82f, 84f, 56f, 30f); ln(50f, 8f, 50f, 22f); stick(92f, 40f, 62f, 66f) }
            Icon.SHAKER -> { c.save(); c.rotate(-30f, 50f, 50f); rc(16f, 36f, 84f, 64f, 14f, fl); for (k in 0..4) dot(28f + k * 11, 50f + (if (k % 2 == 0) -5 else 5), 3f); c.restore() }
            Icon.JINGLE_BELL -> { stick(14f, 86f, 46f, 40f); ln(46f, 40f, 72f, 18f); for (k in 0..3) ci(52f + k * 9, 28f + k * 11, 7f, fl) }
            Icon.BELL_TREE -> { ln(20f, 12f, 80f, 12f); ln(50f, 12f, 50f, 90f); for (k in 0..4) { val w = 46f - k * 6; ov(50f - w / 2, 22f + k * 14, 50f + w / 2, 32f + k * 14, fl) } }
            Icon.CASTANETS -> { arc(14f, 26f, 54f, 74f, 90f, 180f); ln(34f, 26f, 34f, 74f); arc(46f, 26f, 86f, 74f, -90f, 180f); ln(66f, 26f, 66f, 74f); c.drawRect(34f, 26f, 34f, 74f, fl); p.reset(); p.moveTo(34f, 30f); p.quadTo(50f, 10f, 66f, 30f); c.drawPath(p, st) }
            Icon.SURDO -> { drum(50f, 18f, 66f, 54f); p.reset(); p.moveTo(17f, 30f); p.quadTo(50f, -10f, 83f, 30f); c.drawPath(p, st) }
            Icon.HIGH_Q -> { p.reset(); p.moveTo(8f, 50f); for (k in 0..84 step 4) p.lineTo(8f + k, 50f - (30 * sin(k / 6.0) * (1 - k / 100.0)).toFloat()); c.drawPath(p, st); poly(false, null, 70f, 16f, 60f, 34f, 74f, 34f, 64f, 52f) }
            Icon.SLAP -> { hand(48f, 54f, 1.2f, 0f); ln(14f, 20f, 22f, 28f); ln(86f, 20f, 78f, 28f); ln(50f, 8f, 50f, 16f) }
            Icon.SCRATCH -> { ci(46f, 54f, 34f, fl); ci(46f, 54f, 10f); ci(46f, 54f, 22f); dot(46f, 54f, 3f); stick(86f, 12f, 66f, 40f); arc(8f, 8f, 84f, 84f, 200f, 60f) }
            Icon.STICKS -> { stick(14f, 86f, 80f, 14f); stick(30f, 90f, 90f, 30f) }
            Icon.SQUARE_CLICK -> { poly(false, null, 8f, 66f, 26f, 66f, 26f, 30f, 50f, 30f, 50f, 66f, 74f, 66f, 74f, 30f, 92f, 30f) }
            Icon.METRONOME -> { poly(true, fl, 36f, 12f, 64f, 12f, 82f, 90f, 18f, 90f); ln(50f, 78f, 70f, 22f); ci(64f, 38f, 5f, solid); ln(24f, 74f, 76f, 74f) }
            Icon.METRO_BELL -> { bell(50f, 18f, 66f, 58f); ci(50f, 82f, 7f, solid); ln(50f, 8f, 50f, 18f) }
            Icon.GONG -> { ln(10f, 10f, 90f, 10f); ln(16f, 10f, 16f, 92f); ln(84f, 10f, 84f, 92f); ci(50f, 52f, 26f, fl); ci(50f, 52f, 8f); ln(40f, 10f, 44f, 27f); ln(60f, 10f, 56f, 27f) }
            Icon.TIMPANI -> { ov(12f, 26f, 88f, 42f); p.reset(); p.moveTo(12f, 34f); p.cubicTo(14f, 78f, 86f, 78f, 88f, 34f); c.drawPath(p, fl); c.drawPath(p, st); ln(28f, 66f, 22f, 92f); ln(72f, 66f, 78f, 92f); ln(50f, 70f, 50f, 92f) }
            Icon.CAJON -> { poly(true, fl, 24f, 18f, 70f, 18f, 70f, 90f, 24f, 90f); poly(false, null, 70f, 18f, 82f, 10f, 82f, 80f, 70f, 90f); poly(false, null, 24f, 18f, 36f, 10f, 82f, 10f); ci(47f, 42f, 9f) }
            Icon.DJEMBE -> { ov(24f, 10f, 76f, 24f, fl); p.reset(); p.moveTo(24f, 17f); p.cubicTo(28f, 50f, 44f, 52f, 42f, 62f); p.lineTo(34f, 92f); p.lineTo(66f, 92f); p.lineTo(58f, 62f); p.cubicTo(56f, 52f, 72f, 50f, 76f, 17f); c.drawPath(p, st); ln(30f, 30f, 50f, 54f); ln(70f, 30f, 50f, 54f) }
            Icon.TABLA -> { drum(30f, 36f, 34f, 46f, false); ov(52f, 40f, 94f, 52f, fl); p.reset(); p.moveTo(52f, 46f); p.cubicTo(52f, 92f, 94f, 92f, 94f, 46f); c.drawPath(p, st); dot(30f, 40f, 4f); dot(73f, 46f, 4f) }
            Icon.CHIMES -> { ln(8f, 14f, 92f, 14f); for (k in 0..7) { val x = 14f + k * 10; ln(x, 14f, x, 30f + k * 7); dot(x, 30f + k * 7, 2.6f) } }
            Icon.DRUM_MACHINE -> { rc(8f, 24f, 92f, 80f, 8f, fl); for (k in 0..3) for (j in 0..1) rc(16f + k * 19, 46f + j * 16, 30f + k * 19, 58f + j * 16, 2f, if ((k + j) % 3 == 0) solid else null); ln(16f, 34f, 50f, 34f); ci(80f, 34f, 4f) }
            Icon.PAD_GENERIC -> { rc(16f, 16f, 84f, 84f, 12f, fl); ci(50f, 50f, 14f) }
            Icon.DRUMKIT -> { ci(50f, 66f, 20f, fl); ci(50f, 66f, 6f); drum(30f, 30f, 22f, 14f, false); drum(68f, 30f, 22f, 14f, false); cymbal(18f, 18f, 30f, 0.28f); cymbal(84f, 22f, 26f, 0.28f); ln(18f, 22f, 14f, 90f); ln(84f, 26f, 88f, 90f) }
            Icon.STAR -> star(50f, 52f, 40f, 17f, 5)
            Icon.BOLT -> poly(true, fl, 58f, 6f, 22f, 56f, 46f, 56f, 38f, 94f, 78f, 40f, 52f, 40f)
            Icon.HEART -> { p.reset(); p.moveTo(50f, 88f); p.cubicTo(-10f, 46f, 22f, 0f, 50f, 30f); p.cubicTo(78f, 0f, 110f, 46f, 50f, 88f); c.drawPath(p, fl); c.drawPath(p, st) }
            Icon.FIRE -> { p.reset(); p.moveTo(50f, 92f); p.cubicTo(14f, 90f, 14f, 50f, 36f, 30f); p.cubicTo(36f, 46f, 44f, 50f, 46f, 50f); p.cubicTo(40f, 30f, 50f, 14f, 60f, 6f); p.cubicTo(60f, 26f, 90f, 40f, 82f, 70f); p.cubicTo(78f, 86f, 66f, 92f, 50f, 92f); c.drawPath(p, fl); c.drawPath(p, st); p.reset(); p.moveTo(50f, 86f); p.cubicTo(36f, 82f, 40f, 66f, 50f, 58f); p.cubicTo(60f, 66f, 64f, 82f, 50f, 86f); c.drawPath(p, st) }
            Icon.NOTE -> { ov(16f, 64f, 44f, 86f, solid); ln(42f, 74f, 42f, 14f); p.reset(); p.moveTo(42f, 14f); p.cubicTo(60f, 22f, 82f, 28f, 72f, 56f); c.drawPath(p, st) }
            Icon.HEADPHONES -> { arc(16f, 14f, 84f, 82f, 180f, 180f); rc(10f, 50f, 28f, 84f, 6f, fl); rc(72f, 50f, 90f, 84f, 6f, fl) }
            Icon.MIC -> { rc(36f, 8f, 64f, 56f, 14f, fl); arc(26f, 28f, 74f, 70f, 0f, 180f); ln(50f, 70f, 50f, 88f); ln(36f, 90f, 64f, 90f); ln(40f, 22f, 60f, 22f); ln(40f, 32f, 60f, 32f) }
            Icon.SPEAKER -> { rc(20f, 8f, 80f, 92f, 8f, fl); ci(50f, 62f, 18f); ci(50f, 62f, 6f, solid); ci(50f, 26f, 8f) }
            Icon.VINYL -> { ci(50f, 50f, 40f, fl); ci(50f, 50f, 30f); ci(50f, 50f, 12f, solid); arc(20f, 20f, 80f, 80f, 200f, 50f) }
            Icon.GUITAR -> { c.save(); c.rotate(40f, 50f, 50f); ci(50f, 70f, 18f, fl); ci(50f, 48f, 13f, fl); ci(50f, 66f, 5f); rc(46f, 4f, 54f, 50f, 2f); c.restore() }
            Icon.PIANO -> { rc(8f, 22f, 92f, 80f, 4f, fl); for (k in 1..6) ln(8f + k * 12, 22f, 8f + k * 12, 80f); for (k in listOf(1, 2, 4, 5, 6)) c.drawRect(8f + k * 12 - 4, 22f, 8f + k * 12 + 4, 56f, solid) }
            Icon.WAVE -> { p.reset(); p.moveTo(6f, 50f); var x = 6f; while (x < 94) { p.quadTo(x + 7, 20f, x + 14, 50f); p.quadTo(x + 21, 80f, x + 28, 50f); x += 28 }; c.drawPath(p, st) }
            Icon.CROWN -> poly(true, fl, 12f, 78f, 8f, 30f, 32f, 52f, 50f, 20f, 68f, 52f, 92f, 30f, 88f, 78f)
            Icon.SKULL -> { p.reset(); p.moveTo(26f, 76f); p.cubicTo(4f, 50f, 14f, 10f, 50f, 10f); p.cubicTo(86f, 10f, 96f, 50f, 74f, 76f); p.lineTo(74f, 90f); p.lineTo(26f, 90f); p.close(); c.drawPath(p, fl); c.drawPath(p, st); ci(36f, 48f, 9f, solid); ci(64f, 48f, 9f, solid); poly(true, solid, 50f, 60f, 45f, 70f, 55f, 70f); ln(40f, 80f, 40f, 90f); ln(50f, 80f, 50f, 90f); ln(60f, 80f, 60f, 90f) }
            Icon.ROCKET -> { c.save(); c.rotate(45f, 50f, 50f); p.reset(); p.moveTo(50f, 4f); p.cubicTo(70f, 20f, 66f, 60f, 62f, 72f); p.lineTo(38f, 72f); p.cubicTo(34f, 60f, 30f, 20f, 50f, 4f); c.drawPath(p, fl); c.drawPath(p, st); ci(50f, 34f, 7f); poly(false, null, 38f, 56f, 24f, 74f, 38f, 72f); poly(false, null, 62f, 56f, 76f, 74f, 62f, 72f); poly(false, null, 44f, 76f, 50f, 94f, 56f, 76f); c.restore() }
            Icon.MOON -> { p.reset(); p.addCircle(50f, 50f, 38f, Path.Direction.CW); val q = Path(); q.addCircle(68f, 38f, 32f, Path.Direction.CW); p.op(q, Path.Op.DIFFERENCE); c.drawPath(p, fl); c.drawPath(p, st) }
            Icon.SUN -> { ci(50f, 50f, 20f, fl); for (k in 0 until 8) { val a = Math.PI * k / 4; ln(50f + (28 * cos(a)).toFloat(), 50f + (28 * sin(a)).toFloat(), 50f + (42 * cos(a)).toFloat(), 50f + (42 * sin(a)).toFloat()) } }
            Icon.DIAMOND -> { poly(true, fl, 26f, 18f, 74f, 18f, 92f, 40f, 50f, 90f, 8f, 40f); ln(8f, 40f, 92f, 40f); poly(false, null, 26f, 18f, 38f, 40f, 50f, 90f, 62f, 40f, 74f, 18f) }
            Icon.ROBOT -> { rc(20f, 28f, 80f, 78f, 10f, fl); ci(38f, 48f, 7f, solid); ci(62f, 48f, 7f, solid); ln(36f, 66f, 64f, 66f); ln(50f, 28f, 50f, 14f); ci(50f, 10f, 4f); ln(20f, 52f, 10f, 52f); ln(80f, 52f, 90f, 52f); ln(36f, 78f, 36f, 92f); ln(64f, 78f, 64f, 92f) }
            Icon.GLOBE -> { ci(50f, 50f, 38f, fl); ov(32f, 12f, 68f, 88f); ln(12f, 50f, 88f, 50f); arc(18f, 22f, 82f, 48f, 0f, 180f); arc(18f, 52f, 82f, 78f, 180f, 180f) }
            Icon.LEAF -> { p.reset(); p.moveTo(14f, 86f); p.cubicTo(10f, 30f, 50f, 10f, 90f, 10f); p.cubicTo(90f, 60f, 60f, 92f, 14f, 86f); c.drawPath(p, fl); c.drawPath(p, st); ln(14f, 86f, 70f, 30f) }
            Icon.SPARKLE -> { star(40f, 56f, 34f, 9f, 4); star(76f, 22f, 16f, 5f, 4) }
            Icon.EQ -> { val hs = floatArrayOf(40f, 70f, 30f, 82f, 54f, 64f); for (k in hs.indices) rc(10f + k * 14, 92f - hs[k], 20f + k * 14, 92f, 2f, solid) }
            Icon.PALM -> { p.reset(); p.moveTo(50f, 92f); p.quadTo(44f, 60f, 54f, 30f); c.drawPath(p, st); for (a in listOf(-160f, -120f, -60f, -20f, 20f)) { c.save(); c.rotate(a, 54f, 30f); ov(54f, 24f, 92f, 36f, fl); c.restore() } }
            Icon.MASK -> { p.reset(); p.moveTo(10f, 30f); p.cubicTo(30f, 22f, 70f, 22f, 90f, 30f); p.cubicTo(90f, 70f, 60f, 74f, 50f, 60f); p.cubicTo(40f, 74f, 10f, 70f, 10f, 30f); c.drawPath(p, fl); c.drawPath(p, st); ov(24f, 36f, 42f, 48f, solid); ov(58f, 36f, 76f, 48f, solid) }
            Icon.FLAME_SKULL -> { poly(false, null, 20f, 50f, 14f, 14f, 32f, 34f, 40f, 8f, 50f, 30f, 60f, 8f, 68f, 34f, 86f, 14f, 80f, 50f); ov(24f, 40f, 76f, 86f, fl); ci(40f, 60f, 6f, solid); ci(60f, 60f, 6f, solid); ln(44f, 76f, 56f, 76f) }
            Icon.CLOUD -> { p.reset(); p.moveTo(22f, 76f); p.cubicTo(2f, 76f, 4f, 48f, 24f, 50f); p.cubicTo(24f, 26f, 56f, 20f, 62f, 40f); p.cubicTo(84f, 32f, 98f, 60f, 80f, 76f); p.close(); c.drawPath(p, fl); c.drawPath(p, st) }
            Icon.TRUMPET -> { ln(8f, 50f, 60f, 50f); poly(true, fl, 60f, 44f, 92f, 26f, 92f, 74f, 60f, 56f); rc(22f, 40f, 46f, 62f, 6f); for (k in 0..2) ln(28f + k * 8, 30f, 28f + k * 8, 40f) }
            Icon.SAX -> { p.reset(); p.moveTo(40f, 8f); p.lineTo(46f, 16f); p.lineTo(40f, 66f); p.cubicTo(38f, 92f, 76f, 94f, 74f, 64f); p.lineTo(84f, 52f); c.drawPath(p, st); for (k in 0..3) dot(44f - k, 30f + k * 9, 3.2f) }
            Icon.CROSS_STICKS -> { stick(12f, 88f, 84f, 14f); stick(88f, 88f, 16f, 14f); arc(30f, 30f, 70f, 70f, 200f, 140f) }
            Icon.CIRCLE_WAVE -> { ci(50f, 50f, 38f, fl); poly(false, null, 18f, 50f, 34f, 50f, 42f, 28f, 54f, 74f, 62f, 40f, 68f, 50f, 82f, 50f) }
        }
    }

    private fun drawGlyph(g: Glyph) {
        when (g) {
            Glyph.PLAY -> poly(true, solid, 30f, 18f, 82f, 50f, 30f, 82f)
            Glyph.STOP -> rc(24f, 24f, 76f, 76f, 6f, solid)
            Glyph.RECORD -> ci(50f, 50f, 30f, solid)
            Glyph.METRONOME -> drawIcon(Icon.METRONOME)
            Glyph.AUTOPLAY -> { rc(12f, 12f, 88f, 88f, 18f, fl); dot(32f, 32f, 7f); dot(68f, 32f, 7f); dot(50f, 50f, 7f); dot(32f, 68f, 7f); dot(68f, 68f, 7f) }
            Glyph.MENU -> { ln(16f, 28f, 84f, 28f); ln(16f, 50f, 84f, 50f); ln(16f, 72f, 84f, 72f) }
            Glyph.KITS -> drawIcon(Icon.DRUMKIT)
            Glyph.PADS -> { for (k in 0..2) for (j in 0..2) rc(12f + k * 27, 12f + j * 27, 34f + k * 27, 34f + j * 27, 4f, if (k == 1 && j == 1) solid else null) }
            Glyph.DRUMVIEW -> { cymbal(26f, 30f, 36f); cymbal(76f, 26f, 36f); ci(50f, 66f, 22f, fl); ci(50f, 66f, 8f) }
            Glyph.MIDI -> { ci(50f, 50f, 36f, fl); for (k in 0..4) { val a = Math.PI + Math.PI * k / 4; dot(50f + (22 * cos(a)).toFloat(), 54f + (22 * sin(a)).toFloat(), 4.5f) }; rc(42f, 76f, 58f, 86f, 2f, solid) }
            Glyph.LEARN -> { ci(50f, 50f, 34f, fl); ci(50f, 50f, 16f); dot(50f, 50f, 6f); ln(50f, 6f, 50f, 22f); ln(50f, 78f, 50f, 94f); ln(6f, 50f, 22f, 50f); ln(78f, 50f, 94f, 50f) }
            Glyph.PLUS -> { ln(50f, 16f, 50f, 84f); ln(16f, 50f, 84f, 50f) }
            Glyph.MINUS -> ln(16f, 50f, 84f, 50f)
            Glyph.TRASH -> { ln(16f, 24f, 84f, 24f); rc(38f, 12f, 62f, 24f, 2f); poly(false, fl, 24f, 24f, 30f, 90f, 70f, 90f, 76f, 24f); ln(42f, 38f, 42f, 76f); ln(58f, 38f, 58f, 76f) }
            Glyph.UNDO -> { arc(22f, 22f, 82f, 82f, 180f, 230f); poly(false, null, 8f, 40f, 22f, 54f, 36f, 40f) }
            Glyph.SAVE -> { poly(true, fl, 14f, 14f, 72f, 14f, 86f, 28f, 86f, 86f, 14f, 86f); rc(28f, 14f, 66f, 36f, 2f); rc(26f, 54f, 74f, 86f, 2f) }
            Glyph.EXPORT -> { ln(50f, 64f, 50f, 10f); poly(false, null, 30f, 30f, 50f, 10f, 70f, 30f); poly(false, null, 18f, 54f, 18f, 88f, 82f, 88f, 82f, 54f) }
            Glyph.FOLDER -> poly(true, fl, 10f, 24f, 40f, 24f, 48f, 34f, 90f, 34f, 90f, 82f, 10f, 82f)
            Glyph.GEAR -> { star(50f, 50f, 40f, 30f, 8); ci(50f, 50f, 13f) }
            Glyph.BACK -> { ln(84f, 50f, 18f, 50f); poly(false, null, 44f, 22f, 16f, 50f, 44f, 78f) }
            Glyph.RHYTHM -> { ov(14f, 64f, 38f, 82f, solid); ov(56f, 58f, 80f, 76f, solid); ln(36f, 72f, 36f, 22f); ln(78f, 66f, 78f, 16f); ln(36f, 22f, 78f, 16f); ln(36f, 32f, 78f, 26f) }
            Glyph.CHECK -> poly(false, null, 16f, 52f, 40f, 76f, 86f, 26f)
            Glyph.EDIT -> { poly(true, fl, 20f, 70f, 66f, 24f, 78f, 36f, 32f, 82f, 16f, 86f); ln(58f, 32f, 70f, 44f) }
            Glyph.QUANTIZE -> { for (k in 0..4) ln(14f + k * 18, 14f, 14f + k * 18, 86f); for ((x, y) in listOf(14f to 30f, 50f to 54f, 68f to 70f)) rc(x - 6, y - 6, x + 6, y + 6, 2f, solid) }
            Glyph.BLUETOOTH -> poly(false, null, 28f, 30f, 72f, 68f, 50f, 88f, 50f, 12f, 72f, 32f, 28f, 70f)
            Glyph.MORE -> { dot(22f, 50f, 7f); dot(50f, 50f, 7f); dot(78f, 50f, 7f) }
        }
    }
}
