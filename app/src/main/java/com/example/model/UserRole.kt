package com.example.model

enum class UserRole(
    val title: String,
    val emoji: String,
    val subtitle: String,
    val accentHex: Long
) {
    HUSBAND(
        title = "Husband",
        emoji = "👑",
        subtitle = "The Strategic Defender",
        accentHex = 0xFF06B6D4 // Neon Cyan
    ),
    WIFE(
        title = "Wife",
        emoji = "✨",
        subtitle = "The Intuitive Mastermind",
        accentHex = 0xFFEC4899 // Neon Rose / Pink
    );

    companion object {
        fun fromString(value: String): UserRole {
            return entries.find { it.name.equals(value, ignoreCase = true) || it.title.equals(value, ignoreCase = true) }
                ?: HUSBAND
        }
    }
}
