package dev.bongballe.parkbuddy.theme

import androidx.compose.ui.graphics.Color

// Sage Green Theme Palette
val SageGreen = Color(0xFF556B2F)
val SagePrimary = Color(0xFF4A6741)
val SageOnPrimary = Color(0xFFFFFFFF)
val SageContainer = Color(0xFFD8E7D1)
val SageOnContainer = Color(0xFF131F0F)
val TonalSurface = Color(0xFFF2F4F0)
val SurfaceVariant = Color(0xFFE0E4DB)
val OnSurface = Color(0xFF1A1C19)
val OnSurfaceVariant = Color(0xFF43483F)

// Existing Colors (keeping for reference or fallback if needed, but primary roles will be
// overridden)
val PrimaryLight = SagePrimary
val OnPrimaryLight = SageOnPrimary
val PrimaryContainerLight = SageContainer
val OnPrimaryContainerLight = SageOnContainer

val SecondaryLight = SageGreen // Using SageGreen as secondary for accents
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFCEE9DA) // Keep existing or derive
val OnSecondaryContainerLight = Color(0xFF092016)

val TertiaryLight = Color(0xFF3E6373)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFC1E8FB)
val OnTertiaryContainerLight = Color(0xFF001F29)

val Terracotta = Color(0xFFBC5449)

// Map colors
val GoldenYellow = Color(0xFFD4A84B) // Complementary warm yellow for non-watched streets

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val BackgroundLight = TonalSurface
val OnBackgroundLight = OnSurface
val SurfaceLight = TonalSurface
val OnSurfaceLight = OnSurface
val SurfaceVariantLight = SurfaceVariant
val OnSurfaceVariantLight = OnSurfaceVariant
