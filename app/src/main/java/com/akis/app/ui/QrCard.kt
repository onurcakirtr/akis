package com.akis.app.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.akis.app.ServerIcon
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Kişisel QR kartı: sunucu simgesi ve adı, yerel adres, (varsa) Wi-Fi ve uzak adres QR'ları tek bir görselde.
 * Temanın açık renkleriyle çizilir; yazdırmaya ve paylaşmaya uygun.
 */
object QrCard {
    class Colors(val bg: Int, val card: Int, val fg: Int, val mut: Int, val acc: Int)
    class Row(val title: String, val text: String, val qr: String)

    private const val W = 1240
    private const val PAD = 80f
    private const val QR = 360f

    fun render(ctx: Context, c: Colors, name: String, rows: List<Row>, footer: String): Bitmap {
        val rowH = QR + 60f
        val h = (PAD + 180f + 50f + rows.size * (rowH + 30f) + 70f + PAD).toInt()
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(c.bg)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Başlık: simge + ad
        val icon = ServerIcon.bitmap(ctx, 160)
        cv.drawBitmap(icon, PAD, PAD, p)
        icon.recycle()
        p.color = c.fg
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 72f
        cv.drawText(ellipsize(name, p, W - PAD * 2 - 200f), PAD + 200f, PAD + 95f, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = 36f
        p.color = c.mut
        cv.drawText("Akış", PAD + 200f, PAD + 150f, p)

        var y = PAD + 180f + 50f
        for (r in rows) {
            p.color = c.card
            cv.drawRoundRect(RectF(PAD, y, W - PAD, y + rowH), 40f, 40f, p)
            drawQr(cv, r.qr, PAD + 30f, y + 30f, QR, c.fg, p)
            val tx = PAD + 30f + QR + 50f
            val tw = W - PAD - 40f - tx
            p.color = c.acc
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = 44f
            cv.drawText(ellipsize(r.title, p, tw), tx, y + 110f, p)
            p.typeface = Typeface.DEFAULT
            p.color = c.fg
            p.textSize = 38f
            var ly = y + 180f
            for (line in wrap(r.text, p, tw).take(4)) {
                cv.drawText(line, tx, ly, p)
                ly += 52f
            }
            y += rowH + 30f
        }
        p.color = c.mut
        p.textSize = 30f
        p.textAlign = Paint.Align.CENTER
        cv.drawText(footer, W / 2f, h - PAD + 10f, p)
        return bmp
    }

    /** Ekrandaki gibi noktalı QR. */
    private fun drawQr(cv: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, p: Paint) {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        val n = m.width
        val cell = size / n
        p.color = color
        fun finder(i: Int, j: Int) = (i < 7 && j < 7) || (i >= n - 7 && j < 7) || (i < 7 && j >= n - 7)
        for (j in 0 until n) for (i in 0 until n) {
            if (!m.get(i, j)) continue
            val cx = x + i * cell
            val cy = y + j * cell
            if (finder(i, j)) cv.drawRoundRect(RectF(cx, cy, cx + cell + 0.5f, cy + cell + 0.5f), cell * 0.15f, cell * 0.15f, p)
            else cv.drawCircle(cx + cell / 2, cy + cell / 2, cell * 0.48f, p)
        }
    }

    private fun ellipsize(s: String, p: Paint, w: Float): String {
        if (p.measureText(s) <= w) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > w) t = t.dropLast(1)
        return "$t…"
    }

    /** Uzun adresleri satırlara böler (boşluk yoksa karakterden). */
    private fun wrap(s: String, p: Paint, w: Float): List<String> {
        val out = ArrayList<String>()
        var line = ""
        for (ch in s) {
            if (p.measureText(line + ch) > w) { out += line; line = "" }
            line += ch
        }
        if (line.isNotEmpty()) out += line
        return out
    }

    /** Resimler/Akış klasörüne PNG olarak kaydeder. */
    fun save(ctx: Context, bmp: Bitmap, name: String): Uri? = try {
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "akis-" + name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40) + "-" + System.currentTimeMillis() / 1000 + ".png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Akış")
        }
        ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)?.also { uri ->
            ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    } catch (_: Exception) {
        null
    }

    fun share(ctx: Context, uri: Uri, title: String) {
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
