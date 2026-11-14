package com.drumlivepro.desk

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.drumlivepro.app.model.Icon
import com.drumlivepro.app.ui.Glyph
import com.drumlivepro.app.ui.IconPainter
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.plaf.basic.BasicScrollBarUI
import kotlin.math.roundToInt

/** One color theme for the whole interface (same 8 themes as the Android app). */
class Palette(
    val name: String, val bg: Int, val bgDeep: Int, val btn: Int, val btnSel: Int, val border: Int,
    val accent: Int, val mint: Int, val text2: Int, val pad: Int, val padTop: Int, val padHit: Int,
    val navSel: Int, val kitBg: Int, val dotOff: Int,
)

private fun c(v: Long) = v.toInt()

val THEMES = listOf(
    Palette("Grafito", c(0xFF1C1F26), c(0xFF14161B), c(0xFF262A33), c(0xFF34404F), c(0xFF5C6370), c(0xFF8DB8D8), c(0xFF8FC9A3), c(0xFFA3A9B4), c(0xFF20242B), c(0xFF2A2F38), c(0xFF3E5870), c(0xFF2C3440), c(0xFF2A303A), c(0xFF27342C)),
    Palette("Noche suave", c(0xFF1A2236), c(0xFF121828), c(0xFF222C45), c(0xFF2D3D62), c(0xFF56627E), c(0xFF8AB4F8), c(0xFF81C995), c(0xFFA5AECB), c(0xFF1C2439), c(0xFF26304A), c(0xFF34508A), c(0xFF283556), c(0xFF26304C), c(0xFF22362C)),
    Palette("Bosque", c(0xFF1B2620), c(0xFF131B16), c(0xFF23302A), c(0xFF2F4438), c(0xFF5B6E62), c(0xFF9FD0A2), c(0xFFD4E3A0), c(0xFFA7B8AC), c(0xFF1E2A23), c(0xFF27362D), c(0xFF3D6650), c(0xFF2A3D32), c(0xFF26352C), c(0xFF2C3A2A)),
    Palette("Océano", c(0xFF142629), c(0xFF0D1C1E), c(0xFF1B3337), c(0xFF22494E), c(0xFF4F6F72), c(0xFF72C7C7), c(0xFFA3D9B1), c(0xFF9DB8BA), c(0xFF17292C), c(0xFF1F363A), c(0xFF2C6168), c(0xFF1F4247), c(0xFF1E3538), c(0xFF1F3A30)),
    Palette("Lavanda", c(0xFF221F2E), c(0xFF181621), c(0xFF2C2840), c(0xFF3C3558), c(0xFF6A6385), c(0xFFB9A6E8), c(0xFF9ED9C6), c(0xFFADA7C4), c(0xFF242132), c(0xFF2F2A42), c(0xFF4E4278), c(0xFF372F50), c(0xFF2E2A40), c(0xFF283A36)),
    Palette("Vino", c(0xFF2A1C22), c(0xFF1E1418), c(0xFF36242C), c(0xFF4B2F3C), c(0xFF7A5F69), c(0xFFE3A6B8), c(0xFFF0C9A0), c(0xFFC0A6AF), c(0xFF2C1E24), c(0xFF3A2830), c(0xFF6A3A4E), c(0xFF452B37), c(0xFF3A2830), c(0xFF3A3024)),
    Palette("Arena", c(0xFF2A2620), c(0xFF1E1B16), c(0xFF36312A), c(0xFF4A4135), c(0xFF7D7262), c(0xFFE6C47F), c(0xFFB9D08F), c(0xFFC2B8A6), c(0xFF2D2822), c(0xFF3A342B), c(0xFF6B5A38), c(0xFF443C30), c(0xFF3A342B), c(0xFF34382A)),
    Palette("Azul real (original)", c(0xFF00007A), c(0xFF000052), c(0xFF05061C), c(0xFF0B2A9A), c(0xFFA4A6C2), c(0xFF33D6F7), c(0xFF5BE3A2), c(0xFFBFC2E6), c(0xFF080A2E), c(0xFF10133F), c(0xFF1B4FD8), c(0xFF0B1B6E), c(0xFF1A2370), c(0xFF1E3A2E)),
)

