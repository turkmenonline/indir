package com.example.gdl

import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.json.JSONObject

/** Geçmiş listesindeki tek satır: durum ikonu, site + yol, sağda durum rozeti. */
class HistoryRow(c: Context) : LinearLayout(c) {
    private val badge = FrameLayout(c)
    private val sym = Ui.text(c, "", 18f, Ui.OK, true)
    private val spin = ProgressBar(c)
    private val title = Ui.text(c, "", 15f, Ui.textClr(c), true)
    private val sub = Ui.text(c, "", 13f, Ui.mutedClr(c))
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
        val col = LinearLayout(c).apply {
            orientation = VERTICAL
            addView(title)
            addView(sub, Ui.lp(Ui.WRAP, Ui.WRAP, t = Ui.dp(c, 2)))
        }
        addView(col, Ui.lp(0, Ui.WRAP, 1f, l = Ui.dp(c, 12), r = Ui.dp(c, 8)))
        addView(chipHolder, Ui.lp(Ui.WRAP, Ui.WRAP))
    }

    fun bind(h: JSONObject) {
        val c = context
        val url = h.getString("url")
        val status = h.getString("status")

        val uri = try { Uri.parse(url) } catch (_: Exception) { null }
        val host = (uri?.host ?: url).removePrefix("www.")
        val path = (uri?.path ?: "").trimEnd('/')
        title.text = host
        title.setTextColor(Ui.textClr(c))
        sub.text = if (path.isEmpty()) url else path
        sub.setTextColor(Ui.mutedClr(c))

        val kind = when {
            status.startsWith("✓") -> 1
            status.startsWith("✗") -> 2
            else -> 0
        }
        val fg = when (kind) { 1 -> Ui.OK; 2 -> Ui.ERR; else -> Ui.primary(c) }
        val bg = when (kind) { 1 -> Ui.OK_BG; 2 -> Ui.ERR_BG; else -> Ui.primaryBg(c) }

        badge.background = Ui.shape(bg, Ui.dp(c, 20).toFloat())
        sym.text = if (kind == 1) "✓" else "✕"
        sym.setTextColor(fg)
        sym.visibility = if (kind == 0) GONE else VISIBLE
        spin.visibility = if (kind == 0) VISIBLE else GONE

        val label = when (kind) {
            1 -> Regex("\\d+").find(status)?.value?.let { "$it dosya" } ?: "Tamam"
            2 -> "Başarısız"
            else -> status
        }
        chipHolder.removeAllViews()
        chip = Ui.pill(c, label, fg, bg).also { chipHolder.addView(it) }
    }
}
