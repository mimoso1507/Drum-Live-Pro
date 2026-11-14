package com.drumlivepro.app.ui

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** One color theme for the whole interface. */
class Palette(
    val name: String, val bg: Int, val bgDeep: Int, val btn: Int, val btnSel: Int, val border: Int,
    val accent: Int, val mint: Int, val text2: Int, val pad: Int, val padTop: Int, val padHit: Int,
    val navSel: Int, val kitBg: Int, val dotOff: Int,
)

private fun c(v: Long) = v.toInt()

/** 8 themes: 7 soft ones and, last, the original bright royal blue. */
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

/** Current theme colors (set with [T.apply]). */
object T {
    var BG = 0; var BG_DEEP = 0; var PANEL = 0; var BTN = 0; var BTN_SEL = 0; var BORDER = 0
    var CYAN = 0; var MINT = 0; var TEXT2 = 0; var PAD = 0; var PAD_TOP = 0; var PAD_HIT = 0
    var NAV_SEL = 0; var KIT_BG = 0; var DOT_OFF = 0
    const val TEXT = Color.WHITE
    const val RED = 0xFFFF4D5E.toInt()
    const val GOLD = 0xFFE8B84A.toInt()
    var index = 0; private set
    init { apply(0) }
    fun apply(i: Int) {
        index = i.coerceIn(0, THEMES.size - 1)
        val p = THEMES[index]
        BG = p.bg; BG_DEEP = p.bgDeep; PANEL = p.bg; BTN = p.btn; BTN_SEL = p.btnSel; BORDER = p.border
        CYAN = p.accent; MINT = p.mint; TEXT2 = p.text2; PAD = p.pad; PAD_TOP = p.padTop; PAD_HIT = p.padHit
        NAV_SEL = p.navSel; KIT_BG = p.kitBg; DOT_OFF = p.dotOff
    }
}

fun Context.dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
fun Context.dpi(v: Int): Int = dp(v.toFloat()).toInt()
fun View.dp(v: Float) = context.dp(v)
fun View.dpi(v: Int) = context.dpi(v)

fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

fun Context.rounded(fill: Int, stroke: Int = T.BORDER, radiusDp: Float = 14f, strokeDp: Float = 1.6f) = GradientDrawable().apply {
    setColor(fill); cornerRadius = dp(radiusDp); if (strokeDp > 0) setStroke(dp(strokeDp).toInt(), stroke)
}

fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.label(text: String, sizeSp: Float = 15f, color: Int = T.TEXT, bold: Boolean = false) = TextView(this).apply {
    this.text = text; textSize = sizeSp; setTextColor(color)
    typeface = Typeface.create(Typeface.SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
}

/** Rounded text button in the app style. */
fun Context.button(text: String, selected: Boolean = false, sizeSp: Float = 15f, onClick: (View) -> Unit) = TextView(this).apply {
    this.text = text; textSize = sizeSp; setTextColor(T.TEXT); gravity = Gravity.CENTER
    typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    setPadding(dpi(14), dpi(9), dpi(14), dpi(9))
    styleSelected(selected)
    isClickable = true
    setOnClickListener(onClick)
}

fun TextView.styleSelected(sel: Boolean) {
    background = context.rounded(if (sel) T.BTN_SEL else T.BTN, if (sel) T.CYAN else T.BORDER, 14f, if (sel) 2.4f else 1.6f)
}

fun Context.vbox(padDp: Int = 0) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(padDp), dpi(padDp), dpi(padDp), dpi(padDp)) }
fun Context.hbox(padDp: Int = 0) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(padDp), dpi(padDp), dpi(padDp), dpi(padDp)) }

fun LinearLayout.gap(dp: Int) = addView(View(context), lp(context.dpi(dp), context.dpi(dp)))

fun Context.seek(max: Int, value: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
    this.max = max; progress = value
    progressTintList = ColorStateList.valueOf(T.CYAN); thumbTintList = ColorStateList.valueOf(T.CYAN)
    progressBackgroundTintList = ColorStateList.valueOf(T.BORDER)
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
        override fun onStartTrackingTouch(s: SeekBar) {}
        override fun onStopTrackingTouch(s: SeekBar) {}
    })
}

/** Labelled slider row: "Name ........ value". */
fun Context.sliderRow(name: String, max: Int, value: Int, fmt: (Int) -> String, onChange: (Int) -> Unit): LinearLayout {
    val box = vbox()
    val head = hbox()
    val t = label(name, 14f, T.TEXT2); val v = label(fmt(value), 14f, T.CYAN)
    head.addView(t, lp(0, WRAP, 1f)); head.addView(v)
    box.addView(head)
    box.addView(seek(max, value) { v.text = fmt(it); onChange(it) }, lp(MATCH, WRAP))
    return box
}

