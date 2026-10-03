package pro.xiangyu.cashierhelper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Colours that Material has no slot for. */
@Immutable
data class StatusColors(val success: Color, val warning: Color)

private val LocalStatusColors = staticCompositionLocalOf { StatusColors(LightTokens.Success, LightTokens.Warning) }

val MaterialTheme.statusColors: StatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current

private val LightScheme = lightColorScheme(
    primary = LightTokens.Primary,
    onPrimary = LightTokens.OnPrimary,
    primaryContainer = LightTokens.PrimaryContainer,
    onPrimaryContainer = LightTokens.OnPrimaryContainer,
    secondary = LightTokens.Success,
    onSecondary = LightTokens.OnPrimary,
    background = LightTokens.Background,
    onBackground = LightTokens.Text,
    surface = LightTokens.Background,
    onSurface = LightTokens.Text,
    surfaceVariant = LightTokens.Surface2,
    onSurfaceVariant = LightTokens.Muted,
    surfaceContainerLowest = LightTokens.Background,
    surfaceContainerLow = LightTokens.Surface2,
    surfaceContainer = LightTokens.Surface2,
    surfaceContainerHigh = LightTokens.Surface3,
    surfaceContainerHighest = LightTokens.Surface3,
    outline = LightTokens.BorderStrong,
    outlineVariant = LightTokens.Border,
    error = LightTokens.Danger,
    onError = LightTokens.OnPrimary,
    errorContainer = LightTokens.DangerContainer,
    onErrorContainer = LightTokens.OnDangerContainer,
)

private val DarkScheme = darkColorScheme(
    primary = DarkTokens.Primary,
    onPrimary = DarkTokens.OnPrimary,
    primaryContainer = DarkTokens.PrimaryContainer,
    onPrimaryContainer = DarkTokens.OnPrimaryContainer,
    secondary = DarkTokens.Success,
    onSecondary = DarkTokens.OnPrimary,
    background = DarkTokens.Background,
    onBackground = DarkTokens.Text,
    surface = DarkTokens.Surface,
    onSurface = DarkTokens.Text,
    surfaceVariant = DarkTokens.Surface2,
    onSurfaceVariant = DarkTokens.Muted,
    surfaceContainerLowest = DarkTokens.Background,
    surfaceContainerLow = DarkTokens.Surface,
    surfaceContainer = DarkTokens.Surface2,
    surfaceContainerHigh = DarkTokens.Surface3,
    surfaceContainerHighest = DarkTokens.Surface3,
    outline = DarkTokens.BorderStrong,
    outlineVariant = DarkTokens.Border,
    error = DarkTokens.Danger,
    onError = DarkTokens.OnPrimary,
    errorContainer = DarkTokens.DangerContainer,
    onErrorContainer = DarkTokens.OnDangerContainer,
)

private val CashierShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun CashierTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val status = if (darkTheme) StatusColors(DarkTokens.Success, DarkTokens.Warning) else StatusColors(LightTokens.Success, LightTokens.Warning)
    CompositionLocalProvider(LocalStatusColors provides status) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = CashierTypography,
            shapes = CashierShapes,
            content = content,
        )
    }
}
