package com.example.gdl

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

class ProgressSink(private val f: (Int, String) -> Unit, private val checkCancelled: () -> Boolean = { false }) {
    @Suppress("unused")
    fun updateProgress(p: Int, detail: String) {
        if (checkCancelled()) throw RuntimeException("İptal edildi")
        f(p, detail)
    }
    @Suppress("unused")
    fun isCancelled(): Boolean = checkCancelled()
}

class DownloadService : Service() {
    data class DownloadState(val id: Long, val platform: String, val url: String, var pct: Int, var detail: String, val startTime: Long)

    companion object {
        const val EXTRA_URL = "url"
        const val CH_PROGRESS = "progress"
        const val CH_RESULT = "result"

        const val STATUS_QUEUED = "Sırada"
        const val STATUS_DOWNLOADING = "İndiriliyor"
        const val STATUS_INTERRUPTED = "Yarıda kaldı"

        val activeDownloads = ConcurrentHashMap<Long, DownloadState>()
        val futures = ConcurrentHashMap<Long, Future<*>>()
        val cancelFlags = ConcurrentHashMap<Long, Boolean>()
        var onStateChanged: (() -> Unit)? = null

        private const val TAG = "DownloadService"

        fun normalizeUrl(rawUrl: String): String {
            var u = rawUrl.trim()
            try {
                val uri = u.toUri()
                val query = uri.query
                if (!query.isNullOrEmpty()) {
                    val params = query.split("&")
                    val filteredParams = params.filter { !it.startsWith("utm_", ignoreCase = true) }
                    val newQuery = if (filteredParams.isNotEmpty()) filteredParams.joinToString("&") else ""
                    val builder = uri.buildUpon().query(newQuery)
                    u = builder.build().toString()
                }
            } catch (_: Exception) {}
            if (u.endsWith("/")) {
                val uri = u.toUri()
                val path = uri.path ?: ""
                if (path.length > 1) {
                    u = u.removeSuffix("/")
                }
            }
            return u
        }

        fun cancelDownload(id: Long) {
            val future = futures[id]
            if (future != null) {
                val cancelled = future.cancel(false)
                if (cancelled) {
                    activeDownloads.remove(id)
                    futures.remove(id)
                    cancelFlags.remove(id)
                    onStateChanged?.invoke()
                } else {
                    cancelFlags[id] = true
                }
            } else {
                activeDownloads.remove(id)
                onStateChanged?.invoke()
            }
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val pending = AtomicInteger(0)
    private lateinit var nm: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "İndirme durumu", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, "İndirme sonucu", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun getPlatformName(url: String): String = Store.getPlatformName(url)

    private fun progressNotif(platform: String, text: String, pct: Int): Notification {
        val b = NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(R.drawable.stat_sys_download)
            .setContentTitle("İndiriliyor...")
            .setContentText(if (text.isNotEmpty()) "$platform • $text" else platform)
            .setOngoing(true)
        if (pct >= 0) b.setProgress(100, pct, false) else b.setProgress(0, 0, true)
        return b.build()
    }

    private fun stopIfIdle() {
        if (pending.get() == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rawUrl = intent?.getStringExtra(EXTRA_URL)
        val customQuality = intent?.getStringExtra("custom_quality") ?: ""
        if (rawUrl == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        val url = normalizeUrl(rawUrl)
        val platform = getPlatformName(url)

        val existing = activeDownloads.values.find { normalizeUrl(it.url) == url }
        if (existing != null) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "Bu indirme zaten devam ediyor", Toast.LENGTH_SHORT).show()
            }
            stopIfIdle()
            return START_NOT_STICKY
        }

        startForeground(
            1,
            progressNotif(platform, "Hazırlanıyor…", -1),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        pending.incrementAndGet()
        val id = System.currentTimeMillis()
        val state = DownloadState(id, platform, url, -1, "Sırada", id)
        activeDownloads[id] = state
        onStateChanged?.invoke()

        val future = executor.submit {
            try {
                if (cancelFlags[id] == true) {
                    return@submit
                }
                state.detail = "Hazırlanıyor..."
                onStateChanged?.invoke()
                process(id, url, customQuality, platform, state)
            } finally {
                futures.remove(id)
                cancelFlags.remove(id)
                activeDownloads.remove(id)
                onStateChanged?.invoke()
                if (pending.decrementAndGet() == 0) stopIfIdle()
            }
        }
        futures[id] = future
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeDownloads.isNotEmpty()) {
            activeDownloads.values.forEach { st ->
                Store.upsert(this, st.id, st.url, STATUS_INTERRUPTED, "")
            }
            activeDownloads.clear()
            futures.clear()
            cancelFlags.clear()
            onStateChanged?.invoke()
        }
    }

    private fun process(id: Long, url: String, customQuality: String, platform: String, state: DownloadState) {
        val dir = File(cacheDir, "dl_$id").apply { mkdirs() }
        val cookieTmp = File(cacheDir, "cookies_job.txt")
        var log = ""
        var saved = 0
        var savedImages = 0
        var savedVideos = 0
        val savedFiles = mutableListOf<File>()
        var thumbnailBitmap: Bitmap? = null
        var success = false
        var isCancelled = false

        try {
            if (cancelFlags[id] == true) {
                throw Exception("İptal edildi")
            }
            if (!Python.isStarted()) Python.start(AndroidPlatform(this))
            var cookiePath = ""
            val cookies = File(filesDir, "cookies.txt")
            if (cookies.exists() && cookies.length() > 0) {
                cookies.copyTo(cookieTmp, overwrite = true)
                cookiePath = cookieTmp.absolutePath
            }
            val sink = ProgressSink({ p, detail ->
                if (cancelFlags[id] == true) {
                    throw Exception("İptal edildi")
                }
                activeDownloads[id]?.let { st ->
                    st.pct = p
                    st.detail = detail
                }
                onStateChanged?.invoke()
                nm.notify(1, progressNotif(platform, detail, p))
            }, { cancelFlags[id] == true })

            val updatesDir = File(filesDir, "python_updates")
            val updatesPath = if (updatesDir.exists()) updatesDir.absolutePath else ""
            val quality = customQuality.takeIf { it.isNotEmpty() } ?: Store.videoQuality(this)
            log = Python.getInstance().getModule("dl")
                .callAttr("run", url, dir.absolutePath, cookiePath, Store.template(this), sink, updatesPath, quality)
                .toString()

            if (cancelFlags[id] == true) {
                throw Exception("İptal edildi")
            }

            for (f in dir.walkTopDown().filter { it.isFile }) {
                if (cancelFlags[id] == true) {
                    throw Exception("İptal edildi")
                }
                val mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(f.extension.lowercase())
                if (mime != null) {
                    val video = mime.startsWith("video")
                    val image = mime.startsWith("image")
                    if (video || image) {
                        if (save(f)) {
                            savedFiles.add(f)
                            if (video) savedVideos++
                            if (image) savedImages++
                        }
                    }
                }
            }
            saved = savedFiles.size
            if (saved > 0) {
                success = true
                val thumbFile = savedFiles.first()
                Log.d(TAG, "Loading thumbnail from file: ${thumbFile.absolutePath}, exists=${thumbFile.exists()}, length=${thumbFile.length()}")
                thumbnailBitmap = loadThumbnail(thumbFile)
            }
        } catch (e: Exception) {
            if (e.message?.contains("İptal edildi") == true || cancelFlags[id] == true) {
                isCancelled = true
                log = "Kullanıcı tarafından iptal edildi"
            } else {
                log = "Hata: ${e.message}"
                Log.e(TAG, "Process error: ${e.message}", e)
            }
        } finally {
            dir.deleteRecursively()
            cookieTmp.delete()
        }

        if (isCancelled) {
            Store.upsert(this, id, url, STATUS_INTERRUPTED, "Kullanıcı tarafından iptal edildi")
            nm.cancel(1)
            onStateChanged?.invoke()
            return
        }

        if (success) {
            val status = if (saved == 1) "1 dosya" else "$saved dosya"
            Store.upsert(this, id, url, status, log.takeLast(3000))
        } else {
            Store.upsert(this, id, url, STATUS_INTERRUPTED, log.takeLast(3000))
        }
        onStateChanged?.invoke()
        nm.cancel(1)

        val ok = success
        val detailText = when {
            (savedImages > 0) && (savedVideos == 0) -> if (savedImages == 1) "1 fotoğraf galeriye kaydedildi" else "$savedImages fotoğraf galeriye kaydedildi"
            (savedImages == 0) && (savedVideos > 0) -> if (savedVideos == 1) "1 video galeriye kaydedildi" else "$savedVideos video galeriye kaydedildi"
            else -> if (saved == 1) "1 dosya galeriye kaydedildi" else "$saved dosya galeriye kaydedildi"
        }
        val contentText = if (ok) "$platform • $detailText" else "$platform • İndirilemedi"

        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CH_RESULT)
            .setSmallIcon(
                if (ok) R.drawable.stat_sys_download_done
                else R.drawable.stat_notify_error
            )
            .setContentTitle(if (ok) "İndirme Tamamlandı" else "İndirme Başarısız")
            .setContentText(contentText)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (ok && thumbnailBitmap != null) {
            builder.setLargeIcon(thumbnailBitmap)
            val bigPicStyle = NotificationCompat.BigPictureStyle()
                .bigPicture(thumbnailBitmap)
                .bigLargeIcon(null as Bitmap?)
                .setSummaryText(contentText)
            builder.setStyle(bigPicStyle)
            Log.d(TAG, "BigPictureStyle successfully applied to notification.")
        } else {
            Log.w(TAG, "Thumbnail bitmap is null or download failed, skipping BigPictureStyle.")
        }

        nm.notify((id % 100000).toInt() + 10, builder.build())
    }

