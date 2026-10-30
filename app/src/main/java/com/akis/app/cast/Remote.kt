package com.akis.app.cast

import android.content.Context
import com.akis.app.DeviceAccess
import com.akis.app.Prefs
import com.akis.app.ServerState
import com.akis.app.WatchHistory
import com.akis.app.dlna.Entry
import com.akis.app.dlna.Kind
import com.akis.app.dlna.MediaLibrary
import com.akis.app.dlna.Sub
import com.akis.app.dlna.Xml
import com.akis.app.service.DlnaService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class TvType { CAST, DLNA }

/** Ağda bulunan, içerik gönderilebilen bir cihaz (Google TV, Chromecast, DLNA TV / hoparlör). */
data class TvDevice(val id: String, val name: String, val type: TvType, val description: String = "", val ip: String? = null)

/** TV'de o an olan biten — Gönder ekranı, bildirim ve widget bunu gösterir. */
data class RemoteState(
    val device: TvDevice? = null,
    val connecting: String? = null,
    val entryId: String? = null,
    val title: String = "",
    val kind: Kind? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val idle: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Float = 0f,
    val muted: Boolean = false,
) {
    val connected get() = device != null
    val hasMedia get() = device != null && entryId != null && (!idle || buffering || kind == Kind.IMAGE)
}

/** Bir gönderim yöntemi (Cast ya da DLNA). */
interface Backend {
    /** @param subs (adres, altyazı) — Cast için WebVTT, DLNA için SRT adresleri */
    fun load(e: Entry, url: String, mime: String, subs: List<Pair<String, Sub>>, startMs: Long, base: String)
    fun pause()
    fun resume()
    fun stop()
    fun seek(ms: Long)
    fun setVolume(v: Float)
    fun setMuted(m: Boolean)
    fun disconnect()
}

/**
 * TV'ye gönderme cephesi: Cast ve DLNA cihazlarını tek listede toplar, seçili cihaza dosya gönderir ve kumanda eder.
 * TV dosyayı telefondaki Akış sunucusundan çeker; bu yüzden gönderirken sunucu kendiliğinden açılır.
 */
object Remote {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(RemoteState())
    val state: StateFlow<RemoteState> get() = _state
    val devices = MutableStateFlow<List<TvDevice>>(emptyList())
    /** Ekranda bir kez gösterilecek bilgi / hata. */
    val message = MutableStateFlow<String?>(null)

