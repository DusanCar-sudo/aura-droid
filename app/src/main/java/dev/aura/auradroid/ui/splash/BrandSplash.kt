package dev.aura.auradroid.ui.splash

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.aura.auradroid.R
import dev.aura.auradroid.ui.theme.AuraCream
import dev.aura.auradroid.ui.theme.AuraForest
import dev.aura.auradroid.ui.theme.AuraGold
import dev.aura.auradroid.ui.theme.AuraLogo
import dev.aura.auradroid.ui.theme.AuraStraw
import dev.aura.auradroid.ui.theme.Geist
import dev.aura.auradroid.ui.theme.StripeCyan
import dev.aura.auradroid.ui.theme.StripeGreen
import dev.aura.auradroid.ui.theme.StripeRed
import dev.aura.auradroid.ui.theme.StripeYellow
import kotlinx.coroutines.delay

private const val WORDMARK = "AURA DROID"

// The footer wordmark of the website: Geist 600, tracked so tightly that the
// letters connect, filled with a cream-to-olive gradient that fades into the
// ground (BRAND.md §8).
private val WordmarkBrush = Brush.verticalGradient(
    0.00f to Color(0xFFFFF7D6),
    0.38f to Color(0xFFD9CF9F),
    0.72f to Color(0xFF8D8A62),
    1.00f to Color(0x805A5C3C),
)

/**
 * The launch screen, drawn over the app until it calls [onFinished] (about a
 * second and a half, or a tap): the light-point landscape, the peak, the
 * tagline, the four stripes lighting in order, and the big connected
 * AURA DROID wordmark rising from the bottom edge.
 *
 * It is the same identity as the website's hero and footer, so the phone and
 * the page read as one product. With animations switched off in system
 * settings it appears fully drawn and leaves sooner.
 */
@Composable
fun BrandSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val t = remember { Animatable(if (animate) 0f else 1f) }

    LaunchedEffect(Unit) {
        if (animate) t.animateTo(1f, tween(durationMillis = 900, easing = LinearOutSlowInEasing))
        delay(if (animate) 650L else 500L)
        onFinished()
    }

    // Each element eases in over its own slice of the timeline.
    fun slice(from: Float, to: Float): Float = ((t.value - from) / (to - from)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AuraForest)
            .pointerInput(Unit) { detectTapGestures { onFinished() } }
            .semantics(mergeDescendants = true) { contentDescription = "Aura Droid" },
    ) {
        Image(
            painter = painterResource(R.drawable.splash_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = slice(0f, 0.55f) },
        )

        // Keep the text legible over the picture: a soft pool of shade behind
        // the tagline, and the ground rising at the bottom for the wordmark.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color(0x59070907),
                        0.25f to Color.Transparent,
                        0.60f to Color.Transparent,
                        1.00f to Color(0xCC070907),
                    ),
                )
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color(0x99070907), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.44f),
                            radius = size.width * 0.9f,
                        ),
                    )
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .padding(bottom = 96.dp)
                .graphicsLayer {
                    alpha = slice(0.15f, 0.65f)
                    translationY = (1f - slice(0.15f, 0.65f)) * 18.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuraLogo(
                modifier = Modifier.size(72.dp),
                bodyColor = AuraCream,
                coreColor = AuraGold,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = buildAnnotatedString {
                    append("Your coding agent,\n")
                    withStyle(SpanStyle(color = AuraStraw)) { append("in your pocket.") }
                },
                color = AuraCream,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = Geist,
                    fontWeight = FontWeight.W500,
                    fontSize = 28.sp,
                    lineHeight = 30.sp,
                    letterSpacing = (-0.04).em,
                ),
            )
            Spacer(Modifier.height(22.dp))
            StripeRun(progress = slice(0.35f, 1f))
        }

        Wordmark(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            reveal = slice(0.1f, 0.7f),
        )
    }
}

/** AURA DROID, sized so it spans the screen width whatever the phone or font scale. */
@Composable
private fun Wordmark(reveal: Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val targetPx = constraints.maxWidth * 0.94f
        val reference = TextStyle(
            fontFamily = Geist,
            fontWeight = FontWeight.W600,
            fontSize = 100.sp,
            letterSpacing = (-0.07).em,
        )
        val measured = remember(targetPx) {
            measurer.measure(AnnotatedString(WORDMARK), reference, maxLines = 1, softWrap = false)
        }
        val sizeSp = (100f * targetPx / measured.size.width.coerceAtLeast(1)).sp
        val sizePx = with(density) { sizeSp.toPx() }

        BasicText(
            text = WORDMARK,
            maxLines = 1,
            softWrap = false,
            style = reference.copy(
                fontSize = sizeSp,
                lineHeight = sizeSp,
                brush = WordmarkBrush,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .fillMaxWidth()
                // Sunk a little below the edge, as on the site, so it reads as
                // rising out of the ground rather than sitting on a shelf.
                .offset(y = with(density) { (sizePx * 0.10f).toDp() })
                .graphicsLayer {
                    alpha = reveal
                    translationY = (1f - reveal) * sizePx * 0.25f
                },
        )
    }
}

/** The four stripes, in Sinclair order, lighting one after another. */
@Composable
private fun StripeRun(progress: Float) {
    val colors = listOf(StripeRed, StripeYellow, StripeGreen, StripeCyan)
    Canvas(Modifier.size(width = 76.dp, height = 22.dp)) {
        val bar = size.width / (colors.size + 0.5f)   // slanted bars overlap by half a bar
        val slant = size.height * 0.5f                // 5 over 10, like the mark
        colors.forEachIndexed { i, color ->
            val on = (progress * (colors.size + 0.6f) - i).coerceIn(0f, 1f)
            val x = i * bar
            val path = Path().apply {
                moveTo(x, size.height)
                lineTo(x + bar, size.height)
                lineTo(x + bar + slant, 0f)
                lineTo(x + slant, 0f)
                close()
            }
            drawPath(path, color.copy(alpha = 0.16f + 0.84f * on))
        }
    }
}
