package com.akis.app.dlna

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.akis.app.AkisApplication
import com.akis.app.Lang
import com.akis.app.Prefs
import com.akis.app.R
import com.akis.app.ServerState
import java.io.InputStream

/** Web arayüzünden gelen dosyaları İndirilenler/Akış'a ya da seçilen klasöre yazar. */
object Uploads {
    const val DEFAULT_DIR = "Akış"

    fun safeName(raw: String): String {
        val n = raw.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.')
        return n.ifEmpty { "dosya" }.take(180)
    }

    /**
     * Gövdeyi hedefe akıtır.
     * @param subDir doluysa seçilen yükleme klasörü yerine İndirilenler/Akış/<subDir> (dosya isteme bağlantıları)
     * @return kaydedilen dosyanın Uri'si; hata ya da yarım kalırsa null (yarım dosya silinir)
     */
    fun receive(ctx: Context, name: String, input: InputStream, length: Long, from: String, subDir: String? = null): Uri? {
        val cr = ctx.contentResolver
        val mime = MediaLibrary.mimeFor(name, null)
        val folder = if (subDir != null) "" else Prefs.current.uploadFolder
        val uri: Uri = try {
            if (folder.isNotEmpty()) {
                val tree = Uri.parse(folder)
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                DocumentsContract.createDocument(cr, parent, mime, name)
            } else {
                val v = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DEFAULT_DIR" + (subDir?.let { "/$it" } ?: ""))
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
            }
        } catch (e: Exception) {
            ServerState.log(Lang.s(R.string.log_upload_target_fail, e.message ?: ""))
            null
        } ?: return null

        var written = 0L
        val ok = try {
            cr.openOutputStream(uri, "w")?.use { out ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    written += n
                }
            }
            written == length
        } catch (_: Exception) {
            false
        }
        if (!ok) {
            try {
                if (folder.isNotEmpty()) DocumentsContract.deleteDocument(cr, uri) else cr.delete(uri, null, null)
            } catch (_: Exception) {
            }
            ServerState.log(Lang.s(R.string.log_upload_partial, name))
            return null
        }
        if (folder.isEmpty()) {
            try {
                cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (_: Exception) {
            }
        }
        ServerState.log(Lang.s(R.string.log_upload_ok, name, from))
        notify(ctx, name, mime, uri, from)
        return uri
    }

    /** "Tüm dosyalar" açıkken web'de bulunulan klasöre doğrudan yazar; aynı ad varsa "ad (1).uzantı". */
    fun receiveToDir(ctx: Context, dir: java.io.File, name: String, input: InputStream, length: Long, from: String): Uri? {
        var f = java.io.File(dir, name)
        var n = 1
        while (f.exists()) {
            val base = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
            f = java.io.File(dir, "$base ($n)$ext")
            n++
        }
        var written = 0L
        val ok = try {
            java.io.FileOutputStream(f).use { out ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    written += r
                }
            }
            written == length
        } catch (_: Exception) {
            false
        }
        if (!ok) {
            f.delete()
            ServerState.log(Lang.s(R.string.log_upload_partial, name))
            return null
        }
        android.media.MediaScannerConnection.scanFile(ctx, arrayOf(f.path), null, null)
        ServerState.log(Lang.s(R.string.log_upload_ok, f.name, from))
        val uri = Uri.fromFile(f)
        notify(ctx, f.name, MediaLibrary.mimeFor(f.name, null), uri, from, openable = false)
        return uri
    }

    /** @param openable false ise dosya yolu (file://) başka uygulamaya verilemeyeceği için dokununca bir şey açılmaz */
    private fun notify(ctx: Context, name: String, mime: String, uri: Uri, from: String, openable: Boolean = true) {
        val open = if (!openable) null else PendingIntent.getActivity(
            ctx, uri.hashCode(),
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, AkisApplication.CHANNEL_FILES)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(Lang.s(R.string.file_received))
            .setContentText("$name • $from")
            .setColor(0xFFD71921.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(2000 + (uri.hashCode() and 0xFFFF), n)
        } catch (_: SecurityException) {
        }
    }
}
