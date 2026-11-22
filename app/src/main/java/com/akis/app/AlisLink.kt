package com.akis.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Akış ↔ Alış kumanda bağı (yalnızca eşleşmiş Alış cihazları).
 *  - Alış GET /alis/cmd ile bekler (uzun istek); Akış'ta verilen komutlar ([send]) oradan teslim edilir
 *  - Alış POST /alis/state ile her saniye ne oynattığını bildirir: başlık, saniye cinsinden konum, süre,
 *    ses / altyazı parçaları, hız, gecikmeler, ses düzeyi
 * Alış'ın kendi sunucusu yoktur; bağlantıyı hep Alış açar (TV'de güvenlik duvarı / ağ ayarı gerekmez).
 */
object AlisLink {
    data class Device(
        val id: String,
        val name: String,
        val ip: String,
        /** Son durum (Alış'ın gönderdiği JSON); yoksa boş */
        val state: JSONObject,
        /** Son haber (komut bekleme ya da durum) */
        val seen: Long,
    ) {
        val online: Boolean get() = System.currentTimeMillis() - seen < 45_000
        val app: String get() = state.optString("app", "idle")
        val title: String get() = state.optString("title")
        val playing: Boolean get() = state.optBoolean("playing")
        val posMs: Long get() = state.optLong("pos")
        val durMs: Long get() = state.optLong("dur")
    }

    private val _devices = MutableStateFlow<Map<String, Device>>(emptyMap())
    val devices: StateFlow<Map<String, Device>> get() = _devices
    private val queues = ConcurrentHashMap<String, LinkedBlockingQueue<JSONObject>>()

    private fun touch(id: String, name: String, ip: String, state: JSONObject?) {
        _devices.update { m ->
            val old = m[id]
            m + (id to Device(id, name, ip, state ?: old?.state ?: JSONObject(), System.currentTimeMillis()))
        }
    }

    /** Alış'ın durum bildirimi. */
    fun report(dev: Pairing.Device, ip: String, state: JSONObject) = touch(dev.id, dev.name, ip, state)

    /** Komut bekleyen Alış: en çok [waitMs] bekler, gelenleri topluca döndürür. */
    fun poll(dev: Pairing.Device, ip: String, waitMs: Long): JSONArray {
        touch(dev.id, dev.name, ip, null)
        val q = queues.getOrPut(dev.id) { LinkedBlockingQueue() }
        val out = JSONArray()
        val first = q.poll(waitMs.coerceIn(0, 30_000), TimeUnit.MILLISECONDS) ?: return out
        out.put(first)
        while (true) out.put(q.poll() ?: break)
        touch(dev.id, dev.name, ip, null)
        return out
    }

    /** Alış'a komut: op = toggle, play, pause, seek(ms), seekBy(ms), next, prev, speed(v), audio(i), sub(i), subDelay(ms), audioDelay(ms), volume(v), stop, open(url, title, mime) */
    fun send(deviceId: String, cmd: JSONObject) {
        queues.getOrPut(deviceId) { LinkedBlockingQueue() }.offer(cmd)
    }

    fun byIp(ip: String): Device? = _devices.value.values.filter { it.ip == ip && it.online }.maxByOrNull { it.seen }

    fun forget(deviceId: String) {
        queues.remove(deviceId)
        _devices.update { it - deviceId }
    }
}
