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
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ProgressSink(private val f: (Int) -> Unit) {
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
    }

    private data class SavedMedia(
        val uri: Uri,
        val mimeType: String,
        val isVideo: Boolean,
        val file: File? = null
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val pending = AtomicInteger(0)
    private lateinit var nm: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "İndirme durumu", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Devam eden indirme işlemlerinin durumu"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, "İndirme sonucu", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tamamlanan indirme bildirimleri"
            }
        )
    }

    private fun getPlatformName(url: String): String {
        val lower = url.lowercase()
        return when {
            "instagram.com" in lower -> "Instagram"
            "tiktok.com" in lower -> "TikTok"
            "youtube.com" in lower || "youtu.be" in lower -> "YouTube"
            "twitter.com" in lower || "x.com" in lower -> "X"
            "facebook.com" in lower || "fb.watch" in lower -> "Facebook"
            "pinterest.com" in lower || "pin.it" in lower -> "Pinterest"
            "reddit.com" in lower -> "Reddit"
            else -> {
                try {
                    val host = Uri.parse(url).host?.removePrefix("www.") ?: url
                    val name = host.split(".").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Web"
                    if (name.length <= 1) "Web" else name
                } catch (e: Exception) {
                    "Web"
                }
            }
        }
    }

    private fun progressNotif(url: String?, text: String, pct: Int?): Notification {
        val platform = if (url != null) getPlatformName(url) else null
        val contentText = if (platform != null) {
            if (pct != null && pct > 0) "$platform • %$pct" else "$platform • $text"
        } else {
            text
        }

        val b = NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(R.drawable.stat_sys_download)
            .setContentTitle("İndiriliyor...")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (pct != null && pct > 0) {
            b.setProgress(100, pct, false)
        } else {
            b.setProgress(0, 0, true)
        }
        return b.build()
    }

    private fun stopIfIdle() {
        if (pending.get() == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_DELETE") {
            val uris = intent.getParcelableArrayListExtra<Uri>("uris")
            val paths = intent.getStringArrayListExtra("paths")
            val notifId = intent.getIntExtra("notif_id", -1)
            if (notifId != -1) {
                nm.cancel(notifId)
            }
            executor.execute {
                if (uris != null) {
                    for (uri in uris) {
                        try {
                            contentResolver.delete(uri, null, null)
                        } catch (_: Exception) {}
                    }
                }
                if (paths != null) {
                    for (path in paths) {
                        try {
                            val file = File(path)
                            if (file.exists()) file.delete()
                            MediaScannerConnection.scanFile(this, arrayOf(path), null, null)
                        } catch (_: Exception) {}
                    }
                }
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@DownloadService, "Dosya silindi", Toast.LENGTH_SHORT).show()
                }
            }
            stopIfIdle()
            return START_NOT_STICKY
        }

        startForeground(
            1,
            progressNotif(null, "Hazırlanıyor…", null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        val url = intent?.getStringExtra(EXTRA_URL)
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
        Store.upsert(this, id, url, "İndiriliyor", "")
        onChange?.invoke()
        nm.notify(1, progressNotif(url, "Bağlanılıyor…", null))

        val dir = File(cacheDir, "dl_$id").apply { mkdirs() }
        val cookieTmp = File(cacheDir, "cookies_job.txt")
        var log = ""
        val savedMedias = mutableListOf<SavedMedia>()
        var savedImages = 0
        var savedVideos = 0
        var firstPreviewBitmap: Bitmap? = null

        try {
            if (!Python.isStarted()) Python.start(AndroidPlatform(this))
            var cookiePath = ""
            val cookies = File(filesDir, "cookies.txt")
            if (cookies.exists() && cookies.length() > 0) {
                cookies.copyTo(cookieTmp, overwrite = true)
                cookiePath = cookieTmp.absolutePath
            }
            val sink = ProgressSink { p ->
                nm.notify(1, progressNotif(url, "%$p", p))
            }
            val updatesDir = File(filesDir, "python_updates")
            val updatesPath = if (updatesDir.exists()) updatesDir.absolutePath else ""
            log = Python.getInstance().getModule("dl")
                .callAttr("run", url, dir.absolutePath, cookiePath, Store.template(this), sink, updatesPath)
                .toString()

            val downloadedFiles = dir.walkTopDown().filter { it.isFile }.toList()
            for (f in downloadedFiles) {
                val media = save(f)
                if (media != null) {
                    savedMedias.add(media)
                    if (media.isVideo) savedVideos++ else savedImages++
                    if (firstPreviewBitmap == null) {
                        firstPreviewBitmap = getPreviewBitmap(f)
                    }
                }
            }
        } catch (e: Exception) {
            log = "Hata: ${e.message}"
            Log.e("DownloadService", "İndirme istisnası: ${e.message}", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this@DownloadService, "İndirme hatası: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            dir.deleteRecursively()
            cookieTmp.delete()
        }

        val totalSaved = savedMedias.size
        val ok = totalSaved > 0
        val status = if (ok) "✓ $totalSaved dosya kaydedildi" else "✗ İndirilemedi"
        Store.upsert(this, id, url, status, log.takeLast(3000))
        onChange?.invoke()

        if (!ok) {
            val errorMsg = log.ifEmpty { "Dosya indirilemedi veya bağlantı hatası." }
            Log.e("DownloadService", "İndirme başarısız: $errorMsg")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this@DownloadService, "İndirme başarısız: ${errorMsg.take(150)}", Toast.LENGTH_LONG).show()
            }
        }

        val platform = getPlatformName(url)
        val titleText = if (ok) "İndirme Tamamlandı" else "İndirme Başarısız"

        val contentText = if (ok) {
            val mediaDetail = when {
                savedImages > 0 && savedVideos == 0 -> {
                    if (savedImages == 1) "1 fotoğraf galeriye kaydedildi"
                    else "$savedImages fotoğraf galeriye kaydedildi"
                }
                savedVideos > 0 && savedImages == 0 -> {
                    if (savedVideos == 1) "1 video galeriye kaydedildi"
                    else "$savedVideos video galeriye kaydedildi"
                }
                else -> {
                    if (totalSaved == 1) "1 dosya galeriye kaydedildi"
                    else "$totalSaved dosya galeriye kaydedildi"
                }
            }
            "$platform • $mediaDetail"
        } else {
            "$platform • İndirme gerçekleştirilemedi"
        }

        val galleryIntent = Intent(this, GalleryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val galleryPendingIntent = PendingIntent.getActivity(
            this,
            (id % 100000).toInt() + 100,
            galleryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifBuilder = NotificationCompat.Builder(this, CH_RESULT)
            .setSmallIcon(
                if (ok) R.drawable.stat_sys_download_done
                else R.drawable.stat_notify_error
            )
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setContentIntent(galleryPendingIntent)
            .setAutoCancel(true)

        if (ok) {
            if (firstPreviewBitmap != null) {
                notifBuilder.setLargeIcon(firstPreviewBitmap)
                notifBuilder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(firstPreviewBitmap)
                        .bigLargeIcon(null as Bitmap?)
                )
            }

            // Quick Action 1: Görüntüle
            notifBuilder.addAction(
                R.drawable.ic_menu_gallery,
                "Görüntüle",
                galleryPendingIntent
            )

            // Quick Action 2: Paylaş
            val shareUris = ArrayList(savedMedias.map { it.uri })
            if (shareUris.isNotEmpty()) {
                val shareIntent = if (shareUris.size == 1) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = savedMedias.first().mimeType
                        putExtra(Intent.EXTRA_STREAM, shareUris[0])
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                val chooserIntent = Intent.createChooser(shareIntent, "Medya Paylaş").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val sharePendingIntent = PendingIntent.getActivity(
                    this,
                    (id % 100000).toInt() + 200,
                    chooserIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                notifBuilder.addAction(
                    R.drawable.ic_menu_share,
                    "Paylaş",
                    sharePendingIntent
                )
            }

            // Quick Action 3: Sil
            val deleteIntent = Intent(this, DownloadService::class.java).apply {
                action = "ACTION_DELETE"
                putParcelableArrayListExtra("uris", ArrayList(savedMedias.map { it.uri }))
                putStringArrayListExtra("paths", ArrayList(savedMedias.mapNotNull { it.file?.absolutePath }))
                putExtra("notif_id", (id % 100000).toInt() + 10)
            }
            val deletePendingIntent = PendingIntent.getService(
                this,
                (id % 100000).toInt() + 300,
                deleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notifBuilder.addAction(
                R.drawable.ic_menu_delete,
                "Sil",
                deletePendingIntent
            )
        }

        nm.notify((id % 100000).toInt() + 10, notifBuilder.build())
    }

    private fun getPreviewBitmap(file: File): Bitmap? {
        return try {
            val mime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()) ?: ""
            if (mime.startsWith("video")) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val frame = retriever.frameAtTime
                retriever.release()
                frame
            } else if (mime.startsWith("image")) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
                var sampleSize = 1
                val maxDim = 1024
                while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun save(f: File): SavedMedia? {
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(f.extension.lowercase()) ?: return null
        val video = mime.startsWith("video")
        if (!video && !mime.startsWith("image")) return null
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
        val uri = contentResolver.insert(collection, values) ?: return null
        contentResolver.openOutputStream(uri)?.use { o ->
            f.inputStream().use { it.copyTo(o) }
        }
        return SavedMedia(uri, mime, video, f)
    }
}
