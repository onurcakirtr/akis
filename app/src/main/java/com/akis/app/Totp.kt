package com.akis.app

import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * İki adımlı doğrulama (RFC 6238; Google Authenticator, Aegis, Microsoft Authenticator uyumlu).
 * 30 sn'lik 6 haneli kodlar; ±1 adım tolerans; aynı kod ikinci kez kabul edilmez.
 */
object Totp {
    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    @Volatile private var lastStep = 0L

    val enabled: Boolean get() = Prefs.current.totpSecret.isNotEmpty()

    fun newSecret(): String {
        val b = ByteArray(20).also { SecureRandom().nextBytes(it) }
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (x in b) {
            buffer = (buffer shl 8) or (x.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(B32[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(B32[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    private fun decode(s: String): ByteArray {
        val out = ArrayList<Byte>()
        var buffer = 0
        var bits = 0
        for (c in s.uppercase()) {
            val v = B32.indexOf(c)
            if (v < 0) continue
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out += ((buffer shr (bits - 8)) and 0xFF).toByte()
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    private fun code(secret: String, step: Long): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(decode(secret), "HmacSHA1"))
        val h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array())
        val o = h[h.size - 1].toInt() and 0x0F
        val bin = ((h[o].toInt() and 0x7F) shl 24) or ((h[o + 1].toInt() and 0xFF) shl 16) or
            ((h[o + 2].toInt() and 0xFF) shl 8) or (h[o + 3].toInt() and 0xFF)
        return (bin % 1_000_000).toString().padStart(6, '0')
    }

    /** @param secret kurulum sırasında doğrulanan yeni anahtar; null ise kayıtlı anahtar */
    fun check(input: String, secret: String? = null): Boolean {
        val s = secret ?: Prefs.current.totpSecret
        val c = input.filter { it.isDigit() }
        if (s.isEmpty() || c.length != 6) return false
        val now = System.currentTimeMillis() / 30_000
        for (d in -1L..1L) {
            val step = now + d
            if (code(s, step) == c) {
                if (secret == null) {
                    if (step <= lastStep) return false // aynı kod tekrar kullanılamaz
                    lastStep = step
                }
                return true
            }
        }
        return false
    }

    fun uri(secret: String, account: String): String {
        val label = URLEncoder.encode("Akış:$account", "UTF-8").replace("+", "%20")
        return "otpauth://totp/$label?secret=$secret&issuer=" + URLEncoder.encode("Akış", "UTF-8")
    }
}
