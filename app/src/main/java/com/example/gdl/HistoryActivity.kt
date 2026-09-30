package com.example.gdl

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class HistoryActivity : AppCompatActivity() {
    private lateinit var historyListLayout: LinearLayout
    private var hist: List<JSONObject> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Store.applyTheme(this)
        } catch (_: Throwable) {}
        super.onCreate(savedInstanceState)

        fun d(v: Int) = Ui.dp(this, v)

        // Üst başlık
        val header = Ui.header(this, "Geçmiş", null, null)

        historyListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, d(4), 0, d(100))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            addView(historyListLayout, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        }

        val mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(this@HistoryActivity))
            setPadding(d(16), 0, d(16), 0)
            addView(header, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(scroll, Ui.lp(Ui.MATCH, 0, 1f))
        }

        // Floating Liquid Glass Bottom Navigation Bar (3 items)
        val navItems = listOf(
            Ui.NavItem("İndir", R.drawable.ic_tab_download),
            Ui.NavItem("Geçmiş", R.drawable.ic_history),
            Ui.NavItem("Ayarlar", R.drawable.ic_tab_settings)
        )

        val navBar = Ui.floatingNavBar(this, navItems, selectedIndex = 1) { index ->
            when (index) {
                0 -> openMain()
                1 -> { /* Zaten Geçmiş ekranındayız */ }
                2 -> openSettings()
            }
        }

        val navParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            setMargins(d(28), 0, d(28), d(20))
        }

        val scrollHandler = Handler(Looper.getMainLooper())
        val showBarRunnable = Runnable {
            navBar.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(250)
                .start()
        }

        scroll.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            scrollHandler.removeCallbacks(showBarRunnable)
            scrollHandler.postDelayed(showBarRunnable, 400)

            if (scrollY > oldScrollY + 8) {
                val targetY = navBar.height.toFloat() + d(40)
                navBar.animate()
                    .translationY(targetY)
                    .alpha(0f)
                    .setDuration(250)
                    .start()
            } else if (scrollY < oldScrollY - 8) {
                navBar.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Ui.bg(this@HistoryActivity))
            addView(mainContent, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            addView(navBar, navParams)
        }
        setContentView(root)
        Ui.setupEdgeToEdge(this, root)
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    @Suppress("DEPRECATION")
    private fun openMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }


    @Suppress("DEPRECATION")
    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    override fun onStart() {
        super.onStart()
        DownloadService.onChange = { runOnUiThread { refresh() } }
        refresh()
    }

    override fun onStop() {
        DownloadService.onChange = null
        super.onStop()
    }

    private fun refresh() {
        fun d(v: Int) = Ui.dp(this, v)
        hist = Store.history(this)
        historyListLayout.removeAllViews()

        if (hist.isEmpty()) {
            val empty = Ui.text(
                this,
                "Henüz indirme yok\nYapılan indirmeler burada listelenir.",
                14f, Ui.mutedClr(this)
            ).apply {
                gravity = Gravity.CENTER
                setPadding(d(24), d(48), d(24), d(48))
            }
            historyListLayout.addView(empty)
        } else {
            hist.forEach { h ->
                val row = HistoryRow(this).apply {
                    bind(h)
                    setOnClickListener { showItem(h) }
                }
                historyListLayout.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, d(10))
                })
            }
        }

        val bottomSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, d(110))
        }
        historyListLayout.addView(bottomSpacer)
    }

    private fun start(text: String) {
        val url = Regex("https?://\\S+").find(text)?.value
        if (url == null) return
        val i = Intent(this, DownloadService::class.java)
        i.putExtra(DownloadService.EXTRA_URL, url)
        startForegroundService(i)
    }

    private fun showItem(h: JSONObject) {
        val url = h.getString("url")
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val context = this
        val cardBgColor = if (Ui.isDark(context)) Color.parseColor("#1C1C1E") else Color.parseColor("#F8F8FA")
        val borderClr = if (Ui.isDark(context)) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 20), Ui.dp(context, 20), Ui.dp(context, 20), Ui.dp(context, 20))
            background = Ui.shape(cardBgColor, Ui.dp(context, 24).toFloat(), borderClr, Ui.dp(context, 1))
            elevation = Ui.dp(context, 16).toFloat()
        }

        // Başlık ve Durum Rozeti
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleView = Ui.text(context, "İndirme Detayı", 18f, Ui.textClr(context), true)
        topRow.addView(titleView, Ui.lp(0, Ui.WRAP, 1f))

        val status = h.optString("status", "")
        val isSuccess = status.contains("başarılı") || status.contains("kaydedildi") || status.startsWith("✓")
        val badge = if (isSuccess) {
            Ui.pill(context, "✓ Başarılı", Color.WHITE, Color.parseColor("#34C759"))
        } else {
            Ui.pill(context, "✗ İndirilemedi", Color.WHITE, Color.parseColor("#FF3B30"))
        }
        topRow.addView(badge, Ui.lp(Ui.WRAP, Ui.WRAP))

        rootLayout.addView(topRow, Ui.lp(Ui.MATCH, Ui.WRAP, b = Ui.dp(context, 16)))

        // Hata Kodları ve URL Alanı (İçi hafif gömülü: Açık modda #EBEBF0, Koyu modda #141416, kaydırılabilir ScrollView, monospace/SpaceGrotesk font)
        val codeBoxBg = if (Ui.isDark(context)) Color.parseColor("#141416") else Color.parseColor("#EBEBF0")
        val codeText = url + "\n\n" + h.optString("log", "")
        val codeTv = Ui.text(context, codeText, 12.5f, Ui.textClr(context), false).apply {
            typeface = Typeface.MONOSPACE
            setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12))
        }
        val codeScroll = ScrollView(context).apply {
            background = Ui.shape(codeBoxBg, Ui.dp(context, 12).toFloat())
            addView(codeTv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val codeScrollParams = Ui.lp(Ui.MATCH, Ui.dp(context, 260), b = Ui.dp(context, 16))
        rootLayout.addView(codeScroll, codeScrollParams)

        // Alt Butonlar (Yan yana iki adet tam oval radius 999f buton)
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val cancelBtn = Button(context).apply {
            text = "Kapat"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Ui.textClr(context))
            typeface = Ui.font(context, bold = true)
            stateListAnimator = null
            minHeight = Ui.dp(context, 48)
            minimumHeight = Ui.dp(context, 48)
            val tonalBg = if (Ui.isDark(context)) Color.parseColor("#3A3A3C") else Color.parseColor("#E5E5EA")
            background = Ui.ripple(Ui.shape(tonalBg, 999f), 999f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                dialog.dismiss()
            }
        }

        val retryBtn = Button(context).apply {
            text = "Tekrar Dene"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.WHITE)
            typeface = Ui.font(context, bold = true)
            stateListAnimator = null
            minHeight = Ui.dp(context, 48)
            minimumHeight = Ui.dp(context, 48)
            val blueBg = Ui.primary(context)
            background = Ui.ripple(Ui.shape(blueBg, 999f), 999f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                start(url)
                dialog.dismiss()
            }
        }

        buttonRow.addView(cancelBtn, Ui.lp(0, Ui.WRAP, 1f, r = Ui.dp(context, 8)))
        buttonRow.addView(retryBtn, Ui.lp(0, Ui.WRAP, 1f, l = Ui.dp(context, 8)))

        rootLayout.addView(buttonRow, Ui.lp(Ui.MATCH, Ui.WRAP))

        dialog.setContentView(rootLayout)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }
}
