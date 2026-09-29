package com.example.gdl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.provider.MediaStore
import android.webkit.MimeTypeMap
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

    private fun progressNotif(text: String, pct: Int?): Notification {
        val b = Notification.Builder(this, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("İNDİR")
            .setContentText(text)
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
        startForeground(
            1,
            progressNotif("Hazırlanıyor…", null),
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
        val host = try {
            Uri.parse(url).host ?: url
        } catch (e: Exception) {
            url
        }
        Store.upsert(this, id, url, "İndiriliyor", "")
        onChange?.invoke()
        nm.notify(1, progressNotif("İndiriliyor: $host", null))

        val dir = File(cacheDir, "dl_$id").apply { mkdirs() }
        val cookieTmp = File(cacheDir, "cookies_job.txt")
        var log = ""
        var saved = 0
        try {
            if (!Python.isStarted()) Python.start(AndroidPlatform(this))
            var cookiePath = ""
            val cookies = File(filesDir, "cookies.txt")
            if (cookies.exists() && cookies.length() > 0) {
                cookies.copyTo(cookieTmp, overwrite = true)
                cookiePath = cookieTmp.absolutePath
            }
            val sink = ProgressSink { p ->
                nm.notify(1, progressNotif("İndiriliyor: $host", p))
            }
            log = Python.getInstance().getModule("dl")
                .callAttr("run", url, dir.absolutePath, cookiePath, Store.template(this), sink)
                .toString()
            saved = dir.walkTopDown().filter { it.isFile }.count { save(it) }
        } catch (e: Exception) {
            log = "Hata: ${e.message}"
        } finally {
            dir.deleteRecursively()
            cookieTmp.delete()
        }

        val ok = saved > 0
        val status = if (ok) "✓ $saved dosya kaydedildi" else "✗ İndirilemedi"
        Store.upsert(this, id, url, status, log.takeLast(3000))
        onChange?.invoke()

        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CH_RESULT)
            .setSmallIcon(
                if (ok) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setContentTitle(if (ok) "İndirme tamamlandı" else "İndirme başarısız")
            .setContentText("$host — $status")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify((id % 100000).toInt() + 10, n)
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
