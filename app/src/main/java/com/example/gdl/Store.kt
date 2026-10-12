package com.example.gdl

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Store {
    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    const val QUALITY_BEST = "best"
    const val QUALITY_2160P = "2160p"
    const val QUALITY_1440P = "1440p"
    const val QUALITY_1080P = "1080p"
    const val QUALITY_720P = "720p"
    const val QUALITY_360P = "360p"
    const val QUALITY_AUDIO_ONLY = "audio_only"

    /** Hem Ayarlar'daki varsayılan seçici hem de İndir ekranındaki tek seferlik
     * seçici bu listeyi kullanır; sıralama görünüm sırasıdır. */
    val QUALITY_OPTIONS: List<Pair<String, String>> = listOf(
        "En iyi" to QUALITY_BEST,
        "2160p" to QUALITY_2160P,
        "1440p" to QUALITY_1440P,
        "1080p" to QUALITY_1080P,
        "720p" to QUALITY_720P,
        "360p" to QUALITY_360P,
        "Ses" to QUALITY_AUDIO_ONLY
    )

    fun qualityLabel(value: String): String =
        QUALITY_OPTIONS.firstOrNull { it.second == value }?.first ?: "En iyi"

    /** Varsayılan kalite her zaman "en iyi"; İndir ekranından tek seferlik değiştirilir. */
    @Suppress("UNUSED_PARAMETER")
    fun videoQuality(c: Context): String = QUALITY_BEST

    // ---- Altyazı ----
    const val SUBTITLE_OFF = "off"
    const val SUBTITLE_TR = "tr"
    const val SUBTITLE_EN = "en"
    const val SUBTITLE_TR_EN = "tr,en"

    val SUBTITLE_OPTIONS: List<Pair<String, String>> = listOf(
        "Kapalı" to SUBTITLE_OFF,
        "Türkçe" to SUBTITLE_TR,
        "English" to SUBTITLE_EN,
        "Türkçe + English" to SUBTITLE_TR_EN
    )

    fun subtitleLabel(value: String): String =
        SUBTITLE_OPTIONS.firstOrNull { it.second == value }?.first ?: "Kapalı"

    fun subtitleLang(c: Context): String = prefs(c).getString("subtitle_lang", SUBTITLE_OFF) ?: SUBTITLE_OFF
    fun setSubtitleLang(c: Context, v: String) {
        prefs(c).edit().putString("subtitle_lang", v).apply()
    }

    /** true: altyazı videonun içine gömülür (oynatıcıda aç/kapa). false: ayrı .srt dosyası. */
    fun subtitleEmbedded(c: Context): Boolean = prefs(c).getBoolean("subtitle_embedded", true)
    fun setSubtitleEmbedded(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("subtitle_embedded", v).apply()
    }

    /** true: video indirilirken kapak fotoğrafı da galeriye ayrı bir görsel olarak kaydedilir. */
    fun saveCover(c: Context): Boolean = prefs(c).getBoolean("save_cover", false)
    fun setSaveCover(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("save_cover", v).apply()
    }

    private fun prefs(c: Context) = c.getSharedPreferences("gdl", Context.MODE_PRIVATE)

    fun themeMode(c: Context): Int = prefs(c).getInt("theme_mode", THEME_SYSTEM)

    fun setThemeMode(c: Context, mode: Int) {
        prefs(c).edit().putInt("theme_mode", mode).apply()
    }

    fun applyTheme(c: Context) {
        val mode = when (themeMode(c)) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun album(c: Context): String = prefs(c).getString("album", "GalleryDl") ?: "GalleryDl"

    fun setAlbum(c: Context, v: String) {
        val name = v.trim().ifEmpty { "GalleryDl" }
        prefs(c).edit().putString("album", name).apply()
    }

    fun photoTemplate(c: Context): String {
        val t = prefs(c).getString("template", "") ?: ""
        return if (t.isEmpty()) "{username|author[name]|category}_{tweet_id|post_shortcode|id}_{num}.{extension}" else t
    }

    fun rawPhotoTemplate(c: Context): String = prefs(c).getString("template", "") ?: ""

    fun setPhotoTemplate(c: Context, v: String) = setGalleryTemplate(c, v)

    fun galleryTemplate(c: Context): String = photoTemplate(c)

    fun setGalleryTemplate(c: Context, v: String) {
        val t = v.trim()
        if (t.contains("/") || t.contains("\\")) {
            throw IllegalArgumentException("Şablon dosya adı içinde klasör ayırıcı (/ ve \\) içeremez")
        }
        prefs(c).edit().putString("template", t).apply()
    }

    fun template(c: Context): String = galleryTemplate(c)

    fun setTemplate(c: Context, v: String) = setGalleryTemplate(c, v)

    fun videoTemplate(c: Context): String {
        val t = prefs(c).getString("ytdlp_template", "") ?: ""
        return if (t.isEmpty()) "%(title).200B [%(id)s].%(ext)s" else t
    }

    fun rawYtdlpTemplate(c: Context): String = prefs(c).getString("ytdlp_template", "") ?: ""

    fun setYtdlpTemplate(c: Context, v: String) {
        val t = v.trim()
        if (t.contains("/") || t.contains("\\")) {
            throw IllegalArgumentException("Şablon dosya adı içinde klasör ayırıcı (/ ve \\) içeremez")
        }
        prefs(c).edit().putString("ytdlp_template", t).apply()
    }

    fun ytdlpTemplate(c: Context): String = videoTemplate(c)

    fun setVideoTemplate(c: Context, v: String) = setYtdlpTemplate(c, v)

    @Synchronized
    fun history(c: Context): List<JSONObject> {
        val arr = JSONArray(prefs(c).getString("history", "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    @Synchronized
    fun cleanupPendingDownloads(c: Context) {
        val list = history(c).toMutableList()
        var changed = false
        for (i in 0 until list.size) {
            val item = list[i]
            val status = item.optString("status", "")
            if (status == "İndiriliyor" || status == "Sırada") {
                item.put("status", "Yarıda kaldı")
                changed = true
            }
        }
        if (changed) {
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            prefs(c).edit().putString("history", arr.toString()).apply()
        }
    }

    @Synchronized
    fun upsert(c: Context, id: Long, url: String, status: String, log: String) {
        val list = history(c).toMutableList()
        val o = JSONObject()
        o.put("id", id)
        o.put("url", url)
        o.put("status", status)
        o.put("log", log)
        val idx = list.indexOfFirst { it.getLong("id") == id }
        val timestamp = if (idx >= 0 && list[idx].has("timestamp")) {
            list[idx].getLong("timestamp")
        } else {
            id
        }
        o.put("timestamp", timestamp)
        if (idx >= 0) list[idx] = o else list.add(0, o)
        val arr = JSONArray()
        list.take(50).forEach { arr.put(it) }
        prefs(c).edit().putString("history", arr.toString()).apply()
    }

    fun getPlatformName(url: String): String {
        val host = try {
            url.toUri().host ?: url
        } catch (_: Exception) {
            url
        }.lowercase().removePrefix("www.")

        return when {
            host.contains("instagram") -> "Instagram"
            host.contains("youtube") || host.contains("youtu.be") -> "YouTube"
            host.contains("tiktok") -> "TikTok"
            host.contains("twitter") || host.contains("x.com") -> "X"
            host.contains("pinterest") -> "Pinterest"
            host.contains("reddit") -> "Reddit"
            host.contains("facebook") || host.contains("fb.watch") -> "Facebook"
            host.contains("twitch") -> "Twitch"
            host.contains("spotify") -> "Spotify"
            host.contains("soundcloud") -> "SoundCloud"
            host.contains("vimeo") -> "Vimeo"
            else -> {
                val parts = host.split(".")
                val name = if (parts.size >= 2) parts[parts.size - 2] else parts[0]
                name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
    }

    fun getPlatformIconRes(platform: String): Int {
        return when (platform.lowercase()) {
            "instagram" -> R.drawable.ic_platform_instagram
            "youtube" -> R.drawable.ic_platform_youtube
            "tiktok" -> R.drawable.ic_platform_tiktok
            "x", "twitter" -> R.drawable.ic_platform_x
            else -> R.drawable.ic_platform_web
        }
    }

    fun formatTimestamp(timeMs: Long): String {
        if (timeMs <= 0L) return ""
        val date = Date(timeMs)
        val calendarNow = Calendar.getInstance()
        val calendarTarget = Calendar.getInstance().apply { time = date }

        val timeFormat = SimpleDateFormat("HH:mm", Locale("tr", "TR"))
        val timeStr = timeFormat.format(date)

        val isSameDay = calendarNow.get(Calendar.YEAR) == calendarTarget.get(Calendar.YEAR) &&
                        calendarNow.get(Calendar.DAY_OF_YEAR) == calendarTarget.get(Calendar.DAY_OF_YEAR)

        if (isSameDay) {
            return "Bugün $timeStr"
        }

        val calendarYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = calendarYesterday.get(Calendar.YEAR) == calendarTarget.get(Calendar.YEAR) &&
                          calendarYesterday.get(Calendar.DAY_OF_YEAR) == calendarTarget.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) {
            return "Dün $timeStr"
        }

        val dateFormat = if (calendarNow.get(Calendar.YEAR) == calendarTarget.get(Calendar.YEAR)) {
            SimpleDateFormat("d MMM, HH:mm", Locale("tr", "TR"))
        } else {
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale("tr", "TR"))
        }
        return dateFormat.format(date)
    }

    @Synchronized
    fun clearHistory(c: Context) {
        prefs(c).edit().remove("history").apply()
    }

    const val CHANNEL_STABLE = "Stable"
    const val CHANNEL_NIGHTLY = "Nightly"

    const val AUTO_UPDATE_DISABLED = 0
    const val AUTO_UPDATE_DAILY = 1
    const val AUTO_UPDATE_WEEKLY = 2
    const val AUTO_UPDATE_MONTHLY = 3

    fun updateChannel(c: Context): String = prefs(c).getString("update_channel", CHANNEL_STABLE) ?: CHANNEL_STABLE

    fun setUpdateChannel(c: Context, v: String) {
        prefs(c).edit().putString("update_channel", v).apply()
    }

    fun autoUpdatePeriod(c: Context): Int = prefs(c).getInt("auto_update_period", AUTO_UPDATE_DISABLED)

    fun setAutoUpdatePeriod(c: Context, period: Int) {
        prefs(c).edit().putInt("auto_update_period", period).apply()
    }

    fun lastUpdateCheck(c: Context): Long = prefs(c).getLong("last_update_check", 0L)

    fun setLastUpdateCheck(c: Context, timeMs: Long) {
        prefs(c).edit().putLong("last_update_check", timeMs).apply()
    }

    fun cachedVersions(c: Context): String = prefs(c).getString("cached_versions", "Yükleniyor…") ?: "Yükleniyor…"

    fun setCachedVersions(c: Context, v: String) {
        prefs(c).edit().putString("cached_versions", v).apply()
    }

    fun shouldAutoUpdate(c: Context): Boolean {
        val period = autoUpdatePeriod(c)
        if (period == AUTO_UPDATE_DISABLED) return false
        val last = lastUpdateCheck(c)
        val now = System.currentTimeMillis()
        val intervalMs = when (period) {
            AUTO_UPDATE_DAILY -> 24 * 3600 * 1000L
            AUTO_UPDATE_WEEKLY -> 7 * 24 * 3600 * 1000L
            AUTO_UPDATE_MONTHLY -> 30 * 24 * 3600 * 1000L
            else -> return false
        }
        return (now - last) >= intervalMs
    }

    fun cleanCookieDomain(rawDomain: String): String {
        var d = rawDomain.trim().lowercase()
        if (d.startsWith("#httponly_")) {
            d = d.substring("#httponly_".length)
        }
        while (d.startsWith(".")) {
            d = d.substring(1)
        }
        if (d.startsWith("www.")) {
            d = d.substring(4)
        }
        return d
    }

    fun parseCookieKey(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("#") && !trimmed.startsWith("#HttpOnly_", ignoreCase = true)) return null

        val parts = if (trimmed.contains("\t")) {
            trimmed.split("\t")
        } else {
            trimmed.split(Regex("\\s+"))
        }

        if (parts.size >= 6) {
            val domain = cleanCookieDomain(parts[0])
            val path = parts[2].trim().ifEmpty { "/" }
            val name = parts[5].trim()
            if (domain.isNotEmpty() && name.isNotEmpty()) {
                return "$domain|$path|$name"
            }
        } else if (parts.size >= 2) {
            val domain = cleanCookieDomain(parts[0])
            val name = parts.last().trim()
            if (domain.isNotEmpty() && name.isNotEmpty()) {
                return "$domain|/|$name"
            }
        }
        return null
    }

    fun mergeCookies(existingText: String, newText: String): String {
        val cookieMap = LinkedHashMap<String, String>()

        fun processText(text: String) {
            text.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEach
                val key = parseCookieKey(trimmed)
                if (key != null) {
                    cookieMap[key] = trimmed
                }
            }
        }

        processText(existingText)
        processText(newText)

        if (cookieMap.isEmpty()) {
            var cleanNew = newText.trim()
            if (cleanNew.isNotEmpty() && !cleanNew.startsWith("# Netscape")) {
                cleanNew = "# Netscape HTTP Cookie File\n" + cleanNew
            }
            return cleanNew
        }

        val sb = StringBuilder()
        sb.append("# Netscape HTTP Cookie File\n")
        cookieMap.values.forEach { cookieLine ->
            sb.append(cookieLine).append("\n")
        }
        return sb.toString()
    }

    fun parseCookieDomains(rawCookieText: String): List<String> {
        val domains = LinkedHashSet<String>()
        rawCookieText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            if (trimmed.startsWith("#") && !trimmed.startsWith("#HttpOnly_", ignoreCase = true)) return@forEach

            val parts = if (trimmed.contains("\t")) {
                trimmed.split("\t")
            } else {
                trimmed.split(Regex("\\s+"))
            }

            if (parts.isNotEmpty()) {
                val domain = cleanCookieDomain(parts[0])
                if (domain.contains(".") && domain.length >= 3) {
                    domains.add(domain)
                }
            }
        }
        return domains.toList()
    }

    fun isCookieEnabled(c: Context, domain: String): Boolean {
        return prefs(c).getBoolean("cookie_domain_$domain", true)
    }

    fun setCookieEnabled(c: Context, domain: String, enabled: Boolean) {
        prefs(c).edit().putBoolean("cookie_domain_$domain", enabled).apply()
    }

    fun isCookieDomainEnabled(c: Context, domain: String): Boolean = isCookieEnabled(c, domain)
    fun setCookieDomainEnabled(c: Context, domain: String, enabled: Boolean) = setCookieEnabled(c, domain, enabled)

    fun getCookieDisplayName(domain: String): String {
        val d = domain.lowercase()
        return when {
            d.contains("instagram") -> "Instagram"
            d.contains("youtube") || d.contains("youtu.be") -> "YouTube"
            d.contains("tiktok") -> "TikTok"
            d.contains("twitter") || d.contains("x.com") -> "X"
            d.contains("pinterest") -> "Pinterest"
            d.contains("reddit") -> "Reddit"
            d.contains("facebook") || d.contains("fb.watch") -> "Facebook"
            d.contains("twitch") -> "Twitch"
            d.contains("spotify") -> "Spotify"
            d.contains("soundcloud") -> "SoundCloud"
            d.contains("vimeo") -> "Vimeo"
            else -> {
                val parts = d.split(".")
                val name = if (parts.size >= 2) parts[parts.size - 2] else parts[0]
                name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
    }
}
