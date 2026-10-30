package com.akis.app.cast

import com.akis.app.dlna.Entry
import com.akis.app.dlna.Kind
import com.akis.app.dlna.Sub
import com.akis.app.dlna.Xml
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * DLNA (UPnP MediaRenderer) cihazlarını bulur ve AVTransport / RenderingControl ile kumanda eder.
 * Samsung / LG / Sony TV'ler, Kodi, DLNA hoparlörler.
 */
class DlnaRenderer private constructor(
    private val device: TvDevice,
    private val avUrl: String,
    private val rcUrl: String?,
) : Backend {
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var polling = true
    @Volatile private var loaded = false

    init {
        thread(name = "dlna-poll", isDaemon = true) { pollLoop() }
    }

    private fun bg(block: () -> Unit) {
        io.execute { runCatching(block) }
    }

    // ---------------- Backend ----------------

    override fun load(e: Entry, url: String, mime: String, subs: List<Pair<String, Sub>>, startMs: Long, base: String) = bg {
        val didl = Xml.didl(listOf(e), base, smartSubs = true)
        runCatching { av("Stop") }
        val r = av("SetAVTransportURI", "CurrentURI" to url, "CurrentURIMetaData" to didl)
        if (r == null) {
            Remote.message.value = "TV dosyayı kabul etmedi"
            return@bg
        }
        loaded = true
        av("Play", "Speed" to "1")
        if (startMs > 0 && e.kind != Kind.IMAGE) {
            // TV oynatmaya başlayınca kaldığı yere sar
            for (i in 0 until 20) {
                Thread.sleep(500)
                if (state() == "PLAYING") break
            }
            av("Seek", "Unit" to "REL_TIME", "Target" to hms(startMs))
        }
    }

    override fun pause() = bg { av("Pause") }
    override fun resume() = bg { av("Play", "Speed" to "1") }
    override fun stop() = bg { av("Stop"); loaded = false }
    override fun seek(ms: Long) = bg { av("Seek", "Unit" to "REL_TIME", "Target" to hms(ms)) }

    override fun setVolume(v: Float) = bg {
        rc("SetVolume", "Channel" to "Master", "DesiredVolume" to (v * 100).toInt().toString())
    }

    override fun setMuted(m: Boolean) = bg {
        rc("SetMute", "Channel" to "Master", "DesiredMute" to if (m) "1" else "0")
    }

    override fun disconnect() {
        polling = false
        io.shutdown()
        synchronized(active) { if (active[device.id] === this) active.remove(device.id) }
    }

    // ---------------- Durum takibi ----------------

    private fun state(): String? = av("GetTransportInfo")?.let { Xml.arg(it, "CurrentTransportState") }

    private fun pollLoop() {
        var tick = 0
        while (polling) {
            try {
                val st = state()
                val pos = av("GetPositionInfo")
                val vol = if (rcUrl != null && tick % 3 == 0) rc("GetVolume", "Channel" to "Master")?.let { Xml.arg(it, "CurrentVolume").toIntOrNull() } else null
                val mute = if (rcUrl != null && tick % 3 == 0) rc("GetMute", "Channel" to "Master")?.let { Xml.arg(it, "CurrentMute") } else null
                if (st != null) {
                    val rel = pos?.let { parseHms(Xml.arg(it, "RelTime")) }
                    val dur = pos?.let { parseHms(Xml.arg(it, "TrackDuration")) }
                    Remote.report { s ->
                        if (s.device?.id != device.id) return@report s
                        s.copy(
                            playing = st == "PLAYING",
                            buffering = st == "TRANSITIONING",
                            idle = !loaded || st == "STOPPED" || st == "NO_MEDIA_PRESENT",
                            positionMs = rel ?: s.positionMs,
                            durationMs = dur?.takeIf { it > 0 } ?: s.durationMs,
                            volume = vol?.let { it / 100f } ?: s.volume,
                            muted = mute?.let { it == "1" || it.equals("true", true) } ?: s.muted,
                        )
                    }
                }
            } catch (_: Exception) {
            }
            tick++
            Thread.sleep(1000)
        }
    }

    // ---------------- SOAP ----------------

    private fun av(action: String, vararg args: Pair<String, String>) =
        soap(avUrl, "urn:schemas-upnp-org:service:AVTransport:1", action, listOf("InstanceID" to "0") + args)

    private fun rc(action: String, vararg args: Pair<String, String>) =
        rcUrl?.let { soap(it, "urn:schemas-upnp-org:service:RenderingControl:1", action, listOf("InstanceID" to "0") + args) }

    companion object {
        private const val MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"

        private data class Info(val device: TvDevice, val av: String, val rc: String?)

        private val known = ConcurrentHashMap<String, Info>()
        private val active = HashMap<String, DlnaRenderer>()

        fun forDevice(d: TvDevice): DlnaRenderer? {
            val info = known[d.id] ?: return null
            synchronized(active) {
                active[d.id]?.let { return it }
                return DlnaRenderer(info.device, info.av, info.rc).also { active[d.id] = it }
            }
        }

        /** M-SEARCH ile ağdaki DLNA oynatıcılarını ~3 sn'de bulur. */
        fun discover(): List<TvDevice> {
            val locations = LinkedHashSet<String>()
            try {
                DatagramSocket().use { sock ->
                    sock.soTimeout = 800
                    val msg = (
                        "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
                            "ST: $MEDIA_RENDERER\r\n\r\n"
                        ).toByteArray()
                    val group = InetAddress.getByName("239.255.255.250")
                    val end = System.currentTimeMillis() + 3000
                    var sent = 0
                    val buf = ByteArray(4096)
                    while (System.currentTimeMillis() < end) {
                        if (sent < 3) {
                            sock.send(DatagramPacket(msg, msg.size, group, 1900))
                            sent++
                        }
                        try {
                            val pkt = DatagramPacket(buf, buf.size)
                            sock.receive(pkt)
                            val text = String(pkt.data, 0, pkt.length)
                            text.lineSequence().firstOrNull { it.startsWith("LOCATION:", true) }
                                ?.substringAfter(':')?.trim()?.let { locations += it }
                        } catch (_: SocketTimeoutException) {
                        }
                    }
                }
            } catch (_: Exception) {
            }
            val out = ArrayList<TvDevice>()
            for (loc in locations) {
                val info = describe(loc) ?: continue
                known[info.device.id] = info
                if (out.none { it.id == info.device.id }) out += info.device
            }
            return out
        }

        private fun describe(location: String): Info? = try {
            val xml = http(location, null, null) ?: return null
            if (MEDIA_RENDERER !in xml) return null
            val name = Xml.arg(xml, "friendlyName").ifEmpty { "DLNA oynatıcı" }
            val udn = Xml.arg(xml, "UDN").ifEmpty { location }
            val model = Xml.arg(xml, "modelName")
            val urlBase = Xml.arg(xml, "URLBase").ifEmpty { location }
            var av: String? = null
            var rc: String? = null
            Regex("<service>(.*?)</service>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { m ->
                val svc = m.groupValues[1]
                val type = Xml.arg(svc, "serviceType")
                val ctl = Xml.arg(svc, "controlURL")
                if (ctl.isEmpty()) return@forEach
                val abs = URL(URL(urlBase), ctl).toString()
                if ("AVTransport" in type) av = abs
                if ("RenderingControl" in type) rc = abs
            }
            val avUrl = av ?: return null
            Info(TvDevice(udn, name, TvType.DLNA, model.ifEmpty { "DLNA" }, URL(location).host), avUrl, rc)
        } catch (_: Exception) {
            null
        }

        private fun soap(url: String, service: String, action: String, args: List<Pair<String, String>>): String? {
            val body = buildString {
                append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
                append("<s:Body><u:").append(action).append(" xmlns:u=\"").append(service).append("\">")
                for ((k, v) in args) append('<').append(k).append('>').append(Xml.esc(v)).append("</").append(k).append('>')
                append("</u:").append(action).append("></s:Body></s:Envelope>")
            }
            return http(url, body, "\"$service#$action\"")
        }

        /** body null ise GET; aksi halde SOAP POST. Hata kodunda null. */
        private fun http(url: String, body: String?, soapAction: String?): String? {
            val c = URL(url).openConnection() as HttpURLConnection
            return try {
                c.connectTimeout = 3000
                c.readTimeout = 5000
                if (body != null) {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                    c.setRequestProperty("SOAPACTION", soapAction)
                    c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                if (c.responseCode !in 200..299) null else c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
            } catch (_: Exception) {
                null
            } finally {
                c.disconnect()
            }
        }

        fun hms(ms: Long): String {
            val t = ms / 1000
            return String.format(Locale.US, "%d:%02d:%02d", t / 3600, (t / 60) % 60, t % 60)
        }

        fun parseHms(s: String): Long? {
            val p = s.substringBefore('.').split(':')
            if (p.size != 3) return null
            val h = p[0].toLongOrNull() ?: return null
            val m = p[1].toLongOrNull() ?: return null
            val sec = p[2].toLongOrNull() ?: return null
            return ((h * 60 + m) * 60 + sec) * 1000
        }
    }
}
