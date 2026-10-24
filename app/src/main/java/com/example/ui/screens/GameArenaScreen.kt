package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameChoice
import com.example.model.Room
import com.example.model.RoomStatus
import com.example.model.Winner
import com.example.ui.components.ChatPanel
import com.example.ui.components.ChoiceButton
import com.example.ui.components.FloatingChatOverlay
import com.example.ui.components.ScoreBoard
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.NeonRose
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.PaperColor
import com.example.ui.theme.RockColor
import com.example.ui.theme.ScissorsColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MatchMode

@Composable
fun GameArenaScreen(
    room: Room?,
    currentUserId: String,
    matchMode: MatchMode,
    activeSimulatorPlayer: Int,
    selectedChoice: GameChoice,
    simulatorP2Choice: GameChoice,
    chatInput: String,
    unreadChatCount: Int,
    onSwitchSimulatorPlayer: (Int) -> Unit,
    onSelectChoice: (GameChoice) -> Unit,
    onSubmitChoice: () -> Unit,
    onReadyNextRound: () -> Unit,
    onChatInputChange: (String) -> Unit,
    onSendChatMessage: () -> Unit,
    onLeaveArena: () -> Unit,
    modifier: Modifier = Modifier,
    isChatOpen: Boolean = false,
    onToggleChat: () -> Unit = {},
    arenaTab: Int = 0,
    onTabSelected: (Int) -> Unit = {}
) {
    BackHandler {
        onLeaveArena()
    }

    val isCurrentP1 = if (matchMode == MatchMode.DUEL_SIMULATOR) {
        activeSimulatorPlayer == 1
    } else {
        room?.player1?.id == currentUserId
    }

    val currentPlayer = if (isCurrentP1) room?.player1 else room?.player2
    val opponentPlayer = if (isCurrentP1) room?.player2 else room?.player1

    val currentChoice = if (matchMode == MatchMode.DUEL_SIMULATOR) {
        if (activeSimulatorPlayer == 1) selectedChoice else simulatorP2Choice
    } else {
        selectedChoice
    }

    val isRevealed = room?.status == RoomStatus.ROUND_REVEAL
    val hasMyPlayerSubmitted = currentPlayer?.hasSubmitted == true

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0F172A),
                        Color(0xFF090D16),
                        Color(0xFF06090F)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            ArenaTopBar(
                roomId = room?.roomId ?: "",
                matchMode = matchMode,
                activeSimulatorPlayer = activeSimulatorPlayer,
                onSwitchSimulatorPlayer = onSwitchSimulatorPlayer,
                onLeaveArena = onLeaveArena
            )

            // Responsive Full Screen Battle Arena (Removed TabRow, Clean Single Arena Stage)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                ) {
                    BattleArenaContent(
                        room = room,
                        isCurrentP1 = isCurrentP1,
                        currentPlayerChoice = currentChoice,
                        hasSubmitted = hasMyPlayerSubmitted,
                        isRevealed = isRevealed,
                        onSelectChoice = onSelectChoice,
                        onSubmitChoice = onSubmitChoice,
                        onReadyNextRound = onReadyNextRound
                    )
                }
            }
        }

        // Modern Responsive Floating Chat Bubble & Popup Window (24.dp from bottom-right)
        FloatingChatOverlay(
            messages = room?.chatMessages ?: emptyList(),
            currentUserId = currentPlayer?.id ?: currentUserId,
            chatInput = chatInput,
            unreadCount = unreadChatCount,
            onInputChange = onChatInputChange,
            onSendMessage = onSendChatMessage,
            title = if (room != null) "Room ${room.roomId} Chat" else "Live Arena Chat",
            isOnline = true,
            isOpen = isChatOpen,
            onToggleOpen = onToggleChat
        )
    }
}