object T {
    var BG = 0; var BG_DEEP = 0; var BTN = 0; var BTN_SEL = 0; var BORDER = 0
    var CYAN = 0; var MINT = 0; var TEXT2 = 0; var PAD = 0; var PAD_TOP = 0; var PAD_HIT = 0
    var KIT_BG = 0; var DOT_OFF = 0
    const val TEXT = 0xFFFFFFFF.toInt()
    const val RED = 0xFFFF4D5E.toInt()
    var index = 0; private set
    init { apply(0) }
    fun apply(i: Int) {
        index = i.coerceIn(0, THEMES.size - 1)
        val p = THEMES[index]
        BG = p.bg; BG_DEEP = p.bgDeep; BTN = p.btn; BTN_SEL = p.btnSel; BORDER = p.border
        CYAN = p.accent; MINT = p.mint; TEXT2 = p.text2; PAD = p.pad; PAD_TOP = p.padTop; PAD_HIT = p.padHit
        KIT_BG = p.kitBg; DOT_OFF = p.dotOff
    }
}

/** UI scale ("dp"): the layout is designed like a landscape phone, scaled to the screen height. */
object Dp { var s = 1f }
fun dp(v: Float) = v * Dp.s
fun dpi(v: Int) = (v * Dp.s).roundToInt()
fun col(argb: Int) = Color(argb, true)
fun serif(size: Float, bold: Boolean = false): Font = Font(Font.SERIF, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont(dp(size))

fun Graphics.hq(): Graphics2D = (this as Graphics2D).also {
    it.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    it.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    it.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
}

fun roundBox(g: Graphics2D, x: Float, y: Float, w: Float, h: Float, r: Float, fill: Int, stroke: Int, sw: Float) {
    val s = RoundRectangle2D.Float(x + sw / 2, y + sw / 2, w - sw, h - sw, r * 2, r * 2)
    g.color = col(fill); g.fill(s)
    if (sw > 0) { g.color = col(stroke); g.stroke = BasicStroke(sw); g.draw(s) }
}

/** Fixed size helper for BoxLayout rows. */
fun <C : JComponent> C.fixed(): C = apply { maximumSize = preferredSize }

/** Vertical box whose children are left aligned and stretch horizontally. */
open class VBox(padDp: Int = 0) : JPanel(), Scrollable {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
        border = BorderFactory.createEmptyBorder(dpi(padDp), dpi(padDp), dpi(padDp), dpi(padDp))
    }
    override fun addImpl(comp: Component, constraints: Any?, index: Int) {
        (comp as? JComponent)?.alignmentX = 0f
        super.addImpl(comp, constraints, index)
    }
    fun gap(d: Int) { add(Box.createRigidArea(Dimension(dpi(d), dpi(d)))) }
    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(r: Rectangle, o: Int, d: Int) = dpi(24)
    override fun getScrollableBlockIncrement(r: Rectangle, o: Int, d: Int) = r.height - dpi(24)
    override fun getScrollableTracksViewportWidth() = true
    override fun getScrollableTracksViewportHeight() = false
}

class HBox(padDp: Int = 0) : JPanel() {
    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS); isOpaque = false; alignmentX = 0f
        border = BorderFactory.createEmptyBorder(dpi(padDp), dpi(padDp), dpi(padDp), dpi(padDp))
    }
    override fun addImpl(comp: Component, constraints: Any?, index: Int) {
        (comp as? JComponent)?.alignmentY = 0.5f
        super.addImpl(comp, constraints, index)
    }
    fun gap(d: Int) { add(Box.createRigidArea(Dimension(dpi(d), dpi(d)))) }
    fun glue() { add(Box.createHorizontalGlue()) }
    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

