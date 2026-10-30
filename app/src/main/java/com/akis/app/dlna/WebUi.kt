package com.akis.app.dlna

import com.akis.app.Lang
import com.akis.app.R

/** Tarayıcıdan izlemek için sade, şık web arayüzü (TV tarayıcısı, PC, başka telefon). */
object WebUi {
    /** Tek tırnaklı JS dizesi için kaçış. */
    private fun js(s: String) = s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")

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

    private fun searchBox(query: String) =
        "<form class=\"q\" action=\"/ara\"><input name=\"q\" type=\"search\" placeholder=\"${h(Lang.s(R.string.w_search))}\" value=\"${h(query)}\"></form>"

    private fun page(server: String, title: String, crumbs: String, body: String, header: String = "") =
        "<!doctype html><html lang=\"${Lang.locale.language}\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>${h(title)}</title><style>$CSS</style></head><body><header><div class=\"dot\"></div><h1>${h(server)}</h1>$header</header>" +
            "<nav>$crumbs</nav>$body</body></html>"

    private fun sizeText(b: Long) = when {
        b >= 1L shl 30 -> String.format("%.1f GB", b / 1073741824.0)
        b >= 1L shl 20 -> String.format("%.1f MB", b / 1048576.0)
        else -> String.format("%d KB", b / 1024)
    }

    /** Kaldığı yerden başlatır ve 10 sn'de bir konumu telefona bildirir. */
    private fun progressScript(id: String, resumeMs: Long) = """<script>
(function(){var v=document.querySelector('video');if(!v)return;var id='${Xml.enc(id)}',r=${resumeMs / 1000.0};
v.addEventListener('loadedmetadata',function(){if(r>5&&r<v.duration-10)v.currentTime=r;});
function s(){if(!v.duration||isNaN(v.duration))return;try{fetch('/h?id='+id+'&t='+v.currentTime+'&d='+v.duration,{method:'POST',keepalive:true});}catch(e){}}
setInterval(function(){if(!v.paused)s();},10000);v.addEventListener('pause',s);v.addEventListener('ended',s);
window.addEventListener('pagehide',s);})();
</script>"""

    /** Sürükle-bırak dosya yükleme sayfası; dosyalar sırayla PUT /yukle/<ad> ile gönderilir. */
    fun uploadPage(server: String, target: String): String {
        val body = "<div class=\"p\" style=\"max-width:720px\"><h2>${h(Lang.s(R.string.w_upload_title))}</h2>" +
            "<p class=\"s\">${Lang.s(R.string.w_upload_dest, h(target))}</p>" +
            "<div id=\"drop\" class=\"drop\"><p>${h(Lang.s(R.string.w_drop))}</p>" +
            "<label class=\"b\">${h(Lang.s(R.string.w_pick))}<input id=\"pick\" type=\"file\" multiple hidden></label></div><div id=\"list\"></div></div>" +
            "<script>var T={q:'${js(Lang.s(R.string.w_queued))}',done:'${js(Lang.s(R.string.w_sent))}'," +
            "err:'${js(Lang.s(R.string.w_error))}',lost:'${js(Lang.s(R.string.w_lost))}'};</script>" + UPLOAD_JS
        return page(server, Lang.s(R.string.w_upload), "<a href=\"/\">${h(Lang.s(R.string.w_back))}</a>", body)
    }