@Composable
fun ArenaTopBar(
    roomId: String,
    matchMode: MatchMode,
    activeSimulatorPlayer: Int,
    onSwitchSimulatorPlayer: (Int) -> Unit,
    onLeaveArena: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onLeaveArena,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Leave Arena",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "RPS Arena",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Room $roomId",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeonCyan
                        )
                    }
                }

                // Match mode pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (matchMode == MatchMode.DUEL_SIMULATOR) Color(0xFF312E81) else Color(0xFF164E63)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (matchMode == MatchMode.DUEL_SIMULATOR) "DUEL TESTER" else "LIVE ONLINE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (matchMode == MatchMode.DUEL_SIMULATOR) NeonViolet else NeonCyan
                    )
                }
            }

            // In Duel Simulator: Active player switch bar
            if (matchMode == MatchMode.DUEL_SIMULATOR) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF131B2E))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Viewing as Player:",
                        fontSize = 11.sp,
                        color = TextMuted
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSwitchSimulatorPlayer(1) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeSimulatorPlayer == 1) NeonIndigo else DarkSurfaceElevated
                            ),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("switch_to_p1")
                        ) {
                            Text("P1 (Host)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onSwitchSimulatorPlayer(2) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeSimulatorPlayer == 2) NeonViolet else DarkSurfaceElevated
                            ),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("switch_to_p2")
                        ) {
                            Text("P2 (Challenger)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BattleArenaContent(
    room: Room?,
    isCurrentP1: Boolean,
    currentPlayerChoice: GameChoice,
    hasSubmitted: Boolean,
    isRevealed: Boolean,
    onSelectChoice: (GameChoice) -> Unit,
    onSubmitChoice: () -> Unit,
    onReadyNextRound: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Scoreboard
        if (room != null) {
            ScoreBoard(
                player1 = room.player1,
                player2 = room.player2,
                roundNumber = room.roundNumber,
                draws = room.draws,
                isCurrentP1 = isCurrentP1
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Battle Stage: Reveal vs In-Progress
        if (isRevealed && room != null) {
            RoundOutcomeCard(
                room = room,
                isCurrentP1 = isCurrentP1,
                onReadyNextRound = onReadyNextRound
            )
        } else {
            // Weapon Selection Arena
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (hasSubmitted) "Move Locked! Waiting for opponent..." else "Choose Your Weapon",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (hasSubmitted) NeonEmerald else TextPrimary
                    )

                    Text(
                        text = if (hasSubmitted) "Your choice remains hidden until both reveal." else "Rock beats Scissors, Scissors cuts Paper, Paper covers Rock.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                    )

                    // 3 Choice Cards Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChoiceButton(
                            choice = GameChoice.ROCK,
                            isSelected = currentPlayerChoice == GameChoice.ROCK,
                            isEnabled = !hasSubmitted,
                            onSelect = { onSelectChoice(GameChoice.ROCK) },
                            modifier = Modifier.weight(1f)
                        )

                        ChoiceButton(
                            choice = GameChoice.PAPER,
                            isSelected = currentPlayerChoice == GameChoice.PAPER,
                            isEnabled = !hasSubmitted,
                            onSelect = { onSelectChoice(GameChoice.PAPER) },
                            modifier = Modifier.weight(1f)
                        )

                        ChoiceButton(
                            choice = GameChoice.SCISSORS,
                            isSelected = currentPlayerChoice == GameChoice.SCISSORS,
                            isEnabled = !hasSubmitted,
                            onSelect = { onSelectChoice(GameChoice.SCISSORS) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Lock Weapon Button
                    Button(
                        onClick = onSubmitChoice,
                        enabled = !hasSubmitted && currentPlayerChoice != GameChoice.NONE,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("submit_choice_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonIndigo,
                            disabledContainerColor = Color(0xFF1E2843)
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasSubmitted) Icons.Default.CheckCircle else Icons.Default.HourglassTop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasSubmitted) "Weapon Locked & Hidden" else "Lock In ${if (currentPlayerChoice != GameChoice.NONE) currentPlayerChoice.displayName else "Choice"}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun RoundOutcomeCard(
    room: Room,
    isCurrentP1: Boolean,
    onReadyNextRound: () -> Unit
) {
    val p1Choice = room.player1.revealedChoice
    val p2Choice = room.player2?.revealedChoice ?: GameChoice.NONE

    val winner = room.lastWinner

    val isUserWinner = when {
        winner == Winner.DRAW -> false
        isCurrentP1 && winner == Winner.PLAYER_ONE -> true
        !isCurrentP1 && winner == Winner.PLAYER_TWO -> true
        else -> false
    }

    val outcomeTitle = when (winner) {
        Winner.DRAW -> "IT'S A DRAW!"
        Winner.PLAYER_ONE -> if (isCurrentP1) "VICTORY!" else "DEFEAT!"
        Winner.PLAYER_TWO -> if (!isCurrentP1) "VICTORY!" else "DEFEAT!"
        Winner.UNDECIDED -> "ROUND REVEAL"
    }

    val bannerColor = when {
        winner == Winner.DRAW -> NeonAmber
        isUserWinner -> NeonEmerald
        else -> NeonRose
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(2.dp, bannerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Outcome Header Banner
            Text(
                text = outcomeTitle,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = bannerColor,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            val winnerName = when (winner) {
                Winner.PLAYER_ONE -> "${room.player1.username} takes Round ${room.roundNumber}!"
                Winner.PLAYER_TWO -> "${room.player2?.username ?: "Player 2"} takes Round ${room.roundNumber}!"
                Winner.DRAW -> "Both chose matching weapons!"
                Winner.UNDECIDED -> ""
            }

            Text(
                text = winnerName,
                fontSize = 13.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Clash Graphic
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // P1 Weapon
                RevealedWeaponBox(
                    playerName = room.player1.username,
                    choice = p1Choice,
                    isWinner = winner == Winner.PLAYER_ONE
                )

                Text(
                    text = "VS",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonCyan
                )

                // P2 Weapon
                RevealedWeaponBox(
                    playerName = room.player2?.username ?: "Challenger",
                    choice = p2Choice,
                    isWinner = winner == Winner.PLAYER_TWO
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Play Again / Next Round button
            Button(
                onClick = onReadyNextRound,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("play_again_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Next Round (Play Again)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun RevealedWeaponBox(
    playerName: String,
    choice: GameChoice,
    isWinner: Boolean
) {
    val borderColor = if (isWinner) NeonEmerald else DarkSurfaceBorder

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = playerName,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isWinner) NeonEmerald else TextMuted
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isWinner) Color(0xFF132F24) else Color(0xFF1E2843))
                .border(if (isWinner) 2.5.dp else 1.dp, borderColor, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (choice.drawableResId != null) {
                Image(
                    painter = painterResource(id = choice.drawableResId),
                    contentDescription = "${choice.displayName} gesture revealed",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = choice.emoji,
                    fontSize = 36.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = choice.displayName.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isWinner) NeonEmerald else TextPrimary
        )
    }
}