/** Text label that wraps long lines. */
class Label(text: String, size: Float = 15f, color: Int = T.TEXT, bold: Boolean = false) : JTextArea(text) {
    init {
        font = serif(size, bold); foreground = col(color); isOpaque = false; isEditable = false; isFocusable = false
        lineWrap = true; wrapStyleWord = true; border = null; highlighter = null
        (caret as? javax.swing.text.DefaultCaret)?.updatePolicy = javax.swing.text.DefaultCaret.NEVER_UPDATE
        cursor = Cursor.getDefaultCursor()
    }
    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

/** Rounded text button in the app style. */
class RButton(text: String, var selected: Boolean = false, size: Float = 15f, val onClick: (RButton) -> Unit) : JComponent() {
    var text = text; set(v) { field = v; revalidate(); repaint() }
    private var hover = false; private var down = false
    var center = true
    init {
        font = serif(size); cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR); isFocusable = false
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; down = false; repaint() }
            override fun mousePressed(e: MouseEvent) { if (e.button == MouseEvent.BUTTON1) { down = true; repaint() } }
            override fun mouseReleased(e: MouseEvent) {
                val was = down; down = false; repaint()
                if (was && e.button == MouseEvent.BUTTON1 && contains(e.point)) onClick(this@RButton)
            }
        })
    }
    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        return Dimension(fm.stringWidth(text) + dpi(28), fm.height + dpi(18))
    }
    override fun getMaximumSize(): Dimension = if (center) preferredSize else Dimension(Int.MAX_VALUE, preferredSize.height)
    override fun paintComponent(g0: Graphics) {
        val g = g0.hq()
        val sw = dp(if (selected) 2.4f else 1.6f)
        var fill = if (selected) T.BTN_SEL else T.BTN
        if (hover) fill = blend(fill, T.BORDER, 0.18f)
        if (down) fill = blend(fill, T.CYAN, 0.25f)
        roundBox(g, 0f, 0f, width.toFloat(), height.toFloat(), dp(12f), fill, if (selected) T.CYAN else T.BORDER, sw)
        g.font = font; g.color = col(T.TEXT)
        val fm = g.fontMetrics
        var t = text
        while (t.length > 2 && fm.stringWidth(t) > width - dpi(12)) t = t.dropLast(2) + "…"
        g.drawString(t, (width - fm.stringWidth(t)) / 2, (height - fm.height) / 2 + fm.ascent)
    }
}

fun blend(a: Int, b: Int, f: Float): Int {
    val t = f.coerceIn(0f, 1f)
    fun ch(s: Int) = (((a shr s) and 0xff) * (1 - t) + ((b shr s) and 0xff) * t).toInt() shl s
    return (0xff shl 24) or ch(16) or ch(8) or ch(0)
}

/** Square button that draws an icon/glyph (or short bold text). */
class GlyphButton(var glyph: Glyph? = null, var icon: Icon? = null, var label: String? = null, val onClick: () -> Unit) : JComponent() {
    var selected2 = false; set(v) { field = v; repaint() }
    var tint = T.TEXT; set(v) { field = v; repaint() }
    var badge: Int? = null; set(v) { field = v; repaint() }
    var tip: String? = null; set(v) { field = v; toolTipText = v }
    private var down = false; private var hover = false
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
    init {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR); isFocusable = false
        preferredSize = Dimension(dpi(54), dpi(44)); maximumSize = preferredSize; minimumSize = preferredSize
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; down = false; repaint() }
            override fun mousePressed(e: MouseEvent) { if (e.button == MouseEvent.BUTTON1) { down = true; repaint() } }
            override fun mouseReleased(e: MouseEvent) { val w = down; down = false; repaint(); if (w && contains(e.point)) onClick() }
        })
    }
    override fun paintComponent(g0: Graphics) {
        val c = Canvas(g0.create() as Graphics2D)
        val g = c.g
        val sw = dp(if (selected2) 2.4f else 1.6f)
        var fill = if (selected2) T.BTN_SEL else T.BTN
        if (hover) fill = blend(fill, T.BORDER, 0.18f)
        if (down) fill = blend(fill, T.CYAN, 0.3f)
        roundBox(g, 0f, 0f, width.toFloat(), height.toFloat(), dp(12f), fill, if (selected2) T.CYAN else T.BORDER, sw)
        val s = minOf(width, height) * 0.58f
        when {
            label != null -> { tp.color = tint; tp.textSize = height * 0.36f; c.drawText(label!!, width / 2f, height / 2f + tp.textSize * 0.36f, tp) }
            glyph != null -> IconPainter.glyph(c, glyph!!, width / 2f, height / 2f, s, tint)
            icon != null -> IconPainter.draw(c, icon!!, width / 2f, height / 2f, s, tint)
        }
        badge?.let { g.color = col(it); val r = dp(4.5f); g.fill(java.awt.geom.Ellipse2D.Float(width - dp(9f) - r, dp(9f) - r, 2 * r, 2 * r)) }
        g.dispose()
    }
}

