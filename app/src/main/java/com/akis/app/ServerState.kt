package com.akis.app

import com.akis.app.dlna.Kind
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class ClientInfo(
    val ip: String,
    val name: String,
    val userAgent: String,
    val firstSeen: Long,
    val lastSeen: Long,
)

data class StreamInfo(
    val key: String,
    val ip: String,
    val client: String,
    val title: String,
    val kind: Kind,
    val position: Long,
    val size: Long,
    val lastSeen: Long,
)

data class LogEvent(val time: Long, val text: String)

/** Servis ile arayüz arasında paylaşılan canlı durum. */
object ServerState {
    val GENERIC: String get() = Lang.s(R.string.client_generic)

    val running = MutableStateFlow(false)
    val address = MutableStateFlow<String?>(null)
    val startedAt = MutableStateFlow(0L)
    val error = MutableStateFlow<String?>(null)
    val clients = MutableStateFlow<List<ClientInfo>>(emptyList())
    val streams = MutableStateFlow<List<StreamInfo>>(emptyList())
    val events = MutableStateFlow<List<LogEvent>>(emptyList())
    val bytesSent = MutableStateFlow(0L)
    /** Bir cihaza izin verilince istemcilere "içerik değişti" duyurusu için. */
    val accessChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun now() = System.currentTimeMillis()

    fun log(text: String) {
        events.update { (listOf(LogEvent(now(), text)) + it).take(40) }
    }

    fun addBytes(n: Long) {
        if (n > 0) bytesSent.update { it + n }
    }

    fun touchClient(ip: String, ua: String) {
        val name = friendlyName(ua)
        var isNew = false
        clients.update { list ->
            val i = list.indexOfFirst { it.ip == ip }
            if (i < 0) {
                isNew = true
                list + ClientInfo(ip, name, ua, now(), now())
            } else {
                val c = list[i]
                val better = if (c.name == GENERIC && name != GENERIC) name else c.name
                list.toMutableList().also { it[i] = c.copy(name = better, lastSeen = now()) }
            }
        }
        if (isNew) log(Lang.s(R.string.log_new_device, name, ip))
    }

    fun clientName(ip: String) = clients.value.firstOrNull { it.ip == ip }?.name ?: ip

    fun reportStream(ip: String, id: String, title: String, kind: Kind, position: Long, size: Long) {
        val key = "$ip|$id"
        var isNew = false
        streams.update { list ->
            val i = list.indexOfFirst { it.key == key }
            val item = StreamInfo(key, ip, clientName(ip), title, kind, position, size, now())
            if (i < 0) {
                isNew = true
                list + item
            } else {
                list.toMutableList().also { it[i] = item }
            }
        }
        if (isNew) log("▶ $title → ${clientName(ip)}")
    }

    fun prune() {
        val cut = now() - 8_000
        streams.update { list -> list.filter { it.lastSeen > cut } }
    }

    fun reset() {
        clients.value = emptyList()
        streams.value = emptyList()
        bytesSent.value = 0
        error.value = null
    }

    fun friendlyName(ua: String): String {
        val u = ua.lowercase()
        return when {
            "vlc" in u -> "VLC"
            "kodi" in u || "xbmc" in u -> "Kodi"
            "samsung" in u || "sec_hhp" in u || "tizen" in u -> "Samsung TV"
            "webos" in u || "lg" in u.split(' ', '/', ';') -> "LG TV"
            "bravia" in u || "sony" in u -> "Sony TV"
            "philips" in u -> "Philips TV"
            "xbox" in u -> "Xbox"
            "playstation" in u -> "PlayStation"
            "windows-media-player" in u || "microsoft-dlna" in u || "windows" in u && "upnp" in u -> "Windows"
            "foobar" in u -> "foobar2000"
            "bubbleupnp" in u -> "BubbleUPnP"
            "mozilla" in u || "chrome" in u || "safari" in u -> Lang.s(R.string.client_browser)
            else -> GENERIC
        }
    }
}
