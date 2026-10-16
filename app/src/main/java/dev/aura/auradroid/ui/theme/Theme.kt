package dev.aura.auradroid.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark, // Aura gold
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark, // Aura straw
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark, // Aura moss
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark, // Aura forest
    onBackground = OnBackgroundDark, // Aura cream
    surface = SurfaceDark, // Aura forest 2
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)

// ── OLED Black ColorScheme (pure pitch black #000000 for AMOLED screens) ────
private val OledDarkColorScheme = darkColorScheme(
    primary = AuraGold,
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF383218),
    onPrimaryContainer = Color(0xFFF5E7B0),
    secondary = Color(0xFFD4D4D4),
    onSecondary = Color(0xFF141414),
    secondaryContainer = Color(0xFF242424),
    onSecondaryContainer = Color(0xFFEEEEEE),
    tertiary = Color(0xFFA9BF7E),
    onTertiary = Color(0xFF18210A),
    tertiaryContainer = Color(0xFF2E3A1C),
    onTertiaryContainer = Color(0xFFD3E6AB),
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F1F5),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF2F1F5),
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFA0A0A0),
    outline = Color(0xFF3E3E3E),
    outlineVariant = Color(0xFF1E1E1E),
)

private val OledLightColorScheme = lightColorScheme(
    primary = Color(0xFF1A1A1A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E5E5),
    onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF404040),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECECEC),
    onSecondaryContainer = Color(0xFF222222),
    tertiary = Color(0xFF4E6A2B),
    onTertiary = Color(0xFFFFFFFF),
    error = ErrorLight,
    onError = OnErrorLight,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0A0A0A),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFF444444),
    outline = Color(0xFF888888),
    outlineVariant = Color(0xFFCCCCCC),
)

// ── Spectrum ColorScheme (Retro 8-bit homage: blue, bright yellow, cyan) ────
private val SpectrumDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFF00), // ZX Bright Yellow
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF383800),
    onPrimaryContainer = Color(0xFFFFFFB0),
    secondary = Color(0xFF1AA6E0), // Sinclair Cyan
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF003750),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = Color(0xFF2FAE4E), // Sinclair Green
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF003810),
    onTertiaryContainer = Color(0xFF86EFAC),
    error = Color(0xFFE4312B), // Sinclair Red
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF5A0000),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF070A14),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF0E1424),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF162038),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF0000D7), // Sinclair Border Blue
    outlineVariant = Color(0xFF1E2D4A),
)

private val SpectrumLightColorScheme = lightColorScheme(
    primary = Color(0xFF0056B3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF007A87),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8ED),
    onSecondaryContainer = Color(0xFF002025),
    tertiary = Color(0xFF196C2E),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    background = Color(0xFFF0F4F8),
    onBackground = Color(0xFF070A14),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF070A14),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
)

// ── Aura Code ColorScheme (Studio navy & terracotta) ────────────────────────
private val CodeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFCC785C), // Terracotta
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4D2418),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFF9B1B30), // Ruby
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF38121A),
    onSecondaryContainer = Color(0xFFFFD9DF),
    tertiary = Color(0xFF5C9ECC), // Steel Blue
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF1B3850),
    onTertiaryContainer = Color(0xFFC0E0FF),
    error = ErrorDark,
    onError = OnErrorDark,
    background = Color(0xFF0F1724), // Code Navy
    onBackground = Color(0xFFE8E6E3),
    surface = Color(0xFF141E2E),
    onSurface = Color(0xFFE8E6E3),
    surfaceVariant = Color(0xFF1C2739), // Code Panel
    onSurfaceVariant = Color(0xFF8A94A6),
    outline = Color(0xFF485870),
    outlineVariant = Color(0xFF25334A),
)

private val CodeLightColorScheme = lightColorScheme(
    primary = Color(0xFFA85137),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A1208),
    secondary = Color(0xFF801627),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9DF),
    onSecondaryContainer = Color(0xFF32040D),
    tertiary = Color(0xFF2C648F),
    onTertiary = Color(0xFFFFFFFF),
    error = ErrorLight,
    onError = OnErrorLight,
    background = Color(0xFFF7F5F2),
    onBackground = Color(0xFF1A1F2C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1F2C),
    surfaceVariant = Color(0xFFEBE7E0),
    onSurfaceVariant = Color(0xFF5D6575),
    outline = Color(0xFF9AA2B1),
    outlineVariant = Color(0xFFD4D8E0),
)

@Composable
fun AuraDroidTheme(
    darkTheme: Boolean = true, // Default to dark theme for Aura brand
    dynamicColor: Boolean = false,
    palette: dev.aura.auradroid.data.settings.ThemePalette = dev.aura.auradroid.data.settings.ThemePalette.NATURE,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> when (palette) {
            dev.aura.auradroid.data.settings.ThemePalette.NATURE -> if (darkTheme) DarkColorScheme else LightColorScheme
            dev.aura.auradroid.data.settings.ThemePalette.OLED -> if (darkTheme) OledDarkColorScheme else OledLightColorScheme
            dev.aura.auradroid.data.settings.ThemePalette.SPECTRUM -> if (darkTheme) SpectrumDarkColorScheme else SpectrumLightColorScheme
            dev.aura.auradroid.data.settings.ThemePalette.CODE -> if (darkTheme) CodeDarkColorScheme else CodeLightColorScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // Use Aura's theme color
            window.statusBarColor = colorScheme.background.toArgb()

            // Light status bar for dark theme, dark for light
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
