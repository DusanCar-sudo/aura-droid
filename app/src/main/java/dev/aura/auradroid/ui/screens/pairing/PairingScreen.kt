package dev.aura.auradroid.ui.screens.pairing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.aura.auradroid.ui.theme.AuraLogo

/** Which way of running Aura the user is setting up. */
private enum class Path { NONE, DESKTOP, PHONE }

/**
 * The first screen: pick how Aura runs, then set that way up.
 *
 * Both options are offered as equals rather than one being the real path and
 * the other a footnote at the bottom. The previous layout put the desktop form
 * first and the phone-only option below a divider, which read as a fallback for
 * when pairing failed — and someone who simply has no desktop had to scroll
 * past a form they could never complete to find the thing they wanted.
 */
@Composable
fun PairingScreen(
    viewModel: PairingViewModel = hiltViewModel(),
    onPaired: () -> Unit,
    /** Present only when this screen was opened from inside the app. */
    onNavigateBack: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    var path by rememberSaveable { mutableStateOf(Path.NONE) }

    LaunchedEffect(state.pairedProject) {
        if (state.pairedProject != null) onPaired()
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // No Scaffold here to carry system-bar insets, and the activity
                // draws edge to edge — without this the logo runs under the
                // status bar and the buttons under the navigation bar.
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            onNavigateBack?.let { back ->
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            }

            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                AuraLogo(
                    modifier = Modifier.size(56.dp),
                    cyanColor = MaterialTheme.colorScheme.primary,
                    rubyColor = MaterialTheme.colorScheme.tertiary,
                )
            }

            Text(
                "How should Aura run?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "You can change this later, and switch back and forth.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OptionCard(
                icon = Icons.Default.Computer,
                title = "With my desktop",
                subtitle = "Full agent, with your project open on the computer. " +
                    "Needs `aura serve` running there.",
                selected = path == Path.DESKTOP,
                onClick = { path = if (path == Path.DESKTOP) Path.NONE else Path.DESKTOP },
            ) {
                DesktopForm(state, viewModel)
            }

            OptionCard(
                icon = Icons.Default.PhoneAndroid,
                title = "On this phone only",
                subtitle = "No computer needed. Uses your own API key, and Aura can " +
                    "still use tools, files and memory here on the phone.",
                selected = path == Path.PHONE,
                onClick = { path = if (path == Path.PHONE) Path.NONE else Path.PHONE },
            ) {
                PhoneForm(state, viewModel)
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * One of the two ways in, expanding in place when chosen.
 *
 * In place rather than in a dialog: a dialog on a phone loses most of its room
 * to the keyboard the moment the first field is focused, and this one has three.
 */
@Composable
private fun OptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        shape = RoundedCornerShape(16.dp),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    icon,
                    null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = selected) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun DesktopForm(state: PairingUiState, viewModel: PairingViewModel) {
    SetupHint()

    OutlinedTextField(
        value = state.host,
        onValueChange = viewModel::onHostChange,
        label = { Text("Address") },
        leadingIcon = { Icon(Icons.Default.Dns, null) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = state.port,
        onValueChange = viewModel::onPortChange,
        label = { Text("Port") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )

    TokenField(value = state.token, onValueChange = viewModel::onTokenChange)

    state.error?.let { ErrorNote(it) }

    Button(
        onClick = viewModel::pair,
        enabled = !state.testing,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        if (state.testing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(10.dp))
            Text("Connecting…")
        } else {
            Text("Connect")
        }
    }
}

@Composable
private fun PhoneForm(state: PairingUiState, viewModel: PairingViewModel) {
    // Seeded from the last provider used, so returning here is one tap rather
    // than three fields — and rememberSaveable, or rotating the phone mid-setup
    // wipes what was typed.
    var base by rememberSaveable(state.baseUrl) { mutableStateOf(state.baseUrl) }
    var model by rememberSaveable(state.model) { mutableStateOf(state.model) }
    var key by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = base,
        onValueChange = { base = it },
        label = { Text("Base URL") },
        placeholder = { Text("https://api.deepseek.com") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = model,
        onValueChange = { model = it },
        label = { Text("Model ID") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text(if (state.hasApiKey) "API key (already saved)" else "API key") },
        singleLine = true,
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
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.hasApiKey) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "A key is stored on this phone. Leave the field empty to keep it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Text(
            "Stored encrypted on this phone, and kept even if you later pair and " +
                "unpair a desktop.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    state.error?.let { ErrorNote(it) }

    Button(
        onClick = { viewModel.useStandalone(key, base, model) },
        enabled = base.isNotBlank() && model.isNotBlank() &&
            (key.isNotBlank() || state.hasApiKey),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text("Start on this phone")
    }
}

@Composable
private fun ErrorNote(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/**
 * The USB path needs `adb reverse`, which nobody guesses. Showing the two
 * commands here is the difference between pairing working and the user
 * bouncing off an unreachable-host error.
 */
@Composable
private fun SetupHint() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Over USB",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "adb reverse tcp:7337 tcp:7337\naura serve\naura devices add \"my phone\"",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Then type the six-character code it prints. This phone swaps it " +
                    "for its own key, so nobody types anything long.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun TokenField(value: String, onValueChange: (String) -> Unit) {
    // Shown, not masked. This is normally a six-character code being copied off
    // a screen a metre away, and hiding it only stops the person checking what
    // they typed. The toggle stays for the long-token case, where masking earns
    // its keep.
    var visible by remember { mutableStateOf(true) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Pairing code") },
        placeholder = { Text("6 characters, e.g. 4F9K2A") },
        leadingIcon = { Icon(Icons.Default.Key, null) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Hide code" else "Show code",
                )
            }
        },
        visualTransformation =
            if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
