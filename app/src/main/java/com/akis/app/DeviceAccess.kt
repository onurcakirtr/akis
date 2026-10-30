package com.akis.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest

data class KnownDevice(val ip: String, val name: String, val allowed: Boolean)

/**
 * Cihaz onayı ve web PIN'i.
 *  - Onay açıkken tanınmayan cihaz içerik göremez; telefona "İzin ver / Engelle" bildirimi gelir
 *  - PIN doğru girilen tarayıcı çerezle hatırlanır ve onaylı sayılır
 */
object DeviceAccess {
    const val ACTION_ALLOW = "com.akis.app.ACCESS_ALLOW"
    const val ACTION_BLOCK = "com.akis.app.ACCESS_BLOCK"
    const val EXTRA_IP = "ip"
    const val EXTRA_NAME = "name"

    private lateinit var app: Context
    private lateinit var sp: SharedPreferences
    private val _devices = MutableStateFlow<List<KnownDevice>>(emptyList())
    val devices: StateFlow<List<KnownDevice>> get() = _devices

    /** Bildirimi gösterilmiş, cevap bekleyen cihazlar (ip → zaman). */
    private val pending = HashMap<String, Long>()
    /** PIN deneme sayacı (ip → (yanlış sayısı, son deneme)). */
    private val attempts = HashMap<String, Pair<Int, Long>>()

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        app = ctx.applicationContext
        sp = ctx.getSharedPreferences("access", Context.MODE_PRIVATE)
        publish()
    }

    private fun read(key: String): Map<String, String> =
        (sp.getStringSet(key, emptySet()) ?: emptySet()).associate { it.substringBefore('\t') to it.substringAfter('\t', "") }

    private fun write(key: String, map: Map<String, String>) {
        sp.edit().putStringSet(key, map.map { "${it.key}\t${it.value}" }.toSet()).apply()
    }

    private fun publish() {
        _devices.value = read("allowed").map { KnownDevice(it.key, it.value, true) } +
            read("blocked").map { KnownDevice(it.key, it.value, false) }
    }

    fun isAllowed(ip: String) = read("allowed").containsKey(ip)
    fun isBlocked(ip: String) = read("blocked").containsKey(ip)

    fun allow(ip: String, name: String) {
        if (isAllowed(ip) && !isBlocked(ip)) return
        write("allowed", read("allowed") + (ip to name))
        write("blocked", read("blocked") - ip)
        synchronized(pending) { pending.remove(ip) }
        cancelNotification(ip)
        publish()
        ServerState.log("✓ İzin verildi: $name ($ip)")
        ServerState.accessChanged.tryEmit(Unit)
    }

    fun block(ip: String, name: String) {
        write("blocked", read("blocked") + (ip to name))
        write("allowed", read("allowed") - ip)
        synchronized(pending) { pending.remove(ip) }
        cancelNotification(ip)
        publish()
        ServerState.log("⛔ Engellendi: $name ($ip)")
    }

    fun forget(ip: String) {
        write("allowed", read("allowed") - ip)
        write("blocked", read("blocked") - ip)
        publish()
    }

    /** Tanınmayan cihaz için (en fazla dakikada bir) onay bildirimi gösterir. */
    fun request(ip: String, name: String) {
        val now = System.currentTimeMillis()
        synchronized(pending) {
            val last = pending[ip]
            if (last != null && now - last < 60_000) return
            pending[ip] = now
        }
        ServerState.log("? Onay bekliyor: $name ($ip)")
        val nm = app.getSystemService(NotificationManager::class.java)
        fun action(act: String, code: Int) = PendingIntent.getBroadcast(
            app, ip.hashCode() * 2 + code,
            Intent(app, AccessReceiver::class.java).setAction(act).putExtra(EXTRA_IP, ip).putExtra(EXTRA_NAME, name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, AkisApplication.CHANNEL_ACCESS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("$name bağlanmak istiyor")
            .setContentText("$ip adresindeki cihaz içeriklerini görmek istiyor")
            .setColor(0xFFD71921.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .addAction(0, "İzin ver", action(ACTION_ALLOW, 0))
            .addAction(0, "Engelle", action(ACTION_BLOCK, 1))
            .build()
        try { nm.notify(notifId(ip), n) } catch (_: SecurityException) {}
    }

    private fun notifId(ip: String) = 1000 + (ip.hashCode() and 0xFFFF)

    private fun cancelNotification(ip: String) {
        if (::app.isInitialized) app.getSystemService(NotificationManager::class.java).cancel(notifId(ip))
    }

    // ---------------- Web PIN ----------------

    fun token(pin: String, uuid: String): String =
        MessageDigest.getInstance("SHA-256").digest("$pin|$uuid".toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

    fun cookieOk(cookieHeader: String?, pin: String, uuid: String): Boolean {
        if (pin.isEmpty()) return true
        val want = token(pin, uuid)
        return cookieHeader?.split(';')?.any { it.trim() == "akis_pin=$want" } == true
    }

    /** 5 yanlış denemeden sonra 1 dakika bekletir. */
    fun locked(ip: String): Boolean = synchronized(attempts) {
        val (n, t) = attempts[ip] ?: return false
        n >= 5 && System.currentTimeMillis() - t < 60_000
    }

    fun recordAttempt(ip: String, ok: Boolean) = synchronized(attempts) {
        if (ok) attempts.remove(ip)
        else {
            val (n, t) = attempts[ip] ?: (0 to 0L)
            val fresh = System.currentTimeMillis() - t > 60_000
            attempts[ip] = (if (fresh) 1 else n + 1) to System.currentTimeMillis()
        }
    }
}

/** Onay bildirimindeki "İzin ver / Engelle" düğmeleri. */
class AccessReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        DeviceAccess.init(context)
        val ip = intent.getStringExtra(DeviceAccess.EXTRA_IP) ?: return
        val name = intent.getStringExtra(DeviceAccess.EXTRA_NAME) ?: ip
        when (intent.action) {
            DeviceAccess.ACTION_ALLOW -> DeviceAccess.allow(ip, name)
            DeviceAccess.ACTION_BLOCK -> DeviceAccess.block(ip, name)
        }
    }
}
