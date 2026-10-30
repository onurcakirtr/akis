package com.akis.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.akis.app.AkisApplication
import com.akis.app.Lang
import com.akis.app.Prefs
import com.akis.app.R
import com.akis.app.ServerState
import com.akis.app.dlna.DlnaServer
import com.akis.app.net.NetUtil
import com.akis.app.ui.MainActivity
import com.akis.app.ui.formatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.BindException

class DlnaService : Service() {

    companion object {
        const val ACTION_START = "com.akis.app.START"
        const val ACTION_STOP = "com.akis.app.STOP"
        const val ACTION_RESTART = "com.akis.app.RESTART"
        private const val NOTIF_ID = 42

        fun start(ctx: Context) =
            ContextCompat.startForegroundService(ctx, Intent(ctx, DlnaService::class.java).setAction(ACTION_START))

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, DlnaService::class.java).setAction(ACTION_STOP))
        }

        fun restart(ctx: Context) =
            ContextCompat.startForegroundService(ctx, Intent(ctx, DlnaService::class.java).setAction(ACTION_RESTART))
    }

    private var server: DlnaServer? = null
    private var scope: CoroutineScope? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private val main = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { refreshAddress() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_RESTART -> {
                goForeground()
                shutdown(waitForStop = true)
                startServer()
            }
            else -> {
                goForeground()
                if (server == null) startServer()
            }
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
    }

    private fun startServer() {
        Prefs.init(this)
        val settings = Prefs.current
        ServerState.reset()
        val srv = DlnaServer(applicationContext, settings.port, settings.uuid)
        val ip = NetUtil.findLanAddress(this)
        try {
            srv.start(ip)
        } catch (e: BindException) {
            failAndStop(Lang.s(R.string.err_port_in_use, settings.port))
            return
        } catch (e: Exception) {
            failAndStop(Lang.s(R.string.err_start, e.message ?: ""))
            return
        }
        server = srv
        ServerState.kicker = { ip -> srv.kick(ip) }
        if (ip == null) ServerState.error.value = Lang.s(R.string.err_no_wifi)
        acquireLocks(settings.keepAwake)
        registerNetworkCallback()
        ServerState.running.value = true
        ServerState.startedAt.value = System.currentTimeMillis()
        ServerState.log(Lang.s(R.string.log_started))

        val sc = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = sc
        sc.launch {
            combine(ServerState.address, ServerState.clients, ServerState.streams, Prefs.settings) { a, c, s, set ->
                val st = s.firstOrNull()
                listOf(
                    a, c.size, st?.let { "${it.title} → ${it.client}" },
                    if (set.notifDetails && st != null && st.size > 0) st.position * 50 / st.size else null,
                    set.notifDetails,
                )
            }.distinctUntilChanged().collect { updateNotification() }
        }
        sc.launch {
            while (isActive) {
                delay(3000)
                ServerState.prune()
            }
        }
        sc.launch { ServerState.accessChanged.collect { server?.contentChanged() } }
        // İçerik ayarları değişince istemcilere haber ver
        sc.launch {
            Prefs.settings.map {
                listOf(
                    it.shareVideo, it.shareAudio, it.shareImage, it.showRecent, it.folders, it.name,
                    it.hideFolders, it.hiddenBuckets, it.subtitles, it.subLang, it.search, it.history, it.historyMarks,
                    it.allFilesUpnp, it.iconStyle,
                )
            }
                .distinctUntilChanged().drop(1).collect { server?.contentChanged() }
        }
    }

    private fun failAndStop(msg: String) {
        ServerState.error.value = msg
        ServerState.log(msg)
        ServerState.running.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun refreshAddress() {
        val ip = NetUtil.findLanAddress(this)
        server?.setAddress(ip)
        ServerState.error.value = if (ip == null) Lang.s(R.string.err_wifi_lost) else null
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            private fun poke() {
                main.removeCallbacks(refreshRunnable)
                main.postDelayed(refreshRunnable, 1200)
            }
            override fun onAvailable(network: Network) = poke()
            override fun onLost(network: Network) = poke()
            override fun onLinkPropertiesChanged(network: Network, lp: android.net.LinkProperties) = poke()
        }
        val req = NetworkRequest.Builder()
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(req, cb)
            netCallback = cb
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks(keepAwake: Boolean) {
        val wm = applicationContext.getSystemService(WifiManager::class.java)
        multicastLock = wm.createMulticastLock("akis:ssdp").apply {
            setReferenceCounted(false)
            acquire()
        }
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "akis:wifi").apply {
            setReferenceCounted(false)
            acquire()
        }
        if (keepAwake) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "akis:cpu").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        try { multicastLock?.release() } catch (_: Exception) {}
        try { wifiLock?.release() } catch (_: Exception) {}
        try { wakeLock?.release() } catch (_: Exception) {}
        multicastLock = null
        wifiLock = null
        wakeLock = null
    }

    private fun shutdown(waitForStop: Boolean = false) {
        scope?.cancel()
        scope = null
        main.removeCallbacks(refreshRunnable)
        netCallback?.let {
            try { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        netCallback = null
        val srv = server
        server = null
        ServerState.kicker = null
        if (srv != null) {
            val t = Thread { srv.stop() }
            t.start()
            if (waitForStop) t.join(2000) // aynı portu yeniden açabilmek için kapanmasını bekle
            ServerState.log(Lang.s(R.string.log_stopped))
        }
        releaseLocks()
        ServerState.running.value = false
        ServerState.address.value = null
        ServerState.streams.value = emptyList()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    // ---------------- Bildirim ----------------

    private fun buildNotification(): Notification {
        val s = Prefs.current
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, DlnaService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val addr = ServerState.address.value
        val stream = ServerState.streams.value.firstOrNull()
        val clients = ServerState.clients.value.size
        val title = if (stream != null) "▶ ${stream.title}" else Lang.s(R.string.notif_on)
        val text = when {
            stream != null -> Lang.s(R.string.notif_playing_on, stream.client)
            addr != null -> "${s.name} • $addr" + if (clients > 0) " • " + Lang.q(R.plurals.count_devices, clients) else ""
            else -> Lang.s(R.string.waiting_network)
        }
        val b = NotificationCompat.Builder(this, AkisApplication.CHANNEL_SERVER)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(0xFFD71921.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, Lang.s(R.string.stop), stop)
        if (s.notifDetails) {
            if (stream != null && stream.size > 0) b.setProgress(1000, (stream.position * 1000 / stream.size).toInt(), false)
            if (addr != null) b.setSubText(Lang.s(R.string.notif_sent, Lang.q(R.plurals.count_devices, clients), formatBytes(ServerState.bytesSent.value)))
        }
        return b.build()
    }

    private fun updateNotification() {
        if (server == null) return
        val nm = getSystemService(android.app.NotificationManager::class.java)
        try { nm.notify(NOTIF_ID, buildNotification()) } catch (_: SecurityException) {}
    }
}
