package com.akis.app.dlna

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale
import com.akis.app.Lang
import com.akis.app.R

/** Bir videoya bağlı altyazı dosyası. */
data class Sub(val id: String, val lang: String, val ext: String) {
    val label: String get() = Subtitles.langName(lang)
}

/**
 * Altyazı eşleştirme ve dönüştürme:
 *  - film.srt, film.tr.srt, film.eng.forced.srt, film.Türkçe.ass … aynı videoya bağlanır
 *  - .srt / .vtt / .ass / .ssa / .sub (MicroDVD) okunur; TV'ye SRT, tarayıcıya ve Google TV'ye WebVTT verilir
 *  - Türkçe altyazılarda sık görülen Windows-1254 kodlaması UTF-8'e çevrilir
 */
object Subtitles {
    val EXT = setOf("srt", "vtt", "ass", "ssa", "sub")

    private val LANGS = mapOf(
        "tr" to "tr", "tur" to "tr", "turkish" to "tr", "türkçe" to "tr", "turkce" to "tr",
        "en" to "en", "eng" to "en", "english" to "en", "ingilizce" to "en", "i̇ngilizce" to "en",
        "de" to "de", "ger" to "de", "deu" to "de", "german" to "de", "almanca" to "de",
        "fr" to "fr", "fre" to "fr", "fra" to "fr", "french" to "fr", "fransızca" to "fr",
        "es" to "es", "spa" to "es", "spanish" to "es", "ispanyolca" to "es",
        "it" to "it", "ita" to "it", "italian" to "it",
        "ru" to "ru", "rus" to "ru", "russian" to "ru",
        "ar" to "ar", "ara" to "ar", "arabic" to "ar",
        "ja" to "ja", "jpn" to "ja", "japanese" to "ja",
        "ko" to "ko", "kor" to "ko", "korean" to "ko",
    )

    /** Ayarlar'daki "tercih edilen dil" seçenekleri. */
    val CHOICES = listOf("tr", "en", "de", "fr", "es")

    /** Dil kodunun seçili arayüz dilindeki adı ("tr" → "Turkish" / "Türkçe"). */
    fun langName(code: String): String {
        if (code.isEmpty()) return Lang.s(R.string.subtitle_generic)
        val name = if (code in LANGS.values) Locale.forLanguageTag(code).getDisplayLanguage(Lang.locale) else code
        return name.replaceFirstChar { it.titlecase(Lang.locale) }
    }

    /**
     * Altyazı adının (uzantısız, küçük harf) hangi videoya ait olduğunu ve dilini bulur.
     * @return (videonun küçük harfli adı, dil kodu) ya da null
     */
    fun match(subBase: String, videoBases: Collection<String>): Pair<String, String>? {
        var best: String? = null
        for (v in videoBases) {
            val ok = subBase == v || (subBase.startsWith(v) && subBase.length > v.length && subBase[v.length] in ".-_ ")
            if (ok && (best == null || v.length > best.length)) best = v
        }
        best ?: return null
        val rest = subBase.substring(best.length).trim('.', '-', '_', ' ')
        return best to langOf(rest)
    }

    private fun langOf(rest: String): String {
        if (rest.isEmpty()) return ""
        for (token in rest.split('.', '-', '_', ' ')) LANGS[token]?.let { return it }
        return rest.substringBefore('.').take(12)
    }

    /** Tercih edilen dil başa, dilsizler sonra, diğerleri en sona. */
    fun sort(list: List<Sub>, preferred: String): List<Sub> =
        list.sortedWith(compareBy({ if (it.lang == preferred) 0 else if (it.lang.isEmpty()) 1 else 2 }, { it.lang }))

    // ---------------- Okuma ----------------

