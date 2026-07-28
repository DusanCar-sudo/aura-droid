package dev.aura.auradroid.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Aura Logo - based on the brand favicon
 * Outer ring in Aura cyan, inner circle in Aura ruby
 */
@Composable
fun AuraLogo(
    modifier: Modifier = Modifier,
    cyanColor: Color = AuraCyan,
    rubyColor: Color = AuraRuby
) {
    Canvas(modifier = modifier.size(32.dp)) {
        val canvasWidth = size.width
        val center = canvasWidth / 2
        val outerRadius = canvasWidth * 0.41f
        val innerRadius = canvasWidth * 0.16f

        // Outer ring - Aura cyan
        drawCircle(
            color = cyanColor,
            radius = outerRadius,
            center = androidx.compose.ui.geometry.Offset(center, center),
            style = Stroke(width = canvasWidth * 0.0625f)
        )

        // Inner circle - Aura ruby
        drawCircle(
            color = rubyColor,
            radius = innerRadius,
            center = androidx.compose.ui.geometry.Offset(center, center)
        )
    }
}
