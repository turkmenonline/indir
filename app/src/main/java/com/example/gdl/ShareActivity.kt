package com.example.gdl

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
        val url = raw?.let { Regex("https?://\\S+").find(it)?.value }
        if (url != null) {
            val i = Intent(this, DownloadService::class.java).apply {
                putExtra(DownloadService.EXTRA_URL, url)
            }
            startForegroundService(i)
            Toast.makeText(this, "İndirme başladı", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Geçerli bağlantı bulunamadı", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
