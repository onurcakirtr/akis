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
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Alış eşleştirmesi.
 *  - Telefonda "Alış'ı eşleştir" 5 dakikalık 6 haneli bir kod (ve QR) gösterir
 *  - Alış kodu POST /pair ile gönderir, karşılığında cihaza özel kalıcı bir anahtar alır
 *  - Ya da (kodsuz) Alış POST /pair/request gönderir; telefona "İzin ver / Reddet" bildirimi gelir,
 *    Alış POST /pair/status?r=… ile sonucu bekler. "Bu ağa güveniyorum" açıksa istek kendiliğinden kabul edilir
 *  - Alış her istekte "Authorization: Bearer <anahtar>" gönderir; yerel ağda onay / PIN istenmez
 *  - Dosya yönetimi (POST /api/fs) yalnızca eşleşmiş ve bu cihaz için izin verilmiş Alış'a açıktır
 * Anahtarın kendisi saklanmaz, yalnızca SHA-256 özeti.
 */
object Pairing {
    data class Device(
        val id: String,
        val name: String,
        val hash: String,
        val created: Long,
        val lastSeen: Long,
        val fileOps: Boolean,
    )

    private const val CODE_MS = 5 * 60_000L
    private lateinit var sp: SharedPreferences
    private val rnd = SecureRandom()
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> get() = _devices

    /** Geçerli kod ve bitiş zamanı (ms). */
    private val _code = MutableStateFlow<Pair<String, Long>?>(null)
    val code: StateFlow<Pair<String, Long>?> get() = _code
    private var fails = 0
    private lateinit var app: Context

    /** Bildirimle onay bekleyen istekler. state: pending | ok | denied */
    private class Req(val id: String, val ip: String, val name: String, val time: Long, var state: String = "pending", var token: String? = null)
    private val requests = HashMap<String, Req>()

