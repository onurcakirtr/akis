package com.akis.app.dlna

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.math.min

/** Web oynatıcının bilmesi gerekenler: hangi parçalar var, tarayıcıya hangi codec adıyla gidecek. */
data class TrackInfo(
    val index: Int,
    val video: Boolean,
    val mime: String,
    /** Tarayıcı codec adı ("avc1.640028", "mp4a.40.2"); boşsa doğrudan gönderilemez. */
    val codec: String,
    val lang: String,
    val channels: Int,
    val sampleRate: Int,
    val width: Int,
    val height: Int,
    /** Ses tarayıcıya gitmeden önce telefonda AAC'ye çevrilebilir mi. */
    val canTranscode: Boolean,
)

data class Probe(val durationMs: Long, val tracks: List<TrackInfo>)

/**
 * Akış oynatıcısının telefon tarafı: MKV / MP4 / WebM / TS dosyasını Android'in MediaExtractor'ı ile açar,
 * tarayıcının MediaSource'una beslenebilecek parçalı MP4'e (fMP4) çevirip akıtır.
 * Görüntü olduğu gibi aktarılır; tarayıcının çözemediği ses (AC3, DTS…) anında AAC'ye çevrilir.
 */
object Remux {
    private const val VIDEO_TS = 90_000
    private const val V_ID = 1
    private const val A_ID = 2

