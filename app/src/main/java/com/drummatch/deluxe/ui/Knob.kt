package com.drummatch.deluxe.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Rotary knob: drag up/down (or around) to change. Value is normalised 0..1. */
class Knob(ctx: Context, val title: String, var value: Float, val format: (Float) -> String, val onChange: (Float) -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL) }
    private val r = RectF()
    private var lastY = 0f

    fun set(v: Float) { value = v.coerceIn(0f, 1f); invalidate() }

    override fun onMeasure(w: Int, h: Int) {
        val width = resolveSize(dpi(110), w)
        setMeasuredDimension(width, resolveSize(dpi(130), h))
    }

    override fun onDraw(c: Canvas) {
        val th = dp(18f)
        tp.textSize = dp(13f); tp.color = T.TEXT2
        c.drawText(title, width / 2f, th - dp(3f), tp)
        val size = minOf(width.toFloat(), height - th * 2) - dp(14f)
        val cx = width / 2f; val cy = th + (height - th * 2) / 2f
        r.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
        p.strokeWidth = dp(4f); p.color = 0x55A4A6C2
        c.drawArc(r, 135f, 270f, false, p)
        p.color = T.CYAN
        c.drawArc(r, 135f, 270f * value, false, p)
        val a = Math.toRadians(135.0 + 270.0 * value)
        p.style = Paint.Style.FILL
        c.drawCircle(cx + (size / 2 * cos(a)).toFloat(), cy + (size / 2 * sin(a)).toFloat(), dp(7f), p)
        p.style = Paint.Style.STROKE
        tp.textSize = dp(14f); tp.color = T.TEXT
        c.drawText(format(value), cx, height - dp(4f), tp)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastY = e.y; parent?.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastY - e.y; lastY = e.y
                set(value + dy / dp(180f)); onChange(value)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}
