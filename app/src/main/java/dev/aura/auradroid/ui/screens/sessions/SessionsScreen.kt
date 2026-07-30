package dev.aura.auradroid.ui.screens.sessions

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import dev.aura.auradroid.data.export.ChatExporter
import dev.aura.auradroid.data.model.Session
import dev.aura.auradroid.ui.theme.AuraCyan
import dev.aura.auradroid.ui.theme.AuraLogo
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    viewModel: SessionsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    /** Open this conversation in the chat screen. */
    onSessionSelected: (String) -> Unit,
) {
    val sessions by viewModel.sessions.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            AuraSessionsTopBar(onNavigateBack = onNavigateBack)
        },
        floatingActionButton = {
            AuraFloatingActionButton(
                // Create, then open it — a new conversation the user cannot
                // reach is the same bug as one they cannot go back to.
                onClick = { viewModel.createNewSession(onSessionSelected) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header section
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Your Sessions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${sessions.size} session${if (sessions.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            if (sessions.isEmpty()) {
                item {
                    AuraEmptySessionsState(
                        onCreateNew = { viewModel.createNewSession(onSessionSelected) },
                    )
                }
            } else {
                items(sessions) { session ->
                    AuraSessionCard(
                        session = session,
                        onClick = { onSessionSelected(session.id) },
                        onPinToggle = { viewModel.togglePin(session.id) },
                        onRename = { viewModel.renameSession(session.id, it) },
                        onExport = { viewModel.exportSession(context, session.id, it) },
                        onDelete = { viewModel.deleteSession(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun AuraSessionsTopBar(onNavigateBack: () -> Unit) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AuraLogo(
                    modifier = Modifier.size(28.dp),
                    cyanColor = MaterialTheme.colorScheme.primary,
                    rubyColor = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "Sessions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
fun AuraFloatingActionButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab-pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "fab-pulse"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .padding(16.dp)
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "New Session",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
fun AuraSessionCard(
    session: Session,
    onClick: () -> Unit,
    onPinToggle: () -> Unit,
    onRename: (String) -> Unit,
    onExport: (ChatExporter.Format) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (session.isPinned) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        border = if (session.isPinned) {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Avatar + Session info
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Avatar with glow
                Box {
                    // Glow effect
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        when (session.mode) {
                                            dev.aura.auradroid.data.model.SessionMode.CODER ->
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                            dev.aura.auradroid.data.model.SessionMode.GAZELLE ->
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                                            dev.aura.auradroid.data.model.SessionMode.ARCHITECT ->
                                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                                        },
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                when (session.mode) {
                                    dev.aura.auradroid.data.model.SessionMode.CODER ->
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    dev.aura.auradroid.data.model.SessionMode.GAZELLE ->
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                    dev.aura.auradroid.data.model.SessionMode.ARCHITECT ->
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (session.mode) {
                                dev.aura.auradroid.data.model.SessionMode.CODER -> Icons.Default.Code
                                dev.aura.auradroid.data.model.SessionMode.GAZELLE -> Icons.Default.Chat
                                dev.aura.auradroid.data.model.SessionMode.ARCHITECT -> Icons.Default.AccountTree
                            },
                            contentDescription = null,
                            tint = when (session.mode) {
                                dev.aura.auradroid.data.model.SessionMode.CODER ->
                                    MaterialTheme.colorScheme.primary
                                dev.aura.auradroid.data.model.SessionMode.GAZELLE ->
                                    MaterialTheme.colorScheme.secondary
                                dev.aura.auradroid.data.model.SessionMode.ARCHITECT ->
                                    MaterialTheme.colorScheme.tertiary
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Session info
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (session.isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = session.mode.name.lowercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "${session.messageCount} message${if (session.messageCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatDate(session.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (session.isPinned) "Unpin" else "Pin",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onPinToggle()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = null,
                                tint = if (session.isPinned) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export as Markdown", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            onExport(ChatExporter.Format.MARKDOWN)
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.onSurface)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export as JSON", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            onExport(ChatExporter.Format.JSON)
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.DataObject, null, tint = MaterialTheme.colorScheme.onSurface)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text("Rename", color = MaterialTheme.colorScheme.onSurface)
                        },
                        onClick = {
                            renaming = true
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            onDelete()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }

    if (renaming) {
        // Pre-filled, so replacing a generic "New Chat" is one gesture rather
        // than a manual clear.
        var draft by remember(session.id) { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onRename(draft); renaming = false },
                    // A blank name leaves a row with nothing to identify it,
                    // so the action stays unavailable until there is one.
                    enabled = draft.isNotBlank(),
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text("Cancel") }
            },
        )
    }

}

@Composable
fun AuraEmptySessionsState(onCreateNew: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Animated Aura logo
        val infiniteTransition = rememberInfiniteTransition(label = "breathing")

        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = LinearEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "breathing"
        )

        Box(modifier = Modifier.size(80.dp * scale)) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .alpha(0.3f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
            AuraLogo(
                modifier = Modifier.size(64.dp),
                cyanColor = MaterialTheme.colorScheme.primary,
                rubyColor = MaterialTheme.colorScheme.tertiary
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No sessions yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Start your first conversation with Aura",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Button(
            onClick = onCreateNew,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start New Session")
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}
