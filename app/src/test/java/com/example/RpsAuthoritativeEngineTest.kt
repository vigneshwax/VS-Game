package com.example

import com.example.engine.RpsAuthoritativeEngine
import com.example.model.GameChoice
import com.example.model.RoomStatus
import com.example.model.Winner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RpsAuthoritativeEngineTest {

    private lateinit var engine: RpsAuthoritativeEngine

    @Before
    fun setup() {
        engine = RpsAuthoritativeEngine()
    }

    @Test
    fun testCreateRoom() {
        val res = engine.createRoom("StrikerAlice", "p1")
        assertTrue(res.isSuccess)
        val room = res.getOrThrow()
        assertTrue(room.roomId.startsWith("RPS-"))
        assertEquals("StrikerAlice", room.player1.username)
        assertEquals(RoomStatus.WAITING_FOR_OPPONENT, room.status)
        assertFalse(room.isFull)
    }

    @Test
    fun testJoinRoomAndRejectThirdPlayer() {
        val createRes = engine.createRoom("Alice", "p1")
        val room = createRes.getOrThrow()

        val joinRes = engine.joinRoom(room.roomId, "Bob", "p2")
        assertTrue(joinRes.isSuccess)
        val fullRoom = joinRes.getOrThrow()
        assertTrue(fullRoom.isFull)
        assertEquals(RoomStatus.IN_PROGRESS, fullRoom.status)

        val thirdRes = engine.joinRoom(room.roomId, "Charlie", "p3")
        assertTrue(thirdRes.isFailure)
    }

    @Test
    fun testCombatRules() {
        assertEquals(Winner.PLAYER_ONE, engine.evaluateWinner(GameChoice.ROCK, GameChoice.SCISSORS))
        assertEquals(Winner.PLAYER_ONE, engine.evaluateWinner(GameChoice.SCISSORS, GameChoice.PAPER))
        assertEquals(Winner.PLAYER_ONE, engine.evaluateWinner(GameChoice.PAPER, GameChoice.ROCK))
        assertEquals(Winner.PLAYER_TWO, engine.evaluateWinner(GameChoice.SCISSORS, GameChoice.ROCK))
        assertEquals(Winner.DRAW, engine.evaluateWinner(GameChoice.ROCK, GameChoice.ROCK))
        assertEquals(Winner.DRAW, engine.evaluateWinner(GameChoice.PAPER, GameChoice.PAPER))
    }

    @Test
    fun testSecrecyUntilBothSubmit() {
        val room = engine.createRoom("Alice", "p1").getOrThrow()
        engine.joinRoom(room.roomId, "Bob", "p2").getOrThrow()

        // Alice submits Rock
        val p1Sub = engine.submitChoice(room.roomId, "p1", GameChoice.ROCK).getOrThrow()
        assertEquals(RoomStatus.IN_PROGRESS, p1Sub.status)
        assertTrue(p1Sub.player1.hasSubmitted)
        // Choice must NOT be revealed yet!
        assertEquals(GameChoice.NONE, p1Sub.player1.revealedChoice)

        // Bob submits Scissors -> Triggers simultaneous reveal
        val p2Sub = engine.submitChoice(room.roomId, "p2", GameChoice.SCISSORS).getOrThrow()
        assertEquals(RoomStatus.ROUND_REVEAL, p2Sub.status)
        assertEquals(GameChoice.ROCK, p2Sub.player1.revealedChoice)
        assertEquals(GameChoice.SCISSORS, p2Sub.player2?.revealedChoice)
        assertEquals(Winner.PLAYER_ONE, p2Sub.lastWinner)
        assertEquals(1, p2Sub.player1.score)
        assertEquals(0, p2Sub.player2?.score)
    }

    @Test
    fun testLiveChatValidation() {
        val room = engine.createRoom("Alice", "p1").getOrThrow()
        engine.joinRoom(room.roomId, "Bob", "p2").getOrThrow()

        // Valid chat
        val chatRes = engine.sendChatMessage(room.roomId, "p1", "Hello challenger!")
        assertTrue(chatRes.isSuccess)

        // Empty chat rejected
        val emptyRes = engine.sendChatMessage(room.roomId, "p1", "   ")
        assertTrue(emptyRes.isFailure)
    }
}