/** Slider (0..max). */
class Slider(val max: Int, value: Int, val onChange: (Int) -> Unit) : JComponent() {
    var value = value.coerceIn(0, max); set(v) { field = v.coerceIn(0, max); repaint() }
    init {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        preferredSize = Dimension(dpi(200), dpi(30))
        val m = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = drag(e)
            override fun mouseDragged(e: MouseEvent) = drag(e)
        }
        addMouseListener(m); addMouseMotionListener(m)
        addMouseWheelListener { e -> val step = if (max > 300) max / 100 else 1; set(this.value - e.wheelRotation * step) }
    }
    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    private fun drag(e: MouseEvent) {
        val pad = dp(10f); val w = width - 2 * pad
        set(((e.x - pad) / w * max).roundToInt())
    }
    private fun set(v: Int) { val n = v.coerceIn(0, max); if (n != value) { value = n; onChange(n) } }
    override fun paintComponent(g0: Graphics) {
        val g = g0.hq()
        val pad = dp(10f); val w = width - 2 * pad; val cy = height / 2f; val th = dp(4f)
        g.color = col(T.BORDER); g.fill(RoundRectangle2D.Float(pad, cy - th / 2, w, th, th, th))
        val x = pad + w * value / max.coerceAtLeast(1)
        g.color = col(T.CYAN); g.fill(RoundRectangle2D.Float(pad, cy - th / 2, x - pad, th, th, th))
        val r = dp(8f); g.fill(java.awt.geom.Ellipse2D.Float(x - r, cy - r, 2 * r, 2 * r))
    }
}

/** On/off switch. */
class Switch(on: Boolean, val onChange: (Boolean) -> Unit) : JComponent() {
    var on = on; set(v) { field = v; repaint() }
    init {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        preferredSize = Dimension(dpi(46), dpi(26)); maximumSize = preferredSize; minimumSize = preferredSize
        addMouseListener(object : MouseAdapter() { override fun mouseClicked(e: MouseEvent) { this@Switch.on = !this@Switch.on; onChange(this@Switch.on) } })
    }
    override fun paintComponent(g0: Graphics) {
        val g = g0.hq()
        val h = height.toFloat(); val w = width.toFloat()
        g.color = col(if (on) (T.CYAN and 0xFFFFFF) or 0x88000000.toInt() else (T.BORDER and 0xFFFFFF) or 0x66000000)
        g.fill(RoundRectangle2D.Float(0f, h * 0.2f, w, h * 0.6f, h * 0.6f, h * 0.6f))
        g.color = col(if (on) T.CYAN else T.BORDER)
        val r = h * 0.42f; val x = if (on) w - r - 1 else r + 1
        g.fill(java.awt.geom.Ellipse2D.Float(x - r, h / 2 - r, 2 * r, 2 * r))
    }
}

class Input(hint: String, value: String = "") : JTextField(value) {
    private val hintText = hint
    init {
        font = serif(15f); foreground = col(T.TEXT); caretColor = col(T.TEXT); isOpaque = false
        border = BorderFactory.createEmptyBorder(dpi(10), dpi(12), dpi(10), dpi(12))
        selectionColor = col(T.BTN_SEL); selectedTextColor = col(T.TEXT)
    }
    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    override fun paintComponent(g0: Graphics) {
        val g = g0.hq()
        roundBox(g, 0f, 0f, width.toFloat(), height.toFloat(), dp(10f), T.BTN, if (hasFocus()) T.CYAN else T.BORDER, dp(1.6f))
        super.paintComponent(g0)
        if (text.isEmpty() && !hasFocus()) {
            g.font = font; g.color = col(T.TEXT2)
            g.drawString(hintText, insets.left, insets.top + g.fontMetrics.ascent)
        }
    }
}

