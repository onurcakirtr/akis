package com.akis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * "Tüm dosyalar" paylaşımı: telefonun bütün depolama alanları (dahili bellek, SD kart, USB).
 * Android 11+ "Tüm dosyalara erişim" (MANAGE_EXTERNAL_STORAGE), Android 10'da depolama okuma izni gerekir.
 */
object FileAccess {
    fun granted(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** İzin ekranını açan intent (Android 11+). */
    fun settingsIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
        }

    /** Depolama kökleri: dahili bellek + takılı SD kart / USB bellekler. */
    fun roots(): List<File> {
        val out = ArrayList<File>()
        @Suppress("DEPRECATION")
        val primary = Environment.getExternalStorageDirectory()
        if (primary.canRead()) out += primary
        File("/storage").listFiles()?.forEach { f ->
            if (f.name != "emulated" && f.name != "self" && f.isDirectory && f.canRead()) out += f
        }
        return out
    }

    fun rootName(ctx: Context, f: File): String {
        @Suppress("DEPRECATION")
        return if (f.absolutePath == Environment.getExternalStorageDirectory().absolutePath) Lang.s(R.string.internal_storage) else f.name
    }

    /** Yol bir depolama kökünün içindeyse gerçek dosyayı döndürür; değilse null (../ ile dışarı çıkılamaz). */
    fun resolve(path: String): File? {
        if (path.isEmpty()) return null
        val f = try { File(path).canonicalFile } catch (_: Exception) { return null }
        val ok = roots().any { r ->
            val rp = r.canonicalPath
            f.path == rp || f.path.startsWith("$rp/")
        }
        return if (ok && f.exists()) f else null
    }

    /** Görünür içerik: gizli (nokta ile başlayan) dosyalar hariç, klasörler önce. */
    fun list(dir: File): List<File> =
        (dir.listFiles() ?: emptyArray()).filter { !it.name.startsWith(".") }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
}
