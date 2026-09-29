package com.example.gdl

import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.FileProvider
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
            Ui.NavItem("Geçmiş", R.drawable.ic_history),
            Ui.NavItem("Ayarlar", R.drawable.ic_tab_settings)
        )

        val navBar = Ui.floatingNavBar(this, navItems, selectedIndex = 1) { index ->
            when (index) {
                0 -> openMain()
                1 -> { /* Zaten Galeri ekranındayız */ }
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

    @Suppress("DEPRECATION")
    private fun openHistory() {
        val intent = Intent(this, HistoryActivity::class.java).apply {
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
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes = attributes.apply {
                    layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            }
            decorView.setBackgroundColor(Color.BLACK)
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

        // Top Bar Container: Clean header with Close button (X) on left, position indicator in middle, balanced empty view on right
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
            val statusTop = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
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

        // Bottom Floating Liquid Glass Capsule (Apple Photos Style)
        val shareIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_apple_share)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER
        }
        val shareCell = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            val r = Ui.dp(this@GalleryActivity, 22).toFloat()
            background = Ui.ripple(Ui.shape(Color.TRANSPARENT, r), r)
            addView(shareIcon, LinearLayout.LayoutParams(Ui.dp(this@GalleryActivity, 24), Ui.dp(this@GalleryActivity, 24)))
            setOnClickListener {
                val pos = viewPager.currentItem
                if (pos in mediaList.indices) {
                    shareMedia(mediaList[pos])
                }
            }
        }

        val trashIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_apple_trash)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER
        }
        val trashCell = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            val r = Ui.dp(this@GalleryActivity, 22).toFloat()
            background = Ui.ripple(Ui.shape(Color.TRANSPARENT, r), r)
            addView(trashIcon, LinearLayout.LayoutParams(Ui.dp(this@GalleryActivity, 24), Ui.dp(this@GalleryActivity, 24)))
            setOnClickListener {
                val pos = viewPager.currentItem
                if (pos in mediaList.indices) {
                    showDeleteConfirmationDialog(pos, dialog, viewPager, pageIndicator)
                }
            }
        }

        val bottomCapsule = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val pCapV = Ui.dp(this@GalleryActivity, 8)
            val pCapH = Ui.dp(this@GalleryActivity, 16)
            setPadding(pCapH, pCapV, pCapH, pCapV)

            val glassBg = Color.parseColor("#CC1A1A24")
            val strokeColor = Color.parseColor("#33FFFFFF")
            background = Ui.shape(glassBg, 999f, strokeColor, Ui.dp(this@GalleryActivity, 1))
            elevation = Ui.dp(this@GalleryActivity, 10).toFloat()

            val btnW = Ui.dp(this@GalleryActivity, 64)
            val btnH = Ui.dp(this@GalleryActivity, 44)
            addView(shareCell, LinearLayout.LayoutParams(btnW, btnH))
            addView(trashCell, LinearLayout.LayoutParams(btnW, btnH).apply {
                setMargins(Ui.dp(this@GalleryActivity, 8), 0, 0, 0)
            })
        }

        val capsuleHolder = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                bottomCapsule,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL
                )
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(capsuleHolder) { v, insets ->
            val navBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.tappableElement()
            ).bottom
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = Ui.dp(this@GalleryActivity, 20) + navBottom
            v.layoutParams = lp
            insets
        }

        root.addView(
            capsuleHolder,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        )

        dialog.setContentView(root)
        dialog.show()
    }

    private fun shareMedia(item: MediaItem) {
        try {
            val file = item.file
            val uri = if (file != null && file.exists()) {
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            } else {
                item.uri
            }
            if (uri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = if (item.isVideo) "video/*" else "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Fotoğrafı Paylaş"))
            } else {
                Toast.makeText(this, "Paylaşılabilecek dosya bulunamadı", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Paylaşım hatası: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmationDialog(
        position: Int,
        fullscreenDialog: Dialog,
        viewPager: ViewPager2,
        pageIndicator: TextView
    ) {
        if (position !in mediaList.indices) return

        AlertDialog.Builder(this)
            .setTitle("Fotoğrafı Sil")
            .setMessage("Bu fotoğrafı silmek istiyor musunuz?")
            .setPositiveButton("Sil") { _, _ ->
                performDeleteMedia(position, fullscreenDialog, viewPager, pageIndicator)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun performDeleteMedia(
        position: Int,
        fullscreenDialog: Dialog,
        viewPager: ViewPager2,
        pageIndicator: TextView
    ) {
        if (position !in mediaList.indices) return
        val item = mediaList[position]

        try {
            val file = item.file
            if (file != null && file.exists()) {
                file.delete()
                if (item.uri != null) {
                    try {
                        contentResolver.delete(item.uri, null, null)
                    } catch (_: Exception) {}
                }
                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(file.absolutePath),
                    null
                ) { _, _ -> }
            } else if (item.uri != null) {
                try {
                    contentResolver.delete(item.uri, null, null)
                } catch (_: Exception) {}
            }

            Toast.makeText(this, "Fotoğraf silindi", Toast.LENGTH_SHORT).show()

            // Remove from list and update adapter
            val mutableList = mediaList.toMutableList()
            if (position in mutableList.indices) {
                mutableList.removeAt(position)
            }
            mediaList = mutableList
            adapter.notifyDataSetChanged()

            if (mediaList.isEmpty()) {
                fullscreenDialog.dismiss()
            } else {
                viewPager.adapter = PreviewPagerAdapter()
                val newPos = if (position >= mediaList.size) mediaList.size - 1 else position
                viewPager.setCurrentItem(newPos, false)
                pageIndicator.text = "${newPos + 1}/${mediaList.size}"
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Silme hatası: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
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
                val imgView = ZoomableImageView(container.context)
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

    private class ZoomableImageView(context: Context) : AppCompatImageView(context) {
        private val matrix = Matrix()
        private val savedMatrix = Matrix()
        private var mode = NONE

        private val point = PointF()
        private var saveScale = 1f
        private var minScale = 1f
        private var maxScale = 4f
        private var computedFitScale = 1f

        private var origWidth = 0f
        private var origHeight = 0f
        private var viewWidth = 0f
        private var viewHeight = 0f

        private val scaleDetector: ScaleGestureDetector
        private val gestureDetector: GestureDetector

        companion object {
            private const val NONE = 0
            private const val DRAG = 1
            private const val ZOOM = 2
        }

        init {
            scaleType = ScaleType.MATRIX
            scaleDetector = ScaleGestureDetector(context, ScaleListener())
            gestureDetector = GestureDetector(context, GestureListener())
        }

        private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                mode = ZOOM
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                var mScaleFactor = detector.scaleFactor
                val origScale = saveScale
                saveScale *= mScaleFactor
                if (saveScale > maxScale) {
                    saveScale = maxScale
                    mScaleFactor = maxScale / origScale
                } else if (saveScale < minScale) {
                    saveScale = minScale
                    mScaleFactor = minScale / origScale
                }

                if (origWidth * saveScale <= viewWidth || origHeight * saveScale <= viewHeight) {
                    matrix.postScale(mScaleFactor, mScaleFactor, viewWidth / 2f, viewHeight / 2f)
                } else {
                    matrix.postScale(mScaleFactor, mScaleFactor, detector.focusX, detector.focusY)
                }
                fixTranslation()
                imageMatrix = matrix
                return true
            }
        }

        private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val targetScale = if (saveScale > minScale + 0.1f) minScale else maxScale.coerceAtMost(2.5f)
                val mScaleFactor = targetScale / saveScale
                saveScale = targetScale
                matrix.postScale(mScaleFactor, mScaleFactor, e.x, e.y)
                fixTranslation()
                imageMatrix = matrix
                return true
            }
        }

        override fun setImageBitmap(bm: Bitmap?) {
            super.setImageBitmap(bm)
            if (bm != null) {
                origWidth = bm.width.toFloat()
                origHeight = bm.height.toFloat()
                fitImageToView()
            }
        }

        override fun setImageDrawable(drawable: Drawable?) {
            super.setImageDrawable(drawable)
            if (drawable != null) {
                origWidth = drawable.intrinsicWidth.toFloat()
                origHeight = drawable.intrinsicHeight.toFloat()
                if (origWidth <= 0) origWidth = 100f
                if (origHeight <= 0) origHeight = 100f
                fitImageToView()
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            viewWidth = w.toFloat()
            viewHeight = h.toFloat()
            fitImageToView()
        }

        private fun fitImageToView() {
            if (viewWidth <= 0 || viewHeight <= 0 || origWidth <= 0 || origHeight <= 0) return
            val scaleX = viewWidth / origWidth
            val scaleY = viewHeight / origHeight
            computedFitScale = scaleX.coerceAtMost(scaleY)
            minScale = computedFitScale
            saveScale = minScale

            matrix.reset()
            matrix.postScale(computedFitScale, computedFitScale)
            val redundantX = (viewWidth - (origWidth * computedFitScale)) / 2f
            val redundantY = (viewHeight - (origHeight * computedFitScale)) / 2f
            matrix.postTranslate(redundantX, redundantY)
            imageMatrix = matrix
        }

        private fun fixTranslation() {
            matrix.getValues(m)
            val transX = m[Matrix.MTRANS_X]
            val transY = m[Matrix.MTRANS_Y]

            val fixX = getFitTranslation(transX, viewWidth, origWidth * saveScale)
            val fixY = getFitTranslation(transY, viewHeight, origHeight * saveScale)

            if (fixX != 0f || fixY != 0f) {
                matrix.postTranslate(fixX, fixY)
            }
        }

        private fun getFitTranslation(trans: Float, viewSize: Float, contentSize: Float): Float {
            val minTrans: Float
            val maxTrans: Float

            if (contentSize <= viewSize) {
                minTrans = 0f
                maxTrans = viewSize - contentSize
            } else {
                minTrans = viewSize - contentSize
                maxTrans = 0f
            }

            if (trans < minTrans) {
                return minTrans - trans
            }
            if (trans > maxTrans) {
                return maxTrans - trans
            }
            return 0f
        }

        private val m = FloatArray(9)

        override fun onTouchEvent(event: MotionEvent): Boolean {
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            val curr = PointF(event.x, event.y)

            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    performClick()
                    savedMatrix.set(matrix)
                    point.set(curr)
                    mode = DRAG
                    if (saveScale > minScale + 0.05f) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    mode = NONE
                    if (saveScale <= minScale + 0.05f) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                        fitImageToView()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mode == DRAG) {
                        val deltaX = curr.x - point.x
                        val deltaY = curr.y - point.y

                        matrix.set(savedMatrix)
                        matrix.postTranslate(deltaX, deltaY)
                        fixTranslation()
                        imageMatrix = matrix

                        if (saveScale > minScale + 0.05f) {
                            parent?.requestDisallowInterceptTouchEvent(true)
                        } else {
                            parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }
            }
            return true
        }
    }
}
