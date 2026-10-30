package com.akis.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

data class Settings(
    val name: String,
    val port: Int,
    val shareVideo: Boolean,
    val shareAudio: Boolean,
    val shareImage: Boolean,
    val showRecent: Boolean,
    val webUi: Boolean,
    val keepAwake: Boolean,
    val autoStart: Boolean,
    val theme: String,      // Themes.ALL içindeki id ("material", "nothing", "cyberpunk"…)
    val darkMode: String,   // "system" | "light" | "dark"
    val folders: List<String>,
    val uuid: String,
    // Özellikler (her biri Ayarlar → Özellikler'den açılıp kapatılır)
    val hideFolders: Boolean,       // .nomedia ve elle gizlenen klasörler
    val hiddenBuckets: Set<String>, // gizlenen MediaStore klasörleri (BUCKET_ID)
    val subtitles: Boolean,         // genişletilmiş altyazı desteği
    val subLang: String,            // tercih edilen altyazı dili ("tr", "en"…)
    val search: Boolean,            // DLNA + web araması
    val history: Boolean,           // izleme geçmişi / kaldığın yerden devam
    val historyMarks: Boolean,      // izlenenlerin başına ✓
    val deviceApproval: Boolean,    // yeni cihazlar için onay iste
    val webPin: String,             // boşsa PIN kapalı
    val upload: Boolean,            // web arayüzünden yükleme
    val uploadFolder: String,       // boşsa İndirilenler/Akış
    val widget: Boolean,            // ana ekran widget'ları
    val notifDetails: Boolean,      // bildirimde ilerleme / cihaz sayısı
)

/** Basit, senkron ayar deposu. Değişiklikler StateFlow ile UI'ye ve servise yansır. */
object Prefs {
    private lateinit var sp: SharedPreferences
    private lateinit var state: MutableStateFlow<Settings>

    val settings: StateFlow<Settings> get() = state
    val current: Settings get() = state.value

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("akis", Context.MODE_PRIVATE)
        if (!sp.contains("uuid")) sp.edit().putString("uuid", UUID.randomUUID().toString()).apply()
        state = MutableStateFlow(read())
    }

    private fun read() = Settings(
        name = sp.getString("name", null)?.takeIf { it.isNotBlank() } ?: "Akış • ${Build.MODEL}",
        port = sp.getInt("port", 8200),
        shareVideo = sp.getBoolean("shareVideo", true),
        shareAudio = sp.getBoolean("shareAudio", true),
        shareImage = sp.getBoolean("shareImage", true),
        showRecent = sp.getBoolean("showRecent", true),
        webUi = sp.getBoolean("webUi", true),
        keepAwake = sp.getBoolean("keepAwake", true),
        autoStart = sp.getBoolean("autoStart", false),
        theme = sp.getString("theme", "material") ?: "material",
        darkMode = sp.getString("darkMode", "system") ?: "system",
        folders = (sp.getString("folders", "") ?: "").split('\n').filter { it.isNotBlank() },
        uuid = sp.getString("uuid", null) ?: UUID.randomUUID().toString(),
        hideFolders = sp.getBoolean("hideFolders", true),
        hiddenBuckets = sp.getStringSet("hiddenBuckets", emptySet())?.toSet() ?: emptySet(),
        subtitles = sp.getBoolean("subtitles", true),
        subLang = sp.getString("subLang", "tr") ?: "tr",
        search = sp.getBoolean("search", true),
        history = sp.getBoolean("history", true),
        historyMarks = sp.getBoolean("historyMarks", true),
        deviceApproval = sp.getBoolean("deviceApproval", false),
        webPin = sp.getString("webPin", "") ?: "",
        upload = sp.getBoolean("upload", false),
        uploadFolder = sp.getString("uploadFolder", "") ?: "",
        widget = sp.getBoolean("widget", true),
        notifDetails = sp.getBoolean("notifDetails", true),
    )

    fun update(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        state.value = read()
    }

    fun set(key: String, value: Boolean) = update { putBoolean(key, value) }

    fun setHidden(bucket: String, hidden: Boolean) {
        val set = current.hiddenBuckets.toMutableSet()
        if (hidden) set += bucket else set -= bucket
        update { putStringSet("hiddenBuckets", set) }
    }

    fun addFolder(uri: String) {
        val list = current.folders
        if (uri in list) return
        update { putString("folders", (list + uri).joinToString("\n")) }
    }

    fun removeFolder(uri: String) {
        update { putString("folders", current.folders.filter { it != uri }.joinToString("\n")) }
    }
}