    private var backend: Backend? = null
    private var lastHistory = 0L
    @Volatile private var current: Entry? = null

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        if (Prefs.current.cast) CastBackend.init(app)
    }

    /** Ayar açılınca sonradan başlatmak için. */
    fun ensureInit() {
        if (::app.isInitialized && Prefs.current.cast) CastBackend.init(app)
    }

    // ---------------- Cihaz bulma ----------------

    private var dlnaDevices = emptyList<TvDevice>()
    private var castDevices = emptyList<TvDevice>()

    internal fun setCastDevices(list: List<TvDevice>) {
        castDevices = list
        devices.value = castDevices + dlnaDevices
    }

    /** Gönder ekranı açıkken çağrılır: Cast'te aktif arama + DLNA M-SEARCH. */
    fun startScan() {
        if (!Prefs.current.cast) return
        CastBackend.init(app)
        CastBackend.startScan()
        scope.launch {
            val found = withContext(Dispatchers.IO) { DlnaRenderer.discover() }
            dlnaDevices = found
            devices.value = castDevices + dlnaDevices
        }
    }

    fun stopScan() = CastBackend.stopScan()

    // ---------------- Bağlanma ----------------

    fun connect(d: TvDevice) {
        if (_state.value.device?.id == d.id) return
        disconnect(stopPlayback = false)
        when (d.type) {
            TvType.CAST -> {
                _state.update { it.copy(connecting = d.name) }
                CastBackend.connect(d)
            }
            TvType.DLNA -> {
                val r = DlnaRenderer.forDevice(d) ?: run { message.value = "Cihaz artık görünmüyor"; return }
                attach(d, r)
            }
        }
    }

    internal fun attach(d: TvDevice, b: Backend) {
        backend = b
        _state.update { RemoteState(device = d, volume = it.volume) }
        // TV dosyayı bizden çekecek: cihaz onayı açıksa bu cihaza kendiliğinden izin ver
        if (Prefs.current.deviceApproval && d.ip != null) DeviceAccess.allow(d.ip, d.name)
        ServerState.log("📺 ${d.name} cihazına bağlanıldı")
    }

    internal fun connectFailed(msg: String) {
        _state.update { it.copy(connecting = null) }
        message.value = msg
    }

    internal fun detached() {
        backend = null
        current = null
        WatchHistory.preciseId = null
        _state.value = RemoteState()
    }

    fun disconnect(stopPlayback: Boolean = true) {
        val b = backend ?: return
        if (stopPlayback) runCatching { b.stop() }
        b.disconnect()
        detached()
    }

    // ---------------- Oynatma ----------------

    /** Dosyayı seçili TV'de oynatır; sunucu kapalıysa açar. */
    fun play(e: Entry) {
        val b = backend ?: run { message.value = "Önce bir TV seç"; return }
        scope.launch {
            val base = ensureServer() ?: run { message.value = "Sunucu başlatılamadı (Wi-Fi'a bağlı mısın?)"; return@launch }
            val s = Prefs.current
            val subs = if (e.kind == Kind.VIDEO && s.subtitles) {
                val list = withContext(Dispatchers.IO) { MediaLibrary(app).subtitlesOf(e) }
                val fmt = if (_state.value.device?.type == TvType.CAST) "vtt" else "srt"
                list.map { Xml.subUrl(base, it, true, fmt) to it }
            } else emptyList()
            val startMs = if (s.history && e.kind == Kind.VIDEO) WatchHistory.resumeMs(e.id) else 0
            val full = if (e.kind == Kind.VIDEO && e.durationMs <= 0) {
                e.copy(durationMs = withContext(Dispatchers.IO) { MediaLibrary(app).durationOf(e) })
            } else e
            current = full
            WatchHistory.preciseId = full.id
            _state.update {
                it.copy(entryId = full.id, title = full.title, kind = full.kind, positionMs = startMs, durationMs = full.durationMs, idle = false, buffering = true)
            }
            // Cast ana iş parçacığında çalışmalı; DLNA kendi iş parçacığını açar
            runCatching { b.load(full, Xml.mediaUrl(base, full), castMime(full), subs, startMs, base) }
                .onFailure { message.value = "TV bunu oynatamadı: ${it.message}" }
            if (startMs > 0) message.value = "Kaldığın yerden devam ediliyor"
            ServerState.log("📺 ${full.title} → ${_state.value.device?.name}")
        }
    }

    /** Google TV'nin varsayılan alıcısı MKV'yi "video/mp4" olarak verince sorunsuz oynatıyor. */
    private fun castMime(e: Entry): String = when {
        _state.value.device?.type != TvType.CAST -> e.mime
        e.ext == "mkv" || e.mime == "video/x-matroska" -> "video/mp4"
        else -> e.mime
    }

    private suspend fun ensureServer(): String? {
        ServerState.address.value?.let { if (ServerState.running.value) return it }
        DlnaService.start(app)
        return withTimeoutOrNull(8000) {
            var a: String? = null
            while (a == null) {
                delay(200)
                a = ServerState.address.value?.takeIf { ServerState.running.value }
            }
            a
        }
    }

    fun togglePlay() {
        val st = _state.value
        if (st.playing) backend?.pause() else backend?.resume()
    }

    fun pause() = backend?.pause()
    fun resume() = backend?.resume()

    fun stop() {
        backend?.stop()
        reportHistory(force = true)
        current = null
        WatchHistory.preciseId = null
        _state.update { it.copy(entryId = null, title = "", kind = null, playing = false, idle = true, positionMs = 0, durationMs = 0) }
    }

    fun seek(ms: Long) {
        val d = _state.value.durationMs
        val t = if (d > 0) ms.coerceIn(0, d) else ms.coerceAtLeast(0)
        _state.update { it.copy(positionMs = t) }
        backend?.seek(t)
    }

    fun skip(deltaMs: Long) = seek(_state.value.positionMs + deltaMs)

    fun setVolume(v: Float) {
        val x = v.coerceIn(0f, 1f)
        _state.update { it.copy(volume = x) }
        backend?.setVolume(x)
    }

    fun volumeStep(up: Boolean) = setVolume(_state.value.volume + if (up) 0.05f else -0.05f)

    fun toggleMute() {
        val m = !_state.value.muted
        _state.update { it.copy(muted = m) }
        backend?.setMuted(m)
    }

    // ---------------- Arka uçlardan gelen durum ----------------

    internal fun report(transform: (RemoteState) -> RemoteState) {
        _state.update(transform)
        reportHistory(force = false)
    }

    private fun reportHistory(force: Boolean) {
        val e = current ?: return
        val st = _state.value
        if (e.kind != Kind.VIDEO || st.durationMs <= 0 || st.entryId != e.id) return
        val now = System.currentTimeMillis()
        if (!force && now - lastHistory < 5000) return
        lastHistory = now
        WatchHistory.report(e.id, e.title, st.positionMs, st.durationMs)
    }
}
