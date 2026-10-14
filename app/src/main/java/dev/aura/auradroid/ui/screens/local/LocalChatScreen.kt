package dev.aura.auradroid.ui.screens.local

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aura.auradroid.data.standalone.Chunk
import dev.aura.auradroid.data.standalone.LocalLlm
import dev.aura.auradroid.data.standalone.Turn
import dev.aura.auradroid.ui.theme.GeistMono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

data class LocalMsg(val id: Long, val mine: Boolean, val text: String)

@HiltViewModel
class LocalChatViewModel @Inject constructor(
    app: Application,
    private val llm: LocalLlm,
) : AndroidViewModel(app) {

    private val _models = MutableStateFlow(llm.models().map { it.name })
    val models: StateFlow<List<String>> = _models.asStateFlow()

    private val _model = MutableStateFlow(_models.value.firstOrNull())
    val model: StateFlow<String?> = _model.asStateFlow()

    private val _msgs = MutableStateFlow<List<LocalMsg>>(emptyList())
    val msgs: StateFlow<List<LocalMsg>> = _msgs.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()

    private val _speed = MutableStateFlow<Float?>(null)
    val speed: StateFlow<Float?> = _speed.asStateFlow()

    val folder: String get() = llm.dir.path
    private var next = 0L
    private var job: Job? = null

    private fun refresh(select: String? = null) {
        _models.value = llm.models().map { it.name }
        _model.value = select ?: _model.value?.takeIf { it in _models.value } ?: _models.value.firstOrNull()
    }

    fun pick(name: String) { _model.value = name }
    fun dismissNote() { _note.value = null }
    fun newChat() { job?.cancel(); _busy.value = false; _msgs.value = emptyList(); _speed.value = null }

    fun delete(name: String) {
        File(llm.dir, name).delete()
        refresh()
    }

    /** Copy a model file the user picked into the app's models folder. */
    fun importFrom(uri: Uri) = viewModelScope.launch {
        _note.value = "Copying model…"
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val cr = getApplication<Application>().contentResolver
                val name = cr.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && i >= 0) c.getString(i) else null
                } ?: "model.task"
                val out = File(llm.dir, name)
                cr.openInputStream(uri)!!.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                name
            }
        }
        result.onSuccess { refresh(it); _note.value = "Added $it" }
            .onFailure { _note.value = "Could not copy: ${it.message}" }
    }

    /** Download a model from a direct link, with progress in the note line. */
    fun download(url: String) = viewModelScope.launch {
        val u = url.trim()
        if (!u.startsWith("http")) { _note.value = "Paste a direct http(s) link to a .task or .litertlm file."; return@launch }
        val name = u.substringBefore('?').substringAfterLast('/').ifBlank { "model.task" }
        _note.value = "Downloading $name…"
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val client = OkHttpClient.Builder().readTimeout(java.time.Duration.ofSeconds(60)).build()
                client.newCall(Request.Builder().url(u).build()).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}${if (r.code == 401 || r.code == 403) " — this link needs a login; download it elsewhere and use Add file" else ""}")
                    val body = r.body ?: error("empty response")
                    val total = body.contentLength()
                    val tmp = File(llm.dir, "$name.part")
                    var got = 0L; var last = 0L
                    body.byteStream().use { i -> tmp.outputStream().use { o ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            val n = i.read(buf); if (n < 0) break
                            o.write(buf, 0, n); got += n
                            if (got - last > 8_000_000) {
                                last = got
                                _note.value = "Downloading $name… ${got / 1_000_000} MB" +
                                    if (total > 0) " of ${total / 1_000_000} MB" else ""
                            }
                        }
                    } }
                    tmp.renameTo(File(llm.dir, name))
                    name
                }
            }
        }
        result.onSuccess { refresh(it); _note.value = "Downloaded $it" }
            .onFailure { _note.value = "Download failed: ${it.message}" }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || _busy.value) return
        val m = _model.value
        if (m == null) { _note.value = "Add a model first."; return }
        _msgs.value = _msgs.value + LocalMsg(next++, true, t)
        val replyId = next++
        _msgs.value = _msgs.value + LocalMsg(replyId, false, "")
        val history = _msgs.value.dropLast(1).map { Turn(if (it.mine) "user" else "assistant", it.text) }
        _busy.value = true
        val start = System.currentTimeMillis()
        var chars = 0
        job = viewModelScope.launch {
            llm.stream(m, "You are Aura, a helpful assistant running privately on the user's phone. Answer briefly.", history)
                .collect { c ->
                    when (c) {
                        is Chunk.Text -> {
                            chars += c.text.length
                            val dt = System.currentTimeMillis() - start
                            if (dt > 400) _speed.value = chars / 4f / (dt / 1000f)
                            _msgs.value = _msgs.value.map { if (it.id == replyId) it.copy(text = it.text + c.text) else it }
                        }
                        is Chunk.Failed -> _note.value = c.message
                        else -> {}
                    }
                }
            _busy.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalChatScreen(
    viewModel: LocalChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val models by viewModel.models.collectAsState()
    val model by viewModel.model.collectAsState()
    val msgs by viewModel.msgs.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val note by viewModel.note.collectAsState()
    val speed by viewModel.speed.collectAsState()
    var input by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var setup by remember { mutableStateOf(models.isEmpty()) }
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size, msgs.lastOrNull()?.text?.length) {
        if (msgs.isNotEmpty()) list.scrollToItem(msgs.lastIndex)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFrom(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Local model", fontWeight = FontWeight.SemiBold)
                        Text(
                            (model ?: "no model yet") + (speed?.let { " · %.0f t/s".format(it) } ?: ""),
                            fontFamily = GeistMono, fontSize = 10.sp, maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = viewModel::newChat) { Text("New") }
                    TextButton(onClick = { setup = !setup }) { Text(if (setup) "Hide" else "Models") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Message the local model…") }, shape = RoundedCornerShape(16.dp), maxLines = 4,
                )
                IconButton(onClick = { viewModel.send(input); input = "" }, enabled = !busy && input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            note?.let {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, Modifier.weight(1f), fontSize = 12.sp)
                        TextButton(onClick = viewModel::dismissNote) { Text("OK") }
                    }
                }
            }
            if (setup) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Runs fully on this phone — nothing is sent anywhere. Needs a MediaPipe model (.task or .litertlm), e.g. a Gemma 3 1B/2B file.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    models.forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = name == model, onClick = { viewModel.pick(name) })
                            Text(name, Modifier.weight(1f), fontFamily = GeistMono, fontSize = 12.sp)
                            TextButton(onClick = { viewModel.delete(name) }) { Text("Delete") }
                        }
                    }
                    Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Add file from phone") }
                    OutlinedTextField(
                        value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("…or paste a direct download link") },
                    )
                    OutlinedButton(onClick = { viewModel.download(url) }, enabled = url.isNotBlank()) { Text("Download") }
                    Text("Folder: ${viewModel.folder}", fontFamily = GeistMono, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(msgs, key = { it.id }) { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (m.mine) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.widthIn(max = 320.dp),
                        ) {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(m.text.ifEmpty { if (busy) "…" else "" }, Modifier.padding(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
