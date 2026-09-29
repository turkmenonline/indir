package com.example.gdl

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var input: EditText
    private lateinit var adapter: HistoryAdapter
    private var hist: List<JSONObject> = emptyList()

    private inner class HistoryAdapter : BaseAdapter() {
        override fun getCount() = hist.size
        override fun getItem(position: Int): Any = hist[position]
        override fun getItemId(position: Int): Long = hist[position].optLong("id")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? HistoryRow) ?: HistoryRow(this@MainActivity)
            row.bind(hist[position])
            return row
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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

        // Geçmiş listesi
        val sectionTitle = Ui.text(this, "Son indirmeler", 13f, Ui.MUTED, true)

        adapter = HistoryAdapter()
        val list = ListView(this).apply {
            adapter = this@MainActivity.adapter
            divider = ColorDrawable(Color.TRANSPARENT)
            dividerHeight = d(10)
            selector = ColorDrawable(Color.TRANSPARENT)
            clipToPadding = false
            setPadding(0, d(4), 0, d(84))
            isVerticalScrollBarEnabled = false
            setOnItemClickListener { _, _, pos, _ -> showItem(hist[pos]) }
        }
        val empty = Ui.text(
            this,
            "Henüz indirme yok\nBir link yapıştır ya da başka bir uygulamadan Paylaş → İNDİR seç.",
            14f, Ui.MUTED
        ).apply {
            gravity = Gravity.CENTER
            setPadding(d(24), 0, d(24), d(84))
        }
        val listHolder = FrameLayout(this).apply {
            addView(list, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            addView(empty, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        }
        list.emptyView = empty

        val mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(d(16), 0, d(16), 0)
            addView(header, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(inputCard, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(sectionTitle, Ui.lp(Ui.WRAP, Ui.WRAP, l = d(4), t = d(24), b = d(8)))
            addView(listHolder, Ui.lp(Ui.MATCH, 0, 1f))
        }

        // Floating Liquid Glass Bottom Navigation Bar
        val navItems = listOf(
            Ui.NavItem("İndir", R.drawable.ic_tab_download),
            Ui.NavItem("Galeri", R.drawable.ic_tab_gallery),
            Ui.NavItem("Ayarlar", R.drawable.ic_tab_settings)
        )

        val navBar = Ui.floatingNavBar(this, navItems, selectedIndex = 0) { index ->
            when (index) {
                0 -> { /* Zaten İndir ekranındayız */ }
                1 -> openGallery()
                2 -> startActivity(Intent(this, SettingsActivity::class.java))
            }
        }

        val navParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            setMargins(d(20), 0, d(20), d(20))
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Ui.BG)
            addView(mainContent, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            addView(navBar, navParams)
        }
        setContentView(root)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            try {
                startActivity(fallback)
            } catch (e2: Exception) {
                Toast.makeText(this, "Galeri açılamadı", Toast.LENGTH_SHORT).show()
            }
        }
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
        hist = Store.history(this)
        adapter.notifyDataSetChanged()
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

    private fun showItem(h: JSONObject) {
        val url = h.getString("url")
        AlertDialog.Builder(this)
            .setTitle(h.getString("status"))
            .setMessage(url + "\n\n" + h.optString("log"))
            .setPositiveButton("Tekrar dene") { _, _ -> start(url) }
            .setNegativeButton("Kapat", null)
            .show()
    }
}
