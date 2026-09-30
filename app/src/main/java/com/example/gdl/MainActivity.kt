package com.example.gdl

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var goButton: Button
    private lateinit var mainContent: LinearLayout
    private lateinit var activeDownloadsContainer: LinearLayout
    private val currentIndex = 0
    private var selectedQuality = Store.QUALITY_BEST

    private var activeDetailDialog: Dialog? = null
    private var activeDetailId: Long = -1L
    private val dialogUpdateHandler = Handler(Looper.getMainLooper())
    private val dialogUpdateRunnable = object : Runnable {
        override fun run() {
            if (activeDetailDialog != null && activeDetailId != -1L) {
                updateOpenDetailDialog()
                dialogUpdateHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Store.applyTheme(this)
        } catch (_: Throwable) {}
        super.onCreate(savedInstanceState)
        Store.cleanupPendingDownloads(this)
        selectedQuality = Store.videoQuality(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        fun d(v: Int) = Ui.dp(this, v)

        // Üst başlık
        val header = Ui.header(this, "İNDİR", null, null)

        // Link kartı
        input = Ui.field(this, "Gönderi linkini yapıştırın").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    start(text.toString())
                    true
                } else false
            }
        }
        val qualityButtonsHolder = FrameLayout(this)
        fun updateQualityButtons() {
            qualityButtonsHolder.removeAllViews()
            val qualities = listOf(
                Pair("En iyi", Store.QUALITY_BEST),
                Pair("720p", Store.QUALITY_720P),
                Pair("Ses", Store.QUALITY_AUDIO_ONLY)
            )
            val currentIndex = qualities.indexOfFirst { it.second == selectedQuality }.coerceAtLeast(0)
            val titles = qualities.map { it.first }
            val seg = Ui.segmentedControl(this@MainActivity, titles, currentIndex) { index ->
                val qVal = qualities[index].second
                if (selectedQuality != qVal) {
                    selectedQuality = qVal
                    updateQualityButtons()
                }
            }
            qualityButtonsHolder.addView(seg, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        }
        updateQualityButtons()

        val paste = Ui.button(this, "Yapıştır", Ui.Kind.TONAL) { pasteFromClipboard() }
        goButton = Ui.button(this, "İndir", Ui.Kind.FILLED) { start(input.text.toString()) }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(paste, Ui.lp(0, Ui.WRAP, 1f, r = d(4)))
            addView(goButton, Ui.lp(0, Ui.WRAP, 1f, l = d(4)))
        }
        val inputCard = Ui.card(this).apply {
            addView(input, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(qualityButtonsHolder, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(12), b = d(12)))
            addView(buttons, Ui.lp(Ui.MATCH, Ui.WRAP))
        }

        activeDownloadsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(this@MainActivity))
            setPadding(d(16), 0, d(16), d(100))
            clipToPadding = false
            addView(header, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(inputCard, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(16)))
            addView(activeDownloadsContainer, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(16)))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            addView(mainContent)
        }

        // Floating Liquid Glass Bottom Navigation Bar (3 items)
        val navItems = listOf(
            Ui.NavItem("İndir", R.drawable.ic_tab_download),
            Ui.NavItem("Geçmiş", R.drawable.ic_history),
            Ui.NavItem("Ayarlar", R.drawable.ic_tab_settings)
        )

        val navBar = Ui.floatingNavBar(this, navItems, selectedIndex = 0) { index ->
            navigateToTab(index, 0)
        }

        val navParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            setMargins(d(28), 0, d(28), d(20))
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Ui.bg(this@MainActivity))
            addView(scroll, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            addView(navBar, navParams)
        }
        setContentView(root)
        Ui.setupEdgeToEdge(this, root)
        handleIntent(intent)
        handlePageAnimation(intent)
    }

    override fun onStart() {
        super.onStart()
        DownloadService.onStateChanged = {
            runOnUiThread {
                updateActiveBox()
                if (activeDetailDialog != null && activeDetailId != -1L) {
                    if (!DownloadService.activeDownloads.containsKey(activeDetailId)) {
                        activeDetailDialog?.dismiss()
                    } else {
                        updateOpenDetailDialog()
                    }
                }
            }
        }
        updateActiveBox()
    }

    override fun onStop() {
        DownloadService.onStateChanged = null
        activeDetailDialog?.dismiss()
        activeDetailDialog = null
        activeDetailId = -1L
        dialogUpdateHandler.removeCallbacks(dialogUpdateRunnable)
        super.onStop()
    }

    private fun updateActiveBox() {
        activeDownloadsContainer.removeAllViews()
        val downloads = DownloadService.activeDownloads.values.toList()
        if (downloads.isEmpty()) {
            activeDownloadsContainer.visibility = View.GONE
        } else {
            activeDownloadsContainer.visibility = View.VISIBLE
            downloads.forEach { st ->
                val card = createActiveDownloadRow(st)
                activeDownloadsContainer.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, Ui.dp(this@MainActivity, 10))
                })
            }
        }
    }

    private fun createActiveDownloadRow(st: DownloadService.DownloadState): View {
        val c = this
        val d = Ui.dp(c, 14)
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d, d, d, d)
            val r = Ui.dp(c, 16).toFloat()
            background = Ui.shape(Ui.cardBg(c), r, Ui.line(c), Ui.dp(c, 1))
            setOnClickListener {
                showActiveDetailDialog(st.id)
            }
        }

        val topRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val platformTv = Ui.text(c, st.platform, 15f, Ui.textClr(c), true).apply {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }

        val detailTv = Ui.text(c, st.detail, 13f, Ui.mutedClr(c)).apply {
            gravity = Gravity.END
            isSingleLine = true
        }

        topRow.addView(platformTv, Ui.lp(0, Ui.WRAP, 1f))
        topRow.addView(detailTv, Ui.lp(Ui.WRAP, Ui.WRAP, l = Ui.dp(c, 8)))

        val progressBar = ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(Ui.primary(c))
            if (st.detail == "Sırada" || st.pct < 0) {
                isIndeterminate = true
            } else {
                isIndeterminate = false
                progress = st.pct
            }
        }

        card.addView(topRow, Ui.lp(Ui.MATCH, Ui.WRAP))
        card.addView(progressBar, Ui.lp(Ui.MATCH, Ui.dp(c, 3), t = Ui.dp(c, 10)))

        return card
    }

    private var openDetailCodeTv: TextView? = null
    private var openDetailProgressBar: ProgressBar? = null

    private fun showActiveDetailDialog(id: Long) {
        activeDetailId = id
        val st = DownloadService.activeDownloads[id] ?: return

        val dialog = Dialog(this)
        activeDetailDialog = dialog
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

        val statusText = if (st.detail == "Sırada") "Sırada" else "İndiriliyor..."
        val badge = Ui.pill(context, statusText, Color.WHITE, Ui.primary(context))
        topRow.addView(badge, Ui.lp(Ui.WRAP, Ui.WRAP))

        rootLayout.addView(topRow, Ui.lp(Ui.MATCH, Ui.WRAP, b = Ui.dp(context, 16)))

        // Kod Kutusu
        val codeBoxBg = if (Ui.isDark(context)) Color.parseColor("#141416") else Color.parseColor("#EBEBF0")
        
        fun buildDetailText(): String {
            val currSt = DownloadService.activeDownloads[id] ?: st
            val p = currSt.platform
            val u = currSt.url
            val det = currSt.detail
            val startT = currSt.startTime
            val startStr = if (startT > 0L) Store.formatTimestamp(startT) else "-"
            val elapsed = if (startT > 0L) (System.currentTimeMillis() - startT) / 1000 else 0L
            return "Platform: $p\nURL: $u\nDurum/Hız: $det\nBaşlangıç: $startStr\nGeçen Süre: $elapsed sn"
        }

        val codeTv = Ui.text(context, buildDetailText(), 12.5f, Ui.textClr(context), false).apply {
            typeface = Typeface.MONOSPACE
            setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12))
        }
        openDetailCodeTv = codeTv

        val codeScroll = ScrollView(context).apply {
            background = Ui.shape(codeBoxBg, Ui.dp(context, 12).toFloat())
            addView(codeTv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        rootLayout.addView(codeScroll, Ui.lp(Ui.MATCH, Ui.dp(context, 180), b = Ui.dp(context, 12)))

        // ProgressBar
        val dialogProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(Ui.primary(context))
            if (st.detail == "Sırada" || st.pct < 0) {
                isIndeterminate = true
            } else {
                isIndeterminate = false
                progress = st.pct
            }
        }
        openDetailProgressBar = dialogProgressBar
        rootLayout.addView(dialogProgressBar, Ui.lp(Ui.MATCH, Ui.dp(context, 4), b = Ui.dp(context, 16)))

        // Alt Butonlar
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val closeBtn = Button(context).apply {
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

        val cancelBtn = Button(context).apply {
            text = "İptal Et"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.WHITE)
            typeface = Ui.font(context, bold = true)
            stateListAnimator = null
            minHeight = Ui.dp(context, 48)
            minimumHeight = Ui.dp(context, 48)
            val redBg = Color.parseColor("#FF3B30")
            background = Ui.ripple(Ui.shape(redBg, 999f), 999f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                AlertDialog.Builder(context)
                    .setTitle("İptal Onayı")
                    .setMessage("İndirme iptal edilsin mi?")
                    .setPositiveButton("Evet") { _, _ ->
                        DownloadService.cancelDownload(id)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Vazgeç", null)
                    .show()
            }
        }

        buttonRow.addView(closeBtn, Ui.lp(0, Ui.WRAP, 1f, r = Ui.dp(context, 8)))
        buttonRow.addView(cancelBtn, Ui.lp(0, Ui.WRAP, 1f, l = Ui.dp(context, 8)))
        rootLayout.addView(buttonRow, Ui.lp(Ui.MATCH, Ui.WRAP))

        dialog.setContentView(rootLayout)
        dialog.setOnDismissListener {
            activeDetailDialog = null
            activeDetailId = -1L
            openDetailCodeTv = null
            openDetailProgressBar = null
            dialogUpdateHandler.removeCallbacks(dialogUpdateRunnable)
        }

        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()

        dialogUpdateHandler.removeCallbacks(dialogUpdateRunnable)
        dialogUpdateHandler.postDelayed(dialogUpdateRunnable, 1000)
    }

    private fun updateOpenDetailDialog() {
        if (activeDetailDialog == null || activeDetailId == -1L) return
        val st = DownloadService.activeDownloads[activeDetailId]
        if (st == null) {
            activeDetailDialog?.dismiss()
            return
        }
        openDetailCodeTv?.text = run {
            val p = st.platform
            val u = st.url
            val det = st.detail
            val startT = st.startTime
            val startStr = if (startT > 0L) Store.formatTimestamp(startT) else "-"
            val elapsed = if (startT > 0L) (System.currentTimeMillis() - startT) / 1000 else 0L
            "Platform: $p\nURL: $u\nDurum/Hız: $det\nBaşlangıç: $startStr\nGeçen Süre: $elapsed sn"
        }
        openDetailProgressBar?.let { pb ->
            if (st.detail == "Sırada" || st.pct < 0) {
                pb.isIndeterminate = true
            } else {
                pb.isIndeterminate = false
                pb.progress = st.pct
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        handlePageAnimation(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val sharedUrl = intent?.getStringExtra("shared_url")
        if (!sharedUrl.isNullOrBlank()) {
            input.setText(sharedUrl)
            start(sharedUrl)
        }
    }

    private fun handlePageAnimation(intent: Intent?) {
        val fromIndex = intent?.getIntExtra("from_index", -1) ?: -1
        if (fromIndex != -1) {
            val fromRight = fromIndex < currentIndex
            Ui.animatePageIn(mainContent, fromRight)
        }
    }

    @Suppress("DEPRECATION")
    private fun navigateToTab(targetIndex: Int, currentIndex: Int) {
        if (targetIndex == currentIndex) return
        val intent = when (targetIndex) {
            0 -> Intent(this, MainActivity::class.java)
            1 -> Intent(this, HistoryActivity::class.java)
            2 -> Intent(this, SettingsActivity::class.java)
            else -> return
        }.apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("from_index", this@MainActivity.currentIndex)
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isHorizontalSwipe = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = ev.rawX
                touchDownY = ev.rawY
                isHorizontalSwipe = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = ev.rawX - touchDownX
                val deltaY = ev.rawY - touchDownY
                if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 25) {
                    isHorizontalSwipe = true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isHorizontalSwipe) {
                    val deltaX = ev.rawX - touchDownX
                    if (Math.abs(deltaX) > 100) {
                        if (deltaX > 0) {
                            onSwipeRight()
                        } else {
                            onSwipeLeft()
                        }
                        isHorizontalSwipe = false
                        return true
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun onSwipeLeft() {
        navigateToTab(1, 0)
    }

    private fun onSwipeRight() {
        // no-op
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(ClipboardManager::class.java)
        val t = cm?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(this)?.toString()
        if (t.isNullOrBlank()) {
            Toast.makeText(this, "Panoda metin yok", Toast.LENGTH_SHORT).show()
        } else {
            input.setText(t.trim())
            input.setSelection(input.text.length)
        }
    }

    @Suppress("PrivateApi")
    private fun start(text: String) {
        val url = Regex("https?://\\S+").find(text)?.value
        if (url == null) {
            Toast.makeText(this, "Geçerli link bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }
        startDownload(url)
    }

    private fun startDownload(url: String) {
        val i = Intent(this, DownloadService::class.java).apply {
            putExtra(DownloadService.EXTRA_URL, url)
            putExtra("custom_quality", selectedQuality)
        }
        startForegroundService(i)
        input.setText("")
    }
}
