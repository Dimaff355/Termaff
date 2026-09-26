package app.termaff.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import org.connectbot.terminal.VTermKey

/** «Подложка»: Backspace в пустом поле стирает её — так ловим Backspace и от IME, которые не шлют KEYCODE_DEL. */
private const val PAD = " "

/** Клавиши физической клавиатуры, которые уходят в терминал как есть. */
private val KEYS = mapOf(
    Key.DirectionUp to VTermKey.UP, Key.DirectionDown to VTermKey.DOWN,
    Key.DirectionLeft to VTermKey.LEFT, Key.DirectionRight to VTermKey.RIGHT,
    Key.Escape to VTermKey.ESCAPE, Key.Tab to VTermKey.TAB, Key.Delete to VTermKey.DEL,
    Key.MoveHome to VTermKey.HOME, Key.MoveEnd to VTermKey.END,
    Key.PageUp to VTermKey.PAGEUP, Key.PageDown to VTermKey.PAGEDOWN,
)

/**
 * Прямой ввод (vim, mc, htop). termlib просит у клавиатуры TYPE_NULL и отправляет только подтверждённый
 * текст, а Яндекс/SwiftKey держат слово «в составлении» до пробела — буквы появлялись пачкой.
 * Здесь невидимое обычное поле: любое изменение текста, включая составляемое слово, сразу уходит
 * в PTY разницей — Backspace'ы за стёртое + новые символы.
 */
@Composable
fun DirectInput(
    focus: FocusRequester,
    onText: (String) -> Unit,
    onKey: (vtermKey: Int) -> Unit,
    onCtrl: (codepoint: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf(TextFieldValue(PAD, TextRange(1))) }
    BasicTextField(
        value = value,
        onValueChange = { new ->
            val old = value.text
            val common = old.commonPrefixWith(new.text).length
            repeat(old.codePointCount(common, old.length)) { onKey(VTermKey.BACKSPACE) }
            val added = new.text.substring(common)
            if (added.isNotEmpty()) onText(added.replace('\n', '\r'))
            // Буфер сбрасываем, только когда клавиатура ничего не составляет: после Enter, стирания подложки, на длинном тексте
            val reset = new.composition == null && ('\n' in new.text || !new.text.startsWith(PAD) || new.text.length > 64)
            value = if (reset) TextFieldValue(PAD, TextRange(1)) else new
        },
        modifier = modifier.focusRequester(focus).onPreviewKeyEvent { e -> hardwareKey(e, onKey, onCtrl) },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.None,
        ),
    )
}

/** Физическая клавиатура: стрелки/Esc/Tab/… и Ctrl+буква — сразу в терминал. Текст и Enter идут через поле. */
private fun hardwareKey(e: KeyEvent, onKey: (Int) -> Unit, onCtrl: (Int) -> Unit): Boolean {
    val down = e.type == KeyEventType.KeyDown
    KEYS[e.key]?.let { if (down) onKey(it); return true }
    if (e.isCtrlPressed) {
        val c = e.nativeKeyEvent.getUnicodeChar(0)
        if (c > 0) { if (down) onCtrl(c); return true }
    }
    return false
}