@Suppress("DEPRECATION")
fun Context.switchRow(name: String, on: Boolean, onChange: (Boolean) -> Unit) = hbox().apply {
    addView(label(name, 15f), lp(0, WRAP, 1f))
    addView(Switch(context).apply {
        isChecked = on
        thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(T.CYAN, T.BORDER))
        trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf((T.CYAN and 0xFFFFFF) or 0x88000000.toInt(), (T.BORDER and 0xFFFFFF) or 0x66000000))
        setOnCheckedChangeListener { _, b -> onChange(b) }
    })
}

fun Context.input(hint: String, value: String = "", number: Boolean = false) = EditText(this).apply {
    setText(value); this.hint = hint; setTextColor(T.TEXT); setHintTextColor(T.TEXT2)
    background = rounded(T.BTN, T.BORDER, 10f); setPadding(dpi(12), dpi(10), dpi(12), dpi(10))
    if (number) inputType = InputType.TYPE_CLASS_NUMBER
    setSingleLine()
}

/** A dark panel dialog with title and scrollable content. Returns the dialog (already shown). */
fun Context.panelDialog(title: String, content: View, scroll: Boolean = true, widthFraction: Float = 0.92f, actions: List<Pair<String, (Dialog) -> Unit>> = emptyList()): Dialog {
    val d = Dialog(this)
    d.requestWindowFeature(Window.FEATURE_NO_TITLE)
    val root = vbox(14)
    root.background = rounded(T.BG, T.CYAN, 18f, 2f)
    root.addView(label(title, 19f, T.TEXT, true), lp(MATCH, WRAP))
    root.gap(8)
    if (scroll) root.addView(ScrollView(this).apply { addView(content); isFillViewport = true }, lp(MATCH, 0, 1f))
    else root.addView(content, lp(MATCH, 0, 1f))
    val bar = hbox(); bar.gravity = Gravity.END
    for ((name, act) in actions) { bar.gap(8); bar.addView(button(name) { act(d) }) }
    bar.gap(8); bar.addView(button("Cerrar") { d.dismiss() })
    root.gap(8)
    root.addView(bar, lp(MATCH, WRAP))
    d.setContentView(root)
    d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    val dm = resources.displayMetrics
    d.window?.setLayout((dm.widthPixels * widthFraction).toInt(), (dm.heightPixels * 0.9f).toInt())
    d.show()
    return d
}

/** Square-ish button that draws an icon/glyph instead of text. */
class GlyphButton(ctx: Context, var glyph: Glyph? = null, var icon: com.drumlivepro.app.model.Icon? = null, var text: String? = null) : View(ctx) {
    var selected2 = false; set(v) { field = v; invalidate() }
    var tint = T.TEXT; set(v) { field = v; invalidate() }
    var badge: Int? = null; set(v) { field = v; invalidate() }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
    init { isClickable = true }
    override fun onDraw(c: Canvas) {
        val r = dp(12f); val sw = dp(if (selected2) 2.4f else 1.6f)
        bg.style = Paint.Style.FILL; bg.color = if (selected2) T.BTN_SEL else T.BTN
        c.drawRoundRect(sw, sw, width - sw, height - sw, r, r, bg)
        bg.style = Paint.Style.STROKE; bg.strokeWidth = sw; bg.color = if (selected2) T.CYAN else T.BORDER
        c.drawRoundRect(sw, sw, width - sw, height - sw, r, r, bg)
        val s = minOf(width, height) * 0.58f
        when {
            text != null -> { tp.color = tint; tp.textSize = height * 0.36f; c.drawText(text!!, width / 2f, height / 2f + tp.textSize * 0.36f, tp) }
            glyph != null -> IconPainter.glyph(c, glyph!!, width / 2f, height / 2f, s, tint)
            icon != null -> IconPainter.draw(c, icon!!, width / 2f, height / 2f, s, tint)
        }
        badge?.let { bg.style = Paint.Style.FILL; bg.color = it; c.drawCircle(width - dp(9f), dp(9f), dp(4.5f), bg) }
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) { MotionEvent.ACTION_DOWN -> alpha = 0.7f; MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> alpha = 1f }
        return super.onTouchEvent(e)
    }
}

fun FrameLayout.fill(v: View) = addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