    private fun loadThumbnail(f: File): Bitmap? {
        if (!f.exists() || f.length() == 0L) {
            Log.w(TAG, "Thumbnail file does not exist or is empty: ${f.absolutePath}")
            return null
        }
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(f.extension.lowercase()) ?: ""
        return try {
            val bmp = if (mime.startsWith("image")) {
                decodeAndScaleBitmap(f.absolutePath)
            } else if (mime.startsWith("video")) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(f.absolutePath)
                    val rawBmp = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                    rawBmp?.let { scaleBitmap(it, 1080, 720) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting video thumbnail: ${e.message}", e)
                    null
                } finally {
                    retriever.release()
                }
            } else {
                null
            }
            if (bmp == null) {
                Log.w(TAG, "Bitmap decoded as null for file: ${f.absolutePath}, mime: $mime")
            } else {
                Log.d(TAG, "Bitmap successfully loaded and scaled: ${bmp.width}x${bmp.height}")
            }
            bmp
        } catch (e: Exception) {
            Log.e(TAG, "Error loading thumbnail: ${e.message}", e)
            null
        }
    }

    private fun decodeAndScaleBitmap(filePath: String): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(filePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        val maxWidth = 1080
        val maxHeight = 720
        var inSampleSize = 1
        val halfWidth = options.outWidth / 2
        val halfHeight = options.outHeight / 2
        while ((halfWidth / inSampleSize) >= maxWidth && (halfHeight / inSampleSize) >= maxHeight) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        val decoded = BitmapFactory.decodeFile(filePath, decodeOptions) ?: return null
        return scaleBitmap(decoded, maxWidth, maxHeight)
    }

    private fun scaleBitmap(bmp: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bmp.width
        val height = bmp.height
        if (width <= maxWidth && height <= maxHeight) return bmp

        val ratio = width.toFloat() / height.toFloat()
        var targetWidth = maxWidth
        var targetHeight = maxHeight
        if (ratio > 1) {
            targetHeight = (maxWidth / ratio).toInt()
        } else {
            targetWidth = (maxHeight * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bmp, targetWidth, targetHeight, true)
    }

    private fun save(f: File): Boolean {
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(f.extension.lowercase()) ?: return false
        val video = mime.startsWith("video")
        if (!video && !mime.startsWith("image")) return false
        val album = Store.album(this)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (video) "Movies/$album" else "Pictures/$album"
            )
        }
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = contentResolver.insert(collection, values) ?: return false
        contentResolver.openOutputStream(uri)?.use { o ->
            f.inputStream().use { it.copyTo(o) }
        }
        return true
    }
}
