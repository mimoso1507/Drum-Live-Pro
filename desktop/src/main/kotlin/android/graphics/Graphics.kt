@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package android.graphics

// Tiny subset of android.graphics implemented on top of Java2D, so the drawing code of the Android app
// (instrument icons, pads, virtual kit, knobs) runs unchanged in the Windows version.

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Font
import java.awt.Graphics2D
import java.awt.MultipleGradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

fun awtColor(argb: Int) = java.awt.Color(argb, true)

class RectF(@JvmField var left: Float = 0f, @JvmField var top: Float = 0f, @JvmField var right: Float = 0f, @JvmField var bottom: Float = 0f) {
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
    fun width() = right - left
    fun height() = bottom - top
    fun centerX() = (left + right) / 2f
    fun centerY() = (top + bottom) / 2f
    fun contains(x: Float, y: Float) = left < right && top < bottom && x >= left && x < right && y >= top && y < bottom
}

class Typeface private constructor(val family: String, val style: Int) {
    companion object {
        const val NORMAL = 0
        const val BOLD = 1
        @JvmField val SERIF = Typeface(Font.SERIF, NORMAL)
        @JvmField val DEFAULT = Typeface(Font.SANS_SERIF, NORMAL)
        fun create(base: Typeface?, style: Int) = Typeface(base?.family ?: Font.SANS_SERIF, style)
    }
}

abstract class Shader {
    enum class TileMode { CLAMP, REPEAT, MIRROR }
    internal abstract fun toAwt(): java.awt.Paint
}

class LinearGradient(private val x0: Float, private val y0: Float, private val x1: Float, private val y1: Float,
                     private val c0: Int, private val c1: Int, private val tile: TileMode) : Shader() {
    override fun toAwt(): java.awt.Paint {
        if (x0 == x1 && y0 == y1) return awtColor(c0)
        return java.awt.LinearGradientPaint(Point2D.Float(x0, y0), Point2D.Float(x1, y1), floatArrayOf(0f, 1f), arrayOf(awtColor(c0), awtColor(c1)))
    }
}

class RadialGradient : Shader {
    private val cx: Float; private val cy: Float; private val r: Float
    private val colors: IntArray; private val stops: FloatArray
    constructor(cx: Float, cy: Float, r: Float, c0: Int, c1: Int, tile: TileMode) : super() {
        this.cx = cx; this.cy = cy; this.r = r; colors = intArrayOf(c0, c1); stops = floatArrayOf(0f, 1f)
    }
    constructor(cx: Float, cy: Float, r: Float, colors: IntArray, stops: FloatArray?, tile: TileMode) : super() {
        this.cx = cx; this.cy = cy; this.r = r; this.colors = colors
        this.stops = stops ?: FloatArray(colors.size) { if (colors.size == 1) 0f else it.toFloat() / (colors.size - 1) }
    }
    override fun toAwt(): java.awt.Paint {
        // Java2D requires strictly increasing stops
        val st = stops.copyOf()
        for (i in 1 until st.size) if (st[i] <= st[i - 1]) st[i] = (st[i - 1] + 0.0001f).coerceAtMost(1f)
        return java.awt.RadialGradientPaint(Point2D.Float(cx, cy), r.coerceAtLeast(0.01f), st, colors.map { awtColor(it) }.toTypedArray(),
            MultipleGradientPaint.CycleMethod.NO_CYCLE)
    }
}

class Paint(flags: Int = 0) {
    companion object { const val ANTI_ALIAS_FLAG = 1 }
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }
    enum class Align { LEFT, CENTER, RIGHT }

    var color: Int = 0xFF000000.toInt()
    var style = Style.FILL
    var strokeWidth = 0f
    var strokeCap = Cap.BUTT
    var strokeJoin = Join.MITER
    var textSize = 12f
    var textAlign = Align.LEFT
    var typeface: Typeface? = null
    var shader: Shader? = null

    internal fun font(): Font {
        val tf = typeface ?: Typeface.DEFAULT
        return Font(tf.family, if (tf.style == Typeface.BOLD) Font.BOLD else Font.PLAIN, 1).deriveFont(textSize)
    }

    internal fun stroke() = BasicStroke(if (strokeWidth <= 0f) 1f else strokeWidth,
        when (strokeCap) { Cap.BUTT -> BasicStroke.CAP_BUTT; Cap.ROUND -> BasicStroke.CAP_ROUND; Cap.SQUARE -> BasicStroke.CAP_SQUARE },
        when (strokeJoin) { Join.MITER -> BasicStroke.JOIN_MITER; Join.ROUND -> BasicStroke.JOIN_ROUND; Join.BEVEL -> BasicStroke.JOIN_BEVEL })

    fun measureText(s: String): Float = font().getStringBounds(s, FRC).width.toFloat()
}

private val FRC = FontRenderContext(null, true, true)

