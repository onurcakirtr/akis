package com.akis.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.OutputStream
import kotlin.concurrent.thread

/**
 * Canlı mikrofon (bebek telsizi): 16 kHz mono WAV akışı. Mikrofon yalnızca biri dinlerken açıktır;
 * açıkken "Mikrofon açık" bildirimde gösterilir.
 */
object MicStream {
    const val RATE = 16_000
    private const val CHUNK = RATE / 10 * 2 // 100 ms, 16 bit
    private val lock = java.lang.Object()
    /** Son ~3 saniye: ağ takılınca dinleyici araya giren parçaları da alır. */
    private val ring = arrayOfNulls<ByteArray>(32)
    @Volatile private var chunkNo = 0L
    @Volatile private var listeners = 0
    @Volatile private var running = false

    val live = MutableStateFlow(false)

    fun allowed(ctx: Context) = Prefs.current.mic &&
        ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        if (running) return
        running = true
        thread(name = "akis-mic", isDaemon = true) {
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, CHUNK * 4))
            } catch (_: Exception) {
                null
            }
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                rec?.release()
                running = false
                synchronized(lock) { lock.notifyAll() }
                return@thread
            }
            live.value = true
            ServerState.log(Lang.s(R.string.log_mic_on))
            try {
                rec.startRecording()
                while (running && Prefs.current.mic) {
                    val buf = ByteArray(CHUNK)
                    var off = 0
                    while (off < CHUNK) {
                        val n = rec.read(buf, off, CHUNK - off)
                        if (n <= 0) break
                        off += n
                    }
                    if (off <= 0) break
                    synchronized(lock) {
                        chunkNo++
                        ring[(chunkNo % ring.size).toInt()] = if (off == CHUNK) buf else buf.copyOf(off)
                        lock.notifyAll()
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { rec.stop() }
                rec.release()
                running = false
                live.value = false
                synchronized(lock) { lock.notifyAll() }
                ServerState.log(Lang.s(R.string.log_mic_off))
            }
        }
    }

    fun stop() {
        running = false
    }

    /** Uzunluğu belirsiz WAV başlığı (oynatıcılar canlı akış gibi çalar). */
    private fun header(): ByteArray {
        val b = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(-1).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(-1)
        return b.array()
    }

    /** Dinleyici bağlantıyı kesince biter. */
    fun stream(out: OutputStream) {
        synchronized(lock) {
            listeners++
            startRecording()
        }
        try {
            out.write(header())
            out.flush()
            var seen = synchronized(lock) { chunkNo }
            while (true) {
                val parts: List<ByteArray> = synchronized(lock) {
                    val end = System.currentTimeMillis() + 3000
                    while (chunkNo == seen) {
                        val left = end - System.currentTimeMillis()
                        if (left <= 0) throw java.io.IOException("mic")
                        lock.wait(left)
                    }
                    val from = maxOf(seen + 1, chunkNo - ring.size + 1)
                    seen = chunkNo
                    (from..seen).mapNotNull { ring[(it % ring.size).toInt()] }
                }
                for (d in parts) out.write(d)
                out.flush()
            }
        } finally {
            synchronized(lock) {
                listeners--
                if (listeners <= 0) {
                    listeners = 0
                    Thread {
                        Thread.sleep(3000)
                        if (listeners == 0) stop()
                    }.start()
                }
            }
        }
    }
}
