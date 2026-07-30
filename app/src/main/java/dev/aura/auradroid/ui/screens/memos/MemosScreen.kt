package dev.aura.auradroid.ui.screens.memos

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.aura.auradroid.R
import dev.aura.auradroid.data.audio.ListenState
import dev.aura.auradroid.data.model.Memo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemosScreen(
    viewModel: MemosViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onStartProject: (sessionId: String, task: String) -> Unit,
) {
    val memos by viewModel.memos.collectAsState()
    val recording by viewModel.recording.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val listenState by viewModel.listenState.collectAsState()
    val level by viewModel.level.collectAsState()

    var editing by remember { mutableStateOf<Memo?>(null) }

    // Asked at the point of use. A permission prompt on first launch, before
    // the user has any idea why the microphone is wanted, is the one most
    // likely to be refused.
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.startRecording() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memos", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (recording) {
                RecordingPanel(
                    transcript = transcript,
                    level = level,
                    onStop = viewModel::stopAndSave,
                    onCancel = viewModel::cancelRecording,
                )
            }

            (listenState as? ListenState.Error)?.let {
                Text(
                    it.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (memos.isEmpty() && !recording) {
                EmptyMemos(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(memos, key = { it.id }) { memo ->
                        MemoCard(
                            memo = memo,
                            onEdit = { editing = memo },
                            onDelete = { viewModel.deleteMemo(memo.id) },
                            onStartProject = {
                                viewModel.startProject(memo, onStartProject)
                            },
                        )
                    }
                }
            }

            if (!recording) {
                Button(
                    onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                ) {
                    Icon(Icons.Default.Mic, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Record a memo")
                }
            }
        }
    }

    editing?.let { memo ->
        var draft by remember(memo.id) { mutableStateOf(memo.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit memo") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.updateMemo(memo.id, draft); editing = null },
                    enabled = draft.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RecordingPanel(
    transcript: String,
    level: Float,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Scales with loudness, so it is visibly reacting to the voice
                // rather than just claiming to be recording.
                Box(
                    Modifier
                        .size(12.dp)
                        .scale(1f + level * 0.8f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                )
                Spacer(Modifier.width(10.dp))
                Text("Listening…", fontWeight = FontWeight.Medium)
            }

            Text(
                // Shown live so the speaker can see it is being heard, and stop
                // early if it is mishearing them badly.
                transcript.ifBlank { "Say something — this appears as you speak." },
                style = MaterialTheme.typography.bodyMedium,
                color = if (transcript.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onStop, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save")
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Discard")
                }
            }
        }
    }
}

/**
 * The paper colour of the memo icon, sampled from the artwork so the glyphs
 * and the illustration agree rather than nearly agreeing.
 */
val MemoYellow = Color(0xFFF5E7A8)

@Composable
private fun MemoCard(
    memo: Memo,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStartProject: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.StickyNote2,
                    contentDescription = null,
                    tint = MemoYellow,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(memo.title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (!memo.synced) {
                    // Says where the memo is, not that something failed: it is
                    // saved either way, just not yet searchable by Aura.
                    Text(
                        "on phone",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
            Text(
                memo.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatWhen(memo.createdAt)} · ${formatDuration(memo.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (memo.startedSessionId == null) {
                Button(onClick = onStartProject, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start a project from this")
                }
            } else {
                Text(
                    "A conversation was started from this memo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun EmptyMemos(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                // The illustration only where it has room. At toolbar sizes it
                // turns to mush, so those keep the flat glyph.
                painter = painterResource(R.drawable.ic_memo),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("No memos yet", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Record an idea, then turn it into a project.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatWhen(ts: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ts))

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}
