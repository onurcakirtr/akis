package com.akis.app.net

import android.os.Build
import com.akis.app.Lang
import com.akis.app.R
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class HttpRequest(
    val method: String,
    val path: String,
    val query: String,
    val headers: Map<String, String>, // anahtarlar küçük harf
    val body: ByteArray,
    val remote: String,
    /** Cloudflare Tunnel üzerinden geldi (remote = ziyaretçinin gerçek IP'si). */
    val tunnel: Boolean = false,
    /** PUT isteklerinde gövde belleğe alınmaz; buradan akıtılır (contentLength bayt). */
    val bodyStream: InputStream? = null,
    val contentLength: Long = 0,
)

/** Alttaki akıştan en fazla [limit] bayt okutan sarmalayıcı (bağlantıyı kapatmaz). */
class LimitedInputStream(private val input: InputStream, private var left: Long) : InputStream() {
    val remaining: Long get() = left

    override fun read(): Int {
        if (left <= 0) return -1
        val b = input.read()
        if (b >= 0) left--
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (left <= 0) return -1
        val n = input.read(b, off, minOf(len.toLong(), left).toInt())
        if (n > 0) left -= n
        return n
    }

    override fun close() {}
}

/**
 * Gönderimi saniyede en çok [bytesPerSec] bayta sınırlar (0 = sınırsız).
 * Boşta kalınan süre en fazla 1 saniyelik birikmiş hak sayılır; ani patlama olmaz.
 */
class ThrottledOutputStream(private val out: OutputStream) : OutputStream() {
    @Volatile var bytesPerSec = 0L
        set(v) {
            if (field != v) { field = v; start = System.currentTimeMillis(); sent = 0 }
        }
    private var start = System.currentTimeMillis()
    private var sent = 0L

    override fun write(b: Int) {
        pace(1)
        out.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var o = off
        var left = len
        while (left > 0) {
            val n = if (bytesPerSec > 0) minOf(left, 16 * 1024) else left
            pace(n)
            out.write(b, o, n)
            o += n
            left -= n
        }
    }

    private fun pace(n: Int) {
        val r = bytesPerSec
        if (r <= 0) return
        sent += n
        val due = sent * 1000 / r
        val now = System.currentTimeMillis()
        val elapsed = now - start
        if (elapsed > due + 1000) start = now - due - 1000
        else if (due > elapsed) Thread.sleep(due - elapsed)
    }

    override fun flush() = out.flush()

    override fun close() = out.close()
}

class HttpResponder(private val out: OutputStream, private val head: Boolean) {
    var committed = false
        private set
    /** Uzunluğu belirsiz akıştan sonra bağlantı kapatılır. */
    var closeAfter = false
        private set

    /** Content-Length olmadan akıtır (HTTP/1.1 "Connection: close"); istemci koparsa IOException fırlar. */
    fun stream(code: Int, type: String, headers: Map<String, String> = emptyMap(), body: (OutputStream) -> Unit) {
        committed = true
        closeAfter = true
        val sb = StringBuilder("HTTP/1.1 ").append(code).append(' ').append(reason(code)).append("\r\n")
        sb.append("Content-Type: ").append(type).append("\r\n")
        sb.append("Server: Android/").append(Build.VERSION.RELEASE).append(" UPnP/1.0 Akis/1.0\r\n")
        sb.append("Date: ").append(httpDate()).append("\r\n")
        sb.append("Connection: close\r\n")
        for ((k, v) in headers) sb.append(k).append(": ").append(v).append("\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        out.flush()
        if (!head) body(out)
        out.flush()
    }

    fun send(
        code: Int,
        type: String?,
        length: Long,
        headers: Map<String, String> = emptyMap(),
        body: ((OutputStream) -> Unit)? = null,
    ) {
        committed = true
        val sb = StringBuilder("HTTP/1.1 ").append(code).append(' ').append(reason(code)).append("\r\n")
        if (type != null) sb.append("Content-Type: ").append(type).append("\r\n")
        sb.append("Content-Length: ").append(length).append("\r\n")
        sb.append("Server: Android/").append(Build.VERSION.RELEASE).append(" UPnP/1.0 Akis/1.0\r\n")
        sb.append("Date: ").append(httpDate()).append("\r\n")
        sb.append("Connection: keep-alive\r\n")
        for ((k, v) in headers) sb.append(k).append(": ").append(v).append("\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        if (!head && body != null) body(out)
        out.flush()
    }

    fun bytes(code: Int, type: String, data: ByteArray, headers: Map<String, String> = emptyMap()) =
        send(code, type, data.size.toLong(), headers) { it.write(data) }

    fun text(code: Int, s: String, type: String = "text/plain; charset=utf-8") =
        bytes(code, type, s.toByteArray(Charsets.UTF_8))

    fun html(s: String) = text(200, s, "text/html; charset=utf-8")

    fun xml(s: String) = text(200, s, "text/xml; charset=\"utf-8\"")

    companion object {
        private val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }

        fun httpDate(): String = synchronized(fmt) { fmt.format(Date()) }

        fun reason(code: Int) = when (code) {
            200 -> "OK"
            204 -> "No Content"
            206 -> "Partial Content"
            303 -> "See Other"
            304 -> "Not Modified"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            410 -> "Gone"
            413 -> "Payload Too Large"
            412 -> "Precondition Failed"
            416 -> "Range Not Satisfiable"
            500 -> "Internal Server Error"
            else -> "OK"
        }
    }
}

/** Bağımlılıksız, keep-alive destekli küçük HTTP/1.1 sunucusu. */
class HttpServer(
    private val port: Int,
    private val handler: (HttpRequest, HttpResponder) -> Unit,
    /** İstek başına gönderim sınırı (bayt/sn; 0 = sınırsız). */
    private val rateFor: (HttpRequest) -> Long = { 0L },
) {
    @Volatile private var server: ServerSocket? = null
    private var pool: ExecutorService? = null

    fun start() {
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(port))
        server = ss
        val p = Executors.newCachedThreadPool()
        pool = p
        thread(name = "http-accept", isDaemon = true) {
            while (!ss.isClosed) {
                val s = try { ss.accept() } catch (e: IOException) { break }
                try {
                    p.execute {
                        conns.add(s)
                        try { handle(s) } finally { conns.remove(s) }
                    }
                } catch (e: Exception) {
                    s.close()
                }
            }
        }
    }

