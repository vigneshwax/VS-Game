package com.example.engine

import com.example.model.ChatMessage
import com.example.model.GameChoice
import com.example.model.Player
import com.example.model.Room
import com.example.model.RoomStatus
import com.example.model.Winner
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Authoritative in-memory Room & Game Engine.
 * Enforces server-side authority:
 * - Hidden choices until both players have submitted.
 * - Strict winner resolution (Rock beats Scissors, Scissors beats Paper, Paper beats Rock).
 * - Room cap of 2 players max, rejecting any 3rd player.
 * - Anti-tamper choice locks.
 * - Rate-limited, in-memory live chat with length checks.
 */
class RpsAuthoritativeEngine {

    private val rooms = ConcurrentHashMap<String, Room>()
    // Hidden player choices stored authoritatively on the server side
    private val secretChoices = ConcurrentHashMap<String, ConcurrentHashMap<String, GameChoice>>()
    // Rate limit tracker: playerId -> last message timestamp
    private val lastChatTimestamps = ConcurrentHashMap<String, Long>()

    private val random = SecureRandom()

    fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        var code: String
        do {
            val num = (1000..9999).random()
            code = "RPS-$num"
        } while (rooms.containsKey(code))
        return code
    }

    @Synchronized
    fun createRoom(username: String, playerId: String): Result<Room> {
        val cleanName = username.trim()
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Username cannot be empty"))
        }
        val roomId = generateRoomCode()
        val p1 = Player(
            id = playerId,
            username = cleanName,
            isHost = true,
            score = 0
        )
        val initialSysMsg = ChatMessage(
            senderId = "SYSTEM",
            senderName = "Arena Bot",
            text = "Welcome to room $roomId! Waiting for opponent...",
            isSystem = true
        )
        val room = Room(
            roomId = roomId,
            player1 = p1,
            player2 = null,
            roundNumber = 1,
            draws = 0,
            status = RoomStatus.WAITING_FOR_OPPONENT,
            lastWinner = Winner.UNDECIDED,
            chatMessages = listOf(initialSysMsg),
            lastActiveTimestamp = System.currentTimeMillis()
        )
        rooms[roomId] = room
        secretChoices[roomId] = ConcurrentHashMap()
        return Result.success(room)
    }

    @Synchronized
    fun joinRoom(roomId: String, username: String, playerId: String): Result<Room> {
        val cleanCode = roomId.trim().uppercase()
        val cleanName = username.trim()
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Username cannot be empty"))
        }

        val room = rooms[cleanCode] ?: return Result.failure(NoSuchElementException("Room $cleanCode does not exist or has expired."))

        if (room.player1.id == playerId) {
            // Reconnecting player 1
            val updatedP1 = room.player1.copy(isConnected = true)
            val updated = room.copy(
                player1 = updatedP1,
                lastActiveTimestamp = System.currentTimeMillis()
            )
            rooms[cleanCode] = updated
            return Result.success(updated)
        }

        if (room.player2 != null) {
            if (room.player2.id == playerId) {
                // Reconnecting player 2
                val updatedP2 = room.player2.copy(isConnected = true)
                val updated = room.copy(
                    player2 = updatedP2,
                    lastActiveTimestamp = System.currentTimeMillis()
                )
                rooms[cleanCode] = updated
                return Result.success(updated)
            }
            return Result.failure(IllegalStateException("Room $cleanCode is full (maximum 2 players)."))
        }

        val p2 = Player(
            id = playerId,
            username = cleanName,
            isHost = false,
            score = 0
        )
        val joinMsg = ChatMessage(
            senderId = "SYSTEM",
            senderName = "Arena Bot",
            text = "$cleanName joined the room! Battle ready.",
            isSystem = true
        )
        val updatedRoom = room.copy(
            player2 = p2,
            status = RoomStatus.IN_PROGRESS,
            chatMessages = room.chatMessages + joinMsg,
            lastActiveTimestamp = System.currentTimeMillis()
        )
        rooms[cleanCode] = updatedRoom
        return Result.success(updatedRoom)
    }

    fun getRoom(roomId: String): Room? {
        return rooms[roomId.trim().uppercase()]
    }

    @Synchronized
    fun submitChoice(roomId: String, playerId: String, choice: GameChoice): Result<Room> {
        val cleanCode = roomId.trim().uppercase()
        val room = rooms[cleanCode] ?: return Result.failure(NoSuchElementException("Room not found"))

        if (choice == GameChoice.NONE) {
            return Result.failure(IllegalArgumentException("Invalid choice"))
        }

        if (room.status != RoomStatus.IN_PROGRESS) {
            return Result.failure(IllegalStateException("Round is not currently in progress"))
        }

        val roomSecrets = secretChoices.getOrPut(cleanCode) { ConcurrentHashMap() }

        // Prevent changing choice once submitted in the current round
        if (roomSecrets.containsKey(playerId)) {
            return Result.failure(IllegalStateException("Choice already locked for this round"))
        }

        // Verify player is member of the room
        val isP1 = room.player1.id == playerId
        val isP2 = room.player2?.id == playerId

        if (!isP1 && !isP2) {
            return Result.failure(SecurityException("Unauthorized player"))
        }

        // Lock choice secretly on server side
        roomSecrets[playerId] = choice

        val updatedP1 = if (isP1) room.player1.copy(hasSubmitted = true) else room.player1
        val updatedP2 = if (isP2 && room.player2 != null) room.player2.copy(hasSubmitted = true) else room.player2

        // Check if both players have submitted
        val p1Choice = roomSecrets[room.player1.id]
        val p2Choice = room.player2?.let { roomSecrets[it.id] }

        if (p1Choice != null && p2Choice != null && updatedP2 != null) {
            // AUTHORITATIVE RESOLUTION
            val winner = evaluateWinner(p1Choice, p2Choice)
            val newP1Score = if (winner == Winner.PLAYER_ONE) updatedP1.score + 1 else updatedP1.score
            val newP2Score = if (winner == Winner.PLAYER_TWO) updatedP2.score + 1 else updatedP2.score
            val newDraws = if (winner == Winner.DRAW) room.draws + 1 else room.draws

            val outcomeText = when (winner) {
                Winner.PLAYER_ONE -> "${updatedP1.username} wins Round ${room.roundNumber}! (${p1Choice.displayName} beats ${p2Choice.displayName})"
                Winner.PLAYER_TWO -> "${updatedP2.username} wins Round ${room.roundNumber}! (${p2Choice.displayName} beats ${p1Choice.displayName})"
                Winner.DRAW -> "Round ${room.roundNumber} is a DRAW! Both chose ${p1Choice.displayName}."
                Winner.UNDECIDED -> ""
            }

            val resultMsg = ChatMessage(
                senderId = "SYSTEM",
                senderName = "Arena Arbiter",
                text = outcomeText,
                isSystem = true
            )

            val resolvedRoom = room.copy(
                player1 = updatedP1.copy(
                    score = newP1Score,
                    revealedChoice = p1Choice,
                    isReadyForNext = false
                ),
                player2 = updatedP2.copy(
                    score = newP2Score,
                    revealedChoice = p2Choice,
                    isReadyForNext = false
                ),
                draws = newDraws,
                lastWinner = winner,
                status = RoomStatus.ROUND_REVEAL,
                chatMessages = room.chatMessages + resultMsg,
                lastActiveTimestamp = System.currentTimeMillis()
            )
            rooms[cleanCode] = resolvedRoom
            return Result.success(resolvedRoom)
        } else {
            // Only one player has submitted so far.
            // DO NOT REVEAL THE CHOICE TO THE OTHER PLAYER!
            val pendingRoom = room.copy(
                player1 = updatedP1,
                player2 = updatedP2,
                lastActiveTimestamp = System.currentTimeMillis()
            )
            rooms[cleanCode] = pendingRoom
            return Result.success(pendingRoom)
        }
    }

    @Synchronized
    fun readyForNextRound(roomId: String, playerId: String): Result<Room> {
        val cleanCode = roomId.trim().uppercase()
        val room = rooms[cleanCode] ?: return Result.failure(NoSuchElementException("Room not found"))

        val isP1 = room.player1.id == playerId
        val isP2 = room.player2?.id == playerId

        if (!isP1 && !isP2) {
            return Result.failure(SecurityException("Unauthorized player"))
        }

        val updatedP1 = if (isP1) room.player1.copy(isReadyForNext = true) else room.player1
        val updatedP2 = if (isP2 && room.player2 != null) room.player2.copy(isReadyForNext = true) else room.player2

        // If both ready (or single ready in play-again flow)
        if (updatedP1.isReadyForNext && updatedP2?.isReadyForNext == true) {
            // Clear secret choices for the next round
            secretChoices[cleanCode]?.clear()

            val nextRoundMsg = ChatMessage(
                senderId = "SYSTEM",
                senderName = "Arena Arbiter",
                text = "Round ${room.roundNumber + 1} begins! Select your moves.",
                isSystem = true
            )

            val resetRoom = room.copy(
                player1 = updatedP1.copy(
                    hasSubmitted = false,
                    revealedChoice = GameChoice.NONE,
                    isReadyForNext = false
                ),
                player2 = updatedP2.copy(
                    hasSubmitted = false,
                    revealedChoice = GameChoice.NONE,
                    isReadyForNext = false
                ),
                roundNumber = room.roundNumber + 1,
                status = RoomStatus.IN_PROGRESS,
                lastWinner = Winner.UNDECIDED,
                chatMessages = room.chatMessages + nextRoundMsg,
                lastActiveTimestamp = System.currentTimeMillis()
            )
            rooms[cleanCode] = resetRoom
            return Result.success(resetRoom)
        } else {
            val waitingReadyRoom = room.copy(
                player1 = updatedP1,
                player2 = updatedP2,
                lastActiveTimestamp = System.currentTimeMillis()
            )
            rooms[cleanCode] = waitingReadyRoom
            return Result.success(waitingReadyRoom)
        }
    }

    @Synchronized
    fun sendChatMessage(roomId: String, playerId: String, text: String): Result<Room> {
        val cleanCode = roomId.trim().uppercase()
        val room = rooms[cleanCode] ?: return Result.failure(NoSuchElementException("Room not found"))

        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty"))
        }
        if (trimmedText.length > 500) {
            return Result.failure(IllegalArgumentException("Message exceeds 500 characters"))
        }

        // Simple rate limit: 250ms minimum between messages per player
        val now = System.currentTimeMillis()
        val lastTime = lastChatTimestamps[playerId] ?: 0L
        if (now - lastTime < 250) {
            return Result.failure(IllegalStateException("Please slow down (rate limited)"))
        }
        lastChatTimestamps[playerId] = now

        val sender = when (playerId) {
            room.player1.id -> room.player1
            room.player2?.id -> room.player2
            else -> return Result.failure(SecurityException("Unauthorized sender"))
        }

        val newMsg = ChatMessage(
            senderId = sender.id,
            senderName = sender.username,
            text = trimmedText,
            timestamp = now,
            isSystem = false
        )

        val updatedRoom = room.copy(
            chatMessages = room.chatMessages + newMsg,
            lastActiveTimestamp = now
        )
        rooms[cleanCode] = updatedRoom
        return Result.success(updatedRoom)
    }

    @Synchronized
    fun handleDisconnect(roomId: String, playerId: String): Room? {
        val cleanCode = roomId.trim().uppercase()
        val room = rooms[cleanCode] ?: return null

        val isP1 = room.player1.id == playerId
        val isP2 = room.player2?.id == playerId

        if (!isP1 && !isP2) return room

        val leaverName = if (isP1) room.player1.username else room.player2?.username ?: "Opponent"
        val leaveMsg = ChatMessage(
            senderId = "SYSTEM",
            senderName = "Arena Bot",
            text = "$leaverName temporarily disconnected.",
            isSystem = true
        )

        val updated = room.copy(
            player1 = if (isP1) room.player1.copy(isConnected = false) else room.player1,
            player2 = if (isP2 && room.player2 != null) room.player2.copy(isConnected = false) else room.player2,
            status = RoomStatus.OPPONENT_DISCONNECTED,
            chatMessages = room.chatMessages + leaveMsg,
            lastActiveTimestamp = System.currentTimeMillis()
        )
        rooms[cleanCode] = updated
        return updated
    }

    fun evaluateWinner(p1: GameChoice, p2: GameChoice): Winner {
        if (p1 == p2) return Winner.DRAW
        return when (p1) {
            GameChoice.ROCK -> if (p2 == GameChoice.SCISSORS) Winner.PLAYER_ONE else Winner.PLAYER_TWO
            GameChoice.PAPER -> if (p2 == GameChoice.ROCK) Winner.PLAYER_ONE else Winner.PLAYER_TWO
            GameChoice.SCISSORS -> if (p2 == GameChoice.PAPER) Winner.PLAYER_ONE else Winner.PLAYER_TWO
            GameChoice.NONE -> Winner.UNDECIDED
        }
    }
}
