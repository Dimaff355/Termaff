package app.termaff.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.termaff.ssh.SessionState
import app.termaff.ssh.SshSession
import org.connectbot.terminal.ModifierManager
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.VTermKey

// Модификаторы libvterm (vterm_keycodes.h)
private const val ALT = 2
private const val CTRL = 4

/** Липкие Ctrl/Alt с панели клавиш; termlib учитывает их и в прямом режиме. */
private class StickyMods : ModifierManager {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
    val bits get() = (if (ctrl) CTRL else 0) or (if (alt) ALT else 0)
    override fun isCtrlActive() = ctrl
    override fun isAltActive() = alt
    override fun isShiftActive() = false
    override fun clearTransients() { ctrl = false; alt = false }
}

/**
 * Терминал. Два режима ввода:
 * - строка ввода (по умолчанию): обычное текстовое поле — работают свайпы, автозамена, голосовой ввод;
 * - прямой режим: клавиатура пишет прямо в PTY (vim, htop, mc, пароли).
 */
@Composable
fun TerminalScreen(session: SshSession, onBack: () -> Unit, onClose: () -> Unit, onReconnect: () -> Unit) {
    val emu = session.emulator
    val mods = remember { StickyMods() }
    var direct by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf(TextFieldValue()) }
    val termFocus = remember { FocusRequester() }
    val lineFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun key(k: Int) { emu.dispatchKey(mods.bits, k); mods.clearTransients() }
    fun char(c: Int) { emu.dispatchCharacter(mods.bits, c); mods.clearTransients() }
    fun sendLine(suffix: String) { session.write(line.text + suffix); line = TextFieldValue() }
    fun type(s: String) = if (direct) s.codePoints().forEach(::char) else
        line = TextFieldValue(line.text.replaceRange(line.selection.min, line.selection.max, s),
            TextRange(line.selection.min + s.length))

    BackHandler(onBack = onBack)
    LaunchedEffect(direct) { runCatching { if (direct) termFocus.requestFocus() else lineFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            val t = session.target
            Box(Modifier.size(8.dp).background(if (session.state == SessionState.Connected) Accent else Muted, CircleShape))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text("${t.user}@${t.host}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(if (t.port == 22) t.host else "${t.host}:${t.port}", color = Muted,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            IconButton(onClick = { direct = !direct }) {
                Icon(if (direct) Icons.Filled.ShortText else Icons.Filled.Keyboard,
                    if (direct) "Строка ввода" else "Прямой ввод", tint = if (direct) Accent else Text)
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Отключиться") }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Terminal(
                terminalEmulator = emu,
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                initialFontSize = MaterialTheme.typography.bodySmall.fontSize,
                backgroundColor = Bg,
                foregroundColor = Text,
                keyboardEnabled = direct,
                showSoftKeyboard = direct,
                focusRequester = termFocus,
                modifierManager = mods,
                onTerminalTap = { if (!direct) { lineFocus.requestFocus(); keyboard?.show() } },
            )
            when (val st = session.state) {
                SessionState.Connecting -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is SessionState.Closed -> Column(
                    Modifier.align(Alignment.Center).background(Card, RoundedCornerShape(16.dp)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(st.error ?: "Соединение закрыто", color = if (st.error != null) Danger else Muted)
                    Button(onClick = onReconnect) { Text("Переподключиться") }
                }
                SessionState.Connected -> Unit
            }
        }

        KeysBar(mods, ::key, ::type, onTab = { if (direct || line.text.isEmpty()) key(VTermKey.TAB) else sendLine("\t") })

        if (!direct) Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = line,
                onValueChange = { new ->
                    // Ctrl/Alt активны и напечатан один символ → отправляем как сочетание (Ctrl+C и т.п.)
                    if (mods.bits != 0 && new.text.length == line.text.length + 1) {
                        char(new.text[new.selection.start - 1].code)
                    } else {
                        line = new
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(lineFocus).onPreviewKeyEvent {
                    // Backspace в пустой строке стирает символ на сервере
                    if (it.key == Key.Backspace && line.text.isEmpty()) {
                        if (it.type == KeyEventType.KeyDown) key(VTermKey.BACKSPACE)
                        true
                    } else false
                },
                placeholder = { Text("Введите команду…") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Bg, unfocusedIndicatorColor = Bg,
                    focusedContainerColor = Card, unfocusedContainerColor = Card,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendLine("\r") }),
            )
            FilledIconButton(onClick = { sendLine("\r") }, Modifier.padding(start = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, "Отправить")
            }
        }
    }

    session.hostKeyPrompt?.let { p ->
        AlertDialog(
            onDismissRequest = { p.answer.complete(false) },
            title = { Text("Новый сервер") },
            text = { Text("Отпечаток ключа ${p.host}:\n\n${p.fingerprint}\n\nСверьте его с сервером. Доверять?") },
            confirmButton = { TextButton(onClick = { p.answer.complete(true) }) { Text("Доверять") } },
            dismissButton = { TextButton(onClick = { p.answer.complete(false) }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun KeysBar(mods: StickyMods, key: (Int) -> Unit, type: (String) -> Unit, onTab: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        @Composable
        fun k(label: String, selected: Boolean = false, onClick: () -> Unit) = FilterChip(
            selected = selected, onClick = onClick,
            label = { Text(label, fontFamily = FontFamily.Monospace) },
        )
        k("Ctrl", mods.ctrl) { mods.ctrl = !mods.ctrl }
        k("Alt", mods.alt) { mods.alt = !mods.alt }
        k("Esc") { key(VTermKey.ESCAPE) }
        k("Tab", onClick = onTab)
        k("↑") { key(VTermKey.UP) }
        k("↓") { key(VTermKey.DOWN) }
        k("←") { key(VTermKey.LEFT) }
        k("→") { key(VTermKey.RIGHT) }
        for (s in listOf("|", "~", "/", "-", "$", "&", ">")) k(s) { type(s) }
        k("Home") { key(VTermKey.HOME) }
        k("End") { key(VTermKey.END) }
        k("PgUp") { key(VTermKey.PAGEUP) }
        k("PgDn") { key(VTermKey.PAGEDOWN) }
    }
}
