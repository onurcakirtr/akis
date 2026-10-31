package com.akis.app.dlna

import com.akis.app.Lang
import com.akis.app.R

/** Sunucunun kendi ürettiği küçük sayfalar (PIN, onay bekleme). Asıl web uygulaması assets/web içinde. */
object WebUi {
    /** Tek tırnaklı JS dizesi için kaçış. */
    private fun js(s: String) = s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")

    private fun h(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Seçili temanın renkleri (Ayarlar → Görünüm → Tema). */
    @Volatile var themeCss: () -> String = { "" }

    private const val CSS = """
*{box-sizing:border-box}
body{margin:0;font-family:var(--font);background:var(--bg);color:var(--fg);
background-image:radial-gradient(color-mix(in srgb,var(--mut) 45%,transparent) .7px,transparent .7px);background-size:18px 18px;background-attachment:fixed}
header{position:sticky;top:0;z-index:2;padding:18px 22px;display:flex;align-items:center;gap:12px;
backdrop-filter:blur(16px);background:color-mix(in srgb,var(--bg) 78%,transparent)}
.dot{width:12px;height:12px;border-radius:50%;background:var(--acc);box-shadow:0 0 12px var(--acc)}
h1{font-size:20px;margin:0;letter-spacing:.4px}
nav{padding:0 22px 6px;font-size:14px;color:var(--mut)}nav a{color:var(--mut);text-decoration:none}
main{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:14px;padding:14px 22px 48px}
a.c{position:relative;display:flex;flex-direction:column;gap:8px;padding:14px;border-radius:var(--r);background:var(--card);
color:var(--fg);text-decoration:none;transition:transform .35s cubic-bezier(.3,1.7,.5,1),border-radius .35s}
a.c:hover,a.c:focus{transform:scale(1.04);border-radius:34px;outline:2px solid var(--acc)}
.ic{width:56px;height:56px;border-radius:40%;display:grid;place-items:center;font-size:28px;background:color-mix(in srgb,var(--acc) 18%,transparent)}
.th{width:100%;aspect-ratio:16/10;object-fit:cover;border-radius:16px;background:#0004}
.t{font-size:14px;word-break:break-word;line-height:1.3}.s{font-size:12px;color:var(--mut)}
.p{padding:8px 22px 40px;max-width:1100px;margin:auto}
video,audio{width:100%;max-height:78vh;border-radius:var(--r);background:#000}
.btns{display:flex;gap:10px;flex-wrap:wrap;margin-top:14px}
.b{padding:12px 20px;border-radius:999px;background:var(--acc);color:var(--onacc);text-decoration:none;border:0;font-size:15px;cursor:pointer}
.b.o{background:var(--card);color:var(--fg)}
code{word-break:break-all;color:var(--mut);font-size:12px}
form.q{margin-left:auto;flex:0 1 320px}
a.up{margin-left:auto;padding:10px 16px;border-radius:999px;background:var(--card);color:var(--fg);text-decoration:none;font-size:14px;white-space:nowrap}
form.q+a.up{margin-left:0}
.drop{border:2px dashed color-mix(in srgb,var(--mut) 50%,transparent);border-radius:var(--r);padding:48px 20px;text-align:center;
transition:border-color .2s,transform .35s cubic-bezier(.3,1.7,.5,1)}
.drop.on{border-color:var(--acc);transform:scale(1.02)}
.f{background:var(--card);border-radius:18px;padding:12px 16px;margin-top:10px}
.bar{height:6px;border-radius:3px;background:color-mix(in srgb,var(--mut) 25%,transparent);margin-top:8px;overflow:hidden}
.bar i{display:block;height:100%;width:0;background:var(--acc);transition:width .2s}
form.q input{width:100%;padding:11px 18px;border-radius:999px;border:1px solid color-mix(in srgb,var(--mut) 35%,transparent);
background:var(--card);color:var(--fg);font-size:15px;outline:none;transition:border-color .2s}
form.q input:focus{border-color:var(--acc)}
"""

    private fun page(server: String, title: String, crumbs: String, body: String, header: String = "") =
        "<!doctype html><html lang=\"${Lang.locale.language}\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>${h(title)}</title><style>${themeCss()}$CSS</style></head><body><header><div class=\"dot\"></div><h1>${h(server)}</h1>$header</header>" +
            "<nav>$crumbs</nav>$body</body></html>"

    /** Hızlı paylaşım indirme sayfası. name null ise bağlantı geçersiz. */
    fun sharePage(server: String, name: String?, size: Long, expires: Long, dl: String): String {
        if (name == null) {
            return page(server, Lang.s(R.string.w_share_title), "", "<div class=\"p\" style=\"max-width:520px\"><p>${h(Lang.s(R.string.w_share_gone))}</p></div>")
        }
        val sz = when {
            size >= 1L shl 30 -> String.format(java.util.Locale.US, "%.2f GB", size / 1073741824.0)
            size >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", size / 1048576.0)
            else -> "${maxOf(1, size / 1024)} KB"
        }
        val until = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, Lang.locale).format(java.util.Date(expires))
        val body = "<div class=\"p\" style=\"max-width:560px\"><h2>${h(Lang.s(R.string.w_share_title))}</h2>" +
            "<p style=\"font-size:18px;word-break:break-all\">${h(name)}</p><p class=\"s\">$sz • ${h(Lang.s(R.string.w_share_expires, until))}</p>" +
            "<div class=\"btns\"><a class=\"b\" href=\"${h(dl)}\">${h(Lang.s(R.string.web_download))}</a></div></div>"
        return page(server, name, "", body)
    }

