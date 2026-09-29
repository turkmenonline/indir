package com.example.gdl

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class SettingsActivity : Activity() {
    private lateinit var album: EditText
    private lateinit var template: EditText
    private lateinit var cookieBox: EditText
    private lateinit var status: TextView

    private fun cookieFile() = File(filesDir, "cookies.txt")

    private fun d(v: Int) = Ui.dp(this, v)

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun section(t: String): TextView =
        Ui.text(this, t, 13f, Ui.PRIMARY, true).apply { setPadding(d(4), 0, 0, 0) }

    private fun label(t: String): TextView = Ui.text(this, t, 14f, Ui.TEXT, true)

    private fun helper(t: String): TextView = Ui.text(this, t, 12f, Ui.MUTED)

    private fun updateStatus() {
        val f = cookieFile()
        val saved = f.exists() && f.length() > 0
        status.text = if (saved) "Kayıtlı · ${f.length()} bayt" else "Kayıtlı çerez yok"
        status.setTextColor(if (saved) Ui.OK else Ui.MUTED)
        status.background = Ui.shape(
            if (saved) Ui.OK_BG else Ui.BG, d(50).toFloat(),
            if (saved) Ui.OK_BG else Ui.FIELD_LINE, d(1)
        )
    }

    private fun saveCookies(raw: String) {
        var t = raw.replace("\r\n", "\n").trim()
        if (t.isEmpty()) {
            toast("Boş içerik")
            return
        }
        if (!t.lineSequence().first().contains("Netscape HTTP Cookie File")) {
            t = "# Netscape HTTP Cookie File\n" + t
        }
        cookieFile().writeText(t + "\n")
        cookieBox.setText("")
        updateStatus()
        toast("Çerez kaydedildi")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        status = Ui.text(this, "", 12f, Ui.MUTED, true).apply {
            setPadding(d(10), d(4), d(10), d(4))
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
        val cookieButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                Ui.button(this@SettingsActivity, "Kaydet", Ui.Kind.TONAL) { saveCookies(cookieBox.text.toString()) },
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
            addView(cookieBox, Ui.lp(Ui.MATCH, Ui.WRAP, t = d(12)))
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
            addView(helper("Ana ekrandaki indirme listesini siler. Galerideki dosyalara dokunmaz."))
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
            setPadding(d(16), 0, d(16), d(24))
            addView(section("Kaydetme"), Ui.lp(Ui.WRAP, Ui.WRAP, b = d(8)))
            addView(saveCard, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(section("Çerez"), Ui.lp(Ui.WRAP, Ui.WRAP, t = d(24), b = d(8)))
            addView(cookieCard, Ui.lp(Ui.MATCH, Ui.WRAP))
            addView(section("Geçmiş"), Ui.lp(Ui.WRAP, Ui.WRAP, t = d(24), b = d(8)))
            addView(historyCard, Ui.lp(Ui.MATCH, Ui.WRAP))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(content)
        }

        val header = Ui.header(
            this, "Ayarlar",
            Ui.iconButton(this, R.drawable.ic_arrow_back, "Geri") { finish() },
            null
        )
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            addView(header, Ui.lp(Ui.MATCH, Ui.WRAP, l = d(12), r = d(12)))
            addView(scroll, Ui.lp(Ui.MATCH, 0, 1f))
        }
        setContentView(root)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val txt = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (txt == null) toast("Dosya okunamadı") else saveCookies(txt)
        }
    }
}
