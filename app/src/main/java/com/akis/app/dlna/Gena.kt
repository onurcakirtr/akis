package com.akis.app.dlna

import com.akis.app.net.HttpRequest
import com.akis.app.net.HttpResponder
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * UPnP olay bildirimi (GENA): TV / VLC "bana haber ver" diye abone olur; kitaplık değişince
 * SystemUpdateID ile NOTIFY gönderilir, onlar da listeyi kendiliğinden yeniler.
 */
class Gena(private val updateId: () -> Int) {
    private class Sub(val sid: String, val path: String, val callbacks: List<URL>, @Volatile var expires: Long, var seq: Long = 0)

    private val subs = ConcurrentHashMap<String, Sub>()
    private val pool = Executors.newSingleThreadExecutor()

    /** SUBSCRIBE (yeni abonelik ya da yenileme). */
    fun subscribe(req: HttpRequest, res: HttpResponder) {
        val timeout = req.headers["timeout"]?.substringAfter("Second-", "")?.toLongOrNull()?.coerceIn(60, 3600) ?: 1800
        val sid = req.headers["sid"]
        if (sid != null) {
            val s = subs[sid] ?: return res.send(412, null, 0)
            s.expires = System.currentTimeMillis() + timeout * 1000
            return res.send(200, null, 0, mapOf("SID" to sid, "TIMEOUT" to "Second-$timeout"))
        }
        // CALLBACK: <http://ip:port/yol>; yalnızca isteği yapan cihazın kendi adresine bildirim gönderilir
        val callbacks = Regex("<([^>]+)>").findAll(req.headers["callback"] ?: "").mapNotNull {
            runCatching { URL(it.groupValues[1]) }.getOrNull()
        }.filter { it.protocol == "http" && it.host == req.remote }.toList()
        if (callbacks.isEmpty() || req.headers["nt"] != "upnp:event") return res.send(412, null, 0)
        val newSid = "uuid:" + UUID.randomUUID()
        val s = Sub(newSid, req.path, callbacks, System.currentTimeMillis() + timeout * 1000)
        subs[newSid] = s
        res.send(200, null, 0, mapOf("SID" to newSid, "TIMEOUT" to "Second-$timeout"))
        pool.execute { send(s) } // ilk olay: abonelikten hemen sonra mevcut durum
    }

    fun unsubscribe(req: HttpRequest, res: HttpResponder) {
        val sid = req.headers["sid"]
        if (sid == null || subs.remove(sid) == null) return res.send(412, null, 0)
        res.send(200, null, 0)
    }

    /** Kitaplık değişti: içerik dizini abonelerine haber ver. */
    fun changed() {
        val now = System.currentTimeMillis()
        subs.values.removeAll { it.expires < now }
        for (s in subs.values) if (s.path.endsWith("/cd")) pool.execute { send(s) }
    }

    fun stop() {
        subs.clear()
        pool.shutdownNow()
    }

    private fun body(s: Sub): String {
        val props = if (s.path.endsWith("/cd")) {
            "<e:property><SystemUpdateID>${updateId()}</SystemUpdateID></e:property><e:property><ContainerUpdateIDs></ContainerUpdateIDs></e:property>"
        } else {
            "<e:property><SourceProtocolInfo>http-get:*:video/*:*,http-get:*:audio/*:*,http-get:*:image/*:*</SourceProtocolInfo></e:property>" +
                "<e:property><SinkProtocolInfo></SinkProtocolInfo></e:property><e:property><CurrentConnectionIDs>0</CurrentConnectionIDs></e:property>"
        }
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?><e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">$props</e:propertyset>"
    }

    /** NOTIFY; HttpURLConnection bu yöntemi desteklemediği için düz soketle. */
    private fun send(s: Sub) {
        val data = body(s).toByteArray(Charsets.UTF_8)
        val seq = synchronized(s) { s.seq++ }
        for (cb in s.callbacks) {
            try {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(cb.host, if (cb.port > 0) cb.port else 80), 3000)
                    sock.soTimeout = 3000
                    val path = cb.file.ifEmpty { "/" }
                    val head = "NOTIFY $path HTTP/1.1\r\nHOST: ${cb.host}:${if (cb.port > 0) cb.port else 80}\r\n" +
                        "CONTENT-TYPE: text/xml; charset=\"utf-8\"\r\nCONTENT-LENGTH: ${data.size}\r\n" +
                        "NT: upnp:event\r\nNTS: upnp:propchange\r\nSID: ${s.sid}\r\nSEQ: $seq\r\nCONNECTION: close\r\n\r\n"
                    val out = sock.getOutputStream()
                    out.write(head.toByteArray(Charsets.ISO_8859_1))
                    out.write(data)
                    out.flush()
                    runCatching { sock.getInputStream().read(ByteArray(256)) }
                }
                return
            } catch (_: Exception) {
            }
        }
    }
}
