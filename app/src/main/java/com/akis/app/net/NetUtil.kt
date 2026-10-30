package com.akis.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

object NetUtil {
    private val skipPrefixes = listOf("rmnet", "ccmni", "dummy", "tun", "ppp", "v4-", "clat", "ims", "lo")

    /** Wi-Fi / Ethernet / Hotspot üzerindeki yerel IPv4 adresini bulur (mobil veri hariç). */
    fun findLanAddress(ctx: Context): Inet4Address? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        try {
            @Suppress("DEPRECATION")
            for (n in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(n) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                ) continue
                val addr = cm.getLinkProperties(n)?.linkAddresses
                    ?.map { it.address }
                    ?.filterIsInstance<Inet4Address>()
                    ?.firstOrNull { !it.isLoopbackAddress }
                if (addr != null) return addr
            }
        } catch (_: Exception) {
        }
        // Telefon hotspot ise ya da yukarısı bulamadıysa arayüzleri tara
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { nif -> nif.isUp && !nif.isLoopback && skipPrefixes.none { nif.name.startsWith(it) } }
                .sortedBy { nif -> if (nif.name.startsWith("wlan") || nif.name.startsWith("swlan") || nif.name.startsWith("ap")) 0 else 1 }
                .firstNotNullOfOrNull { nif ->
                    nif.inetAddresses.toList().filterIsInstance<Inet4Address>().firstOrNull { it.isSiteLocalAddress }
                }
        } catch (_: Exception) {
            null
        }
    }
}
