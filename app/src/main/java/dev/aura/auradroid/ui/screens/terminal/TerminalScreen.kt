package dev.aura.auradroid.ui.screens.terminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aura.auradroid.data.shell.PhoneShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.aura.auradroid.ui.theme.GeistMono

data class Line(val id: Long, val text: String, val isPrompt: Boolean)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val shell: PhoneShell,
) : ViewModel() {

    private val _lines = MutableStateFlow<List<Line>>(
        listOf(
            Line(0, "Aura shell — this phone, inside the app's own storage.", false),
            Line(1, "Try: ls, pwd, df -h, ps (or 'sudo <cmd>' for elevated adb shell via Shizuku)", false),
        ),
    )
    val lines: StateFlow<List<Line>> = _lines.asStateFlow()

    private val _elevated = MutableStateFlow(false)
    val elevated: StateFlow<Boolean> = _elevated.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _prompt = MutableStateFlow(shell.prompt())
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _where = MutableStateFlow(shell.cwd.path)
    /** Full path of the working directory, so "~" is never a mystery. */
    val where: StateFlow<String> = _where.asStateFlow()

    private var next = 2L

    fun onInput(v: String) { _input.value = v }

    fun toggleElevated() {
        _elevated.value = !_elevated.value
    }

    fun run() {
        val raw = _input.value.trim()
        if (raw.isEmpty() || _running.value) return
        _input.value = ""

        val (cmd, isElevated) = when {
            raw.startsWith("sudo ") -> raw.removePrefix("sudo ").trim() to true
            raw.startsWith("# ") -> raw.removePrefix("# ").trim() to true
            _elevated.value -> raw to true
            else -> raw to false
        }
        val symbol = if (isElevated) "#" else "$"
        append("${_prompt.value} $symbol $cmd", isPrompt = true)

        viewModelScope.launch {
            _running.value = true
            val result = shell.run(cmd, elevated = isElevated)
            if (result.output.isNotBlank()) append(result.output.trimEnd(), false)
            // Only when it failed: a zero exit on every command is noise that
            // pushes the useful output off a small screen.
            if (result.exitCode != 0) append("[exit ${result.exitCode}]", false)
            _prompt.value = shell.prompt()
            _where.value = shell.cwd.path
            _running.value = false
        }
    }

    fun insert(text: String) { _input.value = _input.value + text }

    fun clear() {
        _lines.value = emptyList()
    }

    private fun append(text: String, isPrompt: Boolean) {
        _lines.value = (_lines.value + Line(next++, text, isPrompt)).takeLast(500)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val lines by viewModel.lines.collectAsState()
    val input by viewModel.input.collectAsState()
    val prompt by viewModel.prompt.collectAsState()
    val running by viewModel.running.collectAsState()
    val where by viewModel.where.collectAsState()
    val elevated by viewModel.elevated.collectAsState()

    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Shell", fontWeight = FontWeight.SemiBold)
                        Text(
                            where,
                            fontFamily = GeistMono,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    FilterChip(
                        selected = elevated,
                        onClick = viewModel::toggleElevated,
                        label = { Text("Elevated", fontSize = 12.sp) },
                    )
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = viewModel::clear) { Text("Clear") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
              Column(
                Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
              ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("sudo ", "|", "-", "/", "~", "..", ">", "&&").forEach { k ->
                        Surface(
                            onClick = { viewModel.insert(k) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Text(
                                k,
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                fontFamily = GeistMono,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = viewModel::onInput,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("$prompt \$") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = GeistMono,
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send,
                            autoCorrectEnabled = false,
                        ),
                        keyboardActions = KeyboardActions(onSend = { viewModel.run() }),
                        shape = RoundedCornerShape(12.dp),
                    )
                    IconButton(onClick = viewModel::run, enabled = !running && input.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Run")
                    }
                }
              }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
        ) {
            items(lines, key = { it.id }) { line ->
                Text(
                    line.text,
                    // Monospace and small: shell output is columnar, and
                    // proportional type turns `ls -l` into a mess.
                    fontFamily = GeistMono,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = if (line.isPrompt) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (running) {
                item {
                    Text(
                        "…",
                        fontFamily = GeistMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
