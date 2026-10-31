package dev.aura.auradroid.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.aura.auradroid.R

// Geist and Geist Mono (SIL OFL 1.1, see third_party/Geist-OFL.txt), the same
// upright pair as the websites (BRAND.md §6). Latin subset; other scripts and
// symbols fall back to the system font glyph by glyph.
val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.W400),
    Font(R.font.geist_medium, FontWeight.W500),
    Font(R.font.geist_semibold, FontWeight.W600),
    // W700 asks for bold in a few places; the closest face is semibold.
    Font(R.font.geist_semibold, FontWeight.W700),
)

val GeistMono = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.W400),
    Font(R.font.geist_mono_medium, FontWeight.W500),
    Font(R.font.geist_mono_medium, FontWeight.W600),
    Font(R.font.geist_mono_medium, FontWeight.W700),
)

// The rule: large type runs tight and small type runs open. Body text is
// untracked, since Geist is drawn for it.
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.W500,
        fontSize = 57.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.05).em,
    ),
    displayMedium = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.W500,
        fontSize = 45.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.045).em,
    ),
    displaySmall = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.W500,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.045).em,
    ),
    headlineLarge = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.W500,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.04).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.W500,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.035).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.W500,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.03).em,
    ),
    titleLarge = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.W500,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.02).em,
    ),
    titleMedium = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.W500,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.01).em,
    ),
    titleSmall = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.005).em,
    ),
    bodyLarge = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    // Small labels are the mono voice of the brand: facts, counters, tags.
    labelMedium = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.02.em,
    ),
    labelSmall = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.W400,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.04.em,
    ),
)
