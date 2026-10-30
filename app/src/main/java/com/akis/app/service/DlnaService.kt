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
import com.akis.app.Prefs
import com.akis.app.R
import com.akis.app.ServerState
import com.akis.app.dlna.DlnaServer
import com.akis.app.net.NetUtil
import com.akis.app.ui.MainActivity
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
            failAndStop("${settings.port} portu kullanımda. Ayarlardan başka bir port seç.")
            return
        } catch (e: Exception) {
            failAndStop("Sunucu başlatılamadı: ${e.message}")
            return
        }
        server = srv
        if (ip == null) ServerState.error.value = "Wi-Fi ağı bulunamadı. Wi-Fi'a bağlan ya da hotspot aç."
        acquireLocks(settings.keepAwake)
        registerNetworkCallback()
        ServerState.running.value = true
        ServerState.startedAt.value = System.currentTimeMillis()
        ServerState.log("Yayın başladı")

        val sc = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = sc
        sc.launch {
            combine(ServerState.address, ServerState.clients, ServerState.streams) { a, c, s ->
                Triple(a, c.size, s.firstOrNull()?.let { "${it.title} → ${it.client}" })
            }.distinctUntilChanged().collect { updateNotification() }
        }
        sc.launch {
            while (isActive) {
                delay(3000)
                ServerState.prune()
            }
        }
        // İçerik ayarları değişince istemcilere haber ver
        sc.launch {
            Prefs.settings.map {
                listOf(
                    it.shareVideo, it.shareAudio, it.shareImage, it.showRecent, it.folders, it.name,
                    it.hideFolders, it.hiddenBuckets, it.subtitles, it.subLang, it.search, it.history, it.historyMarks,
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
        ServerState.error.value = if (ip == null) "Wi-Fi bağlantısı koptu, yeniden bağlanınca devam edecek." else null
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
        if (srv != null) {
            val t = Thread { srv.stop() }
            t.start()
            if (waitForStop) t.join(2000) // aynı portu yeniden açabilmek için kapanmasını bekle
            ServerState.log("Yayın durdu")
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
        val title = if (stream != null) "▶ ${stream.title}" else "Akış yayında"
        val text = when {
            stream != null -> "${stream.client} cihazında oynatılıyor"
            addr != null -> "${Prefs.current.name} • $addr" + if (clients > 0) " • $clients cihaz" else ""
            else -> "Ağ bekleniyor…"
        }
        return NotificationCompat.Builder(this, AkisApplication.CHANNEL_SERVER)
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
            .addAction(0, "Durdur", stop)
            .build()
    }

    private fun updateNotification() {
        if (server == null) return
        val nm = getSystemService(android.app.NotificationManager::class.java)
        try { nm.notify(NOTIF_ID, buildNotification()) } catch (_: SecurityException) {}
    }
}
