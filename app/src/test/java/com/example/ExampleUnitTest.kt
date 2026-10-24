package com.example

import com.example.model.UserRole
import com.example.network.GoogleSheetsRpsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testSha256PasswordHashing() {
        val pin1 = "1234"
        val hash1 = GoogleSheetsRpsRepository.hashPin(pin1)
        val hash2 = GoogleSheetsRpsRepository.hashPin(pin1)
        val hashDifferent = GoogleSheetsRpsRepository.hashPin("5678")

        // Deterministic 64-char hex string
        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hashDifferent)
    }

    @Test
    fun testUserRoles() {
        assertEquals("Husband", UserRole.HUSBAND.title)
        assertEquals("Wife", UserRole.WIFE.title)
        assertEquals(0xFF06B6D4, UserRole.HUSBAND.accentHex)
        assertEquals(0xFFEC4899, UserRole.WIFE.accentHex)
    }

    @Test
    fun testSessionSyncResultClasses() {
        val unchanged = com.example.network.SessionSyncResult.Unchanged("v1.0")
        assertEquals("v1.0", unchanged.version)
    }
}
