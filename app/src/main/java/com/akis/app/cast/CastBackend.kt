package com.akis.app.cast

import android.content.Context
import android.net.Uri
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.akis.app.dlna.Entry
import com.akis.app.dlna.Kind
import com.akis.app.dlna.Sub
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.TextTrackStyle
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage

/**
 * Varsayılan medya alıcısı: Google TV'ye / Chromecast'e hiçbir şey kurmak gerekmez.
 * Bildirim ve medya oturumu Akış'ın kendi bildiriminden yönetildiği için Cast SDK'nınki kapalı.
 */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val media = CastMediaOptions.Builder()
            .setNotificationOptions(null)
            .setMediaSessionEnabled(false)
            .build()
        return CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setCastMediaOptions(media)
            .setStopReceiverApplicationWhenEndingSession(true)
            .setResumeSavedSession(true)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

/** Google Cast ile gönderme: cihaz bulma, oturum, yükleme ve kumanda. */
object CastBackend : Backend {
    private var app: Context? = null
    private var router: MediaRouter? = null
    private var routes = mapOf<String, MediaRouter.RouteInfo>()
    private var session: CastSession? = null
    private var client: RemoteMediaClient? = null
    private var pendingName: String? = null

    private val selector: MediaRouteSelector by lazy {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
    }

    private fun castContext(): CastContext? = app?.let { runCatching { CastContext.getSharedInstance(it) }.getOrNull() }

    fun init(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        val cc = castContext() ?: return // Google Play Hizmetleri yoksa yalnızca DLNA çalışır
        cc.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        router = MediaRouter.getInstance(ctx.applicationContext)
        router?.addCallback(selector, routerCb, 0)
        refreshRoutes()
        cc.sessionManager.currentCastSession?.let { attach(it) }
    }

    fun startScan() {
        router?.addCallback(selector, routerCb, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN or MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        refreshRoutes()
    }

    fun stopScan() {
        router?.addCallback(selector, routerCb, 0)
    }

    private val routerCb = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes()
    }

    private fun refreshRoutes() {
        val r = router ?: return
        val list = r.routes.filter { !it.isDefault && !it.isBluetooth && it.isEnabled && it.matchesSelector(selector) }
        routes = list.associateBy { it.id }
        Remote.setCastDevices(
            list.map { route ->
                val dev = CastDevice.getFromBundle(route.extras)
                TvDevice(route.id, route.name, TvType.CAST, route.description ?: "Google Cast", dev?.inetAddress?.hostAddress)
            },
        )
    }

    fun connect(d: TvDevice) {
        val route = routes[d.id] ?: return Remote.connectFailed("Cihaz artık görünmüyor")
        pendingName = d.name
        router?.selectRoute(route)
    }

