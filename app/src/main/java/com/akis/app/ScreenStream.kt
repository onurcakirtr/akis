package com.akis.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import androidx.core.app.NotificationCompat
import com.akis.app.ui.ScreenConsentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Ekran paylaşımı (MJPEG). Biri izlemek isteyince telefonda bildirim çıkar; dokununca Android'in kendi
 * "Ekranı paylaşmaya başla?" penceresi açılır. Onay verilmeden hiçbir şey gönderilmez; izleyen kalmayınca paylaşım biter
 * ve bir sonraki sefer yeniden onay gerekir.
 */
object ScreenStream {
    private const val NOTIF_ASK = 3600
    private val lock = java.lang.Object()
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    @Volatile private var frame: ByteArray? = null
    @Volatile private var frameNo = 0L
    @Volatile private var viewers = 0
    /** HD yayın (LiveTs) bağlıyken ekran kodlayıcıya yansıtılır. */
    @Volatile private var holders = 0
    private var size = 0 to 0
    private var dpi = 160
    @Volatile private var askedAt = 0L
    @Volatile private var declined = false
    private var lastFrameAt = 0L

    /** Paylaşım sürüyor (bildirimde ve ön plan türünde gösterilir). */
    val live = MutableStateFlow(false)

    fun allowed() = Prefs.current.screen

