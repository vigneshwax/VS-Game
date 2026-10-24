package com.example.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.RpsAuthoritativeEngine
import com.example.model.ChatMessage
import com.example.model.GameChoice
import com.example.model.Player
import com.example.model.Room
import com.example.model.RoomStatus
import com.example.model.UserRole
import com.example.network.AuthUser
import com.example.network.GoogleSheetsRpsRepository
import com.example.network.SessionSyncResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,       // Role Selection (Husband / Wife)
    LOGIN,      // Husband or Wife Login
    LOBBY,      // Couple Session Lobby
    ARENA       // Battle Arena + Shared Chat
}

enum class MatchMode {
    COUPLE_SHEETS,
    DUEL_SIMULATOR
}

class RpsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GoogleSheetsRpsRepository(application)
    private val localEngine = RpsAuthoritativeEngine()

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedRole = MutableStateFlow(UserRole.HUSBAND)
    val selectedRole: StateFlow<UserRole> = _selectedRole.asStateFlow()

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    private val _matchMode = MutableStateFlow(MatchMode.COUPLE_SHEETS)
    val matchMode: StateFlow<MatchMode> = _matchMode.asStateFlow()

    private val _appsScriptUrl = MutableStateFlow(repository.getScriptUrl())
    val appsScriptUrl: StateFlow<String> = _appsScriptUrl.asStateFlow()

    private val _currentRoom = MutableStateFlow<Room?>(null)
    val currentRoom: StateFlow<Room?> = _currentRoom.asStateFlow()

    private val _myPlayerId = MutableStateFlow("usr_husband")
    val myPlayerId: StateFlow<String> = _myPlayerId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _showRulesDialog = MutableStateFlow(false)
    val showRulesDialog: StateFlow<Boolean> = _showRulesDialog.asStateFlow()

    // Arena tabs on mobile
    private val _arenaTab = MutableStateFlow(0)
    val arenaTab: StateFlow<Int> = _arenaTab.asStateFlow()

    private val _isChatOpen = MutableStateFlow(false)
    val isChatOpen: StateFlow<Boolean> = _isChatOpen.asStateFlow()

    private val _unreadChatCount = MutableStateFlow(0)
    val unreadChatCount: StateFlow<Int> = _unreadChatCount.asStateFlow()

    // Weapon choice
    private val _selectedChoice = MutableStateFlow(GameChoice.NONE)
    val selectedChoice: StateFlow<GameChoice> = _selectedChoice.asStateFlow()

    // Simulator mode choice
    private val _simulatorP2Choice = MutableStateFlow(GameChoice.NONE)
    val simulatorP2Choice: StateFlow<GameChoice> = _simulatorP2Choice.asStateFlow()

    private val _activeSimulatorPlayer = MutableStateFlow(1)
    val activeSimulatorPlayer: StateFlow<Int> = _activeSimulatorPlayer.asStateFlow()

    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput.asStateFlow()

    private var pollingJob: Job? = null
    private var lastSeenMessageCount = 0
    private var lastStateVersion: String? = null
    private var consecutiveErrors = 0

    fun saveScriptUrl(url: String) {
        repository.setScriptUrl(url)
        _appsScriptUrl.value = url
        lastStateVersion = null
        consecutiveErrors = 0
        _toastMessage.value = if (url.isNotBlank()) "Google Sheets Web App connected!" else "Using local couple engine."
    }

    fun selectRole(role: UserRole) {
        _selectedRole.value = role
        _errorMessage.value = null
        _currentScreen.value = AppScreen.LOGIN
    }

    fun login(displayName: String, pin: String) {
        val role = _selectedRole.value
        _isLoading.value = true
        _errorMessage.value = null
        lastStateVersion = null
        consecutiveErrors = 0

        viewModelScope.launch {
            val authRes = repository.login(displayName, role, pin)
            authRes.onSuccess { user ->
                _currentUser.value = user
                _myPlayerId.value = user.userId
                _matchMode.value = MatchMode.COUPLE_SHEETS

                // Connect to or create shared session
                val sessionRes = if (role == UserRole.HUSBAND) {
                    repository.getOrCreateSession(user.userId, role)
                } else {
                    repository.joinSession("", user.userId, role)
                }

                sessionRes.onSuccess { room ->
                    _currentRoom.value = room
                    _isLoading.value = false
                    _currentScreen.value = if (room.isFull) AppScreen.ARENA else AppScreen.LOBBY
                    startPolling(room.roomId, user.userId)
                }.onFailure { err ->
                    _isLoading.value = false
                    _errorMessage.value = err.message ?: "Failed to connect to couple session."
                }
            }.onFailure { err ->
                _isLoading.value = false
                _errorMessage.value = err.message ?: "Authentication failed."
            }
        }
    }

    private fun startPolling(sessionId: String, userId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                // Adaptive delay calculation based on session state and network conditions
                val currentRoomObj = _currentRoom.value
                val isCombatActive = currentRoomObj?.status == RoomStatus.IN_PROGRESS &&
                    (currentRoomObj.player1.hasSubmitted.not() || currentRoomObj.player2?.hasSubmitted?.not() == true)

                val adaptiveDelayMs = when {
                    consecutiveErrors > 0 -> minOf(7000L, 1400L * (1 shl minOf(consecutiveErrors, 3)))
                    isCombatActive -> 1200L // Active combat: fast 1.2s sync
                    currentRoomObj?.status == RoomStatus.ROUND_REVEAL -> 2800L // Revealing: relax
                    currentRoomObj?.status == RoomStatus.WAITING_FOR_OPPONENT -> 2000L // In lobby: 2s
                    else -> 1800L
                }

                delay(adaptiveDelayMs)

                if (_matchMode.value == MatchMode.COUPLE_SHEETS && _currentRoom.value != null) {
                    val res = repository.syncSessionState(sessionId, userId, lastStateVersion)
                    res.onSuccess { syncResult ->
                        consecutiveErrors = 0
                        when (syncResult) {
                            is SessionSyncResult.Updated -> {
                                lastStateVersion = syncResult.version
                                val updated = syncResult.room
                                _currentRoom.value = updated
                                updateUnreadChat(updated)
                                if (updated.isFull && _currentScreen.value == AppScreen.LOBBY) {
                                    _currentScreen.value = AppScreen.ARENA
                                }
                            }
                            is SessionSyncResult.Unchanged -> {
                                lastStateVersion = syncResult.version
                                // State hasn't changed on server: skip updating _currentRoom to save battery & CPU
                            }
                        }
                    }.onFailure {
                        consecutiveErrors++
                    }
                }
            }
        }
    }

    private fun updateUnreadChat(room: Room) {
        val totalMsgs = room.chatMessages.size
        if (!_isChatOpen.value && totalMsgs > lastSeenMessageCount) {
            _unreadChatCount.value += (totalMsgs - lastSeenMessageCount)
        } else if (_isChatOpen.value) {
            _unreadChatCount.value = 0
        }
        lastSeenMessageCount = totalMsgs
    }

    fun toggleChat() {
        setChatOpen(!_isChatOpen.value)
    }

    fun setChatOpen(open: Boolean) {
        _isChatOpen.value = open
        if (open) {
            _unreadChatCount.value = 0
            _currentRoom.value?.let { lastSeenMessageCount = it.chatMessages.size }
        }
    }

    fun setArenaTab(tab: Int) {
        _arenaTab.value = tab
        if (tab == 1) {
            setChatOpen(true)
        }
    }

    fun setChatInput(text: String) {
        if (text.length <= 500) {
            _chatInput.value = text
        }
    }

    fun toggleRulesDialog(show: Boolean) {
        _showRulesDialog.value = show
    }

    fun selectChoice(choice: GameChoice) {
        if (_matchMode.value == MatchMode.DUEL_SIMULATOR) {
            if (_activeSimulatorPlayer.value == 1) {
                _selectedChoice.value = choice
            } else {
                _simulatorP2Choice.value = choice
            }
        } else {
            _selectedChoice.value = choice
        }
    }

    fun switchSimulatorPlayer(playerNum: Int) {
        _activeSimulatorPlayer.value = playerNum
    }

    fun startDuelSimulator() {
        _matchMode.value = MatchMode.DUEL_SIMULATOR
        _selectedRole.value = UserRole.HUSBAND
        val user = AuthUser("usr_husband_sim", "Husband", UserRole.HUSBAND, "token")
        _currentUser.value = user
        _myPlayerId.value = user.userId

        val roomRes = localEngine.createRoom("Husband", user.userId)
        roomRes.onSuccess { room ->
            val p2Res = localEngine.joinRoom(room.roomId, "Wife", "usr_wife_sim")
            p2Res.onSuccess { completeRoom ->
                val husbandPlayer = completeRoom.player1.copy(role = UserRole.HUSBAND)
                val wifePlayer = completeRoom.player2?.copy(role = UserRole.WIFE)
                val coupleRoom = completeRoom.copy(
                    roomId = "COUPLE-SIM",
                    player1 = husbandPlayer,
                    player2 = wifePlayer
                )
                _currentRoom.value = coupleRoom
                _activeSimulatorPlayer.value = 1
                _selectedChoice.value = GameChoice.NONE
                _simulatorP2Choice.value = GameChoice.NONE
                _currentScreen.value = AppScreen.ARENA
                _toastMessage.value = "Couple Duel Simulator ready!"
            }
        }
    }

    fun submitCurrentChoice() {
        val room = _currentRoom.value ?: return

        if (_matchMode.value == MatchMode.DUEL_SIMULATOR) {
            val isP1Active = _activeSimulatorPlayer.value == 1
            val choice = if (isP1Active) _selectedChoice.value else _simulatorP2Choice.value
            val pId = if (isP1Active) room.player1.id else (room.player2?.id ?: "usr_wife_sim")

            if (choice == GameChoice.NONE) {
                _errorMessage.value = "Please select Rock, Paper, or Scissors"
                return
            }

            val res = localEngine.submitChoice(room.roomId, pId, choice)
            res.onSuccess { updated ->
                _currentRoom.value = updated.copy(
                    player1 = updated.player1.copy(role = UserRole.HUSBAND),
                    player2 = updated.player2?.copy(role = UserRole.WIFE)
                )
                updateUnreadChat(updated)
                if (updated.status == RoomStatus.ROUND_REVEAL) {
                    _selectedChoice.value = GameChoice.NONE
                    _simulatorP2Choice.value = GameChoice.NONE
                }
            }
        } else {
            val user = _currentUser.value ?: return
            val choice = _selectedChoice.value
            if (choice == GameChoice.NONE) {
                _errorMessage.value = "Please select a move"
                return
            }

            // OPTIMISTIC UPDATE: Mark local player's move as locked immediately (Zero UI Lag)
            val isP1 = room.player1.id == user.userId
            val optimisticP1 = if (isP1) room.player1.copy(hasSubmitted = true) else room.player1
            val optimisticP2 = if (!isP1) room.player2?.copy(hasSubmitted = true) else room.player2
            _currentRoom.value = room.copy(player1 = optimisticP1, player2 = optimisticP2)

            viewModelScope.launch {
                val res = repository.submitMove(room.roomId, user.userId, room.roundNumber, choice)
                res.onSuccess { updated ->
                    _currentRoom.value = updated
                    updateUnreadChat(updated)
                    if (updated.status == RoomStatus.ROUND_REVEAL) {
                        _selectedChoice.value = GameChoice.NONE
                    }
                }.onFailure { err ->
                    // Revert optimistic submission on persistent failure
                    _currentRoom.value = room
                    _errorMessage.value = err.message ?: "Move submission failed. Please try again."
                }
            }
        }
    }

    fun readyForNextRound() {
        val room = _currentRoom.value ?: return
        if (_matchMode.value == MatchMode.DUEL_SIMULATOR) {
            val res1 = localEngine.readyForNextRound(room.roomId, room.player1.id)
            val res2 = localEngine.readyForNextRound(room.roomId, room.player2?.id ?: "usr_wife_sim")
            val updated = res2.getOrDefault(res1.getOrDefault(room))
            _currentRoom.value = updated.copy(
                player1 = updated.player1.copy(role = UserRole.HUSBAND),
                player2 = updated.player2?.copy(role = UserRole.WIFE)
            )
            _selectedChoice.value = GameChoice.NONE
            _simulatorP2Choice.value = GameChoice.NONE
        } else {
            val user = _currentUser.value ?: return
            viewModelScope.launch {
                val res = repository.readyNextRound(room.roomId, user.userId)
                res.onSuccess { updated ->
                    _currentRoom.value = updated
                    _selectedChoice.value = GameChoice.NONE
                }.onFailure { err ->
                    _errorMessage.value = err.message
                }
            }
        }
    }

    fun sendChatMessage() {
        val text = _chatInput.value.trim()
        val room = _currentRoom.value ?: return
        if (text.isEmpty()) return

        if (_matchMode.value == MatchMode.DUEL_SIMULATOR) {
            val pId = if (_activeSimulatorPlayer.value == 1) room.player1.id else (room.player2?.id ?: "usr_wife_sim")
            val res = localEngine.sendChatMessage(room.roomId, pId, text)
            res.onSuccess { updated ->
                _currentRoom.value = updated.copy(
                    player1 = updated.player1.copy(role = UserRole.HUSBAND),
                    player2 = updated.player2?.copy(role = UserRole.WIFE)
                )
                _chatInput.value = ""
                updateUnreadChat(updated)
            }
        } else {
            val user = _currentUser.value ?: return

            // OPTIMISTIC UPDATE: Clear input and show message bubble immediately
            val optimisticMsg = ChatMessage(
                id = "opt_" + System.currentTimeMillis(),
                senderId = user.userId,
                senderName = user.displayName,
                text = text,
                timestamp = System.currentTimeMillis()
            )
            _chatInput.value = ""
            _currentRoom.value = room.copy(chatMessages = room.chatMessages + optimisticMsg)

            viewModelScope.launch {
                val res = repository.sendChatMessage(room.roomId, user.userId, user.role, text)
                res.onSuccess { msgs ->
                    _currentRoom.value = _currentRoom.value?.copy(chatMessages = msgs)
                }.onFailure { err ->
                    _errorMessage.value = "Chat sync issue: ${err.message}"
                }
            }
        }
    }

    fun copySessionId(context: Context) {
        val id = _currentRoom.value?.roomId ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Couple Session ID", id)
        clipboard.setPrimaryClip(clip)
        _toastMessage.value = "Session ID ($id) copied to clipboard!"
        Toast.makeText(context, "Copied Session ID: $id", Toast.LENGTH_SHORT).show()
    }

    fun copyRoomCode(context: Context) = copySessionId(context)
    fun copyFullAddress(context: Context) = copySessionId(context)
    fun copyInviteLink(context: Context) = copySessionId(context)

    fun leaveRoom() {
        pollingJob?.cancel()
        pollingJob = null
        lastStateVersion = null
        consecutiveErrors = 0
        _currentRoom.value = null
        _selectedChoice.value = GameChoice.NONE
        _simulatorP2Choice.value = GameChoice.NONE
        _currentScreen.value = AppScreen.HOME
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
