package com.akis.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom

/**
 * Dosya isteme bağlantısı (/drop/<anahtar>): açan kişi yalnızca bu telefona dosya gönderebilir,
 * kitaplığı ya da başka hiçbir şeyi göremez. Süreli; isteğe bağlı toplam boyut sınırı ve "ilk dosyadan sonra kapan".
 */
object FileRequests {
    data class Req(
        val token: String,
        val expires: Long,
        /** Toplam en fazla bu kadar bayt (0 = sınırsız). */
        val maxBytes: Long,
        val once: Boolean,
        val created: Long,
        val files: Int = 0,
        val bytes: Long = 0,
    )

    private lateinit var app: Context
    private val _list = MutableStateFlow<List<Req>>(emptyList())
    val list: StateFlow<List<Req>> get() = _list
    private val rnd = SecureRandom()
    /** Süren yüklemelerin ayırdığı baytlar (sınır aşılmasın diye). */
    private val pending = HashMap<String, Long>()

    private fun db() = File(app.filesDir, "requests.json")

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        try {
            val arr = JSONArray(db().readText())
            _list.value = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Req(o.getString("t"), o.getLong("e"), o.getLong("m"), o.getBoolean("o"), o.getLong("c"), o.optInt("f"), o.optLong("b"))
            }
        } catch (_: Exception) {
        }
        cleanup()
    }

    private fun save() {
        val arr = JSONArray()
        for (r in _list.value) arr.put(
            JSONObject().put("t", r.token).put("e", r.expires).put("m", r.maxBytes).put("o", r.once)
                .put("c", r.created).put("f", r.files).put("b", r.bytes),
        )
        runCatching { db().writeText(arr.toString()) }
    }

    fun create(minutes: Int, maxBytes: Long, once: Boolean): Req {
        val token = ByteArray(16).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val now = System.currentTimeMillis()
        val r = Req(token, now + minutes * 60_000L, maxBytes, once, now)
        _list.value = _list.value + r
        save()
        return r
    }

    fun cleanup() {
        val now = System.currentTimeMillis()
        val live = _list.value.filter { it.expires > now }
        if (live.size != _list.value.size) { _list.value = live; save() }
    }

    fun get(token: String): Req? {
        val r = _list.value.firstOrNull { it.token == token } ?: return null
        if (r.expires <= System.currentTimeMillis()) { remove(token); return null }
        return r
    }

    fun remove(token: String) {
        _list.value = _list.value.filter { it.token != token }
        save()
    }

    /** Yüklemeye başlamadan yer ayırır. @return null ise başlayabilir; aksi halde "gone" ya da "big" */
    fun reserve(token: String, len: Long): String? = synchronized(pending) {
        val r = get(token) ?: return "gone"
        val busy = pending[token] ?: 0L
        if (r.once && (r.files > 0 || busy > 0)) return "gone"
        if (r.maxBytes > 0 && r.bytes + busy + len > r.maxBytes) return "big"
        pending[token] = busy + len
        null
    }

    fun finish(token: String, len: Long, ok: Boolean) {
        synchronized(pending) {
            val left = (pending[token] ?: 0L) - len
            if (left > 0) pending[token] = left else pending.remove(token)
            if (!ok) return
            val r = _list.value.firstOrNull { it.token == token } ?: return
            if (r.once) { remove(token); return }
            _list.value = _list.value.map { if (it.token == token) it.copy(files = it.files + 1, bytes = it.bytes + len) else it }
            save()
        }
    }

    /** Bağlantı adresi: yerel ağ ya da (açıksa) uzak adres. */
    fun url(token: String, remote: Boolean): String? =
        (if (remote) Tunnel.url.value else ServerState.address.value)?.let { "${it.trimEnd('/')}/drop/$token" }
}