    /** Uzaktan (tünel) giriş sayfası. */
    fun remoteLoginPage(server: String, msg: String?, totp: Boolean): String {
        val input = "style=\"padding:12px 18px;border-radius:999px;border:0;background:var(--card);color:var(--fg);font-size:16px\""
        val body = "<div class=\"p\" style=\"max-width:420px\"><h2>${h(Lang.s(R.string.remote_login_title))}</h2>" +
            (if (msg != null) "<p class=\"s\" style=\"color:var(--acc)\">${h(msg)}</p>" else "") +
            "<form method=\"post\" action=\"/rlogin\" style=\"display:flex;flex-direction:column;gap:10px\">" +
            "<input name=\"pw\" type=\"password\" autocomplete=\"current-password\" autofocus $input>" +
            (if (totp) "<input name=\"code\" inputmode=\"numeric\" autocomplete=\"one-time-code\" maxlength=\"6\" placeholder=\"${h(Lang.s(R.string.web_ad_code))}\" $input>" else "") +
            "<button class=\"b\">${h(Lang.s(R.string.remote_login_title))}</button></form></div>"
        return page(server, Lang.s(R.string.remote_login_title), "", body)
    }

    /** Telefondan onay beklenirken: durumu yoklar, onaylanınca ana sayfaya geçer. */
    fun remoteWaitPage(server: String, id: String): String {
        val body = "<div class=\"p\" style=\"max-width:520px\"><h2>${h(Lang.s(R.string.remote_waiting))}</h2></div>" +
            "<script>(function p(){fetch('/rlogin/status?r=${id}').then(function(r){return r.json()}).then(function(j){" +
            "if(j.s==='ok')location.href='/';else if(j.s==='pending')setTimeout(p,1500);else location.href='/rlogin';})" +
            ".catch(function(){setTimeout(p,3000)})})();</script>"
        return page(server, Lang.s(R.string.remote_login_title), "", body)
    }

    /** PIN giriş sayfası. */
    fun pinPage(server: String, wrong: Boolean, locked: Boolean): String {
        val msg = when {
            locked -> "<p class=\"s\">${h(Lang.s(R.string.w_pin_locked))}</p>"
            wrong -> "<p class=\"s\" style=\"color:var(--acc)\">${h(Lang.s(R.string.w_pin_wrong))}</p>"
            else -> "<p class=\"s\">${h(Lang.s(R.string.w_pin_info))}</p>"
        }
        val body = "<div class=\"p\" style=\"max-width:420px\"><h2>${h(Lang.s(R.string.w_pin_enter))}</h2>$msg" +
            "<form method=\"post\" action=\"/pin\" class=\"btns\"><input name=\"pin\" type=\"password\" inputmode=\"numeric\" " +
            "autocomplete=\"off\" autofocus maxlength=\"6\" style=\"flex:1;padding:12px 18px;border-radius:999px;border:0;" +
            "background:var(--card);color:var(--fg);font-size:18px;letter-spacing:6px\"${if (locked) " disabled" else ""}>" +
            "<button class=\"b\"${if (locked) " disabled" else ""}>${h(Lang.s(R.string.w_pin_go))}</button></form></div>"
        return page(server, "PIN", "", body)
    }

    /** Cihaz onayı beklenirken gösterilen, kendini yenileyen sayfa. */
    fun waitPage(server: String): String {
        val body = "<div class=\"p\" style=\"max-width:520px\"><h2>${h(Lang.s(R.string.waiting_approval))}</h2>" +
            "<p class=\"s\">${Lang.s(R.string.w_wait_text)}</p></div>" +
            "<script>setTimeout(function(){location.reload()},3000)</script>"
        return page(server, Lang.s(R.string.w_wait_short), "", body)
    }

}
