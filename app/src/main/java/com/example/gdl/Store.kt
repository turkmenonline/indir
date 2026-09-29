package com.example.gdl

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("gdl", Context.MODE_PRIVATE)

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
}