    /** Telefonda onay iste (bildirim; arka plandan doğrudan pencere açılamıyor). */
    private fun ask(ctx: Context, from: String) {
        val now = System.currentTimeMillis()
        if (projection != null || now - askedAt < 60_000) return
        askedAt = now
        declined = false
        val open = PendingIntent.getActivity(
            ctx, NOTIF_ASK, Intent(ctx, ScreenConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, AkisApplication.CHANNEL_ACCESS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(Lang.s(R.string.screen_ask_title))
            .setContentText(Lang.s(R.string.screen_ask_text, from))
            .setColor(0xFFD71921.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setContentIntent(open)
            .addAction(0, Lang.s(R.string.allow), open)
            .build()
        try { ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ASK, n) } catch (_: SecurityException) {}
    }

    /** Kullanıcı reddetti: bekleyen izleyiciler hemen bırakılsın. */
    fun decline() {
        declined = true
        askedAt = 0
        synchronized(lock) { lock.notifyAll() }
    }

    /** Onay geldi (servis ön plan türünü güncelledikten sonra çağırır). */
    fun begin(ctx: Context, code: Int, data: Intent) {
        synchronized(lock) {
            if (projection != null) return
            val mpm = ctx.getSystemService(MediaProjectionManager::class.java)
            val p = try { mpm.getMediaProjection(code, data) } catch (_: Exception) { null } ?: return decline()
            val t = HandlerThread("akis-screen").also { it.start() }
            thread = t
            val h = Handler(t.looper)
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() = close()
            }, h)
            projection = p
            val m = ctx.resources.displayMetrics
            // Uzun kenar en fazla 1280 piksel
            val scale = minOf(1f, 1280f / maxOf(m.widthPixels, m.heightPixels))
            val w = (m.widthPixels * scale).toInt() and 1.inv()
            val hgt = (m.heightPixels * scale).toInt() and 1.inv()
            val r = ImageReader.newInstance(w, hgt, PixelFormat.RGBA_8888, 2)
            reader = r
            r.setOnImageAvailableListener({ ir ->
                val img = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val now = System.currentTimeMillis()
                    if (now - lastFrameAt < 100) return@setOnImageAvailableListener // en fazla ~10 kare/sn
                    lastFrameAt = now
                    val plane = img.planes[0]
                    val rowPx = plane.rowStride / plane.pixelStride
                    val full = Bitmap.createBitmap(rowPx, img.height, Bitmap.Config.ARGB_8888)
                    full.copyPixelsFromBuffer(plane.buffer)
                    val bmp = if (rowPx == img.width) full else Bitmap.createBitmap(full, 0, 0, img.width, img.height).also { full.recycle() }
                    val o = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 65, o)
                    bmp.recycle()
                    synchronized(lock) {
                        frame = o.toByteArray()
                        frameNo++
                        lock.notifyAll()
                    }
                } catch (_: Exception) {
                } finally {
                    img.close()
                }
            }, h)
            display = try {
                p.createVirtualDisplay("akis-screen", w, hgt, (m.densityDpi * scale).toInt().coerceAtLeast(120), DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, h)
            } catch (_: Exception) {
                null
            }
            if (display == null) { close(); return decline() }
            size = w to hgt
            dpi = (m.densityDpi * scale).toInt().coerceAtLeast(120)
            ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ASK)
            live.value = true
            ServerState.log(Lang.s(R.string.log_screen_on))
            lock.notifyAll()
        }
    }

    /**
     * HD yayın için ekranı [surface]'e yansıtır; paylaşım yoksa telefonda onay ister ve en çok 60 sn bekler.
     * Bağlıyken MJPEG izleyicileri son karede kalır.
     */
    fun attach(ctx: Context, from: String, surface: android.view.Surface, w: Int, h: Int): Boolean {
        synchronized(lock) {
            holders++
            if (projection == null) ask(ctx, from)
            val end = System.currentTimeMillis() + 60_000
            while (display == null) {
                val left = end - System.currentTimeMillis()
                if (left <= 0 || (declined && projection == null)) { holders--; return false }
                lock.wait(left)
            }
            val d = display!!
            runCatching { d.resize(w, h, dpi); d.surface = surface }
            return true
        }
    }

    fun detach() {
        synchronized(lock) {
            holders = (holders - 1).coerceAtLeast(0)
            if (holders == 0) display?.let { d -> runCatching { d.resize(size.first, size.second, dpi); d.surface = reader?.surface } }
        }
        if (viewers == 0 && holders == 0) Thread {
            Thread.sleep(5000)
            if (viewers == 0 && holders == 0) close()
        }.start()
    }

    fun close() {
        synchronized(lock) {
            runCatching { display?.release() }
            val p = projection
            projection = null
            runCatching { p?.stop() }
            runCatching { reader?.close() }
            thread?.quitSafely()
            display = null; reader = null; thread = null
            frame = null
            askedAt = 0
            holders = 0
            lock.notifyAll()
        }
        if (live.value) ServerState.log(Lang.s(R.string.log_screen_off))
        live.value = false
    }

    /** MJPEG akışı; istemci bağlantıyı kesince biter. Ekran değişmezse son kare tekrarlanır. */
    fun stream(ctx: Context, from: String, out: OutputStream) {
        synchronized(lock) {
            viewers++
            if (projection == null) ask(ctx, from)
        }
        try {
            var seen = -1L
            // İlk karede telefondaki onay beklenir (en çok 60 sn)
            var deadline = System.currentTimeMillis() + 60_000
            while (true) {
                val data: ByteArray = synchronized(lock) {
                    while (frame == null || frameNo == seen) {
                        if (declined && projection == null) throw java.io.IOException("declined")
                        val left = deadline - System.currentTimeMillis()
                        if (left <= 0) {
                            if (projection != null && frame != null) break // ekran durgun: son kareyi yinele
                            throw java.io.IOException("no frame")
                        }
                        lock.wait(left)
                    }
                    seen = frameNo
                    frame!!
                }
                deadline = System.currentTimeMillis() + 2_000
                out.write("--akisframe\r\nContent-Type: image/jpeg\r\nContent-Length: ${data.size}\r\n\r\n".toByteArray())
                out.write(data)
                out.write("\r\n".toByteArray())
                out.flush()
                if (!Prefs.current.screen) break
            }
        } finally {
            synchronized(lock) {
                viewers--
                if (viewers <= 0) {
                    viewers = 0
                    Thread {
                        Thread.sleep(8000)
                        if (viewers == 0 && holders == 0) close()
                    }.start()
                }
            }
        }
    }
}
