package com.akis.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.service.quicksettings.TileService
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
        Lang.init(this)
        WatchHistory.init(this)
        DeviceAccess.init(this)
        watchState()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVER, Lang.s(R.string.ch_server), NotificationManager.IMPORTANCE_LOW).apply {
                description = Lang.s(R.string.ch_server_desc)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ACCESS, Lang.s(R.string.f_approval), NotificationManager.IMPORTANCE_HIGH).apply {
                description = Lang.s(R.string.ch_access_desc)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FILES, Lang.s(R.string.ch_files), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = Lang.s(R.string.ch_files_desc)
            }
        )
    }

    /** Sunucu / TV durumu değişince widget'ları ve hızlı ayar kutucuğunu tazeler (ilerleme 5 sn'de bir). */
    private fun watchState() {
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            combine(ServerState.running, ServerState.clients, ServerState.streams, ServerState.address, Prefs.settings) { run, c, s, a, set ->
                val st = s.firstOrNull()
                listOf(
                    run, c.size, a, st?.title, st?.let { if (it.size > 0) it.position * 20 / it.size else 0 },
                    set.widget, set.notifDetails,
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
