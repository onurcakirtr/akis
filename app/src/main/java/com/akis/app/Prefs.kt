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
    val theme: String,      // "material" | "nothing"
    val darkMode: String,   // "system" | "light" | "dark"
    val folders: List<String>,
    val uuid: String,
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
    )

    fun update(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        state.value = read()
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
