package com.akis.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Web'den canlı kamera (MJPEG). Kamera yalnızca biri izlerken açıktır; izleyen kalmayınca kapanır.
 * Açıkken ServerState.cameraLive = true olur ve telefondaki bildirimde gösterilir.
 */
object CameraStream {
    private val lock = java.lang.Object()
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var config = ""
    @Volatile private var frame: ByteArray? = null
    @Volatile private var frameNo = 0L
    @Volatile private var viewers = 0
    private var lastFrameAt = 0L

    fun allowed(ctx: Context) = Prefs.current.camera &&
        ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun cameraId(cm: CameraManager, front: Boolean): String? = cm.cameraIdList.firstOrNull {
        val f = cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)
        f == if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
    } ?: cm.cameraIdList.firstOrNull()

    @SuppressLint("MissingPermission")
    private fun open(ctx: Context, front: Boolean, height: Int) {
        val key = "$front|$height"
        if (device != null && config == key) return
        close()
        config = key
        val t = HandlerThread("akis-cam").also { it.start() }
        thread = t
        val h = Handler(t.looper)
        handler = h
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cameraId(cm, front) ?: return
        val sizes = cm.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.YUV_420_888) ?: return
        // İstenen yüksekliğe en yakın 4:3 / 16:9 boyut
        val size = sizes.filter { it.height <= height }.maxByOrNull { it.width * it.height } ?: sizes.minByOrNull { it.width * it.height } ?: return
        val r = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 3)
        reader = r
        r.setOnImageAvailableListener({ ir ->
            val img = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (now - lastFrameAt < 80) return@setOnImageAvailableListener // en fazla ~12 kare/sn
                lastFrameAt = now
                val nv21 = toNv21(img)
                val o = ByteArrayOutputStream()
                YuvImage(nv21, ImageFormat.NV21, img.width, img.height, null).compressToJpeg(Rect(0, 0, img.width, img.height), 70, o)
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
        cm.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(cam: CameraDevice) {
                device = cam
                ServerState.cameraLive.value = true
                @Suppress("DEPRECATION")
                cam.createCaptureSession(listOf(r.surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(r.surface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        }
                        runCatching { s.setRepeatingRequest(req.build(), null, h) }
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) = close()
                }, h)
            }
            override fun onDisconnected(cam: CameraDevice) = close()
            override fun onError(cam: CameraDevice, error: Int) = close()
        }, h)
    }

    /** YUV_420_888 → NV21 (satır adımı ve piksel adımı gözetilerek). */
    private fun toNv21(img: android.media.Image): ByteArray {
        val w = img.width
        val h = img.height
        val out = ByteArray(w * h * 3 / 2)
        val y = img.planes[0]
        var pos = 0
        val yb = y.buffer
        for (row in 0 until h) {
            yb.position(row * y.rowStride)
            yb.get(out, pos, w)
            pos += w
        }
        val u = img.planes[1]
        val v = img.planes[2]
        val ub = u.buffer
        val vb = v.buffer
        for (row in 0 until h / 2) {
            for (col in 0 until w / 2) {
                val ui = row * u.rowStride + col * u.pixelStride
                val vi = row * v.rowStride + col * v.pixelStride
                out[pos++] = vb.get(vi)
                out[pos++] = ub.get(ui)
            }
        }
        return out
    }

    fun close() {
        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { reader?.close() }
        thread?.quitSafely()
        session = null; device = null; reader = null; thread = null; handler = null
        config = ""
        frame = null
        ServerState.cameraLive.value = false
    }

    /** MJPEG akışı; istemci bağlantıyı kesince biter. */
    fun stream(ctx: Context, front: Boolean, height: Int, out: OutputStream) {
        synchronized(lock) {
            viewers++
            open(ctx, front, height)
        }
        try {
            var seen = -1L
            while (true) {
                val data: ByteArray = synchronized(lock) {
                    val end = System.currentTimeMillis() + 5000
                    while (frameNo == seen || frame == null) {
                        val left = end - System.currentTimeMillis()
                        if (left <= 0) throw java.io.IOException("kare gelmedi")
                        lock.wait(left)
                    }
                    seen = frameNo
                    frame!!
                }
                out.write("--akisframe\r\nContent-Type: image/jpeg\r\nContent-Length: ${data.size}\r\n\r\n".toByteArray())
                out.write(data)
                out.write("\r\n".toByteArray())
                out.flush()
                if (!Prefs.current.camera) break
            }
        } finally {
            synchronized(lock) {
                viewers--
                if (viewers <= 0) {
                    viewers = 0
                    // Kısa süreli yeniden bağlanmalar için 5 sn bekle
                    Thread {
                        Thread.sleep(5000)
                        synchronized(lock) { if (viewers == 0) close() }
                    }.start()
                }
            }
        }
    }

    /** Tek kare (anlık fotoğraf). */
    fun snapshot(): ByteArray? = frame
}