class Path {
    enum class Direction { CW, CCW }
    enum class Op { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }
    internal var p = Path2D.Float()
    fun reset() { p = Path2D.Float() }
    fun moveTo(x: Float, y: Float) = p.moveTo(x, y)
    fun lineTo(x: Float, y: Float) { if (p.currentPoint == null) p.moveTo(x, y) else p.lineTo(x, y) }
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) = p.quadTo(x1, y1, x2, y2)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = p.curveTo(x1, y1, x2, y2, x3, y3)
    fun close() = p.closePath()
    fun arcTo(oval: RectF, start: Float, sweep: Float) {
        p.append(Arc2D.Float(oval.left, oval.top, oval.width(), oval.height(), -start, -sweep, Arc2D.OPEN), p.currentPoint != null)
    }
    fun addCircle(x: Float, y: Float, r: Float, dir: Direction) = p.append(Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r), false)
    fun op(other: Path, op: Op): Boolean {
        val a = Area(p); val b = Area(other.p)
        when (op) {
            Op.DIFFERENCE -> a.subtract(b); Op.INTERSECT -> a.intersect(b); Op.UNION -> a.add(b); Op.XOR -> a.exclusiveOr(b)
            Op.REVERSE_DIFFERENCE -> { b.subtract(a); p = Path2D.Float(b); return true }
        }
        p = Path2D.Float(a); return true
    }
}

/** Canvas that draws on a Java2D [Graphics2D]. */
class Canvas(val g: Graphics2D) {
    private val stack = ArrayList<Pair<AffineTransform, Shape?>>()

    init {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }

    fun save(): Int { stack += g.transform to g.clip; return stack.size }
    fun restore() { val (t, c) = stack.removeAt(stack.size - 1); g.transform = t; g.clip = c }
    fun translate(dx: Float, dy: Float) = g.translate(dx.toDouble(), dy.toDouble())
    fun scale(sx: Float, sy: Float) = g.scale(sx.toDouble(), sy.toDouble())
    fun rotate(deg: Float) = g.rotate(Math.toRadians(deg.toDouble()))
    fun rotate(deg: Float, px: Float, py: Float) = g.rotate(Math.toRadians(deg.toDouble()), px.toDouble(), py.toDouble())
    fun clipRect(l: Float, t: Float, r: Float, b: Float) = g.clip(Rectangle2D.Float(l, t, r - l, b - t))

    private fun shape(s: Shape, p: Paint) {
        g.composite = AlphaComposite.SrcOver
        g.paint = p.shader?.toAwt() ?: awtColor(p.color)
        if (p.style != Paint.Style.STROKE) g.fill(s)
        if (p.style != Paint.Style.FILL) { g.stroke = p.stroke(); g.draw(s) }
    }

    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, p: Paint) {
        g.paint = p.shader?.toAwt() ?: awtColor(p.color); g.stroke = p.stroke(); g.draw(Line2D.Float(x1, y1, x2, y2))
    }
    fun drawCircle(x: Float, y: Float, r: Float, p: Paint) = shape(Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r), p)
    fun drawOval(r: RectF, p: Paint) = shape(Ellipse2D.Float(r.left, r.top, r.width(), r.height()), p)
    fun drawOval(l: Float, t: Float, r: Float, b: Float, p: Paint) = shape(Ellipse2D.Float(l, t, r - l, b - t), p)
    fun drawRect(l: Float, t: Float, r: Float, b: Float, p: Paint) = shape(Rectangle2D.Float(l, t, r - l, b - t), p)
    fun drawRect(r: RectF, p: Paint) = drawRect(r.left, r.top, r.right, r.bottom, p)
    fun drawRoundRect(r: RectF, rx: Float, ry: Float, p: Paint) = drawRoundRect(r.left, r.top, r.right, r.bottom, rx, ry, p)
    fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: Paint) =
        shape(RoundRectangle2D.Float(l, t, r - l, b - t, rx * 2, ry * 2), p)
    fun drawArc(r: RectF, start: Float, sweep: Float, useCenter: Boolean, p: Paint) =
        shape(Arc2D.Float(r.left, r.top, r.width(), r.height(), -start, -sweep, if (useCenter) Arc2D.PIE else Arc2D.OPEN), p)
    fun drawPath(path: Path, p: Paint) = shape(path.p, p)

    fun drawText(s: String, x: Float, y: Float, p: Paint) {
        val f = p.font()
        g.font = f
        g.paint = awtColor(p.color)
        val w = if (p.textAlign == Paint.Align.LEFT) 0f else f.getStringBounds(s, g.fontRenderContext).width.toFloat()
        val dx = when (p.textAlign) { Paint.Align.LEFT -> 0f; Paint.Align.CENTER -> -w / 2; Paint.Align.RIGHT -> -w }
        g.drawString(s, x + dx, y)
    }
}
