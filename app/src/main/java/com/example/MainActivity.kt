package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.RulesDialog
import com.example.ui.screens.GameArenaScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.RPSArenaTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.RpsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RPSArenaTheme {
                val viewModel: RpsViewModel = viewModel()
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: RpsViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val selectedRole by viewModel.selectedRole.collectAsStateWithLifecycle()
    val appsScriptUrl by viewModel.appsScriptUrl.collectAsStateWithLifecycle()
    val currentRoom by viewModel.currentRoom.collectAsStateWithLifecycle()
    val matchMode by viewModel.matchMode.collectAsStateWithLifecycle()
    val myPlayerId by viewModel.myPlayerId.collectAsStateWithLifecycle()
    val activeSimulatorPlayer by viewModel.activeSimulatorPlayer.collectAsStateWithLifecycle()
    val selectedChoice by viewModel.selectedChoice.collectAsStateWithLifecycle()
    val simulatorP2Choice by viewModel.simulatorP2Choice.collectAsStateWithLifecycle()
    val chatInput by viewModel.chatInput.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val showRulesDialog by viewModel.showRulesDialog.collectAsStateWithLifecycle()
    val arenaTab by viewModel.arenaTab.collectAsStateWithLifecycle()
    val isChatOpen by viewModel.isChatOpen.collectAsStateWithLifecycle()
    val unreadChatCount by viewModel.unreadChatCount.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                AppScreen.HOME -> {
                    HomeScreen(
                        appsScriptUrl = appsScriptUrl,
                        onSaveScriptUrl = viewModel::saveScriptUrl,
                        errorMessage = errorMessage,
                        onSelectRole = viewModel::selectRole,
                        onStartDuelSimulator = viewModel::startDuelSimulator,
                        onShowRules = { viewModel.toggleRulesDialog(true) }
                    )
                }
                AppScreen.LOGIN -> {
                    LoginScreen(
                        role = selectedRole,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onLogin = { name, pin -> viewModel.login(name, pin) },
                        onBack = { viewModel.leaveRoom() }
                    )
                }
                AppScreen.LOBBY -> {
                    LobbyScreen(
                        room = currentRoom,
                        hostAddress = "Google Sheets Cloud",
                        onCopyRoomCode = viewModel::copyRoomCode,
                        onCopyFullAddress = viewModel::copyFullAddress,
                        onCopyInviteLink = viewModel::copyInviteLink,
                        onLeaveRoom = viewModel::leaveRoom
                    )
                }
                AppScreen.ARENA -> {
                    GameArenaScreen(
                        room = currentRoom,
                        currentUserId = myPlayerId,
                        matchMode = matchMode,
                        activeSimulatorPlayer = activeSimulatorPlayer,
                        selectedChoice = selectedChoice,
                        simulatorP2Choice = simulatorP2Choice,
                        chatInput = chatInput,
                        unreadChatCount = unreadChatCount,
                        isChatOpen = isChatOpen,
                        onToggleChat = viewModel::toggleChat,
                        arenaTab = arenaTab,
                        onTabSelected = viewModel::setArenaTab,
                        onSwitchSimulatorPlayer = viewModel::switchSimulatorPlayer,
                        onSelectChoice = viewModel::selectChoice,
                        onSubmitChoice = viewModel::submitCurrentChoice,
                        onReadyNextRound = viewModel::readyForNextRound,
                        onChatInputChange = viewModel::setChatInput,
                        onSendChatMessage = viewModel::sendChatMessage,
                        onLeaveArena = viewModel::leaveRoom
                    )
                }
            }

            if (showRulesDialog) {
                RulesDialog(onDismiss = { viewModel.toggleRulesDialog(false) })
            }
        }
    }
}
