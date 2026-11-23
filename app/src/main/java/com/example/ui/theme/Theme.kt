package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LuxuryDarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = ObsidianBlack,
    primaryContainer = GoldContainer,
    onPrimaryContainer = GoldBright,
    secondary = GoldBright,
    onSecondary = ObsidianBlack,
    secondaryContainer = CharcoalElevated,
    onSecondaryContainer = IvoryWhite,
    tertiary = PitchEmerald,
    onTertiary = ObsidianBlack,
    tertiaryContainer = PitchEmeraldContainer,
    onTertiaryContainer = PitchEmerald,
    background = ObsidianBlack,
    onBackground = IvoryWhite,
    surface = CharcoalDeep,
    onSurface = IvoryWhite,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = SilverGrey,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = CharcoalDeep,
    surfaceContainer = CharcoalCard,
    surfaceContainerHigh = CharcoalElevated,
    surfaceContainerHighest = Color(0xFF282D3D),
    outline = CharcoalBorder,
    outlineVariant = GoldBorder,
    error = CrimsonLoss,
    onError = IvoryWhite,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonLoss
)

@Immutable
data class LuxuryTokens(
    val goldGradient: Brush = Brush.linearGradient(
        colors = listOf(GoldBright, GoldPrimary, GoldMuted)
    ),
    val darkCardGradient: Brush = Brush.verticalGradient(
        colors = listOf(CharcoalElevated, CharcoalCard)
    ),
    val heroScrimGradient: Brush = Brush.verticalGradient(
        colors = listOf(
            ObsidianBlack.copy(alpha = 0.25f),
            ObsidianBlack.copy(alpha = 0.78f),
            ObsidianBlack
        )
    ),
    val goldBorderColor: Color = GoldBorder,
    val pitchGreen: Color = PitchEmerald,
    val pitchGreenContainer: Color = PitchEmeraldContainer,
    val drawAmber: Color = AmberDraw,
    val drawAmberContainer: Color = AmberContainer,
    val lossCrimson: Color = CrimsonLoss,
    val lossCrimsonContainer: Color = CrimsonContainer
)

val LocalLuxuryTokens = staticCompositionLocalOf { LuxuryTokens() }

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun AllFootballPredictionsTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLuxuryTokens provides LuxuryTokens()) {
        MaterialTheme(
            colorScheme = LuxuryDarkColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
