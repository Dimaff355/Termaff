package app.termaff.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.termaff.R

// Палитра из макета (интерфейс.png): графитовый фон, мятный акцент
val Bg = Color(0xFF0E1113)
val Card = Color(0xFF1A1F23)
val Accent = Color(0xFF2EE6A6)
val Text = Color(0xFFE6EAED)
val Muted = Color(0xFF8A949C)
val Danger = Color(0xFFFF5C5C)

/** Моноширинный шрифт приложения (строка ввода, панель клавиш, ключи) — тот же, что в терминале. */
val Mono = FontFamily(Font(R.font.jetbrains_mono))

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
) {
    // Без Scaffold/Surface цвет текста по умолчанию чёрный — задаём светлый для всех экранов
    CompositionLocalProvider(LocalContentColor provides Text, content = content)
}