    private const val UPLOAD_JS = """<script>
(function(){var q=[],busy=false,d=document.getElementById('drop'),l=document.getElementById('list');
function fmt(b){return b>1073741824?(b/1073741824).toFixed(2)+' GB':b>1048576?(b/1048576).toFixed(1)+' MB':Math.ceil(b/1024)+' KB'}
function add(fs){for(var i=0;i<fs.length;i++){var f=fs[i],e=document.createElement('div');e.className='f';
e.innerHTML='<div class="t"></div><div class="s"></div><div class="bar"><i></i></div>';e.querySelector('.t').textContent=f.name;
e.querySelector('.s').textContent=fmt(f.size)+' • '+T.q;l.appendChild(e);q.push({f:f,e:e});}next();}
function next(){if(busy||!q.length)return;busy=true;var it=q.shift(),x=new XMLHttpRequest(),s=it.e.querySelector('.s'),b=it.e.querySelector('i');
x.open('PUT','/yukle/'+encodeURIComponent(it.f.name));
x.upload.onprogress=function(ev){if(ev.lengthComputable){var p=ev.loaded/ev.total*100;b.style.width=p+'%';s.textContent=fmt(ev.loaded)+' / '+fmt(ev.total)+' • %'+Math.floor(p);}};
x.onload=function(){s.textContent=x.status==200?fmt(it.f.size)+' • '+T.done:T.err+' ('+x.status+')';b.style.width='100%';busy=false;next();};
x.onerror=function(){s.textContent=T.lost;busy=false;next();};x.send(it.f);}
document.getElementById('pick').onchange=function(e){add(e.target.files);e.target.value='';};
['dragenter','dragover'].forEach(function(n){d.addEventListener(n,function(e){e.preventDefault();d.classList.add('on')})});
['dragleave','drop'].forEach(function(n){d.addEventListener(n,function(e){e.preventDefault();d.classList.remove('on')})});
d.addEventListener('drop',function(e){add(e.dataTransfer.files)});})();
</script>"""

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

    fun list(
        server: String,
        title: String,
        entries: List<Entry>,
        parentId: String?,
        search: Boolean = false,
        query: String = "",
        upload: Boolean = false,
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
                    val sub = if (e.childCount >= 0) "<span class=\"s\">${h(Lang.q(R.plurals.count_items, e.childCount))}</span>" else ""
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
            append("<a href=\"/\">${h(Lang.s(R.string.w_home))}</a>")
            if (parentId != null) {
                val back = if (parentId == "0") "/" else "/w/${Xml.enc(parentId)}"
                append(" &nbsp;·&nbsp; <a href=\"$back\">${h(Lang.s(R.string.w_back))}</a> &nbsp;·&nbsp; ${h(title)}")
            }
        }
        val body = if (entries.isEmpty()) "<div class=\"p\"><p>${h(Lang.s(R.string.w_empty))}</p></div>" else "<main>$cards</main>"
        val header = (if (search) searchBox(query) else "") + (if (upload) "<a class=\"up\" href=\"/yukle\">${h(Lang.s(R.string.w_upload_btn))}</a>" else "")
        return page(server, title, crumbs, body, header)
    }

    /** @param resumeMs kaldığı yer; -1 ise geçmiş kapalı (konum bildirilmez) */
    fun player(server: String, e: Entry, absoluteUrl: String, subs: List<Sub> = emptyList(), resumeMs: Long = -1): String {
        val src = "/m/${Xml.enc(e.id)}/file.${e.ext}"
        val tag = if (e.kind == Kind.VIDEO) "video" else "audio"
        val back = if (e.parentId == "0") "/" else "/w/${Xml.enc(e.parentId)}"
        val tracks = subs.mapIndexed { i, sub ->
            "<track kind=\"subtitles\" src=\"/s/${Xml.enc(sub.id)}.vtt\" srclang=\"${h(sub.lang.ifEmpty { "und" })}\" " +
                "label=\"${h(sub.label)}\"${if (i == 0) " default" else ""}>"
        }.joinToString("")
        val body = "<div class=\"p\"><$tag controls autoplay playsinline src=\"$src\">$tracks</$tag>" +
            "<h2>${h(e.title)}</h2><div class=\"btns\">" +
            "<a class=\"b\" href=\"vlc://${h(absoluteUrl)}\">${h(Lang.s(R.string.w_open_vlc))}</a>" +
            "<a class=\"b o\" href=\"$src\" download>${h(Lang.s(R.string.w_download))}</a>" +
            "<button class=\"b o\" onclick=\"navigator.clipboard.writeText('${h(absoluteUrl)}');this.textContent='${h(js(Lang.s(R.string.w_copied)))}'\">${h(Lang.s(R.string.w_copy_link))}</button>" +
            "</div><p><code>${h(absoluteUrl)}</code></p></div>" +
            if (resumeMs >= 0 && e.kind == Kind.VIDEO) progressScript(e.id, resumeMs) else ""
        return page(server, e.title, "<a href=\"$back\">${h(Lang.s(R.string.w_back))}</a>", body)
    }
}
