package com.akis.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class AkisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVER, "Yayın durumu", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Akış sunucusu çalışırken gösterilir"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CHANNEL_SERVER = "server"
    }
}
