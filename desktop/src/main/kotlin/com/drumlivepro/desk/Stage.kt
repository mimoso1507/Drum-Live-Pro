package com.drumlivepro.desk

import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.LayoutManager
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.Timer

/**
 * Root of the window. Always lays the app out in landscape: if the window/screen is taller than wide
 * (for example a monitor rotated to portrait), the app is shown in a 16:9 band in the middle.
 * Dialogs and toasts are drawn inside the window (not as separate windows), so full screen is never interrupted.
 */
class Stage : JLayeredPane() {
    val dialogs = ArrayList<Dlg>()
    var main: JComponent? = null
        set(v) { field?.let { remove(it) }; field = v; if (v != null) add(v, DEFAULT_LAYER); revalidate(); repaint() }

    init {
        isOpaque = true
        layout = object : LayoutManager {
            override fun addLayoutComponent(name: String?, comp: Component?) {}
            override fun removeLayoutComponent(comp: Component?) {}
            override fun preferredLayoutSize(parent: Container) = Dimension(dpi(1280), dpi(720))
            override fun minimumLayoutSize(parent: Container) = Dimension(400, 225)
            override fun layoutContainer(parent: Container) { val r = area(); for (c in components) c.bounds = r }
        }
    }

    /** Landscape area: whole window, or a 16:9 band when the window is portrait. */
    fun area(): Rectangle {
        val w = width; val h = height
        if (h <= w * 0.8) return Rectangle(0, 0, w, h)
        val bh = (w * 9 / 16.0).toInt()
        return Rectangle(0, (h - bh) / 2, w, bh)
    }

    override fun paintComponent(g: Graphics) { g.color = col(T.BG_DEEP); g.fillRect(0, 0, width, height) }

    fun topDialog(): Dlg? = dialogs.lastOrNull()

    private var toastView: JComponent? = null
    private val toastTimer = Timer(2600) { toastView?.let { remove(it); repaint() }; toastView = null }.apply { isRepeats = false }

    fun toast(msg: String) {
        toastView?.let { remove(it) }
        val lbl = Label(msg, 14f, T.TEXT)
        val box = object : JPanel(null) {
            override fun paintComponent(g: Graphics) {
                val r = area()
                val fm = lbl.getFontMetrics(lbl.font)
                val tw = minOf(fm.stringWidth(msg) + dpi(4), (r.width * 0.7).toInt())
                lbl.setSize(tw, Short.MAX_VALUE.toInt())
                val th = lbl.preferredSize.height
                val bw = tw + dpi(36); val bh = th + dpi(22)
                val x = (r.width - bw) / 2; val y = r.height - bh - dpi(28)
                val g2 = g.hq()
                g2.color = Color(0, 0, 0, 200); g2.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), bw.toFloat(), bh.toFloat(), dp(28f), dp(28f)))
                g2.color = col(T.CYAN); g2.draw(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), bw.toFloat(), bh.toFloat(), dp(28f), dp(28f)))
                lbl.setBounds(x + dpi(18), y + dpi(11), tw, th)
                val gg = g2.create(x + dpi(18), y + dpi(11), tw, th); lbl.paint(gg); gg.dispose()
            }
        }
        box.isOpaque = false
        toastView = box
        add(box, POPUP_LAYER)
        revalidate(); repaint()
        toastTimer.restart()
    }
}

/** A dark panel "dialog" drawn over the app, with title, scrollable content and buttons. */
class Dlg(private val stage: Stage, title: String, content: JComponent, scroll: Boolean = true, private val widthFraction: Float = 0.92f,
          actions: List<Pair<String, (Dlg) -> Unit>> = emptyList()) {
    var onDismiss: (() -> Unit)? = null
    private val shade = object : JPanel() {
        override fun paintComponent(g: Graphics) { g.color = Color(0, 0, 0, 140); g.fillRect(0, 0, width, height) }
    }
    private val box = object : JPanel(java.awt.BorderLayout(0, dpi(8))) {
        override fun paintComponent(g: Graphics) = roundBox(g.hq(), 0f, 0f, width.toFloat(), height.toFloat(), dp(18f), T.BG, T.CYAN, dp(2f))
    }

    init {
        shade.isOpaque = false
        shade.addMouseListener(object : MouseAdapter() {})   // block clicks to the app below
        box.isOpaque = false
        box.border = BorderFactory.createEmptyBorder(dpi(14), dpi(16), dpi(14), dpi(16))
        box.add(Label(title, 19f, T.TEXT, true), java.awt.BorderLayout.NORTH)
        box.add(if (scroll) scrollOf(content) else content, java.awt.BorderLayout.CENTER)
        val bar = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, dpi(8), 0)).apply { isOpaque = false }
        for ((name, act) in actions) bar.add(RButton(name) { act(this) })
        bar.add(RButton("Cerrar") { dismiss() })
        box.add(bar, java.awt.BorderLayout.SOUTH)
        shade.layout = object : LayoutManager {
            override fun addLayoutComponent(name: String?, comp: Component?) {}
            override fun removeLayoutComponent(comp: Component?) {}
            override fun preferredLayoutSize(parent: Container) = parent.size
            override fun minimumLayoutSize(parent: Container) = Dimension(0, 0)
            override fun layoutContainer(parent: Container) {
                val w = (parent.width * widthFraction).toInt()
                box.setSize(w, Short.MAX_VALUE.toInt())
                val h = minOf(box.preferredSize.height + dpi(4), (parent.height * 0.92).toInt())
                box.setBounds((parent.width - w) / 2, (parent.height - h) / 2, w, h)
            }
        }
        shade.add(box)
        shade.border = BorderFactory.createEmptyBorder()
        stage.add(shade, JLayeredPane.MODAL_LAYER, 0)
        stage.dialogs += this
        stage.revalidate(); stage.repaint()
    }

    val isShowing get() = shade.parent != null

    fun dismiss() {
        if (shade.parent == null) return
        stage.remove(shade); stage.dialogs.remove(this)
        stage.revalidate(); stage.repaint()
        onDismiss?.invoke()
    }
}
