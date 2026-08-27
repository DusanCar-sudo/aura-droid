package dev.aura.auradroid.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Aura Logo - rounded triangular mark from the app icon.
 */
@Composable
fun AuraLogo(
    modifier: Modifier = Modifier,
    cyanColor: Color = AuraCyan,
    rubyColor: Color = AuraRuby
) {
    Canvas(modifier = modifier.size(32.dp)) {
        drawAuraMark(cyanColor = cyanColor, violetColor = rubyColor)
    }
}

private fun DrawScope.drawAuraMark(cyanColor: Color, violetColor: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.18f, h * 0.78f)
        cubicTo(w * 0.27f, h * 0.54f, w * 0.38f, h * 0.29f, w * 0.48f, h * 0.14f)
        cubicTo(w * 0.52f, h * 0.08f, w * 0.58f, h * 0.08f, w * 0.62f, h * 0.14f)
        cubicTo(w * 0.73f, h * 0.31f, w * 0.84f, h * 0.55f, w * 0.93f, h * 0.78f)
        cubicTo(w * 0.77f, h * 0.68f, w * 0.59f, h * 0.63f, w * 0.41f, h * 0.66f)
        cubicTo(w * 0.30f, h * 0.68f, w * 0.23f, h * 0.72f, w * 0.18f, h * 0.78f)
    }

    drawPath(
        path = path,
        color = cyanColor.copy(alpha = 0.24f),
        style = Stroke(
            width = w * 0.19f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
    drawPath(
        path = path,
        color = cyanColor,
        style = Stroke(
            width = w * 0.105f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
    drawPath(
        path = path,
        color = violetColor.copy(alpha = 0.7f),
        style = Stroke(
            width = w * 0.045f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}
