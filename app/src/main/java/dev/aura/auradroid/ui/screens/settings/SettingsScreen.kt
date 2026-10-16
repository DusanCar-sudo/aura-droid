package dev.aura.auradroid.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.aura.auradroid.data.settings.ThemeMode
import dev.aura.auradroid.data.settings.ThemePalette
import dev.aura.auradroid.data.standalone.ModelFactory
import dev.aura.auradroid.ui.theme.AuraLogo
import dev.aura.auradroid.ui.theme.GeistMono
import dev.aura.auradroid.ui.theme.previewAccent
import dev.aura.auradroid.ui.theme.previewGround
import dev.aura.auradroid.ui.theme.previewSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onUnpair: () -> Unit,
    /** Opens the mode chooser, so a desktop can be paired at any time. */
    onSetUpDesktop: () -> Unit = {},
    onOpenMemory: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var confirmUnpair by remember { mutableStateOf(false) }
    var editingStandalone by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AuraLogo(
                            modifier = Modifier.size(24.dp),
                            bodyColor = MaterialTheme.colorScheme.onBackground,
                            coreColor = MaterialTheme.colorScheme.primary,
                        )
                        Text("Settings", fontWeight = FontWeight.SemiBold)
                    }
                },
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section("Paired desktop") {
                if (state.host == null) {
                    Text(
                        "No desktop paired. This phone is running Aura on its own.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Without this there is no way back to pairing once the
                    // phone-only mode is set up: the chooser only appears when
                    // nothing at all is configured, so someone who picked
                    // "on this phone" was stuck with it.
                    Button(
                        onClick = onSetUpDesktop,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Computer, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Connect a desktop")
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    when (state.reachable) {
                                        true -> MaterialTheme.colorScheme.primary
                                        false -> MaterialTheme.colorScheme.error
                                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                ),
                        )
                        Text(
                            "${state.host}:${state.port}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = GeistMono,
                        )
                    }
                    state.projectName?.let { Row2("Project", it) }
                    state.model?.let { Row2("Model", it) }
                    state.identity?.let {
                        Row2("Identity", it)
                        Text(
                            "Over Wi-Fi this must match the Identity shown by " +
                                "`aura serve --lan`. If it ever changes without you " +
                                "re-pairing, something else is answering.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.reachable == false) {
                        Text(
                            "Not reachable right now. Check that `aura serve` is running.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Section("Appearance") {
                AppearanceSection(
                    mode = state.appearance.themeMode,
                    palette = state.appearance.palette,
                    dynamicColor = state.appearance.dynamicColor,
                    onModeChange = viewModel::setThemeMode,
                    onPaletteChange = viewModel::setThemePalette,
                    onDynamicColor = viewModel::setDynamicColor,
                )
            }

            Section("Approvals") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Approve all actions", fontWeight = FontWeight.Medium)
                        Text(
                            "Let Aura run every tool without asking. Off means it " +
                                "asks before each action, as usual.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.autoApproveAll,
                        onCheckedChange = viewModel::setAutoApproveAll,
                    )
                }
                Text(
                    "All actions require approval unless this is on. It changes " +
                        "the default in every conversation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("Sandbox") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sandbox out", fontWeight = FontWeight.Medium)
                        Text(
                            "Let Aura reach beyond the app sandbox on request: " +
                                "read/write files elsewhere on the phone and move " +
                                "anywhere the device user can. Sensitive app data " +
                                "stays off-limits.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.sandboxOut,
                        onCheckedChange = viewModel::setSandboxOut,
                    )
                }
            }

            Section("Elevated shell (Shizuku)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (state.shizukuStatus) {
                                            ShizukuStatus.READY -> MaterialTheme.colorScheme.primary
                                            ShizukuStatus.PERMISSION_NEEDED -> Color(0xFFF59E0B)
                                            ShizukuStatus.NOT_RUNNING -> MaterialTheme.colorScheme.onSurfaceVariant
                                            ShizukuStatus.NOT_INSTALLED -> MaterialTheme.colorScheme.error
                                        },
                                    ),
                            )
                            Text(
                                when (state.shizukuStatus) {
                                    ShizukuStatus.READY -> "Active (uid 2000 / adb shell)"
                                    ShizukuStatus.PERMISSION_NEEDED -> "Permission required"
                                    ShizukuStatus.NOT_RUNNING -> "Service not running"
                                    ShizukuStatus.NOT_INSTALLED -> "Not installed"
                                },
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Text(
                            when (state.shizukuStatus) {
                                ShizukuStatus.READY ->
                                    "Commands run with elevated privileges. Allows app management, system settings, dumpsys, and full storage access."
                                ShizukuStatus.PERMISSION_NEEDED ->
                                    "Shizuku service is running, but this app needs permission to communicate with it."
                                ShizukuStatus.NOT_RUNNING ->
                                    "Shizuku is installed. Start it via Wireless Debugging or computer ADB."
                                ShizukuStatus.NOT_INSTALLED ->
                                    "Shizuku is required for elevated shell commands on non-rooted devices."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when (state.shizukuStatus) {
                    ShizukuStatus.PERMISSION_NEEDED -> {
                        Button(
                            onClick = viewModel::requestShizukuPermission,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Request Shizuku permission")
                        }
                    }
                    ShizukuStatus.NOT_INSTALLED -> {
                        LinkRow(
                            label = "Get Shizuku",
                            shown = "shizuku.rikka.app",
                            url = "https://shizuku.rikka.app",
                        )
                    }
                    ShizukuStatus.NOT_RUNNING -> {
                        Text(
                            "Start command:\nadb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = GeistMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ShizukuStatus.READY -> {
                        // Already active
                    }
                }
            }

            Section("Standalone") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Run without a desktop", fontWeight = FontWeight.Medium)
                        Text(
                            // Said plainly: this is the one place the phone
                            // holds a provider credential, and the user should
                            // know that before turning it on rather than after.
                            "Talk to a model directly from this phone. Needs an " +
                                "API key, which is stored encrypted on the device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.standalone,
                        onCheckedChange = { on ->
                            if (on) editingStandalone = true else viewModel.disableStandalone()
                        },
                    )
                }
                if (state.standalone) {
                    val provName = ModelFactory.detectProvider(state.baseUrl)?.name ?: "Custom"
                    Row2("Provider", provName)
                    Row2("Model", state.standaloneModel.ifBlank { "—" })
                    Row2("Endpoint", state.baseUrl.ifBlank { "—" })
                    Row2("API key", if (state.hasApiKey) "stored on device" else "none")
                    TextButton(onClick = { editingStandalone = true }) { Text("Change") }
                }
            }

            // The agent's own notes. Visible and deletable on principle: it
            // writes these without being asked, and something that remembers
            // things about you that you cannot read is not something to ship.
            Section("What Aura remembers") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (state.memoryCount) {
                                0 -> "Nothing yet"
                                1 -> "1 note"
                                else -> "${state.memoryCount} notes"
                            },
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Facts she saved herself while you talked — preferences, " +
                                "decisions, what you are building.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onOpenMemory) { Text("View") }
                }
            }

            // No API-key field unless standalone is on: when paired with a
            // desktop the keys live there, and the phone holds only a pairing
            // token.
            Section("About") {
                // Read from the build, not typed in again here. The literal
                // that used to sit in this line was already a version behind
                // the one in build.gradle.kts.
                Row2("Version", dev.aura.auradroid.BuildConfig.VERSION_NAME)
                LinkRow(
                    label = "Aura Code CLI",
                    shown = "github.com/DusanCar-sudo/aura-code",
                    url = "https://github.com/DusanCar-sudo/aura-code",
                )
                LinkRow(
                    label = "Website",
                    shown = "aurawebsite-eta.vercel.app",
                    url = "https://aurawebsite-eta.vercel.app",
                )
                Text(
                    if (state.standalone) {
                        "This phone is talking to a model directly. The paired " +
                            "desktop, if any, is not being used."
                    } else {
                        "Provider and API key are configured on the desktop with " +
                            "`aura setup --web`. Turn on Standalone above to use a " +
                            "model directly from this phone instead."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(
                    Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                )
                Text(
                    "© 2026 LeanProgressIQ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            if (state.host != null) {
                OutlinedButton(
                    onClick = { confirmUnpair = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Default.LinkOff, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Unpair this desktop")
                }
            }
        }
    }

    if (editingStandalone) {
        var key by rememberSaveable { mutableStateOf("") }
        var selectedProviderId by rememberSaveable {
            mutableStateOf(
                state.activeProviderId
                    ?: ModelFactory.detectProvider(state.baseUrl)?.id
                    ?: "openrouter"
            )
        }
        var base by rememberSaveable {
            mutableStateOf(state.baseUrl.ifBlank {
                ModelFactory.findById(selectedProviderId)?.defaultBaseUrl ?: "https://openrouter.ai/api/v1"
            })
        }
        var model by rememberSaveable {
            mutableStateOf(state.standaloneModel.ifBlank {
                ModelFactory.findById(selectedProviderId)?.defaultModel ?: "openrouter/auto"
            })
        }
        var showKey by rememberSaveable { mutableStateOf(false) }
        var testing by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

        val activeFactoryProvider = ModelFactory.findById(selectedProviderId)
        val hasSavedKeyForProvider = state.savedProviders.contains(selectedProviderId) ||
            (selectedProviderId == "local") ||
            (selectedProviderId == (state.activeProviderId ?: ModelFactory.detectProvider(state.baseUrl)?.id) && state.hasApiKey)

        AlertDialog(
            onDismissRequest = { editingStandalone = false },
            title = { Text("Model Factory") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Provider",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(ModelFactory.PROVIDERS) { p ->
                            val isSaved = state.savedProviders.contains(p.id) || p.id == "local"
                            val isSelected = selectedProviderId == p.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedProviderId = p.id
                                    base = p.defaultBaseUrl
                                    model = p.defaultModel
                                    testResult = null
                                    key = ""
                                },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(p.name, style = MaterialTheme.typography.labelSmall)
                                        if (isSaved) {
                                            Spacer(Modifier.width(4.dp))
                                            Text("●", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                },
                            )
                        }
                    }

                    if (activeFactoryProvider != null && activeFactoryProvider.models.isNotEmpty()) {
                        Text(
                            "Curated Models (${activeFactoryProvider.name})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(activeFactoryProvider.models) { m ->
                                val isSelected = model == m.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        model = m.id
                                        testResult = null
                                    },
                                    label = {
                                        Column {
                                            Text(
                                                (if (m.isFree) "[Free] " else "") + m.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            )
                                            if (m.speed.isNotBlank()) {
                                                Text(
                                                    m.speed,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = base,
                        onValueChange = {
                            base = it
                            testResult = null
                        },
                        label = { Text("Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = {
                            model = it
                            testResult = null
                        },
                        label = { Text("Model ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (selectedProviderId != "local" && !base.trim().equals("local", true)) {
                        OutlinedTextField(
                            value = key,
                            onValueChange = {
                                key = it
                                testResult = null
                            },
                            label = {
                                Text(
                                    if (hasSavedKeyForProvider) "API key (Saved on phone)"
                                    else "API key"
                                )
                            },
                            singleLine = true,
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(
                                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showKey) "Hide key" else "Show key",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            if (hasSavedKeyForProvider) {
                                "✓ Key saved on device. Leave blank to keep existing key."
                            } else {
                                "Enter API key. It will be encrypted into hardware KeyStore and remembered for ${activeFactoryProvider?.name ?: "this provider"}."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasSavedKeyForProvider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    testResult?.let { (ok, msg) ->
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    state.saveError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            testing = true
                            testResult = null
                            viewModel.testStandalone(key, base, model, selectedProviderId) { res ->
                                testing = false
                                res.onSuccess {
                                    testResult = true to it
                                }.onFailure {
                                    testResult = false to (it.message ?: "Connection failed")
                                }
                            }
                        },
                        enabled = !testing && base.isNotBlank() && model.isNotBlank() &&
                            (key.isNotBlank() || hasSavedKeyForProvider || base.trim().equals("local", true)),
                    ) {
                        if (testing) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(4.dp))
                            Text("Testing...")
                        } else {
                            Text("Test")
                        }
                    }
                    TextButton(
                        onClick = {
                            viewModel.enableStandalone(key, base, model, selectedProviderId)
                            editingStandalone = false
                        },
                        enabled = base.isNotBlank() && model.isNotBlank() &&
                            (key.isNotBlank() || hasSavedKeyForProvider || base.trim().equals("local", true)),
                    ) { Text("Save") }
                }
            },
            dismissButton = {
                TextButton(onClick = { editingStandalone = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            icon = { Icon(Icons.Default.Info, null) },
            title = { Text("Unpair?") },
            text = {
                Text(
                    "The stored token will be deleted from this phone. You will " +
                        "need the token from `aura serve` to connect again.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    viewModel.unpair(onUnpair)
                }) {
                    Text("Unpair", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                content = content,
            )
        }
    }
}

/**
 * Theme mode + dynamic colour.
 *
 * System is first because it is the safe default — it already matches what the
 * phone is in. Dynamic colour is offered only on Android 12+, the only version
 * that supports it; on older devices the toggle would do nothing, so it is
 * omitted rather than shown disabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(
    mode: ThemeMode,
    palette: ThemePalette,
    dynamicColor: Boolean,
    onModeChange: (ThemeMode) -> Unit,
    onPaletteChange: (ThemePalette) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Palette, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Theme Mode", fontWeight = FontWeight.Medium)
    }

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val options = listOf(
            ThemeMode.SYSTEM to "System",
            ThemeMode.DARK to "Dark",
            ThemeMode.LIGHT to "Light",
        )
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onModeChange(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1) }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Palette", fontWeight = FontWeight.Medium)
            Text(
                if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    "Dynamic wallpaper colour is currently overriding the palette."
                } else {
                    "Choose Aura's visual identity."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemePalette.entries.forEach { p ->
            val isSelected = palette == p && (!dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            Surface(
                onClick = {
                    if (dynamicColor) onDynamicColor(false)
                    onPaletteChange(p)
                },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                },
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(p.previewGround)
                                .border(1.dp, Color(0x33888888), CircleShape),
                        )
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(p.previewSurface)
                                .border(1.dp, Color(0x33888888), CircleShape),
                        )
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(p.previewAccent)
                                .border(1.dp, Color(0x33888888), CircleShape),
                        )
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            p.title,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            p.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                    )
                }
            }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dynamic colour", fontWeight = FontWeight.Medium)
                Text(
                    "Match the palette to your wallpaper (overrides palette selection).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = dynamicColor, onCheckedChange = onDynamicColor)
        }
    }
}

/**
 * A row whose value opens in a browser.
 *
 * The URL is shown rather than hidden behind a word like "here": on a phone
 * there is no status bar to preview a link in, and someone about to leave the
 * app for a site is entitled to see which one first.
 */
@Composable
private fun LinkRow(label: String, shown: String, url: String) {
    val uriHandler = LocalUriHandler.current

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { runCatching { uriHandler.openUri(url) } },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                shown,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 190.dp),
            )
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Row2(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
