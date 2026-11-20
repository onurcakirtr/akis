package com.akis.app

import android.content.Context
import android.content.SharedPreferences
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

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("akis_pair", Context.MODE_PRIVATE)
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
        val bytes = ByteArray(32).also { rnd.nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        val now = System.currentTimeMillis()
        val dev = Device(UUID.randomUUID().toString(), name.trim().take(60).ifEmpty { "Alış" }, sha256(token), now, now, false)
        save(_devices.value + dev)
        return dev to token
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
