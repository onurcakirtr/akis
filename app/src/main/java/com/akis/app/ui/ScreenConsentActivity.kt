package com.akis.app.ui

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.akis.app.ScreenStream
import com.akis.app.service.DlnaService

/** Görünmez ara ekran: Android'in ekran paylaşımı onay penceresini açar, sonucu servise iletir. */
class ScreenConsentActivity : AppCompatActivity() {
    private val ask = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) DlnaService.startProjection(this, r.resultCode, data)
        else ScreenStream.decline()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        if (!ScreenStream.allowed()) { finish(); return }
        ask.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }
}
