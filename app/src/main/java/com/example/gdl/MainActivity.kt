package com.example.gdl

import android.Manifest
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
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

        // Geçmiş listesi
        val sectionTitle = Ui.text(this, "Son indirmeler", 13f, Ui.mutedClr(this), true)

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
            14f, Ui.mutedClr(this)
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
            setBackgroundColor(Ui.bg(this@MainActivity))
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

        var lastFirstVisibleItem = 0
        var lastTop = 0
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    scrollHandler.removeCallbacks(showBarRunnable)
                    scrollHandler.postDelayed(showBarRunnable, 200)
                }
            }

            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                scrollHandler.removeCallbacks(showBarRunnable)
                scrollHandler.postDelayed(showBarRunnable, 400)

                val firstChild = view?.getChildAt(0) ?: return
                val top = firstChild.top
                val isScrollingDown = if (firstVisibleItem == lastFirstVisibleItem) {
                    top < lastTop
                } else {
                    firstVisibleItem > lastFirstVisibleItem
                }

                if (isScrollingDown && top != lastTop) {
                    val targetY = navBar.height.toFloat() + d(40)
                    navBar.animate()
                        .translationY(targetY)
                        .alpha(0f)
                        .setDuration(250)
                        .start()
                } else if (!isScrollingDown && top != lastTop) {
                    navBar.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }

                lastFirstVisibleItem = firstVisibleItem
                lastTop = top
            }
        })

        val root = FrameLayout(this).apply {
            setBackgroundColor(Ui.bg(this@MainActivity))
            addView(mainContent, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            addView(navBar, navParams)
        }
        setContentView(root)
        Ui.setupEdgeToEdge(this, root)
    }

    @Suppress("DEPRECATION")
    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        startActivity(intent)
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