    override fun disconnect() {
        detach()
        castContext()?.sessionManager?.endCurrentSession(true)
        router?.unselect(MediaRouter.UNSELECT_REASON_DISCONNECTED)
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(s: CastSession, sessionId: String) = attach(s)
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) = attach(s)
        override fun onSessionEnded(s: CastSession, error: Int) {
            if (session === s) {
                detach()
                Remote.detached()
            }
        }
        override fun onSessionResumeFailed(s: CastSession, error: Int) {}
        override fun onSessionStartFailed(s: CastSession, error: Int) =
            Remote.connectFailed("TV'ye bağlanılamadı (${CastStatusCodes.getStatusCodeString(error)})")
        override fun onSessionStarting(s: CastSession) {}
        override fun onSessionEnding(s: CastSession) {}
        override fun onSessionResuming(s: CastSession, sessionId: String) {}
        override fun onSessionSuspended(s: CastSession, reason: Int) {}
    }

    private val castListener = object : Cast.Listener() {
        override fun onVolumeChanged() = update()
    }

    private fun attach(s: CastSession) {
        if (session === s && client != null) return update()
        detach()
        session = s
        runCatching { s.addCastListener(castListener) }
        client = s.remoteMediaClient?.also {
            it.registerCallback(clientCb)
            it.addProgressListener(progressListener, 1000)
        }
        val dev = s.castDevice
        val name = dev?.friendlyName ?: pendingName ?: "Google TV"
        pendingName = null
        Remote.attach(TvDevice(dev?.deviceId ?: name, name, TvType.CAST, dev?.modelName ?: "Google Cast", dev?.inetAddress?.hostAddress), this)
        update()
    }

    private fun detach() {
        client?.let { runCatching { it.unregisterCallback(clientCb); it.removeProgressListener(progressListener) } }
        session?.let { runCatching { it.removeCastListener(castListener) } }
        client = null
        session = null
    }

    private val clientCb = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = update()
        override fun onMetadataUpdated() = update()
    }

    private val progressListener = RemoteMediaClient.ProgressListener { progressMs, durationMs ->
        Remote.report { it.copy(positionMs = progressMs, durationMs = if (durationMs > 0) durationMs else it.durationMs) }
    }

    private fun update() {
        val s = session ?: return
        val c = client
        val ms = c?.mediaStatus
        val player = ms?.playerState ?: MediaStatus.PLAYER_STATE_UNKNOWN
        val finished = player == MediaStatus.PLAYER_STATE_IDLE && ms?.idleReason == MediaStatus.IDLE_REASON_FINISHED
        Remote.report { st ->
            st.copy(
                playing = player == MediaStatus.PLAYER_STATE_PLAYING,
                buffering = player == MediaStatus.PLAYER_STATE_BUFFERING || player == MediaStatus.PLAYER_STATE_LOADING,
                idle = ms == null || player == MediaStatus.PLAYER_STATE_IDLE,
                positionMs = if (finished) st.durationMs else c?.approximateStreamPosition?.takeIf { it > 0 } ?: st.positionMs,
                durationMs = c?.streamDuration?.takeIf { it > 0 } ?: st.durationMs,
                volume = runCatching { s.volume.toFloat() }.getOrDefault(st.volume),
                muted = runCatching { s.isMute }.getOrDefault(st.muted),
            )
        }
    }

    // ---------------- Backend ----------------

    override fun load(e: Entry, url: String, mime: String, subs: List<Pair<String, Sub>>, startMs: Long, base: String) {
        val c = client ?: throw IllegalStateException("TV bağlantısı yok")
        val type = when (e.kind) {
            Kind.IMAGE -> MediaMetadata.MEDIA_TYPE_PHOTO
            Kind.AUDIO -> MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
            else -> MediaMetadata.MEDIA_TYPE_MOVIE
        }
        val meta = MediaMetadata(type).apply {
            putString(MediaMetadata.KEY_TITLE, e.title)
            e.artist?.let { putString(MediaMetadata.KEY_ARTIST, it) }
            e.album?.let { putString(MediaMetadata.KEY_ALBUM_TITLE, it) }
            addImage(WebImage(Uri.parse(if (e.kind == Kind.IMAGE) url else "$base/t/${com.akis.app.dlna.Xml.enc(e.id)}.jpg")))
        }
        val tracks = subs.mapIndexed { i, (u, sub) ->
            MediaTrack.Builder((i + 1).toLong(), MediaTrack.TYPE_TEXT)
                .setName(sub.label)
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(u)
                .setContentType("text/vtt")
                .setLanguage(sub.lang.ifEmpty { "und" })
                .build()
        }
        val info = MediaInfo.Builder(url)
            .setStreamType(if (e.kind == Kind.IMAGE) MediaInfo.STREAM_TYPE_NONE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mime)
            .setMetadata(meta)
            .apply { if (tracks.isNotEmpty()) setMediaTracks(tracks) }
            .build()
        val req = MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setAutoplay(true)
            .setCurrentTime(startMs)
            .apply { if (tracks.isNotEmpty()) setActiveTrackIds(longArrayOf(1)) }
            .build()
        if (tracks.isNotEmpty()) runCatching {
            c.setTextTrackStyle(
                TextTrackStyle().apply {
                    fontScale = 1.1f
                    foregroundColor = 0xFFFFFFFF.toInt()
                    backgroundColor = 0x00000000
                    edgeType = TextTrackStyle.EDGE_TYPE_OUTLINE
                    edgeColor = 0xFF000000.toInt()
                },
            )
        }
        c.load(req).setResultCallback { r ->
            if (!r.status.isSuccess) {
                Remote.message.value = "TV bunu oynatamadı (${r.status.statusMessage ?: CastStatusCodes.getStatusCodeString(r.status.statusCode)})"
            }
        }
    }

    override fun pause() { client?.pause() }
    override fun resume() { client?.play() }
    override fun stop() { client?.stop() }

    override fun seek(ms: Long) {
        client?.seek(MediaSeekOptions.Builder().setPosition(ms).build())
    }

    override fun setVolume(v: Float) {
        runCatching { session?.volume = v.toDouble() }
    }

    override fun setMuted(m: Boolean) {
        runCatching { session?.isMute = m }
    }
}
