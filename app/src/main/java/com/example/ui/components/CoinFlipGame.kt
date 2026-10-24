package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CoinFlipDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        CoinFlipGame(onClose = onDismiss)
    }
}

@Composable
fun CoinFlipGame(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val chores = listOf(
        "Who washes the dishes tonight?",
        "Who picks what we eat for dinner?",
        "Who decides on the movie?",
        "Who makes morning coffee / breakfast?",
        "Who walks the dog / takes out the trash?",
        "Who gets control of the TV remote?"
    )

    var selectedChoreIndex by remember { mutableIntStateOf(0) }
    var rotationY by remember { mutableStateOf(0f) }
    var isFlipping by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var husbandWins by remember { mutableIntStateOf(0) }
    var wifeWins by remember { mutableIntStateOf(0) }

    val animatedRotation by animateFloatAsState(
        targetValue = rotationY,
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        finishedListener = {
            isFlipping = false
        },
        label = "coin_rotation"
    )

    fun flipCoin() {
        if (isFlipping) return
        isFlipping = true
        val isHusband = Math.random() < 0.5
        val flips = (4..6).random() * 360f
        val finalAngle = if (isHusband) flips else flips + 180f
        rotationY += finalAngle
        val winnerStr = if (isHusband) "HUSBAND" else "WIFE"
        result = winnerStr
        if (isHusband) husbandWins++ else wifeWins++
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(NeonAmber, NeonCyan.copy(alpha = 0.5f)))),
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
                            .background(Brush.linearGradient(listOf(NeonAmber, Color(0xFFD97706)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🪙", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Couple Decision Coin",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Quick chore decider",
                            fontSize = 11.sp,
                            color = NeonAmber
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

            // Chore Selector Dropdown / Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        selectedChoreIndex = (selectedChoreIndex + 1) % chores.size
                    }
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DECISION QUESTION (TAP TO CHANGE)", fontSize = 10.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = chores[selectedChoreIndex],
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3D Animated Coin
            val isHeadsVisible = ((animatedRotation.toInt() % 360) in 0..90) || ((animatedRotation.toInt() % 360) in 270..360)

            Box(
                modifier = Modifier
                    .size(130.dp)
                    .graphicsLayer {
                        this.rotationY = animatedRotation
                        cameraDistance = 12f * density
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            if (isHeadsVisible) {
                                listOf(Color(0xFF6366F1), Color(0xFF3730A3), Color(0xFF1E1B4B))
                            } else {
                                listOf(Color(0xFFEC4899), Color(0xFFBE185D), Color(0xFF831843))
                            }
                        )
                    )
                    .border(
                        3.dp,
                        Brush.linearGradient(
                            listOf(NeonAmber, Color(0xFFFDE047), NeonAmber)
                        ),
                        CircleShape
                    )
                    .clickable(enabled = !isFlipping) { flipCoin() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isHeadsVisible) "👨" else "👩",
                        fontSize = 42.sp
                    )
                    Text(
                        text = if (isHeadsVisible) "HUSBAND" else "WIFE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Result Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (result == "HUSBAND") NeonIndigo.copy(alpha = 0.2f)
                        else if (result == "WIFE") NeonRose.copy(alpha = 0.2f)
                        else DarkSurfaceElevated
                    )
                    .border(
                        1.dp,
                        if (result == "HUSBAND") NeonIndigo
                        else if (result == "WIFE") NeonRose
                        else DarkSurfaceBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 10.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isFlipping) {
                    Text("Flipping coin through the air...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonAmber)
                } else if (result != null) {
                    Text(
                        text = "🏆 Winner: $result!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = if (result == "HUSBAND") NeonIndigo else NeonRose
                    )
                } else {
                    Text("Tap the coin or button below to flip!", fontSize = 12.sp, color = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Score Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text("👨 Husband: $husbandWins wins", fontSize = 11.sp, color = NeonIndigo, fontWeight = FontWeight.Bold)
                Text("👩 Wife: $wifeWins wins", fontSize = 11.sp, color = NeonRose, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { flipCoin() },
                enabled = !isFlipping,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("flip_coin_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonAmber)
            ) {
                Text(
                    text = if (isFlipping) "Flipping..." else "🪙 Flip the Coin",
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    fontSize = 14.sp
                )
            }
        }
    }
}
