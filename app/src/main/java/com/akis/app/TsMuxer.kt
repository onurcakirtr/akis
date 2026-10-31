package com.akis.app

import java.io.ByteArrayOutputStream

/**
 * Canlı yayın için küçük MPEG-TS paketleyici: H.264 (PID 0x100) + AAC/ADTS (PID 0x101).
 * TV'ler ve VLC canlı yayında en çok bu biçimi tanır.
 */
class TsMuxer(private val audio: Boolean) {
    private val cc = HashMap<Int, Int>()

    private fun nextCc(pid: Int): Int {
        val v = cc[pid] ?: 0
        cc[pid] = (v + 1) and 0x0F
        return v
    }

    /** PAT + PMT (her anahtar karenin önüne konur; yeni bağlanan oynatıcı hemen tanısın). */
    fun psi(): ByteArray {
        val o = ByteArrayOutputStream()
        // PAT
        val pat = byteArrayOf(
            0x00, 0xB0.toByte(), 13, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
            0x00, 0x01, (0xE0 or (PMT_PID shr 8)).toByte(), (PMT_PID and 0xFF).toByte(),
        )
        o.write(section(0, pat))
        // PMT
        val streams = ArrayList<Byte>()
        fun es(type: Int, pid: Int) {
            streams += type.toByte(); streams += (0xE0 or (pid shr 8)).toByte(); streams += (pid and 0xFF).toByte()
            streams += 0xF0.toByte(); streams += 0x00
        }
        es(0x1B, VIDEO_PID)
        if (audio) es(0x0F, AUDIO_PID)
        val len = 9 + streams.size + 4
        val pmt = byteArrayOf(
            0x02, (0xB0 or (len shr 8)).toByte(), (len and 0xFF).toByte(), 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
            (0xE0 or (VIDEO_PID shr 8)).toByte(), (VIDEO_PID and 0xFF).toByte(), 0xF0.toByte(), 0x00,
        ) + streams.toByteArray()
        o.write(section(PMT_PID, pmt))
        return o.toByteArray()
    }

    /** Tek pakette PSI bölümü (CRC eklenir, kalan 0xFF ile doldurulur). */
    private fun section(pid: Int, body: ByteArray): ByteArray {
        val crc = crc32(body)
        val p = ByteArray(188) { 0xFF.toByte() }
        p[0] = 0x47
        p[1] = (0x40 or (pid shr 8)).toByte()
        p[2] = (pid and 0xFF).toByte()
        p[3] = (0x10 or nextCc(pid)).toByte()
        p[4] = 0 // pointer_field
        System.arraycopy(body, 0, p, 5, body.size)
        val c = 5 + body.size
        p[c] = (crc ushr 24).toByte(); p[c + 1] = (crc ushr 16).toByte(); p[c + 2] = (crc ushr 8).toByte(); p[c + 3] = crc.toByte()
        return p
    }

    /** Bir H.264 erişim birimi (Annex-B). pts 90 kHz. */
    fun video(au: ByteArray, pts: Long, key: Boolean): ByteArray {
        // Erişim birimi ayırıcı (AUD): bazı TV'ler bunu bekler
        val aud = byteArrayOf(0, 0, 0, 1, 0x09, 0xF0.toByte())
        val pes = pesHeader(0xE0, pts, 0) + aud + au
        return packets(VIDEO_PID, pes, pcr = maxOf(0L, pts - 27_000), rai = key)
    }

    /** Bir AAC çerçevesi (ham; ADTS başlığı burada eklenir). */
    fun audio(aac: ByteArray, pts: Long, sampleRate: Int, channels: Int): ByteArray {
        val frame = adts(aac.size, sampleRate, channels) + aac
        val pes = pesHeader(0xC0, pts, frame.size)
        return packets(AUDIO_PID, pes + frame, pcr = null, rai = false)
    }

    private fun pesHeader(streamId: Int, pts: Long, payloadLen: Int): ByteArray {
        val p = pts and 0x1FFFFFFFFL
        // Video için uzunluk 0 (sınırsız) olabilir; ses için gerçek uzunluk
        val len = if (streamId == 0xE0) 0 else (payloadLen + 8).coerceAtMost(0xFFFF)
        return byteArrayOf(
            0, 0, 1, streamId.toByte(), (len shr 8).toByte(), (len and 0xFF).toByte(),
            0x80.toByte(), 0x80.toByte(), 5,
            (0x21 or ((p shr 29).toInt() and 0x0E)).toByte(),
            (p shr 22).toByte(),
            (((p shr 14).toInt() and 0xFE) or 1).toByte(),
            (p shr 7).toByte(),
            (((p shl 1).toInt() and 0xFE) or 1).toByte(),
        )
    }

    private fun packets(pid: Int, data: ByteArray, pcr: Long?, rai: Boolean): ByteArray {
        val out = ByteArrayOutputStream(data.size + data.size / 184 * 4 + 188)
        var off = 0
        var first = true
        while (off < data.size) {
            val remaining = data.size - off
            var af = ByteArray(0) // uyarlama alanı (uzunluk baytı hariç)
            if (first && (pcr != null || rai)) {
                val flags = (if (rai) 0x40 else 0) or (if (pcr != null) 0x10 else 0)
                af = if (pcr != null) {
                    val b = pcr and 0x1FFFFFFFFL
                    byteArrayOf(
                        flags.toByte(), (b shr 25).toByte(), (b shr 17).toByte(), (b shr 9).toByte(), (b shr 1).toByte(),
                        (((b and 1L).toInt() shl 7) or 0x7E).toByte(), 0,
                    )
                } else byteArrayOf(flags.toByte())
            }
            var afTotal = if (af.isEmpty()) 0 else af.size + 1
            var space = 184 - afTotal
            if (remaining < space) {
                val need = 184 - remaining
                af = when {
                    need == 1 -> ByteArray(0)
                    af.isEmpty() -> ByteArray(need - 1).also { it.fill(0xFF.toByte()); it[0] = 0 }
                    else -> af + ByteArray(need - 1 - af.size) { 0xFF.toByte() }
                }
                afTotal = need
                space = remaining
            }
            out.write(0x47)
            out.write((if (first) 0x40 else 0) or (pid shr 8))
            out.write(pid and 0xFF)
            out.write((if (afTotal > 0) 0x30 else 0x10) or nextCc(pid))
            if (afTotal > 0) {
                out.write(afTotal - 1)
                out.write(af)
            }
            out.write(data, off, space)
            off += space
            first = false
        }
        return out.toByteArray()
    }

    private fun adts(len: Int, rate: Int, ch: Int): ByteArray {
        val sfi = RATES.indexOf(rate).let { if (it < 0) 8 else it }
        val full = len + 7
        return byteArrayOf(
            0xFF.toByte(), 0xF1.toByte(),
            ((1 shl 6) or (sfi shl 2) or (ch shr 2)).toByte(),
            (((ch and 3) shl 6) or (full shr 11)).toByte(),
            ((full shr 3) and 0xFF).toByte(),
            (((full and 7) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
    }

    companion object {
        const val VIDEO_PID = 0x100
        const val AUDIO_PID = 0x101
        const val PMT_PID = 0x1000
        private val RATES = listOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000)

        /** MPEG-2 CRC32 (yansıtmasız, 0x04C11DB7). */
        fun crc32(b: ByteArray): Int {
            var crc = -1
            for (x in b) {
                crc = crc xor ((x.toInt() and 0xFF) shl 24)
                repeat(8) { crc = if (crc < 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1 }
            }
            return crc
        }
    }
}
