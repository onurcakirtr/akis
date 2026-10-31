package com.akis.app.dlna

import android.content.Context
import com.akis.app.FileAccess
import com.akis.app.Lang
import com.akis.app.QuickShare
import com.akis.app.Prefs
import com.akis.app.R
import com.akis.app.ServerIcon
import com.akis.app.ServerState
import com.akis.app.WatchHistory
import com.akis.app.net.HttpRequest
import com.akis.app.net.HttpResponder
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder

/**
 * Web uygulamasının (assets/web) sunucu tarafı:
 *  /             uygulama kabuğu (index.html; metinler ve tema içine gömülür)
 *  /static/…     app.js, app.css
 *  /api/home     ana sayfa rafları
 *  /api/list     klasör içeriği
 *  /api/search   arama
 *  /api/item     tek öğe + altyazılar + kaldığı yer + sıradaki
 *  /api/probe    video parçaları (oynatıcı için)
 *  /x/<id>       oynatıcının fMP4 akışı (Remux)
 */
class WebApi(private val ctx: Context, private val lib: MediaLibrary) {

    private fun decode(s: String) = URLDecoder.decode(s, "UTF-8")

    private fun param(req: HttpRequest, name: String): String =
        req.query.split('&').firstOrNull { it.substringBefore('=') == name }?.substringAfter('=', "")?.let { decode(it) } ?: ""

    /** @return true ise istek bu sınıf tarafından cevaplandı. */
    fun handle(req: HttpRequest, res: HttpResponder): Boolean {
        val p = req.path
        when {
            p == "/" || p == "/index.html" -> res.html(shell())
            p.startsWith("/static/") -> static(p.removePrefix("/static/"), res)
            p == "/api/home" -> json(res, home())
            p == "/api/list" -> json(res, list(param(req, "id").ifEmpty { "0" }))
            p == "/api/search" -> json(res, JSONObject().put("items", items(lib.search(param(req, "q"), limit = 300))))
            p == "/api/item" -> item(param(req, "id"), param(req, "in"))?.let { json(res, it) } ?: res.text(404, Lang.s(R.string.http_not_found))
            p == "/api/probe" -> probe(param(req, "id"), res)
            // Uzun istek: kitaplık değişene kadar (en çok 25 sn) bekler
            p == "/api/changes" -> json(res, JSONObject().put("v", ServerState.awaitChange(param(req, "since").toIntOrNull() ?: -1, 25_000)))
            p == "/api/info" -> info(param(req, "id"), param(req, "p"), res)
            p.startsWith("/x/") -> stream(req, res, decode(p.removePrefix("/x/")))
            p == "/api/files" -> files(param(req, "p"), res)
            p == "/raw" -> raw(req, res, param(req, "p"))
            p == "/api/zipls" -> zipList(param(req, "p"), res)
            p == "/zipget" -> zipGet(req, res, param(req, "p"), param(req, "e"))
            p == "/tf" -> fileThumb(param(req, "p"), res)
            p == "/api/status" -> if (Prefs.current.phoneStatus) json(res, PhoneInfo.status(ctx)) else res.text(404, Lang.s(R.string.http_not_found))
            p == "/api/apps" -> if (Prefs.current.appsList) json(res, PhoneInfo.apps(ctx, Prefs.current.appsSystem)) else res.text(404, Lang.s(R.string.http_not_found))
            p == "/appicon" -> if (Prefs.current.appsList) PhoneInfo.icon(ctx, param(req, "pkg"))?.let {
                res.bytes(200, "image/png", it, mapOf("Cache-Control" to "max-age=86400"))
            } ?: res.text(404, Lang.s(R.string.http_not_found)) else res.text(404, Lang.s(R.string.http_not_found))
            p == "/apk" -> if (Prefs.current.appsList) apk(req, res, param(req, "pkg")) else res.text(404, Lang.s(R.string.http_not_found))
            p == "/zip" -> zip(req, res, param(req, "p"))
            // Eski adresler yeni uygulamaya yönlensin
            p.startsWith("/w/") -> res.send(303, null, 0, mapOf("Location" to "/#/f/" + p.removePrefix("/w/")))
            p.startsWith("/p/") -> res.send(303, null, 0, mapOf("Location" to "/#/v/" + p.removePrefix("/p/")))
            p == "/ara" -> res.send(303, null, 0, mapOf("Location" to "/#/s/" + req.query.substringAfter("q=", "")))
            p == "/yukle" -> res.send(303, null, 0, mapOf("Location" to "/#/up"))
            else -> return false
        }
        return true
    }

