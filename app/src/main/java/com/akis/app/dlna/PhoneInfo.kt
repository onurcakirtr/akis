package com.akis.app.dlna

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.LruCache
import com.akis.app.FileAccess
import com.akis.app.ServerState
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Web'deki "Telefon durumu" ve "Yüklü uygulamalar" sayfalarının verisi (hafif; anlık okunur). */
object PhoneInfo {
    fun status(ctx: Context): JSONObject {
        val o = JSONObject()
        // Pil
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { b ->
            val level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level >= 0 && scale > 0) o.put("battery", level * 100 / scale)
            val st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            o.put("charging", st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL)
            val temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            if (temp != Int.MIN_VALUE) o.put("temp", temp / 10.0)
        }
        // Depolama
        val storage = JSONArray()
        val roots = if (FileAccess.granted(ctx)) FileAccess.roots() else {
            @Suppress("DEPRECATION")
            listOf(Environment.getExternalStorageDirectory())
        }
        for (r in roots) {
            try {
                val s = StatFs(r.path)
                storage.put(JSONObject().put("name", FileAccess.rootName(ctx, r)).put("total", s.totalBytes).put("free", s.availableBytes))
            } catch (_: Exception) {
            }
        }
        o.put("storage", storage)
        // Bellek
        val mi = ActivityManager.MemoryInfo()
        ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        o.put("ramTotal", mi.totalMem).put("ramFree", mi.availMem)
        // Wi-Fi (ağ adı konum izni gerektirdiği için gösterilmez)
        try {
            @Suppress("DEPRECATION")
            val wi = ctx.applicationContext.getSystemService(WifiManager::class.java).connectionInfo
            if (wi != null && wi.networkId != -1) {
                o.put("rssi", wi.rssi).put("linkSpeed", wi.linkSpeed)
                @Suppress("DEPRECATION")
                o.put("signal", WifiManager.calculateSignalLevel(wi.rssi, 5))
            }
        } catch (_: Exception) {
        }
        o.put("device", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}").put("android", Build.VERSION.RELEASE)
        val started = ServerState.startedAt.value
        if (ServerState.running.value && started > 0) o.put("uptime", System.currentTimeMillis() - started)
        return o
    }

    private class AppRow(val pkg: String, val label: String, val version: String, val size: Long, val split: Boolean, val system: Boolean, val updated: Long)

    @Volatile private var appCache: Pair<Long, List<AppRow>>? = null

    private fun rows(ctx: Context): List<AppRow> {
        appCache?.let { (t, l) -> if (System.currentTimeMillis() - t < 60_000) return l }
        val pm = ctx.packageManager
        val out = ArrayList<AppRow>()
        for (ai in pm.getInstalledApplications(0)) {
            if (ai.packageName == ctx.packageName) continue
            val files = listOf(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())
            val pi = runCatching { pm.getPackageInfo(ai.packageName, 0) }.getOrNull()
            out += AppRow(
                ai.packageName, ai.loadLabel(pm).toString(), pi?.versionName ?: "",
                files.sumOf { File(it).length() }, files.size > 1,
                (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
                pi?.lastUpdateTime ?: 0,
            )
        }
        out.sortBy { it.label.lowercase() }
        appCache = System.currentTimeMillis() to out
        return out
    }

    fun apps(ctx: Context, system: Boolean): JSONObject {
        val arr = JSONArray()
        for (r in rows(ctx)) {
            if (r.system && !system) continue
            arr.put(
                JSONObject().put("pkg", r.pkg).put("name", r.label).put("ver", r.version).put("sz", r.size)
                    .put("split", r.split).put("sys", r.system).put("upd", r.updated / 1000),
            )
        }
        return JSONObject().put("items", arr)
    }

    private val icons = LruCache<String, ByteArray>(3 * 1024 * 1024)

    fun icon(ctx: Context, pkg: String): ByteArray? {
        icons.get(pkg)?.let { return it }
        return try {
            val d = ctx.packageManager.getApplicationIcon(pkg)
            val b = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 144, 144)
            d.draw(Canvas(b))
            val o = ByteArrayOutputStream()
            b.compress(Bitmap.CompressFormat.PNG, 100, o)
            b.recycle()
            o.toByteArray().also { icons.put(pkg, it) }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** (dosya adı için güvenli etiket, APK dosyaları) */
    fun apkFiles(ctx: Context, pkg: String): Pair<String, List<File>>? = try {
        val pm = ctx.packageManager
        val ai = pm.getApplicationInfo(pkg, 0)
        val pi = pm.getPackageInfo(pkg, 0)
        val files = (listOf(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())).map { File(it) }.filter { it.canRead() }
        val label = (ai.loadLabel(pm).toString() + if (pi.versionName.isNullOrEmpty()) "" else "-" + pi.versionName)
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (files.isEmpty()) null else label to files
    } catch (_: Exception) {
        null
    }
}
