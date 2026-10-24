package com.example.network

import android.util.Log
import com.example.engine.RpsAuthoritativeEngine
import com.example.model.GameChoice
import com.example.model.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight Authoritative Server running directly on device / emulator
 * listening on a local port (default 8887).
 * Allows any other Android device or emulator on the network to connect.
 */
class LocalRoomHostServer(
    val port: Int = 8887,
    private val engine: RpsAuthoritativeEngine
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Sockets mapped by playerId
    private val connectedClients = ConcurrentHashMap<String, PrintWriter>()

    val hostIp: String get() = NetworkUtils.getLocalIpAddress()

    @Synchronized
    fun start(onStarted: (port: Int, hostIp: String) -> Unit = { _, _ -> }, onError: (String) -> Unit = {}) {
        if (serverSocket != null && !serverSocket!!.isClosed) {
            onStarted(port, hostIp)
            return
        }

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                val currentIp = hostIp
                Log.d("LocalRoomHostServer", "Server started on $currentIp:$port")
                onStarted(port, currentIp)

                while (isActive && serverSocket != null && !serverSocket!!.isClosed) {
                    val socket = serverSocket!!.accept()
                    val remoteIp = socket.inetAddress.hostAddress
                    Log.d("LocalRoomHostServer", "Client connected from $remoteIp")
                    scope.launch { handleClient(socket) }
                }
            } catch (e: Exception) {
                Log.e("LocalRoomHostServer", "Server exception: ${e.message}")
                onError(e.message ?: "Failed to start server")
            }
        }
    }

    private fun handleClient(socket: Socket) {
        var playerId: String? = null
        var roomId: String? = null
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            var line: String? = null
            while (reader.readLine().also { line = it } != null) {
                val raw = line?.trim() ?: continue
                if (raw.isEmpty()) continue

                val json = JSONObject(raw)
                val action = json.optString("action")
                val reqPlayerId = json.optString("playerId")
                if (reqPlayerId.isNotEmpty()) {
                    playerId = reqPlayerId
                    connectedClients[reqPlayerId] = writer
                }

                when (action) {
                    "create_room" -> {
                        val username = json.getString("username")
                        val result = engine.createRoom(username, reqPlayerId)
                        result.onSuccess { room ->
                            roomId = room.roomId
                            sendRoomState(room)
                        }.onFailure { err ->
                            sendError(writer, err.message ?: "Failed to create room")
                        }
                    }
                    "join_room" -> {
                        val reqRoomId = json.getString("roomId")
                        val username = json.getString("username")
                        val result = engine.joinRoom(reqRoomId, username, reqPlayerId)
                        result.onSuccess { room ->
                            roomId = room.roomId
                            broadcastRoomState(room)
                        }.onFailure { err ->
                            sendError(writer, err.message ?: "Failed to join room")
                        }
                    }
                    "submit_choice" -> {
                        val reqRoomId = json.getString("roomId")
                        val choiceStr = json.getString("choice")
                        val choice = GameChoice.fromString(choiceStr)
                        val result = engine.submitChoice(reqRoomId, reqPlayerId, choice)
                        result.onSuccess { room ->
                            broadcastRoomState(room)
                        }.onFailure { err ->
                            sendError(writer, err.message ?: "Failed to submit choice")
                        }
                    }
                    "ready_next" -> {
                        val reqRoomId = json.getString("roomId")
                        val result = engine.readyForNextRound(reqRoomId, reqPlayerId)
                        result.onSuccess { room ->
                            broadcastRoomState(room)
                        }.onFailure { err ->
                            sendError(writer, err.message ?: "Failed to prepare next round")
                        }
                    }
                    "chat_message" -> {
                        val reqRoomId = json.getString("roomId")
                        val text = json.getString("text")
                        val result = engine.sendChatMessage(reqRoomId, reqPlayerId, text)
                        result.onSuccess { room ->
                            broadcastRoomState(room)
                        }.onFailure { err ->
                            sendError(writer, err.message ?: "Failed to send chat")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("LocalRoomHostServer", "Client disconnect: ${e.message}")
        } finally {
            if (playerId != null && roomId != null) {
                connectedClients.remove(playerId)
                val updated = engine.handleDisconnect(roomId!!, playerId!!)
                if (updated != null) {
                    broadcastRoomState(updated)
                }
            }
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun sendRoomState(room: Room) {
        val json = serializeRoom(room)
        val p1Writer = connectedClients[room.player1.id]
        p1Writer?.println(json.toString())
    }

    fun broadcastRoomState(room: Room) {
        val json = serializeRoom(room)
        val msg = json.toString()

        connectedClients[room.player1.id]?.println(msg)
        room.player2?.let { p2 ->
            connectedClients[p2.id]?.println(msg)
        }
    }

    private fun sendError(writer: PrintWriter, message: String) {
        val json = JSONObject().apply {
            put("type", "error")
            put("message", message)
        }
        writer.println(json.toString())
    }

    private fun serializeRoom(room: Room): JSONObject {
        return JSONObject().apply {
            put("type", "room_state")
            put("roomId", room.roomId)
            put("roundNumber", room.roundNumber)
            put("draws", room.draws)
            put("status", room.status.name)
            put("lastWinner", room.lastWinner.name)

            put("player1", JSONObject().apply {
                put("id", room.player1.id)
                put("username", room.player1.username)
                put("score", room.player1.score)
                put("hasSubmitted", room.player1.hasSubmitted)
                put("revealedChoice", room.player1.revealedChoice.name)
                put("isConnected", room.player1.isConnected)
                put("isReadyForNext", room.player1.isReadyForNext)
            })

            room.player2?.let { p2 ->
                put("player2", JSONObject().apply {
                    put("id", p2.id)
                    put("username", p2.username)
                    put("score", p2.score)
                    put("hasSubmitted", p2.hasSubmitted)
                    put("revealedChoice", p2.revealedChoice.name)
                    put("isConnected", p2.isConnected)
                    put("isReadyForNext", p2.isReadyForNext)
                })
            }

            val chatArray = org.json.JSONArray()
            for (msg in room.chatMessages) {
                chatArray.put(JSONObject().apply {
                    put("id", msg.id)
                    put("senderId", msg.senderId)
                    put("senderName", msg.senderName)
                    put("text", msg.text)
                    put("timestamp", msg.timestamp)
                    put("isSystem", msg.isSystem)
                })
            }
            put("chatMessages", chatArray)
        }
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
            serverSocket = null
            serverJob?.cancel()
            serverJob = null
            connectedClients.clear()
        } catch (_: Exception) {}
    }
}