/** Panel that paints a rounded box behind its children. */
class Card(private val fill: Int, private val stroke: Int, padDp: Int = 10, private val radius: Float = 14f) : VBox(padDp) {
    override fun paintComponent(g: Graphics) {
        roundBox(g.hq(), 0f, 0f, width.toFloat(), height.toFloat(), dp(radius), fill, stroke, dp(1.2f))
    }
    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

/** FlowLayout that wraps and reports the wrapped height (for chips / grids). */
class WrapLayout(align: Int = LEFT, hgap: Int = dpi(6), vgap: Int = dpi(6)) : FlowLayout(align, hgap, vgap) {
    override fun preferredLayoutSize(target: Container) = size(target, true)
    override fun minimumLayoutSize(target: Container) = size(target, false).also { it.width -= hgap + 1 }
    private fun size(target: Container, pref: Boolean): Dimension = synchronized(target.treeLock) {
        var tw = target.size.width
        var p: Container? = target
        while (tw == 0 && p?.parent != null) { p = p.parent; tw = p.size.width }
        if (tw == 0) tw = Int.MAX_VALUE
        val ins: Insets = target.insets
        val maxW = tw - (ins.left + ins.right + hgap * 2)
        var w = 0; var h = 0; var rowW = 0; var rowH = 0
        for (m in target.components) {
            if (!m.isVisible) continue
            val d = if (pref) m.preferredSize else m.minimumSize
            if (rowW + d.width > maxW && rowW > 0) { w = maxOf(w, rowW); h += rowH + vgap; rowW = 0; rowH = 0 }
            if (rowW != 0) rowW += hgap
            rowW += d.width; rowH = maxOf(rowH, d.height)
        }
        w = maxOf(w, rowW); h += rowH
        Dimension(w + ins.left + ins.right + hgap * 2, h + ins.top + ins.bottom + vgap * 2)
    }
}

class WrapPanel(align: Int = FlowLayout.LEFT) : JPanel(WrapLayout(align)) {
    init { isOpaque = false; alignmentX = 0f }
    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

fun scrollOf(v: JComponent): JScrollPane = JScrollPane(v).apply {
    border = null; isOpaque = false; viewport.isOpaque = false
    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    verticalScrollBar.unitIncrement = dpi(24)
    verticalScrollBar.preferredSize = Dimension(dpi(10), 0)
    verticalScrollBar.isOpaque = false
    verticalScrollBar.setUI(object : BasicScrollBarUI() {
        override fun configureScrollBarColors() { thumbColor = col(T.BORDER); trackColor = col(0) }
        override fun createDecreaseButton(o: Int) = zero()
        override fun createIncreaseButton(o: Int) = zero()
        private fun zero() = javax.swing.JButton().apply { preferredSize = Dimension(0, 0); minimumSize = preferredSize; maximumSize = preferredSize }
        override fun paintTrack(g: Graphics, c: JComponent, r: Rectangle) {}
        override fun paintThumb(g: Graphics, c: JComponent, r: Rectangle) {
            val gg = g.hq(); gg.color = col(T.BORDER)
            gg.fill(RoundRectangle2D.Float(r.x + dp(2f), r.y.toFloat(), r.width - dp(4f), r.height.toFloat(), dp(6f), dp(6f)))
        }
    })
}

// ---------- small builders mirroring the Android helpers ----------
fun section(title: String) = Label(title, 16f, T.CYAN, true).apply { border = BorderFactory.createEmptyBorder(dpi(14), 0, dpi(6), 0) }

fun sliderRow(name: String, max: Int, value: Int, fmt: (Int) -> String, onChange: (Int) -> Unit): VBox {
    val box = VBox()
    val head = HBox()
    val v = javax.swing.JLabel(fmt(value)).apply { font = serif(14f); foreground = col(T.CYAN) }
    head.add(javax.swing.JLabel(name).apply { font = serif(14f); foreground = col(T.TEXT2) }); head.glue(); head.add(v)
    box.add(head)
    box.add(Slider(max, value) { v.text = fmt(it); onChange(it) })
    return box
}

fun switchRow(name: String, on: Boolean, onChange: (Boolean) -> Unit) = HBox().apply {
    border = BorderFactory.createEmptyBorder(dpi(4), 0, dpi(4), 0)
    add(javax.swing.JLabel(name).apply { font = serif(15f); foreground = col(T.TEXT) }); glue(); add(Switch(on, onChange))
}

fun <V> chips(items: List<V>, selected: V, name: (V) -> String, onPick: (V) -> Unit): WrapPanel {
    val g = WrapPanel()
    val views = ArrayList<RButton>()
    for (it in items) {
        val b = RButton(name(it), it == selected, 14f) { _ -> views.forEachIndexed { i, v -> v.selected = items[i] == it; v.repaint() }; onPick(it) }
        views += b; g.add(b)
    }
    return g
}

