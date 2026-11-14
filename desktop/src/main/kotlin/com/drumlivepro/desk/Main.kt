package com.drumlivepro.desk

import android.graphics.Canvas
import com.drumlivepro.app.model.Icon
import com.drumlivepro.app.ui.IconPainter
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import javax.swing.UIManager

fun appVersion(): String = object {}.javaClass.getResourceAsStream("/drumlivepro-version.txt")?.use { it.readBytes().decodeToString().trim() } ?: "?"

/** App icon: the drum kit icon on a rounded navy tile. */
fun appIcon(size: Int): BufferedImage {
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    val c = Canvas(g)
    roundBox(g, 0f, 0f, size.toFloat(), size.toFloat(), size * 0.22f, 0xFF0B1B6E.toInt(), 0xFF33D6F7.toInt(), size / 32f)
    IconPainter.draw(c, Icon.DRUMKIT, size / 2f, size / 2f, size * 0.78f, 0xFF33D6F7.toInt(), 1.15f)
    g.dispose()
    return img
}

fun main(args: Array<String>) {
    System.setProperty("sun.java2d.uiScale.enabled", "true")
    // Packaging helper: writes PNG icons used to build the Windows .ico
    if (args.firstOrNull() == "--write-icons") {
        val dir = File(args.getOrElse(1) { "." }).apply { mkdirs() }
        for (s in listOf(16, 24, 32, 48, 64, 128, 256)) ImageIO.write(appIcon(s), "png", File(dir, "icon_$s.png"))
        return
    }
    SwingUtilities.invokeLater {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
        // The layout is designed like a landscape phone (~400dp tall); scale to the screen.
        Dp.s = (minOf(screen.height, screen.width * 9 / 16) / 640f).coerceIn(1f, 2.4f)
        ToolTipManager.sharedInstance().initialDelay = 500
        val frame = JFrame()
        frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        frame.iconImages = listOf(16, 32, 48, 64, 128, 256).map { appIcon(it) }
        frame.minimumSize = java.awt.Dimension(800, 450)
        val app = App(frame, appVersion())
        frame.title = app.appTitle
        app.start()
        // Optional automated screenshot (used by the build checks): --shot <file> [tab]
        val i = args.indexOf("--shot")
        if (i >= 0) {
            val out = File(args[i + 1])
            val tab = args.getOrNull(i + 2)
            app.postDelayed(1500) {
                tab?.let { t -> when (t) {
                    "fx" -> showFxDialog(app); "menu" -> showMainMenu(app); "pad" -> showPadEditor(app, 0)
                    else -> app.showTab(App.Tab.valueOf(t))
                } }
                app.postDelayed(1500) {
                    val img = BufferedImage(frame.contentPane.width, frame.contentPane.height, BufferedImage.TYPE_INT_RGB)
                    val g = img.createGraphics(); frame.contentPane.paintAll(g); g.dispose()
                    ImageIO.write(img, "png", out)
                    app.quit()
                }
            }
        }
    }
}