    const val ACTION_ALLOW = "com.akis.app.PAIR_ALLOW"
    const val ACTION_DENY = "com.akis.app.PAIR_DENY"
    const val EXTRA_ID = "id"

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        app = ctx.applicationContext
        sp = app.getSharedPreferences("akis_pair", Context.MODE_PRIVATE)
        _devices.value = load()
    }

    private fun load(): List<Device> = runCatching {
        val a = JSONArray(sp.getString("devices", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Device(o.getString("id"), o.optString("name"), o.getString("hash"), o.optLong("created"), o.optLong("seen"), o.optBoolean("fileOps"))
        }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun save(list: List<Device>) {
        _devices.value = list
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("name", it.name).put("hash", it.hash)
                    .put("created", it.created).put("seen", it.lastSeen).put("fileOps", it.fileOps),
            )
        }
        sp.edit().putString("devices", a.toString()).apply()
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    @Synchronized
    fun newCode(): String {
        val c = "%06d".format(rnd.nextInt(1_000_000))
        _code.value = c to System.currentTimeMillis() + CODE_MS
        fails = 0
        return c
    }

    fun cancelCode() {
        _code.value = null
    }

    /** Kod doğruysa yeni cihazı kaydeder ve anahtarı döndürür; yanlışsa null. 5 yanlış denemede kod geçersiz olur. */
    @Synchronized
    fun pair(code: String, name: String): Pair<Device, String>? {
        val cur = _code.value ?: return null
        if (System.currentTimeMillis() > cur.second) {
            _code.value = null
            return null
        }
        if (!MessageDigest.isEqual(code.trim().toByteArray(), cur.first.toByteArray())) {
            if (++fails >= 5) _code.value = null
            return null
        }
        _code.value = null
        return issue(name)
    }

    private fun issue(name: String): Pair<Device, String> {
        val bytes = ByteArray(32).also { rnd.nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        val now = System.currentTimeMillis()
        val dev = Device(UUID.randomUUID().toString(), name.trim().take(60).ifEmpty { "Alış" }, sha256(token), now, now, false)
        save(_devices.value + dev)
        return dev to token
    }

    /**
     * Kodsuz eşleştirme isteği. "Bu ağa güveniyorum" açıksa hemen anahtar döner ("ok:<anahtar>"),
     * değilse telefona bildirim gider ve istek kimliği döner ("wait:<id>").
     */
    @Synchronized
    fun request(ip: String, name: String, auto: Boolean): String {
        val now = System.currentTimeMillis()
        requests.values.removeAll { now - it.time > 5 * 60_000 }
        val clean = name.trim().take(60).ifEmpty { "Alış" }
        if (auto) {
            val (d, token) = issue(clean)
            ServerState.log("🔗 ${d.name}")
            return "ok:$token"
        }
        requests.values.firstOrNull { it.ip == ip && it.state == "pending" }?.let { return "wait:${it.id}" }
        val r = Req(UUID.randomUUID().toString(), ip, clean, now)
        requests[r.id] = r
        notify(r)
        return "wait:${r.id}"
    }

    /** pending | ok:<anahtar> (bir kez) | denied | gone */
    @Synchronized
    fun status(id: String, ip: String): String {
        val r = requests[id]?.takeIf { it.ip == ip } ?: return "gone"
        return when (r.state) {
            "ok" -> { requests.remove(id); "ok:${r.token}" }
            "denied" -> { requests.remove(id); "denied" }
            else -> "pending"
        }
    }

    @Synchronized
    fun answer(id: String, allow: Boolean) {
        val r = requests[id] ?: return
        if (r.state != "pending") return
        if (allow) {
            val (d, token) = issue(r.name)
            r.token = token
            r.state = "ok"
            ServerState.log("🔗 ${d.name}")
        } else {
            r.state = "denied"
        }
        app.getSystemService(NotificationManager::class.java).cancel(notifId(id))
    }

    private fun notifId(id: String) = 3000 + (id.hashCode() and 0xFFFF)

    private fun notify(r: Req) {
        fun action(act: String, code: Int) = PendingIntent.getBroadcast(
            app, r.id.hashCode() * 2 + code,
            Intent(app, PairReceiver::class.java).setAction(act).putExtra(EXTRA_ID, r.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, AkisApplication.CHANNEL_ACCESS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(Lang.s(R.string.pair_req_title, r.name))
            .setContentText(Lang.s(R.string.pair_req_text, r.ip))
            .setColor(0xFFD71921.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setTimeoutAfter(5 * 60_000)
            .addAction(0, Lang.s(R.string.allow), action(ACTION_ALLOW, 0))
            .addAction(0, Lang.s(R.string.pair_deny), action(ACTION_DENY, 1))
            .build()
        try { app.getSystemService(NotificationManager::class.java).notify(notifId(r.id), n) } catch (_: SecurityException) {}
    }

    /** "Authorization: Bearer …" başlığından eşleşmiş cihaz. */
    fun byAuth(header: String?): Device? {
        if (header == null || !header.startsWith("Bearer ", ignoreCase = true)) return null
        val t = header.substring(7).trim()
        if (t.length < 32) return null
        val h = sha256(t)
        val d = _devices.value.firstOrNull { MessageDigest.isEqual(it.hash.toByteArray(), h.toByteArray()) } ?: return null
        val now = System.currentTimeMillis()
        if (now - d.lastSeen > 60_000) save(_devices.value.map { if (it.id == d.id) it.copy(lastSeen = now) else it })
        return d
    }

    fun setFileOps(id: String, on: Boolean) = save(_devices.value.map { if (it.id == id) it.copy(fileOps = on) else it })

    fun remove(id: String) = save(_devices.value.filter { it.id != id })
}

/** Eşleştirme bildirimindeki "İzin ver / Reddet" düğmeleri. */
class PairReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        Pairing.init(context)
        val id = intent.getStringExtra(Pairing.EXTRA_ID) ?: return
        Pairing.answer(id, intent.action == Pairing.ACTION_ALLOW)
    }
}
