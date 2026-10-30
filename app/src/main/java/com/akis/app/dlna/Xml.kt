package com.akis.app.dlna

import java.net.URLEncoder
import com.akis.app.Lang
import com.akis.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Xml {
    fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) when (ch) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            '\'' -> sb.append("&apos;")
            else -> if (ch.code >= 0x20 || ch == '\n' || ch == '\t') sb.append(ch)
        }
        return sb.toString()
    }

    fun unesc(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun arg(xml: String, name: String): String {
        val m = Regex("<(?:\\w+:)?$name(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?$name>", RegexOption.DOT_MATCHES_ALL).find(xml)
        return m?.groupValues?.get(1)?.trim()?.let { unesc(it) } ?: ""
    }

    fun mediaUrl(base: String, e: Entry) = "$base/m/${enc(e.id)}/file.${e.ext.ifEmpty { "bin" }}"

    /** Altyazı adresi. smart=true ise /s/ üzerinden UTF-8'e ve istenen biçime (srt/vtt) çevrilerek verilir. */
    fun subUrl(base: String, sub: Sub, smart: Boolean, format: String = "srt") =
        if (smart) "$base/s/${enc(sub.id)}.$format" else "$base/m/${enc(sub.id)}/sub.srt"

    private const val FLAGS = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
    const val CONTENT_FEATURES = FLAGS

    private fun duration(ms: Long): String {
        val t = ms / 1000
        return String.format(Locale.US, "%d:%02d:%02d.%03d", t / 3600, (t / 60) % 60, t % 60, ms % 1000)
    }

    private fun iso(sec: Long) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(sec * 1000))

    fun device(name: String, uuid: String, base: String, iconVersion: String) = """<?xml version="1.0" encoding="utf-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <device>
    <deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>
    <friendlyName>${esc(name)}</friendlyName>
    <manufacturer>Akış</manufacturer>
    <modelName>Akış</modelName>
    <modelNumber>1.0</modelNumber>
    <modelDescription>${esc(Lang.s(R.string.model_desc))}</modelDescription>
    <UDN>uuid:$uuid</UDN>
    <dlna:X_DLNADOC>DMS-1.50</dlna:X_DLNADOC>
    <presentationURL>$base/</presentationURL>
    <iconList>
      <icon><mimetype>image/png</mimetype><width>48</width><height>48</height><depth>24</depth><url>/icon48.png?v=$iconVersion</url></icon>
      <icon><mimetype>image/png</mimetype><width>120</width><height>120</height><depth>24</depth><url>/icon.png?v=$iconVersion</url></icon>
    </iconList>
    <serviceList>
      <service>
        <serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>
        <serviceId>urn:upnp-org:serviceId:ContentDirectory</serviceId>
        <SCPDURL>/cd.xml</SCPDURL>
        <controlURL>/ctl/cd</controlURL>
        <eventSubURL>/evt/cd</eventSubURL>
      </service>
      <service>
        <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
        <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
        <SCPDURL>/cm.xml</SCPDURL>
        <controlURL>/ctl/cm</controlURL>
        <eventSubURL>/evt/cm</eventSubURL>
      </service>
    </serviceList>
  </device>
</root>"""

    fun didl(entries: List<Entry>, base: String, smartSubs: Boolean = false, searchable: Boolean = false): String = buildString {
        append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" ")
        append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
        append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" ")
        append("xmlns:dlna=\"urn:schemas-dlna-org:metadata-1-0/\" ")
        append("xmlns:sec=\"http://www.sec.co.kr/\">")
        for (e in entries) {
            if (e.kind == Kind.FOLDER) {
                append("<container id=\"").append(esc(e.id)).append("\" parentID=\"").append(esc(e.parentId))
                append("\" restricted=\"1\" searchable=\"").append(if (searchable && e.id == "0") 1 else 0).append('"')
                if (e.childCount >= 0) append(" childCount=\"").append(e.childCount).append('"')
                append("><dc:title>").append(esc(e.title)).append("</dc:title>")
                append("<upnp:class>object.container.storageFolder</upnp:class></container>")
                continue
            }
            val cls = when (e.kind) {
                Kind.VIDEO -> "object.item.videoItem"
                Kind.AUDIO -> "object.item.audioItem.musicTrack"
                Kind.IMAGE -> "object.item.imageItem.photo"
                else -> "object.item"
            }
            append("<item id=\"").append(esc(e.id)).append("\" parentID=\"").append(esc(e.parentId)).append("\" restricted=\"1\">")
            append("<dc:title>").append(esc(e.title)).append("</dc:title>")
            append("<upnp:class>").append(cls).append("</upnp:class>")
            if (e.dateSec > 0) append("<dc:date>").append(iso(e.dateSec)).append("</dc:date>")
            e.artist?.let { append("<upnp:artist>").append(esc(it)).append("</upnp:artist><dc:creator>").append(esc(it)).append("</dc:creator>") }
            e.album?.let { append("<upnp:album>").append(esc(it)).append("</upnp:album>") }
            append("<upnp:albumArtURI dlna:profileID=\"JPEG_TN\">").append(esc("$base/t/${enc(e.id)}.jpg")).append("</upnp:albumArtURI>")
            append("<res protocolInfo=\"http-get:*:").append(e.mime).append(':').append(FLAGS).append("\" size=\"").append(e.size).append('"')
            if (e.durationMs > 0) append(" duration=\"").append(duration(e.durationMs)).append('"')
            if (e.width > 0 && e.height > 0) append(" resolution=\"").append(e.width).append('x').append(e.height).append('"')
            append('>').append(esc(mediaUrl(base, e))).append("</res>")
            e.subtitles.forEachIndexed { i, sub ->
                val su = subUrl(base, sub, smartSubs)
                append("<res protocolInfo=\"http-get:*:text/srt:*\">").append(esc(su)).append("</res>")
                if (i == 0) append("<sec:CaptionInfoEx sec:type=\"srt\">").append(esc(su)).append("</sec:CaptionInfoEx>")
            }
            append("</item>")
        }
        append("</DIDL-Lite>")
    }

    fun soap(action: String, service: String, body: String) =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" +
            "<u:${action}Response xmlns:u=\"urn:schemas-upnp-org:service:$service:1\">$body</u:${action}Response>" +
            "</s:Body></s:Envelope>"

    const val FAULT = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><s:Fault>" +
        "<faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError " +
        "xmlns=\"urn:schemas-upnp-org:control-1-0\"><errorCode>401</errorCode><errorDescription>Invalid Action" +
        "</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"

    private fun arg(n: String, dir: String, v: String) =
        "<argument><name>$n</name><direction>$dir</direction><relatedStateVariable>$v</relatedStateVariable></argument>"

    private fun sv(n: String, t: String, ev: Boolean = false) =
        "<stateVariable sendEvents=\"${if (ev) "yes" else "no"}\"><name>$n</name><dataType>$t</dataType></stateVariable>"

    /** ContentDirectory tanımı; arama açıksa Search eylemi de listelenir. */
    fun cdScpd(search: Boolean): String = if (!search) CD_SCPD else CD_SCPD.replace(
        "<action><name>GetSystemUpdateID</name>",
        "<action><name>Search</name><argumentList>" +
            arg("ContainerID", "in", "A_ARG_TYPE_ObjectID") + arg("SearchCriteria", "in", "A_ARG_TYPE_SearchCriteria") +
            arg("Filter", "in", "A_ARG_TYPE_Filter") + arg("StartingIndex", "in", "A_ARG_TYPE_Index") +
            arg("RequestedCount", "in", "A_ARG_TYPE_Count") + arg("SortCriteria", "in", "A_ARG_TYPE_SortCriteria") +
            arg("Result", "out", "A_ARG_TYPE_Result") + arg("NumberReturned", "out", "A_ARG_TYPE_Count") +
            arg("TotalMatches", "out", "A_ARG_TYPE_Count") + arg("UpdateID", "out", "A_ARG_TYPE_UpdateID") +
            "</argumentList></action><action><name>GetSystemUpdateID</name>",
    ).replace("</serviceStateTable>", sv("A_ARG_TYPE_SearchCriteria", "string") + "</serviceStateTable>")

    private val CD_SCPD = "<?xml version=\"1.0\" encoding=\"utf-8\"?><scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">" +
        "<specVersion><major>1</major><minor>0</minor></specVersion><actionList>" +
        "<action><name>Browse</name><argumentList>" +
        arg("ObjectID", "in", "A_ARG_TYPE_ObjectID") + arg("BrowseFlag", "in", "A_ARG_TYPE_BrowseFlag") +
        arg("Filter", "in", "A_ARG_TYPE_Filter") + arg("StartingIndex", "in", "A_ARG_TYPE_Index") +
        arg("RequestedCount", "in", "A_ARG_TYPE_Count") + arg("SortCriteria", "in", "A_ARG_TYPE_SortCriteria") +
        arg("Result", "out", "A_ARG_TYPE_Result") + arg("NumberReturned", "out", "A_ARG_TYPE_Count") +
        arg("TotalMatches", "out", "A_ARG_TYPE_Count") + arg("UpdateID", "out", "A_ARG_TYPE_UpdateID") +
        "</argumentList></action>" +
        "<action><name>GetSystemUpdateID</name><argumentList>" + arg("Id", "out", "SystemUpdateID") + "</argumentList></action>" +
        "<action><name>GetSearchCapabilities</name><argumentList>" + arg("SearchCaps", "out", "SearchCapabilities") + "</argumentList></action>" +
        "<action><name>GetSortCapabilities</name><argumentList>" + arg("SortCaps", "out", "SortCapabilities") + "</argumentList></action>" +
        "</actionList><serviceStateTable>" +
        sv("A_ARG_TYPE_ObjectID", "string") + sv("A_ARG_TYPE_Result", "string") + sv("A_ARG_TYPE_BrowseFlag", "string") +
        sv("A_ARG_TYPE_Filter", "string") + sv("A_ARG_TYPE_SortCriteria", "string") + sv("A_ARG_TYPE_Index", "ui4") +
        sv("A_ARG_TYPE_Count", "ui4") + sv("A_ARG_TYPE_UpdateID", "ui4") + sv("SystemUpdateID", "ui4", true) +
        sv("SearchCapabilities", "string") + sv("SortCapabilities", "string") +
        "</serviceStateTable></scpd>"

    val CM_SCPD = "<?xml version=\"1.0\" encoding=\"utf-8\"?><scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">" +
        "<specVersion><major>1</major><minor>0</minor></specVersion><actionList>" +
        "<action><name>GetProtocolInfo</name><argumentList>" + arg("Source", "out", "SourceProtocolInfo") +
        arg("Sink", "out", "SinkProtocolInfo") + "</argumentList></action>" +
        "<action><name>GetCurrentConnectionIDs</name><argumentList>" + arg("ConnectionIDs", "out", "CurrentConnectionIDs") +
        "</argumentList></action></actionList><serviceStateTable>" +
        sv("SourceProtocolInfo", "string", true) + sv("SinkProtocolInfo", "string", true) + sv("CurrentConnectionIDs", "string", true) +
        "</serviceStateTable></scpd>"
}
