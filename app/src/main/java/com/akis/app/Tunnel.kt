package com.akis.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.concurrent.thread

/**
 * Uzaktan erişim: Cloudflare Tunnel (cloudflared; uygulamaya gömülü, Termux'un Android derlemesi).
 *  - Hızlı tünel: hesap gerekmez, rastgele …trycloudflare.com adresi
 *  - Kendi alan adı: Cloudflare panelinde oluşturulan tünelin token'ı
 * Sunucu açıkken ve ayar açıksa çalışır; koparsa artan aralıklarla yeniden dener.
 */
object Tunnel {
    enum class State { OFF, STARTING, UP, ERROR }

    private lateinit var app: Context
    private val _state = MutableStateFlow(State.OFF)
    val state: StateFlow<State> get() = _state
    /** Uzak adres (https://…); kendi alan adı modunda kullanıcının girdiği adres. */
    val url = MutableStateFlow<String?>(null)
    val lastError = MutableStateFlow<String?>(null)

    @Volatile private var proc: Process? = null
    @Volatile private var wanted = false
    @Volatile private var port = 8200
    private var attempts = 0

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    private fun binary(): File? = if (::app.isInitialized) {
        File(app.applicationInfo.nativeLibraryDir, "libcloudflared.so").takeIf { it.exists() }
    } else null

    /** Bu telefonda (64 bit ARM) çalışabilir mi. */
    val supported: Boolean get() = binary() != null

    @Synchronized
    fun start(serverPort: Int) {
        val s = Prefs.current
        if (!s.remoteOn || binary() == null) return
        if (s.remoteMode == "token" && s.remoteToken.isBlank()) {
            _state.value = State.ERROR
            lastError.value = Lang.s(R.string.remote_need_token)
            return
        }
        port = serverPort
        wanted = true
        attempts = 0
        if (proc == null) launch()
    }

    @Synchronized
    fun stop() {
        wanted = false
        proc?.destroy()
        proc = null
        _state.value = State.OFF
        url.value = null
    }

    fun restart(serverPort: Int) {
        stop()
        start(serverPort)
    }

    private fun launch() {
        val bin = binary() ?: return
        val s = Prefs.current
        _state.value = State.STARTING
        lastError.value = null
        url.value = if (s.remoteMode == "token") s.remoteHost.trim().takeIf { it.isNotEmpty() }?.let { if (it.startsWith("http")) it else "https://$it" } else null
        val cmd = mutableListOf(bin.path, "tunnel", "--no-autoupdate", "--metrics", "127.0.0.1:0")
        if (s.remoteMode == "token") cmd += listOf("run", "--token", s.remoteToken.trim())
        else cmd += listOf("--url", "http://127.0.0.1:$port")
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        val env = pb.environment()
        env["HOME"] = app.filesDir.path
        env["TMPDIR"] = app.cacheDir.path
        // Termux klasörleri yok: Android'in kendi DNS çözümleyicisi ve sertifika deposu kullanılsın
        env["GODEBUG"] = "netdns=cgo"
        env["SSL_CERT_DIR"] = "/system/etc/security/cacerts"
        val p = try {
            pb.start()
        } catch (e: Exception) {
            fail(e.message ?: "start")
            return
        }
        proc = p
        thread(name = "cloudflared", isDaemon = true) {
            val tail = ArrayDeque<String>()
            val urlRe = Regex("https://[a-z0-9-]+\\.trycloudflare\\.com")
            try {
                p.inputStream.bufferedReader().forEachLine { line ->
                    if (tail.size >= 8) tail.removeFirst()
                    tail.addLast(line)
                    if (s.remoteMode != "token") urlRe.find(line)?.let { url.value = it.value }
                    if ("Registered tunnel connection" in line || "Connection registered" in line) {
                        attempts = 0
                        _state.value = State.UP
                        ServerState.log(Lang.s(R.string.log_remote_up))
                    }
                }
            } catch (_: Exception) {
            }
            val code = runCatching { p.waitFor() }.getOrDefault(-1)
            synchronized(this) { if (proc === p) proc = null }
            if (wanted) {
                val msg = tail.lastOrNull { "ERR" in it || "error" in it.lowercase() }?.substringAfter("ERR")?.trim() ?: "exit $code"
                fail(msg)
                // Artan aralıklarla yeniden dene (5 sn … 2 dk)
                attempts++
                Thread.sleep(minOf(120_000L, 5_000L * attempts))
                synchronized(this) { if (wanted && proc == null) launch() }
            }
        }
    }

    private fun fail(msg: String) {
        _state.value = State.ERROR
        lastError.value = msg.take(200)
        ServerState.log(Lang.s(R.string.log_remote_err, msg.take(80)))
    }
}