    private fun json(res: HttpResponder, o: JSONObject) =
        res.bytes(200, "application/json; charset=utf-8", o.toString().toByteArray(Charsets.UTF_8), mapOf("Cache-Control" to "no-store"))

    // ---------------- Kabuk ve statik dosyalar ----------------

    private fun asset(name: String): ByteArray? = try {
        ctx.assets.open("web/$name").use { it.readBytes() }
    } catch (_: IOException) {
        null
    }

    private fun static(name: String, res: HttpResponder) {
        if (name.contains("..")) return res.text(404, Lang.s(R.string.http_not_found))
        val data = asset(name) ?: return res.text(404, Lang.s(R.string.http_not_found))
        val type = when (name.substringAfterLast('.')) {
            "js" -> "text/javascript; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
        res.bytes(200, type, data, mapOf("Cache-Control" to "no-cache"))
    }

    private fun shell(): String {
        val s = Prefs.current
        val html = asset("index.html")?.toString(Charsets.UTF_8) ?: return "Akış"
        val conf = JSONObject()
            .put("name", s.name)
            .put("search", s.search)
            .put("upload", s.upload)
            .put("history", s.history)
            .put("subtitles", s.subtitles)
            .put("files", filesOn())
            .put("admin", s.adminOn && s.adminHash.isNotEmpty())
            .put("status", s.phoneStatus).put("apps", s.appsList)
            .put("docs", s.docPreview).put("zipView", s.zipView).put("thumbs", s.richThumbs).put("keys", s.keysHelp)
            .put("subLang", s.subLang)
            .put("lang", Lang.locale.language)
            .put("uploadTarget", if (s.uploadFolder.isEmpty()) Lang.s(R.string.downloads_dir, Uploads.DEFAULT_DIR) else MediaLibrary.treeName(ctx, s.uploadFolder))
            .put("t", WebStrings.json())
        return html
            .replace("{{LANG}}", Lang.locale.language)
            .replace("{{TITLE}}", esc(s.name))
            .replace("{{ICONV}}", ServerIcon.version(ctx))
            .replace("{{THEME}}", WebUi.themeCss())
            .replace("{{CONF}}", conf.toString().replace("</", "<\\/"))
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // ---------------- JSON ----------------

    private fun kindCode(k: Kind) = when (k) {
        Kind.FOLDER -> "f"
        Kind.VIDEO -> "v"
        Kind.AUDIO -> "a"
        Kind.IMAGE -> "i"
        Kind.SUBTITLE -> "s"
    }

    private fun itemJson(e: Entry): JSONObject {
        val o = JSONObject().put("id", e.id).put("t", e.title).put("k", kindCode(e.kind))
        if (e.kind != Kind.FOLDER) {
            o.put("ext", e.ext).put("sz", e.size).put("mime", e.mime)
            if (e.durationMs > 0) o.put("d", e.durationMs)
            if (e.width > 0) o.put("w", e.width).put("h", e.height)
            e.artist?.let { o.put("ar", it) }
            e.album?.let { o.put("al", it) }
            if (e.dateSec > 0) o.put("dt", e.dateSec)
            if (Prefs.current.history && e.kind == Kind.VIDEO) {
                WatchHistory.get(e.id)?.let { r ->
                    if (r.watched) o.put("ok", true)
                    if (r.inProgress) o.put("p", r.fraction.toDouble()).put("pos", r.posMs).put("d", r.durMs)
                }
            }
        } else if (e.childCount >= 0) {
            o.put("n", e.childCount)
        }
        return o
    }

    private fun items(list: List<Entry>) = JSONArray().also { a -> list.forEach { a.put(itemJson(it)) } }

    private fun home(): JSONObject {
        val s = Prefs.current
        val o = JSONObject()
        if (s.history) {
            o.put("cont", items(WatchHistory.inProgress().take(16).mapNotNull { lib.lookup(it.id) }))
        }
        val cats = JSONArray()
        for (f in lib.root()) {
            val n = when (f.id) {
                "v" -> lib.count(Kind.VIDEO)
                "a" -> lib.count(Kind.AUDIO)
                "i" -> lib.count(Kind.IMAGE)
                "f" -> s.folders.size
                else -> -1
            }
            if (f.id == "h:c" || f.id == "r" || (f.id == "pd:" && filesOn())) continue
            cats.put(JSONObject().put("id", f.id).put("t", f.title).put("n", n))
        }
        o.put("cats", cats)
        if (s.showRecent) o.put("recent", items(lib.children("r").take(24)))
        return o
    }

    private fun crumbs(id: String): JSONArray {
        val chain = ArrayList<JSONObject>()
        var cur = id
        var guard = 0
        while (cur != "0" && guard++ < 12) {
            val m = lib.metadata(cur) ?: break
            chain += JSONObject().put("id", cur).put("t", m.title)
            cur = lib.parentOf(cur)
        }
        return JSONArray().also { a -> chain.reversed().forEach { a.put(it) } }
    }

    private fun list(id: String): JSONObject {
        val title = if (id == "0") Prefs.current.name else lib.metadata(id)?.title ?: ""
        return JSONObject()
            .put("id", id)
            .put("t", title)
            .put("parent", if (id == "0") JSONObject.NULL else lib.parentOf(id))
            .put("crumbs", crumbs(id))
            .put("items", items(lib.children(id)))
    }

    private fun item(id: String, inFolder: String): JSONObject? {
        val e = lib.lookup(id) ?: return null
        val full = if (e.kind == Kind.VIDEO && e.durationMs <= 0) e.copy(durationMs = lib.durationOf(e)) else e
        val o = JSONObject().put("item", itemJson(full))
        val s = Prefs.current
        if (s.subtitles && e.kind == Kind.VIDEO) {
            val subs = JSONArray()
            lib.subtitlesOf(e).forEach { sub ->
                subs.put(
                    JSONObject().put("u", "/s/" + Xml.enc(sub.id) + ".vtt").put("l", sub.label).put("lang", sub.lang)
                        .put("emb", sub.embedded).put("forced", sub.forced),
                )
            }
            o.put("subs", subs)
        }
        if (s.history && e.kind == Kind.VIDEO) o.put("resume", WatchHistory.resumeMs(e.id))
        // Aynı klasörde önceki / sonraki
        val folder = inFolder.ifEmpty { e.parentId }
        val siblings = lib.children(folder).filter { it.kind == e.kind }
        val i = siblings.indexOfFirst { it.id == e.id }
        if (i >= 0) {
            siblings.getOrNull(i + 1)?.let { o.put("next", itemJson(it)) }
            siblings.getOrNull(i - 1)?.let { o.put("prev", itemJson(it)) }
        }
        o.put("crumbs", crumbs(folder))
        return o
    }

    private fun probe(id: String, res: HttpResponder) {
        val e = lib.lookup(id) ?: return res.text(404, Lang.s(R.string.http_not_found))
        val uri = e.uri ?: return res.text(404, Lang.s(R.string.http_not_found))
        val pr = try {
            Remux.probe(ctx, uri)
        } catch (_: Exception) {
            return json(res, JSONObject().put("ok", false))
        }
        // MKV'de ses parçalarının adı ("Türkçe Dublaj") ve varsayılanı: aynı sıradaki ses parçasıyla eşleşir
        val mkvAudio = if (Mkv.isMkv(e)) Mkv.tracks(ctx, e).filter { it.type == Mkv.AUDIO } else emptyList()
        var audioNo = 0
        val tracks = JSONArray()
        for (t in pr.tracks) {
            val mk = if (!t.video) mkvAudio.getOrNull(audioNo++) else null
            tracks.put(
                JSONObject().put("i", t.index).put("v", t.video).put("mime", t.mime).put("codec", t.codec)
                    .put("lang", t.lang).put("ch", t.channels).put("rate", t.sampleRate)
                    .put("w", t.width).put("h", t.height).put("tx", t.canTranscode)
                    .put("name", mk?.name ?: "").put("def", mk?.isDefault ?: false)
                    .apply { if (mk != null && mk.lang.isNotEmpty()) put("lang", mk.lang) },
            )
        }
        json(res, JSONObject().put("ok", true).put("d", if (pr.durationMs > 0) pr.durationMs else e.durationMs).put("tracks", tracks))
    }

    // ---------------- Tüm dosyalar (dosya gezgini) ----------------

    fun filesOn() = Prefs.current.allFilesWeb && FileAccess.granted(ctx)

    private fun files(path: String, res: HttpResponder) {
        if (!filesOn()) return res.text(403, Lang.s(R.string.http_not_found))
        val o = JSONObject()
        val items = JSONArray()
        if (path.isEmpty()) {
            for (r in FileAccess.roots()) items.put(JSONObject().put("n", FileAccess.rootName(ctx, r)).put("p", r.path).put("dir", true))
            return json(res, o.put("path", "").put("crumbs", JSONArray()).put("items", items))
        }
        val dir = FileAccess.resolve(path)?.takeIf { it.isDirectory } ?: return res.text(404, Lang.s(R.string.http_not_found))
        for (f in FileAccess.list(dir)) {
            val j = JSONObject().put("n", f.name).put("p", f.path).put("dir", f.isDirectory).put("m", f.lastModified() / 1000)
            if (f.isFile) {
                j.put("sz", f.length()).put("ext", f.name.substringAfterLast('.', "").lowercase())
                val k = MediaLibrary.kindOf(f.name, null)
                if (k == Kind.VIDEO || k == Kind.AUDIO || k == Kind.IMAGE) j.put("k", kindCode(k)).put("id", "p:" + f.path)
            }
            items.put(j)
        }
        // Kökten bu klasöre kadar yol
        val crumbs = JSONArray()
        val root = FileAccess.roots().firstOrNull { dir.path == it.canonicalPath || dir.path.startsWith(it.canonicalPath + "/") }
        if (root != null) {
            val rp = root.canonicalPath
            crumbs.put(JSONObject().put("n", FileAccess.rootName(ctx, root)).put("p", rp))
            var acc = rp
            for (seg in dir.path.removePrefix(rp).split('/').filter { it.isNotEmpty() }) {
                acc = "$acc/$seg"
                crumbs.put(JSONObject().put("n", seg).put("p", acc))
            }
        }
        json(res, o.put("path", dir.path).put("crumbs", crumbs).put("items", items))
    }

    /** Herhangi bir dosyayı (HTTP Range destekli) verir. */
    private fun raw(req: HttpRequest, res: HttpResponder, path: String) {
        if (!filesOn()) return res.text(403, Lang.s(R.string.http_not_found))
        val f = FileAccess.resolve(path)?.takeIf { it.isFile } ?: return res.text(404, Lang.s(R.string.http_file_not_found))
        serveFile(req, res, f, null)
    }

    /**
     * Dosyayı HTTP Range destekli verir.
     * @param attachment indirme adı (null ise tarayıcı kendisi açar)
     * @return dosyanın tamamı baştan sona gönderildi mi
     */
    fun serveFile(req: HttpRequest, res: HttpResponder, f: java.io.File, attachment: String?): Boolean {
        val size = f.length()
        var start = 0L
        var end = size - 1
        var partial = false
        req.headers["range"]?.let { range ->
            Regex("bytes=(\\d*)-(\\d*)").find(range)?.let { m ->
                val (a, b) = m.destructured
                if (a.isNotEmpty()) {
                    start = a.toLong()
                    if (b.isNotEmpty()) end = minOf(b.toLong(), size - 1)
                } else if (b.isNotEmpty()) {
                    start = (size - b.toLong()).coerceAtLeast(0)
                }
                partial = true
            }
        }
        if (size > 0 && start >= size) {
            res.send(416, null, 0, mapOf("Content-Range" to "bytes */$size"))
            return false
        }
        val len = if (size == 0L) 0 else end - start + 1
        val headers = linkedMapOf("Accept-Ranges" to "bytes", "Cache-Control" to "no-cache")
        if (partial) headers["Content-Range"] = "bytes $start-$end/$size"
        if (attachment != null) {
            val ascii = attachment.replace(Regex("[^A-Za-z0-9._ -]"), "_")
            headers["Content-Disposition"] = "attachment; filename=\"$ascii\"; filename*=UTF-8''" + Xml.enc(attachment)
        }
        val type = MediaLibrary.mimeFor(f.name, null)
        var sent = 0L
        java.io.RandomAccessFile(f, "r").use { raf ->
            raf.seek(start)
            res.send(if (partial) 206 else 200, type, len, headers) { out ->
                val buf = ByteArray(256 * 1024)
                var left = len
                while (left > 0) {
                    val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) break
                    out.write(buf, 0, n)
                    left -= n
                }
                sent = len - left
                ServerState.addBytes(sent)
            }
        }
        return start == 0L && sent == size && req.method != "HEAD"
    }

    /** /share/<anahtar> → indirme sayfası, /share/<anahtar>/dl → dosya. PIN istemez (anahtar tahmin edilemez). */
    fun share(req: HttpRequest, res: HttpResponder) {
        val parts = req.path.removePrefix("/share/").split('/')
        val sh = QuickShare.get(parts[0])
        if (sh == null) return res.html(WebUi.sharePage(Prefs.current.name, null, 0, 0, ""))
        if (parts.getOrNull(1) == "dl") {
            if (serveFile(req, res, sh.file, sh.name)) {
                ServerState.log(Lang.s(R.string.log_share_dl, sh.name, ServerState.clientName(req.remote)))
                QuickShare.downloaded(sh.token)
            }
            return
        }
        res.html(WebUi.sharePage(Prefs.current.name, sh.name, sh.size, sh.expires, "/share/${sh.token}/dl"))
    }

    /** Uygulamanın APK'sı; parçalıysa (split) tüm parçalar tek .apks arşivinde. */
    private fun apk(req: HttpRequest, res: HttpResponder, pkg: String) {
        val info = PhoneInfo.apkFiles(ctx, pkg) ?: return res.text(404, Lang.s(R.string.http_not_found))
        val (label, files) = info
        ServerState.log("⬇ APK: $label → ${ServerState.clientName(req.remote)}")
        if (files.size == 1) {
            serveFile(req, res, files[0], "$label.apk")
            return
        }
        val name = "$label.apks"
        res.stream(200, "application/zip", mapOf("Content-Disposition" to "attachment; filename*=UTF-8''" + Xml.enc(name), "Cache-Control" to "no-store")) { out ->
            val z = java.util.zip.ZipOutputStream(out)
            z.setLevel(java.util.zip.Deflater.NO_COMPRESSION)
            for (f in files) {
                z.putNextEntry(java.util.zip.ZipEntry(f.name))
                f.inputStream().use { it.copyTo(z, 256 * 1024) }
                z.closeEntry()
            }
            z.finish()
        }
    }

    // ---------------- ZIP içine bakma ----------------

    private fun zipFile(path: String): java.io.File? =
        if (!filesOn() || !Prefs.current.zipView) null
        else FileAccess.resolve(path)?.takeIf { it.isFile && it.name.endsWith(".zip", true) }

    /** ZIP içindekiler: liste dosyanın sonundaki dizinden okunur, telefonu yormaz. */
    private fun zipList(path: String, res: HttpResponder) {
        val f = zipFile(path) ?: return res.text(404, Lang.s(R.string.http_not_found))
        val items = JSONArray()
        try {
            java.util.zip.ZipFile(f).use { z ->
                var n = 0
                for (e in z.entries()) {
                    if (n++ >= 5000) break
                    items.put(JSONObject().put("n", e.name).put("dir", e.isDirectory).put("sz", e.size).put("m", e.time / 1000))
                }
            }
        } catch (_: Exception) {
            return json(res, JSONObject().put("err", "bad"))
        }
        json(res, JSONObject().put("name", f.name).put("items", items))
    }

    private fun zipGet(req: HttpRequest, res: HttpResponder, path: String, entry: String) {
        val f = zipFile(path) ?: return res.text(404, Lang.s(R.string.http_not_found))
        try {
            java.util.zip.ZipFile(f).use { z ->
                val e = z.getEntry(entry)?.takeIf { !it.isDirectory } ?: return res.text(404, Lang.s(R.string.http_not_found))
                val name = e.name.substringAfterLast('/')
                val ascii = name.replace(Regex("[^A-Za-z0-9._ -]"), "_")
                val headers = mapOf(
                    "Content-Disposition" to "attachment; filename=\"$ascii\"; filename*=UTF-8''" + Xml.enc(name),
                    "Cache-Control" to "no-store",
                )
                val type = MediaLibrary.mimeFor(name, null)
                val body: (java.io.OutputStream) -> Unit = { out -> z.getInputStream(e).use { it.copyTo(out, 256 * 1024) } }
                if (e.size >= 0) res.send(200, type, e.size, headers, body) else res.stream(200, type, headers, body)
            }
        } catch (_: java.util.zip.ZipException) {
            res.text(404, Lang.s(R.string.http_not_found))
        }
    }

    // ---------------- Daha iyi küçük resimler (dosya gezgini) ----------------

    private val fileThumbs = android.util.LruCache<String, ByteArray>(4 * 1024 * 1024)

    /** APK → uygulama ikonu, PDF → ilk sayfa, klasör → içindeki ilk fotoğraf. */
    private fun fileThumb(path: String, res: HttpResponder) {
        if (!filesOn() || !Prefs.current.richThumbs) return res.text(404, Lang.s(R.string.http_not_found))
        val f = FileAccess.resolve(path) ?: return res.text(404, Lang.s(R.string.http_not_found))
        val key = f.path + "|" + f.lastModified()
        fileThumbs.get(key)?.let { return res.bytes(200, "image/png", it, mapOf("Cache-Control" to "max-age=3600")) }
        val bmp: android.graphics.Bitmap? = try {
            when {
                f.isDirectory -> FileAccess.list(f).firstOrNull { it.isFile && MediaLibrary.kindOf(it.name, null) == Kind.IMAGE }?.let {
                    android.media.ThumbnailUtils.createImageThumbnail(it, android.util.Size(256, 256), null)
                }
                f.name.endsWith(".apk", true) -> {
                    val pm = ctx.packageManager
                    pm.getPackageArchiveInfo(f.path, 0)?.applicationInfo?.let { ai ->
                        ai.sourceDir = f.path
                        ai.publicSourceDir = f.path
                        drawableToBitmap(ai.loadIcon(pm))
                    }
                }
                f.name.endsWith(".pdf", true) -> android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    android.graphics.pdf.PdfRenderer(pfd).use { r ->
                        if (r.pageCount == 0) null else r.openPage(0).use { page ->
                            val w = 256
                            val h = (w.toFloat() * page.height / page.width).toInt().coerceIn(64, 512)
                            val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                            b.eraseColor(android.graphics.Color.WHITE)
                            page.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            b
                        }
                    }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        if (bmp == null) return res.text(404, Lang.s(R.string.http_not_found))
        val o = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, o)
        bmp.recycle()
        val data = o.toByteArray()
        fileThumbs.put(key, data)
        res.bytes(200, "image/png", data, mapOf("Cache-Control" to "max-age=3600"))
    }

    private fun drawableToBitmap(d: android.graphics.drawable.Drawable): android.graphics.Bitmap {
        val b = android.graphics.Bitmap.createBitmap(192, 192, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        d.setBounds(0, 0, 192, 192)
        d.draw(c)
        return b
    }

    /** Klasörü (alt klasörleriyle, gizli dosyalar hariç) telefonda geçici dosya oluşturmadan ZIP olarak akıtır. */
    /** Web'de seçilen öğeleri tek ZIP olarak akıtır. Gövde: i=<kitaplık kimliği>&p=<dosya yolu>… */
    fun zipSelection(req: HttpRequest, res: HttpResponder) {
        val pairs = String(req.body, Charsets.UTF_8).split('&').mapNotNull {
            val k = it.substringBefore('=', "")
            val v = runCatching { decode(it.substringAfter('=', "")) }.getOrNull()
            if (k.isEmpty() || v.isNullOrEmpty()) null else k to v
        }
        if (pairs.isEmpty()) return res.text(400, Lang.s(R.string.http_not_found))
        ServerState.log("⬇ ZIP (${pairs.size}) → ${ServerState.clientName(req.remote)}")
        res.stream(
            200, "application/zip",
            mapOf("Content-Disposition" to "attachment; filename=\"akis.zip\"", "Cache-Control" to "no-store"),
        ) { out ->
            val z = java.util.zip.ZipOutputStream(out)
            z.setLevel(java.util.zip.Deflater.NO_COMPRESSION)
            val used = HashSet<String>()
            val buf = ByteArray(256 * 1024)
            var sent = 0L
            fun unique(name: String): String {
                if (used.add(name)) return name
                val base = name.substringBeforeLast('.', name)
                val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
                var n = 2
                while (!used.add("$base ($n)$ext")) n++
                return "$base ($n)$ext"
            }
            fun put(rel: String, time: Long, open: () -> java.io.InputStream?) {
                val e = java.util.zip.ZipEntry(unique(rel))
                if (time > 0) e.time = time
                z.putNextEntry(e)
                try {
                    open()?.use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            z.write(buf, 0, n)
                            sent += n
                        }
                    }
                } catch (e: java.io.FileNotFoundException) {
                } catch (e: SecurityException) {
                }
                z.closeEntry()
            }
            fun addFile(f: java.io.File, rel: String) {
                if (f.isDirectory) {
                    val kids = FileAccess.list(f)
                    if (kids.isEmpty()) { z.putNextEntry(java.util.zip.ZipEntry(unique("$rel/"))); z.closeEntry() }
                    for (k in kids) addFile(k, "$rel/${k.name}")
                } else {
                    put(rel, f.lastModified()) { f.inputStream() }
                }
            }
            fun addEntry(e: Entry, prefix: String) {
                val uri = e.uri ?: return
                val name = prefix + e.title + if (e.ext.isNotEmpty()) "." + e.ext else ""
                put(name, e.dateSec * 1000) { ctx.contentResolver.openInputStream(uri) }
            }
            try {
                for ((k, v) in pairs) {
                    when (k) {
                        "p" -> if (filesOn()) FileAccess.resolve(v)?.let { addFile(it, it.name) }
                        "i" -> {
                            val e = lib.lookup(v)
                            if (e != null && e.kind != Kind.FOLDER) addEntry(e, "")
                            else {
                                // Kitaplık klasörü: içindeki medya (bir düzey)
                                val title = lib.metadata(v)?.title ?: "klasor"
                                lib.children(v).filter { it.kind != Kind.FOLDER }.forEach { addEntry(it, "$title/") }
                            }
                        }
                    }
                }
                z.finish()
            } finally {
                ServerState.addBytes(sent)
            }
        }
    }

    private fun zip(req: HttpRequest, res: HttpResponder, path: String) {
        if (!filesOn()) return res.text(403, Lang.s(R.string.http_not_found))
        val dir = FileAccess.resolve(path)?.takeIf { it.isDirectory } ?: return res.text(404, Lang.s(R.string.http_not_found))
        val name = dir.name.ifEmpty { "akis" } + ".zip"
        val ascii = name.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        ServerState.log("⬇ $name → ${ServerState.clientName(req.remote)}")
        res.stream(
            200, "application/zip",
            mapOf(
                "Content-Disposition" to "attachment; filename=\"$ascii\"; filename*=UTF-8''" + Xml.enc(name),
                "Cache-Control" to "no-store",
            ),
        ) { out ->
            val z = java.util.zip.ZipOutputStream(out)
            z.setLevel(java.util.zip.Deflater.NO_COMPRESSION) // medya zaten sıkıştırılmış; telefonu yormasın
            val buf = ByteArray(256 * 1024)
            var sent = 0L
            fun add(f: java.io.File, rel: String) {
                if (f.isDirectory) {
                    val kids = FileAccess.list(f)
                    if (kids.isEmpty()) { z.putNextEntry(java.util.zip.ZipEntry("$rel/")); z.closeEntry() }
                    for (k in kids) add(k, if (rel.isEmpty()) k.name else "$rel/${k.name}")
                    return
                }
                val entry = java.util.zip.ZipEntry(rel)
                entry.time = f.lastModified()
                z.putNextEntry(entry)
                try {
                    f.inputStream().use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            z.write(buf, 0, n)
                            sent += n
                        }
                    }
                } catch (e: java.io.FileNotFoundException) {
                    // okunamayan dosya atlanır
                }
                z.closeEntry()
            }
            try {
                for (k in FileAccess.list(dir)) add(k, k.name)
                z.finish()
            } finally {
                ServerState.addBytes(sent)
            }
        }
    }

    /** Özellikler paneli: her öğe için ad, tür, boyut, tarih; medyada ayrıca süre, parçalar, EXIF, etiketler. */
    private fun info(id: String, path: String, res: HttpResponder) {
        val e = if (id.isNotEmpty()) lib.lookup(id) else null
        val f = if (e == null && path.isNotEmpty() && filesOn()) FileAccess.resolve(path) else null
        val o = JSONObject()
        if (e == null && f == null) return res.text(404, Lang.s(R.string.http_not_found))
        if (e == null) {
            val file = f!!
            o.put("name", file.name).put("m", file.lastModified() / 1000).put("path", file.path).put("dir", file.isDirectory)
            if (file.isDirectory) o.put("count", FileAccess.list(file).size)
            else o.put("size", file.length()).put("mime", MediaLibrary.mimeFor(file.name, null))
            return json(res, o)
        }
        o.put("name", e.title + if (e.ext.isNotEmpty()) "." + e.ext else "").put("size", e.size).put("mime", e.mime).put("k", kindCode(e.kind))
        if (e.dateSec > 0) o.put("m", e.dateSec)
        if (e.id.startsWith("p:")) o.put("path", e.id.substring(2))
        if (e.width > 0) o.put("w", e.width).put("h", e.height)
        val uri0 = e.uri ?: return json(res, o)
        when (e.kind) {
            Kind.IMAGE -> exifInto(o, uri0)
            Kind.VIDEO, Kind.AUDIO -> {
                try {
                    val r = android.media.MediaMetadataRetriever()
                    try {
                        r.setDataSource(ctx, uri0)
                        fun m(k: Int) = r.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() }
                        m(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { o.put("d", it) }
                        m(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { o.put("br", it) }
                        m(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?.let { o.put("w", it) }
                        m(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?.let { o.put("h", it) }
                        m(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { o.put("title", it) }
                        m(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { o.put("artist", it) }
                        m(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { o.put("album", it) }
                        m(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR)?.let { o.put("year", it) }
                        m(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)?.let { o.put("genre", it) }
                    } finally {
                        r.release()
                    }
                } catch (_: Exception) {
                }
                // Parçalar: biçim, çözünürlük, kanal, dil ve (MKV'de) ad
                try {
                    val pr = Remux.probe(ctx, uri0)
                    val mkv = if (Mkv.isMkv(e)) Mkv.tracks(ctx, e) else emptyList()
                    val mkvAudio = mkv.filter { it.type == Mkv.AUDIO }
                    var ai = 0
                    val tracks = JSONArray()
                    for (t in pr.tracks) {
                        val mk = if (!t.video) mkvAudio.getOrNull(ai++) else null
                        tracks.put(
                            JSONObject().put("v", t.video).put("mime", t.mime).put("w", t.width).put("h", t.height)
                                .put("ch", t.channels).put("rate", t.sampleRate).put("lang", mk?.lang?.ifEmpty { null } ?: t.lang)
                                .put("name", mk?.name ?: "").put("def", mk?.isDefault ?: false),
                        )
                    }
                    o.put("tracks", tracks)
                } catch (_: Exception) {
                }
                if (e.kind == Kind.VIDEO) {
                    val subs = JSONArray()
                    lib.subtitlesOf(e).forEach { subs.put(it.label) }
                    o.put("subs", subs)
                }
            }
            else -> {}
        }
        json(res, o)
    }

    /** Fotoğrafın EXIF bilgisi: tarih, kamera, çekim değerleri, konum. */
    private fun exifInto(o: JSONObject, uri0: android.net.Uri) {
        var uri = uri0
        // Galeri fotoğraflarında gerçek konum için özgün dosya istenir (izin varsa)
        if (uri.authority == android.provider.MediaStore.AUTHORITY &&
            ctx.checkSelfPermission(android.Manifest.permission.ACCESS_MEDIA_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            uri = runCatching { android.provider.MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
        }
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val x = android.media.ExifInterface(input)
                fun s(tag: String) = x.getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }
                (s(android.media.ExifInterface.TAG_DATETIME_ORIGINAL) ?: s(android.media.ExifInterface.TAG_DATETIME))?.let { o.put("date", it) }
                val camera = listOfNotNull(s(android.media.ExifInterface.TAG_MAKE), s(android.media.ExifInterface.TAG_MODEL))
                    .let { l -> if (l.size == 2 && l[1].startsWith(l[0], true)) listOf(l[1]) else l }.joinToString(" ")
                if (camera.isNotEmpty()) o.put("camera", camera)
                val w = x.getAttributeInt(android.media.ExifInterface.TAG_IMAGE_WIDTH, 0)
                val h = x.getAttributeInt(android.media.ExifInterface.TAG_IMAGE_LENGTH, 0)
                if (w > 0 && h > 0) o.put("w", w).put("h", h)
                x.getAttributeDouble(android.media.ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { o.put("f", it) }
                x.getAttributeDouble(android.media.ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let { o.put("exp", it) }
                x.getAttributeInt(android.media.ExifInterface.TAG_ISO_SPEED_RATINGS, 0).takeIf { it > 0 }?.let { o.put("iso", it) }
                x.getAttributeDouble(android.media.ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let { o.put("focal", it) }
                val ll = FloatArray(2)
                if (x.getLatLong(ll) && (ll[0] != 0f || ll[1] != 0f)) o.put("lat", ll[0].toDouble()).put("lon", ll[1].toDouble())
            }
        } catch (_: Exception) {
        }
    }

    /** /x/<id>?t=saniye&a=ses_parçası&tx=1 */
    private fun stream(req: HttpRequest, res: HttpResponder, id: String) {
        val e = lib.lookup(id) ?: return res.text(404, Lang.s(R.string.http_not_found))
        val uri = e.uri ?: return res.text(404, Lang.s(R.string.http_not_found))
        val t = param(req, "t").toDoubleOrNull() ?: 0.0
        val a = param(req, "a").toIntOrNull() ?: -1
        val tx = param(req, "tx") == "1"
        if (t < 1.0) ServerState.log("▶ ${e.title} → ${ServerState.clientName(req.remote)}")
        res.stream(200, "video/mp4", mapOf("Cache-Control" to "no-store", "Access-Control-Allow-Origin" to "*")) { out ->
            Remux.stream(ctx, uri, (t * 1_000_000).toLong(), a, tx, out)
        }
    }
}
