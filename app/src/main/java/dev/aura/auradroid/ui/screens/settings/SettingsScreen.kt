package dev.aura.auradroid.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.aura.auradroid.data.settings.ThemeMode
import dev.aura.auradroid.ui.theme.AuraLogo

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
                            cyanColor = MaterialTheme.colorScheme.primary,
                            rubyColor = MaterialTheme.colorScheme.tertiary,
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
                            fontFamily = FontFamily.Monospace,
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
                    dynamicColor = state.appearance.dynamicColor,
                    onModeChange = viewModel::setThemeMode,
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
        var base by rememberSaveable {
            mutableStateOf(state.baseUrl.ifBlank { "https://api.deepseek.com" })
        }
        var model by rememberSaveable {
            mutableStateOf(state.standaloneModel.ifBlank { "deepseek-chat" })
        }
        var showKey by rememberSaveable { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingStandalone = false },
            title = { Text("Standalone setup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = base,
                        onValueChange = { base = it },
                        label = { Text("Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = {
                            Text(if (state.hasApiKey) "API key (already saved)" else "API key")
                        },
                        singleLine = true,
                        // Masked by default and never read back from storage:
                        // a key that can be displayed is a key that can be
                        // shoulder-surfed off a screen.
                        visualTransformation = if (showKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
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
                    if (state.hasApiKey) {
                        Text(
                            "Leave empty to keep the key already stored. It survives " +
                                "pairing and unpairing a desktop.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.saveError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.enableStandalone(key, base, model)
                        editingStandalone = false
                    },
                    enabled = base.isNotBlank() && model.isNotBlank() &&
                        (key.isNotBlank() || state.hasApiKey),
                ) { Text("Save") }
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
    dynamicColor: Boolean,
    onModeChange: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Palette, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Theme", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow {
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
                ) { Text(label) }
            }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dynamic colour", fontWeight = FontWeight.Medium)
                Text(
                    "Match the palette to your wallpaper.",
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
