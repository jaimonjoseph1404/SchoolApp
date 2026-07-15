package org.familytools.educationtracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF00796B)
private val TealDark = Color(0xFF4DB6AC)
private val Amber = Color(0xFFFFA000)

private val LightColors = lightColorScheme(
    primary = Teal,
    secondary = Amber,
)

private val DarkColors = darkColorScheme(
    primary = TealDark,
    secondary = Amber,
)

@Composable
fun EducationTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
