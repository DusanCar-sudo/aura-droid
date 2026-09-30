package dev.aura.auradroid.ui.theme

import androidx.compose.ui.graphics.Color

// Aura brand colours: the Nature palette (BRAND.md §5).
// Near-black forest, moss cards, cream text, and the gold of late light.
// The four stripes are fixed: they are never themed or recoloured.

// ── Brand constants ─────────────────────────────────────────────────────────
val AuraForest = Color(0xFF070907)      // ground
val AuraForest2 = Color(0xFF0C100C)     // raised ground
val AuraMoss = Color(0xFF141A14)        // cards
val AuraMoss2 = Color(0xFF1A211A)       // raised cards
val AuraCream = Color(0xFFF2EBC9)       // text on dark, ground on light
val AuraCream2 = Color(0xFFE2DBB7)
val AuraDim = Color(0xFFB9BAA2)         // secondary text on dark
val AuraGold = Color(0xFFE7CF85)        // the light: one accent per surface
val AuraStraw = Color(0xFFD8CB95)
val AuraInk = Color(0xFF10150F)         // text on light
val AuraOlive = Color(0xFF535C37)       // accent on light (5.9:1 on cream)

// The stripes, in Sinclair order. Fixed brand colours.
val StripeRed = Color(0xFFE4312B)
val StripeYellow = Color(0xFFF8B91E)
val StripeGreen = Color(0xFF2FAE4E)
val StripeCyan = Color(0xFF1AA6E0)

// ── Material 3 roles: dark (the default) ────────────────────────────────────
val PrimaryDark = AuraGold
val OnPrimaryDark = AuraInk
val PrimaryContainerDark = Color(0xFF4A4222)
val OnPrimaryContainerDark = Color(0xFFF5E7B0)

val SecondaryDark = Color(0xFFC9C7A0)
val OnSecondaryDark = Color(0xFF1B1D10)
val SecondaryContainerDark = Color(0xFF2B3020)
val OnSecondaryContainerDark = AuraCream2

val TertiaryDark = Color(0xFFA9BF7E)    // moss green
val OnTertiaryDark = Color(0xFF18210A)
val TertiaryContainerDark = Color(0xFF2E3A1C)
val OnTertiaryContainerDark = Color(0xFFD3E6AB)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = AuraForest
val OnBackgroundDark = AuraCream
val SurfaceDark = AuraForest2
val OnSurfaceDark = AuraCream
val SurfaceVariantDark = AuraMoss2
val OnSurfaceVariantDark = AuraDim
val OutlineDark = Color(0xFF5A5F4B)
val OutlineVariantDark = Color(0xFF2C3226)

// ── Material 3 roles: light ─────────────────────────────────────────────────
val PrimaryLight = AuraOlive
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = AuraGold
val OnPrimaryContainerLight = Color(0xFF2A2410)

val SecondaryLight = Color(0xFF6B5F2A)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = AuraCream2
val OnSecondaryContainerLight = Color(0xFF2B2610)

val TertiaryLight = Color(0xFF4E6A2B)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFD3E6AB)
val OnTertiaryContainerLight = Color(0xFF18210A)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val BackgroundLight = AuraCream
val OnBackgroundLight = AuraInk
val SurfaceLight = Color(0xFFF7F2DB)
val OnSurfaceLight = AuraInk
val SurfaceVariantLight = Color(0xFFE4DDB8)
val OnSurfaceVariantLight = Color(0xFF3B4232)
val OutlineLight = Color(0xFF7A7F63)
val OutlineVariantLight = Color(0xFFCDC59B)

// ── Message bubbles ─────────────────────────────────────────────────────────
val UserMessageLight = Color(0xFFE4DDB8)
val UserMessageDark = AuraMoss2

val AssistantMessageLight = Color(0xFFFBF8E8)
val AssistantMessageDark = AuraMoss

// ── Code blocks ─────────────────────────────────────────────────────────────
val CodeBackgroundLight = Color(0xFFEAE3BF)
val CodeBackgroundDark = Color(0xFF050705)
val CodeBorderLight = Color(0xFFCDC59B)
val CodeBorderDark = Color(0xFF2C3226)

// Glow (used by the splash and the empty state): a warm light, never a fill.
val GlowLight = Color(0xFFC8AD5C)
val GlowDark = AuraGold
val GlowHotLight = Color(0xFFE7CF85)
val GlowHotDark = Color(0xFFFFF7D6)
