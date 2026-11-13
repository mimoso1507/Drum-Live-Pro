package com.drummatch.deluxe.ui

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

/** Colors taken from the user's reference screenshot (royal navy + cyan + mint). */
object T {
    const val BG = 0xFF00007A.toInt()
    const val BG_DEEP = 0xFF000052.toInt()
    const val PANEL = 0xFF00006A.toInt()
    const val BTN = 0xFF05061C.toInt()
    const val BTN_SEL = 0xFF0B2A9A.toInt()
    const val BORDER = 0xFFA4A6C2.toInt()
    const val CYAN = 0xFF33D6F7.toInt()
    const val MINT = 0xFF5BE3A2.toInt()
    const val TEXT = Color.WHITE
    const val TEXT2 = 0xFFBFC2E6.toInt()
    const val RED = 0xFFFF4D5E.toInt()
    const val PAD = 0xFF080A2E.toInt()
    const val PAD_HIT = 0xFF1B4FD8.toInt()
    const val GOLD = 0xFFE8B84A.toInt()
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
        trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(0x8833D6F7.toInt(), 0x66A4A6C2))
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
class GlyphButton(ctx: Context, var glyph: Glyph? = null, var icon: com.drummatch.deluxe.model.Icon? = null, var text: String? = null) : View(ctx) {
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
