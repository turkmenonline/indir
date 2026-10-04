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
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.app.Activity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.gdl.R
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderScriptBlur

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

    fun dp(c: Context?, v: Float): Int {
        if (c == null) return (v * 2).toInt()
        return try {
            (v * c.resources.displayMetrics.density).toInt()
        } catch (_: Throwable) {
            (v * 2).toInt()
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

    /**
     * iOS'un seçenek sayfalarında (Fotoğraflar dışa aktarma, Kısayollar vb.)
     * görülen, yan yana kaydırılan kapsül seçenekler. [segmentedControl]'den
     * farkı: sabit tek-satır genişlik paylaşımı yerine her seçenek kendi
     * doğal genişliğinde durur ve satır sığmazsa yana kaydırılır — bu yüzden
     * 5-7 gibi çok sayıda seçenek olduğunda (örn. video kalitesi) daha
     * doğru görünür.
     */
    fun qualityChips(
        c: Context,
        items: List<Pair<String, String>>, // (etiket, değer)
        selectedValue: String,
        onSelect: (String) -> Unit
    ): View {
        val isDarkTheme = isDark(c)
        val unselectedBg = if (isDarkTheme) safeColor("#141416", Color.DKGRAY) else safeColor("#E5E5EA", Color.LTGRAY)
        val unselectedFg = mutedClr(c)
        val selectedBg = primary(c)
        val selectedFg = Color.WHITE
        val r = dp(c, 999).toFloat()

        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        items.forEachIndexed { index, (label, value) ->
            val isSelected = (value == selectedValue)
            val chip = TextView(c).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(if (isSelected) selectedFg else unselectedFg)
                typeface = font(c, bold = isSelected)
                gravity = Gravity.CENTER
                maxLines = 1
                val pV = dp(c, 9)
                val pH = dp(c, 16)
                setPadding(pH, pV, pH, pV)
                background = ripple(shape(if (isSelected) selectedBg else unselectedBg, r), r)
                if (isSelected) elevation = dp(c, 1).toFloat()
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (value != selectedValue) onSelect(value)
                }
            }
            row.addView(
                chip,
                LinearLayout.LayoutParams(WRAP, WRAP).apply {
                    if (index > 0) marginStart = dp(c, 8)
                }
            )
        }

        return HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            addView(row, FrameLayout.LayoutParams(WRAP, WRAP))
        }
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

    /** iOS 26 tarzı açma/kapama düğmesi. Dinleyici yalnızca kullanıcı dokunuşunda çağrılır. */
    fun iosSwitch(c: Context, checked: Boolean, onChange: (Boolean) -> Unit): IosSwitch =
        IosSwitch(c).apply {
            isChecked = checked
            setOnCheckedChangeListener(onChange)
        }

    data class NavItem(val title: String, val iconRes: Int)

    fun floatingNavBar(
        c: Context,
        items: List<NavItem>,
        selectedIndex: Int,
        viewPager: ViewPager2? = null,
        onSelect: (Int) -> Unit
    ): BlurView {
        val isDarkTheme = isDark(c)
        val pillRadius = 999f

        val blurView = BlurView(c).apply {
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            elevation = dp(c, 16).toFloat()
            background = if (isDarkTheme) {
                shape(Color.parseColor("#201C1C1E"), pillRadius, Color.parseColor("#45FFFFFF"), dp(c, 1.2f))
            } else {
                shape(Color.parseColor("#28FFFFFF"), pillRadius, Color.parseColor("#22000000"), dp(c, 1.2f))
            }
        }

        val activity = c as? Activity
        val rootView = activity?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
            ?: blurView

        blurView.setupWith(rootView, RenderScriptBlur(c)).apply {
            activity?.window?.decorView?.background?.let { setFrameClearDrawable(it) }
            setBlurRadius(18f)
            setBlurAutoUpdate(true)
        }

        val tabsRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6))
        }

        val activeColor = if (isDarkTheme) Color.parseColor("#0A84FF") else Color.parseColor("#007AFF")
        val inactiveColor = if (isDarkTheme) Color.parseColor("#8E8E93") else Color.parseColor("#3C3C43")

        fun updateIcons(activeIndex: Int) {
            for (i in 0 until tabsRow.childCount) {
                val cell = tabsRow.getChildAt(i) as? ViewGroup
                val icon = cell?.getChildAt(0) as? ImageView
                val isSel = (i == activeIndex)
                icon?.imageTintList = ColorStateList.valueOf(if (isSel) activeColor else inactiveColor)

                val cellBg = if (isSel) {
                    if (isDarkTheme) {
                        shape(Color.parseColor("#25FFFFFF"), 999f, Color.parseColor("#45FFFFFF"), dp(c, 1))
                    } else {
                        shape(Color.parseColor("#15007AFF"), 999f, Color.parseColor("#30007AFF"), dp(c, 1))
                    }
                } else {
                    shape(Color.TRANSPARENT, 999f)
                }
                cell?.background = ripple(cellBg, 999f)
            }
        }

        items.forEachIndexed { index, item ->
            val isSelected = (index == selectedIndex)
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
                    shape(Color.parseColor("#25FFFFFF"), 999f, Color.parseColor("#45FFFFFF"), dp(c, 1))
                } else {
                    shape(Color.parseColor("#15007AFF"), 999f, Color.parseColor("#30007AFF"), dp(c, 1))
                }
            } else {
                shape(Color.TRANSPARENT, 999f)
            }

            val cellWidth = dp(c, 76)
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

            tabsRow.addView(tabCell, LinearLayout.LayoutParams(cellWidth, cellHeight).apply {
                gravity = Gravity.CENTER
                setMargins(dp(c, 4), 0, dp(c, 4), 0)
            })
        }

        blurView.addView(tabsRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        if (viewPager != null) {
            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateIcons(position)
                }
            })
        }

        return blurView
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

