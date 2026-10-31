package com.akis.app

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.io.IOException
import java.io.OutputStream
import kotlin.concurrent.thread

/**
 * HD canlı yayın (UPnP "Canlı" klasörü): kamera ya da ekran donanımda H.264'e, (açıksa) mikrofon AAC'ye kodlanır,
 * MPEG-TS olarak akıtılır. Kaynak başına tek kodlayıcı çalışır; tüm izleyiciler aynı yayını alır.
 * Yeni bağlanan izleyici bir sonraki anahtar karede (en geç ~1 sn) katılır. İzleyen kalmayınca her şey kapanır.
 */
object LiveTs {
    private class Chunk(val seq: Long, val key: Boolean, val data: ByteArray)

    private class Session(val source: String) {
        val lock = java.lang.Object()
        val ring = ArrayDeque<Chunk>()
        var seq = 0L
        var viewers = 0
        @Volatile var alive = true
        var video: MediaCodec? = null
        var audio: MediaCodec? = null
        var mux: TsMuxer? = null
        var config: ByteArray? = null
        var startUs = 0L
        var videoDelta: Long? = null
        var camThread: HandlerThread? = null
        var camera: CameraDevice? = null
        var sink: ((ByteArray, Long) -> Unit)? = null
        var screenSurface: Surface? = null
        var error: String? = null

        fun push(key: Boolean, data: ByteArray) = synchronized(lock) {
            ring.addLast(Chunk(++seq, key, data))
            while (ring.size > 600) ring.removeFirst()
            lock.notifyAll()
        }
    }

    private val sessions = HashMap<String, Session>()

    fun allowed(ctx: Context, source: String) = when (source) {
        "cam" -> CameraStream.allowed(ctx)
        "screen" -> ScreenStream.allowed()
        else -> false
    }

    private fun bitrate(): Int {
        val s = Prefs.current
        val base = if (s.liveRes >= 1080) 6_000_000 else 3_000_000
        return when (s.liveBitrate) {
            "low" -> base / 2
            "high" -> base * 5 / 3
            else -> base
        }
    }

    /** İzleyicinin bağlantısı koptuğunda biter. */
    fun stream(ctx: Context, source: String, who: String, out: OutputStream) {
        val s = synchronized(sessions) {
            val cur = sessions[source]?.takeIf { it.alive }
            val sess = cur ?: Session(source).also { sessions[source] = it }
            synchronized(sess.lock) { sess.viewers++ }
            if (cur == null) thread(name = "akis-live-$source", isDaemon = true) { start(ctx, sess, who) }
            sess
        }
        try {
            val joinSeq = synchronized(s.lock) { s.seq }
            requestKey(s)
            var next = -1L
            val deadline = System.currentTimeMillis() + if (source == "screen") 75_000 else 15_000
            while (true) {
                val batch: List<Chunk> = synchronized(s.lock) {
                    while (true) {
                        if (!s.alive) throw IOException(s.error ?: "ended")
                        if (next < 0) {
                            // Bağlandıktan sonraki ilk anahtar kareyi bekle (eski kareler gecikme yaratmasın)
                            val k = s.ring.lastOrNull { it.key && it.seq > joinSeq }
                            if (k != null) { next = k.seq; break }
                            val left = deadline - System.currentTimeMillis()
                            if (left <= 0) throw IOException("no keyframe")
                            s.lock.wait(minOf(left, 500))
                        } else {
                            val first = s.ring.firstOrNull()
                            if (first != null && first.seq > next) {
                                // Çok geride kaldı: son anahtar kareye atla
                                next = s.ring.lastOrNull { it.key }?.seq ?: s.seq
                            }
                            if (s.seq >= next) break
                            s.lock.wait(3000)
                            if (s.seq < next) throw IOException("stalled")
                        }
                    }
                    val list = s.ring.filter { it.seq >= next }
                    next = (list.lastOrNull()?.seq ?: (next - 1)) + 1
                    list
                }
                for (c in batch) out.write(c.data)
                out.flush()
            }
        } finally {
            val idle = synchronized(s.lock) { --s.viewers <= 0 }
            if (idle) thread(isDaemon = true) {
                Thread.sleep(5000)
                if (synchronized(s.lock) { s.viewers <= 0 }) stop(s)
            }
        }
    }

