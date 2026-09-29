package com.example.gdl

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONArray
import org.json.JSONObject

object Store {
    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

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

    fun template(c: Context): String = prefs(c).getString("template", "") ?: ""

    fun setTemplate(c: Context, v: String) {
        prefs(c).edit().putString("template", v.trim()).apply()
    }

    @Synchronized
    fun history(c: Context): List<JSONObject> {
        val arr = JSONArray(prefs(c).getString("history", "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
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
        if (idx >= 0) list[idx] = o else list.add(0, o)
        val arr = JSONArray()
        list.take(50).forEach { arr.put(it) }
        prefs(c).edit().putString("history", arr.toString()).apply()
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
}
