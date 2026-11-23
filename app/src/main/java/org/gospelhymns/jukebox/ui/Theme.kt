package org.gospelhymns.jukebox.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.gospelhymns.jukebox.R

private val Ink = Color(0xFF1C1915)
private val Paper = Color(0xFFF7F3EA)
private val Burgundy = Color(0xFF6E2B3A)
private val Pine = Color(0xFF1F4D3A)
private val Card = Color(0xFFFFFBF5)

private val LightColors = lightColorScheme(
    primary = Burgundy,
    onPrimary = Color(0xFFFFF8F4),
    secondary = Pine,
    onSecondary = Color(0xFFF4FFF8),
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E0D4),
    onSurfaceVariant = Color(0xFF4A433C),
    outline = Color(0xFFC4B8A5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE7B7C1),
    onPrimary = Color(0xFF3F121C),
    secondary = Color(0xFF9DCEB8),
    onSecondary = Color(0xFF083326),
    background = Color(0xFF141210),
    onBackground = Color(0xFFF3EBDD),
    surface = Color(0xFF1E1B18),
    onSurface = Color(0xFFF3EBDD),
    surfaceVariant = Color(0xFF3A332C),
    onSurfaceVariant = Color(0xFFD9CBB8),
    outline = Color(0xFF8A7B6A),
)

private val HymnFont = FontFamily(Font(R.font.noto_sans_tc, FontWeight.Normal))

private val HymnType = Typography(
    headlineLarge = TextStyle(fontFamily = HymnFont, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = HymnFont, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = HymnFont, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = HymnFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = HymnFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = HymnFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = HymnFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = HymnFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun HymnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = HymnType,
        content = content,
    )
}
