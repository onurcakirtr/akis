package com.akis.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * İnternetten (tünel üzerinden) giriş: ayrı uzaktan erişim şifresi + (açıksa) doğrulama kodu
 * + (isteğe bağlı) telefonda onay. Giriş yapan tarayıcı 7 gün hatırlanır; telefondan tüm oturumlar kapatılabilir.
 */
object RemoteAuth {
    const val ACTION_APPROVE = "com.akis.app.REMOTE_APPROVE"
    const val ACTION_DENY = "com.akis.app.REMOTE_DENY"
    const val EXTRA_REQ = "req"
    private const val NOTIF_ID = 3300
    private const val DAYS7 = 7 * 24 * 60 * 60_000L

    private lateinit var app: Context
    private lateinit var sp: SharedPreferences
    private val rnd = SecureRandom()
    private val fails = HashMap<String, Pair<Int, Long>>()
    private class Pending(val ip: String, val created: Long, @Volatile var state: String = "pending")
    private val pending = HashMap<String, Pending>()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        sp = ctx.getSharedPreferences("remote", Context.MODE_PRIVATE)
    }

    private fun random(n: Int) = ByteArray(n).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private fun hash(pw: String, salt: String): String {
        val spec = PBEKeySpec(pw.toCharArray(), salt.toByteArray(), 20_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
    }

    val hasPassword: Boolean get() = Prefs.current.remoteHash.isNotEmpty()

    fun setPassword(pw: String) {
        val salt = random(16)
        Prefs.update { putString("remoteHash", "$salt:${hash(pw, salt)}") }
        endAll()
    }

    private fun check(pw: String): Boolean {
        val stored = Prefs.current.remoteHash
        val salt = stored.substringBefore(':', "")
        if (salt.isEmpty()) return false
        return MessageDigest.isEqual(hash(pw, salt).toByteArray(), stored.substringAfter(':').toByteArray())
    }

    fun locked(ip: String): Boolean = synchronized(fails) {
        val (n, t) = fails[ip] ?: return false
        if (System.currentTimeMillis() - t > 15 * 60_000L) { fails.remove(ip); return false }
        n >= 5
    }

    /** @return "locked", "wrong", "code", "ok:<oturum>" ya da onay bekleniyorsa "wait:<istek>" */
    fun login(ip: String, pw: String, code: String): String {
        if (locked(ip)) return "locked"
        val pwOk = check(pw)
        val codeOk = !Totp.enabled || Totp.check(code)
        if (!pwOk || !codeOk) {
            synchronized(fails) {
                val (c, _) = fails[ip] ?: (0 to 0L)
                fails[ip] = (c + 1) to System.currentTimeMillis()
            }
            ServerState.log(Lang.s(R.string.log_remote_wrong, ip))
            return if (locked(ip)) "locked" else if (pwOk) "code" else "wrong"
        }
        synchronized(fails) { fails.remove(ip) }
        if (!Prefs.current.remoteApprove) return "ok:" + newSession(ip)
        val id = random(12)
        synchronized(pending) {
            pending.values.removeAll { System.currentTimeMillis() - it.created > 2 * 60_000L }
            pending[id] = Pending(ip, System.currentTimeMillis())
        }
        notifyApproval(ip, id)
        return "wait:$id"
    }

    fun status(id: String, ip: String): String {
        val p = synchronized(pending) { pending[id] } ?: return "expired"
        if (p.ip != ip || System.currentTimeMillis() - p.created > 2 * 60_000L) return "expired"
        return when (p.state) {
            "approved" -> { synchronized(pending) { pending.remove(id) }; "ok:" + newSession(ip) }
            else -> p.state
        }
    }

    fun decide(id: String, approve: Boolean) {
        synchronized(pending) { pending[id]?.state = if (approve) "approved" else "denied" }
        app.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }

    // ---------------- Oturumlar (7 gün) ----------------

    private fun sessions(): Map<String, Long> {
        val now = System.currentTimeMillis()
        val all = (sp.getStringSet("s", emptySet()) ?: emptySet()).mapNotNull {
            val t = it.substringBefore('\t')
            val exp = it.substringAfter('\t', "").toLongOrNull() ?: return@mapNotNull null
            t to exp
        }.toMap()
        val live = all.filterValues { it > now }
        if (live.size != all.size) write(live)
        return live
    }

    private fun write(m: Map<String, Long>) {
        sp.edit().putStringSet("s", m.map { "${it.key}\t${it.value}" }.toSet()).apply()
    }

    private fun newSession(ip: String): String {
        val tok = random(32)
        // Saklanan: anahtarın özeti (çerez çalınsa bile depodan geri üretilemez)
        write(sessions() + (sha(tok) to System.currentTimeMillis() + DAYS7))
        ServerState.log(Lang.s(R.string.log_remote_in, ip))
        return tok
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun valid(cookieHeader: String?): Boolean {
        val tok = cookieHeader?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("akis_remote=") }?.substringAfter('=') ?: return false
        return sessions().containsKey(sha(tok))
    }

    val count: Int get() = if (::sp.isInitialized) sessions().size else 0

    fun endAll() {
        if (::sp.isInitialized) write(emptyMap())
    }

    private fun notifyApproval(ip: String, id: String) {
        fun action(act: String, code: Int) = PendingIntent.getBroadcast(
            app, 3400 + code,
            Intent(app, RemoteReceiver::class.java).setAction(act).putExtra(EXTRA_REQ, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, AkisApplication.CHANNEL_ACCESS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(Lang.s(R.string.remote_req_title))
            .setContentText(Lang.s(R.string.remote_req_text, ip))
            .setStyle(NotificationCompat.BigTextStyle().bigText(Lang.s(R.string.remote_req_text, ip)))
            .setColor(0xFFD71921.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(2 * 60_000L)
            .addAction(0, Lang.s(R.string.allow), action(ACTION_APPROVE, 0))
            .addAction(0, Lang.s(R.string.admin_deny), action(ACTION_DENY, 1))
            .build()
        try { app.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n) } catch (_: SecurityException) {}
    }
}

class RemoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        RemoteAuth.init(context)
        RemoteAuth.decide(intent.getStringExtra(RemoteAuth.EXTRA_REQ) ?: return, intent.action == RemoteAuth.ACTION_APPROVE)
    }
}
