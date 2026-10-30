package com.akis.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom

/**
 * Hızlı paylaşım: Paylaş → Akış ile gelen dosya için süreli indirme bağlantısı (/share/<anahtar>).
 * Android başka uygulamadan gelen erişimi kısa sürede geri aldığı için dosya önbelleğe kopyalanır; süre dolunca silinir.
 */
object QuickShare {
    data class Share(
        val token: String,
        val name: String,
        val mime: String,
        val size: Long,
        val path: String,
        val expires: Long,
        val once: Boolean,
        val created: Long,
        val downloads: Int = 0,
    ) {
        val file: File get() = File(path)
    }

    private lateinit var app: Context
    private val _shares = MutableStateFlow<List<Share>>(emptyList())
    val shares: StateFlow<List<Share>> get() = _shares
    private val rnd = SecureRandom()

    private fun dir() = File(app.cacheDir, "shares").apply { mkdirs() }
    private fun db() = File(app.filesDir, "shares.json")

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        try {
            val arr = JSONArray(db().readText())
            val list = ArrayList<Share>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += Share(
                    o.getString("t"), o.getString("n"), o.getString("m"), o.getLong("s"), o.getString("p"),
                    o.getLong("e"), o.getBoolean("o"), o.getLong("c"), o.optInt("d"),
                )
            }
            _shares.value = list
        } catch (_: Exception) {
        }
        cleanup()
    }

    private fun save() {
        val arr = JSONArray()
        for (s in _shares.value) arr.put(
            JSONObject().put("t", s.token).put("n", s.name).put("m", s.mime).put("s", s.size).put("p", s.path)
                .put("e", s.expires).put("o", s.once).put("c", s.created).put("d", s.downloads),
        )
        runCatching { db().writeText(arr.toString()) }
    }

    /** Süresi dolanları ve sahipsiz kopyaları siler. */
    fun cleanup() {
        val now = System.currentTimeMillis()
        val (live, dead) = _shares.value.partition { it.expires > now && it.file.exists() }
        dead.forEach { it.file.parentFile?.deleteRecursively() }
        if (dead.isNotEmpty()) { _shares.value = live; save() }
        val keep = live.map { it.token }.toSet()
        dir().listFiles()?.forEach { if (it.name !in keep) it.deleteRecursively() }
    }

    fun get(token: String): Share? {
        val s = _shares.value.firstOrNull { it.token == token } ?: return null
        if (s.expires <= System.currentTimeMillis() || !s.file.exists()) { remove(token); return null }
        return s
    }

    fun remove(token: String) {
        _shares.value.firstOrNull { it.token == token }?.file?.parentFile?.deleteRecursively()
        _shares.value = _shares.value.filter { it.token != token }
        save()
    }

    /** Tam indirme bitti: sayacı artır, "tek indirme" ise kapat. */
    fun downloaded(token: String) {
        val s = _shares.value.firstOrNull { it.token == token } ?: return
        if (s.once) remove(token)
        else { _shares.value = _shares.value.map { if (it.token == token) it.copy(downloads = it.downloads + 1) else it }; save() }
    }

    fun nameOf(ctx: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "dosya"
        var size = -1L
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        } catch (_: Exception) {
        }
        return name to size
    }

    /**
     * Dosyayı önbelleğe kopyalayıp paylaşım oluşturur (çağıran arka planda çalıştırmalı).
     * @param progress kopyalanan bayt
     */
    fun create(ctx: Context, uri: Uri, mime: String?, minutes: Int, once: Boolean, progress: (Long) -> Unit): Share? {
        init(ctx)
        val (name0, _) = nameOf(ctx, uri)
        val name = name0.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").ifEmpty { "dosya" }
        val bytes = ByteArray(16).also { rnd.nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        val d = File(dir(), token).apply { mkdirs() }
        val f = File(d, name)
        var copied = 0L
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        copied += n
                        if (copied - last > 1_000_000) { last = copied; progress(copied) }
                    }
                }
            } ?: return null
        } catch (_: Exception) {
            d.deleteRecursively()
            return null
        }
        val now = System.currentTimeMillis()
        val s = Share(
            token, name, com.akis.app.dlna.MediaLibrary.mimeFor(name, mime), copied, f.path,
            now + minutes * 60_000L, once, now,
        )
        _shares.value = _shares.value + s
        save()
        ServerState.log(Lang.s(R.string.log_share_created, name))
        return s
    }

    fun url(token: String): String? = ServerState.address.value?.let { "$it/share/$token" }
}
