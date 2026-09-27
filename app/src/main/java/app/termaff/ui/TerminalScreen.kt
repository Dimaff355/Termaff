package app.termaff.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.termaff.data.Snippet
import app.termaff.data.Store
import app.termaff.R
import app.termaff.ssh.SessionState
import app.termaff.ssh.Sessions
import app.termaff.ssh.SshSession
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.VTermKey

// Модификаторы libvterm (vterm_keycodes.h)
private const val ALT = 2
private const val CTRL = 4

/** Липкие Ctrl/Alt с панели клавиш: действуют на следующую клавишу в любом режиме ввода. */
private class StickyMods {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
    val bits get() = (if (ctrl) CTRL else 0) or (if (alt) ALT else 0)
    fun clearTransients() { ctrl = false; alt = false }
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
    var direct by remember { mutableStateOf(session.altScreen) }
    var line by remember { mutableStateOf(TextFieldValue()) }
    /** Позиция в истории: history.size — «новая строка». */
    var histPos by remember { mutableIntStateOf(session.history.size) }
    val secret = session.secretPrompt
    val context = LocalContext.current
    // Системный monospace на части прошивок (MIUI/HyperOS) не моноширинный → буквы «разъезжаются». Свой шрифт.
    val font = remember { context.resources.getFont(R.font.jetbrains_mono) }
    val fontSize = terminalFontSize()
    val theme = termTheme(Store.theme)
    val currentFont by rememberUpdatedState(fontSize)
    val directFocus = remember { FocusRequester() }
    val lineFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun key(k: Int) { emu.dispatchKey(mods.bits, k); mods.clearTransients() }
    fun char(c: Int) { emu.dispatchCharacter(mods.bits, c); mods.clearTransients() }
    fun sendLine(suffix: String) {
        session.addHistory(line.text)
        session.write(line.text + suffix)
        line = TextFieldValue()
        histPos = session.history.size
    }
    fun setLine(text: String) { line = TextFieldValue(text, TextRange(text.length)) }
    /** ↑/↓ в строке ввода листают историю; если она пуста или идёт ввод пароля — стрелка уходит на сервер. */
    fun arrow(up: Boolean) {
        val h = session.history
        if (direct || secret || h.isEmpty()) return key(if (up) VTermKey.UP else VTermKey.DOWN)
        histPos = (histPos + if (up) -1 else 1).coerceIn(0, h.size)
        setLine(h.getOrElse(histPos) { "" })
    }
    /** Прямой ввод: с липким Ctrl/Alt первый символ уходит сочетанием (Ctrl+C), остальное — как есть. */
    fun sendDirect(text: String) {
        // Enter — всегда обычный \r (иначе libvterm кодирует Ctrl+Enter как CSI 13;5u и shell печатает мусор)
        if (mods.bits == 0 || text[0] == '\r') { mods.clearTransients(); return session.write(text) }
        char(text.codePointAt(0))
        text.substring(Character.charCount(text.codePointAt(0))).takeIf { it.isNotEmpty() }?.let(session::write)
    }
    fun focusInput() {
        runCatching { if (direct) directFocus.requestFocus() else lineFocus.requestFocus() }
        keyboard?.show()
    }
    fun type(s: String) = if (direct) s.codePoints().forEach(::char) else
        line = TextFieldValue(line.text.replaceRange(line.selection.min, line.selection.max, s),
            TextRange(line.selection.min + s.length))

    BackHandler(onBack = onBack)
    // vim/htop/mc включили альтернативный экран → прямой ввод, вышли → обратно строка ввода
    LaunchedEffect(session.altScreen) { direct = session.altScreen }
    LaunchedEffect(direct) { focusInput() }
    LaunchedEffect(theme) { emu.applyColorScheme(theme.ansi, theme.fg.toArgb(), theme.bg.toArgb()) }

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
        if (Sessions.list.size > 1) SessionTabs(session)

