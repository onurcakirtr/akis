package com.akis.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.service.quicksettings.TileService
import com.akis.app.cast.Remote
import com.akis.app.service.ServerTileService
import com.akis.app.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class AkisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        WatchHistory.init(this)
        DeviceAccess.init(this)
        com.akis.app.cast.Remote.init(this)
        watchState()
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

    /** Sunucu / TV durumu değişince widget'ları ve hızlı ayar kutucuğunu tazeler (ilerleme 5 sn'de bir). */
    private fun watchState() {
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            combine(ServerState.running, ServerState.clients, ServerState.streams, Remote.state, Prefs.settings) { run, c, s, rs, set ->
                val st = s.firstOrNull()
                listOf(
                    run, c.size, ServerState.address.value, st?.title, st?.let { if (it.size > 0) it.position * 20 / it.size else 0 },
                    rs.title, rs.playing, rs.buffering, rs.hasMedia, rs.device?.name, rs.positionMs / 5000,
                    set.widget, set.notifDetails, set.cast,
                )
            }.distinctUntilChanged().collect {
                Widgets.refresh(this@AkisApplication)
                TileService.requestListeningState(this@AkisApplication, ComponentName(this@AkisApplication, ServerTileService::class.java))
            }
        }
    }

    companion object {
        const val CHANNEL_SERVER = "server"
        const val CHANNEL_ACCESS = "access"
        const val CHANNEL_FILES = "files"
    }
}
