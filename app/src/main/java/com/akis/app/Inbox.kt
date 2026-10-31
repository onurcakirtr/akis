package com.akis.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom

/**
 * Dosya isteme bağlantılarıyla gelen dosyalar (İndirilenler/Akış/Gelenler): kimden ve ne zaman geldiği burada tutulur.
 * Dosya başka yerden silinirse kayıt da düşer.
 */
object Inbox {
    data class Item(val id: String, val uri: String, val name: String, val size: Long, val mime: String, val from: String, val time: Long)

    private lateinit var app: Context
    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> get() = _items
    private val rnd = SecureRandom()

    private fun db() = File(app.filesDir, "inbox.json")

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        try {
            val a = JSONArray(db().readText())
            _items.value = (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Item(o.getString("i"), o.getString("u"), o.getString("n"), o.getLong("s"), o.getString("m"), o.getString("f"), o.getLong("t"))
            }
        } catch (_: Exception) {
        }
    }

    private fun save() {
        val a = JSONArray()
        for (x in _items.value) a.put(
            JSONObject().put("i", x.id).put("u", x.uri).put("n", x.name).put("s", x.size).put("m", x.mime).put("f", x.from).put("t", x.time),
        )
        runCatching { db().writeText(a.toString()) }
    }

    @Synchronized
    fun add(uri: Uri, name: String, size: Long, mime: String, from: String) {
        val id = ByteArray(8).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        _items.value = (listOf(Item(id, uri.toString(), name, size, mime, from, System.currentTimeMillis())) + _items.value).take(500)
        save()
    }

    fun get(id: String): Item? = _items.value.firstOrNull { it.id == id }

    private fun exists(x: Item): Boolean = try {
        app.contentResolver.openFileDescriptor(Uri.parse(x.uri), "r")?.use { true } ?: false
    } catch (_: Exception) {
        false
    }

    /** Silinmiş dosyaların kayıtlarını atar. */
    @Synchronized
    fun refresh() {
        if (!::app.isInitialized) return
        val live = _items.value.filter { exists(it) }
        if (live.size != _items.value.size) { _items.value = live; save() }
    }

    @Synchronized
    fun delete(id: String) {
        val x = get(id) ?: return
        runCatching { app.contentResolver.delete(Uri.parse(x.uri), null, null) }
        _items.value = _items.value.filter { it.id != id }
        save()
    }
}
