package com.example.gdl

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var goButton: Button
    private lateinit var mainContent: LinearLayout
    private val currentIndex = 0
    private var selectedQuality = Store.QUALITY_BEST

    private fun helper(t: String): TextView = Ui.text(this, t, 12f, Ui.mutedClr(this))

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

        mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(this@MainActivity))
            setPadding(d(16), 0, d(16), d(100))
            clipToPadding = false
            addView(header, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(inputCard, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(16)))
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