    private val AAC_RATES = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)

    // ---------------- Analiz ----------------

    fun probe(ctx: Context, uri: Uri): Probe {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            var dur = 0L
            val list = ArrayList<TrackInfo>()
            val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (f.containsKey(MediaFormat.KEY_DURATION)) dur = maxOf(dur, f.getLong(MediaFormat.KEY_DURATION) / 1000)
                val video = mime.startsWith("video/")
                if (!video && !mime.startsWith("audio/")) continue
                val codec = runCatching { if (video) videoConfig(f)?.codec else audioPassthrough(f)?.codec }.getOrNull() ?: ""
                val canTx = !video && f.containsKey(MediaFormat.KEY_SAMPLE_RATE) &&
                    f.getInteger(MediaFormat.KEY_SAMPLE_RATE) in AAC_RATES &&
                    runCatching { codecs.findDecoderForFormat(f) != null }.getOrDefault(false)
                list += TrackInfo(
                    index = i, video = video, mime = mime, codec = codec,
                    lang = f.getString(MediaFormat.KEY_LANGUAGE)?.takeIf { it != "und" } ?: "",
                    channels = f.optInt(MediaFormat.KEY_CHANNEL_COUNT),
                    sampleRate = f.optInt(MediaFormat.KEY_SAMPLE_RATE),
                    width = f.optInt(MediaFormat.KEY_WIDTH),
                    height = f.optInt(MediaFormat.KEY_HEIGHT),
                    canTranscode = canTx,
                )
            }
            return Probe(dur, list)
        } finally {
            ex.release()
        }
    }

    private fun MediaFormat.optInt(key: String) = if (containsKey(key)) getInteger(key) else 0

    // ---------------- Codec yapılandırmaları ----------------

    private class Config(val codec: String, val entry: String, val box: ByteArray, val annexB: Boolean)

    private fun csd(f: MediaFormat, n: Int): ByteArray? = f.getByteBuffer("csd-$n")?.let { b ->
        val d = b.duplicate()
        d.rewind()
        ByteArray(d.remaining()).also { d.get(it) }
    }

    /** Annex B (00 00 01 / 00 00 00 01 başlangıç kodlu) veriyi NAL birimlerine ayırır. */
    private fun splitNals(b: ByteArray, len: Int = b.size): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var i = 0
        var start = -1
        while (i + 2 < len) {
            if (b[i].toInt() == 0 && b[i + 1].toInt() == 0 && b[i + 2].toInt() == 1) {
                if (start >= 0) {
                    var end = i
                    while (end > start && b[end - 1].toInt() == 0) end--
                    if (end > start) out += b.copyOfRange(start, end)
                }
                i += 3
                start = i
            } else {
                i++
            }
        }
        if (start in 0 until len) out += b.copyOfRange(start, len)
        return out
    }

    private fun isAnnexB(b: ByteArray, len: Int) =
        len > 4 && b[0].toInt() == 0 && b[1].toInt() == 0 && (b[2].toInt() == 1 || (b[2].toInt() == 0 && b[3].toInt() == 1))

    /** H.264/H.265 NAL'larından emülasyon önleme baytlarını (00 00 03) atar. */
    private fun rbsp(nal: ByteArray, from: Int): ByteArray {
        val o = ByteArrayOutputStream()
        var zeros = 0
        for (k in from until nal.size) {
            val v = nal[k].toInt() and 0xFF
            if (zeros >= 2 && v == 3) {
                zeros = 0
                continue
            }
            o.write(v)
            zeros = if (v == 0) zeros + 1 else 0
        }
        return o.toByteArray()
    }

    private fun videoConfig(f: MediaFormat): Config? {
        val mime = f.getString(MediaFormat.KEY_MIME) ?: return null
        return when (mime) {
            MediaFormat.MIMETYPE_VIDEO_AVC -> avcConfig(f)
            MediaFormat.MIMETYPE_VIDEO_HEVC -> hevcConfig(f)
            MediaFormat.MIMETYPE_VIDEO_VP9 -> {
                val box = full("vpcC", 1, 0, byteArrayOf(0, 41, 0x80.toByte(), 1, 1, 1, 0, 0))
                Config("vp09.00.41.08", "vp09", box, false)
            }
            MediaFormat.MIMETYPE_VIDEO_AV1 -> {
                val c = csd(f, 0) ?: return null
                if (c.isEmpty() || (c[0].toInt() and 0x80) == 0) return null
                val profile = (c[1].toInt() and 0xFF) shr 5
                val level = c[1].toInt() and 0x1F
                val tier = if ((c[2].toInt() and 0x80) != 0) "H" else "M"
                val high = (c[2].toInt() and 0x40) != 0
                val twelve = (c[2].toInt() and 0x20) != 0
                val depth = if (high) (if (twelve) 12 else 10) else 8
                Config("av01.$profile.%02d$tier.%02d".format(level, depth), "av01", box("av1C", c), false)
            }
            else -> null
        }
    }

    private fun avcConfig(f: MediaFormat): Config? {
        val c0 = csd(f, 0) ?: return null
        if (c0.isNotEmpty() && c0[0].toInt() == 1 && c0.size > 4) {
            // Zaten avcC
            return Config("avc1.%02X%02X%02X".format(c0[1], c0[2], c0[3]), "avc1", box("avcC", c0), true)
        }
        val nals = splitNals(c0) + (csd(f, 1)?.let { splitNals(it) } ?: emptyList())
        val sps = nals.firstOrNull { (it[0].toInt() and 0x1F) == 7 } ?: return null
        val pps = nals.filter { (it[0].toInt() and 0x1F) == 8 }
        val o = ByteArrayOutputStream()
        o.write(1); o.write(sps[1].toInt()); o.write(sps[2].toInt()); o.write(sps[3].toInt())
        o.write(0xFF); o.write(0xE1)
        o.write(sps.size shr 8); o.write(sps.size and 0xFF); o.write(sps)
        o.write(pps.size)
        for (p in pps) { o.write(p.size shr 8); o.write(p.size and 0xFF); o.write(p) }
        val profile = sps[1].toInt() and 0xFF
        if (profile in intArrayOf(100, 110, 122, 244)) {
            // High profil eki: chroma 4:2:0, 8 bit, SPS uzantısı yok
            o.write(0xFD); o.write(0xF8); o.write(0xF8); o.write(0)
        }
        return Config("avc1.%02X%02X%02X".format(sps[1], sps[2], sps[3]), "avc1", box("avcC", o.toByteArray()), true)
    }

    private fun hevcConfig(f: MediaFormat): Config? {
        val c0 = csd(f, 0) ?: return null
        if (c0.size > 22 && c0[0].toInt() == 1) {
            val ptl = c0.copyOfRange(1, 13)
            return Config(hevcCodec(ptl), "hvc1", box("hvcC", c0), true)
        }
        val nals = splitNals(c0)
        fun type(n: ByteArray) = (n[0].toInt() shr 1) and 0x3F
        val vps = nals.filter { type(it) == 32 }
        val sps = nals.filter { type(it) == 33 }
        val pps = nals.filter { type(it) == 34 }
        val s = sps.firstOrNull() ?: return null
        val r = rbsp(s, 2)
        if (r.size < 13) return null
        val maxSubLayers = ((r[0].toInt() shr 1) and 0x07) + 1
        val nesting = r[0].toInt() and 1
        val ptl = r.copyOfRange(1, 13) // profil(1) + uyumluluk(4) + kısıtlar(6) + seviye(1)
        val profileIdc = ptl[0].toInt() and 0x1F
        val depth = if (profileIdc == 2) 2 else 0 // Main10 → 10 bit
        val o = ByteArrayOutputStream()
        o.write(1)
        o.write(ptl, 0, 12)
        o.write(0xF0); o.write(0x00) // min_spatial_segmentation
        o.write(0xFC) // parallelismType
        o.write(0xFD) // chroma 4:2:0
        o.write(0xF8 or depth); o.write(0xF8 or depth)
        o.write(0); o.write(0) // avgFrameRate
        o.write((maxSubLayers shl 3) or (nesting shl 2) or 3)
        val arrays = listOf(32 to vps, 33 to sps, 34 to pps).filter { it.second.isNotEmpty() }
        o.write(arrays.size)
        for ((t, list) in arrays) {
            o.write(0x80 or t)
            o.write(list.size shr 8); o.write(list.size and 0xFF)
            for (n in list) { o.write(n.size shr 8); o.write(n.size and 0xFF); o.write(n) }
        }
        return Config(hevcCodec(ptl), "hvc1", box("hvcC", o.toByteArray()), true)
    }

    /** hvc1.<profil>.<uyumluluk>.<katman><seviye>.<kısıtlar> */
    private fun hevcCodec(ptl: ByteArray): String {
        val space = (ptl[0].toInt() shr 6) and 3
        val tier = (ptl[0].toInt() shr 5) and 1
        val profile = ptl[0].toInt() and 0x1F
        var compat = 0L
        for (k in 0 until 4) compat = (compat shl 8) or (ptl[1 + k].toLong() and 0xFF)
        var rev = 0L
        for (k in 0 until 32) if ((compat shr k) and 1L == 1L) rev = rev or (1L shl (31 - k))
        val cons = (0 until 6).map { ptl[5 + it].toInt() and 0xFF }.dropLastWhile { it == 0 }
        val prefix = when (space) { 1 -> "A"; 2 -> "B"; 3 -> "C"; else -> "" }
        val level = ptl[11].toInt() and 0xFF
        val sb = StringBuilder("hvc1.").append(prefix).append(profile).append('.').append(java.lang.Long.toHexString(rev).uppercase())
            .append('.').append(if (tier == 1) 'H' else 'L').append(level)
        for (c in cons) sb.append('.').append("%02X".format(c))
        return sb.toString()
    }

    private class AudioConfig(val codec: String, val entry: String, val box: ByteArray, val rate: Int, val channels: Int)

    /** Dönüştürmeden gönderilebilen sesler: AAC ve Opus. */
    private fun audioPassthrough(f: MediaFormat): AudioConfig? {
        val mime = f.getString(MediaFormat.KEY_MIME) ?: return null
        val rate = f.optInt(MediaFormat.KEY_SAMPLE_RATE)
        val ch = f.optInt(MediaFormat.KEY_CHANNEL_COUNT)
        return when (mime) {
            MediaFormat.MIMETYPE_AUDIO_AAC -> {
                val asc = csd(f, 0) ?: aacAsc(rate, ch) ?: return null
                val aot = (asc[0].toInt() and 0xFF) shr 3
                AudioConfig("mp4a.40.$aot", "mp4a", esds(asc), rate, ch)
            }
            MediaFormat.MIMETYPE_AUDIO_OPUS -> {
                val head = csd(f, 0) ?: return null
                if (head.size < 19) return null
                val o = ByteArrayOutputStream()
                o.write(0) // sürüm
                o.write(head[9].toInt()) // kanal
                o.write(head[11].toInt()); o.write(head[10].toInt()) // pre-skip (LE → BE)
                o.write(head[15].toInt()); o.write(head[14].toInt()); o.write(head[13].toInt()); o.write(head[12].toInt())
                o.write(head[17].toInt()); o.write(head[16].toInt()) // kazanç
                o.write(head[18].toInt()) // eşleme ailesi
                if (head[18].toInt() != 0 && head.size > 19) o.write(head, 19, head.size - 19)
                AudioConfig("opus", "Opus", box("dOps", o.toByteArray()), 48000, head[9].toInt())
            }
            else -> null
        }
    }

    /** AAC-LC AudioSpecificConfig (2 bayt). */
    private fun aacAsc(rate: Int, ch: Int): ByteArray? {
        val idx = AAC_RATES.indexOf(rate)
        if (idx < 0 || ch !in 1..7) return null
        val v = (2 shl 11) or (idx shl 7) or (ch shl 3)
        return byteArrayOf((v shr 8).toByte(), v.toByte())
    }

    // ---------------- MP4 kutuları ----------------

    private fun u32(o: ByteArrayOutputStream, v: Long) {
        o.write((v shr 24).toInt() and 0xFF); o.write((v shr 16).toInt() and 0xFF); o.write((v shr 8).toInt() and 0xFF); o.write(v.toInt() and 0xFF)
    }

    private fun u16(o: ByteArrayOutputStream, v: Int) { o.write((v shr 8) and 0xFF); o.write(v and 0xFF) }

    private fun u64(o: ByteArrayOutputStream, v: Long) { u32(o, v ushr 32); u32(o, v and 0xFFFFFFFFL) }

    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        val size = 8 + parts.sumOf { it.size }
        val o = ByteArrayOutputStream(size)
        u32(o, size.toLong())
        o.write(type.toByteArray(Charsets.ISO_8859_1))
        for (p in parts) o.write(p)
        return o.toByteArray()
    }

    private fun full(type: String, version: Int, flags: Int, vararg parts: ByteArray): ByteArray {
        val h = byteArrayOf(version.toByte(), (flags shr 16).toByte(), (flags shr 8).toByte(), flags.toByte())
        return box(type, h, *parts)
    }

    private fun bytes(block: ByteArrayOutputStream.() -> Unit) = ByteArrayOutputStream().apply(block).toByteArray()

    private val MATRIX = bytes {
        for (v in longArrayOf(0x00010000, 0, 0, 0, 0x00010000, 0, 0, 0, 0x40000000)) u32(this, v)
    }

    private fun esds(asc: ByteArray): ByteArray {
        fun desc(tag: Int, body: ByteArray) = bytes { write(tag); write(body.size); write(body) }
        val dsi = desc(5, asc)
        val dcd = desc(4, bytes {
            write(0x40); write(0x15); write(0); write(0); write(0)
            u32(this, 0); u32(this, 0)
            write(dsi)
        })
        val es = desc(3, bytes { u16(this, 1); write(0); write(dcd); write(desc(6, byteArrayOf(2))) })
        return full("esds", 0, 0, es)
    }

    private fun trak(id: Int, video: Boolean, timescale: Int, entry: ByteArray, w: Int, h: Int): ByteArray {
        val tkhd = full("tkhd", 0, 3, bytes {
            u32(this, 0); u32(this, 0); u32(this, id.toLong()); u32(this, 0); u32(this, 0)
            u32(this, 0); u32(this, 0); u16(this, 0); u16(this, 0)
            u16(this, if (video) 0 else 0x0100); u16(this, 0)
            write(MATRIX)
            u32(this, (w.toLong() shl 16)); u32(this, (h.toLong() shl 16))
        })
        val mdhd = full("mdhd", 0, 0, bytes { u32(this, 0); u32(this, 0); u32(this, timescale.toLong()); u32(this, 0); u16(this, 0x55C4); u16(this, 0) })
        val hdlr = full("hdlr", 0, 0, bytes {
            u32(this, 0); write((if (video) "vide" else "soun").toByteArray()); u32(this, 0); u32(this, 0); u32(this, 0)
            write("Akis".toByteArray()); write(0)
        })
        val xmhd = if (video) full("vmhd", 0, 1, ByteArray(8)) else full("smhd", 0, 0, ByteArray(4))
        val dinf = box("dinf", full("dref", 0, 0, bytes { u32(this, 1) }, full("url ", 0, 1)))
        val stbl = box(
            "stbl",
            full("stsd", 0, 0, bytes { u32(this, 1) }, entry),
            full("stts", 0, 0, bytes { u32(this, 0) }),
            full("stsc", 0, 0, bytes { u32(this, 0) }),
            full("stsz", 0, 0, bytes { u32(this, 0); u32(this, 0) }),
            full("stco", 0, 0, bytes { u32(this, 0) }),
        )
        return box("trak", tkhd, box("mdia", mdhd, hdlr, box("minf", xmhd, dinf, stbl)))
    }

    private fun videoEntry(c: Config, w: Int, h: Int) = box(c.entry, bytes {
        write(ByteArray(6)); u16(this, 1)
        u16(this, 0); u16(this, 0); write(ByteArray(12))
        u16(this, w); u16(this, h)
        u32(this, 0x00480000); u32(this, 0x00480000); u32(this, 0)
        u16(this, 1); write(ByteArray(32)); u16(this, 0x18); u16(this, 0xFFFF)
    }, c.box)

    private fun audioEntry(c: AudioConfig) = box(c.entry, bytes {
        write(ByteArray(6)); u16(this, 1)
        u32(this, 0); u32(this, 0)
        u16(this, c.channels); u16(this, 16); u16(this, 0); u16(this, 0)
        u32(this, (min(c.rate, 65535).toLong() shl 16))
    }, c.box)

    private fun init(v: Config?, vw: Int, vh: Int, a: AudioConfig?): ByteArray {
        val ftyp = box("ftyp", "iso5".toByteArray(), bytes { u32(this, 0x200) }, "iso5".toByteArray(), "iso6".toByteArray(), "mp41".toByteArray())
        val mvhd = full("mvhd", 0, 0, bytes {
            u32(this, 0); u32(this, 0); u32(this, 1000); u32(this, 0)
            u32(this, 0x00010000); u16(this, 0x0100); u16(this, 0); u32(this, 0); u32(this, 0)
            write(MATRIX); write(ByteArray(24)); u32(this, 3)
        })
        val traks = ArrayList<ByteArray>()
        val trex = ArrayList<ByteArray>()
        if (v != null) {
            traks += trak(V_ID, true, VIDEO_TS, videoEntry(v, vw, vh), vw, vh)
            trex += full("trex", 0, 0, bytes { u32(this, V_ID.toLong()); u32(this, 1); u32(this, 0); u32(this, 0); u32(this, 0) })
        }
        if (a != null) {
            traks += trak(A_ID, false, a.rate, audioEntry(a), 0, 0)
            trex += full("trex", 0, 0, bytes { u32(this, A_ID.toLong()); u32(this, 1); u32(this, 0); u32(this, 0); u32(this, 0) })
        }
        return ftyp + box("moov", mvhd, *traks.toTypedArray(), box("mvex", *trex.toTypedArray()))
    }

    private class Sample(val data: ByteArray, val ptsUs: Long, val key: Boolean)

    private var sequence = 0

    /** Bir parçanın (moof + mdat) baytları. dts/cto zaman ölçeğinde. */
    private fun fragment(trackId: Int, baseDts: Long, durs: LongArray, sizes: IntArray, keys: BooleanArray, ctos: LongArray, data: ByteArray): ByteArray {
        val n = sizes.size
        val seq = synchronized(this) { ++sequence }
        fun moof(offset: Int): ByteArray {
            val trun = full("trun", 1, 0x000F01, bytes {
                u32(this, n.toLong()); u32(this, offset.toLong())
                for (k in 0 until n) {
                    u32(this, durs[k]); u32(this, sizes[k].toLong())
                    u32(this, if (keys[k]) 0x02000000 else 0x01010000)
                    u32(this, ctos[k] and 0xFFFFFFFFL)
                }
            })
            val traf = box(
                "traf",
                full("tfhd", 0, 0x020000, bytes { u32(this, trackId.toLong()) }),
                full("tfdt", 1, 0, bytes { u64(this, baseDts) }),
                trun,
            )
            return box("moof", full("mfhd", 0, 0, bytes { u32(this, seq.toLong()) }), traf)
        }
        val size = moof(0).size
        return moof(size + 8) + box("mdat", data)
    }

    // ---------------- Akış ----------------

    /**
     * startUs'tan önceki ilk anahtar kareden başlayarak fMP4 akıtır. İstemci bağlantıyı kesince IOException ile biter.
     * @param audioTrack -1 ise ses yok
     * @param forceTranscode ses (AAC/Opus olsa bile) AAC'ye çevrilsin
     */
    fun stream(ctx: Context, uri: Uri, startUs: Long, audioTrack: Int, forceTranscode: Boolean, out: OutputStream) {
        val ex = MediaExtractor()
        var tx: AacTranscoder? = null
        try {
            ex.setDataSource(ctx, uri, null)
            var vIdx = -1
            var vCfg: Config? = null
            var vw = 0
            var vh = 0
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    vCfg = videoConfig(f) ?: continue
                    vIdx = i
                    vw = f.optInt(MediaFormat.KEY_WIDTH)
                    vh = f.optInt(MediaFormat.KEY_HEIGHT)
                    break
                }
            }
            var aCfg: AudioConfig? = null
            if (audioTrack in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(audioTrack)
                aCfg = if (!forceTranscode) audioPassthrough(f) else null
                if (aCfg == null) {
                    tx = runCatching { AacTranscoder(f) }.getOrNull()
                    tx?.let { t -> aCfg = AudioConfig("mp4a.40.2", "mp4a", esds(t.asc), t.rate, t.channels) }
                }
            }
            val audio = aCfg
            val aIdx = if (audio != null) audioTrack else -1
            if (vIdx < 0 && aIdx < 0) return
            if (vIdx >= 0) ex.selectTrack(vIdx)
            if (aIdx >= 0) ex.selectTrack(aIdx)
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            out.write(init(vCfg, vw, vh, audio))
            out.flush()

            val vq = ArrayList<Sample>()
            val aq = ArrayList<Sample>()
            var lastVDur = (VIDEO_TS / 30).toLong()
            val aRate = audio?.rate ?: 48000
            var lastADur = 1024L
            var nextAudioStartUs = -1L

            fun flushVideo() {
                if (vq.isEmpty()) return
                val pts = vq.map { it.ptsUs * VIDEO_TS / 1_000_000 }
                val dts = pts.sorted()
                val n = vq.size
                val durs = LongArray(n) { k -> if (k + 1 < n) maxOf(1, dts[k + 1] - dts[k]) else lastVDur }
                if (n > 1) lastVDur = maxOf(1, (dts[n - 1] - dts[0]) / (n - 1))
                val data = ByteArrayOutputStream()
                val sizes = IntArray(n)
                for (k in 0 until n) { sizes[k] = vq[k].data.size; data.write(vq[k].data) }
                out.write(fragment(V_ID, dts[0], durs, sizes, BooleanArray(n) { vq[it].key }, LongArray(n) { pts[it] - dts[it] }, data.toByteArray()))
                vq.clear()
            }

            fun flushAudio() {
                if (aq.isEmpty()) return
                val n = aq.size
                val ts = aq.map { it.ptsUs * aRate / 1_000_000 }
                val durs = LongArray(n) { k -> if (k + 1 < n) maxOf(1, ts[k + 1] - ts[k]) else lastADur }
                if (n > 1) lastADur = maxOf(1, (ts[n - 1] - ts[0]) / (n - 1))
                val data = ByteArrayOutputStream()
                val sizes = IntArray(n)
                for (k in 0 until n) { sizes[k] = aq[k].data.size; data.write(aq[k].data) }
                out.write(fragment(A_ID, ts[0], durs, sizes, BooleanArray(n) { true }, LongArray(n), data.toByteArray()))
                aq.clear()
            }

            fun addAudio(bytes: ByteArray, ptsUs: Long) {
                if (nextAudioStartUs < 0) nextAudioStartUs = ptsUs
                aq += Sample(bytes, ptsUs, true)
                if (vIdx < 0 && ptsUs - aq.first().ptsUs >= 1_000_000) flushAudio()
            }

            val buf = ByteBuffer.allocate(8 * 1024 * 1024)
            while (true) {
                val track = ex.sampleTrackIndex
                if (track < 0) break
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) break
                val pts = ex.sampleTime
                val key = (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                val raw = ByteArray(size)
                buf.position(0)
                buf.get(raw, 0, size)
                if (track == vIdx) {
                    val cfg = vCfg!!
                    if (key && vq.isNotEmpty() && pts - vq.first().ptsUs >= 900_000) {
                        flushVideo()
                        flushAudio()
                        out.flush()
                    }
                    if (vq.isEmpty() && !key) {
                        // Anahtar kare gelmeden kare gönderme
                    } else {
                        vq += Sample(if (cfg.annexB && isAnnexB(raw, size)) toLengthPrefixed(raw, size) else raw, pts, key)
                    }
                } else if (track == aIdx) {
                    val t = tx
                    if (t == null) addAudio(raw, pts) else {
                        t.feed(raw, size, pts)
                        for ((b, p) in t.take()) addAudio(b, p)
                    }
                }
                ex.advance()
            }
            tx?.let { t ->
                t.finish()
                for ((b, p) in t.take()) addAudio(b, p)
            }
            flushVideo()
            flushAudio()
            out.flush()
        } finally {
            tx?.release()
            ex.release()
        }
    }

    /** Annex B → 4 bayt uzunluk önekli NAL'lar (MP4 biçimi). */
    private fun toLengthPrefixed(b: ByteArray, len: Int): ByteArray {
        val o = ByteArrayOutputStream(len + 16)
        for (n in splitNals(b, len)) {
            u32(o, n.size.toLong())
            o.write(n)
        }
        return o.toByteArray()
    }

    // ---------------- Ses çevirme (AC3, DTS, MP3, FLAC… → AAC) ----------------

    private class AacTranscoder(src: MediaFormat) {
        val rate: Int = src.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        private val srcCh = src.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val channels = min(2, srcCh)
        val asc: ByteArray = aacAsc(rate, channels) ?: throw IllegalArgumentException("Desteklenmeyen örnekleme hızı: $rate")
        private val dec = MediaCodec.createDecoderByType(src.getString(MediaFormat.KEY_MIME)!!)
        private val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        private var decCh = srcCh
        private val info = MediaCodec.BufferInfo()
        private val ready = ArrayList<Pair<ByteArray, Long>>()
        private var encDone = false

        init {
            try {
                dec.configure(src, null, null, 0)
                dec.start()
                val f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels)
                f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                f.setInteger(MediaFormat.KEY_BIT_RATE, if (channels == 1) 96_000 else 160_000)
                f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
                enc.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                enc.start()
            } catch (e: Exception) {
                release()
                throw e
            }
        }

        fun feed(data: ByteArray, size: Int, ptsUs: Long) {
            while (true) {
                val i = dec.dequeueInputBuffer(5_000)
                if (i >= 0) {
                    val b = dec.getInputBuffer(i)!!
                    b.clear()
                    b.put(data, 0, min(size, b.capacity()))
                    dec.queueInputBuffer(i, 0, min(size, b.capacity()), ptsUs, 0)
                    break
                }
                pump()
            }
            pump()
        }

        fun finish() {
            val i = dec.dequeueInputBuffer(50_000)
            if (i >= 0) dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            repeat(200) {
                pump(eos = true)
                if (encDone) return
            }
        }

        fun take(): List<Pair<ByteArray, Long>> {
            val r = ArrayList(ready)
            ready.clear()
            return r
        }

        private fun pump(eos: Boolean = false) {
            // Çözücü çıktısı → (karıştırılmış) PCM → kodlayıcı
            while (true) {
                val o = dec.dequeueOutputBuffer(info, if (eos) 10_000 else 0)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    decCh = dec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    continue
                }
                if (o < 0) break
                val end = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                if (info.size > 0) {
                    val b = dec.getOutputBuffer(o)!!
                    b.position(info.offset)
                    b.limit(info.offset + info.size)
                    val pcm = ByteArray(info.size)
                    b.get(pcm)
                    encode(downmix(pcm), info.presentationTimeUs)
                }
                dec.releaseOutputBuffer(o, false)
                if (end) {
                    val i = enc.dequeueInputBuffer(50_000)
                    if (i >= 0) enc.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                }
            }
            drainEncoder(if (eos) 10_000 else 0)
        }

        private fun encode(pcm: ByteArray, ptsUs: Long) {
            var off = 0
            val frameBytes = 2 * channels
            while (off < pcm.size) {
                val i = enc.dequeueInputBuffer(5_000)
                if (i < 0) {
                    drainEncoder(0)
                    continue
                }
                val b = enc.getInputBuffer(i)!!
                b.clear()
                val n = min(pcm.size - off, b.capacity() / frameBytes * frameBytes)
                b.put(pcm, off, n)
                val t = ptsUs + (off / frameBytes) * 1_000_000L / rate
                enc.queueInputBuffer(i, 0, n, t, 0)
                off += n
            }
            drainEncoder(0)
        }

        private fun drainEncoder(timeout: Long) {
            while (true) {
                val o = enc.dequeueOutputBuffer(info, timeout)
                if (o < 0) break
                val cfg = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                if (info.size > 0 && !cfg) {
                    val b = enc.getOutputBuffer(o)!!
                    b.position(info.offset)
                    b.limit(info.offset + info.size)
                    val a = ByteArray(info.size)
                    b.get(a)
                    ready += a to info.presentationTimeUs
                }
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) encDone = true
                enc.releaseOutputBuffer(o, false)
            }
        }

        /** 16 bit PCM; 2'den fazla kanalı stereoya indirir (FL FR C LFE BL BR sırası). */
        private fun downmix(pcm: ByteArray): ByteArray {
            if (decCh <= 2 && decCh == channels) return pcm
            val frames = pcm.size / (2 * decCh)
            val out = ByteArray(frames * 2 * channels)
            fun s(f: Int, c: Int): Int {
                val i = (f * decCh + c) * 2
                return (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            }
            for (f in 0 until frames) {
                val l: Int
                val r: Int
                if (decCh == 1) {
                    l = s(f, 0); r = l
                } else if (decCh == 2) {
                    l = s(f, 0); r = s(f, 1)
                } else {
                    val c = if (decCh > 2) s(f, 2) else 0
                    val bl = if (decCh > 4) s(f, 4) else 0
                    val br = if (decCh > 5) s(f, 5) else 0
                    l = ((s(f, 0) + 0.707 * c + 0.707 * bl) * 0.45).toInt()
                    r = ((s(f, 1) + 0.707 * c + 0.707 * br) * 0.45).toInt()
                }
                val lc = l.coerceIn(-32768, 32767)
                val rc = r.coerceIn(-32768, 32767)
                val o = f * 2 * channels
                out[o] = lc.toByte(); out[o + 1] = (lc shr 8).toByte()
                if (channels == 2) { out[o + 2] = rc.toByte(); out[o + 3] = (rc shr 8).toByte() }
            }
            return out
        }

        fun release() {
            runCatching { dec.stop() }; runCatching { dec.release() }
            runCatching { enc.stop() }; runCatching { enc.release() }
        }
    }
}
