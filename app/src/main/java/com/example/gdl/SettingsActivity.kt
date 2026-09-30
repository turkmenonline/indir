package com.example.gdl

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.io.File

class SettingsActivity : AppCompatActivity() {
    private lateinit var album: EditText
    private lateinit var template: EditText
    private lateinit var cookieBox: EditText
    private lateinit var status: TextView
    private lateinit var ytDlpVersionText: TextView
    private lateinit var galleryDlVersionText: TextView
    private lateinit var domainBadgesLayout: LinearLayout
    private lateinit var domainBadgesScroll: HorizontalScrollView
    private lateinit var mainContent: LinearLayout
    private val currentIndex = 2

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun cookieFile() = File(filesDir, "cookies.txt")

    private fun d(v: Int) = Ui.dp(this, v)

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun section(t: String): TextView =
        Ui.text(this, t, 13f, Ui.primary(this), true).apply { setPadding(d(4), 0, 0, 0) }

    private fun label(t: String): TextView = Ui.text(this, t, 14f, Ui.textClr(this), true)

    private fun helper(t: String): TextView = Ui.text(this, t, 12f, Ui.mutedClr(this))

    private fun updateStatus() {
        val f = cookieFile()
        val saved = f.exists() && f.length() > 0
        status.text = if (saved) "Kayıtlı · ${f.length()} bayt" else "Kayıtlı çerez yok"
        status.setTextColor(if (saved) Ui.OK else Ui.mutedClr(this))
        status.background = Ui.shape(
            if (saved) Ui.OK_BG else Ui.bg(this), d(50).toFloat(),
            if (saved) Ui.OK_BG else Ui.fieldLine(this), d(1)
        )

        if (::domainBadgesLayout.isInitialized) {
            domainBadgesLayout.removeAllViews()
            if (saved) {
                val content = f.readText()
                val domains = Store.parseCookieDomains(content)
                if (domains.isNotEmpty()) {
                    domainBadgesScroll.visibility = View.VISIBLE
                    domains.forEach { domain ->
                        val pill = Ui.pill(this, "✔ $domain", Ui.OK, Ui.OK_BG)
                        domainBadgesLayout.addView(pill, Ui.lp(Ui.WRAP, Ui.WRAP, r = d(6)))
                    }
                } else {
                    domainBadgesScroll.visibility = View.GONE
                }
            } else {
                domainBadgesScroll.visibility = View.GONE
            }
        }
    }

    private fun saveCookies(raw: String, isMerge: Boolean = true) {
        val t = raw.replace("\r\n", "\n").trim()
        if (t.isEmpty()) {
            toast("Boş içerik")
            return
        }

        val f = cookieFile()
        val existing = if (f.exists() && f.length() > 0) f.readText() else ""
        val finalContent = if (isMerge && existing.isNotEmpty()) {
            Store.mergeCookies(existing, t)
        } else {
            if (!t.lineSequence().first().contains("Netscape HTTP Cookie File")) {
                "# Netscape HTTP Cookie File\n" + t
            } else {
                t
            }
        }

        f.writeText(finalContent.trimEnd() + "\n")
        cookieBox.setText("")
        updateStatus()
        val domains = Store.parseCookieDomains(finalContent)
        val domainCount = domains.size
        if (isMerge && existing.isNotEmpty()) {
            toast("Çerezler birleştirildi ($domainCount site)")
        } else {
            toast("Çerez kaydedildi")
        }
    }

