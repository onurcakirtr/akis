package com.akis.app.dlna

import android.content.Context
import com.akis.app.Admin
import com.akis.app.DeviceAccess
import com.akis.app.FileAccess
import com.akis.app.Prefs
import com.akis.app.QuickShare
import com.akis.app.ServerState
import com.akis.app.net.HttpRequest
import com.akis.app.net.HttpResponder
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

/**
 * Web'den yönetim (/admin/…). Her istek: yönetim açık + geçerli oturum çerezi + aynı IP.
 * Değişiklik isteklerinde ayrıca "X-Akis: 1" başlığı aranır (başka sitelerden gizli istek gönderilemesin).
 * Güvenlik ayarları (yönetici şifresi, web PIN'i, cihaz onayı, yönetimi açma, tüm dosyalar) bilerek yalnızca telefondan değişir.
 */
class AdminApi(private val ctx: Context, private val lib: MediaLibrary) {

    /** Web'den değiştirilebilen açık / kapalı ayarlar. */
    private val SWITCHES = listOf(
        "shareVideo", "shareAudio", "shareImage", "showRecent", "hideFolders",
        "subtitles", "search", "history", "historyMarks", "upload",
    )

    private fun json(res: HttpResponder, o: JSONObject, headers: Map<String, String> = emptyMap()) =
        res.bytes(200, "application/json; charset=utf-8", o.toString().toByteArray(Charsets.UTF_8), headers + ("Cache-Control" to "no-store"))

    private fun err(res: HttpResponder, code: Int, what: String) =
        res.bytes(code, "application/json; charset=utf-8", JSONObject().put("err", what).toString().toByteArray(Charsets.UTF_8))

    private fun param(req: HttpRequest, name: String): String =
        req.query.split('&').firstOrNull { it.substringBefore('=') == name }?.substringAfter('=', "")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""

    private fun body(req: HttpRequest): JSONObject = runCatching { JSONObject(String(req.body, Charsets.UTF_8)) }.getOrDefault(JSONObject())

    /** @return true ise cevaplandı */
    fun handle(req: HttpRequest, res: HttpResponder): Boolean {
        val p = req.path
        if (!p.startsWith("/admin/")) return false
        if (!Prefs.current.adminOn || !Admin.hasPassword) { err(res, 404, "off"); return true }
        val ip = req.remote
        val post = req.method == "POST"
        if (post && req.headers["x-akis"] != "1") { err(res, 403, "csrf"); return true }
        when (p) {
            "/admin/login" -> if (post) {
                val b = body(req)
                val r = Admin.login(ip, ServerState.clientName(ip), b.optString("pw"), b.optString("code"))
                if (r == "wrong" || r == "locked" || r == "off" || r == "code") err(res, 403, r) else json(res, JSONObject().put("r", r))
            } else err(res, 405, "method")
            "/admin/status" -> {
                val s = Admin.status(param(req, "r"), ip)
                if (s.startsWith("ok:")) {
                    json(
                        res, JSONObject().put("s", "ok"),
                        mapOf("Set-Cookie" to "akis_admin=${s.substring(3)}; Path=/admin; Max-Age=43200; HttpOnly; SameSite=Strict"),
                    )
                } else {
                    json(res, JSONObject().put("s", s))
                }
            }
            else -> {
                if (!Admin.valid(req.headers["cookie"], ip)) { err(res, 401, "login"); return true }
                when (p) {
                    "/admin/state" -> json(res, state().put("me", ip))
                    "/admin/dirs" -> dirs(param(req, "p"), res)
                    "/admin/logout" -> { Admin.endSession(); json(res, JSONObject().put("ok", true)) }
                    "/admin/do" -> if (post) act(body(req), ip, res) else err(res, 405, "method")
                    else -> err(res, 404, "?")
                }
            }
        }
        return true
    }

    private fun state(): JSONObject {
        val s = Prefs.current
        val sw = JSONObject()
        val values = mapOf(
            "shareVideo" to s.shareVideo, "shareAudio" to s.shareAudio, "shareImage" to s.shareImage, "showRecent" to s.showRecent,
            "hideFolders" to s.hideFolders, "subtitles" to s.subtitles, "search" to s.search, "history" to s.history,
            "historyMarks" to s.historyMarks, "upload" to s.upload,
        )
        for (k in SWITCHES) sw.put(k, values[k] == true)
        val folders = JSONArray()
        s.folders.forEach { folders.put(JSONObject().put("uri", it).put("name", MediaLibrary.treeName(ctx, it)).put("path", it.startsWith("/"))) }
        val buckets = JSONArray()
        lib.allBuckets().forEach { b -> buckets.put(JSONObject().put("id", b.id).put("name", b.name).put("n", b.count).put("hidden", b.id in s.hiddenBuckets)) }
        val clients = JSONArray()
        ServerState.clients.value.forEach { c -> clients.put(JSONObject().put("ip", c.ip).put("name", c.name).put("last", c.lastSeen)) }
        val known = JSONArray()
        DeviceAccess.devices.value.forEach { d -> known.put(JSONObject().put("ip", d.ip).put("name", d.name).put("allowed", d.allowed).put("until", d.until)) }
        val shares = JSONArray()
        QuickShare.shares.value.forEach { x ->
            shares.put(JSONObject().put("token", x.token).put("name", x.name).put("expires", x.expires).put("once", x.once).put("dl", x.downloads))
        }
        return JSONObject()
            .put("name", s.name).put("switches", sw).put("subLang", s.subLang).put("langs", JSONArray(Subtitles.CHOICES))
            .put("folders", folders).put("buckets", buckets).put("clients", clients).put("known", known).put("shares", shares)
            .put("filesGranted", FileAccess.granted(ctx)).put("now", System.currentTimeMillis())
            .put("ring", s.ring).put("ringing", com.akis.app.Ringer.active)
    }

