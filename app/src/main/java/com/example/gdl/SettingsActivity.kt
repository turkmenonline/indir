package com.example.gdl

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.net.Uri
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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
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
    private lateinit var galleryTemplateEdit: EditText
    private lateinit var ytdlpTemplateEdit: EditText
    private lateinit var ytDlpVersionText: TextView
    private lateinit var galleryDlVersionText: TextView
    private lateinit var domainRowsLayout: LinearLayout
    private lateinit var mainContent: LinearLayout
    private val currentIndex = 2

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun cookieFile() = File(filesDir, "cookies.txt")

    private fun d(v: Int) = Ui.dp(this, v)

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun section(t: String): TextView =
        Ui.text(this, t.uppercase(), 12.5f, Ui.mutedClr(this), bold = true).apply {
            letterSpacing = 0.05f
        }

    private fun label(t: String): TextView = Ui.text(this, t, 15.5f, Ui.textClr(this), true)

    private fun helper(t: String): TextView = Ui.text(this, t, 13f, Ui.mutedClr(this), false)

    private fun updateStatus() {
        val f = cookieFile()
        val saved = f.exists() && f.length() > 0

        if (::domainRowsLayout.isInitialized) {
            domainRowsLayout.removeAllViews()
            if (saved) {
                val content = f.readText()
                val domains = Store.parseCookieDomains(content)
                if (domains.isNotEmpty()) {
                    domainRowsLayout.visibility = View.VISIBLE
                    domains.forEachIndexed { index, domain ->
                        val displayName = Store.getCookieDisplayName(domain)
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            minimumHeight = d(52)
                            setPadding(d(16), d(8), d(16), d(8))
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                setMargins(-d(16), 0, -d(16), 0)
                            }

                            val labelView = TextView(this@SettingsActivity).apply {
                                text = displayName
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                                setTextColor(Ui.textClr(this@SettingsActivity))
                                typeface = Ui.font(this@SettingsActivity, bold = true)
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            }
                            addView(labelView)

                            val switchView = Ui.iosSwitch(this@SettingsActivity, Store.isCookieEnabled(this@SettingsActivity, domain)) { checked ->
                                Store.setCookieEnabled(this@SettingsActivity, domain, checked)
                                toast("$displayName çerez ${if (checked) "açık" else "kapalı"}")
                            }
                            addView(switchView, Ui.lp(Ui.WRAP, Ui.WRAP))
                        }
                        domainRowsLayout.addView(row)

                        if (index < domains.size - 1) {
                            val divider = View(this).apply {
                                setBackgroundColor(if (Ui.isDark(this@SettingsActivity)) Color.parseColor("#15FFFFFF") else Color.parseColor("#15000000"))
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, d(1)).apply {
                                    setMargins(d(16), 0, 0, 0)
                                }
                            }
                            domainRowsLayout.addView(divider)
                        }
                    }
                } else {
                    domainRowsLayout.visibility = View.GONE
                }
            } else {
                domainRowsLayout.visibility = View.GONE
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

        // yt-dlp (Video Motoru)
        val ytTitle = label("yt-dlp (Video Motoru)")
        val ytSub = helper("Sürüm: Yükleniyor…")
        ytDlpVersionText = ytSub

        val motorIconTint = if (Ui.isDark(this)) Color.WHITE else Color.parseColor("#007AFF")

        val btnYtUpdate = ImageView(this).apply {
            setImageResource(R.drawable.ic_autorenew)
            imageTintList = ColorStateList.valueOf(motorIconTint)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val p = d(7)
            setPadding(p, p, p, p)
            background = Ui.ripple(Ui.shape(Ui.primaryBg(this@SettingsActivity), d(12).toFloat()), d(12).toFloat())
            setOnClickListener {
                runYtDlpUpdate()
            }
        }

        val btnYtSettings = ImageView(this).apply {
            setImageResource(R.drawable.ic_tab_settings)
            imageTintList = ColorStateList.valueOf(motorIconTint)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val p = d(7)
            setPadding(p, p, p, p)
            background = Ui.ripple(Ui.shape(Ui.primaryBg(this@SettingsActivity), d(12).toFloat()), d(12).toFloat())
            setOnClickListener {
                showUpdateSettingsDialog()
            }
        }

        val ytRightButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(btnYtUpdate, LinearLayout.LayoutParams(d(36), d(36)).apply {
                marginEnd = d(6)
                gravity = Gravity.CENTER_VERTICAL
            })
            addView(btnYtSettings, LinearLayout.LayoutParams(d(36), d(36)).apply {
                gravity = Gravity.CENTER_VERTICAL
            })
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

        // gallery-dl (Fotoğraf Motoru)
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

        val btnGdlUpdate = ImageView(this).apply {
            setImageResource(R.drawable.ic_autorenew)
            imageTintList = ColorStateList.valueOf(motorIconTint)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val p = d(7)
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
            addView(btnGdlUpdate, LinearLayout.LayoutParams(d(36), d(36)).apply {
                marginStart = d(8)
                gravity = Gravity.CENTER_VERTICAL
            })
        }

        // Kart 1 (GENEL)
        val genCard = Ui.card(this).apply {
            addView(ytCardContent)
            val divider = View(context).apply {
                setBackgroundColor(Ui.line(context))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 1)).apply {
                    setMargins(0, Ui.dp(context, 14), 0, Ui.dp(context, 14))
                }
            }
            addView(divider)
            addView(gdlCardContent)
        }

        // Tema segmenti
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

        // Video Kalitesi segmenti
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

        // Kart 2 (GÖRÜNÜM)
        val appearanceCard = Ui.card(this).apply {
            addView(label("Uygulama Teması"))
            addView(helper("Arayüz rengini tercihine göre ayarlar"), Ui.lp(Ui.WRAP, Ui.WRAP, l = d(2), t = d(4), b = d(12)))
            addView(themeCardHolder, Ui.lp(Ui.MATCH, Ui.WRAP))

            val divider2 = View(context).apply {
                setBackgroundColor(Ui.line(context))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 1)).apply {
                    setMargins(0, Ui.dp(context, 16), 0, Ui.dp(context, 16))
                }
            }
            addView(divider2)

            addView(label("Video Kalitesi"))
            addView(helper("İndirilen videoların çözünürlük kalitesini belirler"), Ui.lp(Ui.WRAP, Ui.WRAP, l = d(2), t = d(4), b = d(12)))
            addView(qualityCardHolder, Ui.lp(Ui.MATCH, Ui.WRAP))
        }

        album = Ui.field(this, "GalleryDl").apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(Store.album(this@SettingsActivity))
        }
        galleryTemplateEdit = Ui.field(this, "{username|author[name]|category}_{tweet_id|post_shortcode|id}_{num}.{extension}").apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(Store.rawPhotoTemplate(this@SettingsActivity))
        }
        ytdlpTemplateEdit = Ui.field(this, "%(title).200B [%(id)s].%(ext)s").apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(Store.rawYtdlpTemplate(this@SettingsActivity))
        }
        domainRowsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        updateStatus()

        fun makeLink(text: String, url: String): TextView {
            return Ui.text(this, text, 13f, Ui.primary(this), bold = true).apply {
                paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                setOnClickListener {
                    try {
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(i)
                    } catch (_: Exception) {}
                }
            }
        }

        // Kart 3 (KAYDETME)
        val storageCard = Ui.card(this).apply {
            addView(label("Galeri klasörü adı"))
            addView(album, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(8)))
            addView(helper("Pictures/… ve Movies/… altında oluşturulur"), Ui.lp(Ui.WRAP, Ui.WRAP, l = d(2), t = d(6)))

            addView(label("gallery-dl şablonu (X, Instagram ve diğer sosyal siteler)"), Ui.lp(Ui.WRAP, Ui.WRAP, t = d(20)))
            addView(galleryTemplateEdit, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(8)))
            addView(makeLink("gallery-dl Kılavuzu", "https://github.com/mikf/gallery-dl/blob/master/docs/formatting.md"), Ui.lp(Ui.WRAP, Ui.WRAP, l = d(2), t = d(6)))

            addView(label("yt-dlp şablonu (YouTube ve video siteleri)"), Ui.lp(Ui.WRAP, Ui.WRAP, t = d(20)))
            addView(ytdlpTemplateEdit, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(8)))
            addView(makeLink("yt-dlp Kılavuzu (OUTPUT TEMPLATE)", "https://github.com/yt-dlp/yt-dlp#output-template"), Ui.lp(Ui.WRAP, Ui.WRAP, l = d(2), t = d(6)))

            addView(
                Ui.button(this@SettingsActivity, "Kaydet", Ui.Kind.FILLED) {
                    val aText = album.text.toString()
                    val gText = galleryTemplateEdit.text.toString().trim()
                    val ytText = ytdlpTemplateEdit.text.toString().trim()

                    if (gText.contains("/") || gText.contains("\\") || ytText.contains("/") || ytText.contains("\\")) {
                        toast("Şablon dosya adı içinde klasör ayırıcı (/ ve \\) içeremez")
                        return@button
                    }

                    if (gText.contains("%(") && gText.contains(")s")) {
                        toast("Uyarı: gallery-dl şablonunda yt-dlp sözdizimi (%(...)s) kullanıldı")
                    }
                    if (ytText.contains("{") && ytText.contains("}")) {
                        toast("Uyarı: yt-dlp şablonunda gallery-dl sözdizimi ({...}) kullanıldı")
                    }

                    var finalG = gText
                    if (finalG.isNotEmpty() && !finalG.contains("{extension}", ignoreCase = true)) {
                        finalG = finalG.removeSuffix(".") + ".{extension}"
                    }

                    var finalYt = ytText
                    if (finalYt.isNotEmpty() && !finalYt.contains("%(ext)s")) {
                        finalYt = finalYt.removeSuffix(".") + ".%(ext)s"
                    }

                    galleryTemplateEdit.setText(finalG)
                    ytdlpTemplateEdit.setText(finalYt)

                    try {
                        Store.setAlbum(this@SettingsActivity, aText)
                        Store.setGalleryTemplate(this@SettingsActivity, finalG)
                        Store.setYtdlpTemplate(this@SettingsActivity, finalYt)
                        toast("Kaydedildi")
                    } catch (e: Exception) {
                        toast("Hata: ${e.message}")
                    }
                },
                Ui.lp(Ui.MATCH, Ui.WRAP, t = d(20))
            )
        }

        // Kart 4 (ÇEREZ)
        @Suppress("DEPRECATION")
        val cookieButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                Ui.button(this@SettingsActivity, "Dosyadan aktar", Ui.Kind.TONAL) {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    i.addCategory(Intent.CATEGORY_OPENABLE)
                    i.type = "*/*"
                    startActivityForResult(i, 1)
                },
                Ui.lp(0, Ui.WRAP, 1f, r = d(4))
            )
            addView(
                Ui.button(this@SettingsActivity, "Çerezi sil", Ui.Kind.DANGER) {
                    val f = cookieFile()
                    if (!f.exists() || f.length() == 0L) {
                        toast("Kayıtlı çerez yok")
                        return@button
                    }

                    val dialog = Dialog(this@SettingsActivity)
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    
                    val cardBgColor = if (Ui.isDark(this@SettingsActivity)) Color.parseColor("#1E1E22") else Color.parseColor("#F8F8FA")
                    val borderClr = if (Ui.isDark(this@SettingsActivity)) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")

                    val rootLayout = LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(d(20), d(20), d(20), d(20))
                        background = Ui.shape(cardBgColor, d(20).toFloat(), borderClr, d(1))
                        elevation = d(16).toFloat()
                    }

                    val titleView = Ui.text(this@SettingsActivity, "Çerezleri Sil", 18f, Ui.textClr(this@SettingsActivity), true)
                    rootLayout.addView(titleView, Ui.lp(Ui.MATCH, Ui.WRAP, b = d(4)))

                    val descView = Ui.text(this@SettingsActivity, "Kaldırmak istediğiniz çerezleri seçin:", 14f, Ui.mutedClr(this@SettingsActivity), false)
                    rootLayout.addView(descView, Ui.lp(Ui.MATCH, Ui.WRAP, b = d(16)))

                    val checkboxes = mutableMapOf<String, CheckBox>()
                    val checkboxContainer = LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                    }

                    val content = f.readText()
                    val domains = Store.parseCookieDomains(content)
                    domains.forEach { domain ->
                        val displayName = Store.getCookieDisplayName(domain)
                        val cb = CheckBox(this@SettingsActivity).apply {
                            text = displayName
                            isChecked = true
                            setTextColor(Ui.textClr(this@SettingsActivity))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        }
                        checkboxes[domain] = cb
                        checkboxContainer.addView(cb, Ui.lp(Ui.MATCH, Ui.WRAP, b = d(6)))
                    }

                    rootLayout.addView(checkboxContainer, Ui.lp(Ui.MATCH, Ui.WRAP, b = d(20)))

                    val buttonRow = LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }
                    
                    val cancelBtn = Ui.button(this@SettingsActivity, "Vazgeç", Ui.Kind.TONAL) {
                        dialog.dismiss()
                    }.apply {
                        val tonalBg = if (Ui.isDark(this@SettingsActivity)) Color.parseColor("#333338") else Color.parseColor("#E5E5EA")
                        background = Ui.ripple(Ui.shape(tonalBg, 999f), 999f)
                    }

                    val deleteBtn = Ui.button(this@SettingsActivity, "Sil", Ui.Kind.DANGER) {
                        val selectedDomains = checkboxes.filter { it.value.isChecked }.keys
                        if (selectedDomains.isNotEmpty()) {
                            val allDomains = checkboxes.keys
                            if (selectedDomains.containsAll(allDomains)) {
                                cookieFile().delete()
                                updateStatus()
                                toast("Çerezler silindi")
                            } else {
                                val currentContent = cookieFile().readText()
                                val remainingLines = mutableListOf<String>()
                                currentContent.lineSequence().forEach { line ->
                                    val trimmed = line.trim()
                                    if (trimmed.isEmpty() || (trimmed.startsWith("#") && !trimmed.startsWith("#HttpOnly_", ignoreCase = true))) {
                                        remainingLines.add(line)
                                    } else {
                                        val parts = if (trimmed.contains("\t")) trimmed.split("\t") else trimmed.split(Regex("\\s+"))
                                        if (parts.isNotEmpty()) {
                                            val domain = Store.cleanCookieDomain(parts[0])
                                            if (!selectedDomains.any { sd -> domain == sd || domain.endsWith(".$sd") }) {
                                                remainingLines.add(line)
                                            }
                                        } else {
                                            remainingLines.add(line)
                                        }
                                    }
                                }
                                val hasData = remainingLines.any { it.trim().isNotEmpty() && !it.trim().startsWith("#") }
                                if (hasData) {
                                    cookieFile().writeText(remainingLines.joinToString("\n").trimEnd() + "\n")
                                } else {
                                    cookieFile().delete()
                                }
                                updateStatus()
                                toast("Seçilen çerezler silindi")
                            }
                        }
                        dialog.dismiss()
                    }.apply {
                        background = Ui.ripple(Ui.shape(Color.parseColor("#DC2626"), 999f), 999f)
                        setTextColor(Color.WHITE)
                    }

                    buttonRow.addView(cancelBtn, Ui.lp(0, Ui.WRAP, 1f, r = d(8)))
                    buttonRow.addView(deleteBtn, Ui.lp(0, Ui.WRAP, 1f, l = d(8)))

                    rootLayout.addView(buttonRow, Ui.lp(Ui.MATCH, Ui.WRAP))
                    
                    dialog.setContentView(rootLayout)
                    dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
                    dialog.show()
                },
                Ui.lp(0, Ui.WRAP, 1f, l = d(4))
            )
        }
        val cookieCard = Ui.card(this).apply {
            addView(helper("Giriş isteyen siteler için cookies.txt (Netscape biçimi)"))
            addView(domainRowsLayout, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(14)))
            addView(cookieButtons, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(14)))
        }

        // Kart 5 (GEÇMİŞ)
        val historyCard = Ui.card(this).apply {
            addView(helper("İndirme geçmişini ve kayıtlarını temizler. Galerideki dosyalara dokunmaz."))
            addView(
                Ui.button(this@SettingsActivity, "Geçmişi temizle", Ui.Kind.DANGER) {
                    val dialog = Dialog(this@SettingsActivity)
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    
                    val cardBgColor = if (Ui.isDark(this@SettingsActivity)) Color.parseColor("#1C1C1E") else Color.parseColor("#F8F8FA")
                    val borderClr = if (Ui.isDark(this@SettingsActivity)) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")

                    val rootLayout = LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(d(20), d(20), d(20), d(20))
                        background = Ui.shape(cardBgColor, d(24).toFloat(), borderClr, d(1))
                        elevation = d(16).toFloat()
                    }

                    val titleView = Ui.text(this@SettingsActivity, "Geçmişi Temizle", 18f, Ui.textClr(this@SettingsActivity), true)
                    rootLayout.addView(titleView, Ui.lp(Ui.MATCH, Ui.WRAP, b = d(8)))

                    val descView = Ui.text(this@SettingsActivity, "Tüm indirme kayıtları silinecektir. Galerideki dosyalarınıza dokunulmaz.", 13f, Ui.mutedClr(this@SettingsActivity), false)
                    rootLayout.addView(descView, Ui.lp(Ui.MATCH, Ui.WRAP, b = d(24)))

                    val buttonRow = LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }
                    
                    val cancelBtn = Ui.button(this@SettingsActivity, "Vazgeç", Ui.Kind.TONAL) {
                        dialog.dismiss()
                    }.apply {
                        val tonalBg = if (Ui.isDark(this@SettingsActivity)) Color.parseColor("#3A3A3C") else Color.parseColor("#E5E5EA")
                        background = Ui.ripple(Ui.shape(tonalBg, 999f), 999f)
                    }

                    val clearBtn = Ui.button(this@SettingsActivity, "Temizle", Ui.Kind.DANGER) {
                        Store.clearHistory(this@SettingsActivity)
                        toast("Geçmiş temizlendi")
                        dialog.dismiss()
                    }.apply {
                        background = Ui.ripple(Ui.shape(Ui.ERR, 999f), 999f)
                        setTextColor(Color.WHITE)
                    }

                    buttonRow.addView(cancelBtn, Ui.lp(0, Ui.WRAP, 1f, r = d(8)))
                    buttonRow.addView(clearBtn, Ui.lp(0, Ui.WRAP, 1f, l = d(8)))

                    rootLayout.addView(buttonRow, Ui.lp(Ui.MATCH, Ui.WRAP))
                    
                    dialog.setContentView(rootLayout)
                    dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
                    dialog.show()
                },
                Ui.lp(Ui.MATCH, Ui.WRAP, t = d(12))
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(16), 0, d(16), d(96))
            clipToPadding = false
            addView(section("GENEL"), Ui.lp(Ui.WRAP, Ui.WRAP, b = d(8)))
            addView(genCard, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(section("GÖRÜNÜM"), Ui.lp(Ui.WRAP, Ui.WRAP, t = d(24), b = d(8)))
            addView(appearanceCard, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(section("KAYDETME"), Ui.lp(Ui.WRAP, Ui.WRAP, t = d(24), b = d(8)))
            addView(storageCard, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(section("ÇEREZ"), Ui.lp(Ui.WRAP, Ui.WRAP, t = d(24), b = d(8)))
            addView(cookieCard, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(section("GEÇMİŞ"), Ui.lp(Ui.WRAP, Ui.WRAP, t = d(24), b = d(8)))
            addView(historyCard, Ui.lp(Ui.MATCH, Ui.WRAP))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            addView(content)
        }

        val header = Ui.header(this, "Ayarlar", null, null)
        mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(this@SettingsActivity))
            addView(header, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(12), b = d(12)))
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
            setMargins(0, 0, 0, d(24))
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
