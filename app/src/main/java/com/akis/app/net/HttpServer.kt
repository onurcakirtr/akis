package com.akis.app.net

import android.os.Build
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
)

class HttpResponder(private val out: OutputStream, private val head: Boolean) {
    var committed = false
        private set

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
            206 -> "Partial Content"
            304 -> "Not Modified"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
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
                try { p.execute { handle(s) } } catch (e: Exception) { s.close() }
            }
        }
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
            val output = BufferedOutputStream(sock.getOutputStream(), 64 * 1024)
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
                val len = headers["content-length"]?.toIntOrNull() ?: 0
                val body = if (len > 0) readFully(input, len) ?: return else ByteArray(0)
                val target = parts[1]
                val q = target.indexOf('?')
                val req = HttpRequest(
                    method = parts[0].uppercase(),
                    path = if (q >= 0) target.substring(0, q) else target,
                    query = if (q >= 0) target.substring(q + 1) else "",
                    headers = headers,
                    body = body,
                    remote = remote,
                )
                val resp = HttpResponder(output, req.method == "HEAD")
                try {
                    handler(req, resp)
                } catch (e: IOException) {
                    return // istemci bağlantıyı kesti
                } catch (e: Exception) {
                    if (resp.committed) return
                    try { resp.text(500, "Hata: ${e.message}") } catch (_: IOException) { return }
                }
                if (!resp.committed) {
                    try { resp.text(404, "Bulunamadı") } catch (_: IOException) { return }
                }
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
