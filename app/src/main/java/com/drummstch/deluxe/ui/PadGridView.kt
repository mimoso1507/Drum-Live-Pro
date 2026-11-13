package com.drummstch.deluxe.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.drummstch.deluxe.model.PadConfig
import com.drummstch.deluxe.seq.PatternGen
import java.util.concurrent.atomic.AtomicLongArray

class PadGridView(ctx: Context) : View(ctx) {
    var pads: List<PadConfig> = emptyList(); set(v) { field = v; requestLayout(); invalidate() }
    var columns = 4
    var hitTimes: AtomicLongArray? = null
    var autoPlayMode = false; set(v) { field = v; invalidate() }
    var learnPad = -1; set(v) { field = v; invalidate() }
    var learnWaiting = false; set(v) { field = v; invalidate() }
    var velocityByPosition = false
    var keepAnimating: () -> Boolean = { false }

    var onHit: ((pad: Int, vel: Int) -> Unit)? = null
    var onLongPress: ((pad: Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = T.TEXT2; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL) }
    private val rect = RectF()
    private val localHit = LongArray(128)
    private val handler2 = Handler(Looper.getMainLooper())
    private var longRunnable: Runnable? = null
    private val pointerPad = HashMap<Int, Int>()
    private var longFired = false

    private val rows get() = (pads.size + columns - 1) / columns

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

    override fun onDraw(c: Canvas) {
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
            // body
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, blend(0xFF10133F.toInt(), T.PAD_HIT, flash), blend(T.PAD, 0xFF0E2E9E.toInt(), flash), Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, r, r, paint)
            paint.shader = null
            // border
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
            // icon
            val s = minOf(rect.width(), rect.height()) * 0.56f
            val iconColor = if (flash > 0f) blend(T.TEXT2, T.CYAN, flash) else if (pad.autoLevel > 0) T.MINT else 0xFFD2D4EC.toInt()
            IconPainter.draw(c, pad.displayIcon, rect.centerX(), rect.centerY() - rect.height() * 0.04f, s, iconColor)
            // name
            text.textSize = (rect.height() * 0.11f).coerceIn(dp(9f), dp(13f))
            if (rect.height() > dp(70f)) c.drawText(ellipsize(pad.displayName, rect.width() * 0.9f), rect.centerX(), rect.bottom - dp(7f), text)
            // autoplay dots
            if (autoPlayMode) {
                val n = PatternGen.MAX_LEVEL; val dr = dp(3.4f); val sp = dp(10f)
                val x0 = rect.centerX() - sp * (n - 1) / 2; val y = rect.top + dp(10f)
                for (k in 0 until n) {
                    paint.style = Paint.Style.FILL
                    paint.color = if (k < pad.autoLevel) T.MINT else 0xFF1E3A2E.toInt()
                    c.drawCircle(x0 + k * sp, y, dr, paint)
                }
            }
        }
        if (learnWaiting) {
            text.textSize = dp(15f); text.color = T.CYAN
            c.drawText(if (learnPad < 0) "Aprender: toca un pad…" else "Ahora presiona una tecla/pad del controlador MIDI", width / 2f, height - dp(4f), text)
            text.color = T.TEXT2
        }
        if (animating) postInvalidateOnAnimation()
    }

    private fun ellipsize(s: String, w: Float): String {
        if (text.measureText(s) <= w) return s
        var e = s.length
        while (e > 1 && text.measureText(s.substring(0, e) + "…") > w) e--
        return s.substring(0, e) + "…"
    }

    private fun blend(a: Int, b: Int, f: Float): Int {
        val t = f.coerceIn(0f, 1f)
        fun ch(s: Int) = (((a shr s) and 0xff) * (1 - t) + ((b shr s) and 0xff) * t).toInt() shl s
        return (0xff shl 24) or ch(16) or ch(8) or ch(0)
    }

    fun flash(i: Int) { if (i in localHit.indices) { localHit[i] = System.nanoTime(); postInvalidateOnAnimation() } }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                val x = e.getX(idx); val y = e.getY(idx)
                val pad = padAt(x, y)
                if (pad >= 0) {
                    pointerPad[e.getPointerId(idx)] = pad
                    var vel = 112
                    if (velocityByPosition) { padRect(pad, rect); vel = (127 - ((y - rect.top) / rect.height()) * 87).toInt().coerceIn(30, 127) }
                    flash(pad)
                    onHit?.invoke(pad, vel)
                    if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                        longFired = false
                        longRunnable?.let { handler2.removeCallbacks(it) }
                        longRunnable = Runnable { longFired = true; performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); onLongPress?.invoke(pad) }.also { handler2.postDelayed(it, 600) }
                    } else cancelLong()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val id = e.getPointerId(0)
                val p0 = pointerPad[id]
                if (p0 != null && padAt(e.getX(0), e.getY(0)) != p0) cancelLong()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { cancelLong(); pointerPad.clear() }
            MotionEvent.ACTION_POINTER_UP -> pointerPad.remove(e.getPointerId(e.actionIndex))
        }
        return true
    }

    private fun cancelLong() { longRunnable?.let { handler2.removeCallbacks(it) }; longRunnable = null }
}