    fun decode(bytes: ByteArray, preferredLang: String): String {
        var start = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) start = 3
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        val utf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8.decode(ByteBuffer.wrap(bytes, start, bytes.size - start)).toString()
        } catch (e: CharacterCodingException) {
            val cs = when (preferredLang) {
                "ru" -> "windows-1251"
                "ar" -> "windows-1256"
                "en", "de", "fr", "es", "it" -> "windows-1252"
                else -> "windows-1254" // Türkçe ve bilinmeyenler
            }
            String(bytes, Charset.forName(cs))
        }
    }

    private data class Cue(val start: Long, val end: Long, val text: String)

    private fun parse(text: String, ext: String): List<Cue> = when (ext) {
        "ass", "ssa" -> parseAss(text)
        "sub" -> parseMicroDvd(text)
        else -> parseSrtVtt(text)
    }

    private val TIME = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")

    private fun time(s: String): Long? {
        val m = TIME.find(s) ?: return null
        val (h, mi, se, ms) = m.destructured
        return ((h.ifEmpty { "0" }.toLong() * 60 + mi.toLong()) * 60 + se.toLong()) * 1000 + ms.padEnd(3, '0').toLong()
    }

    private fun parseSrtVtt(text: String): List<Cue> {
        val out = ArrayList<Cue>()
        val blocks = text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n\\s*\n"))
        for (b in blocks) {
            val lines = b.trim('\n').lines()
            val i = lines.indexOfFirst { "-->" in it }
            if (i < 0) continue
            val (a, z) = lines[i].split("-->", limit = 2)
            val start = time(a) ?: continue
            val end = time(z) ?: continue
            val body = lines.drop(i + 1).joinToString("\n").trim()
            if (body.isNotEmpty()) out += Cue(start, end, body)
        }
        return out
    }

    private fun parseAss(text: String): List<Cue> {
        val out = ArrayList<Cue>()
        var fields = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("Format:", true) && fields.size > 0) {
                val f = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                if ("text" in f) fields = f
                continue
            }
            if (!line.startsWith("Dialogue:", true)) continue
            val parts = line.substringAfter(':').split(',', limit = fields.size)
            if (parts.size < fields.size) continue
            val start = time(parts[fields.indexOf("start")].trim()) ?: continue
            val end = time(parts[fields.indexOf("end")].trim()) ?: continue
            val body = parts[fields.indexOf("text")]
                .replace(Regex("\\{[^}]*\\}"), "")
                .replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
                .trim()
            if (body.isNotEmpty()) out += Cue(start, end, body)
        }
        return out.sortedBy { it.start }
    }

    private fun parseMicroDvd(text: String): List<Cue> {
        val out = ArrayList<Cue>()
        val re = Regex("""^\{(\d+)\}\{(\d+)\}(.*)$""")
        var fps = 23.976
        for (raw in text.lines()) {
            val m = re.find(raw.trim()) ?: continue
            val (a, z, t) = m.destructured
            if (a == "1" && z == "1") { t.toDoubleOrNull()?.let { fps = it }; continue }
            val body = t.replace(Regex("\\{[^}]*\\}"), "").replace('|', '\n').trim()
            if (body.isNotEmpty()) out += Cue((a.toLong() * 1000 / fps).toLong(), (z.toLong() * 1000 / fps).toLong(), body)
        }
        return out
    }

    private fun stamp(ms: Long, sep: Char) = String.format(
        Locale.US, "%02d:%02d:%02d%c%03d", ms / 3_600_000, (ms / 60_000) % 60, (ms / 1000) % 60, sep, ms % 1000,
    )

    /** UTF-8 (BOM'lu) SRT — TV'ler için en uyumlu biçim. */
    fun toSrt(text: String, ext: String): String {
        if (ext == "srt") return text.replace("\r\n", "\n")
        val sb = StringBuilder()
        parse(text, ext).forEachIndexed { i, c ->
            sb.append(i + 1).append('\n').append(stamp(c.start, ',')).append(" --> ").append(stamp(c.end, ','))
                .append('\n').append(c.text).append("\n\n")
        }
        return sb.toString()
    }

    /** WebVTT — tarayıcılar ve Google Cast için. */
    fun toVtt(text: String, ext: String): String {
        if (ext == "vtt") return text
        val sb = StringBuilder("WEBVTT\n\n")
        for (c in parse(text, ext)) {
            sb.append(stamp(c.start, '.')).append(" --> ").append(stamp(c.end, '.')).append('\n')
                .append(c.text.replace(Regex("</?font[^>]*>"), "")).append("\n\n")
        }
        return sb.toString()
    }
}
