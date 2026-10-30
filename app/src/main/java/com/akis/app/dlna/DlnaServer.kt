package com.akis.app.dlna

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.akis.app.DeviceAccess
import com.akis.app.Lang
import com.akis.app.R
import com.akis.app.Prefs
import com.akis.app.Settings
import com.akis.app.ServerState
import com.akis.app.WatchHistory
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
                ServerState.log(Lang.s(R.string.log_ssdp_fail, e.message ?: ""))
            }
        }
    }

    fun announce() {
        ssdp?.announce()
    }

    fun contentChanged() {
        lib.clearSearchCache()
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
        if (!checkAccess(req, res, settings)) return
        when (req.method) {
            "GET", "HEAD" -> when {
                p == "/desc.xml" -> res.xml(Xml.device(settings.name, uuid, base))
                p == "/cd.xml" -> res.xml(Xml.cdScpd(settings.search))
                p == "/cm.xml" -> res.xml(Xml.CM_SCPD)
                p == "/icon.png" -> res.bytes(200, "image/png", icon)
                p.startsWith("/m/") -> serveMedia(req, res, decode(p.removePrefix("/m/").substringBefore('/')), base)
                p.startsWith("/s/") -> serveSubtitle(res, decode(p.removePrefix("/s/").substringBeforeLast('.')), p.substringAfterLast('.'))
                p.startsWith("/t/") -> {
                    val id = decode(p.removePrefix("/t/").removeSuffix(".jpg"))
                    val b = lib.thumbnail(id)
                    if (b != null) res.bytes(200, "image/jpeg", b, mapOf("Cache-Control" to "max-age=86400"))
                    else res.text(404, Lang.s(R.string.http_no_thumb))
                }
                !settings.webUi -> res.text(403, Lang.s(R.string.http_web_off))
                p == "/" || p == "/index.html" -> res.html(WebUi.list(settings.name, settings.name, lib.root(), null, search = settings.search, upload = settings.upload))
                p == "/yukle" && settings.upload -> res.html(WebUi.uploadPage(settings.name, uploadTarget()))
                p == "/ara" && settings.search -> {
                    val q = queryParam(req.query, "q")
                    res.html(WebUi.list(settings.name, Lang.s(R.string.search_results, q), lib.search(q, limit = 200).map(WatchHistory::mark), "0", search = true, query = q, upload = settings.upload))
                }
                p.startsWith("/w/") -> {
                    val id = decode(p.removePrefix("/w/"))
                    val title = lib.metadata(id)?.title ?: ""
                    res.html(WebUi.list(settings.name, title, lib.children(id).map(WatchHistory::mark), lib.parentOf(id), search = settings.search, upload = settings.upload))
                }
                p.startsWith("/p/") -> {
                    val e = lib.lookup(decode(p.removePrefix("/p/")))
                    if (e == null) res.text(404, Lang.s(R.string.http_not_found))
                    else res.html(
                        WebUi.player(
                            settings.name, e, Xml.mediaUrl(base, e),
                            subs = if (settings.subtitles) lib.subtitlesOf(e) else emptyList(),
                            resumeMs = if (settings.history) WatchHistory.resumeMs(e.id) else -1,
                        ),
                    )
                }
                else -> res.text(404, Lang.s(R.string.http_not_found))
            }
            "POST" -> if (p == "/h") webProgress(req, res) else control(req, res, base)
            "PUT" -> if (p.startsWith("/yukle/") && settings.upload && settings.webUi) upload(req, res) else res.text(403, Lang.s(R.string.http_upload_off))
            "SUBSCRIBE" -> res.send(200, null, 0, mapOf("SID" to "uuid:${UUID.randomUUID()}", "TIMEOUT" to "Second-1800"))
            "UNSUBSCRIBE" -> res.send(200, null, 0)
            "OPTIONS" -> res.send(200, null, 0, mapOf("Allow" to "GET, HEAD, POST, PUT, SUBSCRIBE, UNSUBSCRIBE"))
            else -> res.text(405, Lang.s(R.string.http_unsupported))
        }
    }

    // ---------------- Erişim (cihaz onayı + web PIN'i) ----------------

    private val openPaths = setOf("/desc.xml", "/cd.xml", "/cm.xml", "/icon.png")

    private fun webPath(p: String) =
        p == "/" || p == "/index.html" || p.startsWith("/w/") || p.startsWith("/p/") || p == "/ara" || p == "/h" || p.startsWith("/yukle")

    /** @return true ise istek işlenebilir; false ise cevap zaten verildi. */
    private fun checkAccess(req: HttpRequest, res: HttpResponder, settings: Settings): Boolean {
        val ip = req.remote
        val p = req.path
        if (ip.startsWith("127.") || ip == currentIp?.hostAddress) return true
        if (p in openPaths) return true
        if (p == "/pin") {
            pinLogin(req, res, settings)
            return false
        }
        val pinSet = settings.webPin.isNotEmpty()
        val pinOk = pinSet && DeviceAccess.cookieOk(req.headers["cookie"], settings.webPin, uuid)
        if (pinSet && webPath(p) && !pinOk) {
            if (req.method == "GET") res.html(WebUi.pinPage(settings.name, wrong = false, locked = DeviceAccess.locked(ip)))
            else res.text(403, Lang.s(R.string.http_pin_required))
            return false
        }
        if (!settings.deviceApproval || pinOk || DeviceAccess.isAllowed(ip)) return true
        if (DeviceAccess.isBlocked(ip)) {
            res.text(403, Lang.s(R.string.http_blocked))
            return false
        }
        DeviceAccess.request(ip, ServerState.clientName(ip))
        when {
            p.startsWith("/ctl/") -> res.text(500, Xml.FAULT, "text/xml; charset=\"utf-8\"")
            webPath(p) && req.method == "GET" -> res.html(WebUi.waitPage(settings.name))
            else -> res.text(403, Lang.s(R.string.waiting_approval))
        }
        return false
    }

    private fun pinLogin(req: HttpRequest, res: HttpResponder, settings: Settings) {
        val ip = req.remote
        if (settings.webPin.isEmpty()) return res.send(303, null, 0, mapOf("Location" to "/"))
        if (req.method != "POST") return res.html(WebUi.pinPage(settings.name, wrong = false, locked = DeviceAccess.locked(ip)))
        if (DeviceAccess.locked(ip)) return res.html(WebUi.pinPage(settings.name, wrong = false, locked = true))
        val pin = queryParam(String(req.body, Charsets.UTF_8).trim(), "pin")
        val ok = pin == settings.webPin
        DeviceAccess.recordAttempt(ip, ok)
        if (!ok) return res.html(WebUi.pinPage(settings.name, wrong = true, locked = DeviceAccess.locked(ip)))
        val token = DeviceAccess.token(settings.webPin, uuid)
        res.send(
            303, null, 0,
            mapOf("Location" to "/", "Set-Cookie" to "akis_pin=$token; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly"),
        )
    }

    // ---------------- SOAP ----------------

    private fun control(req: HttpRequest, res: HttpResponder, base: String) {
        val action = (req.headers["soapaction"] ?: "").trim('"', ' ').substringAfter('#')
        val body = String(req.body, Charsets.UTF_8)
        when (req.path) {
            "/ctl/cd" -> when (action) {
                "Browse" -> browse(body, res, base)
                "GetSystemUpdateID" -> res.xml(Xml.soap(action, "ContentDirectory", "<Id>${updateId.get()}</Id>"))
                "Search" -> if (Prefs.current.search) search(body, res, base) else res.text(500, Xml.FAULT, "text/xml; charset=\"utf-8\"")
                "GetSearchCapabilities" -> res.xml(
                    Xml.soap(
                        action, "ContentDirectory",
                        if (Prefs.current.search) "<SearchCaps>dc:title,upnp:class,upnp:artist,upnp:album</SearchCaps>" else "<SearchCaps></SearchCaps>",
                    ),
                )
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
            else -> res.text(404, Lang.s(R.string.http_not_found))
        }
    }

    private fun uploadTarget(): String {
        val f = Prefs.current.uploadFolder
        return if (f.isEmpty()) Lang.s(R.string.downloads_dir, Uploads.DEFAULT_DIR) else MediaLibrary.treeName(ctx, f)
    }

    /** PUT /yukle/<dosya adı> — gövde doğrudan hedef dosyaya akıtılır. */
    private fun upload(req: HttpRequest, res: HttpResponder) {
        val input = req.bodyStream ?: return res.text(400, Lang.s(R.string.http_no_body))
        if (req.contentLength <= 0) return res.text(400, Lang.s(R.string.http_empty_file))
        val name = Uploads.safeName(decode(req.path.removePrefix("/yukle/")))
        val uri = Uploads.receive(ctx, name, input, req.contentLength, ServerState.clientName(req.remote))
        if (uri == null) return res.text(500, "{\"ok\":false}", "application/json")
        contentChanged()
        res.text(200, "{\"ok\":true}", "application/json")
    }

    /** Web oynatıcının bildirdiği konum: /h?id=…&t=saniye&d=saniye */
    private fun webProgress(req: HttpRequest, res: HttpResponder) {
        val id = queryParam(req.query, "id")
        val t = queryParam(req.query, "t").toDoubleOrNull()
        val d = queryParam(req.query, "d").toDoubleOrNull()
        val e = lib.lookup(id)
        if (e != null && t != null && d != null && d > 0 && e.kind == Kind.VIDEO) {
            WatchHistory.report(id, e.title, (t * 1000).toLong(), (d * 1000).toLong())
        }
        res.send(204, null, 0)
    }

    private fun queryParam(query: String, name: String): String =
        query.split('&').firstOrNull { it.substringBefore('=') == name }?.substringAfter('=', "")?.let { decode(it) } ?: ""

    /** UPnP Search: SearchCriteria'dan aranan sözcüğü ve türü (video / müzik / foto) çıkarır. */
    private fun search(body: String, res: HttpResponder, base: String) {
        val criteria = Xml.arg(body, "SearchCriteria")
        val start = Xml.arg(body, "StartingIndex").toIntOrNull()?.coerceAtLeast(0) ?: 0
        val count = Xml.arg(body, "RequestedCount").toIntOrNull() ?: 0
        val term = Regex("""contains\s*"([^"]*)"""", RegexOption.IGNORE_CASE).findAll(criteria)
            .map { it.groupValues[1] }.firstOrNull { it.isNotBlank() } ?: ""
        val kinds = buildSet {
            if ("videoItem" in criteria) add(Kind.VIDEO)
            if ("audioItem" in criteria) add(Kind.AUDIO)
            if ("imageItem" in criteria) add(Kind.IMAGE)
        }
        val all = if (term.isEmpty() && kinds.isEmpty()) emptyList() else lib.search(term, kinds, limit = 1000)
        val part = all.drop(start).let { if (count > 0) it.take(count) else it }.map(WatchHistory::mark)
        val didl = Xml.didl(part, base, Prefs.current.subtitles)
        val out = "<Result>${Xml.esc(didl)}</Result><NumberReturned>${part.size}</NumberReturned>" +
            "<TotalMatches>${all.size}</TotalMatches><UpdateID>${updateId.get()}</UpdateID>"
        if (term.isNotEmpty()) ServerState.log(Lang.s(R.string.log_search, term))
        res.xml(Xml.soap("Search", "ContentDirectory", out))
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
            val list = listOfNotNull(lib.metadata(oid)?.let(WatchHistory::mark))
            didl = Xml.didl(list, base, Prefs.current.subtitles, Prefs.current.search)
            returned = list.size
            total = list.size
        } else {
            val all = lib.children(oid)
            val part = all.drop(start).let { if (count > 0) it.take(count) else it }.map(WatchHistory::mark)
            didl = Xml.didl(part, base, Prefs.current.subtitles)
            returned = part.size
            total = all.size
        }
        val out = "<Result>${Xml.esc(didl)}</Result><NumberReturned>$returned</NumberReturned>" +
            "<TotalMatches>$total</TotalMatches><UpdateID>${updateId.get()}</UpdateID>"
        res.xml(Xml.soap("Browse", "ContentDirectory", out))
    }

    // ---------------- Dosya akışı ----------------

    /** Altyazıyı UTF-8'e ve istenen biçime (srt / vtt) çevirerek verir. */
    private fun serveSubtitle(res: HttpResponder, id: String, format: String) {
        val e = lib.lookup(id)
        val uri = e?.uri
        if (e == null || uri == null || e.kind != Kind.SUBTITLE) return res.text(404, Lang.s(R.string.http_sub_not_found))
        val bytes = try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                while (out.size() < 8 * 1024 * 1024) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        } catch (_: Exception) {
            null
        } ?: return res.text(404, Lang.s(R.string.http_sub_open_fail))
        val text = Subtitles.decode(bytes, Prefs.current.subLang)
        val cors = mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-cache")
        if (format == "vtt") {
            res.bytes(200, "text/vtt; charset=utf-8", Subtitles.toVtt(text, e.ext).toByteArray(Charsets.UTF_8), cors)
        } else {
            val body = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + Subtitles.toSrt(text, e.ext).toByteArray(Charsets.UTF_8)
            res.bytes(200, "text/srt; charset=utf-8", body, cors)
        }
    }

    private fun serveMedia(req: HttpRequest, res: HttpResponder, id: String, base: String) {
        val e = lib.lookup(id) ?: return res.text(404, Lang.s(R.string.http_file_not_found))
        val uri = e.uri ?: return res.text(404, Lang.s(R.string.http_file_not_found))
        val pfd = try { ctx.contentResolver.openFileDescriptor(uri, "r") } catch (ex: Exception) { null }
            ?: return res.text(404, Lang.s(R.string.http_file_open_fail))
        pfd.use { fd ->
            val size = if (e.size > 0) e.size else fd.statSize
            if (size <= 0) return res.text(500, Lang.s(R.string.http_file_size_fail))
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
                "Access-Control-Allow-Origin" to "*",
            )
            // Samsung / LG: altyazı adresini başlıkta ister
            if (e.kind == Kind.VIDEO && req.headers["getcaptioninfo.sec"] == "1") {
                lib.subtitlesOf(e).firstOrNull()?.let { headers["CaptionInfo.sec"] = Xml.subUrl(base, it, Prefs.current.subtitles) }
            }
            if (partial) headers["Content-Range"] = "bytes $start-$end/$size"
            val track = e.kind == Kind.VIDEO || e.kind == Kind.AUDIO
            val histEntry = if (e.kind == Kind.VIDEO && Prefs.current.history) e.copy(durationMs = lib.durationOf(e)) else null
            var lastHist = 0L

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
                                val now = System.currentTimeMillis()
                                if (histEntry != null && now - lastHist > 5000) {
                                    lastHist = now
                                    WatchHistory.reportBytes(histEntry, start, pos, size)
                                }
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
