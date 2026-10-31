package dev.aura.auradroid.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aura.auradroid.data.repository.ContextSnapshot
import dev.aura.auradroid.ui.theme.GeistMono

/**
 * The instrument strip above the conversation: which model, where it runs,
 * how full its context is, what it has cost. Everything shown comes from live
 * state; a value that is not known yet reads "—" rather than a made-up number.
 */
@Composable
fun FlightDeck(
    model: String?,
    backend: String,
    link: String,
    linkOk: Boolean,
    project: String?,
    mode: String,
    thinking: Boolean,
    health: ContextSnapshot?,
    speed: Float? = null,
    messageCount: Int,
    split: Boolean,
    onToggleSplit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val mono = GeistMono
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val ok = MaterialTheme.colorScheme.primary
    val warn = MaterialTheme.colorScheme.error

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (thinking) "● RUN" else "● IDLE",
                    fontFamily = mono, fontSize = 11.sp,
                    color = if (thinking) MaterialTheme.colorScheme.tertiary else ok,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    model ?: "model —",
                    fontFamily = mono, fontSize = 11.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    (speed?.let { "%.0f t/s · ".format(it) } ?: "") + link + (health?.let { " · ctx ${"%.0f".format(it.usagePercent)}%" } ?: ""),
                    fontFamily = mono, fontSize = 10.sp,
                    color = if (linkOk) ok else warn,
                )
                TextButton(
                    onClick = onToggleSplit,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp),
                ) {
                    Text(if (split) "UNSPLIT" else "SPLIT ▤", fontFamily = mono, fontSize = 11.sp)
                }
                Text(if (open) "▴" else "▾", fontFamily = mono, fontSize = 12.sp, color = dim)
            }
            if (open) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Cell("BACKEND", backend, mono)
                    Cell("LINK", link, mono, if (linkOk) ok else warn)
                    Cell("MODE", mode, mono)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Cell("PROJECT", project ?: "—", mono)
                    Cell("MSGS", messageCount.toString(), mono)
                    Cell("TURNS", health?.turnCount?.toString() ?: "—", mono)
                    Cell("SPEED", speed?.let { "%.1f t/s".format(it) } ?: "—", mono)
                }
                Spacer(Modifier.height(6.dp))
                val pct = health?.usagePercent?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CTX", fontFamily = mono, fontSize = 9.sp, color = dim)
                    Spacer(Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { pct ?: 0f },
                        modifier = Modifier.weight(1f).height(5.dp),
                        color = if ((pct ?: 0f) > 0.8f) warn else ok,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        health?.let { "${it.estimatedTokens}/${it.contextWindow} · ${"%.0f".format(it.usagePercent)}%" } ?: "—",
                        fontFamily = mono, fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                val (cpu, ram) = rememberSysHistory(active = open)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SysGraph("CPU · this app", cpu, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    SysGraph("RAM · phone", ram, ok, Modifier.weight(1f))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "COST " + (health?.let { "$" + "%.4f".format(it.totalCostUsd) } ?: "—"),
                    fontFamily = mono, fontSize = 10.sp, color = dim,
                )
            }
        }
    }
}

@Composable
private fun Cell(label: String, value: String, mono: androidx.compose.ui.text.font.FontFamily, color: Color = Color.Unspecified) {
    Column {
        Text(label, fontFamily = mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = mono, fontSize = 11.sp, maxLines = 1, color = color)
    }
}
