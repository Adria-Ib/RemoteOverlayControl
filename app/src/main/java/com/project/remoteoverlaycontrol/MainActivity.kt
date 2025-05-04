package com.project.remoteoverlaycontrol

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val overlayDelayMillis = 10_000L // 10 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch overlay after delay
        CoroutineScope(Dispatchers.Main).launch {
            delay(overlayDelayMillis)
            val intent = Intent(this@MainActivity, OverlayService::class.java)
            startService(intent)
            finish()
        }
    }
}