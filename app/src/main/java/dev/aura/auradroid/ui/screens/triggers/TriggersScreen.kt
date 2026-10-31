package dev.aura.auradroid.ui.screens.triggers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.aura.auradroid.data.trigger.TriggerRule

/**
 * The rule table, in one column: every way Aura wakes herself. Adding is a
 * dialog with the fields a rule actually has — nothing more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggersScreen(
    viewModel: TriggersViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val rules by viewModel.rules.collectAsState()
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Triggers", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { adding = true }) {
                        Icon(Icons.Default.Add, "Add trigger")
                    }
                },
            )
        },
    ) { padding ->
        if (rules.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Text(
                    "No triggers yet.\n\nAdd one and Aura will run a task on a schedule, " +
                        "or when an app sends a notification.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(rules, key = { it.id }) { rule ->
                    RuleRow(
                        rule = rule,
                        onToggle = { viewModel.toggle(rule) },
                        onDelete = { viewModel.delete(rule) },
                        onRun = { viewModel.runNow(rule) },
                    )
                }
            }
        }
    }

    if (adding) {
        AddRuleDialog(
            onDismiss = { adding = false },
            onAdd = {
                viewModel.add(it)
                adding = false
            },
        )
    }
}

@Composable
private fun RuleRow(
    rule: TriggerRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onRun: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rule.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        describe(rule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onRun) {
                    Icon(Icons.Default.PlayArrow, null); Text("Run now")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null); Text("Delete")
                }
            }
        }
    }
}

private fun describe(rule: TriggerRule): String = when {
    rule.isTime -> "every ${rule.intervalMinutes} min"
    rule.sourcePackage.isNotBlank() && rule.keyword.isNotBlank() ->
        "when ${rule.sourcePackage} sends \"${rule.keyword}\""
    rule.sourcePackage.isNotBlank() -> "when ${rule.sourcePackage} notifies"
    rule.keyword.isNotBlank() -> "when any app sends \"${rule.keyword}\""
    else -> "when any app notifies"
}

@Composable
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    onAdd: (TriggerRule) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var isTime by remember { mutableStateOf(true) }
    var interval by remember { mutableStateOf("30") }
    var sourcePackage by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New trigger") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Name") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = isTime, onClick = { isTime = true }, label = { Text("On a schedule") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !isTime, onClick = { isTime = false }, label = { Text("On a notification") })
                }
                if (isTime) {
                    OutlinedTextField(
                        interval, { interval = it },
                        label = { Text("Every N minutes") }, singleLine = true,
                    )
                } else {
                    OutlinedTextField(
                        sourcePackage, { sourcePackage = it },
                        label = { Text("App package (empty = any)") }, singleLine = true,
                    )
                    OutlinedTextField(
                        keyword, { keyword = it },
                        label = { Text("Text contains (empty = any)") }, singleLine = true,
                    )
                }
                OutlinedTextField(
                    prompt, { prompt = it },
                    label = { Text("What Aura should do") },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedLabel = label.trim()
                    val trimmedPrompt = prompt.trim()
                    if (trimmedLabel.isEmpty() || trimmedPrompt.isEmpty()) return@TextButton
                    onAdd(
                        TriggerRule(
                            label = trimmedLabel,
                            prompt = trimmedPrompt,
                            kind = if (isTime) TriggerRule.Kind.TIME else TriggerRule.Kind.NOTIFICATION,
                            intervalMinutes = interval.toLongOrNull() ?: TriggerRule.DEFAULT_INTERVAL_MINUTES,
                            sourcePackage = sourcePackage.trim(),
                            keyword = keyword.trim(),
                        ),
                    )
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
