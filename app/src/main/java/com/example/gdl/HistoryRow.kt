package com.example.gdl

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Geçmiş listesindeki tek satır: platform logosu, site + yol + tarih/saat, sağda durum rozeti. */
class HistoryRow(c: Context) : LinearLayout(c) {
    private val badge = FrameLayout(c)
    private val sym = Ui.text(c, "", 14f, Color.WHITE, true)
    private val spin = ProgressBar(c)
    private val title = Ui.text(c, "", 15f, Ui.textClr(c), true)
    private val sub = Ui.text(c, "", 13f, Ui.mutedClr(c))
    private val time = Ui.text(c, "", 12f, Ui.mutedClr(c))
    private val chipHolder = FrameLayout(c)
    private var chip: TextView? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val p = Ui.dp(c, 14)
        setPadding(p, p, p, p)
        val r = Ui.dp(c, 16).toFloat()
        background = Ui.ripple(Ui.shape(Ui.cardBg(c), r, Ui.line(c), Ui.dp(c, 1)), r)

        val bs = Ui.dp(c, 40)
        sym.gravity = Gravity.CENTER
        badge.addView(sym, FrameLayout.LayoutParams(bs, bs))
        val ps = Ui.dp(c, 22)
        spin.isIndeterminate = true
        spin.indeterminateTintList = ColorStateList.valueOf(Ui.primary(c))
        badge.addView(spin, FrameLayout.LayoutParams(ps, ps, Gravity.CENTER))
        addView(badge, Ui.lp(bs, bs))

        title.isSingleLine = true
        title.ellipsize = TextUtils.TruncateAt.END
        sub.isSingleLine = true
        sub.ellipsize = TextUtils.TruncateAt.MIDDLE
        time.isSingleLine = true

        val col = LinearLayout(c).apply {
            orientation = VERTICAL
            addView(title)
            addView(sub, Ui.lp(Ui.WRAP, Ui.WRAP, t = Ui.dp(c, 2)))
            addView(time, Ui.lp(Ui.WRAP, Ui.WRAP, t = Ui.dp(c, 2)))
        }
        addView(col, Ui.lp(0, Ui.WRAP, 1f, l = Ui.dp(c, 12), r = Ui.dp(c, 8)))
        addView(chipHolder, Ui.lp(Ui.WRAP, Ui.WRAP))
    }

    private data class PlatformInfo(val symbol: String, val bgColor: Int, val textColor: Int)

    private fun getPlatformInfo(context: Context, url: String): PlatformInfo {
        val lower = url.lowercase()
        return when {
            lower.contains("instagram.com") || lower.contains("instagr.am") ->
                PlatformInfo("IG", Color.parseColor("#E1306C"), Color.WHITE)
            lower.contains("x.com") || lower.contains("twitter.com") || lower.contains("t.co") ->
                PlatformInfo("𝕏", Color.parseColor("#1DA1F2"), Color.WHITE)
            lower.contains("tiktok.com") || lower.contains("musical.ly") ->
                PlatformInfo("♪", Color.parseColor("#161823"), Color.WHITE)
            lower.contains("youtube.com") || lower.contains("youtu.be") ->
                PlatformInfo("▶", Color.parseColor("#FF0000"), Color.WHITE)
            else ->
                PlatformInfo("🌐", Ui.primaryBg(context), Ui.primary(context))
        }
    }

    private fun formatTimestamp(timeMs: Long): String {
        val t = if (timeMs <= 0) System.currentTimeMillis() else timeMs
        val date = Date(t)
        val calNow = Calendar.getInstance()
        val calDate = Calendar.getInstance().apply { time = date }

        val sameDay = calNow.get(Calendar.YEAR) == calDate.get(Calendar.YEAR) &&
                      calNow.get(Calendar.DAY_OF_YEAR) == calDate.get(Calendar.DAY_OF_YEAR)

        calNow.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = calNow.get(Calendar.YEAR) == calDate.get(Calendar.YEAR) &&
                        calNow.get(Calendar.DAY_OF_YEAR) == calDate.get(Calendar.DAY_OF_YEAR)

        val timeStr = SimpleDateFormat("HH:mm", Locale("tr")).format(date)
        return when {
            sameDay -> "Bugün $timeStr"
            yesterday -> "Dün $timeStr"
            else -> SimpleDateFormat("d MMM, HH:mm", Locale("tr")).format(date)
        }
    }

    fun bind(h: JSONObject) {
        val c = context
        val url = h.optString("url", "")
        val status = h.optString("status", "")
        val timestamp = h.optLong("timestamp", 0L).takeIf { it > 0 } ?: h.optLong("id", System.currentTimeMillis())

        val uri = try { Uri.parse(url) } catch (_: Exception) { null }
        val host = (uri?.host ?: url).removePrefix("www.")
        val path = (uri?.path ?: "").trimEnd('/')
        title.text = host
        title.setTextColor(Ui.textClr(c))
        sub.text = if (path.isEmpty()) url else path
        sub.setTextColor(Ui.mutedClr(c))
        time.text = formatTimestamp(timestamp)
        time.setTextColor(Ui.mutedClr(c))

        val kind = when {
            status.startsWith("✓") || status.contains("dosya") -> 1
            status.startsWith("✗") || status.contains("Başarısız") || status.contains("Hata") -> 2
            status == "Sırada" || status == "İndiriliyor" -> 0
            else -> 1
        }
        val fg = when (kind) { 1 -> Ui.OK; 2 -> Ui.ERR; else -> Ui.primary(c) }
        val bg = when (kind) { 1 -> Ui.OK_BG; 2 -> Ui.ERR_BG; else -> Ui.primaryBg(c) }

        val platform = getPlatformInfo(c, url)
        badge.background = Ui.shape(platform.bgColor, Ui.dp(c, 20).toFloat())
        sym.text = platform.symbol
        sym.setTextColor(platform.textColor)

        val isInProgress = status == "Sırada" || status == "İndiriliyor"
        spin.visibility = if (isInProgress) VISIBLE else GONE
        sym.visibility = if (isInProgress) GONE else VISIBLE

        val label = when {
            status.startsWith("✓") || status.contains("dosya") -> Regex("\\d+").find(status)?.value?.let { "$it dosya" } ?: "Tamam"
            status.startsWith("✗") || status.contains("Başarısız") || status.contains("Hata") -> "Başarısız"
            status == "Sırada" || status == "İndiriliyor" -> status
            else -> status
        }
        chipHolder.removeAllViews()
        chip = Ui.pill(c, label, fg, bg).also { chipHolder.addView(it) }
    }
}
