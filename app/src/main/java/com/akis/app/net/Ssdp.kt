package com.akis.app.net

import android.util.Log
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketAddress
import kotlin.concurrent.thread

/**
 * SSDP: ağa "ben medya sunucusuyum" duyurusu yapar ve VLC/TV'lerin M-SEARCH sorularına cevap verir.
 */
class Ssdp(
    private val uuid: String,
    private val location: String,
    private val ifaceAddr: Inet4Address,
) {
    private val group: InetAddress = InetAddress.getByName("239.255.255.250")
    @Volatile private var running = false
    private var socket: MulticastSocket? = null

    private val types = listOf(
        "upnp:rootdevice" to "uuid:$uuid::upnp:rootdevice",
        "uuid:$uuid" to "uuid:$uuid",
        "urn:schemas-upnp-org:device:MediaServer:1" to "uuid:$uuid::urn:schemas-upnp-org:device:MediaServer:1",
        "urn:schemas-upnp-org:service:ContentDirectory:1" to "uuid:$uuid::urn:schemas-upnp-org:service:ContentDirectory:1",
        "urn:schemas-upnp-org:service:ConnectionManager:1" to "uuid:$uuid::urn:schemas-upnp-org:service:ConnectionManager:1",
    )

    fun start() {
        val s = MulticastSocket(null as SocketAddress?)
        s.reuseAddress = true
        s.bind(InetSocketAddress(PORT))
        s.timeToLive = 4
        val nif = try { NetworkInterface.getByInetAddress(ifaceAddr) } catch (e: Exception) { null }
        try {
            if (nif != null) {
                s.joinGroup(InetSocketAddress(group, PORT), nif)
                s.networkInterface = nif
            } else {
                @Suppress("DEPRECATION")
                s.joinGroup(group)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Multicast grubuna katılamadı", e)
        }
        socket = s
        running = true
        thread(name = "ssdp-rx", isDaemon = true) { receiveLoop(s) }
        thread(name = "ssdp-tx", isDaemon = true) {
            // İlk açılışta birkaç kez hızlıca duyur, sonra 30 sn'de bir
            repeat(3) {
                if (!running) return@thread
                notifyAll("ssdp:alive")
                Thread.sleep(700)
            }
            while (running) {
                for (i in 0 until 30) {
                    if (!running) return@thread
                    Thread.sleep(1000)
                }
                notifyAll("ssdp:alive")
            }
        }
    }

    fun announce() = thread(isDaemon = true) { notifyAll("ssdp:alive") }

    fun stop() {
        if (!running) return
        running = false
        try { notifyAll("ssdp:byebye") } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun notifyAll(nts: String) {
        val s = socket ?: return
        for ((nt, usn) in types) {
            val msg = buildString {
                append("NOTIFY * HTTP/1.1\r\n")
                append("HOST: 239.255.255.250:1900\r\n")
                append("CACHE-CONTROL: max-age=1800\r\n")
                append("LOCATION: ").append(location).append("\r\n")
                append("NT: ").append(nt).append("\r\n")
                append("NTS: ").append(nts).append("\r\n")
                append("SERVER: ").append(SERVER).append("\r\n")
                append("USN: ").append(usn).append("\r\n\r\n")
            }.toByteArray()
            try { s.send(DatagramPacket(msg, msg.size, group, PORT)) } catch (e: Exception) { Log.w(TAG, "NOTIFY gönderilemedi", e) }
        }
    }

    private fun receiveLoop(s: MulticastSocket) {
        val buf = ByteArray(4096)
        while (running) {
            val pkt = DatagramPacket(buf, buf.size)
            try { s.receive(pkt) } catch (e: Exception) { break }
            val text = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
            if (!text.startsWith("M-SEARCH", ignoreCase = true)) continue
            val st = text.lineSequence()
                .firstOrNull { it.startsWith("ST:", ignoreCase = true) }
                ?.substringAfter(':')?.trim() ?: continue
            for ((nt, usn) in types) {
                if (st != "ssdp:all" && st != nt) continue
                val resp = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("CACHE-CONTROL: max-age=1800\r\n")
                    append("DATE: ").append(HttpResponder.httpDate()).append("\r\n")
                    append("EXT:\r\n")
                    append("LOCATION: ").append(location).append("\r\n")
                    append("SERVER: ").append(SERVER).append("\r\n")
                    append("ST: ").append(nt).append("\r\n")
                    append("USN: ").append(usn).append("\r\n")
                    append("Content-Length: 0\r\n\r\n")
                }.toByteArray()
                try { s.send(DatagramPacket(resp, resp.size, pkt.socketAddress)) } catch (_: Exception) {}
            }
        }
    }

    companion object {
        private const val TAG = "Ssdp"
        private const val PORT = 1900
        private const val SERVER = "Android/1.0 UPnP/1.0 Akis/1.0"
    }
}
