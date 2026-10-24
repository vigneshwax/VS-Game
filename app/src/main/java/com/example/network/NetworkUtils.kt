package com.example.network

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * Finds the primary non-loopback IPv4 address of the device (Wi-Fi, hotspot, or Ethernet).
     * Returns "127.0.0.1" if no external network interface is active.
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addresses = intf.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        // Filter out dummy/link-local addresses
                        if (!hostAddress.startsWith("169.254")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    /**
     * Parses an input string that might be:
     * - "RPS-1234" (just room code)
     * - "192.168.1.50#RPS-1234" (IP and room code)
     * - "192.168.1.50:8887#RPS-1234"
     * - "192.168.1.50:8887"
     * Returns Pair<parsedHost, parsedRoomCode>
     */
    fun parseRoomTarget(input: String, defaultHost: String): Pair<String, String> {
        val trimmed = input.trim()
        if (trimmed.contains("#")) {
            val parts = trimmed.split("#")
            val hostPart = parts[0].trim()
            val codePart = parts.getOrNull(1)?.trim()?.uppercase() ?: ""
            return hostPart to codePart
        }
        if (trimmed.contains("/") && trimmed.startsWith("http")) {
            // URL format e.g. https://server.com/join?room=RPS-1234
            val uri = android.net.Uri.parse(trimmed)
            val host = uri.host ?: defaultHost
            val room = uri.getQueryParameter("room") ?: uri.lastPathSegment ?: ""
            return host to room.uppercase()
        }
        return defaultHost to trimmed.uppercase()
    }
}
