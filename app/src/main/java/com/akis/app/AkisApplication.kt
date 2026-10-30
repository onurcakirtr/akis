package com.akis.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class AkisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        WatchHistory.init(this)
        DeviceAccess.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVER, "Yayın durumu", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Akış sunucusu çalışırken gösterilir"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ACCESS, "Cihaz onayı", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Yeni bir cihaz bağlanmak istediğinde sorulur"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FILES, "Gelen dosyalar", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Web arayüzünden dosya yüklendiğinde"
            }
        )
    }

    companion object {
        const val CHANNEL_SERVER = "server"
        const val CHANNEL_ACCESS = "access"
        const val CHANNEL_FILES = "files"
    }
}
