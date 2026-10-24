package com.example.network

import android.content.Context
import android.util.Log
import com.example.engine.RpsAuthoritativeEngine
import com.example.model.ChatMessage
import com.example.model.GameChoice
import com.example.model.Player
import com.example.model.Room
import com.example.model.RoomStatus
import com.example.model.UserRole
import com.example.model.Winner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AuthUser(
    val userId: String,
    val displayName: String,
    val role: UserRole,
    val sessionToken: String
)

sealed class SessionSyncResult {
    data class Updated(val room: Room, val version: String) : SessionSyncResult()
    data class Unchanged(val version: String) : SessionSyncResult()
}

class GoogleSheetsRpsRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("rps_arena_prefs", Context.MODE_PRIVATE)

    // Optimized client with HTTP connection pooling, fast timeouts, and keep-alive
    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Local engine fallback so that app is 100% interactive out-of-the-box before Google Sheets deployment
    private val localEngine = RpsAuthoritativeEngine()

    fun getScriptUrl(): String {
        return prefs.getString("apps_script_url", "") ?: ""
    }

    fun setScriptUrl(url: String) {
        prefs.edit().putString("apps_script_url", url.trim()).apply()
    }

    companion object {
        fun hashPin(pin: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Executes an operation with exponential backoff and jitter to ensure no data loss on transient hiccups.
     */
    private suspend fun <T> executeWithRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 400,
        factor: Double = 1.8,
        operationName: String = "operation",
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelayMs
        var lastError: Throwable = Exception("Unknown failure")

        for (attempt in 1..maxAttempts) {
            val result = block()
            if (result.isSuccess) {
                return result
            }
            val error = result.exceptionOrNull() ?: Exception("Unknown error")
            lastError = error
            Log.w("SheetsRepo", "$operationName failed (attempt $attempt/$maxAttempts): ${error.message}")

            if (attempt < maxAttempts) {
                val jitter = (Math.random() * 150).toLong()
                delay(currentDelay + jitter)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
        return Result.failure(lastError)
    }

    suspend fun login(
        displayName: String,
        role: UserRole,
        pin: String
    ): Result<AuthUser> = withContext(Dispatchers.IO) {
        val cleanName = displayName.trim()
        if (cleanName.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Display name cannot be empty."))
        }
        if (pin.length < 4) {
            return@withContext Result.failure(IllegalArgumentException("PIN must be at least 4 digits."))
        }

        val passwordHash = hashPin(pin)
        val scriptUrl = getScriptUrl()

        if (scriptUrl.isNotBlank() && scriptUrl.startsWith("http")) {
            return@withContext executeWithRetry(maxAttempts = 3, operationName = "login") {
                try {
                    val payload = JSONObject().apply {
                        put("action", "login")
                        put("displayName", cleanName)
                        put("role", role.title)
                        put("passwordHash", passwordHash)
                    }
                    val request = Request.Builder()
                        .url(scriptUrl)
                        .post(payload.toString().toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)

                    if (json.optBoolean("success", false)) {
                        val data = json.getJSONObject("data")
                        val user = AuthUser(
                            userId = data.getString("userId"),
                            displayName = data.getString("displayName"),
                            role = UserRole.fromString(data.getString("role")),
                            sessionToken = data.optString("sessionToken", "")
                        )
                        Result.success(user)
                    } else {
                        Result.failure(Exception(json.optString("error", "Login failed.")))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

        // Local in-memory authentication when Google Apps Script URL has not been configured yet
        val mockUserId = "usr_${role.name.lowercase()}_${cleanName.lowercase().take(4)}"
        return@withContext Result.success(
            AuthUser(
                userId = mockUserId,
                displayName = cleanName,
                role = role,
                sessionToken = "token_${System.currentTimeMillis()}"
            )
        )
    }

    suspend fun getOrCreateSession(
        userId: String,
        role: UserRole
    ): Result<Room> = withContext(Dispatchers.IO) {
        val scriptUrl = getScriptUrl()
        if (scriptUrl.isNotBlank() && scriptUrl.startsWith("http")) {
            return@withContext executeWithRetry(maxAttempts = 3, operationName = "getOrCreateSession") {
                try {
                    val payload = JSONObject().apply {
                        put("action", "get_or_create_session")
                        put("userId", userId)
                        put("role", role.title)
                    }
                    val request = Request.Builder()
                        .url(scriptUrl)
                        .post(payload.toString().toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)

                    if (json.optBoolean("success", false)) {
                        val room = parseSessionJson(json.getJSONObject("data"))
                        Result.success(room)
                    } else {
                        Result.failure(Exception(json.optString("error", "Failed to create session.")))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

        // Local fallback
        val mockRoomId = "COUPLE-7700"
        val existing = localEngine.getRoom(mockRoomId)
        if (existing != null) {
            return@withContext Result.success(existing)
        }
        val created = localEngine.createRoom("Husband", userId).getOrThrow()
        return@withContext Result.success(created.copy(roomId = mockRoomId))
    }

    suspend fun joinSession(
        sessionId: String,
        userId: String,
        role: UserRole
    ): Result<Room> = withContext(Dispatchers.IO) {
        val scriptUrl = getScriptUrl()
        if (scriptUrl.isNotBlank() && scriptUrl.startsWith("http")) {
            return@withContext executeWithRetry(maxAttempts = 3, operationName = "joinSession") {
                try {
                    val payload = JSONObject().apply {
                        put("action", "join_session")
                        put("sessionId", sessionId.trim().uppercase())
                        put("userId", userId)
                        put("role", role.title)
                    }
                    val request = Request.Builder()
                        .url(scriptUrl)
                        .post(payload.toString().toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)

                    if (json.optBoolean("success", false)) {
                        val room = parseSessionJson(json.getJSONObject("data"))
                        Result.success(room)
                    } else {
                        Result.failure(Exception(json.optString("error", "Failed to join session.")))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

        // Local fallback
        val room = localEngine.getRoom("COUPLE-7700") ?: localEngine.createRoom("Husband", "p1_h").getOrThrow()
        val joined = localEngine.joinRoom(room.roomId, "Wife", userId).getOrThrow()
        return@withContext Result.success(joined)
    }

    /**
     * High-speed version-aware session synchronization.
     * When server returns unchanged: true, zero JSON parsing overhead is spent.
     */
    suspend fun syncSessionState(
        sessionId: String,
        userId: String,
        knownVersion: String? = null
    ): Result<SessionSyncResult> = withContext(Dispatchers.IO) {
        val scriptUrl = getScriptUrl()
        if (scriptUrl.isNotBlank() && scriptUrl.startsWith("http")) {
            try {
                val vParam = if (!knownVersion.isNullOrBlank()) "&v=${knownVersion.trim()}" else ""
                val urlWithParams = if (scriptUrl.contains("?")) {
                    "$scriptUrl&action=get_session_state&sessionId=${sessionId.trim()}&userId=$userId$vParam"
                } else {
                    "$scriptUrl?action=get_session_state&sessionId=${sessionId.trim()}&userId=$userId$vParam"
                }
                val request = Request.Builder().url(urlWithParams).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)

                if (json.optBoolean("success", false)) {
                    val isUnchanged = json.optBoolean("unchanged", false)
                    val serverVersion = json.optString("version", "")
                    if (isUnchanged) {
                        return@withContext Result.success(SessionSyncResult.Unchanged(serverVersion))
                    }
                    val dataObj = json.optJSONObject("data")
                    if (dataObj != null) {
                        val room = parseSessionJson(dataObj)
                        return@withContext Result.success(SessionSyncResult.Updated(room, serverVersion))
                    }
                }
                return@withContext Result.failure(Exception(json.optString("error", "Error syncing session.")))
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }

        // Local fallback
        val room = localEngine.getRoom(sessionId) ?: localEngine.getRoom("COUPLE-7700")
        if (room != null) {
            val localVersion = "${room.roundNumber}_${room.chatMessages.size}_${room.status}"
            if (knownVersion == localVersion) {
                return@withContext Result.success(SessionSyncResult.Unchanged(localVersion))
            }
            return@withContext Result.success(SessionSyncResult.Updated(room, localVersion))
        }
        return@withContext Result.failure(NoSuchElementException("Session not found"))
    }

    suspend fun getSessionState(
        sessionId: String,
        userId: String
    ): Result<Room> = withContext(Dispatchers.IO) {
        val syncRes = syncSessionState(sessionId, userId, null)
        syncRes.mapCatching {
            when (it) {
                is SessionSyncResult.Updated -> it.room
                is SessionSyncResult.Unchanged -> throw IllegalStateException("Unexpected unchanged on full fetch")
            }
        }
    }

    suspend fun submitMove(
        sessionId: String,
        userId: String,
        roundNumber: Int,
        choice: GameChoice
    ): Result<Room> = withContext(Dispatchers.IO) {
        val scriptUrl = getScriptUrl()
        if (scriptUrl.isNotBlank() && scriptUrl.startsWith("http")) {
            return@withContext executeWithRetry(maxAttempts = 3, initialDelayMs = 300, operationName = "submitMove") {
                try {
                    val payload = JSONObject().apply {
                        put("action", "submit_move")
                        put("sessionId", sessionId.trim().uppercase())
                        put("userId", userId)
                        put("roundNumber", roundNumber)
                        put("move", choice.name)
                    }
                    val request = Request.Builder()
                        .url(scriptUrl)
                        .post(payload.toString().toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)

                    if (json.optBoolean("success", false)) {
                        val room = parseSessionJson(json.getJSONObject("data"))
                        Result.success(room)
                    } else {
                        Result.failure(Exception(json.optString("error", "Failed to submit move.")))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

        // Local fallback
        return@withContext localEngine.submitChoice(sessionId, userId, choice)
    }

    suspend fun sendChatMessage(
        sessionId: String,
        userId: String,
        role: UserRole,
        text: String
    ): Result<List<ChatMessage>> = withContext(Dispatchers.IO) {
        val scriptUrl = getScriptUrl()
        if (scriptUrl.isNotBlank() && scriptUrl.startsWith("http")) {
            return@withContext executeWithRetry(maxAttempts = 3, initialDelayMs = 300, operationName = "sendChatMessage") {
                try {
                    val payload = JSONObject().apply {
                        put("action", "send_chat")
                        put("sessionId", sessionId.trim().uppercase())
                        put("userId", userId)
                        put("senderRole", role.title)
                        put("message", text.trim())
                    }
                    val request = Request.Builder()
                        .url(scriptUrl)
                        .post(payload.toString().toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)

                    if (json.optBoolean("success", false)) {
                        val list = mutableListOf<ChatMessage>()
                        val arr = json.optJSONArray("data")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                list.add(
                                    ChatMessage(
                                        id = obj.optString("id"),
                                        senderId = obj.optString("senderId"),
                                        senderName = obj.optString("senderName"),
                                        text = obj.optString("text"),
                                        timestamp = obj.optLong("timestamp"),
                                        isSystem = obj.optBoolean("isSystem", false)
                                    )
                                )
                            }
                        }
                        Result.success(list)
                    } else {
                        Result.failure(Exception(json.optString("error", "Failed to send chat.")))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

        // Local fallback
        val res = localEngine.sendChatMessage(sessionId, userId, text)
        return@withContext res.map { it.chatMessages }
    }

    suspend fun readyNextRound(
        sessionId: String,
        userId: String
    ): Result<Room> = withContext(Dispatchers.IO) {
        val scriptUrl = getScriptUrl()
        if (scriptUrl.isNotBlank() && scriptUrl.startsWith("http")) {
            return@withContext executeWithRetry(maxAttempts = 3, operationName = "readyNextRound") {
                try {
                    val payload = JSONObject().apply {
                        put("action", "ready_next_round")
                        put("sessionId", sessionId.trim().uppercase())
                        put("userId", userId)
                    }
                    val request = Request.Builder()
                        .url(scriptUrl)
                        .post(payload.toString().toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)

                    if (json.optBoolean("success", false)) {
                        val room = parseSessionJson(json.getJSONObject("data"))
                        Result.success(room)
                    } else {
                        Result.failure(Exception(json.optString("error", "Failed to prepare next round.")))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

        // Local fallback
        val res = localEngine.readyForNextRound(sessionId, userId)
        return@withContext res
    }

    private fun parseSessionJson(data: JSONObject): Room {
        val sessionId = data.getString("sessionId")
        val roundNumber = data.optInt("roundNumber", 1)
        val draws = data.optInt("draws", 0)
        val statusStr = data.optString("status", "WAITING_FOR_OPPONENT")
        val status = try {
            RoomStatus.valueOf(statusStr)
        } catch (_e: Exception) {
            RoomStatus.IN_PROGRESS
        }

        val lastWinnerStr = data.optString("lastWinner", "UNDECIDED")
        val lastWinner = try {
            Winner.valueOf(lastWinnerStr)
        } catch (_e: Exception) {
            Winner.UNDECIDED
        }

        val hObj = data.getJSONObject("husband")
        val husbandChoice = try {
            GameChoice.valueOf(hObj.optString("revealedChoice", "NONE"))
        } catch (_e: Exception) {
            GameChoice.NONE
        }

        val husband = Player(
            id = hObj.optString("id", "usr_husband"),
            username = hObj.optString("username", "Husband"),
            role = UserRole.HUSBAND,
            isHost = true,
            score = hObj.optInt("score", 0),
            hasSubmitted = hObj.optBoolean("hasSubmitted", false),
            revealedChoice = husbandChoice,
            isConnected = hObj.optBoolean("isConnected", true),
            isReadyForNext = false
        )

        var wife: Player? = null
        if (data.has("wife")) {
            val wObj = data.getJSONObject("wife")
            val wifeId = wObj.optString("id", "")
            if (wifeId.isNotBlank()) {
                val wifeChoice = try {
                    GameChoice.valueOf(wObj.optString("revealedChoice", "NONE"))
                } catch (_e: Exception) {
                    GameChoice.NONE
                }
                wife = Player(
                    id = wifeId,
                    username = wObj.optString("username", "Wife"),
                    role = UserRole.WIFE,
                    isHost = false,
                    score = wObj.optInt("score", 0),
                    hasSubmitted = wObj.optBoolean("hasSubmitted", false),
                    revealedChoice = wifeChoice,
                    isConnected = wObj.optBoolean("isConnected", true),
                    isReadyForNext = false
                )
            }
        }

        val chatList = mutableListOf<ChatMessage>()
        val chatArr = data.optJSONArray("chatMessages")
        if (chatArr != null) {
            for (i in 0 until chatArr.length()) {
                val c = chatArr.getJSONObject(i)
                chatList.add(
                    ChatMessage(
                        id = c.optString("id"),
                        senderId = c.optString("senderId"),
                        senderName = c.optString("senderName"),
                        text = c.optString("text"),
                        timestamp = c.optLong("timestamp"),
                        isSystem = c.optBoolean("isSystem", false)
                    )
                )
            }
        }

        return Room(
            roomId = sessionId,
            player1 = husband,
            player2 = wife,
            roundNumber = roundNumber,
            draws = draws,
            status = status,
            lastWinner = lastWinner,
            chatMessages = chatList,
            lastActiveTimestamp = System.currentTimeMillis()
        )
    }
}