/**
 * iOS 26 tarzı açma/kapama düğmesi (kendi çizen View).
 *
 * Animasyon (iOS 26 "liquid glass" davranışı, iPhone kayıtlarına göre ayarlı):
 *  1) Basınca kulp büyür, izden taşar ve saydam cama dönüşür (altındaki iz rengi görünür)
 *  2) Bırakınca kulp diğer uca kayar; öndeki kenar hızlı, arkadaki kenar geriden gelir (sıvı gibi uzar)
 *  3) Varınca cam kulp tekrar opak beyaza döner ve normal boyuna iner
 * İz rengi (gri -> #34C759) öndeki kenarı izler.
 *
 *   val sw = Ui.iosSwitch(context, checked = true) { checked -> ... }
 *   sw.isChecked = false   // koddan ayarlama animasyonsuzdur ve dinleyiciyi TETİKLEMEZ
 */
class IosSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun px(v: Float): Float = v * density

    // ---- Ölçüler (dp) ----
    private val trackW = px(66f)
    private val trackH = px(30f)
    private val viewH = px(44f)            // basılı kulp izden taşar; görünüm izden yüksektir
    private val margin = px(2.5f)
    private val thumbW = px(40f)
    private val thumbWPressed = px(58f)   // iPhone'da cam kulp izin ~%90'ı kadar
    private val thumbH = trackH - 2f * margin
    private val thumbHPressed = px(37f)

    // ---- Süreler (ms) ve eğriler: hissi buradan ayarla ----
    private val moveMs = 400L              // kayma süresi
    private val pressMs = 140L             // basınca büyüme / camlaşma
    private val sizeBackDelayMs = 80L      // bırakınca boyun küçülmeye başlaması
    private val sizeBackMs = 280L
    private val glassBackDelayMs = 150L    // camın opak beyaza dönmeye başlaması
    private val glassBackMs = 230L
    private val easeOut = PathInterpolator(0.2f, 0.9f, 0.25f, 1f)
    private val leadEase = PathInterpolator(0.2f, 0.8f, 0.3f, 1f)     // öndeki kenar
    private val trailEase = PathInterpolator(0.3f, 0.15f, 0.2f, 1f)   // arkadaki kenar

    private val onColor = Color.parseColor("#34C759")
    private val offColorDark = Color.parseColor("#39393D")
    private val offColorLight = Color.parseColor("#E9E9EA")

    private val evaluator = ArgbEvaluator()
    private val rect = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(px(2f), 0f, px(1f), Color.argb(60, 0, 0, 0))
    }
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = px(0.8f)
        color = Color.argb(46, 0, 0, 0)
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = px(1.4f)
        color = Color.WHITE
    }

    private var checkedState = false
    private var tL = 0f        // kulbun SOL kenarının ilerlemesi (0 = kapalı uç, 1 = açık uç)
    private var tR = 0f        // kulbun SAĞ kenarının ilerlemesi
    private var colorP = 0f    // iz rengi: 0 = kapalı, 1 = açık
    private var press = 0f     // kulp boyutu: 0 = normal, 1 = büyük
    private var glass = 0f     // kulp saydamlığı: 0 = opak beyaz, 1 = cam

    private var moveAnim: ValueAnimator? = null
    private var sizeAnim: ValueAnimator? = null
    private var glassAnim: ValueAnimator? = null
    private var listener: ((Boolean) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        // setShadowLayer yazılım katmanı ister
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    /** Koddan ayarlama: animasyonsuz, dinleyiciyi çağırmaz. */
    var isChecked: Boolean
        get() = checkedState
        set(value) {
            checkedState = value
            moveAnim?.cancel()
            val p = if (value) 1f else 0f
            tL = p
            tR = p
            colorP = p
            invalidate()
        }

    /** Dinleyici yalnızca kullanıcı dokunuşunda çağrılır. */
    fun setOnCheckedChangeListener(l: ((Boolean) -> Unit)?) {
        listener = l
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.4f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(trackW.toInt(), viewH.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // İz (dikeyde ortalı)
        val off = if (Ui.isDark(context)) offColorDark else offColorLight
        trackPaint.color = evaluator.evaluate(colorP, off, onColor) as Int
        val trackTop = (h - trackH) / 2f
        rect.set(0f, trackTop, w, trackTop + trackH)
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, trackPaint)

        // Kulp: sol ve sağ kenar ayrı ilerler (sıvı uzama etkisi)
        val extra = (thumbWPressed - thumbW) * press
        val minL = margin
        val maxL = w - margin - thumbW
        val left = minL + (maxL - minL) * tL - extra * tL
        val right = minL + thumbW + (maxL - minL) * tR + extra * (1f - tR)
        val th = thumbH + (thumbHPressed - thumbH) * press
        val top = h / 2f - th / 2f
        val bottom = top + th
        val r = th / 2f
        rect.set(left, top, right, bottom)

        // Dolgu: cam iken neredeyse tamamen saydamlaşır, alttaki iz rengi görünür.
        // Gölge de camken kaybolur; yoksa saydam dolgunun altından koyu leke gibi görünür.
        thumbPaint.setShadowLayer(px(2f), 0f, px(1f), Color.argb((60f * (1f - glass)).toInt(), 0, 0, 0))
        thumbPaint.alpha = (255f - 215f * glass).toInt()
        canvas.drawRoundRect(rect, r, r, thumbPaint)

        // Cam parlaması (üstte parlak, altta hafif)
        if (glass > 0.01f) {
            glassPaint.shader = LinearGradient(
                0f, top, 0f, bottom,
                intArrayOf(
                    Color.argb((110f * glass).toInt(), 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Color.argb((190f * glass).toInt(), 255, 255, 255)
                ),
                floatArrayOf(0f, 0.35f, 0.78f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, r, r, glassPaint)
        }

        // İnce dış çizgi + camken parlak iç kenar
        strokePaint.color = Color.argb((46f + 50f * glass).toInt(), 0, 0, 0)
        canvas.drawRoundRect(rect, r, r, strokePaint)
        if (glass > 0.01f) {
            rimPaint.alpha = (200f * glass).toInt()
            val inset = px(0.7f)
            rect.inset(inset, inset)
            canvas.drawRoundRect(rect, r - inset, r - inset, rimPaint)
        }
    }

    private fun animateSize(target: Float, delay: Long, duration: Long) {
        sizeAnim?.cancel()
        sizeAnim = ValueAnimator.ofFloat(press, target).apply {
            this.duration = duration
            startDelay = delay
            interpolator = easeOut
            addUpdateListener {
                press = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun animateGlass(target: Float, delay: Long, duration: Long) {
        glassAnim?.cancel()
        glassAnim = ValueAnimator.ofFloat(glass, target).apply {
            this.duration = duration
            startDelay = delay
            interpolator = easeOut
            addUpdateListener {
                glass = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun animateMove(forward: Boolean) {
        moveAnim?.cancel()
        val startL = tL
        val startR = tR
        val target = if (forward) 1f else 0f
        moveAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = moveMs
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                val lead = leadEase.getInterpolation(t)
                val trail = trailEase.getInterpolation(t)
                if (forward) {
                    tR = startR + (target - startR) * lead
                    tL = startL + (target - startL) * trail
                    colorP = tR
                } else {
                    tL = startL + (target - startL) * lead
                    tR = startR + (target - startR) * trail
                    colorP = tL
                }
                invalidate()
            }
            start()
        }
    }

    /** Parmak kalkınca: değiştiyse cam, kayma bitene doğru opak beyaza döner; değişmediyse hemen geri döner. */
    private fun release(toggled: Boolean) {
        if (toggled) {
            animateSize(0f, sizeBackDelayMs, sizeBackMs)
            animateGlass(0f, glassBackDelayMs, glassBackMs)
        } else {
            animateSize(0f, 0L, 160L)
            animateGlass(0f, 0L, 160L)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // ScrollView içinde dikey kaydırma bizi iptal etmesin
                parent?.requestDisallowInterceptTouchEvent(true)
                animateSize(1f, 0L, pressMs)
                animateGlass(1f, 0L, pressMs)
            }
            MotionEvent.ACTION_UP -> {
                val inside = event.x >= 0f && event.x <= width && event.y >= 0f && event.y <= height
                release(inside)
                if (inside) performClick()
            }
            MotionEvent.ACTION_CANCEL -> release(false)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        checkedState = !checkedState
        animateMove(checkedState)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        listener?.invoke(checkedState)
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Switch"
        info.isCheckable = true
        info.isChecked = checkedState
    }

    override fun onDetachedFromWindow() {
        moveAnim?.cancel()
        sizeAnim?.cancel()
        glassAnim?.cancel()
        super.onDetachedFromWindow()
    }
}
