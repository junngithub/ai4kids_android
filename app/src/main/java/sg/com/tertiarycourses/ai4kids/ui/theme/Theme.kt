package sg.com.tertiarycourses.ai4kids.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp

/**
 * Shared, kid-friendly visual styling tokens — the Android counterpart of the
 * iOS app's `Theme`. Bright, rounded, high-contrast colors tuned for young
 * learners (ages 4–16).
 */
object Theme {
    // Brand palette (AI4Kids — playful, saturated). Mirrors the iOS values.
    val Purple = Color(red = 0.45f, green = 0.30f, blue = 0.92f)
    val Pink = Color(red = 0.98f, green = 0.35f, blue = 0.62f)
    val Orange = Color(red = 1.00f, green = 0.58f, blue = 0.20f)
    val Yellow = Color(red = 1.00f, green = 0.80f, blue = 0.16f)
    val Green = Color(red = 0.20f, green = 0.78f, blue = 0.45f)
    val Blue = Color(red = 0.20f, green = 0.62f, blue = 0.98f)
    val Teal = Color(red = 0.12f, green = 0.78f, blue = 0.78f)
    val Red = Color(red = 0.96f, green = 0.34f, blue = 0.34f)

    val Ink = Color(red = 0.16f, green = 0.14f, blue = 0.30f)

    val CardCornerRadius = 28.dp
    val BigCornerRadius = 36.dp

    val CardShape = RoundedCornerShape(CardCornerRadius)
    val BigShape = RoundedCornerShape(BigCornerRadius)

    /** Soft, friendly drop shadow color/elevation used on cards and buttons. */
    val SoftShadowElevation = 10.dp

    /** Text shadow used for headings drawn over colored surfaces. */
    val SoftTextShadow = Shadow(
        color = Color.Black.copy(alpha = 0.18f),
        offset = Offset(0f, 4f),
        blurRadius = 8f,
    )

    /** The app-wide warm background gradient. */
    val Background = Brush.linearGradient(
        colors = listOf(
            Color(red = 0.96f, green = 0.95f, blue = 1.0f),
            Color(red = 1.0f, green = 0.96f, blue = 0.93f),
        ),
    )
}

private val AppColorScheme = lightColorScheme(
    primary = Theme.Purple,
    secondary = Theme.Pink,
    background = Color(red = 0.96f, green = 0.95f, blue = 1.0f),
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Theme.Ink,
    onSurface = Theme.Ink,
)

@Composable
fun AI4KidsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography(),
        content = content,
    )
}
