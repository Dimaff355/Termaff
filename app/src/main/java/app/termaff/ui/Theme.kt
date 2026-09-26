package app.termaff.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Палитра из макета (интерфейс.png): графитовый фон, мятный акцент
val Bg = Color(0xFF0E1113)
val Card = Color(0xFF1A1F23)
val Accent = Color(0xFF2EE6A6)
val Text = Color(0xFFE6EAED)
val Muted = Color(0xFF8A949C)
val Danger = Color(0xFFFF5C5C)

@Composable
fun TermaffTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Accent,
        onPrimary = Bg,
        background = Bg,
        onBackground = Text,
        surface = Bg,
        onSurface = Text,
        surfaceVariant = Card,
        onSurfaceVariant = Muted,
        surfaceContainer = Card,
        surfaceContainerHigh = Card,
        error = Danger,
    ),
    content = content,
)
