package com.example.network

import android.util.Log
import com.example.model.ChatMessage
import com.example.model.GameChoice
import com.example.model.Player
import com.example.model.Room
import com.example.model.RoomStatus
import com.example.model.Winner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

class LocalRoomClient {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var listenerJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _roomState = MutableStateFlow<Room?>(null)
    val roomState: StateFlow<Room?> = _roomState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _diagnosticLog = MutableStateFlow<List<String>>(emptyList())
    val diagnosticLog: StateFlow<List<String>> = _diagnosticLog.asStateFlow()

    private fun log(message: String) {
        Log.d("RpsClient", message)
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val updated = (_diagnosticLog.value + "[$timestamp] $message").takeLast(50)
        _diagnosticLog.value = updated
    }

    fun connect(
        targetHost: String,
        targetPort: Int = 8887,
        onConnected: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        disconnect()

        // Parse host and optional port if passed as "ip:port" or "http://ip:port"
        var cleanHost = targetHost.trim()
        var cleanPort = targetPort

        if (cleanHost.startsWith("http://")) cleanHost = cleanHost.removePrefix("http://")
        if (cleanHost.startsWith("https://")) cleanHost = cleanHost.removePrefix("https://")
        if (cleanHost.startsWith("ws://")) cleanHost = cleanHost.removePrefix("ws://")
        if (cleanHost.startsWith("wss://")) cleanHost = cleanHost.removePrefix("wss://")

        if (cleanHost.contains("/")) cleanHost = cleanHost.substringBefore("/")

        if (cleanHost.contains(":")) {
            val parts = cleanHost.split(":")
            cleanHost = parts[0]
            parts.getOrNull(1)?.toIntOrNull()?.let { cleanPort = it }
        }

        if (cleanHost.isEmpty()) {
            cleanHost = "127.0.0.1"
        }

        val finalHost = cleanHost
        val finalPort = cleanPort

        _connectionState.value = ConnectionState.CONNECTING
        log("Attempting connection to $finalHost:$finalPort...")

        scope.launch {
            try {
                val s = Socket()
                // 5-second socket connection timeout
                s.connect(InetSocketAddress(finalHost, finalPort), 5000)
                socket = s
                writer = PrintWriter(s.getOutputStream(), true)
                _connectionState.value = ConnectionState.CONNECTED
                log("Connected successfully to $finalHost:$finalPort")
                onConnected()

                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                var line: String? = null
                while (isActive && reader.readLine().also { line = it } != null) {
                    val raw = line?.trim() ?: continue
                    if (raw.isEmpty()) continue
                    handleMessage(raw)
                }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.DISCONNECTED
                val friendlyError = when (e) {
                    is ConnectException -> {
                        if (finalHost == "127.0.0.1" || finalHost == "localhost") {
                            "Connection refused on 127.0.0.1. Enter Player 1's actual Wi-Fi Host IP (shown in their room lobby)."
                        } else {
                            "Could not reach host at $finalHost:$finalPort. Ensure Player 1 is hosting and both devices are on the same Wi-Fi."
                        }
                    }
                    is SocketTimeoutException -> "Connection timed out connecting to $finalHost:$finalPort. Check IP address."
                    is NoRouteToHostException -> "No route to host at $finalHost. Check network connection."
                    is UnknownHostException -> "Unknown host address '$finalHost'."
                    else -> e.message ?: "Failed to connect to host"
                }
                log("Connection failed: $friendlyError")
                onFailure(friendlyError)
            } finally {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    private fun handleMessage(raw: String) {
        try {
            val json = JSONObject(raw)
            val type = json.optString("type")
            if (type == "error") {
                val msg = json.optString("message")
                _errorMessage.value = msg
                log("Server Error: $msg")
                return
            }
            if (type == "room_state") {
                val room = parseRoomJson(json)
                _roomState.value = room
                log("Room state updated: ${room.roomId} (Round ${room.roundNumber}, ${room.status})")
            }
        } catch (e: Exception) {
            log("Parse error: ${e.message}")
        }
    }

    private fun parseRoomJson(json: JSONObject): Room {
        val roomId = json.getString("roomId")
        val roundNumber = json.getInt("roundNumber")
        val draws = json.getInt("draws")
        val status = RoomStatus.valueOf(json.getString("status"))
        val lastWinner = Winner.valueOf(json.getString("lastWinner"))

        val p1Json = json.getJSONObject("player1")
        val p1 = Player(
            id = p1Json.getString("id"),
            username = p1Json.getString("username"),
            score = p1Json.getInt("score"),
            hasSubmitted = p1Json.getBoolean("hasSubmitted"),
            revealedChoice = GameChoice.fromString(p1Json.getString("revealedChoice")),
            isConnected = p1Json.getBoolean("isConnected"),
            isReadyForNext = p1Json.getBoolean("isReadyForNext"),
            isHost = true
        )

        var p2: Player? = null
        if (json.has("player2")) {
            val p2Json = json.getJSONObject("player2")
            p2 = Player(
                id = p2Json.getString("id"),
                username = p2Json.getString("username"),
                score = p2Json.getInt("score"),
                hasSubmitted = p2Json.getBoolean("hasSubmitted"),
                revealedChoice = GameChoice.fromString(p2Json.getString("revealedChoice")),
                isConnected = p2Json.getBoolean("isConnected"),
                isReadyForNext = p2Json.getBoolean("isReadyForNext"),
                isHost = false
            )
        }

        val chatList = mutableListOf<ChatMessage>()
        val chatArray = json.optJSONArray("chatMessages")
        if (chatArray != null) {
            for (i in 0 until chatArray.length()) {
                val item = chatArray.getJSONObject(i)
                chatList.add(
                    ChatMessage(
                        id = item.optString("id"),
                        senderId = item.optString("senderId"),
                        senderName = item.optString("senderName"),
                        text = item.optString("text"),
                        timestamp = item.optLong("timestamp"),
                        isSystem = item.optBoolean("isSystem")
                    )
                )
            }
        }

        return Room(
            roomId = roomId,
            player1 = p1,
            player2 = p2,
            roundNumber = roundNumber,
            draws = draws,
            status = status,
            lastWinner = lastWinner,
            chatMessages = chatList
        )
    }

    fun sendCreateRoom(username: String, playerId: String) {
        log("Sending create_room for $username ($playerId)")
        val json = JSONObject().apply {
            put("action", "create_room")
            put("username", username)
            put("playerId", playerId)
        }
        writer?.println(json.toString())
    }

    fun sendJoinRoom(roomId: String, username: String, playerId: String) {
        log("Sending join_room for $username into $roomId")
        val json = JSONObject().apply {
            put("action", "join_room")
            put("roomId", roomId)
            put("username", username)
            put("playerId", playerId)
        }
        writer?.println(json.toString())
    }

    fun sendSubmitChoice(roomId: String, playerId: String, choice: GameChoice) {
        log("Submitting choice $choice in $roomId")
        val json = JSONObject().apply {
            put("action", "submit_choice")
            put("roomId", roomId)
            put("playerId", playerId)
            put("choice", choice.name)
        }
        writer?.println(json.toString())
    }

    fun sendReadyNext(roomId: String, playerId: String) {
        log("Sending ready_next in $roomId")
        val json = JSONObject().apply {
            put("action", "ready_next")
            put("roomId", roomId)
            put("playerId", playerId)
        }
        writer?.println(json.toString())
    }

    fun sendChatMessage(roomId: String, playerId: String, text: String) {
        val json = JSONObject().apply {
            put("action", "chat_message")
            put("roomId", roomId)
            put("playerId", playerId)
            put("text", text)
        }
        writer?.println(json.toString())
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun disconnect() {
        try {
            listenerJob?.cancel()
            listenerJob = null
            socket?.close()
            socket = null
            writer = null
            _connectionState.value = ConnectionState.DISCONNECTED
        } catch (_: Exception) {}
    }
}
