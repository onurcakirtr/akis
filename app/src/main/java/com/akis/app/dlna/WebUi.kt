package com.akis.app.dlna

/** Tarayıcıdan izlemek için sade, şık web arayüzü (TV tarayıcısı, PC, başka telefon). */
object WebUi {
    private fun h(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private const val CSS = """
:root{--bg:#0d0d10;--card:#19191e;--fg:#f3f3f6;--mut:#9b9ba6;--acc:#d71921;--r:24px}
@media(prefers-color-scheme:light){:root{--bg:#f4f4f6;--card:#fff;--fg:#111;--mut:#666}}
*{box-sizing:border-box}
body{margin:0;font-family:system-ui,Roboto,sans-serif;background:var(--bg);color:var(--fg);
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
.b{padding:12px 20px;border-radius:999px;background:var(--acc);color:#fff;text-decoration:none;border:0;font-size:15px;cursor:pointer}
.b.o{background:var(--card);color:var(--fg)}
code{word-break:break-all;color:var(--mut);font-size:12px}
form.q{margin-left:auto;flex:0 1 320px}
form.q input{width:100%;padding:11px 18px;border-radius:999px;border:1px solid color-mix(in srgb,var(--mut) 35%,transparent);
background:var(--card);color:var(--fg);font-size:15px;outline:none;transition:border-color .2s}
form.q input:focus{border-color:var(--acc)}
"""

    private fun searchBox(query: String) =
        "<form class=\"q\" action=\"/ara\"><input name=\"q\" type=\"search\" placeholder=\"Ara…\" value=\"${h(query)}\"></form>"

    private fun page(server: String, title: String, crumbs: String, body: String, header: String = "") =
        "<!doctype html><html lang=\"tr\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>${h(title)}</title><style>$CSS</style></head><body><header><div class=\"dot\"></div><h1>${h(server)}</h1>$header</header>" +
            "<nav>$crumbs</nav>$body</body></html>"

    private fun sizeText(b: Long) = when {
        b >= 1L shl 30 -> String.format("%.1f GB", b / 1073741824.0)
        b >= 1L shl 20 -> String.format("%.1f MB", b / 1048576.0)
        else -> String.format("%d KB", b / 1024)
    }

    fun list(
        server: String,
        title: String,
        entries: List<Entry>,
        parentId: String?,
        search: Boolean = false,
        query: String = "",
    ): String {
        val cards = StringBuilder()
        for (e in entries) {
            val id = Xml.enc(e.id)
            when (e.kind) {
                Kind.FOLDER -> {
                    val icon = when (e.id) {
                        "v" -> "🎬"; "a" -> "🎵"; "i" -> "🖼️"; "r" -> "✨"; "f" -> "🗂️"
                        else -> "📁"
                    }
                    val sub = if (e.childCount >= 0) "<span class=\"s\">${e.childCount} öğe</span>" else ""
                    cards.append("<a class=\"c\" href=\"/w/$id\"><div class=\"ic\">$icon</div><span class=\"t\">${h(e.title)}</span>$sub</a>")
                }
                Kind.IMAGE -> cards.append(
                    "<a class=\"c\" href=\"/m/$id/file.${e.ext}\" target=\"_blank\"><img class=\"th\" loading=\"lazy\" src=\"/t/$id.jpg\" " +
                        "onerror=\"this.style.display='none'\"><span class=\"t\">${h(e.title)}</span></a>",
                )
                else -> cards.append(
                    "<a class=\"c\" href=\"/p/$id\"><img class=\"th\" loading=\"lazy\" src=\"/t/$id.jpg\" " +
                        "onerror=\"this.outerHTML='<div class=ic>${if (e.kind == Kind.VIDEO) "🎬" else "🎵"}</div>'\">" +
                        "<span class=\"t\">${h(e.title)}</span><span class=\"s\">${sizeText(e.size)}</span></a>",
                )
            }
        }
        val crumbs = buildString {
            append("<a href=\"/\">⌂ Ana sayfa</a>")
            if (parentId != null) {
                val back = if (parentId == "0") "/" else "/w/${Xml.enc(parentId)}"
                append(" &nbsp;·&nbsp; <a href=\"$back\">← Geri</a> &nbsp;·&nbsp; ${h(title)}")
            }
        }
        val body = if (entries.isEmpty()) "<div class=\"p\"><p>Burada bir şey yok.</p></div>" else "<main>$cards</main>"
        return page(server, title, crumbs, body, if (search) searchBox(query) else "")
    }

    fun player(server: String, e: Entry, absoluteUrl: String, subs: List<Sub> = emptyList()): String {
        val src = "/m/${Xml.enc(e.id)}/file.${e.ext}"
        val tag = if (e.kind == Kind.VIDEO) "video" else "audio"
        val back = if (e.parentId == "0") "/" else "/w/${Xml.enc(e.parentId)}"
        val tracks = subs.mapIndexed { i, sub ->
            "<track kind=\"subtitles\" src=\"/s/${Xml.enc(sub.id)}.vtt\" srclang=\"${h(sub.lang.ifEmpty { "und" })}\" " +
                "label=\"${h(sub.label)}\"${if (i == 0) " default" else ""}>"
        }.joinToString("")
        val body = "<div class=\"p\"><$tag controls autoplay playsinline src=\"$src\">$tracks</$tag>" +
            "<h2>${h(e.title)}</h2><div class=\"btns\">" +
            "<a class=\"b\" href=\"vlc://${h(absoluteUrl)}\">VLC'de aç</a>" +
            "<a class=\"b o\" href=\"$src\" download>İndir</a>" +
            "<button class=\"b o\" onclick=\"navigator.clipboard.writeText('${h(absoluteUrl)}');this.textContent='Kopyalandı ✓'\">Bağlantıyı kopyala</button>" +
            "</div><p><code>${h(absoluteUrl)}</code></p></div>"
        return page(server, e.title, "<a href=\"$back\">← Geri</a>", body)
    }
}