    private fun loadEngineVersions() {
        val updatesDir = File(filesDir, "python_updates")
        Thread {
            try {
                if (!Python.isStarted()) Python.start(AndroidPlatform(this))
                val py = Python.getInstance()
                val dlModule = py.getModule("dl")
                val jsonStr = dlModule.callAttr("get_versions_json", updatesDir.absolutePath).toString()

                val obj = JSONObject(jsonStr)
                val ytVer = obj.optString("yt_dlp", "bilinmiyor")
                val gdlVer = obj.optString("gallery_dl", "bilinmiyor")

                val fullVer = "yt-dlp $ytVer • gallery-dl $gdlVer"
                Store.setCachedVersions(this, fullVer)

                mainHandler.post {
                    ytDlpVersionText.text = "Sürüm: $ytVer"
                    galleryDlVersionText.text = "Sürüm: $gdlVer"
                }

                if (Store.shouldAutoUpdate(this)) {
                    runYtDlpUpdate(isAuto = true)
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun runYtDlpUpdate(isAuto: Boolean = false) {
        if (!isAuto) toast("yt-dlp güncellemesi başlatıldı…")
        ytDlpVersionText.text = "Güncelleniyor…"
        val updatesDir = File(filesDir, "python_updates")

        Thread {
            try {
                if (!Python.isStarted()) Python.start(AndroidPlatform(this))
                val py = Python.getInstance()
                val dlModule = py.getModule("dl")
                val channel = Store.updateChannel(this)

                val newVer = dlModule.callAttr("update_yt_dlp", updatesDir.absolutePath, channel).toString()
                Store.setLastUpdateCheck(this, System.currentTimeMillis())

                mainHandler.post {
                    ytDlpVersionText.text = "Sürüm: $newVer"
                    toast("yt-dlp başarıyla güncellendi!")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    val msg = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
                    loadEngineVersions()
                    if (!isAuto) toast("yt-dlp hatası: $msg")
                }
            }
        }.start()
    }

    private fun runGalleryDlUpdate() {
        toast("gallery-dl güncellemesi başlatıldı…")
        galleryDlVersionText.text = "Güncelleniyor…"
        val updatesDir = File(filesDir, "python_updates")

        Thread {
            try {
                if (!Python.isStarted()) Python.start(AndroidPlatform(this))
                val py = Python.getInstance()
                val dlModule = py.getModule("dl")

                val newVer = dlModule.callAttr("update_gallery_dl", updatesDir.absolutePath).toString()

                mainHandler.post {
                    galleryDlVersionText.text = "Sürüm: $newVer"
                    toast("gallery-dl başarıyla güncellendi!")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    val msg = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
                    loadEngineVersions()
                    toast("gallery-dl hatası: $msg")
                }
            }
        }.start()
    }

    private fun showUpdateSettingsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        var currentCh = Store.updateChannel(this)
        var currentPeriod = Store.autoUpdatePeriod(this)

        val card = Ui.card(this).apply {
            val p = d(20)
            setPadding(p, p, p, p)
        }

        val dialogTitle = Ui.text(this, "Güncelleme Ayarları", 18f, Ui.textClr(this), bold = true)
        card.addView(dialogTitle)

        // Güncelleme Kanalı
        val chHeading = Ui.text(this, "Güncelleme kanalı", 14f, Ui.primary(this), bold = true)
        card.addView(chHeading, Ui.lp(Ui.WRAP, Ui.WRAP, t = d(16)))

        val channelOptions = listOf(
            Store.CHANNEL_STABLE to "Stable (PyPI Kararlı)",
            Store.CHANNEL_NIGHTLY to "Nightly (GitHub En Son)"
        )

        val chRows = mutableListOf<TextView>()
        val chContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        channelOptions.forEach { (key, labelText) ->
            val row = Ui.text(
                this,
                if (key == currentCh) "🔘  $labelText" else "⚪  $labelText",
                14f,
                if (key == currentCh) Ui.primary(this) else Ui.textClr(this),
                bold = (key == currentCh)
            ).apply {
                setPadding(d(8), d(10), d(8), d(10))
                background = Ui.ripple(Ui.shape(Color.TRANSPARENT, d(10).toFloat()), d(10).toFloat())
                setOnClickListener {
                    currentCh = key
                    chRows.forEachIndexed { idx, tv ->
                        val k = channelOptions[idx].first
                        val lbl = channelOptions[idx].second
                        val sel = (k == currentCh)
                        tv.text = if (sel) "🔘  $lbl" else "⚪  $lbl"
                        tv.setTextColor(if (sel) Ui.primary(this@SettingsActivity) else Ui.textClr(this@SettingsActivity))
                        tv.typeface = Ui.font(this@SettingsActivity, bold = sel)
                    }
                }
            }
            chRows.add(row)
            chContainer.addView(row)
        }
        card.addView(chContainer, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(6)))

        // Otomatik Güncelleme
        val autoHeading = Ui.text(this, "Otomatik güncelleme", 14f, Ui.primary(this), bold = true)
        card.addView(autoHeading, Ui.lp(Ui.WRAP, Ui.WRAP, t = d(16)))

        val periodOptions = listOf(
            Store.AUTO_UPDATE_DISABLED to "Devre dışı bırakıldı",
            Store.AUTO_UPDATE_DAILY to "Her gün",
            Store.AUTO_UPDATE_WEEKLY to "Her hafta",
            Store.AUTO_UPDATE_MONTHLY to "Her ay"
        )

        val periodRows = mutableListOf<TextView>()
        val periodContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        periodOptions.forEach { (code, labelText) ->
            val row = Ui.text(
                this,
                if (code == currentPeriod) "🔘  $labelText" else "⚪  $labelText",
                14f,
                if (code == currentPeriod) Ui.primary(this) else Ui.textClr(this),
                bold = (code == currentPeriod)
            ).apply {
                setPadding(d(8), d(10), d(8), d(10))
                background = Ui.ripple(Ui.shape(Color.TRANSPARENT, d(10).toFloat()), d(10).toFloat())
                setOnClickListener {
                    currentPeriod = code
                    periodRows.forEachIndexed { idx, tv ->
                        val c = periodOptions[idx].first
                        val lbl = periodOptions[idx].second
                        val sel = (c == currentPeriod)
                        tv.text = if (sel) "🔘  $lbl" else "⚪  $lbl"
                        tv.setTextColor(if (sel) Ui.primary(this@SettingsActivity) else Ui.textClr(this@SettingsActivity))
                        tv.typeface = Ui.font(this@SettingsActivity, bold = sel)
                    }
                }
            }
            periodRows.add(row)
            periodContainer.addView(row)
        }
        card.addView(periodContainer, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(6)))

        // İptal / Onayla Butonları
        val btnContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                Ui.button(this@SettingsActivity, "İptal et", Ui.Kind.OUTLINE) {
                    dialog.dismiss()
                },
                Ui.lp(0, Ui.WRAP, 1f, r = d(6))
            )
            addView(
                Ui.button(this@SettingsActivity, "Onayla", Ui.Kind.FILLED) {
                    Store.setUpdateChannel(this@SettingsActivity, currentCh)
                    Store.setAutoUpdatePeriod(this@SettingsActivity, currentPeriod)
                    toast("Ayarlar kaydedildi")
                    dialog.dismiss()
                },
                Ui.lp(0, Ui.WRAP, 1f, l = d(6))
            )
        }
        card.addView(btnContainer, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(24)))

        val root = FrameLayout(this).apply {
            setPadding(d(20), d(20), d(20), d(20))
            addView(card, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        }

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    @Suppress("DEPRECATION")
    private fun navigateToTab(targetIndex: Int, currentIndex: Int) {
        if (targetIndex == currentIndex) return
        val intent = when (targetIndex) {
            0 -> Intent(this, MainActivity::class.java)
            1 -> Intent(this, HistoryActivity::class.java)
            2 -> Intent(this, SettingsActivity::class.java)
            else -> return
        }.apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("from_index", this@SettingsActivity.currentIndex)
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isHorizontalSwipe = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = ev.rawX
                touchDownY = ev.rawY
                isHorizontalSwipe = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = ev.rawX - touchDownX
                val deltaY = ev.rawY - touchDownY
                if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 25) {
                    isHorizontalSwipe = true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isHorizontalSwipe) {
                    val deltaX = ev.rawX - touchDownX
                    if (Math.abs(deltaX) > 100) {
                        if (deltaX > 0) {
                            onSwipeRight()
                        } else {
                            onSwipeLeft()
                        }
                        isHorizontalSwipe = false
                        return true
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun onSwipeLeft() {
        // no-op
    }

    private fun onSwipeRight() {
        navigateToTab(1, 2)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Store.applyTheme(this)
        } catch (_: Throwable) {}
        super.onCreate(savedInstanceState)

        // Kart 1: yt-dlp (Video Motoru)
        val ytTitle = label("yt-dlp (Video Motoru)")
        val ytSub = helper("Sürüm: Yükleniyor…")
        ytDlpVersionText = ytSub

        val btnYtUpdate = Ui.text(this, "🔄", 18f, Ui.primary(this), bold = true).apply {
            gravity = Gravity.CENTER
            val p = d(10)
            setPadding(p, p, p, p)
            background = Ui.ripple(Ui.shape(Ui.primaryBg(this@SettingsActivity), d(12).toFloat()), d(12).toFloat())
            setOnClickListener {
                runYtDlpUpdate()
            }
        }

        val btnYtSettings = Ui.text(this, "⚙️", 18f, Ui.primary(this), bold = true).apply {
            gravity = Gravity.CENTER
            val p = d(10)
            setPadding(p, p, p, p)
            background = Ui.ripple(Ui.shape(Ui.primaryBg(this@SettingsActivity), d(12).toFloat()), d(12).toFloat())
            setOnClickListener {
                showUpdateSettingsDialog()
            }
        }

        val ytRightButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(btnYtUpdate, Ui.lp(Ui.WRAP, Ui.WRAP, r = d(6)))
            addView(btnYtSettings, Ui.lp(Ui.WRAP, Ui.WRAP))
        }

        val ytTextColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ytTitle)
            addView(ytSub, Ui.lp(Ui.WRAP, Ui.WRAP, t = d(4)))
        }

        val ytCardContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ytTextColumn, Ui.lp(0, Ui.WRAP, 1f))
            addView(ytRightButtons, Ui.lp(Ui.WRAP, Ui.WRAP, l = d(8)))
        }

        val ytCard = Ui.card(this).apply {
            addView(ytCardContent)
        }

        // Kart 2: gallery-dl (Fotoğraf Motoru)
        val gdlTitle = label("gallery-dl (Fotoğraf Motoru)")
        val gdlSub = helper("Sürüm: Yükleniyor…")
        galleryDlVersionText = gdlSub

        val gdlBadge = Ui.pill(this, "Kararlı (Stable)", Ui.OK, Ui.OK_BG)

        val gdlSubRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(gdlSub)
            addView(gdlBadge, Ui.lp(Ui.WRAP, Ui.WRAP, l = d(8)))
        }

        val btnGdlUpdate = Ui.text(this, "🔄", 18f, Ui.primary(this), bold = true).apply {
            gravity = Gravity.CENTER
            val p = d(10)
            setPadding(p, p, p, p)
            background = Ui.ripple(Ui.shape(Ui.primaryBg(this@SettingsActivity), d(12).toFloat()), d(12).toFloat())
            setOnClickListener {
                runGalleryDlUpdate()
            }
        }

        val gdlTextColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(gdlTitle)
            addView(gdlSubRow, Ui.lp(Ui.WRAP, Ui.WRAP, t = d(4)))
        }

        val gdlCardContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(gdlTextColumn, Ui.lp(0, Ui.WRAP, 1f))
            addView(btnGdlUpdate, Ui.lp(Ui.WRAP, Ui.WRAP, l = d(8)))
        }

        val gdlCard = Ui.card(this).apply {
            addView(gdlCardContent)
        }

        // Tema kartı
        val themeCardHolder = FrameLayout(this)
        fun updateThemeSegment() {
            themeCardHolder.removeAllViews()
            val currentTheme = Store.themeMode(this@SettingsActivity)
            val themeModes = listOf(
                Pair("Sistem", Store.THEME_SYSTEM),
                Pair("Açık", Store.THEME_LIGHT),
                Pair("Koyu", Store.THEME_DARK)
            )
            val currentIndex = themeModes.indexOfFirst { it.second == currentTheme }.coerceAtLeast(0)
            val titles = themeModes.map { it.first }
            val seg = Ui.segmentedControl(this@SettingsActivity, titles, currentIndex) { idx ->
                val newMode = themeModes[idx].second
                if (Store.themeMode(this@SettingsActivity) != newMode) {
                    Store.setThemeMode(this@SettingsActivity, newMode)
                    Store.applyTheme(this@SettingsActivity)
                }
            }
            themeCardHolder.addView(seg, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        }
        updateThemeSegment()
        val themeCard = Ui.card(this).apply {
            addView(label("Uygulama Teması"))
            addView(helper("Arayüz rengini tercihine göre ayarlar"), Ui.lp(Ui.WRAP, Ui.WRAP, l = d(2), t = d(4), b = d(12)))
            addView(themeCardHolder, Ui.lp(Ui.MATCH, Ui.WRAP))
        }

        // Video Kalitesi kartı
        val qualityCardHolder = FrameLayout(this)
        fun updateQualitySegment() {
            qualityCardHolder.removeAllViews()
            val currentQuality = Store.videoQuality(this@SettingsActivity)
            val qualityList = listOf(
                Pair("En iyi", Store.QUALITY_BEST),
                Pair("720p", Store.QUALITY_720P),
                Pair("Ses", Store.QUALITY_AUDIO_ONLY)
            )
            val currentIndex = qualityList.indexOfFirst { it.second == currentQuality }.coerceAtLeast(0)
            val titles = qualityList.map { it.first }
            val seg = Ui.segmentedControl(this@SettingsActivity, titles, currentIndex) { idx ->
                val qVal = qualityList[idx].second
                if (Store.videoQuality(this@SettingsActivity) != qVal) {
                    Store.setVideoQuality(this@SettingsActivity, qVal)
                    updateQualitySegment()
                    toast("Video kalitesi güncellendi: ${qualityList[idx].first}")
                }
            }
            qualityCardHolder.addView(seg, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        }
        updateQualitySegment()
        val qualityCard = Ui.card(this).apply {
            addView(label("Video Kalitesi"))
            addView(helper("İndirilen videoların çözünürlük kalitesini belirler"), Ui.lp(Ui.WRAP, Ui.WRAP, l = d(2), t = d(4), b = d(12)))
            addView(qualityCardHolder, Ui.lp(Ui.MATCH, Ui.WRAP))
        }

        album = Ui.field(this, "GalleryDl").apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(Store.album(this@SettingsActivity))
        }
        template = Ui.field(this, "örn. {category}_{id}_{num}.{extension}").apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(Store.template(this@SettingsActivity))
        }
        cookieBox = Ui.field(this, "cookies.txt içeriğini buraya yapıştırın").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
        }
        status = Ui.text(this, "", 12f, Ui.mutedClr(this), true).apply {
            setPadding(d(10), d(4), d(10), d(4))
        }

        domainBadgesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        domainBadgesScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            addView(domainBadgesLayout)
        }

        updateStatus()

        // Kaydetme kartı
        val saveCard = Ui.card(this).apply {
            addView(label("Galeri klasörü adı"))
            addView(album, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(8)))
            addView(helper("Pictures/… ve Movies/… altında oluşturulur"), Ui.lp(Ui.WRAP, Ui.WRAP, l = d(2), t = d(6)))
            addView(label("Dosya adı şablonu"), Ui.lp(Ui.WRAP, Ui.WRAP, t = d(20)))
            addView(template, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(8)))
            addView(helper("Boş bırakırsan varsayılan kullanılır (yalnızca gallery-dl)"), Ui.lp(Ui.WRAP, Ui.WRAP, l = d(2), t = d(6)))
            addView(
                Ui.button(this@SettingsActivity, "Kaydet", Ui.Kind.FILLED) {
                    Store.setAlbum(this@SettingsActivity, album.text.toString())
                    Store.setTemplate(this@SettingsActivity, template.text.toString())
                    toast("Kaydedildi")
                },
                Ui.lp(Ui.MATCH, Ui.WRAP, t = d(20))
            )
        }

        // Çerez kartı
        @Suppress("DEPRECATION")
        val cookieButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                Ui.button(this@SettingsActivity, "Kaydet", Ui.Kind.TONAL) { saveCookies(cookieBox.text.toString(), isMerge = true) },
                Ui.lp(0, Ui.WRAP, 1f, r = d(4))
            )
            addView(
                Ui.button(this@SettingsActivity, "Dosyadan aktar", Ui.Kind.TONAL) {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    i.addCategory(Intent.CATEGORY_OPENABLE)
                    i.type = "*/*"
                    startActivityForResult(i, 1)
                },
                Ui.lp(0, Ui.WRAP, 1f, l = d(4))
            )
        }
        val cookieCard = Ui.card(this).apply {
            addView(helper("Giriş isteyen siteler için cookies.txt (Netscape biçimi)"))
            addView(status, Ui.lp(Ui.WRAP, Ui.WRAP, t = d(12)))
            addView(domainBadgesScroll, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(10)))
            addView(cookieBox, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(10)))
            addView(cookieButtons, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(12)))
            addView(
                Ui.button(this@SettingsActivity, "Çerezi sil", Ui.Kind.DANGER) {
                    cookieFile().delete()
                    updateStatus()
                    toast("Çerez silindi")
                },
                Ui.lp(Ui.MATCH, Ui.WRAP, t = d(8))
            )
        }

        // Geçmiş kartı
        val historyCard = Ui.card(this).apply {
            addView(helper("İndirme geçmişini ve kayıtlarını temizler. Galerideki dosyalara dokunmaz."))
            addView(
                Ui.button(this@SettingsActivity, "Geçmişi temizle", Ui.Kind.DANGER) {
                    Store.clearHistory(this@SettingsActivity)
                    toast("Geçmiş temizlendi")
                },
                Ui.lp(Ui.MATCH, Ui.WRAP, t = d(12))
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(16), 0, d(16), d(100))
            clipToPadding = false
            addView(section("Genel"), Ui.lp(Ui.WRAP, Ui.WRAP, b = d(8)))
            addView(ytCard, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(gdlCard, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(10)))
            addView(section("Görünüm"), Ui.lp(Ui.WRAP, Ui.WRAP, t = d(24), b = d(8)))
            addView(themeCard, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(qualityCard, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(10)))
            addView(section("Kaydetme"), Ui.lp(Ui.WRAP, Ui.WRAP, t = d(24), b = d(8)))
            addView(saveCard, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(section("Çerez"), Ui.lp(Ui.WRAP, Ui.WRAP, t = d(24), b = d(8)))
            addView(cookieCard, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(section("Geçmiş"), Ui.lp(Ui.WRAP, Ui.WRAP, t = d(24), b = d(8)))
            addView(historyCard, Ui.lp(Ui.MATCH, Ui.WRAP))
        }
        val bottomSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this@SettingsActivity, 110))
        }
        content.addView(bottomSpacer)
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            addView(content)
        }

        val header = Ui.header(
            this, "Ayarlar",
            Ui.iconButton(this, R.drawable.ic_arrow_back, "Geri") { finish() },
            null
        )
        mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(this@SettingsActivity))
            addView(header, Ui.lp(Ui.MATCH, Ui.WRAP, l = d(12), r = d(12)))
            addView(scroll, Ui.lp(Ui.MATCH, 0, 1f))
        }

        // Floating Liquid Glass Bottom Navigation Bar (3 items)
        val navItems = listOf(
            Ui.NavItem("İndir", R.drawable.ic_tab_download),
            Ui.NavItem("Geçmiş", R.drawable.ic_history),
            Ui.NavItem("Ayarlar", R.drawable.ic_tab_settings)
        )

        val navBar = Ui.floatingNavBar(this, navItems, selectedIndex = 2) { index ->
            navigateToTab(index, 2)
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

        scroll.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            scrollHandler.removeCallbacks(showBarRunnable)
            scrollHandler.postDelayed(showBarRunnable, 400)

            if (scrollY > oldScrollY + 8) {
                val targetY = navBar.height.toFloat() + d(40)
                navBar.animate()
                    .translationY(targetY)
                    .alpha(0f)
                    .setDuration(250)
                    .start()
            } else if (scrollY < oldScrollY - 8) {
                navBar.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Ui.bg(this@SettingsActivity))
            addView(mainContent, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            addView(navBar, navParams)
        }
        setContentView(root)
        Ui.setupEdgeToEdge(this, root)

        loadEngineVersions()
        handlePageAnimation(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePageAnimation(intent)
    }

    private fun handlePageAnimation(intent: Intent?) {
        val fromIndex = intent?.getIntExtra("from_index", -1) ?: -1
        if (fromIndex != -1) {
            val fromRight = fromIndex < currentIndex
            Ui.animatePageIn(mainContent, fromRight)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val txt = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (txt == null) toast("Dosya okunamadı") else saveCookies(txt, isMerge = true)
        }
    }
}
