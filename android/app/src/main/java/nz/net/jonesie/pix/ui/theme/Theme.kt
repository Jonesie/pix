package nz.net.jonesie.pix.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PixColorScheme = darkColorScheme(
    primary = PixAccent,
    onPrimary = PixBg,
    secondary = PixAccent2,
    onSecondary = PixBg,
    background = PixBg,
    onBackground = PixText,
    surface = PixBgElevated,
    onSurface = PixText,
    surfaceVariant = PixBgElevated,
    onSurfaceVariant = PixTextDim,
    outline = PixBorder,
    error = PixError,
)

@Composable
fun PixTheme(content: @Composable () -> Unit) {
    // The site is dark-only; mirror that rather than following system light mode.
    MaterialTheme(
        colorScheme = PixColorScheme,
        typography = PixTypography,
        content = content,
    )
}
