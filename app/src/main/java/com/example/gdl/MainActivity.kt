package com.example.gdl

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Store.applyTheme(this)
        } catch (_: Throwable) {}
        super.onCreate(savedInstanceState)

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
        val paste = Ui.button(this, "Yapıştır", Ui.Kind.TONAL) { pasteFromClipboard() }
        val go = Ui.button(this, "İndir", Ui.Kind.FILLED) { start(input.text.toString()) }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(paste, Ui.lp(0, Ui.WRAP, 1f, r = d(4)))
            addView(go, Ui.lp(0, Ui.WRAP, 1f, l = d(4)))
        }
        val inputCard = Ui.card(this).apply {
            addView(input, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(buttons, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(12)))
        }

        val centerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(inputCard, Ui.lp(Ui.MATCH, Ui.WRAP))
        }

        val mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(this@MainActivity))
            setPadding(d(16), 0, d(16), d(84))
            addView(header, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(centerContainer, Ui.lp(Ui.MATCH, 0, 1f))
        }

        // 4 Sebepli Alt Bar: [İndir] • [Galeri] • [Geçmiş] • [Ayarlar]
        val navItems = listOf(
            Ui.NavItem("İndir", R.drawable.ic_tab_download),
            Ui.NavItem("Galeri", R.drawable.ic_tab_gallery),
            Ui.NavItem("Geçmiş", R.drawable.ic_history),
            Ui.NavItem("Ayarlar", R.drawable.ic_tab_settings)
        )

        val navBar = Ui.floatingNavBar(this, navItems, selectedIndex = 0) { index ->
            when (index) {
                0 -> { /* Zaten İndir ekranındayız */ }
                1 -> openGallery()
                2 -> openHistory()
                3 -> openSettings()
            }
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
    private fun openGallery() {
        val intent = Intent(this, GalleryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    @Suppress("DEPRECATION")
    private fun openHistory() {
        val intent = Intent(this, HistoryActivity::class.java).apply {
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
        val i = Intent(this, DownloadService::class.java)
        i.putExtra(DownloadService.EXTRA_URL, url)
        startForegroundService(i)
        input.setText("")
    }
}
