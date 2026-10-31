package dev.aura.auradroid.ui.screens.chat

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aura.auradroid.ui.theme.GeistMono
import kotlinx.coroutines.delay

/** One reading per second, newest last. CPU is this app's own share of all cores. */
@Composable
fun rememberSysHistory(active: Boolean, points: Int = 60): Pair<List<Float>, List<Float>> {
    val context = LocalContext.current
    var cpu by remember { mutableStateOf(emptyList<Float>()) }
    var ram by remember { mutableStateOf(emptyList<Float>()) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        var lastCpu = Process.getElapsedCpuTime()
        var lastAt = System.nanoTime()
        while (true) {
            delay(1000)
            val c = Process.getElapsedCpuTime()
            val t = System.nanoTime()
            val used = (c - lastCpu).toFloat()
            val wall = (t - lastAt) / 1_000_000f
            lastCpu = c; lastAt = t
            val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
            val usedFrac = 1f - info.availMem.toFloat() / info.totalMem.toFloat()
            cpu = (cpu + (used / wall / cores).coerceIn(0f, 1f)).takeLast(points)
            ram = (ram + usedFrac.coerceIn(0f, 1f)).takeLast(points)
        }
    }
    return cpu to ram
}

@Composable
fun SysGraph(label: String, values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Row {
            Text(label, fontFamily = GeistMono, fontSize = 9.sp, color = dim)
            Spacer(Modifier.weight(1f))
            Text(
                values.lastOrNull()?.let { "%.0f%%".format(it * 100) } ?: "—",
                fontFamily = GeistMono, fontSize = 10.sp,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            drawLine(dim.copy(alpha = 0.25f), Offset(0f, size.height), Offset(size.width, size.height))
            if (values.size < 2) return@Canvas
            val step = size.width / (values.size - 1)
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = i * step
                val y = size.height - v * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2.5f))
        }
    }
}
