package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Player
import com.example.model.UserRole
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
fun ScoreBoard(
    player1: Player,
    player2: Player?,
    roundNumber: Int,
    draws: Int,
    isCurrentP1: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header with Round Number and Draw count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF312E81))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "ROUND $roundNumber",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = NeonCyan
                    )
                }

                Text(
                    text = "Draws: $draws",
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Players comparison row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Player 1 Card (Husband)
                val p1Color = if (player1.role == UserRole.HUSBAND) NeonCyan else NeonIndigo
                PlayerScoreItem(
                    player = player1,
                    isYou = isCurrentP1,
                    tagColor = p1Color,
                    modifier = Modifier.weight(1f)
                )

                // VS Divider
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "VS",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = NeonAmber
                    )
                }

                // Player 2 Card (Wife)
                val p2Color = if (player2?.role == UserRole.WIFE) NeonRose else NeonViolet
                PlayerScoreItem(
                    player = player2,
                    isYou = !isCurrentP1 && player2 != null,
                    tagColor = p2Color,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun PlayerScoreItem(
    player: Player?,
    isYou: Boolean,
    tagColor: Color,
    modifier: Modifier = Modifier
) {
    val roleTitle = when (player?.role) {
        UserRole.HUSBAND -> "HUSBAND"
        UserRole.WIFE -> "WIFE"
        else -> if (player?.isHost == true) "HOST" else "PLAYER"
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF131B2E))
            .border(
                1.dp,
                if (isYou) tagColor.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Online status indicator dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (player?.isConnected == true) NeonEmerald else Color.Gray)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = player?.username ?: "Waiting...",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (player != null) TextPrimary else TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Role badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(tagColor.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(
                text = if (isYou) "YOU • $roleTitle" else roleTitle,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = tagColor
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "${player?.score ?: 0}",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = tagColor
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Submission status pill
        val isSubmitted = player?.hasSubmitted == true
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSubmitted) Color(0x3310B981) else Color(0x33F59E0B))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isSubmitted) Icons.Default.Check else Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = if (isSubmitted) NeonEmerald else NeonAmber,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (isSubmitted) "Locked" else "Thinking",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSubmitted) NeonEmerald else NeonAmber
                )
            }
        }
    }
}