    /** Klasör seçici: yalnızca klasörler (tüm dosyalara erişim izni varsa). */
    private fun dirs(path: String, res: HttpResponder) {
        if (!FileAccess.granted(ctx)) return err(res, 403, "perm")
        val items = JSONArray()
        if (path.isEmpty()) {
            FileAccess.roots().forEach { items.put(JSONObject().put("n", FileAccess.rootName(ctx, it)).put("p", it.path)) }
            return json(res, JSONObject().put("path", "").put("items", items))
        }
        val d = FileAccess.resolve(path)?.takeIf { it.isDirectory } ?: return err(res, 404, "?")
        FileAccess.list(d).filter { it.isDirectory }.forEach { items.put(JSONObject().put("n", it.name).put("p", it.path)) }
        val parent = d.parentFile?.path?.takeIf { FileAccess.resolve(it) != null } ?: ""
        json(res, JSONObject().put("path", d.path).put("parent", parent).put("items", items))
    }

    private fun act(b: JSONObject, ip: String, res: HttpResponder) {
        val op = b.optString("op")
        when (op) {
            "set" -> {
                val k = b.optString("key")
                when {
                    k in SWITCHES -> {
                        val v = b.optBoolean("value")
                        Prefs.set(k, v)
                        Admin.audit("$k = $v", ip)
                    }
                    k == "name" -> {
                        val v = b.optString("value").trim().take(60)
                        if (v.isEmpty()) return err(res, 400, "empty")
                        Prefs.update { putString("name", v) }
                        Admin.audit("name = $v", ip)
                    }
                    k == "subLang" && b.optString("value") in Subtitles.CHOICES -> {
                        Prefs.update { putString("subLang", b.optString("value")) }
                        Admin.audit("subLang = ${b.optString("value")}", ip)
                    }
                    else -> return err(res, 403, "key")
                }
            }
            "hide" -> {
                Prefs.setHidden(b.optString("bucket"), b.optBoolean("on"))
                Admin.audit("hidden ${b.optString("bucket")} = ${b.optBoolean("on")}", ip)
            }
            "folderRemove" -> {
                val uri = b.optString("uri")
                if (uri !in Prefs.current.folders) return err(res, 404, "?")
                if (!uri.startsWith("/")) runCatching {
                    ctx.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(uri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                Prefs.removeFolder(uri)
                Admin.audit("- folder ${MediaLibrary.treeName(ctx, uri)}", ip)
            }
            "folderAdd" -> {
                if (!FileAccess.granted(ctx)) return err(res, 403, "perm")
                val d = FileAccess.resolve(b.optString("path"))?.takeIf { it.isDirectory } ?: return err(res, 404, "?")
                Prefs.addFolder(d.path)
                Admin.audit("+ folder ${d.path}", ip)
            }
            "kick" -> {
                val target = b.optString("ip")
                if (target == ip) return err(res, 400, "self")
                DeviceAccess.kick(target, ServerState.clientName(target))
            }
            "ban" -> {
                val target = b.optString("ip")
                if (target == ip) return err(res, 400, "self")
                DeviceAccess.ban(target, ServerState.clientName(target), b.optInt("min", 15).coerceIn(1, 7 * 24 * 60))
            }
            "unblock" -> {
                DeviceAccess.forget(b.optString("ip"))
                Admin.audit("unblock ${b.optString("ip")}", ip)
            }
            "ring" -> {
                if (!Prefs.current.ring) return err(res, 403, "off")
                com.akis.app.Ringer.start(ctx, ServerState.clientName(ip))
                Admin.audit("ring", ip)
            }
            "ringStop" -> com.akis.app.Ringer.stop()
            "shareClose" -> {
                QuickShare.remove(b.optString("token"))
                Admin.audit("share closed", ip)
            }
            else -> return err(res, 400, "op")
        }
        json(res, JSONObject().put("ok", true))
    }
}
