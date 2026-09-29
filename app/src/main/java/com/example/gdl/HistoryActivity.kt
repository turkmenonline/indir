package com.example.gdl

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class HistoryActivity : AppCompatActivity() {
    private lateinit var adapter: HistoryAdapter
    private var hist: List<JSONObject> = emptyList()

    private inner class HistoryAdapter : BaseAdapter() {
        override fun getCount() = hist.size
        override fun getItem(position: Int): Any = hist[position]
        override fun getItemId(position: Int): Long = hist[position].optLong("id")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? HistoryRow) ?: HistoryRow(this@HistoryActivity)
            row.bind(hist[position])
            return row
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Store.applyTheme(this)
        } catch (_: Throwable) {}
        super.onCreate(savedInstanceState)

        fun d(v: Int) = Ui.dp(this, v)

        // Üst başlık
        val header = Ui.header(
            this, "Geçmiş",
            Ui.iconButton(this, R.drawable.ic_arrow_back, "Geri") { finish() },
            null
        )

        // Geçmiş listesi
        adapter = HistoryAdapter()
        val list = ListView(this).apply {
            adapter = this@HistoryActivity.adapter
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
            "Henüz indirme geçmişi yok\nİndirdiğiniz dosyalar burada listelenir.",
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
            setBackgroundColor(Ui.bg(this@HistoryActivity))
            setPadding(d(16), 0, d(16), 0)
            addView(header, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(listHolder, Ui.lp(Ui.MATCH, 0, 1f))
        }

        // 4 Sebepli Alt Bar: [İndir] • [Galeri] • [Geçmiş] • [Ayarlar]
        val navItems = listOf(
            Ui.NavItem("İndir", R.drawable.ic_tab_download),
            Ui.NavItem("Galeri", R.drawable.ic_tab_gallery),
            Ui.NavItem("Geçmiş", R.drawable.ic_history),
            Ui.NavItem("Ayarlar", R.drawable.ic_tab_settings)
        )

        val navBar = Ui.floatingNavBar(this, navItems, selectedIndex = 2) { index ->
            when (index) {
                0 -> openMain()
                1 -> openGallery()
                2 -> { /* Zaten Geçmiş ekranındayız */ }
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
    private fun openGallery() {
        val intent = Intent(this, GalleryActivity::class.java).apply {
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
        hist = Store.history(this)
        adapter.notifyDataSetChanged()
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

    private fun start(url: String) {
        val i = Intent(this, DownloadService::class.java)
        i.putExtra(DownloadService.EXTRA_URL, url)
        startForegroundService(i)
    }
}
