package com.akis.app.dlna

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.akis.app.Prefs
import com.akis.app.ServerState
import com.akis.app.net.HttpRequest
import com.akis.app.net.HttpResponder
import com.akis.app.net.HttpServer
import com.akis.app.net.Ssdp
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/** HTTP + SSDP + ContentDirectory'yi bir araya getiren UPnP/DLNA medya sunucusu. */
class DlnaServer(private val ctx: Context, val port: Int, private val uuid: String) {
    private val lib = MediaLibrary(ctx)
    private val http = HttpServer(port, ::handle)
    private var ssdp: Ssdp? = null
    @Volatile private var currentIp: Inet4Address? = null
    private val updateId = AtomicInteger(1)

    private val icon: ByteArray by lazy { makeIcon() }

    fun start(ip: Inet4Address?) {
        http.start()
        setAddress(ip)
    }

    @Synchronized
    fun setAddress(ip: Inet4Address?) {
        if (ip == currentIp && (ip == null || ssdp != null)) return
        ssdp?.stop()
        ssdp = null
        currentIp = ip
        ServerState.address.value = ip?.let { "http://${it.hostAddress}:$port" }
        if (ip != null) {
            try {
                ssdp = Ssdp(uuid, "http://${ip.hostAddress}:$port/desc.xml", ip).also { it.start() }
            } catch (e: Exception) {
                ServerState.log("Ağ duyurusu başlatılamadı: ${e.message}")
            }
        }
    }

    fun announce() {
        ssdp?.announce()
    }

    fun contentChanged() {
        updateId.incrementAndGet()
        announce()
    }

    @Synchronized
    fun stop() {
        ssdp?.stop()
        ssdp = null
        http.stop()
    }

    private fun decode(s: String) = URLDecoder.decode(s, "UTF-8")

    private fun handle(req: HttpRequest, res: HttpResponder) {
        ServerState.touchClient(req.remote, req.headers["user-agent"] ?: "")
        val base = req.headers["host"]?.let { "http://$it" } ?: ServerState.address.value ?: "http://127.0.0.1:$port"
        val p = req.path
        val settings = Prefs.current
        when (req.method) {
            "GET", "HEAD" -> when {
                p == "/desc.xml" -> res.xml(Xml.device(settings.name, uuid, base))
                p == "/cd.xml" -> res.xml(Xml.CD_SCPD)
                p == "/cm.xml" -> res.xml(Xml.CM_SCPD)
                p == "/icon.png" -> res.bytes(200, "image/png", icon)
                p.startsWith("/m/") -> serveMedia(req, res, decode(p.removePrefix("/m/").substringBefore('/')))
                p.startsWith("/t/") -> {
                    val id = decode(p.removePrefix("/t/").removeSuffix(".jpg"))
                    val b = lib.thumbnail(id)
                    if (b != null) res.bytes(200, "image/jpeg", b, mapOf("Cache-Control" to "max-age=86400"))
                    else res.text(404, "Küçük resim yok")
                }
                !settings.webUi -> res.text(403, "Web arayüzü kapalı. Akış uygulamasından açabilirsin.")
                p == "/" || p == "/index.html" -> res.html(WebUi.list(settings.name, settings.name, lib.root(), null))
                p.startsWith("/w/") -> {
                    val id = decode(p.removePrefix("/w/"))
                    val title = lib.metadata(id)?.title ?: ""
                    res.html(WebUi.list(settings.name, title, lib.children(id), lib.parentOf(id)))
                }
                p.startsWith("/p/") -> {
                    val e = lib.lookup(decode(p.removePrefix("/p/")))
                    if (e == null) res.text(404, "Bulunamadı")
                    else res.html(WebUi.player(settings.name, e, Xml.mediaUrl(base, e)))
                }
                else -> res.text(404, "Bulunamadı")
            }
            "POST" -> control(req, res, base)
            "SUBSCRIBE" -> res.send(200, null, 0, mapOf("SID" to "uuid:${UUID.randomUUID()}", "TIMEOUT" to "Second-1800"))
            "UNSUBSCRIBE" -> res.send(200, null, 0)
            "OPTIONS" -> res.send(200, null, 0, mapOf("Allow" to "GET, HEAD, POST, SUBSCRIBE, UNSUBSCRIBE"))
            else -> res.text(405, "Desteklenmiyor")
        }
    }

    // ---------------- SOAP ----------------

    private fun control(req: HttpRequest, res: HttpResponder, base: String) {
        val action = (req.headers["soapaction"] ?: "").trim('"', ' ').substringAfter('#')
        val body = String(req.body, Charsets.UTF_8)
        when (req.path) {
            "/ctl/cd" -> when (action) {
                "Browse" -> browse(body, res, base)
                "GetSystemUpdateID" -> res.xml(Xml.soap(action, "ContentDirectory", "<Id>${updateId.get()}</Id>"))
                "GetSearchCapabilities" -> res.xml(Xml.soap(action, "ContentDirectory", "<SearchCaps></SearchCaps>"))
                "GetSortCapabilities" -> res.xml(Xml.soap(action, "ContentDirectory", "<SortCaps>dc:title</SortCaps>"))
                else -> res.text(500, Xml.FAULT, "text/xml; charset=\"utf-8\"")
            }
            "/ctl/cm" -> when (action) {
                "GetProtocolInfo" -> res.xml(
                    Xml.soap(action, "ConnectionManager", "<Source>http-get:*:video/*:*,http-get:*:audio/*:*,http-get:*:image/*:*</Source><Sink></Sink>"),
                )
                "GetCurrentConnectionIDs" -> res.xml(Xml.soap(action, "ConnectionManager", "<ConnectionIDs>0</ConnectionIDs>"))
                else -> res.text(500, Xml.FAULT, "text/xml; charset=\"utf-8\"")
            }
            else -> res.text(404, "Bulunamadı")
        }
    }