    /** Açık bağlantılar (cihaz atılırken kapatılır). */
    private val conns = java.util.concurrent.ConcurrentHashMap.newKeySet<Socket>()

    /** Bir IP'nin tüm açık bağlantılarını (süren akışlar dahil) hemen keser. */
    fun closeClient(ip: String) {
        for (s in conns.toList()) if (s.inetAddress?.hostAddress == ip) runCatching { s.close() }
    }

    fun stop() {
        try { server?.close() } catch (_: IOException) {}
        server = null
        pool?.shutdownNow()
        pool = null
    }

    private fun handle(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 60_000
            sock.tcpNoDelay = true
            val input = BufferedInputStream(sock.getInputStream())
            val throttle = ThrottledOutputStream(sock.getOutputStream())
            val output = BufferedOutputStream(throttle, 64 * 1024)
            val remote = sock.inetAddress?.hostAddress ?: "?"
            while (true) {
                val line = try { readLine(input) } catch (e: IOException) { null } ?: return
                if (line.isEmpty()) continue
                val parts = line.split(' ')
                if (parts.size < 2) return
                val headers = HashMap<String, String>()
                while (true) {
                    val h = try { readLine(input) } catch (e: IOException) { null } ?: return
                    if (h.isEmpty()) break
                    val i = h.indexOf(':')
                    if (i > 0) headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
                }
                val method = parts[0].uppercase()
                val len = headers["content-length"]?.toLongOrNull() ?: 0
                val streaming = method == "PUT"
                if (streaming && headers["transfer-encoding"]?.contains("chunked", true) == true) return
                val stream = if (streaming) LimitedInputStream(input, len) else null
                if (!streaming && len > 4 * 1024 * 1024) return
                val body = if (!streaming && len > 0) readFully(input, len.toInt()) ?: return else ByteArray(0)
                val target = parts[1]
                val q = target.indexOf('?')
                val cfIp = headers["cf-connecting-ip"]?.trim()?.takeIf { it.isNotEmpty() && it.length < 64 }
                val viaTunnel = cfIp != null && (sock.inetAddress?.isLoopbackAddress == true)
                val req = HttpRequest(
                    method = method,
                    path = if (q >= 0) target.substring(0, q) else target,
                    query = if (q >= 0) target.substring(q + 1) else "",
                    headers = headers,
                    body = body,
                    remote = if (viaTunnel) cfIp else remote,
                    tunnel = viaTunnel,
                    bodyStream = stream,
                    contentLength = if (streaming) len else body.size.toLong(),
                )
                throttle.bytesPerSec = rateFor(req)
                val resp = HttpResponder(output, req.method == "HEAD")
                try {
                    handler(req, resp)
                } catch (e: IOException) {
                    return // istemci bağlantıyı kesti
                } catch (e: Exception) {
                    if (resp.committed) return
                    try { resp.text(500, Lang.s(R.string.http_error, e.message ?: "")) } catch (_: IOException) { return }
                }
                if (!resp.committed) {
                    try { resp.text(404, Lang.s(R.string.http_not_found)) } catch (_: IOException) { return }
                }
                if (stream != null && stream.remaining > 0) return // gövde yarım kaldı; bağlantıyı kapat
                if (resp.closeAfter) return
                val conn = headers["connection"]?.lowercase()
                val http10 = parts.getOrNull(2) == "HTTP/1.0"
                if (conn == "close" || (http10 && conn != "keep-alive")) return
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream(128)
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 16 * 1024) throw IOException("Satır çok uzun")
        }
        return buf.toString("UTF-8")
    }

    private fun readFully(input: InputStream, len: Int): ByteArray? {
        if (len > 4 * 1024 * 1024) return null
        val data = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = input.read(data, off, len - off)
            if (n < 0) return null
            off += n
        }
        return data
    }
}
