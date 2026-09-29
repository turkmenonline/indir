package com.example.gdl

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Kod ile çizilen ekranlar için güvenli dinamik renkler ve bileşenler. */
object Ui {
    fun isDark(c: Context?): Boolean {
        if (c == null) return false
        return try {
            val nightMode = c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightMode == Configuration.UI_MODE_NIGHT_YES
        } catch (_: Throwable) {
            false
        }
    }

    fun setupEdgeToEdge(activity: AppCompatActivity, root: View) {
        try {
            val window = activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT

            val isDarkTheme = isDark(activity)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !isDarkTheme
            controller.isAppearanceLightNavigationBars = !isDarkTheme

            ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
                val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                root.setPadding(root.paddingLeft, statusTop, root.paddingRight, navBottom)
                insets
            }
        } catch (_: Throwable) {}
    }

    private fun safeColor(colorHex: String, fallback: Int): Int {
        return try {
            Color.parseColor(colorHex)
        } catch (_: Throwable) {
            fallback
        }
    }

    fun bg(c: Context?): Int = if (isDark(c)) safeColor("#0F0F12", Color.BLACK) else safeColor("#F5F5FA", Color.WHITE)
    fun cardBg(c: Context?): Int = if (isDark(c)) safeColor("#1C1C24", Color.DKGRAY) else Color.WHITE
    fun line(c: Context?): Int = if (isDark(c)) safeColor("#282834", Color.GRAY) else safeColor("#E3E3EC", Color.LTGRAY)
    fun fieldLine(c: Context?): Int = if (isDark(c)) safeColor("#383848", Color.GRAY) else safeColor("#D3D3E0", Color.LTGRAY)
    fun textClr(c: Context?): Int = if (isDark(c)) Color.WHITE else safeColor("#1B1B1F", Color.BLACK)
    fun mutedClr(c: Context?): Int = if (isDark(c)) safeColor("#9E9EB2", Color.GRAY) else safeColor("#6B6B7B", Color.GRAY)
    fun hintClr(c: Context?): Int = if (isDark(c)) safeColor("#6E6E82", Color.GRAY) else safeColor("#9A9AAA", Color.GRAY)
    fun primary(c: Context?): Int = if (isDark(c)) safeColor("#389BFF", Color.BLUE) else safeColor("#007AFF", Color.BLUE)
    fun primaryBg(c: Context?): Int = if (isDark(c)) safeColor("#16263D", Color.DKGRAY) else safeColor("#EBF3FF", Color.LTGRAY)

    val OK = safeColor("#15803D", Color.GREEN)
    val OK_BG = safeColor("#DFF5E6", Color.LTGRAY)
    val ERR = safeColor("#B3261E", Color.RED)
    val ERR_BG = safeColor("#FCE7E5", Color.LTGRAY)
    private val ERR_LINE = safeColor("#F0C6C2", Color.RED)
    private const val RIPPLE = 0x22007AFF

    enum class Kind { FILLED, TONAL, OUTLINE, DANGER }

    const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    fun dp(c: Context?, v: Int): Int {
        if (c == null) return v * 2
        return try {
            (v * c.resources.displayMetrics.density).toInt()
        } catch (_: Throwable) {
            v * 2
        }
    }

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

    fun font(c: Context?, bold: Boolean = false): Typeface {
        val style = if (bold) Typeface.BOLD else Typeface.NORMAL
        if (c == null) return Typeface.create(Typeface.DEFAULT, style)
        return try {
            val tf = ResourcesCompat.getFont(c, R.font.space_grotesk)
            if (tf != null) {
                Typeface.create(tf, style)
            } else {
                Typeface.create(Typeface.DEFAULT, style)
            }
        } catch (_: Throwable) {
            try {
                Typeface.create(Typeface.DEFAULT, style)
            } catch (_: Throwable) {
                Typeface.DEFAULT
            }
        }
    }

    fun text(c: Context, s: String, sp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(c).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            typeface = font(c, bold)
        }

    fun childText(c: Context, s: String, sp: Float = 14f, color: Int? = null, bold: Boolean = false): TextView =
        text(c, s, sp, color ?: textClr(c), bold)

    fun button(c: Context, label: String, kind: Kind, onClick: () -> Unit): Button {
        val (bgClr, fgClr, lineClr) = when (kind) {
            Kind.FILLED -> Triple(primary(c), Color.WHITE, primary(c))
            Kind.TONAL -> Triple(primaryBg(c), primary(c), primaryBg(c))
            Kind.OUTLINE -> Triple(cardBg(c), textClr(c), fieldLine(c))
            Kind.DANGER -> Triple(cardBg(c), ERR, ERR_LINE)
        }
        val r = dp(c, 14).toFloat()
        return Button(c).apply {
            text = label
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(fgClr)
            typeface = font(c, bold = true)
            stateListAnimator = null
            minHeight = dp(c, 48)
            minimumHeight = dp(c, 48)
            setPadding(dp(c, 16), 0, dp(c, 16), 0)
            background = ripple(shape(bgClr, r, lineClr, dp(c, 1)), r)
            setOnClickListener { onClick() }
        }
    }

    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(c, 16)
        setPadding(p, p, p, p)
        background = shape(cardBg(c), dp(c, 18).toFloat(), line(c), dp(c, 1))
    }

    fun field(c: Context, hintText: String): EditText = EditText(c).apply {
        hint = hintText
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(textClr(c))
        setHintTextColor(hintClr(c))
        typeface = font(c, bold = false)
        val r = dp(c, 12).toFloat()
        background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                shape(bg(c), r, primary(c), dp(c, 2))
            )
            addState(intArrayOf(), shape(bg(c), r, fieldLine(c), dp(c, 1)))
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
            imageTintList = ColorStateList.valueOf(textClr(c))
            contentDescription = desc
            scaleType = ImageView.ScaleType.CENTER
            background = ripple(shape(Color.TRANSPARENT, dp(c, 24).toFloat()), dp(c, 24).toFloat())
            setOnClickListener { onClick() }
        }

    fun header(c: Context, title: String, leading: View?, trailing: View?): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(c, 64)
            val size = dp(c, 48)

            if (leading != null) {
                addView(leading, lp(size, size))
            } else if (trailing != null) {
                val dummyLeft = View(c).apply { visibility = View.INVISIBLE }
                addView(dummyLeft, lp(size, size))
            }

            val titleView = text(c, title, 20f, textClr(c), true).apply {
                gravity = Gravity.CENTER
            }
            addView(titleView, lp(0, WRAP, 1f))

            if (trailing != null) {
                addView(trailing, lp(size, size))
            } else if (leading != null) {
                val dummyRight = View(c).apply { visibility = View.INVISIBLE }
                addView(dummyRight, lp(size, size))
            }
        }

    data class NavItem(val title: String, val iconRes: Int)

    fun floatingNavBar(
        c: Context,
        items: List<NavItem>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit
    ): LinearLayout {
        val glassBgColor = if (isDark(c)) safeColor("#CC1A1A24", Color.BLACK) else safeColor("#E6FFFFFF", Color.WHITE)
        val borderWhite = if (isDark(c)) safeColor("#383848", Color.DKGRAY) else safeColor("#A0FFFFFF", Color.LTGRAY)
        val pillRadius = 999f

        return LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val pV = dp(c, 4)
            val pH = dp(c, 6)
            setPadding(pH, pV, pH, pV)
            background = shape(glassBgColor, pillRadius, borderWhite, dp(c, 1))
            elevation = dp(c, 10).toFloat()

            items.forEachIndexed { index, item ->
                val isSelected = (index == selectedIndex)
                val activeColor = primary(c)
                val inactiveColor = mutedClr(c)
                val fgColor = if (isSelected) activeColor else inactiveColor

                val icon = ImageView(c).apply {
                    setImageResource(item.iconRes)
                    imageTintList = ColorStateList.valueOf(fgColor)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val sz = dp(c, 20)
                    layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
                }

                val label = text(c, item.title, 10.5f, fgColor, isSelected).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = null
                }

                val tabCell = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    minimumWidth = dp(c, 68)
                    val pTabV = dp(c, 6)
                    val pTabH = dp(c, 14)
                    setPadding(pTabH, pTabV, pTabH, pTabV)

                    background = if (isSelected) {
                        ripple(shape(primaryBg(c), pillRadius), pillRadius)
                    } else {
                        ripple(shape(Color.TRANSPARENT, pillRadius), pillRadius)
                    }

                    addView(icon)
                    addView(label, lp(WRAP, WRAP, t = dp(c, 2)))

                    setOnClickListener { onSelect(index) }
                }

                addView(tabCell, lp(0, WRAP, 1f, l = dp(c, 2), r = dp(c, 2)))
            }
        }
    }
}
