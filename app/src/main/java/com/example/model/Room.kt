package com.example.model

data class Player(
    val id: String,
    val username: String,
    val role: UserRole = UserRole.HUSBAND,
    val isHost: Boolean = false,
    val score: Int = 0,
    val hasSubmitted: Boolean = false,
    val revealedChoice: GameChoice = GameChoice.NONE,
    val isConnected: Boolean = true,
    val isReadyForNext: Boolean = false
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystem: Boolean = false
)

enum class RoomStatus {
    WAITING_FOR_OPPONENT,
    IN_PROGRESS,
    ROUND_REVEAL,
    OPPONENT_DISCONNECTED,
    ABANDONED
}

data class Room(
    val roomId: String,
    val player1: Player,
    val player2: Player? = null,
    val roundNumber: Int = 1,
    val draws: Int = 0,
    val status: RoomStatus = RoomStatus.WAITING_FOR_OPPONENT,
    val lastWinner: Winner = Winner.UNDECIDED,
    val chatMessages: List<ChatMessage> = emptyList(),
    val lastActiveTimestamp: Long = System.currentTimeMillis()
) {
    val isFull: Boolean get() = player2 != null
}
