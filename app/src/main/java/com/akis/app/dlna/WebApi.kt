package com.akis.app.dlna

import android.content.Context
import com.akis.app.FileAccess
import com.akis.app.Lang
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
            p.startsWith("/x/") -> stream(req, res, decode(p.removePrefix("/x/")))
            p == "/api/files" -> files(param(req, "p"), res)
            p == "/raw" -> raw(req, res, param(req, "p"))
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
        if (size > 0 && start >= size) return res.send(416, null, 0, mapOf("Content-Range" to "bytes */$size"))
        val len = if (size == 0L) 0 else end - start + 1
        val headers = linkedMapOf("Accept-Ranges" to "bytes", "Cache-Control" to "no-cache")
        if (partial) headers["Content-Range"] = "bytes $start-$end/$size"
        val type = MediaLibrary.mimeFor(f.name, null)
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
                ServerState.addBytes(len - left)
            }
        }
    }

    /** Klasörü (alt klasörleriyle, gizli dosyalar hariç) telefonda geçici dosya oluşturmadan ZIP olarak akıtır. */
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
