package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameChoice
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.NeonRose
import com.example.ui.theme.PaperColor
import com.example.ui.theme.RockColor
import com.example.ui.theme.ScissorsColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun ChoiceButton(
    choice: GameChoice,
    isSelected: Boolean,
    isEnabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "choice_scale"
    )

    val (choiceColor, badgeBg) = when (choice) {
        GameChoice.ROCK -> RockColor to Color(0x33EF4444)
        GameChoice.PAPER -> PaperColor to Color(0x338B5CF6)
        GameChoice.SCISSORS -> ScissorsColor to Color(0x3310B981)
        GameChoice.NONE -> NeonIndigo to DarkSurfaceElevated
    }

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) choiceColor else DarkSurfaceBorder.copy(alpha = 0.6f),
        label = "choice_border"
    )

    val borderWidth = if (isSelected) 2.5.dp else 1.dp
    val elevation = if (isSelected) 12.dp else 2.dp

    Card(
        modifier = modifier
            .scale(scale)
            .testTag("choice_${choice.name.lowercase()}")
            .clip(RoundedCornerShape(18.dp))
            .semantics {
                contentDescription = "${choice.displayName} gesture card, beats ${choice.beats}. ${if (isSelected) "Selected" else "Tap to select"}."
            }
            .clickable(enabled = isEnabled) { onSelect() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E2843) else Color(0xFF0F172A)
        ),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Hand gesture image container with subtle glow & rounded corners
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) {
                            Brush.radialGradient(
                                listOf(choiceColor.copy(alpha = 0.35f), Color(0xFF0D1322))
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(Color(0xFF161F33), Color(0xFF0B101D))
                            )
                        }
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 0.5.dp,
                        color = if (isSelected) choiceColor else DarkSurfaceBorder,
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (choice.drawableResId != null) {
                    Image(
                        painter = painterResource(id = choice.drawableResId),
                        contentDescription = "${choice.displayName} hand gesture",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = choice.emoji,
                        fontSize = 32.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = choice.displayName.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
                color = if (isSelected) choiceColor else TextPrimary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Beats ${choice.beats}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) choiceColor.copy(alpha = 0.9f) else TextMuted,
                fontSize = 10.sp
            )
        }
    }
}