    private fun browse(body: String, res: HttpResponder, base: String) {
        val oid = Xml.arg(body, "ObjectID").ifEmpty { "0" }
        val flag = Xml.arg(body, "BrowseFlag").ifEmpty { "BrowseDirectChildren" }
        val start = Xml.arg(body, "StartingIndex").toIntOrNull()?.coerceAtLeast(0) ?: 0
        val count = Xml.arg(body, "RequestedCount").toIntOrNull() ?: 0
        val didl: String
        val returned: Int
        val total: Int
        if (flag == "BrowseMetadata") {
            val list = listOfNotNull(lib.metadata(oid))
            didl = Xml.didl(list, base)
            returned = list.size
            total = list.size
        } else {
            val all = lib.children(oid)
            val part = all.drop(start).let { if (count > 0) it.take(count) else it }
            didl = Xml.didl(part, base)
            returned = part.size
            total = all.size
        }
        val out = "<Result>${Xml.esc(didl)}</Result><NumberReturned>$returned</NumberReturned>" +
            "<TotalMatches>$total</TotalMatches><UpdateID>${updateId.get()}</UpdateID>"
        res.xml(Xml.soap("Browse", "ContentDirectory", out))
    }

    // ---------------- Dosya akışı ----------------

    private fun serveMedia(req: HttpRequest, res: HttpResponder, id: String) {
        val e = lib.lookup(id) ?: return res.text(404, "Dosya bulunamadı")
        val uri = e.uri ?: return res.text(404, "Dosya bulunamadı")
        val pfd = try { ctx.contentResolver.openFileDescriptor(uri, "r") } catch (ex: Exception) { null }
            ?: return res.text(404, "Dosya açılamadı")
        pfd.use { fd ->
            val size = if (e.size > 0) e.size else fd.statSize
            if (size <= 0) return res.text(500, "Dosya boyutu okunamadı")
            var start = 0L
            var end = size - 1
            var partial = false
            val range = req.headers["range"]
            if (range != null) {
                val m = Regex("bytes=(\\d*)-(\\d*)").find(range)
                if (m != null) {
                    val (a, b) = m.destructured
                    if (a.isNotEmpty()) {
                        start = a.toLong()
                        if (b.isNotEmpty()) end = min(b.toLong(), size - 1)
                    } else if (b.isNotEmpty()) {
                        start = (size - b.toLong()).coerceAtLeast(0)
                    }
                    if (start >= size) {
                        return res.send(416, null, 0, mapOf("Content-Range" to "bytes */$size"))
                    }
                    partial = true
                }
            }
            val length = end - start + 1
            val headers = linkedMapOf(
                "Accept-Ranges" to "bytes",
                "transferMode.dlna.org" to (if (e.kind == Kind.IMAGE) "Interactive" else "Streaming"),
                "contentFeatures.dlna.org" to Xml.CONTENT_FEATURES,
                "Cache-Control" to "no-cache",
            )
            if (partial) headers["Content-Range"] = "bytes $start-$end/$size"
            val track = e.kind == Kind.VIDEO || e.kind == Kind.AUDIO

            FileInputStream(fd.fileDescriptor).use { fis ->
                try {
                    fis.channel.position(start)
                } catch (ex: Exception) {
                    var skip = start
                    while (skip > 0) {
                        val k = fis.skip(skip)
                        if (k <= 0) break
                        skip -= k
                    }
                }
                res.send(if (partial) 206 else 200, e.mime, length, headers) { out ->
                    val buf = ByteArray(256 * 1024)
                    var left = length
                    var pos = start
                    var acc = 0L
                    if (track) ServerState.reportStream(req.remote, id, e.title, e.kind, pos, size)
                    try {
                        while (left > 0) {
                            val n = fis.read(buf, 0, min(buf.size.toLong(), left).toInt())
                            if (n < 0) break
                            out.write(buf, 0, n)
                            left -= n
                            pos += n
                            acc += n
                            if (acc >= 1_048_576) {
                                ServerState.addBytes(acc)
                                acc = 0
                                if (track) ServerState.reportStream(req.remote, id, e.title, e.kind, pos, size)
                            }
                        }
                    } finally {
                        ServerState.addBytes(acc)
                    }
                }
            }
        }
    }

    private fun makeIcon(): ByteArray {
        val bmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF0A0A0A.toInt()
        c.drawRoundRect(RectF(0f, 0f, 120f, 120f), 34f, 34f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        paint.color = 0xFFFFFFFF.toInt()
        c.drawRoundRect(RectF(24f, 30f, 96f, 82f), 12f, 12f, paint)
        paint.strokeCap = Paint.Cap.ROUND
        c.drawLine(42f, 96f, 78f, 96f, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFD71921.toInt()
        c.drawCircle(60f, 56f, 12f, paint)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
