package com.akis.app

import android.content.Context
import com.akis.app.dlna.Entry
import com.akis.app.dlna.Kind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

data class WatchRecord(
    val id: String,
    val title: String,
    val posMs: Long,
    val durMs: Long,
    val updated: Long,
    val watched: Boolean,
) {
    val fraction: Float get() = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
    val inProgress: Boolean get() = !watched && fraction in 0.03f..0.92f && posMs > 30_000
}

/**
 * İzleme geçmişi: hangi videonun nereye kadar izlendiği.
 *  - Kendi kumandamız (Cast / DLNA) ve web oynatıcı tam saniyeyi bildirir
 *  - TV dosyayı kendi açtığında, gönderilen bayttan tahmin edilir
 */
object WatchHistory {
    private const val WATCHED_AT = 0.92f
    private const val MAX = 500

    private lateinit var file: File
    private val map = LinkedHashMap<String, WatchRecord>()
    private val _records = MutableStateFlow<List<WatchRecord>>(emptyList())
    val records: StateFlow<List<WatchRecord>> get() = _records
    @Volatile private var saveQueued = false
    /** Kumandayla oynatılan video: konumu kesin bilindiği için bayt tahmini yapılmaz. */
    @Volatile var preciseId: String? = null

    private val enabled get() = Prefs.current.history

    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.filesDir, "history.json")
        try {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val r = WatchRecord(
                    o.getString("id"), o.optString("title"), o.optLong("pos"), o.optLong("dur"),
                    o.optLong("upd"), o.optBoolean("done"),
                )
                map[r.id] = r
            }
        } catch (_: Exception) {
        }
        publish()
    }

    fun get(id: String): WatchRecord? = synchronized(map) { map[id] }

    fun isWatched(id: String) = get(id)?.watched == true

    /** Kaldığı yer (ms); devam edilecek bir şey yoksa 0. */
    fun resumeMs(id: String): Long = get(id)?.takeIf { it.inProgress }?.posMs ?: 0

    fun inProgress(): List<WatchRecord> = synchronized(map) { map.values.filter { it.inProgress }.sortedByDescending { it.updated } }

    /** Kesin konum bildirimi (kumanda, web oynatıcı). */
    fun report(id: String, title: String, posMs: Long, durMs: Long) {
        if (!enabled || durMs <= 0) return
        val prev = get(id)
        // Baştan yeniden izlemeye başlanınca ✓ kalsın
        val watched = posMs >= durMs * WATCHED_AT || (prev?.watched == true && posMs < 30_000)
        put(WatchRecord(id, title, posMs.coerceIn(0, durMs), durMs, System.currentTimeMillis(), watched))
    }

    /**
     * TV dosyayı kendi okurken bayt konumundan tahmin. TV'ler dosyanın sonunu (indeks için) ve biraz ilerisini
     * önceden okur; bu yüzden sondan başlayan istekler yok sayılır ve tahmin biraz geride tutulur.
     */
    fun reportBytes(e: Entry, requestStart: Long, pos: Long, size: Long) {
        if (!enabled || e.kind != Kind.VIDEO || e.durationMs <= 0 || size <= 0 || e.id == preciseId) return
        val prev = get(e.id)
        val startFrac = requestStart.toFloat() / size
        if (startFrac > 0.9f && (prev == null || prev.fraction < 0.8f)) return
        val lag = maxOf(16L * 1024 * 1024, size / 50) // önden tamponlanan kısım
        val est = ((pos - lag).coerceAtLeast(0).toDouble() / size * e.durationMs).toLong()
        if (prev != null && est < prev.posMs && requestStart > 0 && prev.updated > System.currentTimeMillis() - 10_000) return
        put(
            WatchRecord(
                e.id, e.title, est, e.durationMs, System.currentTimeMillis(),
                (prev?.watched == true && est < 30_000) || est >= e.durationMs * WATCHED_AT,
            ),
        )
    }

    fun setWatched(id: String, title: String, durMs: Long, watched: Boolean) {
        put(WatchRecord(id, title, if (watched) durMs else 0, durMs, System.currentTimeMillis(), watched))
    }

    fun clear() {
        synchronized(map) { map.clear() }
        publish()
        save()
    }

    /** ✓ işareti açıksa izlenenlerin başına ekler. */
    fun mark(e: Entry): Entry {
        val s = Prefs.current
        if (!s.history || !s.historyMarks || e.kind != Kind.VIDEO) return e
        return if (isWatched(e.id)) e.copy(title = "✓ " + e.title) else e
    }

    private fun put(r: WatchRecord) {
        synchronized(map) {
            map.remove(r.id)
            map[r.id] = r
            while (map.size > MAX) map.remove(map.keys.first())
        }
        publish()
        save()
    }

    private fun publish() {
        _records.value = synchronized(map) { map.values.sortedByDescending { it.updated } }
    }

    private fun save() {
        if (saveQueued || !::file.isInitialized) return
        saveQueued = true
        thread(isDaemon = true) {
            Thread.sleep(3000)
            saveQueued = false
            val arr = JSONArray()
            synchronized(map) {
                for (r in map.values) arr.put(
                    JSONObject().put("id", r.id).put("title", r.title).put("pos", r.posMs).put("dur", r.durMs)
                        .put("upd", r.updated).put("done", r.watched),
                )
            }
            try {
                val tmp = File(file.parentFile, "history.json.tmp")
                tmp.writeText(arr.toString())
                tmp.renameTo(file)
            } catch (_: Exception) {
            }
        }
    }
}
