package com.example.gdl

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

/** Kod ile çizilen ekranlar için ortak renk ve bileşenler. */
object Ui {
    val BG = Color.parseColor("#F5F5FA")
    val CARD = Color.WHITE
    val LINE = Color.parseColor("#E3E3EC")
    val FIELD_LINE = Color.parseColor("#D3D3E0")
    val TEXT = Color.parseColor("#1B1B1F")
    val MUTED = Color.parseColor("#6B6B7B")
    val HINT = Color.parseColor("#9A9AAA")
    val PRIMARY = Color.parseColor("#4338CA")
    val PRIMARY_BG = Color.parseColor("#E8E7FB")
    val OK = Color.parseColor("#15803D")
    val OK_BG = Color.parseColor("#DFF5E6")
    val ERR = Color.parseColor("#B3261E")
    val ERR_BG = Color.parseColor("#FCE7E5")
    private val ERR_LINE = Color.parseColor("#F0C6C2")
    private const val RIPPLE = 0x224338CA

    enum class Kind { FILLED, TONAL, OUTLINE, DANGER }

    const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun lp(
        w: Int, h: Int, weight: Float = 0f,
        l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(l, t, r, b) }

    fun shape(color: Int, radius: Float, stroke: Int = 0, strokeW: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
            if (strokeW > 0) setStroke(strokeW, stroke)
        }

    fun ripple(base: Drawable, radius: Float): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(RIPPLE), base, shape(Color.WHITE, radius))

    fun font(c: Context, bold: Boolean = false): Typeface {
        val style = if (bold) Typeface.BOLD else Typeface.NORMAL
        return try {
            val tf = ResourcesCompat.getFont(c, R.font.space_grotesk)
            if (tf != null) Typeface.create(tf, style) else Typeface.create("sans-serif-medium", style)
        } catch (_: Exception) {
            Typeface.create("sans-serif-medium", style)
        }
    }

    fun text(c: Context, s: String, sp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(c).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            typeface = font(c, bold)
        }

    fun childText(c: Context, s: String, sp: Float = 14f, color: Int = TEXT, bold: Boolean = false): TextView =
        text(c, s, sp, color, bold)

    fun button(c: Context, label: String, kind: Kind, onClick: () -> Unit): Button {
        val (bg, fg, line) = when (kind) {
            Kind.FILLED -> Triple(PRIMARY, Color.WHITE, PRIMARY)
            Kind.TONAL -> Triple(PRIMARY_BG, PRIMARY, PRIMARY_BG)
            Kind.OUTLINE -> Triple(CARD, TEXT, FIELD_LINE)
            Kind.DANGER -> Triple(CARD, ERR, ERR_LINE)
        }
        val r = dp(c, 14).toFloat()
        return Button(c).apply {
            text = label
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(fg)
            typeface = font(c, bold = true)
            stateListAnimator = null
            minHeight = dp(c, 48)
            minimumHeight = dp(c, 48)
            setPadding(dp(c, 16), 0, dp(c, 16), 0)
            background = ripple(shape(bg, r, line, dp(c, 1)), r)
            setOnClickListener { onClick() }
        }
    }

    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(c, 16)
        setPadding(p, p, p, p)
        background = shape(CARD, dp(c, 18).toFloat(), LINE, dp(c, 1))
    }

    fun field(c: Context, hintText: String): EditText = EditText(c).apply {
        hint = hintText
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(TEXT)
        setHintTextColor(HINT)
        typeface = font(c, bold = false)
        val r = dp(c, 12).toFloat()
        background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                shape(BG, r, PRIMARY, dp(c, 2))
            )
            addState(intArrayOf(), shape(BG, r, FIELD_LINE, dp(c, 1)))
        }
        setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
    }

    fun pill(c: Context, s: String, fg: Int, bg: Int): TextView =
        text(c, s, 12f, fg, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4))
            background = shape(bg, dp(c, 50).toFloat())
        }

    fun iconButton(c: Context, res: Int, desc: String, onClick: () -> Unit): ImageButton =
        ImageButton(c).apply {
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(TEXT)
            contentDescription = desc
            scaleType = android.widget.ImageView.ScaleType.CENTER
            background = ripple(shape(Color.TRANSPARENT, dp(c, 24).toFloat()), dp(c, 24).toFloat())
            setOnClickListener { onClick() }
        }

    fun header(c: Context, title: String, leading: View?, trailing: View?): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(c, 64)
            val size = dp(c, 48)
            if (leading != null) addView(leading, lp(size, size, r = dp(c, 4)))
            addView(
                text(c, title, 24f, TEXT, true),
                lp(0, WRAP, 1f, l = if (leading == null) dp(c, 4) else 0)
            )
            if (trailing != null) addView(trailing, lp(size, size))
        }

    data class NavItem(val title: String, val iconRes: Int)

    fun floatingNavBar(
        c: Context,
        items: List<NavItem>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit
    ): LinearLayout {
        val glassBgColor = Color.parseColor("#E6FFFFFF")
        val borderWhite = Color.parseColor("#A0FFFFFF")
        val radius = dp(c, 32).toFloat()

        return LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pV = dp(c, 6)
            val pH = dp(c, 8)
            setPadding(pH, pV, pH, pV)
            background = shape(glassBgColor, radius, borderWhite, dp(c, 2))
            elevation = dp(c, 12).toFloat()

            items.forEachIndexed { index, item ->
                val isSelected = (index == selectedIndex)
                val activeColor = PRIMARY
                val inactiveColor = MUTED
                val fgColor = if (isSelected) activeColor else inactiveColor

                val icon = ImageButton(c).apply {
                    setImageResource(item.iconRes)
                    imageTintList = ColorStateList.valueOf(fgColor)
                    background = null
                    isClickable = false
                    isFocusable = false
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val sz = dp(c, 20)
                    layoutParams = LinearLayout.LayoutParams(sz, sz)
                }

                val label = text(c, item.title, 13f, fgColor, isSelected).apply {
                    gravity = Gravity.CENTER
                }

                val rTab = dp(c, 22).toFloat()
                val tabCell = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    val pTabV = dp(c, 10)
                    val pTabH = dp(c, 14)
                    setPadding(pTabH, pTabV, pTabH, pTabV)

                    background = if (isSelected) {
                        ripple(shape(PRIMARY_BG, rTab), rTab)
                    } else {
                        ripple(shape(Color.TRANSPARENT, rTab), rTab)
                    }

                    addView(icon)
                    addView(label, lp(WRAP, WRAP, l = dp(c, 6)))

                    setOnClickListener { onSelect(index) }
                }

                addView(tabCell, lp(0, WRAP, 1f, l = dp(c, 3), r = dp(c, 3)))
            }
        }
    }
}
