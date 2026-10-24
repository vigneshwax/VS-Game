package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.NeonRose
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TicTacToeDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        TicTacToeGame(onClose = onDismiss)
    }
}

@Composable
fun TicTacToeGame(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    var board by remember { mutableStateOf(List(9) { "" }) }
    var isHusbandTurn by remember { mutableStateOf(true) }
    var husbandScore by remember { mutableIntStateOf(0) }
    var wifeScore by remember { mutableIntStateOf(0) }
    var draws by remember { mutableIntStateOf(0) }

    fun checkWinner(b: List<String>): String? {
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // Rows
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // Cols
            listOf(0, 4, 8), listOf(2, 4, 6)             // Diagonals
        )
        for (line in lines) {
            val (a, bIdx, c) = line
            if (b[a].isNotEmpty() && b[a] == b[bIdx] && b[a] == b[c]) {
                return b[a]
            }
        }
        if (b.all { it.isNotEmpty() }) return "DRAW"
        return null
    }

    val winner = checkWinner(board)

    fun onCellClick(index: Int) {
        if (board[index].isNotEmpty() || winner != null) return
        val newBoard = board.toMutableList()
        val mark = if (isHusbandTurn) "X" else "O"
        newBoard[index] = mark
        board = newBoard
        val w = checkWinner(newBoard)
        if (w == "X") husbandScore++
        else if (w == "O") wifeScore++
        else if (w == "DRAW") draws++
        isHusbandTurn = !isHusbandTurn
    }

    fun resetBoard() {
        board = List(9) { "" }
        isHusbandTurn = true
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(NeonIndigo, NeonCyan.copy(alpha = 0.5f)))),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(NeonIndigo, NeonViolet))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("❌", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "XO Clash: Couple Duel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tic Tac Toe • 2-Player Board",
                            fontSize = 11.sp,
                            color = NeonCyan
                        )
                    }
                }

                if (onClose != null) {
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scoreboard
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceElevated)
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👨 Husband (X)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonIndigo)
                    Text("$husbandScore", fontSize = 20.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Draws", fontSize = 10.sp, color = TextMuted)
                    Text("$draws", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👩 Wife (O)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonRose)
                    Text("$wifeScore", fontSize = 20.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Turn or Winner banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when (winner) {
                            "X" -> NeonIndigo.copy(alpha = 0.25f)
                            "O" -> NeonRose.copy(alpha = 0.25f)
                            "DRAW" -> NeonAmber.copy(alpha = 0.25f)
                            else -> DarkSurfaceElevated
                        }
                    )
                    .border(
                        1.dp,
                        when (winner) {
                            "X" -> NeonIndigo
                            "O" -> NeonRose
                            "DRAW" -> NeonAmber
                            else -> DarkSurfaceBorder
                        },
                        RoundedCornerShape(10.dp)
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (winner) {
                        "X" -> "🎉 HUSBAND WINS THIS ROUND!"
                        "O" -> "🎉 WIFE WINS THIS ROUND!"
                        "DRAW" -> "🤝 IT'S A DRAW!"
                        else -> if (isHusbandTurn) "Husband's Turn (X)" else "Wife's Turn (O)"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = when (winner) {
                        "X" -> NeonIndigo
                        "O" -> NeonRose
                        "DRAW" -> NeonAmber
                        else -> if (isHusbandTurn) NeonIndigo else NeonRose
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3x3 Grid
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0C101C))
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (row in 0..2) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (col in 0..2) {
                                val idx = row * 3 + col
                                val mark = board[idx]
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (mark.isEmpty()) DarkSurfaceElevated else Color(0xFF1E2843))
                                        .border(
                                            1.dp,
                                            if (mark == "X") NeonIndigo.copy(alpha = 0.6f)
                                            else if (mark == "O") NeonRose.copy(alpha = 0.6f)
                                            else DarkSurfaceBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable(enabled = mark.isEmpty() && winner == null) {
                                            onCellClick(idx)
                                        }
                                        .testTag("xo_cell_$idx"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (mark.isNotEmpty()) {
                                        Text(
                                            text = mark,
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (mark == "X") NeonIndigo else NeonRose
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Reset & Play Again Button
            Button(
                onClick = { resetBoard() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("xo_reset_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (winner != null) NeonIndigo else DarkSurfaceElevated
                )
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (winner != null) "Play Next Round" else "Reset Board",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
