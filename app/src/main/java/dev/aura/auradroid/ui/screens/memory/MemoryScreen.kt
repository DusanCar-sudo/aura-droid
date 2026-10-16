package dev.aura.auradroid.ui.screens.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aura.auradroid.data.memory.AgentMemory
import dev.aura.auradroid.data.model.Memory
import dev.aura.auradroid.ui.theme.GeistMono
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memory: AgentMemory,
) : ViewModel() {

    val notes: StateFlow<List<Memory>> = memory.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun forget(id: String) = viewModelScope.launch { memory.forget(id) }

    fun forgetAll() = viewModelScope.launch { memory.forgetAll() }

    fun seedDurableMemory() = viewModelScope.launch { memory.injectDurableMemories() }
}

/**
 * Everything the agent has decided to remember, and a way to delete any of it.
 *
 * Exists because the agent writes these on its own initiative. A memory the
 * user cannot inspect is one they cannot correct, and a wrong fact that keeps
 * getting injected into every prompt is worse than no memory at all — it makes
 * the assistant confidently wrong in the same way every time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val notes by viewModel.notes.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shown = remember(notes, query) {
        val q = query.trim()
        if (q.isEmpty()) notes
        else notes.filter { it.text.contains(q, true) || it.tag.contains(q, true) }
    }
    // One "episode" per day, newest first, so a session's lessons sit together.
    val episodes = remember(shown) {
        shown.sortedByDescending { it.createdAt }.groupBy { episodeDay(it.createdAt) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.Psychology,
                            null,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Column {
                            Text("Aura's memory", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Lessons Aura saved by herself · ${notes.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.seedDurableMemory() }) {
                        Text("Restore defaults", color = MaterialTheme.colorScheme.primary)
                    }
                    if (notes.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) {
                            Text("Clear all", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (notes.isEmpty()) {
            EmptyMemory(
                onSeedMemory = { viewModel.seedDurableMemory() },
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search what Aura learned…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }
            episodes.forEach { (day, group) ->
                item(key = "h-$day") {
                    Text(
                        "EPISODE · ${day.uppercase()}  ·  ${group.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = GeistMono,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(group, key = { it.id }) { note ->
                    NoteCard(note, onForget = { viewModel.forget(note.id) })
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Forget everything?") },
            text = {
                Text(
                    "Aura will lose every note she has saved about you and your work. " +
                        "Conversations are not affected.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.forgetAll()
                }) {
                    Text("Forget all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun NoteCard(note: Memory, onForget: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(note.text, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        note.tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        formatDate(note.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    // Shown because it explains the ordering: the notes that get
                    // used are the ones that stay in the prompt.
                    if (note.useCount > 0) {
                        Text(
                            "used ${note.useCount}×",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            IconButton(onClick = onForget) {
                Icon(
                    Icons.Default.DeleteOutline,
                    "Forget this",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyMemory(
    onSeedMemory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Psychology,
            null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Nothing remembered yet",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "As you talk, Aura saves what is worth keeping — how you like to work, " +
                "what you are building, decisions you made. It appears here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSeedMemory,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text("Inject foundation memory")
        }
    }
}

private fun episodeDay(millis: Long): String =
    SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(millis))

private fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
