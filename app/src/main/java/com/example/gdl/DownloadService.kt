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
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ProgressSink(private val f: (Int) -> Unit) {
    @Suppress("unused")
    fun update(p: Int) {
        f(p)
    }
}

class DownloadService : Service() {
    companion object {
        const val EXTRA_URL = "url"
        const val CH_PROGRESS = "progress"
        const val CH_RESULT = "result"
        var onChange: (() -> Unit)? = null
        private const val TAG = "DownloadService"
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

    private fun progressNotif(platform: String, text: String, pct: Int?): Notification {
        val b = NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(R.drawable.stat_sys_download)
            .setContentTitle("İndiriliyor...")
            .setContentText(if (text.isNotEmpty()) "$platform • $text" else platform)
            .setOngoing(true)
        if (pct != null) b.setProgress(100, pct, false) else b.setProgress(0, 0, true)
        return b.build()
    }

    private fun stopIfIdle() {
        if (pending.get() == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        val platform = url?.let { getPlatformName(it) } ?: "İndiriliyor"
        startForeground(
            1,
            progressNotif(platform, "Hazırlanıyor…", null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        if (url == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        pending.incrementAndGet()
        val id = System.currentTimeMillis()
        Store.upsert(this, id, url, "Sırada", "")
        onChange?.invoke()
        executor.execute {
            try {
                process(id, url)
            } finally {
                if (pending.decrementAndGet() == 0) stopIfIdle()
            }
        }
        return START_NOT_STICKY
    }

    private fun process(id: Long, url: String) {
        val platform = getPlatformName(url)
        Store.upsert(this, id, url, "İndiriliyor", "")
        onChange?.invoke()
        nm.notify(1, progressNotif(platform, "İndiriliyor...", null))

        val dir = File(cacheDir, "dl_$id").apply { mkdirs() }
        val cookieTmp = File(cacheDir, "cookies_job.txt")
        var log: String
        var saved = 0
        var savedImages = 0
        var savedVideos = 0
        val savedFiles = mutableListOf<File>()
        var thumbnailBitmap: Bitmap? = null

        try {
            if (!Python.isStarted()) Python.start(AndroidPlatform(this))
            var cookiePath = ""
            val cookies = File(filesDir, "cookies.txt")
            if (cookies.exists() && cookies.length() > 0) {
                cookies.copyTo(cookieTmp, overwrite = true)
                cookiePath = cookieTmp.absolutePath
            }
            val sink = ProgressSink { p ->
                nm.notify(1, progressNotif(platform, "$p%", p))
            }
            val updatesDir = File(filesDir, "python_updates")
            val updatesPath = if (updatesDir.exists()) updatesDir.absolutePath else ""
            log = Python.getInstance().getModule("dl")
                .callAttr("run", url, dir.absolutePath, cookiePath, Store.template(this), sink, updatesPath)
                .toString()

            for (f in dir.walkTopDown().filter { it.isFile }) {
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
                val thumbFile = savedFiles.first()
                Log.d(TAG, "Loading thumbnail from file: ${thumbFile.absolutePath}, exists=${thumbFile.exists()}, length=${thumbFile.length()}")
                thumbnailBitmap = loadThumbnail(thumbFile)
            }
        } catch (e: Exception) {
            log = "Hata: ${e.message}"
            Log.e(TAG, "Process error: ${e.message}", e)
        } finally {
            dir.deleteRecursively()
            cookieTmp.delete()
        }

        val ok = saved > 0
        val status = if (ok) "✓ $saved dosya kaydedildi" else "✗ İndirilemedi"
        Store.upsert(this, id, url, status, log.takeLast(3000))
        onChange?.invoke()

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
