package com.akis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.toArgb
import com.akis.app.ui.theme.Themes
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

/**
 * VLC'de, Windows Gezgini'nde ve TV'lerde sunucu adının yanında görünen simge (web sekmesi ikonu da bu).
 * Hazır renkler, seçili temanın renkleri ya da galeriden seçilen bir resim.
 */
object ServerIcon {
    class Preset(val id: String, @param:StringRes val name: Int, val bg: Int, val fg: Int, val dot: Int)

    val PRESETS = listOf(
        Preset("default", R.string.icon_default, 0xFF0A0A0A.toInt(), 0xFFFFFFFF.toInt(), 0xFFD71921.toInt()),
        Preset("theme", R.string.icon_theme, 0, 0, 0),
        Preset("red", R.string.icon_red, 0xFFD71921.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        Preset("blue", R.string.icon_blue, 0xFF1E6FE8.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFD43B.toInt()),
        Preset("green", R.string.icon_green, 0xFF1B8A4B.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        Preset("purple", R.string.icon_purple, 0xFF6B3FD8.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF79C6.toInt()),
        Preset("orange", R.string.icon_orange, 0xFFF06A1D.toInt(), 0xFFFFFFFF.toInt(), 0xFF0A0A0A.toInt()),
        Preset("light", R.string.icon_light, 0xFFF2F2F2.toInt(), 0xFF111111.toInt(), 0xFFD71921.toInt()),
    )

    private val cache = HashMap<String, ByteArray>()

    fun customFile(ctx: Context) = File(ctx.filesDir, "server_icon.png")

    /** Simge değişince istemcilerin yeniden indirmesi için adreslere eklenen sürüm. */
    fun version(ctx: Context): String {
        val s = Prefs.current
        val key = when (s.iconStyle) {
            "custom" -> "custom-" + customFile(ctx).lastModified()
            "theme" -> "theme-" + s.theme
            else -> s.iconStyle
        }
        return Integer.toHexString(key.hashCode())
    }

    private fun colors(ctx: Context, id: String): Preset {
        if (id == "theme") {
            val cs = Themes.byId(Prefs.current.theme).scheme(ctx, true)
            return Preset("theme", R.string.icon_theme, cs.primary.toArgb(), cs.onPrimary.toArgb(), cs.primaryContainer.toArgb())
        }
        return PRESETS.firstOrNull { it.id == id && it.id != "theme" } ?: PRESETS.first()
    }

    /** Hazır simgeyi çizer (ekran sembolü + nokta). */
    fun draw(ctx: Context, id: String, size: Int): Bitmap {
        val p = colors(ctx, id)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val k = size / 120f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = p.bg
        c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 34f * k, 34f * k, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f * k
        paint.color = p.fg
        c.drawRoundRect(RectF(24f * k, 30f * k, 96f * k, 82f * k), 12f * k, 12f * k, paint)
        paint.strokeCap = Paint.Cap.ROUND
        c.drawLine(42f * k, 96f * k, 78f * k, 96f * k, paint)
        paint.style = Paint.Style.FILL
        paint.color = p.dot
        c.drawCircle(60f * k, 56f * k, 12f * k, paint)
        return bmp
    }

    fun bitmap(ctx: Context, size: Int, style: String = Prefs.current.iconStyle): Bitmap {
        if (style == "custom") {
            val f = customFile(ctx)
            if (f.exists()) {
                BitmapFactory.decodeFile(f.path)?.let { src ->
                    val out = Bitmap.createScaledBitmap(src, size, size, true)
                    if (out !== src) src.recycle()
                    return out
                }
            }
        }
        return draw(ctx, if (style == "custom") "default" else style, size)
    }

    fun png(ctx: Context, size: Int): ByteArray {
        val key = version(ctx) + "@" + size
        synchronized(cache) { cache[key]?.let { return it } }
        val bmp = bitmap(ctx, size)
        val o = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, o)
        bmp.recycle()
        return o.toByteArray().also {
            synchronized(cache) {
                // Simge değişince eski boyutlar atılır; aynı simgenin farklı boyutları birlikte durur
                cache.keys.removeAll { k -> !k.startsWith(version(ctx) + "@") }
                cache[key] = it
            }
        }
    }

    /** Galeriden seçilen resmi ortadan kare kırpıp 256×256 PNG olarak saklar. */
    fun saveCustom(ctx: Context, uri: Uri): Boolean = try {
        val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val m = min(info.size.width, info.size.height)
            if (m > 1024) d.setTargetSampleSize(m / 1024)
        }
        val side = min(src.width, src.height)
        val out = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            src,
            Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2),
            Rect(0, 0, 256, 256),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        src.recycle()
        customFile(ctx).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        out.recycle()
        true
    } catch (_: Exception) {
        false
    }
}
