package com.akis.app.dlna

import android.content.Context
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.Locale

/**
 * Küçük bir MKV (Matroska / WebM) okuyucu:
 *  - parça listesi: her ses / altyazı parçasının adı ("Türkçe Dublaj"), dili, varsayılan / zorunlu işareti
 *  - gömülü metin altyazıları (SRT, ASS/SSA, WebVTT) dosyadan çıkarıp SRT olarak verir
 * Dosyanın tamamı okunmaz: yalnızca blok başlıkları okunur, görüntü ve ses verisinin üzerinden atlanır.
 */
object Mkv {
    const val VIDEO = 1
    const val AUDIO = 2
    const val SUBTITLE = 0x11

    data class Track(
        val number: Int,
        val type: Int,
        val codec: String,
        /** İki harfli dil kodu ("tr", "en"); bilinmiyorsa boş. */
        val lang: String,
        val name: String,
        val isDefault: Boolean,
        val forced: Boolean,
    ) {
        /** Metin altyazısı mı (resim tabanlı PGS / VobSub değil). */
        val textSub: Boolean get() = type == SUBTITLE && codec.startsWith("S_TEXT/")
    }

    private class Cue(val startMs: Long, var endMs: Long, val text: String)

    private val headerCache = LruCache<String, List<Track>>(500)
    private val subCache = LruCache<String, Map<Int, String>>(12)
    private val locks = HashMap<String, Any>()

    fun isMkv(e: Entry) = e.ext == "mkv" || e.ext == "webm" || e.ext == "mka" || e.mime.contains("matroska")

    // ---------------- Okuma ----------------

    private class Reader(val ch: FileChannel) {
        val size: Long = ch.size()
        private val small = ByteBuffer.allocate(32)

        fun bytes(pos: Long, n: Int): ByteArray {
            val b = ByteBuffer.allocate(n)
            var p = pos
            while (b.hasRemaining()) {
                val r = ch.read(b, p)
                if (r <= 0) break
                p += r
            }
            return b.array().copyOf(b.position())
        }

        /** Element başlığı: (kimlik, gövde boyu -1 = bilinmiyor, başlık uzunluğu) */
        fun header(pos: Long): Triple<Long, Long, Int>? {
            small.clear()
            val r = ch.read(small, pos)
            if (r < 2) return null
            val a = small.array()
            val idLen = vlen(a[0].toInt() and 0xFF)
            if (idLen == 0 || idLen > 4 || idLen >= r) return null
            var id = 0L
            for (i in 0 until idLen) id = (id shl 8) or (a[i].toLong() and 0xFF)
            val sLen = vlen(a[idLen].toInt() and 0xFF)
            if (sLen == 0 || idLen + sLen > r) return null
            var size = (a[idLen].toLong() and 0xFF) and ((1L shl (8 - sLen)) - 1)
            var allOnes = size == (1L shl (8 - sLen)) - 1
            for (i in 1 until sLen) {
                val v = a[idLen + i].toLong() and 0xFF
                if (v != 0xFFL) allOnes = false
                size = (size shl 8) or v
            }
            return Triple(id, if (allOnes) -1 else size, idLen + sLen)
        }
    }

    private fun vlen(b: Int): Int {
        for (i in 0 until 8) if ((b shr (7 - i)) and 1 == 1) return i + 1
        return 0
    }

    private fun uint(b: ByteArray): Long {
        var v = 0L
        for (x in b) v = (v shl 8) or (x.toLong() and 0xFF)
        return v
    }

