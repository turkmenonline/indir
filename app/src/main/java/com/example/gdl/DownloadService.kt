package com.example.gdl

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
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
import java.net.URLDecoder
import java.util.Collections
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
        const val EXTRA_ID = "download_id"
        const val ACTION_CANCEL = "com.example.gdl.action.CANCEL_DOWNLOAD"
        const val CH_PROGRESS = "progress"
        const val CH_RESULT = "result"
        // Android 16 canlı bildirim için ayrı kanal: bazı üreticiler (Samsung One UI) düşük
        // önemdeki kanalları Now Bar / hap olarak göstermiyor. Kanal önemi sonradan değişmediği
        // için yeni bir kanal kimliği kullanıyoruz.
        const val CH_LIVE = "progress_live"

        fun ensureLiveChannel(nm: NotificationManager) {
            val ch = NotificationChannel(CH_LIVE, "İndirme durumu (canlı)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Devam eden indirmeler için canlı bildirim"
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(ch)
        }

        /**
         * Samsung One UI'ın kendi "ongoing activity" (Live Notification / Now Bar) alanları.
         * Yalnızca Samsung cihazlarda eklenir; diğer cihazlarda hiçbir etkisi yoktur.
         * Samsung bu özelliği paket adına bağlı bir izin listesiyle kısıtlıyor.
         */
        fun samsungExtras(primary: String, secondary: String, progress: Int): Bundle = Bundle().apply {
            putInt("android.ongoingActivityNoti.style", 1)
            // Durum çubuğundaki hapın arka planı: verilmezse açık renk zeminde silik görünüyor.
            putInt("android.ongoingActivityNoti.chipBgColor", 0xFF1A73E8.toInt())
            putString("android.ongoingActivityNoti.primaryInfo", primary)
            putString("android.ongoingActivityNoti.secondaryInfo", secondary)
            putString("android.ongoingActivityNoti.nowbarPrimaryInfo", primary)
            putString("android.ongoingActivityNoti.nowbarSecondaryInfo", secondary)
            if (progress in 0..100) {
                putInt("android.ongoingActivityNoti.progress", progress)
                putInt("android.ongoingActivityNoti.progressMax", 100)
            }
            // Bildirimdeki eylem düğmeleri (İptal) Samsung'un kendi canlı bildirim düzeninde
            // gösterilsin diye. Değerler bir geliştirici makalesinden, doğrulanmadı.
            putInt("android.ongoingActivityNoti.actionType", 1)
            putInt("android.ongoingActivityNoti.actionPrimarySet", 0)
        }

        fun isSamsung(): Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

        const val STATUS_QUEUED = "Sırada"
        const val STATUS_DOWNLOADING = "İndiriliyor"
        const val STATUS_INTERRUPTED = "Yarıda kaldı"

        val activeDownloads = ConcurrentHashMap<Long, DownloadState>()
        val futures = ConcurrentHashMap<Long, Future<*>>()
        val cancelledTasks = ConcurrentHashMap.newKeySet<Long>()
        val runningTasks = ConcurrentHashMap.newKeySet<Long>()
        var onStateChanged: (() -> Unit)? = null
        
        private val stateListeners = Collections.newSetFromMap(ConcurrentHashMap<() -> Unit, Boolean>())
        
        fun addStateListener(listener: () -> Unit) { stateListeners.add(listener) }
        fun removeStateListener(listener: () -> Unit) { stateListeners.remove(listener) }
        
        fun notifyGlobalStateChanged() {
            onStateChanged?.invoke()
            stateListeners.forEach { listener ->
                try { listener.invoke() } catch (_: Throwable) {}
            }
        }

        private const val TAG = "DownloadService"

        fun normalizeUrl(rawUrl: String): String {
            var u = rawUrl.trim()
            try {
                val uri = u.toUri()
                val encodedQuery = uri.encodedQuery
                if (!encodedQuery.isNullOrEmpty()) {
                    var queryText = encodedQuery
                    if (!queryText.contains("=") && queryText.contains("%3D", ignoreCase = true)) {
                        try {
                            queryText = URLDecoder.decode(queryText, "UTF-8")
                        } catch (_: Exception) {}
                    }
                    val params = queryText.split("&")
                    val filteredParams = params.filter { !it.startsWith("utm_", ignoreCase = true) }
                    val builder = uri.buildUpon().clearQuery()
                    if (filteredParams.isNotEmpty()) {
                        val newQuery = filteredParams.joinToString("&")
                        builder.encodedQuery(newQuery)
                    }
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
            if (!activeDownloads.containsKey(id)) return
            cancelledTasks.add(id)
            if (runningTasks.contains(id)) {
                // ÇALIŞIYOR: Python'a iptal gönder. Kart, process() bitince finally'de kalkar.
                try {
                    if (Python.isStarted()) {
                        val r = Python.getInstance().getModule("dl")
                            .callAttr("cancel_task", id.toString())
                        Log.d(TAG, "CANCEL running id=$id -> $r")
                    } else {
                        Log.w(TAG, "CANCEL id=$id Python henüz başlamadı")
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "CANCEL cancel_task hatası id=$id: ${e}", e)
                }
            } else {
                // SIRADA: hiç başlamadı. Kartı hemen kaldır; sıra gelince executor işi
                // cancelledTasks'ı görüp anında çıkar (Future.cancel KULLANMA: çalışan
                // işte de true döner ve indirmeyi durdurmaz, sadece kartı siler).
                Log.d(TAG, "CANCEL queued id=$id")
                activeDownloads.remove(id)
                notifyGlobalStateChanged()
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
        ensureLiveChannel(nm)
        // Önceki denemeden kalan, artık kullanılmayan kanalı telefonun bildirim ayarlarından kaldır.
        nm.deleteNotificationChannel("fgs_quiet")
    }

    private fun getPlatformName(url: String): String = Store.getPlatformName(url)

    /** Android 16 ve üstünde canlı bildirim (Now Bar / hap) kullanılır. */
    private fun liveActive(): Boolean = Build.VERSION.SDK_INT >= 36

    /** İlerleme bildirimini kapatır. */
    private fun cancelProgressNotifs() {
        nm.cancel(1)
    }

    private fun progressNotif(platform: String, text: String, pct: Int, id: Long): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        // "İptal" eylemi: bu bildirimin ait olduğu indirmeyi durdurur (ana ekrandaki İptal Et ile aynı).
        val cancelIntent = PendingIntent.getService(
            this,
            (id % 1_000_000L).toInt() + 1000,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val live = liveActive()
        val b = NotificationCompat.Builder(this, if (live) CH_LIVE else CH_PROGRESS)
            .setSmallIcon(if (live) com.example.gdl.R.drawable.ic_tab_download else R.drawable.stat_sys_download)
            .setContentTitle("İndiriliyor...")
            .setContentText(if (text.isNotEmpty()) "$platform • $text" else platform)
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(com.example.gdl.R.drawable.ic_cancel, "İptal", cancelIntent)

        if (live) {
            // Android 16+ Live Update (Now Bar / durum çubuğu hapı).
            // Başarısız olursa klasik bildirime düşer, indirme etkilenmez.
            b.setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
            if (isSamsung()) {
                b.addExtras(
                    samsungExtras(
                        primary = if (pct >= 0) "%${pct.coerceIn(0, 100)}" else "İndiriliyor",
                        secondary = if (text.isNotEmpty()) "$platform • $text" else platform,
                        progress = pct
                    )
                )
            }
            try {
                b.setRequestPromotedOngoing(true)
                if (pct >= 0) {
                    val p = pct.coerceIn(0, 100)
                    b.setStyle(
                        NotificationCompat.ProgressStyle()
                            .setProgress(p)
                            .setStyledByProgress(true)
                    )
                    b.setShortCriticalText("$p%")
                } else {
                    b.setStyle(
                        NotificationCompat.ProgressStyle()
                            .setProgressIndeterminate(true)
                            .setStyledByProgress(true)
                    )
                }
                return b.build()
            } catch (e: Throwable) {
                Log.w("DownloadService", "Live Update kurulamadı, klasik bildirime dönülüyor", e)
                b.setRequestPromotedOngoing(false)
                b.setStyle(null)
            }
        }

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
        val customSubtitle = intent?.getStringExtra("custom_subtitle")
        val customCover = if (intent?.hasExtra("custom_cover") == true) intent.getBooleanExtra("custom_cover", false) else null
        if (intent != null && intent.action == ACTION_CANCEL) {
            // Bildirimdeki "İptal" eylemi
            val cancelId = intent.getLongExtra(EXTRA_ID, -1L)
            if (cancelId > 0) cancelDownload(cancelId)
            stopIfIdle()
            return START_NOT_STICKY
        }
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

        val id = System.currentTimeMillis()
        startForeground(
            1,
            progressNotif(platform, "Hazırlanıyor…", -1, id),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        pending.incrementAndGet()
        val state = DownloadState(id, platform, url, -1, "Sırada", id)
        activeDownloads[id] = state
        notifyGlobalStateChanged()

        val future = executor.submit {
            runningTasks.add(id)
            try {
                if (cancelledTasks.contains(id)) {
                    val dir = File(cacheDir, "dl_$id")
                    dir.deleteRecursively()
                    Store.upsert(this, id, url, STATUS_INTERRUPTED, "Kullanıcı tarafından iptal edildi")
                    cancelProgressNotifs()
                    cancelledTasks.remove(id)
                    activeDownloads.remove(id)
                    notifyGlobalStateChanged()
                    stopIfIdle()
                    return@submit
                }
                state.detail = "Hazırlanıyor..."
                notifyGlobalStateChanged()
                process(id, url, customQuality, platform, state, customSubtitle, customCover)
            } finally {
                runningTasks.remove(id)
                futures.remove(id)
                cancelledTasks.remove(id)
                activeDownloads.remove(id)
                notifyGlobalStateChanged()
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
            cancelledTasks.clear()
            runningTasks.clear()
            notifyGlobalStateChanged()
        }
    }

    private fun process(id: Long, url: String, customQuality: String, platform: String, state: DownloadState,
                        customSubtitle: String? = null, customCover: Boolean? = null) {
        val dir = File(cacheDir, "dl_$id").apply { mkdirs() }
        val cookieTmp = File(cacheDir, "cookies_job.txt")
        var log = ""
        var saved = 0
        var savedImages = 0
        var savedVideos = 0
        var savedAudios = 0
        var savedCovers = 0
        val savedFiles = mutableListOf<File>()
        var thumbnailBitmap: Bitmap? = null
        var success = false
        var isCancelled = false

        try {
            if (cancelledTasks.contains(id)) {
                throw Exception("İptal edildi")
            }
            if (!Python.isStarted()) Python.start(AndroidPlatform(this))
            var cookiePath = ""
            val cookies = File(filesDir, "cookies.txt")
            if (cookies.exists() && cookies.length() > 0) {
                val filteredLines = mutableListOf<String>()
                cookies.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || (trimmed.startsWith("#") && !trimmed.startsWith("#HttpOnly_", ignoreCase = true))) {
                        filteredLines.add(line)
                    } else {
                        val parts = if (trimmed.contains("\t")) trimmed.split("\t") else trimmed.split(Regex("\\s+"))
                        if (parts.isNotEmpty()) {
                            val rawDomain = parts[0]
                            val cleanedDomain = Store.cleanCookieDomain(rawDomain)
                            if (Store.isCookieDomainEnabled(this, cleanedDomain)) {
                                filteredLines.add(line)
                            }
                        } else {
                            filteredLines.add(line)
                        }
                    }
                }
                val hasCookieData = filteredLines.any { it.trim().isNotEmpty() && !it.trim().startsWith("#") }
                if (hasCookieData) {
                    cookieTmp.writeText(filteredLines.joinToString("\n"))
                    cookiePath = cookieTmp.absolutePath
                }
            }
            val sink = ProgressSink({ p, detail ->
                if (cancelledTasks.contains(id)) {
                    throw Exception("İptal edildi")
                }
                activeDownloads[id]?.let { st ->
                    st.pct = p
                    st.detail = detail
                }
                notifyGlobalStateChanged()
                nm.notify(1, progressNotif(platform, detail, p, id))
            }, { cancelledTasks.contains(id) })

            val updatesDir = File(filesDir, "python_updates")
            val updatesPath = if (updatesDir.exists()) updatesDir.absolutePath else ""
            val quality = customQuality.takeIf { it.isNotEmpty() } ?: Store.videoQuality(this)
            val subtitleLang = customSubtitle ?: Store.subtitleLang(this)
            val subtitleEmbed = Store.subtitleEmbedded(this)
            val saveCover = customCover ?: Store.saveCover(this)
            val galleryTemplate = Store.galleryTemplate(this)
            val ytdlpTemplate = Store.ytdlpTemplate(this)
            val nativeDir = File(applicationInfo.nativeLibraryDir)
            Log.d("FFMPEG_FIX", "nativeDir: ${nativeDir.list()?.joinToString()}")
            val ffmpegFile = File(nativeDir, "libffmpeg.so").takeIf { it.exists() }
                ?: noBackupFilesDir.walkTopDown().firstOrNull { it.isFile && it.name == "libffmpeg.so" }
            val ffmpegPath = ffmpegFile?.absolutePath ?: ""
            // FFmpeg.init()'in açtığı libavcodec vb. kütüphanelerin klasörü
            val ffmpegLibDir = noBackupFilesDir.walkTopDown()
                .firstOrNull { it.isFile && it.name.startsWith("libavcodec") }
                ?.parentFile?.absolutePath ?: ""
            Log.d("FFMPEG_FIX", "ffmpegPath=$ffmpegPath libDir=$ffmpegLibDir")
            log = Python.getInstance().getModule("dl")
                .callAttr("run", id.toString(), url, dir.absolutePath, cookiePath, galleryTemplate, ytdlpTemplate, sink, updatesPath, quality, ffmpegPath, ffmpegLibDir,
                    subtitleLang, subtitleEmbed, saveCover)
                .toString()

            if (cancelledTasks.contains(id)) {
                throw Exception("İptal edildi")
            }

            // Kapak dosyaları ("kapak" klasörü) en sona bırakılır: yalnızca video/ses
            // gerçekten kaydedildiyse galeriye eklenir.
            val allFiles = dir.walkTopDown().filter { it.isFile }
                .sortedBy { it.parentFile?.name == "kapak" }
            for (f in allFiles) {
                if (cancelledTasks.contains(id)) {
                    throw Exception("İptal edildi")
                }
                val ext = f.extension.lowercase()
                if (f.parentFile?.name == "kapak") {
                    if (savedVideos + savedAudios > 0 && f.length() > 0 &&
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.startsWith("image") == true
                    ) {
                        if (save(f, platform, cover = true)) {
                            savedCovers++
                            log += "\n[kapak] ${f.name}: kaydedildi"
                        }
                    }
                    continue
                }
                if (ext == "srt" || ext == "vtt") {
                    // Altyazı videoya gömülemediyse (embed kapalı ya da ffmpeg yoktu)
                    // buraya ayrı bir dosya olarak düşer; Galeri'ye değil
                    // İndirilenler'e kaydedilir, çünkü görsel medya değildir.
                    if (saveSubtitle(f, platform)) {
                        log += "\n[altyazı] ${f.name}: İndirilenler'e kaydedildi"
                    }
                    continue
                }
                val mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(ext)
                if (mime != null) {
                    val video = mime.startsWith("video")
                    val image = mime.startsWith("image")
                    val audio = mime.startsWith("audio")
                    if (video || image || audio) {
                        if (video) log += "\n[ses] ${f.name}: ${if (hasAudio(f)) "var" else "YOK"}"
                        if (save(f, platform)) {
                            savedFiles.add(f)
                            if (video) savedVideos++
                            if (audio) savedAudios++
                            if (image) savedImages++
                        }
                    }
                }
            }
            saved = savedFiles.size + savedCovers
            if (saved > 0) {
                success = true
                val thumbFile = savedFiles.first()
                Log.d(TAG, "Loading thumbnail from file: ${thumbFile.absolutePath}, exists=${thumbFile.exists()}, length=${thumbFile.length()}")
                thumbnailBitmap = loadThumbnail(thumbFile)
            }
        } catch (e: Exception) {
            if (e.message?.contains("İptal edildi") == true || cancelledTasks.contains(id)) {
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

        if (isCancelled || cancelledTasks.contains(id)) {
            dir.deleteRecursively()
            Store.upsert(this, id, url, STATUS_INTERRUPTED, "Kullanıcı tarafından iptal edildi")
            cancelProgressNotifs()
            cancelledTasks.remove(id)
            activeDownloads.remove(id)
            notifyGlobalStateChanged()
            stopIfIdle()
            return
        }

        val netHeader = "Ağ: ${Store.networkLabel(this)}\n"
        if (success) {
            val status = if (saved == 1) "1 dosya" else "$saved dosya"
            Store.upsert(this, id, url, status, netHeader + log.takeLast(8000))
        } else {
            Store.upsert(this, id, url, STATUS_INTERRUPTED, netHeader + log.takeLast(8000))
        }
        notifyGlobalStateChanged()
        cancelProgressNotifs()

        val ok = success
        val coverNote = if (savedCovers > 0) " (kapak dahil)" else ""
        val detailText = when {
            (savedImages > 0) && (savedVideos == 0) -> if (savedImages == 1) "1 fotoğraf galeriye kaydedildi" else "$savedImages fotoğraf galeriye kaydedildi"
            (savedImages == 0) && (savedVideos > 0) -> if (savedVideos == 1) "1 video galeriye kaydedildi" else "$savedVideos video galeriye kaydedildi"
            else -> if (saved == 1) "1 dosya galeriye kaydedildi" else "$saved dosya galeriye kaydedildi"
        }
        val contentText = if (ok) "$platform • $detailText$coverNote" else "$platform • İndirilemedi"

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

    private fun hasAudio(f: File): Boolean {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(f.absolutePath)
            (0 until ex.trackCount).any {
                ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio") == true
            }
        } catch (e: Exception) {
            Log.w(TAG, "hasAudio hatası: ${e.message}")
            true
        } finally {
            ex.release()
        }
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

    private fun save(f: File, platform: String, cover: Boolean = false): Boolean {
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(f.extension.lowercase()) ?: return false
        val video = mime.startsWith("video")
        val image = mime.startsWith("image")
        val audio = mime.startsWith("audio")
        if (!video && !image && !audio) return false
        val album = Store.album(this)

        var cleanName = f.name.trim()

        // Eğer dosya adının başında zaten platform adı yoksa ekleyeceğiz:
        // Önce hazır gelen "Video by" veya "Photo by" fazlalıklarını temizleyip standart hale getirelim:
        val typePrefix = when {
            image -> "photo by"
            video -> "video by"
            audio -> "audio by"
            else -> ""
        }

        // Eğer siteden zaten "video by" veya "photo by" ile başlamışsa o kısmı normalize et:
        if (cleanName.startsWith("video by ", ignoreCase = true)) {
            cleanName = cleanName.substring(9).trim()
        } else if (cleanName.startsWith("photo by ", ignoreCase = true)) {
            cleanName = cleanName.substring(9).trim()
        } else if (cleanName.startsWith("audio by ", ignoreCase = true)) {
            cleanName = cleanName.substring(9).trim()
        }

        // Platform adını temizle (boşsa ekleme):
        val cleanPlatform = platform.trim()
        
        // Nihai dosya adı formatı: "Platform video by DosyaAdı.mp4"
        if (cover) {
            cleanName = "${cleanName.substringBeforeLast('.')} kapak.${cleanName.substringAfterLast('.', "jpg")}"
        }
        val finalName = if (cleanPlatform.isNotEmpty() && typePrefix.isNotEmpty()) {
            "$cleanPlatform $typePrefix $cleanName"
        } else if (typePrefix.isNotEmpty()) {
            "$typePrefix $cleanName"
        } else {
            cleanName
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                when {
                    video -> "Movies/$album"
                    audio -> "Music/$album"
                    else -> "Pictures/$album"
                }
            )
        }
        val collection = when {
            video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            audio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = contentResolver.insert(collection, values) ?: return false
        contentResolver.openOutputStream(uri)?.use { o ->
            f.inputStream().use { it.copyTo(o) }
        }
        return true
    }

    /**
     * .srt/.vtt altyazı dosyaları görsel medya olmadığı için Galeri'ye değil,
     * İndirilenler/<albüm> klasörüne kaydedilir. Android'in Downloads
     * koleksiyonu yalnızca "Download/" altındaki yollara izin verir.
     */
    private fun saveSubtitle(f: File, platform: String): Boolean {
        val ext = f.extension.lowercase()
        val mime = if (ext == "srt") "application/x-subrip" else "text/vtt"
        val album = Store.album(this)
        val cleanPlatform = platform.trim()
        val finalName = if (cleanPlatform.isNotEmpty()) "$cleanPlatform altyazı ${f.name}" else f.name

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/$album")
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        contentResolver.openOutputStream(uri)?.use { o ->
            f.inputStream().use { it.copyTo(o) }
        }
        return true
    }
}
