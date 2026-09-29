package com.example.gdl

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val url = text?.let { Regex("https?://\\S+").find(it)?.value }
        if (url == null) {
            Toast.makeText(this, "Link bulunamadı", Toast.LENGTH_SHORT).show()
        } else {
            val i = Intent(this, DownloadService::class.java)
            i.putExtra(DownloadService.EXTRA_URL, url)
            startForegroundService(i)
            Toast.makeText(this, "İndirme başladı", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