    private fun <T> open(ctx: Context, uri: Uri, block: (Reader) -> T): T? = try {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { block(Reader(it)) }
        }
    } catch (_: Exception) {
        null
    }

    private const val SEGMENT = 0x18538067L
    private const val INFO = 0x1549A966L
    private const val TIMECODE_SCALE = 0x2AD7B1L
    private const val TRACKS = 0x1654AE6BL
    private const val TRACK_ENTRY = 0xAEL
    private const val CLUSTER = 0x1F43B675L
    private const val CLUSTER_TC = 0xE7L
    private const val SIMPLE_BLOCK = 0xA3L
    private const val BLOCK_GROUP = 0xA0L
    private const val BLOCK = 0xA1L
    private const val BLOCK_DURATION = 0x9BL
    private val TOP_LEVEL = setOf(CLUSTER, 0x1C53BB6BL, 0x1254C367L, 0x1941A469L, 0x1043A770L, TRACKS, INFO, 0x114D9B74L)

    private class Header(val tracks: List<Track>, val segStart: Long, val segEnd: Long, val scale: Long, val firstCluster: Long)

    private fun parseHeader(r: Reader): Header? {
        var pos = 0L
        // EBML başlığını geç
        val ebml = r.header(pos) ?: return null
        if (ebml.first != 0x1A45DFA3L) return null
        pos += ebml.third + ebml.second
        val seg = r.header(pos) ?: return null
        if (seg.first != SEGMENT) return null
        val segStart = pos + seg.third
        val segEnd = if (seg.second < 0) r.size else minOf(r.size, segStart + seg.second)
        var p = segStart
        var scale = 1_000_000L
        var tracks: List<Track>? = null
        var firstCluster = -1L
        while (p < segEnd) {
            val h = r.header(p) ?: break
            val body = p + h.third
            when (h.first) {
                INFO -> scale = readInfo(r, body, body + h.second)
                TRACKS -> tracks = readTracks(r, body, body + h.second)
                CLUSTER -> {
                    if (firstCluster < 0) firstCluster = p
                    if (tracks != null) break
                }
            }
            if (h.second < 0) break
            p = body + h.second
        }
        return Header(tracks ?: return null, segStart, segEnd, scale, firstCluster)
    }

    private fun readInfo(r: Reader, from: Long, to: Long): Long {
        var p = from
        while (p < to) {
            val h = r.header(p) ?: break
            if (h.first == TIMECODE_SCALE) return uint(r.bytes(p + h.third, h.second.toInt()))
            p += h.third + h.second
        }
        return 1_000_000L
    }

    private val ISO3: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for (two in Locale.getISOLanguages()) {
            try { m[Locale(two).isO3Language] = two } catch (_: Exception) { }
        }
        // ISO 639-2/B kodları (MKV'de sık görülür)
        m["ger"] = "de"; m["fre"] = "fr"; m["dut"] = "nl"; m["chi"] = "zh"; m["gre"] = "el"; m["per"] = "fa"; m["cze"] = "cs"; m["rum"] = "ro"
        m
    }

    private fun normLang(code: String): String {
        val c = code.lowercase().substringBefore('-')
        return when {
            c.isEmpty() || c == "und" || c == "zxx" -> ""
            c.length == 2 -> c
            else -> ISO3[c] ?: c
        }
    }

    private fun readTracks(r: Reader, from: Long, to: Long): List<Track> {
        val out = ArrayList<Track>()
        var p = from
        while (p < to) {
            val h = r.header(p) ?: break
            if (h.first == TRACK_ENTRY && h.second in 1..1_000_000) {
                val data = r.bytes(p + h.third, h.second.toInt())
                var num = 0
                var type = 0
                var codec = ""
                var lang = "eng"
                var bcp = ""
                var name = ""
                var def = true
                var forced = false
                var q = 0
                while (q < data.size) {
                    val idLen = vlen(data[q].toInt() and 0xFF)
                    if (idLen == 0 || q + idLen >= data.size) break
                    var id = 0L
                    for (i in 0 until idLen) id = (id shl 8) or (data[q + i].toLong() and 0xFF)
                    val sLen = vlen(data[q + idLen].toInt() and 0xFF)
                    if (sLen == 0) break
                    var size = (data[q + idLen].toLong() and 0xFF) and ((1L shl (8 - sLen)) - 1)
                    for (i in 1 until sLen) size = (size shl 8) or (data[q + idLen + i].toLong() and 0xFF)
                    val b = q + idLen + sLen
                    val e = minOf(data.size.toLong(), b + size).toInt()
                    val v = data.copyOfRange(b, e)
                    when (id) {
                        0xD7L -> num = uint(v).toInt()
                        0x83L -> type = uint(v).toInt()
                        0x86L -> codec = String(v, Charsets.US_ASCII).trimEnd('\u0000')
                        0x22B59CL -> lang = String(v, Charsets.US_ASCII).trimEnd('\u0000')
                        0x22B59DL -> bcp = String(v, Charsets.US_ASCII).trimEnd('\u0000')
                        0x536EL -> name = String(v, Charsets.UTF_8).trimEnd('\u0000').trim()
                        0x88L -> def = uint(v) != 0L
                        0x55AAL -> forced = uint(v) != 0L
                    }
                    q = e
                }
                if (num > 0) out += Track(num, type, codec, normLang(bcp.ifEmpty { lang }), name, def, forced)
            }
            if (h.second < 0) break
            p += h.third + h.second
        }
        return out
    }

    private fun key(uri: Uri, ctx: Context): String {
        val size = try { ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0 } catch (_: Exception) { 0 }
        return "$uri|$size"
    }

    /** Parça listesi (yalnızca başlık okunur; hızlı). */
    fun tracks(ctx: Context, e: Entry): List<Track> {
        val uri = e.uri ?: return emptyList()
        if (!isMkv(e)) return emptyList()
        val k = "$uri|${e.size}"
        headerCache.get(k)?.let { return it }
        val t = open(ctx, uri) { parseHeader(it)?.tracks } ?: emptyList()
        headerCache.put(k, t)
        return t
    }

    // ---------------- Altyazı çıkarma ----------------

    /** Bir altyazı parçasını SRT metni olarak verir (ilk istekte dosya taranır, sonra önbellekten). */
    fun subtitle(ctx: Context, e: Entry, trackNumber: Int): String? {
        val uri = e.uri ?: return null
        val k = key(uri, ctx)
        subCache.get(k)?.let { return it[trackNumber] }
        val disk = diskFile(ctx, k, trackNumber)
        if (disk.exists()) return disk.readText()
        val lock = synchronized(locks) { locks.getOrPut(k) { Any() } }
        synchronized(lock) {
            subCache.get(k)?.let { return it[trackNumber] }
            if (disk.exists()) return disk.readText()
            val all = open(ctx, uri) { extract(it) } ?: return null
            subCache.put(k, all)
            for ((n, srt) in all) runCatching { diskFile(ctx, k, n).writeText(srt) }
            return all[trackNumber]
        }
    }

    private fun diskFile(ctx: Context, key: String, track: Int): File {
        val dir = File(ctx.cacheDir, "mkvsubs").apply { mkdirs() }
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
        return File(dir, "$h-$track.srt")
    }

    private fun extract(r: Reader): Map<Int, String> {
        val hd = parseHeader(r) ?: return emptyMap()
        val subs = hd.tracks.filter { it.textSub }.associateBy { it.number }
        if (subs.isEmpty() || hd.firstCluster < 0) return emptyMap()
        val cues = HashMap<Int, MutableList<Cue>>()
        val scaleMs = hd.scale / 1_000_000.0

        fun block(pos: Long, len: Long, clusterTc: Long, durationTc: Long) {
            // Blok başı: parça numarası (varint), 16 bit göreli zaman, bayraklar
            val head = r.bytes(pos, 8)
            if (head.isEmpty()) return
            val tl = vlen(head[0].toInt() and 0xFF)
            if (tl == 0 || tl + 3 > head.size) return
            var track = (head[0].toLong() and 0xFF) and ((1L shl (8 - tl)) - 1)
            for (i in 1 until tl) track = (track shl 8) or (head[i].toLong() and 0xFF)
            val tr = subs[track.toInt()] ?: return
            val rel = ((head[tl].toInt() shl 8) or (head[tl + 1].toInt() and 0xFF)).toShort().toLong()
            val flags = head[tl + 2].toInt() and 0xFF
            if ((flags and 0x06) != 0) return // altyazıda kafes (lacing) beklenmez
            val dataLen = (len - tl - 3).toInt()
            if (dataLen <= 0 || dataLen > 1_000_000) return
            val raw = String(r.bytes(pos + tl + 3, dataLen), Charsets.UTF_8).trimEnd('\u0000')
            val text = cueText(tr.codec, raw)
            if (text.isBlank()) return
            val start = ((clusterTc + rel) * scaleMs).toLong()
            val end = if (durationTc > 0) start + (durationTc * scaleMs).toLong() else -1
            cues.getOrPut(tr.number) { ArrayList() } += Cue(start, end, text)
        }

        var p = hd.firstCluster
        while (p < hd.segEnd) {
            val h = r.header(p) ?: break
            val body = p + h.third
            if (h.first != CLUSTER) {
                if (h.second < 0) break
                p = body + h.second
                continue
            }
            val end = if (h.second < 0) hd.segEnd else minOf(hd.segEnd, body + h.second)
            var q = body
            var tc = 0L
            while (q < end) {
                val c = r.header(q) ?: break
                if (h.second < 0 && c.first in TOP_LEVEL) break // boyutu bilinmeyen kümenin sonu
                val cb = q + c.third
                when (c.first) {
                    CLUSTER_TC -> tc = uint(r.bytes(cb, c.second.toInt()))
                    SIMPLE_BLOCK -> block(cb, c.second, tc, 0)
                    BLOCK_GROUP -> {
                        var g = cb
                        val ge = cb + c.second
                        var bPos = -1L
                        var bLen = 0L
                        var dur = 0L
                        while (g < ge) {
                            val gh = r.header(g) ?: break
                            if (gh.first == BLOCK) { bPos = g + gh.third; bLen = gh.second }
                            if (gh.first == BLOCK_DURATION) dur = uint(r.bytes(g + gh.third, gh.second.toInt()))
                            g += gh.third + gh.second
                        }
                        if (bPos >= 0) block(bPos, bLen, tc, dur)
                    }
                }
                if (c.second < 0) break
                q = cb + c.second
            }
            p = if (h.second < 0) q else end
        }
        val out = HashMap<Int, String>()
        for ((n, list) in cues) {
            list.sortBy { it.startMs }
            for (i in list.indices) {
                if (list[i].endMs < 0) list[i].endMs = minOf(list[i].startMs + 4000, list.getOrNull(i + 1)?.startMs ?: (list[i].startMs + 4000))
            }
            out[n] = toSrt(list)
        }
        return out
    }

    /** ASS/SSA blok verisi: ReadOrder,Layer,Style,Name,MarginL,MarginR,MarginV,Effect,Text */
    private fun cueText(codec: String, raw: String): String = when {
        codec == "S_TEXT/ASS" || codec == "S_TEXT/SSA" -> {
            var t = raw
            repeat(8) { t = t.substringAfter(',', t) }
            t.replace(Regex("\\{[^}]*\\}"), "").replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ").trim()
        }
        else -> raw.replace("\r\n", "\n").trim()
    }

    private fun toSrt(list: List<Cue>): String {
        fun ts(ms: Long) = String.format(Locale.US, "%02d:%02d:%02d,%03d", ms / 3_600_000, (ms / 60_000) % 60, (ms / 1000) % 60, ms % 1000)
        val sb = StringBuilder()
        list.forEachIndexed { i, c ->
            sb.append(i + 1).append('\n').append(ts(c.startMs)).append(" --> ").append(ts(c.endMs)).append('\n').append(c.text).append("\n\n")
        }
        return sb.toString()
    }
}