    private fun requestKey(s: Session) {
        runCatching { s.video?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }) }
    }

    private fun fail(s: Session, msg: String) {
        s.error = msg
        stop(s)
    }

    private fun start(ctx: Context, s: Session, who: String) {
        try {
            val wantAudio = MicStream.allowed(ctx)
            s.mux = TsMuxer(wantAudio)
            s.startUs = System.nanoTime() / 1000
            val (w, h) = when (s.source) {
                "cam" -> cameraSize(ctx) ?: return fail(s, "camera")
                else -> screenSize(ctx)
            }
            val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate())
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1)
                // Ekran durgunsa kodlayıcı son kareyi yinelesin; TV beklemede kalmasın
                setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000)
            }
            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = enc.createInputSurface()
            enc.start()
            s.video = enc
            when (s.source) {
                "cam" -> openCamera(ctx, s, surface)
                else -> {
                    if (!ScreenStream.attach(ctx, who, surface, w, h)) return fail(s, "screen")
                    s.screenSurface = surface
                }
            }
            if (wantAudio) startAudio(s)
            ServerState.log("📡 HD ${if (s.source == "cam") "📷" else "🖥"} → $who")
            drainVideo(s, enc)
        } catch (e: Exception) {
            fail(s, e.message ?: "encoder")
        }
    }

    // ---------------- Video ----------------

    private fun drainVideo(s: Session, enc: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (s.alive) try {
            val i = enc.dequeueOutputBuffer(info, 20_000)
            if (i < 0) continue
            val buf = enc.getOutputBuffer(i)
            if (buf != null && info.size > 0) {
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                val data = ByteArray(info.size).also { buf.get(it) }
                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    s.config = data
                } else {
                    val key = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                    val delta = s.videoDelta ?: (System.nanoTime() / 1000 - info.presentationTimeUs).also { s.videoDelta = it }
                    val pts = pts90(s, info.presentationTimeUs + delta)
                    val au = if (key && s.config != null && !startsWithSps(data)) s.config!! + data else data
                    val mux = s.mux!!
                    val ts = synchronized(mux) { if (key) mux.psi() + mux.video(au, pts, true) else mux.video(au, pts, false) }
                    s.push(key, ts)
                }
            }
            enc.releaseOutputBuffer(i, false)
        } catch (_: Exception) {
            return
        }
    }

    private fun startsWithSps(d: ByteArray): Boolean {
        val i = if (d.size > 4 && d[2].toInt() == 1) 3 else 4
        return d.size > i && (d[i].toInt() and 0x1F) == 7
    }

    private fun pts90(s: Session, us: Long) = maxOf(0L, (us - s.startUs + 700_000) * 9 / 100)

    // ---------------- Ses (mikrofon → AAC) ----------------

    private fun startAudio(s: Session) {
        val rate = MicStream.RATE
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 48_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
        s.audio = enc
        val sink: (ByteArray, Long) -> Unit = sink@{ pcm, capturedUs ->
            if (!s.alive) return@sink
            try {
                val i = enc.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val b = enc.getInputBuffer(i) ?: return@sink
                    b.clear()
                    val n = minOf(pcm.size, b.remaining())
                    b.put(pcm, 0, n)
                    enc.queueInputBuffer(i, 0, n, capturedUs, 0)
                }
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val o = enc.dequeueOutputBuffer(info, 0)
                    if (o < 0) break
                    val ob = enc.getOutputBuffer(o)
                    if (ob != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val aac = ByteArray(info.size).also { ob.get(it) }
                        val mux = s.mux!!
                        val ts = synchronized(mux) { mux.audio(aac, pts90(s, info.presentationTimeUs), rate, 1) }
                        s.push(false, ts)
                    }
                    enc.releaseOutputBuffer(o, false)
                }
            } catch (_: Exception) {
            }
        }
        s.sink = sink
        MicStream.attach(sink)
    }

    // ---------------- Kaynaklar ----------------

    private fun cameraId(cm: CameraManager, front: Boolean): String? = cm.cameraIdList.firstOrNull {
        val f = cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)
        f == if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
    } ?: cm.cameraIdList.firstOrNull()

    /** Ayardaki yüksekliğe (720/1080) en yakın, kodlayıcıya verilebilen kamera boyutu (16:9 öncelikli). */
    private fun cameraSize(ctx: Context): Pair<Int, Int>? {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cameraId(cm, Prefs.current.camFront) ?: return null
        val sizes = cm.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(MediaCodec::class.java) ?: return null
        val target = Prefs.current.liveRes
        val fit = sizes.filter { it.height <= target && it.width % 16 == 0 && it.height % 16 == 0 }
        val wide = fit.filter { it.width * 9 == it.height * 16 }
        val best = (wide.ifEmpty { fit }).maxByOrNull { it.width * it.height } ?: sizes.minByOrNull { it.width * it.height } ?: return null
        return best.width to best.height
    }

    private fun screenSize(ctx: Context): Pair<Int, Int> {
        val m = ctx.resources.displayMetrics
        val long = if (Prefs.current.liveRes >= 1080) 1920f else 1280f
        val scale = minOf(1f, long / maxOf(m.widthPixels, m.heightPixels))
        return ((m.widthPixels * scale).toInt() / 16 * 16) to ((m.heightPixels * scale).toInt() / 16 * 16)
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(ctx: Context, s: Session, surface: Surface) {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cameraId(cm, Prefs.current.camFront) ?: throw IOException("camera")
        val t = HandlerThread("akis-live-cam").also { it.start() }
        s.camThread = t
        val h = Handler(t.looper)
        cm.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(cam: CameraDevice) {
                if (!s.alive) { cam.close(); return }
                s.camera = cam
                ServerState.cameraLive.value = true
                @Suppress("DEPRECATION")
                cam.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cs: CameraCaptureSession) {
                        val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        }
                        runCatching { cs.setRepeatingRequest(req.build(), null, h) }
                    }
                    override fun onConfigureFailed(cs: CameraCaptureSession) = fail(s, "camera")
                }, h)
            }
            override fun onDisconnected(cam: CameraDevice) = fail(s, "camera")
            override fun onError(cam: CameraDevice, error: Int) = fail(s, "camera")
        }, h)
    }

    private fun stop(s: Session) {
        synchronized(s.lock) {
            if (!s.alive) return
            s.alive = false
            s.lock.notifyAll()
        }
        synchronized(sessions) { if (sessions[s.source] === s) sessions.remove(s.source) }
        s.sink?.let { MicStream.detach(it) }
        runCatching { s.camera?.close() }
        if (s.camera != null) ServerState.cameraLive.value = false
        s.camThread?.quitSafely()
        if (s.screenSurface != null) ScreenStream.detach()
        runCatching { s.video?.stop() }
        runCatching { s.video?.release() }
        runCatching { s.audio?.stop() }
        runCatching { s.audio?.release() }
        runCatching { s.screenSurface?.release() }
    }
}
