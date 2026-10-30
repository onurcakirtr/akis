package com.akis.app.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.akis.app.Prefs
import com.akis.app.Lang
import com.akis.app.R
import com.akis.app.ServerState
import kotlinx.coroutines.flow.combine
import com.akis.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Bildirim panelindeki hızlı ayar kutucuğu: tek dokunuşla yayını aç/kapat. */
class ServerTileService : TileService() {
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job = CoroutineScope(Dispatchers.Main).launch {
            combine(ServerState.running, ServerState.streams, ServerState.clients) { on, _, _ -> on }.collect { render(it) }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (ServerState.running.value) {
            DlnaService.stop(this)
            return
        }
        try {
            DlnaService.start(this)
        } catch (e: Exception) {
            // Bazı sürümler arka plandan servis başlatmaya izin vermez: uygulamayı açıp oradan başlat
            val i = Intent(this, MainActivity::class.java)
                .setAction(DlnaService.ACTION_START)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(i)
            }
        }
    }

    private fun render(on: Boolean) {
        val t = qsTile ?: return
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Akış"
        val stream = ServerState.streams.value.firstOrNull()
        val clients = ServerState.clients.value.size
        t.subtitle = when {
            !on -> Lang.s(R.string.status_off)
            !Prefs.current.notifDetails -> Lang.s(R.string.status_on)
            stream != null -> "▶ ${stream.title}"
            clients > 0 -> Lang.s(R.string.status_on_devices, Lang.q(R.plurals.count_devices, clients))
            else -> Lang.s(R.string.status_on)
        }
        t.updateTile()
    }
}
