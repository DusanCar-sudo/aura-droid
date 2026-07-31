package dev.aura.auradroid.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.gson.Gson
import dev.aura.auradroid.data.model.MessageRole
import dev.aura.auradroid.data.model.SessionMode
import dev.aura.auradroid.data.network.ConnState
import dev.aura.auradroid.data.repository.ArtifactPayload
import dev.aura.auradroid.data.repository.PlanPayload
import dev.aura.auradroid.data.repository.ToolPayload
import dev.aura.auradroid.ui.theme.AuraLogo
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToSessions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMemos: () -> Unit = {},
    onNavigateToTerminal: () -> Unit = {},
    onNeedsPairing: () -> Unit,
) {
    val needsPairing by viewModel.needsPairing.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val thinking by viewModel.thinking.collectAsState()
    val pendingConfirm by viewModel.pendingConfirm.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val dictating by viewModel.dictating.collectAsState()
    val micLevel by viewModel.micLevel.collectAsState()
    val speakingId by viewModel.speakingId.collectAsState()
    val standalone by viewModel.standalone.collectAsState()
    val autoApprove by viewModel.autoApprove.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val projectName by viewModel.projectName.collectAsState()

    val context = LocalContext.current

    // Asked the first time the microphone is used, not at launch. Held as state
    // so the hold-to-talk button knows whether pressing it can start listening
    // or has to ask first — a gesture cannot wait for a dialog mid-press.
    var canRecord by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> canRecord = granted }

    // Any document the picker can open. Images go to the model as pictures,
    // text as text; the ViewModel decides which from what comes back.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> uris.forEach(viewModel::attach) }

    // The system camera writes into a file this app owns, then it is read back
    // and downscaled. Going through MediaStore instead would leave every shot
    // in the user's gallery, which is not what attaching a photo to a question
    // is meant to do.
    var pendingPhoto by remember { mutableStateOf<android.net.Uri?>(null) }
    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        if (saved) pendingPhoto?.let(viewModel::attach)
        pendingPhoto = null
    }

    LaunchedEffect(needsPairing) {
        if (needsPairing == true) onNeedsPairing()
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissNotice()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, thinking) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ChatTopBar(
                projectName = projectName,
                mode = currentMode,
                connection = connection,
                standalone = standalone != null,
                autoApprove = autoApprove,
                onRevokeAutoApprove = { viewModel.setAutoApprove(false) },
                onMenuClick = onNavigateToSessions,
                onSettingsClick = onNavigateToSettings,
                onMemosClick = onNavigateToMemos,
                onTerminalClick = onNavigateToTerminal,
                onNewChat = viewModel::createNewSession,
                onModeChange = viewModel::setMode,
            )
        },
        bottomBar = {
            ChatInput(
                text = inputText,
                // Standalone has no socket to be connected to; the provider is
                // reached per message, so the input is always live.
                enabled = standalone != null || connection is ConnState.Connected,
                dictating = dictating,
                micLevel = micLevel,
                attachments = attachments,
                thinking = thinking,
                onChange = viewModel::onInputChange,
                onSend = viewModel::sendMessage,
                onStop = viewModel::stopReply,
                onRemoveAttachment = viewModel::removeAttachment,
                onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                onTakePhoto = {
                    val (_, uri) = dev.aura.auradroid.data.attach.Attachments
                        .newCameraTarget(context)
                    pendingPhoto = uri
                    camera.launch(uri)
                },
                onHoldStart = {
                    if (canRecord) {
                        viewModel.startHoldDictation()
                    } else {
                        micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                onHoldEnd = viewModel::stopHoldDictation,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (messages.isEmpty()) {
                item { EmptyState(standalone = standalone != null) }
            }
            items(messages, key = { it.id }) { message ->
                MessageRow(
                    message = message,
                    speaking = speakingId == message.id.toString(),
                    onSpeak = { viewModel.speak(message.id.toString(), message.content) },
                )
            }
            if (thinking) {
                item { ThinkingRow() }
            }
        }
    }

    // The agent is blocked until this is answered, so it is a modal sheet
    // rather than a dismissible banner.
    pendingConfirm?.let { confirm ->
        ApprovalSheet(
            message = confirm.message,
            onAllow = { remember ->
                viewModel.respondToConfirm(confirm.id, true, rememberForChat = remember)
            },
            onDeny = { viewModel.respondToConfirm(confirm.id, false) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    projectName: String?,
    mode: SessionMode,
    connection: ConnState,
    standalone: Boolean = false,
    autoApprove: Boolean = false,
    onRevokeAutoApprove: () -> Unit = {},
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMemosClick: () -> Unit,
    onTerminalClick: () -> Unit,
    onNewChat: () -> Unit,
    onModeChange: (SessionMode) -> Unit,
) {
    var modeMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AuraLogo(
                    modifier = Modifier.size(26.dp),
                    cyanColor = MaterialTheme.colorScheme.primary,
                    rubyColor = MaterialTheme.colorScheme.tertiary,
                )
                Column {
                    Text(
                        projectName ?: "Aura",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (standalone) {
                        StandaloneLabel(mode)
                    } else {
                        ConnectionLabel(connection, mode)
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, "Sessions") }
        },
        actions = {
            // Only while it is on, and it is the off switch. A standing
            // permission the user cannot see they granted is the part of this
            // that would actually bite them.
            if (autoApprove) {
                IconButton(onClick = onRevokeAutoApprove) {
                    Icon(
                        Icons.Default.LockOpen,
                        contentDescription = "Tools are auto-approved in this chat — " +
                            "tap to require approval again",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Box {
                IconButton(onClick = { modeMenu = true }) { Icon(Icons.Default.Tune, "Mode") }
                DropdownMenu(modeMenu, onDismissRequest = { modeMenu = false }) {
                    SessionMode.entries.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.name.lowercase()) },
                            onClick = { onModeChange(m); modeMenu = false },
                            trailingIcon = {
                                if (m == mode) {
                                    Icon(
                                        Icons.Default.Check, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onNewChat) { Icon(Icons.Default.Add, "New chat") }
            // Five actions did not fit — the last ones were laid out past the
            // right edge of the screen and could not be tapped at all. Memos
            // and Shell move behind an overflow, which is what it is for.
            Box {
                var more by remember { mutableStateOf(false) }
                IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(
                        text = { Text("Memos") },
                        onClick = { more = false; onMemosClick() },
                        leadingIcon = {
                            Icon(
                                Icons.Default.StickyNote2, null,
                                tint = dev.aura.auradroid.ui.screens.memos.MemoYellow,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Shell") },
                        onClick = { more = false; onTerminalClick() },
                        leadingIcon = { Icon(Icons.Default.Terminal, null) },
                    )
                }
            }
            IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Settings") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

/** No socket to report on — say where the model is instead. */
@Composable
private fun StandaloneLabel(mode: SessionMode) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary),
        )
        Text(
            "on device · ${mode.name.lowercase()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Connection state has to be visible — a dead socket otherwise looks like a slow agent. */
@Composable
private fun ConnectionLabel(connection: ConnState, mode: SessionMode) {
    val (dot, label) = when (connection) {
        is ConnState.Connected ->
            MaterialTheme.colorScheme.primary to mode.name.lowercase()
        is ConnState.Connecting ->
            MaterialTheme.colorScheme.secondary to "connecting…"
        is ConnState.Reconnecting ->
            MaterialTheme.colorScheme.secondary to
                "reconnecting in ${connection.retryInSeconds}s"
        is ConnState.Failed ->
            MaterialTheme.colorScheme.error to "disconnected"
        is ConnState.Disconnected ->
            MaterialTheme.colorScheme.onSurfaceVariant to "offline"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageRow(
    message: MessageItem,
    speaking: Boolean = false,
    onSpeak: () -> Unit = {},
) {
    val artifact = remember(message.metadata) {
        runCatching {
            Gson().fromJson(message.metadata, ArtifactPayload::class.java)
        }.getOrNull()
    }

    when {
        message.role == MessageRole.TOOL -> ToolRow(message)
        artifact != null -> ArtifactRow(artifact)
        message.role == MessageRole.SYSTEM -> SystemRow(message)
        else -> BubbleRow(message, speaking, onSpeak)
    }
}

@Composable
private fun ArtifactRow(artifact: ArtifactPayload) {
    var showPreview by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.InsertDriveFile,
                    null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    artifact.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showPreview = !showPreview }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (showPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPreview) "Hide preview" else "Show preview",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = { showCode = !showCode }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Show code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (showPreview) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                ) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = WebViewClient()
                                loadDataWithBaseURL(null, artifact.content, "text/html", "utf-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (showCode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SelectionContainer {
                        Text(
                            artifact.content,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleRow(
    message: MessageItem,
    speaking: Boolean = false,
    onSpeak: () -> Unit = {},
) {
    val isUser = message.role == MessageRole.USER

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
            AuraLogo(
                modifier = Modifier.size(22.dp).padding(top = 4.dp),
                cyanColor = MaterialTheme.colorScheme.primary,
                rubyColor = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(
            Modifier.widthIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isUser) "You" else "Aura",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                // Only on replies, and only once they are complete: speaking a
                // half-arrived answer would read the first few words and stop.
                if (!message.isStreaming && message.content.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    val clipboard = LocalClipboardManager.current
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy message",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(15.dp)
                            .clickable {
                                clipboard.setText(AnnotatedString(message.content))
                            },
                    )
                }
                if (!isUser && !message.isStreaming && message.content.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = if (speaking) Icons.Default.StopCircle else Icons.Default.VolumeUp,
                        contentDescription = if (speaking) "Stop speaking" else "Read aloud",
                        tint = if (speaking) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onSpeak),
                    )
                }
            }
            Surface(
                color = if (isUser) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Bottom) {
                    // Selectable, so text can be picked out by hand. The copy
                    // button below covers the whole message; this covers the
                    // one sentence someone actually wants.
                    SelectionContainer {
                        Text(
                            message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    // Cursor shows the answer is still arriving, so a pause
                    // reads as thinking rather than as a finished short reply.
                    if (message.isStreaming) {
                        Spacer(Modifier.width(3.dp))
                        BlinkingCursor()
                    }
                }
            }

            // Anything the agent produced as a file, offered as one. A whole
            // HTML page is useless if the only way out of it is selecting text
            // on a phone screen.
            if (!isUser && !message.isStreaming) {
                val blocks = remember(message.content) {
                    dev.aura.auradroid.data.export.CodeBlocks.extract(message.content)
                }
                for (block in blocks) SaveableBlock(block)
            }
        }
    }
}

/** A file the agent wrote: save it to Downloads, or send it somewhere. */
@Composable
private fun SaveableBlock(block: dev.aura.auradroid.data.export.CodeBlock) {
    val context = LocalContext.current
    var saved by remember(block.fileName) { mutableStateOf<String?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.InsertDriveFile, null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    block.fileName,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${block.code.lines().size} lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    // Reports where it went: "saved" with no location is the
                    // kind of message that sends people hunting.
                    saved = dev.aura.auradroid.data.export.CodeBlocks
                        .saveToDownloads(context, block.fileName, block.code)
                        ?: "could not save"
                }) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
                TextButton(onClick = {
                    dev.aura.auradroid.data.export.Sharing.shareText(
                        context = context,
                        fileName = block.fileName,
                        content = block.code,
                        subject = block.fileName,
                    )
                }) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
            }
            saved?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ToolRow(message: MessageItem) {
    val payload = remember(message.toolCalls) {
        message.toolCalls?.let {
            runCatching { Gson().fromJson(it, ToolPayload::class.java) }.getOrNull()
        }
    }

    val accent = when (payload?.status) {
        "COMPLETED" -> MaterialTheme.colorScheme.primary
        "BLOCKED" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(accent))
                Text(
                    payload?.name ?: message.content,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = accent,
                )
                payload?.ms?.let {
                    Text(
                        "${it}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
            payload?.input?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            payload?.result?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it.lineSequence().take(4).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun SystemRow(message: MessageItem) {
    val plan = remember(message.metadata) {
        message.metadata?.let {
            runCatching { Gson().fromJson(it, PlanPayload::class.java) }.getOrNull()
        }
    }

    if (plan != null) {
        PlanCard(plan, message.content)
        return
    }

    Text(
        message.content,
        style = MaterialTheme.typography.labelSmall,
        color = if (message.errorMessage != null) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlanCard(plan: PlanPayload, title: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                plan.goal ?: title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            plan.steps.forEach { step ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    val (mark, tint) = when (step.status) {
                        "DONE" -> "✓" to MaterialTheme.colorScheme.primary
                        "RUNNING" -> "→" to MaterialTheme.colorScheme.secondary
                        "FAILED" -> "✗" to MaterialTheme.colorScheme.error
                        else -> "·" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(mark, style = MaterialTheme.typography.bodySmall, color = tint)
                    Text(
                        "[${step.specialist}] ${step.task}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApprovalSheet(
    message: String,
    onAllow: (rememberForChat: Boolean) -> Unit,
    onDeny: () -> Unit,
) {
    var rememberForChat by remember { mutableStateOf(false) }

    ModalBottomSheet(
        // Dismissing without answering would leave the agent blocked until it
        // times out, so a swipe-away denies rather than doing nothing.
        onDismissRequest = onDeny,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    Icons.Default.Security,
                    null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    "Approval needed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(13.dp),
                )
            }

            Text(
                "Aura is waiting on your answer and will not continue until you decide.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Offered per conversation, not forever. Granting a blanket
            // permission from a phone is easy; remembering weeks later that you
            // did is not, so it expires with the chat it was granted in.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { rememberForChat = !rememberForChat },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = rememberForChat,
                    onCheckedChange = { rememberForChat = it },
                )
                Column(Modifier.padding(start = 2.dp)) {
                    Text(
                        "Don't ask again in this chat",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Cleared when you start or open another conversation.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                OutlinedButton(onClick = onDeny, modifier = Modifier.weight(1f)) { Text("Deny") }
                Button(
                    onClick = { onAllow(rememberForChat) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (rememberForChat) "Allow all" else "Allow")
                }
            }
        }
    }
}

@Composable
private fun ChatInput(
    text: String,
    enabled: Boolean,
    dictating: Boolean,
    micLevel: Float,
    attachments: List<dev.aura.auradroid.data.attach.Attachment>,
    thinking: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onPickFiles: () -> Unit,
    onTakePhoto: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    var attachMenu by remember { mutableStateOf(false) }
    val canSend = enabled && (text.isNotBlank() || attachments.isNotEmpty())

    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                // The activity is edge-to-edge, so this row would otherwise sit
                // *under* the navigation bar — invisible on a phone with the
                // three-button layout, and only a sliver short on a gesture bar.
                // union takes the larger of the two per side: the nav bar when
                // the keyboard is closed, the keyboard when it is open.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (attachments.isNotEmpty()) {
                AttachmentStrip(attachments, onRemoveAttachment)
            }
            if (dictating) {
                DictationBar(micLevel)
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box {
                    IconButton(onClick = { attachMenu = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Attach",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    DropdownMenu(attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Take a photo") },
                            onClick = { attachMenu = false; onTakePhoto() },
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Photo or file") },
                            onClick = { attachMenu = false; onPickFiles() },
                            leadingIcon = { Icon(Icons.Default.AttachFile, null) },
                        )
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onChange,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(if (enabled) "Message Aura…" else "Not connected")
                    },
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    shape = RoundedCornerShape(22.dp),
                )

                HoldToTalkButton(onHoldStart = onHoldStart, onHoldEnd = onHoldEnd, active = dictating)

                if (thinking) {
                    IconButton(onClick = onStop) {
                        Icon(
                            Icons.Default.StopCircle,
                            "Stop",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    IconButton(onClick = onSend, enabled = canSend) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            "Send",
                            tint = if (canSend) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Press and hold to talk; release to stop.
 *
 * A press-and-hold rather than a tap-on, tap-off toggle because that is what
 * the gesture already means everywhere else on a phone, and because it cannot
 * be left running by accident — the microphone stops when the finger lifts,
 * with nothing to forget.
 *
 * onPress/tryAwaitRelease rather than a clickable: release has to be observed
 * even when the finger slides off the button, which a click never reports.
 */
@Composable
private fun HoldToTalkButton(
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    active: Boolean,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (active) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                } else {
                    Color.Transparent
                },
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        haptics.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                        )
                        onHoldStart()
                        // Suspends until the finger lifts or the gesture is
                        // cancelled; both mean stop listening.
                        tryAwaitRelease()
                        onHoldEnd()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (active) Icons.Default.GraphicEq else Icons.Default.Mic,
            contentDescription = if (active) "Listening — release to stop" else "Hold to talk",
            tint = if (active) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Shows the microphone is live and hearing something, while held. */
@Composable
private fun DictationBar(level: Float) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error),
        )
        Text(
            "Listening — release to stop",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { level.coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.error,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

/** What is going with the next message, and a way to take it back out. */
@Composable
private fun AttachmentStrip(
    attachments: List<dev.aura.auradroid.data.attach.Attachment>,
    onRemove: (String) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            val isImage =
                attachment.kind == dev.aura.auradroid.data.attach.AttachmentKind.IMAGE
            AssistChip(
                onClick = { onRemove(attachment.id) },
                label = {
                    Text(
                        attachment.name.take(22),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                leadingIcon = {
                    Icon(
                        if (isImage) Icons.Default.Image else Icons.Default.InsertDriveFile,
                        null,
                        modifier = Modifier.size(15.dp),
                    )
                },
                trailingIcon = {
                    Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(15.dp))
                },
            )
        }
    }
}

@Composable
private fun BlinkingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursor-alpha",
    )
    Box(
        Modifier
            .size(width = 6.dp, height = 14.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun ThinkingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        val transition = rememberInfiniteTransition(label = "thinking")
        val scale by transition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                tween(900, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "thinking-scale",
        )
        Box(
            Modifier
                .size((9 * scale).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary),
        )
        Text(
            "Aura is working…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(standalone: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            AuraLogo(
                modifier = Modifier.size(46.dp),
                cyanColor = MaterialTheme.colorScheme.primary,
                rubyColor = MaterialTheme.colorScheme.tertiary,
            )
        }
        Text(
            "What shall we build?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            if (standalone) {
                "Running on this phone. Hold the microphone to talk, or use + to " +
                    "attach a photo or a file."
            } else {
                "Aura runs on your desktop with your project open."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
