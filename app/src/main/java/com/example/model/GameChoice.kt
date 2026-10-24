package com.example.model

import androidx.annotation.DrawableRes
import com.example.R

enum class GameChoice(
    val displayName: String,
    val emoji: String,
    val beats: String,
    @DrawableRes val drawableResId: Int? = null
) {
    ROCK("Rock", "🪨", "Scissors", R.drawable.rock),
    PAPER("Paper", "📄", "Rock", R.drawable.paper),
    SCISSORS("Scissors", "✂️", "Paper", R.drawable.scissors),
    NONE("None", "❓", "", null);

    companion object {
        fun fromString(value: String): GameChoice {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: NONE
        }
    }
}

enum class RoundResultStatus {
    WAITING_FOR_MOVES,
    REVEALED,
    NEXT_ROUND_READY
}

enum class Winner {
    PLAYER_ONE,
    PLAYER_TWO,
    DRAW,
    UNDECIDED
}
