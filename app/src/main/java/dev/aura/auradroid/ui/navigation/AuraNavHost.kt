package dev.aura.auradroid.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.aura.auradroid.ui.screens.chat.ChatScreen
import dev.aura.auradroid.ui.screens.chat.ChatViewModel
import dev.aura.auradroid.ui.screens.memory.MemoryScreen
import dev.aura.auradroid.ui.screens.memos.MemosScreen
import dev.aura.auradroid.ui.screens.pairing.PairingScreen
import dev.aura.auradroid.ui.screens.sessions.SessionsScreen
import dev.aura.auradroid.ui.screens.settings.SettingsScreen
import dev.aura.auradroid.ui.screens.terminal.TerminalScreen
import dev.aura.auradroid.ui.screens.triggers.TriggersScreen

object Routes {
    const val PAIRING = "pairing"
    const val CHAT = "chat"
    const val SESSIONS = "sessions"
    const val SETTINGS = "settings"
    const val MEMOS = "memos"
    const val TERMINAL = "terminal"
    const val LOCAL = "local"
    const val MEMORY = "memory"
    const val TRIGGERS = "triggers"

    /**
     * Key the sessions screen writes the chosen conversation into.
     *
     * popBackStack cannot carry a result, so the selection is handed to the
     * chat entry's SavedStateHandle and read on the way back. This survives
     * process death, unlike a value held in a composable.
     */
    const val SELECTED_SESSION = "selectedSessionId"

    /** A task to send as soon as the chat screen opens, e.g. from a memo. */
    const val PENDING_TASK = "pendingTask"
}

@Composable
fun AuraNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.CHAT,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Routes.PAIRING) {
            // Reachable two ways: as the first screen with nothing configured,
            // and from Settings to change how Aura runs. Only the second has
            // somewhere to go back to, and offering Back with an empty stack
            // would drop the user out of the app.
            val fromSettings = navController.previousBackStackEntry != null

            PairingScreen(
                onPaired = {
                    // Pairing is a precondition, not a step in a journey — drop
                    // it from the back stack so Back does not return to it.
                    navController.navigate(Routes.CHAT) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateBack = if (fromSettings) {
                    { navController.popBackStack() }
                } else {
                    null
                },
            )
        }
        composable(Routes.CHAT) { entry ->
            // The chat screen keeps its ViewModel while Sessions sits on top of
            // it, so opening a conversation is a load, not a re-creation.
            val chatViewModel: ChatViewModel = hiltViewModel(entry)
            val selected by entry.savedStateHandle
                .getStateFlow<String?>(Routes.SELECTED_SESSION, null)
                .collectAsState()

            val pendingTask by entry.savedStateHandle
                .getStateFlow<String?>(Routes.PENDING_TASK, null)
                .collectAsState()

            LaunchedEffect(selected) {
                selected?.let { id ->
                    chatViewModel.loadSession(id)
                    // Clear it, or returning to chat by any other route would
                    // reopen this conversation again.
                    entry.savedStateHandle[Routes.SELECTED_SESSION] = null
                }
            }

            // Standalone is switched on in Settings while this screen sits on
            // the back stack, so the mode is re-read on the way back rather
            // than only when the ViewModel is first built.
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        chatViewModel.configure()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(pendingTask) {
                pendingTask?.let { task ->
                    // Sent after the session above is loaded, so a memo's brief
                    // lands in its own conversation rather than the last one.
                    chatViewModel.sendTaskText(task)
                    entry.savedStateHandle[Routes.PENDING_TASK] = null
                }
            }

            // Three panes: swipe right for the memory you save, left for the
            // memory Aura saves, centre is the conversation.
            val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = 1) { 3 }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val toChat: () -> Unit = { scope.launch { pager.animateScrollToPage(1) } }

            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            // Thin three-segment marker: which pane you are on, tap to jump.
            androidx.compose.foundation.layout.Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("MY MEMORY", "CHAT", "AURA'S MEMORY").forEachIndexed { i, label ->
                    val on = pager.currentPage == i
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { scope.launch { pager.animateScrollToPage(i) } },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(
                                    if (on) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        )
                    }
                }
            }
            androidx.compose.foundation.pager.HorizontalPager(
                state = pager,
                modifier = Modifier
                    .weight(1f)
                    .consumeWindowInsets(WindowInsets.statusBars),
                beyondViewportPageCount = 1,
            ) { page ->
                when (page) {
                    0 -> MemosScreen(
                        onNavigateBack = toChat,
                        onStartProject = { sessionId, task ->
                            chatViewModel.loadSession(sessionId)
                            chatViewModel.sendTaskText(task)
                            toChat()
                        },
                    )
                    1 -> ChatScreen(
                        viewModel = chatViewModel,
                        onNavigateToSessions = { navController.navigate(Routes.SESSIONS) },
                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                        onNavigateToMemos = { scope.launch { pager.animateScrollToPage(0) } },
                        onNavigateToTerminal = { navController.navigate(Routes.TERMINAL) },
                        onNavigateToLocal = { navController.navigate(Routes.LOCAL) },
                        onNeedsPairing = {
                            navController.navigate(Routes.PAIRING) {
                                popUpTo(Routes.CHAT) { inclusive = true }
                            }
                        },
                    )
                    else -> MemoryScreen(onNavigateBack = toChat)
                }
            }
            }
        }
        composable(Routes.SESSIONS) {
            SessionsScreen(
                onNavigateBack = { navController.popBackStack() },
                onSessionSelected = { id ->
                    // Hand the choice to the chat entry underneath, then pop.
                    // Tapping a conversation previously called an empty
                    // function, so nothing happened at all.
                    navController.previousBackStackEntry
                        ?.savedStateHandle?.set(Routes.SELECTED_SESSION, id)
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.LOCAL) {
            dev.aura.auradroid.ui.screens.local.LocalChatScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.TERMINAL) {
            TerminalScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.MEMOS) {
            MemosScreen(
                onNavigateBack = { navController.popBackStack() },
                onStartProject = { sessionId, task ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set(Routes.SELECTED_SESSION, sessionId)
                        set(Routes.PENDING_TASK, task)
                    }
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onUnpair = {
                    // Back to the chooser, not out of the app: unpairing may
                    // leave the phone-only mode still configured and perfectly
                    // usable, and the chooser is where that is decided.
                    navController.navigate(Routes.PAIRING) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSetUpDesktop = { navController.navigate(Routes.PAIRING) },
                onOpenMemory = { navController.navigate(Routes.MEMORY) },
                onOpenTriggers = { navController.navigate(Routes.TRIGGERS) },
            )
        }
        composable(Routes.MEMORY) {
            MemoryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.TRIGGERS) {
            TriggersScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
