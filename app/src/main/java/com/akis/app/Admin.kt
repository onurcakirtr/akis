package com.akis.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Web'den yönetim güvenliği:
 *  - ayrı yönetici şifresi (tuzlu PBKDF2 özeti saklanır)
 *  - iki adımlı doğrulama açıksa ayrıca 6 haneli kod
 *  - doğru şifreden sonra telefonda "İzin ver / Reddet" onayı
 *  - tek oturum; 30 dk hareketsizlikte ya da en geç 12 saatte biter; IP'ye bağlı
 *  - bir cihazdan 5 yanlış denemede 15 dk bekleme + telefona uyarı
 */
object Admin {
    const val ACTION_APPROVE = "com.akis.app.ADMIN_APPROVE"
    const val ACTION_DENY = "com.akis.app.ADMIN_DENY"
    const val EXTRA_REQ = "req"
    private const val NOTIF_ID = 3100
    private const val IDLE_MS = 30 * 60_000L
    private const val MAX_MS = 12 * 60 * 60_000L

    private lateinit var app: Context
    private val rnd = SecureRandom()

    data class Session(val token: String, val ip: String, val name: String, val created: Long, @Volatile var last: Long)

    private class Pending(val id: String, val ip: String, val name: String, val created: Long, @Volatile var state: String = "pending")

    private val pending = HashMap<String, Pending>()
    private val fails = HashMap<String, Pair<Int, Long>>()
    @Volatile private var session: Session? = null
    private val _active = MutableStateFlow<Session?>(null)
    /** Telefondaki Ayarlar ekranı için açık oturum. */
    val active: StateFlow<Session?> get() = _active

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    private fun random(n: Int) = ByteArray(n).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    // ---------------- Şifre ----------------

    private fun hash(pw: String, salt: String): String {
        val spec = PBEKeySpec(pw.toCharArray(), salt.toByteArray(), 20_000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return key.joinToString("") { "%02x".format(it) }
    }

    val hasPassword: Boolean get() = Prefs.current.adminHash.isNotEmpty()

    fun setPassword(pw: String) {
        val salt = random(16)
        Prefs.update { putString("adminHash", "$salt:${hash(pw, salt)}") }
        endSession()
    }

    private fun check(pw: String): Boolean {
        val stored = Prefs.current.adminHash
        val salt = stored.substringBefore(':', "")
        if (salt.isEmpty()) return false
        return MessageDigest.isEqual(hash(pw, salt).toByteArray(), stored.substringAfter(':').toByteArray())
    }

    // ---------------- Giriş ----------------

    fun locked(ip: String): Boolean = synchronized(fails) {
        val (n, t) = fails[ip] ?: return false
        if (System.currentTimeMillis() - t > 15 * 60_000L) { fails.remove(ip); return false }
        n >= 5
    }

    /**
     * @return "locked", "wrong", "off" ya da onay isteği kimliği (telefona bildirim gider)
     */
    fun login(ip: String, name: String, pw: String, code: String = ""): String {
        if (!Prefs.current.adminOn || !hasPassword) return "off"
        if (locked(ip)) return "locked"
        val codeOk = !Totp.enabled || Totp.check(code)
        if (!check(pw) || !codeOk) {
            val n = synchronized(fails) {
                val (c, _) = fails[ip] ?: (0 to 0L)
                fails[ip] = (c + 1) to System.currentTimeMillis()
                c + 1
            }
            ServerState.log(Lang.s(R.string.log_admin_wrong, name, ip))
            if (n == 5) notify(Lang.s(R.string.admin_wrong_title), Lang.s(R.string.admin_wrong_text, name, ip), null)
            return if (n >= 5) "locked" else if (check(pw)) "code" else "wrong"
        }
        synchronized(fails) { fails.remove(ip) }
        val id = random(12)
        synchronized(pending) {
            pending.values.removeAll { System.currentTimeMillis() - it.created > 2 * 60_000L }
            pending[id] = Pending(id, ip, name, System.currentTimeMillis())
        }
        notify(Lang.s(R.string.admin_req_title, name), Lang.s(R.string.admin_req_text, ip), id)
        return id
    }

    /** Onay bekleyen giriş: "pending", "denied", "expired" ya da onaylandıysa oturum anahtarı ile "ok:<anahtar>". */
    fun status(id: String, ip: String): String {
        val p = synchronized(pending) { pending[id] } ?: return "expired"
        if (p.ip != ip) return "expired"
        if (System.currentTimeMillis() - p.created > 2 * 60_000L) {
            synchronized(pending) { pending.remove(id) }
            return "expired"
        }
        return when (p.state) {
            "approved" -> {
                synchronized(pending) { pending.remove(id) }
                val now = System.currentTimeMillis()
                val s = Session(random(32), p.ip, p.name, now, now)
                session = s
                _active.value = s
                ServerState.log(Lang.s(R.string.log_admin_in, p.name, p.ip))
                "ok:" + s.token
            }
            else -> p.state
        }
    }

    fun decide(id: String, approve: Boolean) {
        synchronized(pending) { pending[id]?.state = if (approve) "approved" else "denied" }
        app.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }

    // ---------------- Oturum ----------------

    /** Çerezdeki anahtar geçerliyse oturumu uzatır. */
    fun valid(cookieHeader: String?, ip: String): Boolean {
        val s = session ?: return false
        if (!Prefs.current.adminOn) { endSession(); return false }
        val now = System.currentTimeMillis()
        if (now - s.last > IDLE_MS || now - s.created > MAX_MS) { endSession(); return false }
        if (s.ip != ip) return false
        val tok = cookieHeader?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("akis_admin=") }?.substringAfter('=') ?: return false
        if (!MessageDigest.isEqual(tok.toByteArray(), s.token.toByteArray())) return false
        s.last = now
        return true
    }

    fun endSession() {
        if (session != null) ServerState.log(Lang.s(R.string.log_admin_out))
        session = null
        _active.value = null
    }

    fun audit(what: String, ip: String) = ServerState.log(Lang.s(R.string.log_admin_action, what, ip))

    // ---------------- Bildirim ----------------

    private fun notify(title: String, text: String, reqId: String?) {
        val b = NotificationCompat.Builder(app, AkisApplication.CHANNEL_ACCESS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFFD71921.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(2 * 60_000L)
        if (reqId != null) {
            fun action(act: String, code: Int) = PendingIntent.getBroadcast(
                app, 3200 + code,
                Intent(app, AdminReceiver::class.java).setAction(act).putExtra(EXTRA_REQ, reqId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, Lang.s(R.string.allow), action(ACTION_APPROVE, 0))
            b.addAction(0, Lang.s(R.string.admin_deny), action(ACTION_DENY, 1))
        }
        try { app.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, b.build()) } catch (_: SecurityException) {}
    }
}

/** Yönetim girişi bildirimindeki "İzin ver / Reddet". */
class AdminReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        Admin.init(context)
        val id = intent.getStringExtra(Admin.EXTRA_REQ) ?: return
        Admin.decide(id, intent.action == Admin.ACTION_APPROVE)
    }
}