        // Щипок в termlib только визуальный (сбрасывается после жеста) — по его итогу меняем шрифт по-настоящему:
        // терминал пересчитывает колонки и сообщает серверу новый размер
        Box(Modifier.weight(1f).fillMaxWidth().background(theme.bg).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var zoom = 1f
                do {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    if (e.changes.count { it.pressed } > 1) zoom *= e.calculateZoom()
                } while (e.changes.any { it.pressed })
                if (zoom != 1f) Store.saveFontSize((currentFont * zoom).coerceIn(6f, 24f))
            }
        }) {
            Terminal(
                terminalEmulator = emu,
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                typeface = font,
                initialFontSize = fontSize.sp,
                backgroundColor = theme.bg,
                foregroundColor = theme.fg,
                // Свой ввод (строка или DirectInput) вместо встроенного IME termlib — см. DirectInput.kt
                keyboardEnabled = false,
                showSoftKeyboard = false,
                onTerminalTap = ::focusInput,
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

        SnippetsBar(session.target.serverId, onRun = session::run, onInsert = { if (!direct) setLine(it) })
        KeysBar(mods, ::key, ::type, ::arrow, onTab = { if (direct || line.text.isEmpty()) key(VTermKey.TAB) else sendLine("\t") })

        if (direct) DirectInput(
            directFocus, ::sendDirect, ::key,
            onCtrl = { emu.dispatchCharacter(CTRL or mods.bits, it); mods.clearTransients() },
            modifier = Modifier.size(1.dp).alpha(0f),
        )
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
                    val down = it.type == KeyEventType.KeyDown
                    when {
                        // Backspace в пустой строке стирает символ на сервере
                        it.key == Key.Backspace && line.text.isEmpty() -> { if (down) key(VTermKey.BACKSPACE); true }
                        it.key == Key.DirectionUp || it.key == Key.DirectionDown -> { if (down) arrow(it.key == Key.DirectionUp); true }
                        else -> false
                    }
                },
                placeholder = { Text(if (secret) "Пароль (не сохраняется)" else "Введите команду…") },
                leadingIcon = if (secret) ({ Icon(Icons.Filled.Lock, null, tint = Accent) }) else null,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Bg, unfocusedIndicatorColor = Bg,
                    focusedContainerColor = Card, unfocusedContainerColor = Card,
                ),
                // Пароль: тип Password — клавиатура не подсказывает и не запоминает введённое
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Send,
                    keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text, autoCorrectEnabled = !secret,
                ),
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

/**
 * Размер шрифта терминала, sp. Авто — 80 колонок по ширине экрана (под них рассчитаны fastfetch, mc, htop),
 * не крупнее 12 sp. Ширина символа JetBrains Mono — 0.6 em; 0.97 — запас на округление ширины ячейки в termlib.
 */
@Composable
fun terminalFontSize(): Float {
    if (Store.fontSize > 0) return Store.fontSize
    val auto = (LocalConfiguration.current.screenWidthDp - 8) / (80 * 0.6f * LocalDensity.current.fontScale) * 0.97f
    return auto.coerceIn(6f, 12f)
}

/** Переключатель открытых сессий (виден, когда их больше одной). */
@Composable
private fun SessionTabs(current: SshSession) = Row(
    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    Sessions.list.forEach { s ->
        FilterChip(
            selected = s == current, onClick = { Sessions.select(s) },
            label = { Text(s.target.title, maxLines = 1) },
            leadingIcon = {
                val color = when (s.state) { SessionState.Connected -> Accent; SessionState.Connecting -> Muted; else -> Danger }
                Box(Modifier.size(8.dp).background(color, CircleShape))
            },
        )
    }
}

/** Быстрые команды этого сервера: нажатие — выполнить, долгое нажатие — вставить в строку ввода (однострочные). */
@Composable
private fun SnippetsBar(serverId: String, onRun: (Snippet) -> Unit, onInsert: (String) -> Unit) {
    val list = Store.snippets.filter { it.fits(serverId) }
    if (list.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        list.forEach { s ->
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Card)
                    .combinedClickable(onLongClick = { s.steps.singleOrNull()?.let(onInsert) }) { onRun(s) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Accent, modifier = Modifier.size(16.dp))
                Text(s.title, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun KeysBar(mods: StickyMods, key: (Int) -> Unit, type: (String) -> Unit, arrow: (Boolean) -> Unit, onTab: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        @Composable
        fun k(label: String, selected: Boolean = false, onClick: () -> Unit) = FilterChip(
            selected = selected, onClick = onClick,
            label = { Text(label, fontFamily = Mono) },
        )
        k("Ctrl", mods.ctrl) { mods.ctrl = !mods.ctrl }
        k("Alt", mods.alt) { mods.alt = !mods.alt }
        k("Esc") { key(VTermKey.ESCAPE) }
        k("Tab", onClick = onTab)
        k("↑") { arrow(true) }
        k("↓") { arrow(false) }
        k("←") { key(VTermKey.LEFT) }
        k("→") { key(VTermKey.RIGHT) }
        for (s in listOf("|", "~", "/", "-", "$", "&", ">")) k(s) { type(s) }
        k("Home") { key(VTermKey.HOME) }
        k("End") { key(VTermKey.END) }
        k("PgUp") { key(VTermKey.PAGEUP) }
        k("PgDn") { key(VTermKey.PAGEDOWN) }
    }
}
