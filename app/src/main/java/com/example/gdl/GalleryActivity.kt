package com.example.gdl

import android.app.Dialog
import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.io.File
import java.util.concurrent.Executors

class GalleryActivity : AppCompatActivity() {

    data class MediaItem(
        val uri: Uri?,
        val file: File?,
        val isVideo: Boolean,
        val dateModified: Long
    ) {
        val key: String
            get() = file?.canonicalPath ?: file?.absolutePath ?: uri?.toString() ?: ""
    }

    private lateinit var gridView: GridView
    private lateinit var emptyView: LinearLayout
    private lateinit var headerView: View
    private var mediaList: List<MediaItem> = emptyList()
    private lateinit var adapter: GalleryAdapter

    // Memory cache for 60/120 FPS fast scrolling
    private val memoryCache: LruCache<String, Bitmap> by lazy {
        val maxMemoryKB = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSizeKB = maxMemoryKB / 8
        object : LruCache<String, Bitmap>(cacheSizeKB) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }
    }

    // Background thread pool for async thumbnail decoding
    private val threadPool = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Store.applyTheme(this)
        } catch (_: Throwable) {}
        super.onCreate(savedInstanceState)

        fun d(v: Int) = Ui.dp(this, v)

        // Header
        val header = Ui.header(this, "Galeri", null, null)
        headerView = header

        // Empty State View
        val emptyIcon = TextView(this).apply {
            text = "🖼️"
            textSize = 48f
            gravity = Gravity.CENTER
        }
        val emptyTitle = Ui.text(this, "Henüz indirilmiş fotoğraf yok", 15f, Ui.textClr(this), true).apply {
            gravity = Gravity.CENTER
        }
        val emptySub = Ui.text(this, "İndirdiğiniz fotoğraf ve videolar burada gösterilir.", 13f, Ui.mutedClr(this)).apply {
            gravity = Gravity.CENTER
        }
        emptyView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(d(24), 0, d(24), d(84))
            addView(emptyIcon)
            addView(emptyTitle, Ui.lp(Ui.WRAP, Ui.WRAP, t = d(12)))
            addView(emptySub, Ui.lp(Ui.WRAP, Ui.WRAP, t = d(6)))
        }

        // Grid View for 3-column layout
        adapter = GalleryAdapter()
        gridView = GridView(this).apply {
            numColumns = 3
            horizontalSpacing = d(4)
            verticalSpacing = d(4)
            clipToPadding = false
            setPadding(d(12), d(4), d(12), d(84))
            this.adapter = this@GalleryActivity.adapter
            setOnItemClickListener { _, _, position, _ ->
                if (position in mediaList.indices) {
                    showSwipeFullscreenPreview(position)
                }
            }
        }

        val gridHolder = FrameLayout(this).apply {
            addView(gridView, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            addView(emptyView, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        }
        gridView.emptyView = emptyView

        val mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(this@GalleryActivity))
            addView(header, Ui.lp(Ui.MATCH, Ui.WRAP, l = d(16), r = d(16)))
            addView(gridHolder, Ui.lp(Ui.MATCH, 0, 1f))
        }

        // Floating Liquid Glass Bottom Navigation Bar
        val navItems = listOf(
            Ui.NavItem("İndir", R.drawable.ic_tab_download),
            Ui.NavItem("Galeri", R.drawable.ic_tab_gallery),
            Ui.NavItem("Ayarlar", R.drawable.ic_tab_settings)
        )

        val navBar = Ui.floatingNavBar(this, navItems, selectedIndex = 1) { index ->
            when (index) {
                0 -> openMain()
                1 -> { /* Zaten Galeri ekranındayız */ }
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

        // Scroll listener for hide/show on grid scroll with 400ms idle timer
        val showBarRunnable = Runnable {
            navBar.animate().translationY(0f).alpha(1f).setDuration(250).start()
        }

        gridView.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    mainHandler.removeCallbacks(showBarRunnable)
                    mainHandler.postDelayed(showBarRunnable, 300)
                }
            }

            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                mainHandler.removeCallbacks(showBarRunnable)
                mainHandler.postDelayed(showBarRunnable, 400)
            }
        })

        val root = FrameLayout(this).apply {
            setBackgroundColor(Ui.bg(this@GalleryActivity))
            addView(mainContent, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            addView(navBar, navParams)
        }
        setContentView(root)
        Ui.setupEdgeToEdge(this, root)
    }

    override fun onResume() {
        super.onResume()
        loadMedia()
    }

    override fun onDestroy() {
        try {
            threadPool.shutdown()
        } catch (_: Exception) {}
        super.onDestroy()
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

    private fun loadMedia() {
        val items = mutableListOf<MediaItem>()
        val album = Store.album(this)

        // 1. Scan Public Directories
        val dirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), album),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), album),
            File("/storage/emulated/0/Pictures/$album"),
            File("/storage/emulated/0/Movies/$album")
        )

        for (dir in dirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.length() > 0) {
                        val ext = file.extension.lowercase()
                        val isImg = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "heic")
                        val isVid = ext in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp")
                        if (isImg || isVid) {
                            items.add(MediaItem(Uri.fromFile(file), file, isVid, file.lastModified()))
                        }
                    }
                }
            }
        }

        // 2. Scan MediaStore
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DATE_MODIFIED
            )

            fun scanUri(collectionUri: Uri, isVideo: Boolean) {
                contentResolver.query(
                    collectionUri,
                    projection,
                    "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                    arrayOf("%$album%"),
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val path = if (pathCol >= 0) cursor.getString(pathCol) else null
                        val date = if (dateCol >= 0) cursor.getLong(dateCol) * 1000 else System.currentTimeMillis()
                        val uri = ContentUris.withAppendedId(collectionUri, id)
                        val f = path?.let { File(it) }
                        if (f != null && f.exists() && f.length() > 0) {
                            items.add(MediaItem(uri, f, isVideo, date))
                        }
                    }
                }
            }

            scanUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false)
            scanUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true)
        } catch (_: Exception) {}

        // Distinct by absolute path to eliminate duplicate entries completely
        mediaList = items
            .distinctBy { it.key }
            .sortedByDescending { it.dateModified }

        adapter.notifyDataSetChanged()
    }

    private inner class GalleryAdapter : BaseAdapter() {
        override fun getCount(): Int = mediaList.size
        override fun getItem(position: Int): Any = mediaList[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val frame = (convertView as? FrameLayout) ?: FrameLayout(this@GalleryActivity).apply {
                val sz = (resources.displayMetrics.widthPixels - Ui.dp(this@GalleryActivity, 32)) / 3
                layoutParams = AbsListView.LayoutParams(sz, sz)
            }
            frame.removeAllViews()

            val item = mediaList[position]
            val imgView = ImageView(this@GalleryActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#1A1A24"))
                tag = item.key
            }

            frame.addView(imgView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

            // Video overlay badge
            if (item.isVideo) {
                val playBadge = TextView(this@GalleryActivity).apply {
                    text = "▶"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setBackgroundColor(Color.parseColor("#99000000"))
                }
                val lp = FrameLayout.LayoutParams(Ui.dp(this@GalleryActivity, 26), Ui.dp(this@GalleryActivity, 26), Gravity.BOTTOM or Gravity.END).apply {
                    setMargins(0, 0, Ui.dp(this@GalleryActivity, 6), Ui.dp(this@GalleryActivity, 6))
                }
                frame.addView(playBadge, lp)
            }

            // Async Thumbnail Loading with LruCache memory caching
            val cachedBmp = memoryCache.get(item.key)
            if (cachedBmp != null) {
                imgView.setImageBitmap(cachedBmp)
            } else {
                threadPool.execute {
                    val bmp = loadThumbnailBitmap(item)
                    if (bmp != null) {
                        memoryCache.put(item.key, bmp)
                        mainHandler.post {
                            if (imgView.tag == item.key) {
                                imgView.setImageBitmap(bmp)
                            }
                        }
                    }
                }
            }

            return frame
        }
    }

    private fun loadThumbnailBitmap(item: MediaItem): Bitmap? {
        return try {
            if (item.file != null && item.file.exists()) {
                if (item.isVideo) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(item.file.absolutePath)
                        retriever.frameAtTime
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                } else {
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeFile(item.file.absolutePath, options)
                }
            } else if (item.uri != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentResolver.loadThumbnail(item.uri, Size(250, 250), null)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun showSwipeFullscreenPreview(startPosition: Int) {
        if (mediaList.isEmpty() || startPosition !in mediaList.indices) return

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            statusBarColor = Color.BLACK
            navigationBarColor = Color.BLACK
            WindowCompat.setDecorFitsSystemWindows(this, false)
            val controller = WindowCompat.getInsetsController(this, decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // ViewPager2 for smooth horizontal swipe between media items
        val viewPager = ViewPager2(this).apply {
            adapter = PreviewPagerAdapter()
            setCurrentItem(startPosition, false)
        }
        root.addView(
            viewPager,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Top Bar Container: Clean header with Close button (X) on left, single-piece position indicator in middle
        val btnSize = Ui.dp(this, 40)
        val pH = Ui.dp(this, 16)
        val pV = Ui.dp(this, 12)

        val closeBtn = Ui.text(this, "✕", 22f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { dialog.dismiss() }
        }

        val pageIndicator = Ui.text(this, "${startPosition + 1}/${mediaList.size}", 16f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            typeface = Ui.font(this@GalleryActivity, bold = true)
        }

        val dummyRight = View(this).apply {
            visibility = View.INVISIBLE
        }

        val topBarContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(pH, pV, pH, pV)

            addView(closeBtn, LinearLayout.LayoutParams(btnSize, btnSize))
            addView(pageIndicator, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(dummyRight, LinearLayout.LayoutParams(btnSize, btnSize))
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pageIndicator.text = "${position + 1}/${mediaList.size}"
            }
        })

        val topBarHolder = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                topBarContent,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(topBarHolder) { v, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(0, statusTop, 0, 0)
            insets
        }

        root.addView(
            topBarHolder,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        dialog.setContentView(root)
        dialog.show()
    }

    private inner class PreviewPagerAdapter : RecyclerView.Adapter<PreviewViewHolder>() {
        override fun getItemCount(): Int = mediaList.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
            val frame = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.BLACK)
            }
            return PreviewViewHolder(frame)
        }

        override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
            val item = mediaList[position]
            holder.bind(item)
        }
    }

    private inner class PreviewViewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container) {
        fun bind(item: MediaItem) {
            container.removeAllViews()

            if (item.isVideo) {
                val videoView = VideoView(container.context).apply {
                    val mc = MediaController(container.context)
                    mc.setAnchorView(this)
                    setMediaController(mc)
                    if (item.file != null && item.file.exists()) {
                        setVideoPath(item.file.absolutePath)
                    } else if (item.uri != null) {
                        setVideoURI(item.uri)
                    }
                }
                val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
                container.addView(videoView, lp)
                videoView.start()
            } else {
                val imgView = ImageView(container.context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                container.addView(imgView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

                // Load Full Image Asynchronously
                threadPool.execute {
                    val bmp = try {
                        if (item.file != null && item.file.exists()) {
                            BitmapFactory.decodeFile(item.file.absolutePath)
                        } else if (item.uri != null) {
                            BitmapFactory.decodeStream(contentResolver.openInputStream(item.uri))
                        } else null
                    } catch (_: Throwable) {
                        null
                    }
                    if (bmp != null) {
                        mainHandler.post {
                            imgView.setImageBitmap(bmp)
                        }
                    }
                }
            }
        }
    }
}
