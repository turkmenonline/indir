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
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
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
import com.example.gdl.R

/** Kod ile çizilen ekranlar için güvenli dinamik renkler ve bileşenler. */
object Ui {
    fun animatePageIn(contentView: View, fromRight: Boolean) {
        val width = contentView.resources.displayMetrics.widthPixels.toFloat()
        contentView.translationX = if (fromRight) width * 0.35f else -width * 0.35f
        contentView.alpha = 0.6f
        contentView.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .start()
    }

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

    fun bg(c: Context?): Int = if (isDark(c)) safeColor("#000000", Color.BLACK) else safeColor("#F2F2F7", Color.WHITE)
    fun cardBg(c: Context?): Int = if (isDark(c)) safeColor("#1C1C1E", Color.DKGRAY) else Color.WHITE
    fun line(c: Context?): Int = if (isDark(c)) safeColor("#2C2C2E", Color.GRAY) else safeColor("#E5E5EA", Color.LTGRAY)
    fun fieldLine(c: Context?): Int = if (isDark(c)) safeColor("#383848", Color.GRAY) else safeColor("#D3D3E0", Color.LTGRAY)
    fun textClr(c: Context?): Int = if (isDark(c)) Color.WHITE else safeColor("#1B1B1F", Color.BLACK)
    @Suppress("UNUSED_PARAMETER")
    fun mutedClr(c: Context?): Int = if (isDark(c)) safeColor("#98989D", Color.GRAY) else safeColor("#8E8E93", Color.GRAY)
    fun hintClr(c: Context?): Int = if (isDark(c)) safeColor("#6E6E82", Color.GRAY) else safeColor("#9A9AAA", Color.GRAY)
    fun primary(c: Context?): Int = if (isDark(c)) safeColor("#0A84FF", Color.BLUE) else safeColor("#007AFF", Color.BLUE)
    fun primaryBg(c: Context?): Int = if (isDark(c)) safeColor("#1C2D4A", Color.DKGRAY) else safeColor("#EBF3FF", Color.LTGRAY)

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
        if (c == null) return if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val fontRes = if (bold) R.font.sf_pro_bold else R.font.sf_pro_regular
        return try {
            val tf = ResourcesCompat.getFont(c, fontRes)
            tf ?: (if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)
        } catch (_: Throwable) {
            if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    fun text(c: Context, s: String, sp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(c).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            typeface = font(c, bold)
            if (bold && sp >= 18f) {
                letterSpacing = -0.02f
            }
            if (s.any { it.isDigit() }) {
                fontFeatureSettings = "tnum"
            }
        }

    fun childText(c: Context, s: String, sp: Float = 15f, color: Int? = null, bold: Boolean = false): TextView =
        text(c, s, sp, color ?: textClr(c), bold)

    fun button(c: Context, label: String, kind: Kind, onClick: () -> Unit): Button {
        val (bgClr, fgClr, lineClr) = when (kind) {
            Kind.FILLED -> Triple(primary(c), Color.WHITE, Color.TRANSPARENT)
            Kind.TONAL -> Triple(if (isDark(c)) safeColor("#3A3A3C", Color.DKGRAY) else safeColor("#E5E5EA", Color.LTGRAY), textClr(c), Color.TRANSPARENT)
            Kind.OUTLINE -> Triple(cardBg(c), textClr(c), fieldLine(c))
            Kind.DANGER -> Triple(cardBg(c), ERR, ERR_LINE)
        }
        val r = dp(c, 15).toFloat()
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
            background = ripple(shape(bgClr, r, lineClr, if (lineClr != Color.TRANSPARENT) dp(c, 1) else 0), r)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }
    }

    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(c, 16)
        setPadding(p, p, p, p)
        val borderClr = if (isDark(c)) safeColor("#2C2C2E", Color.GRAY) else safeColor("#1A000000", Color.LTGRAY)
        background = shape(cardBg(c), dp(c, 20).toFloat(), borderClr, dp(c, 1))
    }

    fun field(c: Context, hintText: String): EditText = EditText(c).apply {
        hint = hintText
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(textClr(c))
        setHintTextColor(hintClr(c))
        typeface = font(c, bold = false)
        val r = dp(c, 12).toFloat()
        val fieldBg = if (isDark(c)) safeColor("#2C2C2E", Color.DKGRAY) else safeColor("#F2F2F7", Color.LTGRAY)
        background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                shape(fieldBg, r, primary(c), dp(c, 2))
            )
            addState(intArrayOf(), shape(fieldBg, r, Color.TRANSPARENT, 0))
        }
        setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
    }

    fun segmentedControl(
        c: Context,
        items: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit
    ): View {
        val isDarkTheme = isDark(c)
        val capsuleBg = if (isDarkTheme) safeColor("#141416", Color.DKGRAY) else safeColor("#E5E5EA", Color.LTGRAY)
        val capsuleRadius = dp(c, 16).toFloat()
        
        val container = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = dp(c, 3)
            setPadding(pad, pad, pad, pad)
            background = shape(capsuleBg, capsuleRadius)
        }

        items.forEachIndexed { index, title ->
            val isSelected = (index == selectedIndex)
            val btnRadius = dp(c, 13).toFloat()
            
            val itemBg = if (isSelected) {
                if (isDarkTheme) safeColor("#3A3A3C", Color.DKGRAY) else Color.WHITE
            } else {
                Color.TRANSPARENT
            }
            
            val textColor = if (isSelected) {
                if (isDarkTheme) Color.WHITE else Color.parseColor("#1C1C1E")
            } else {
                Color.parseColor("#8E8E93")
            }

            val tv = TextView(c).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                setTextColor(textColor)
                typeface = font(c, bold = isSelected)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = null
                
                val pV = dp(c, 8)
                val pH = dp(c, 10)
                setPadding(pH, pV, pH, pV)
                
                background = ripple(shape(itemBg, btnRadius), btnRadius)
                if (isSelected) {
                    elevation = dp(c, 2).toFloat()
                }
                
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (selectedIndex != index) {
                        onSelect(index)
                    }
                }
            }
            container.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) {
                    setMargins(dp(c, 2), 0, 0, 0)
                }
            })
        }
        return container
    }

    fun pill(c: Context, s: String, fg: Int, bg: Int): TextView =
        text(c, s, 12f, fg, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4))
            background = shape(bg, dp(c, 50).toFloat())
            fontFeatureSettings = "tnum"
        }

    fun iconButton(c: Context, res: Int, desc: String, onClick: () -> Unit): ImageButton =
        ImageButton(c).apply {
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(textClr(c))
            contentDescription = desc
            scaleType = ImageView.ScaleType.CENTER
            background = ripple(shape(Color.TRANSPARENT, dp(c, 24).toFloat()), dp(c, 24).toFloat())
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }

    fun header(c: Context, title: String, leading: View?, trailing: View?): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(c, 56)
            val size = dp(c, 40)

            if (leading != null) {
                addView(leading, lp(size, size))
            } else if (trailing != null) {
                val dummyLeft = View(c).apply { visibility = View.INVISIBLE }
                addView(dummyLeft, lp(size, size))
            }

            val titleView = text(c, title, 24f, textClr(c), true).apply {
                gravity = Gravity.CENTER
                letterSpacing = -0.02f
            }
            addView(titleView, lp(0, WRAP, 1f))

            if (trailing != null) {
                addView(trailing, lp(size, size))
            } else if (leading != null) {
                val dummyRight = View(c).apply { visibility = View.INVISIBLE }
                addView(dummyRight, lp(size, size))
            }
        }

    fun iosGroupedRow(c: Context, title: String, subtitle: String, showDivider: Boolean, onClick: () -> Unit): View {
        val container = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(c, 16)
            setPadding(p, dp(c, 14), p, dp(c, 14))
            background = ripple(shape(Color.TRANSPARENT, 0f), 0f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }

        val rowLayout = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val textCol = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(c, title, 16f, textClr(c), true))
            addView(text(c, subtitle, 13f, mutedClr(c), false), lp(WRAP, WRAP, t = dp(c, 2)))
        }

        val chevron = text(c, "›", 20f, mutedClr(c), true).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

        rowLayout.addView(textCol, lp(0, WRAP, 1f))
        rowLayout.addView(chevron, lp(WRAP, WRAP, l = dp(c, 12)))
        container.addView(rowLayout)

        if (showDivider) {
            val divider = View(c).apply {
                setBackgroundColor(line(c))
                layoutParams = LinearLayout.LayoutParams(MATCH, dp(c, 1)).apply {
                    setMargins(dp(c, 16), dp(c, 14), 0, 0)
                }
            }
            container.addView(divider)
        }

        return container
    }

    fun iosGroupedCard(c: Context, views: List<View>): LinearLayout {
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val radius = dp(c, 16).toFloat()
            val borderClr = if (isDark(c)) safeColor("#2C2C2E", Color.GRAY) else safeColor("#1A000000", Color.LTGRAY)
            background = shape(cardBg(c), radius, borderClr, dp(c, 1))
            clipToOutline = true
            views.forEach { addView(it, lp(MATCH, WRAP)) }
        }
    }

    data class NavItem(val title: String, val iconRes: Int)

    fun floatingNavBar(
        c: Context,
        items: List<NavItem>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit
    ): LinearLayout {
        val isDarkTheme = isDark(c)
        val glassBgColor = if (isDarkTheme) Color.parseColor("#CC1C1C22") else Color.parseColor("#F2FFFFFF")
        val borderWhite = if (isDarkTheme) Color.parseColor("#50FFFFFF") else Color.parseColor("#25000000")
        val strokeWidth = (1.5f * c.resources.displayMetrics.density).toInt()
        val pillRadius = 999f

        return LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val pV = dp(c, 6)
            val pH = dp(c, 8)
            setPadding(pH, pV, pH, pV)
            background = shape(glassBgColor, pillRadius, borderWhite, strokeWidth)
            elevation = dp(c, 16).toFloat()
            outlineProvider = ViewOutlineProvider.BACKGROUND

            items.forEachIndexed { index, item ->
                val isSelected = (index == selectedIndex)
                val activeColor = if (isDarkTheme) Color.parseColor("#0A84FF") else Color.parseColor("#007AFF")
                val inactiveColor = if (isDarkTheme) Color.parseColor("#8E8E93") else Color.parseColor("#636366")
                val fgColor = if (isSelected) activeColor else inactiveColor

                val sz = dp(c, 26)
                val icon = ImageView(c).apply {
                    setImageResource(item.iconRes)
                    imageTintList = ColorStateList.valueOf(fgColor)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                        gravity = Gravity.CENTER
                    }
                }

                val tabBg = if (isSelected) {
                    if (isDarkTheme) {
                        shape(Color.parseColor("#38FFFFFF"), 999f, Color.parseColor("#60FFFFFF"), dp(c, 1))
                    } else {
                        shape(Color.parseColor("#20007AFF"), 999f, Color.parseColor("#40007AFF"), dp(c, 1))
                    }
                } else {
                    shape(Color.TRANSPARENT, 999f)
                }

                val cellWidth = dp(c, 78)
                val cellHeight = dp(c, 48)

                val tabCell = LinearLayout(c).apply {
                    gravity = Gravity.CENTER
                    background = ripple(tabBg, 999f)

                    addView(icon)

                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelect(index)
                    }
                }

                addView(tabCell, LinearLayout.LayoutParams(cellWidth, cellHeight).apply {
                    gravity = Gravity.CENTER
                    setMargins(dp(c, 4), 0, dp(c, 4), 0)
                })
            }
        }
    }

    fun iosLargeTitle(c: Context, title: String): TextView {
        return TextView(c).apply {
            this.text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(textClr(c))
            typeface = font(c, bold = true)
            letterSpacing = -0.02f
            gravity = Gravity.START
        }
    }
}
