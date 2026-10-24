package com.example.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object LanRoomDiscovery {
    private const val DISCOVERY_PORT = 8889
    private const val TAG = "LanRoomDiscovery"

    /**
     * Periodically broadcasts room availability on the local Wi-Fi subnet.
     */
    class Announcer(
        private val roomId: String,
        private val tcpPort: Int = 8887
    ) {
        private var job: Job? = null
        private var socket: DatagramSocket? = null

        fun start(scope: CoroutineScope) {
            stop()
            job = scope.launch(Dispatchers.IO) {
                try {
                    socket = DatagramSocket().apply {
                        broadcast = true
                    }
                    val message = "RPS_ARENA:$roomId:$tcpPort"
                    val bytes = message.toByteArray(Charsets.UTF_8)
                    val broadcastAddr = InetAddress.getByName("255.255.255.255")
                    val packet = DatagramPacket(bytes, bytes.size, broadcastAddr, DISCOVERY_PORT)

                    Log.d(TAG, "Starting LAN announcement for room $roomId on port $DISCOVERY_PORT")

                    while (isActive) {
                        try {
                            socket?.send(packet)
                        } catch (e: Exception) {
                            Log.d(TAG, "Broadcast send failed: ${e.message}")
                        }
                        delay(1200)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Announcer error: ${e.message}")
                }
            }
        }

        fun stop() {
            try {
                job?.cancel()
                job = null
                socket?.close()
                socket = null
            } catch (_: Exception) {}
        }
    }

    /**
     * Listens on LAN for UDP broadcast for the specified room code.
     * Returns the host's IP address if discovered within timeoutMs, or null.
     */
    suspend fun discoverHostForRoom(
        targetRoomCode: String,
        timeoutMs: Long = 3000
    ): Pair<String, Int>? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(DISCOVERY_PORT).apply {
                    broadcast = true
                    soTimeout = 1000
                }
                val buffer = ByteArray(1024)
                val cleanTarget = targetRoomCode.trim().uppercase()

                Log.d(TAG, "Listening for LAN room broadcast for $cleanTarget...")

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        val parts = text.split(":")
                        if (parts.size >= 3 && parts[0] == "RPS_ARENA") {
                            val announcedRoom = parts[1].trim().uppercase()
                            val port = parts[2].trim().toIntOrNull() ?: 8887
                            if (announcedRoom == cleanTarget) {
                                val hostIp = packet.address.hostAddress ?: continue
                                Log.d(TAG, "Discovered room $cleanTarget at $hostIp:$port")
                                return@withTimeoutOrNull hostIp to port
                            }
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Keep listening until timeout
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Discovery listener error: ${e.message}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
            null
        }
    }
}
