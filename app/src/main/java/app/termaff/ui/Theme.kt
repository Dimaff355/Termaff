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
/** Заливка выбранного (чипы, сегменты, липкие Ctrl/Alt) — приглушённый акцент. */
val Selected = Color(0xFF1C3A30)

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
        // Выбранные FilterChip/SegmentedButton берут secondaryContainer — по умолчанию он сиреневый
        secondaryContainer = Selected,
        onSecondaryContainer = Accent,
        outlineVariant = Color(0xFF2A3136),
        error = Danger,
    ),
) {
    // Без Scaffold/Surface цвет текста по умолчанию чёрный — задаём светлый для всех экранов
    CompositionLocalProvider(LocalContentColor provides Text, content = content)
}

/** Цветовая схема терминала: фон, текст и 16 цветов ANSI (0xRRGGBB). */
class TermTheme(val id: String, val name: String, bg: Long, fg: Long, vararg ansi: Long) {
    val bg = Color(0xFF000000 or bg)
    val fg = Color(0xFF000000 or fg)
    val ansi = IntArray(16) { (0xFF000000 or ansi[it]).toInt() }
}

val TermThemes = listOf(
    TermTheme(
        "termaff", "Termaff", 0x0E1113, 0xE6EAED,
        0x1A1F23, 0xFF5C5C, 0x2EE6A6, 0xF5C84C, 0x4FA3FF, 0xC678DD, 0x3CC8D8, 0xC8CED3,
        0x5C656C, 0xFF7B7B, 0x6FF0C4, 0xFFD97A, 0x7DBBFF, 0xD7A0F0, 0x6FDDE8, 0xFFFFFF,
    ),
    TermTheme(
        "dracula", "Dracula", 0x282A36, 0xF8F8F2,
        0x21222C, 0xFF5555, 0x50FA7B, 0xF1FA8C, 0xBD93F9, 0xFF79C6, 0x8BE9FD, 0xF8F8F2,
        0x6272A4, 0xFF6E6E, 0x69FF94, 0xFFFFA5, 0xD6ACFF, 0xFF92DF, 0xA4FFFF, 0xFFFFFF,
    ),
    TermTheme(
        "gruvbox", "Gruvbox", 0x282828, 0xEBDBB2,
        0x282828, 0xCC241D, 0x98971A, 0xD79921, 0x458588, 0xB16286, 0x689D6A, 0xA89984,
        0x928374, 0xFB4934, 0xB8BB26, 0xFABD2F, 0x83A598, 0xD3869B, 0x8EC07C, 0xEBDBB2,
    ),
    TermTheme(
        "nord", "Nord", 0x2E3440, 0xD8DEE9,
        0x3B4252, 0xBF616A, 0xA3BE8C, 0xEBCB8B, 0x81A1C1, 0xB48EAD, 0x88C0D0, 0xE5E9F0,
        0x4C566A, 0xBF616A, 0xA3BE8C, 0xEBCB8B, 0x81A1C1, 0xB48EAD, 0x8FBCBB, 0xECEFF4,
    ),
    TermTheme(
        "light", "Светлая", 0xFAFAFA, 0x383A42,
        0x383A42, 0xE45649, 0x50A14F, 0xC18401, 0x4078F2, 0xA626A4, 0x0184BC, 0xA0A1A7,
        0x4F525D, 0xE06C75, 0x50A14F, 0xC18401, 0x61AFEF, 0xC678DD, 0x56B6C2, 0xFFFFFF,
    ),
)

fun termTheme(id: String) = TermThemes.firstOrNull { it.id == id } ?: TermThemes[0]
