package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChatMessage
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.NeonRose
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FloatingChatOverlay(
    messages: List<ChatMessage>,
    currentUserId: String,
    chatInput: String,
    unreadCount: Int,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Live Arena Chat",
    isOnline: Boolean = true,
    isOpen: Boolean = false,
    onToggleOpen: () -> Unit = {}
) {
    var isMinimized by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto-scroll when messages update while open
    LaunchedEffect(messages.size, isOpen) {
        if (isOpen && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        // Floating Popup Chat Window
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(animationSpec = tween(220)) +
                    scaleIn(initialScale = 0.88f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut(animationSpec = tween(180)) +
                    scaleOut(targetScale = 0.88f) +
                    slideOutVertically(targetOffsetY = { it / 3 }),
            modifier = Modifier
                .padding(end = 16.dp, bottom = 88.dp)
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth(0.92f)
                    .then(
                        if (isMinimized) Modifier.height(60.dp)
                        else Modifier.heightIn(min = 360.dp, max = 500.dp)
                    )
                    .shadow(elevation = 20.dp, shape = RoundedCornerShape(22.dp))
                    .testTag("floating_chat_popup"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(
                    1.5.dp,
                    Brush.linearGradient(
                        listOf(NeonIndigo, NeonCyan.copy(alpha = 0.6f))
                    )
                )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceElevated)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .clickable(enabled = isMinimized) { isMinimized = false },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(NeonIndigo, NeonCyan))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubble,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = title,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 1.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isOnline) NeonEmerald else NeonRose)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (isOnline) "Online • Real-Time" else "Offline",
                                        color = if (isOnline) NeonEmerald else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Minimize and Close Action Buttons
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isMinimized = !isMinimized },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("minimize_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = if (isMinimized) "Expand Chat" else "Minimize Chat",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = onToggleOpen,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("close_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Chat",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (!isMinimized) {
                        // Scrollable Conversation Area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(0xFF0C101C))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Welcome Message Banner
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = NeonIndigo.copy(alpha = 0.12f)
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            NeonIndigo.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = NeonCyan,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Welcome to RPS Arena Chat!",
                                                    color = NeonCyan,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Coordinate moves, discuss strategy, or cheer your player.",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                if (messages.isEmpty() || messages.all { it.isSystem }) {
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "No match messages yet.",
                                                color = TextMuted,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "Send a message below to start chatting!",
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                items(messages, key = { it.id }) { msg ->
                                    if (msg.isSystem) {
                                        SystemMessageBubble(msg)
                                    } else {
                                        val isMe = msg.senderId == currentUserId
                                        ChatMessageBubble(msg, isMe)
                                    }
                                }
                            }
                        }

                        // Message Input Field and Send Button
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceElevated)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = chatInput,
                                    onValueChange = onInputChange,
                                    placeholder = {
                                        Text("Type your message...", color = TextMuted, fontSize = 13.sp)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("chat_input_field"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonIndigo,
                                        unfocusedBorderColor = DarkSurfaceBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        cursorColor = NeonCyan,
                                        focusedContainerColor = DarkSurface,
                                        unfocusedContainerColor = DarkSurface
                                    ),
                                    singleLine = false,
                                    maxLines = 3,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = {
                                        if (chatInput.isNotBlank()) onSendMessage()
                                    })
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = onSendMessage,
                                    enabled = chatInput.isNotBlank() && chatInput.length <= 500,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .testTag("send_chat_button")
                                        .clip(CircleShape)
                                        .background(
                                            if (chatInput.isNotBlank()) NeonIndigo else Color(0xFF283354)
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send Message",
                                        tint = if (chatInput.isNotBlank()) Color.White else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Character count indicator
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp, end = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                val count = chatInput.length
                                Text(
                                    text = "$count/500",
                                    fontSize = 10.sp,
                                    color = if (count > 480) NeonRose else TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // Circular Floating Chat Bubble (24.dp from right and bottom)
        Box(
            modifier = Modifier.padding(end = 24.dp, bottom = 24.dp)
        ) {
            val scale by animateFloatAsState(
                targetValue = if (isOpen) 1.05f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "bubble_scale"
            )

            Surface(
                modifier = Modifier
                    .size(56.dp)
                    .scale(scale)
                    .shadow(elevation = 14.dp, shape = CircleShape)
                    .testTag("floating_chat_bubble")
                    .semantics {
                        contentDescription = if (isOpen) "Close Live Chat" else "Open Live Chat"
                    }
                    .clickable { onToggleOpen() },
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(
                    2.dp,
                    if (isOpen) NeonCyan else NeonIndigo.copy(alpha = 0.8f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF6366F1), Color(0xFF4338CA))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    BadgedBox(
                        badge = {
                            if (!isOpen && unreadCount > 0) {
                                Badge(
                                    containerColor = NeonRose,
                                    contentColor = Color.White,
                                    modifier = Modifier.padding(top = 2.dp, end = 2.dp)
                                ) {
                                    Text(
                                        text = if (unreadCount > 99) "99+" else "$unreadCount",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isOpen) Icons.Default.Close else Icons.Default.ChatBubble,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
