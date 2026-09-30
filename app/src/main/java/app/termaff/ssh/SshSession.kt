package app.termaff.ssh

import android.util.Base64
import app.termaff.data.Server
import app.termaff.data.Snippet
import app.termaff.data.SshKey
import app.termaff.data.Store
import app.termaff.data.Vault
import app.termaff.tr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.trilead.ssh2.Connection
import com.trilead.ssh2.Session
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import org.connectbot.terminal.VTermKey
import java.io.IOException
import java.security.MessageDigest
import kotlin.concurrent.thread

/** Параметры подключения с уже расшифрованными секретами. Живёт только в памяти сессии. */
class Target(
    val serverId: String,
    val title: String,
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
    /** Приватный ключ в PEM/OpenSSH-формате; пустой — вход по паролю. */
    val key: String,
    /** Пароль ключа (если вход по ключу). */
    val passphrase: String,
    val startup: String,
) {
    constructor(s: Server, k: SshKey? = Store.key(s.keyId)) : this(
        s.id, s.title, s.host, s.port, s.user, Vault.decrypt(s.password),
        k?.let { Vault.decrypt(it.private) }.orEmpty(), k?.let { Vault.decrypt(it.passphrase) }.orEmpty(), s.startup,
    )
}

/** Запрос пользователю: доверять ли ключу сервера. */
class HostKeyPrompt(val host: String, val fingerprint: String, val answer: CompletableDeferred<Boolean>)

sealed interface SessionState {
    data object Connecting : SessionState
    data object Connected : SessionState
    data class Closed(val error: String? = null) : SessionState
}

/**
 * Одно SSH-подключение: TCP → проверка ключа хоста → вход → PTY-shell.
 * Байты сервера идут в [emulator], ввод пишется через [write] в отдельной корутине (не в UI-потоке).
 * Эмулятор живёт дольше соединения: после обрыва и переподключения экран и история остаются.
 */
class SshSession(val target: Target) {
    private var _state by mutableStateOf<SessionState>(SessionState.Connecting)
    var state: SessionState
        get() = _state
        private set(v) { _state = v; Sessions.changed() }
    var hostKeyPrompt by mutableStateOf<HostKeyPrompt?>(null)
        private set
    /** Сервер на альтернативном экране (vim, htop, mc) — нужен прямой ввод. */
    var altScreen by mutableStateOf(false)
        private set
    /** Сервер ждёт пароль (sudo, su, passphrase) — строку ввода маскируем и не запоминаем. */
    var secretPrompt by mutableStateOf(false)
        private set
    /** История строки ввода: только в памяти, пароли сюда не попадают. */
    val history = ArrayList<String>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val output = Channel<ByteArray>(Channel.UNLIMITED)
    @Volatile private var conn: Connection? = null
    private var job: Job? = null
    /** Вход уже удавался: значит, обрыв — это сеть, и есть смысл переподключаться. */
    @Volatile private var established = false
    @Volatile private var shell: Session? = null
    /** Последний размер экрана (колонки, строки): resize может прийти раньше, чем откроется PTY. */
    @Volatile var size = 80 to 24
        private set
    /** Программа включила мышь (`ESC[?1000h`…) и её формат SGR (`?1006h`) — свайп шлёт ей колесо. */
    @Volatile private var mouse = false
    @Volatile private var sgrMouse = false

    val emulator: TerminalEmulator = TerminalEmulatorFactory.create(
        defaultForeground = Color(0xFFE6EAED),
        defaultBackground = Color(0xFF0E1113),
        onKeyboardInput = ::write,
        onResize = { d ->
            size = d.columns to d.rows
            // Корутины на IO-пуле могут выполниться не по порядку (поворот = смена ширины и шрифта подряд):
            // под замком отправляем всегда ПОСЛЕДНИЙ размер, а не тот, что был при запуске
            scope.launch { synchronized(this@SshSession) { runCatching { size.let { shell?.resizePTY(it.first, it.second, 0, 0) } } } }
        },
    )

    /** Подключиться; обрыв уже работавшей сессии — переподключение с паузой 2, 4, 8… 30 с, в том же терминале. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var attempt = 0
            while (true) {
                state = SessionState.Connecting
                val error = try {
                    if (connect()) return@launch close()
                    tr("Связь потеряна")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    // Ключ хоста сменился — повтор не поможет
                    if (generateSequence<Throwable>(e) { it.cause }.any { it is SecurityException }) return@launch close(e.text)
                    e.text
                } catch (e: Exception) {
                    return@launch close(e.text)
                } finally {
                    disconnect()
                }
                ensureActive()
                if (state == SessionState.Connected) attempt = 0
                if (!established || attempt >= RETRIES) return@launch close(error)
                attempt++
                // Программа на сервере могла оставить альтернативный экран и режимы клавиш/мыши — сбрасываем локально
                emulator.writeInput(("\u001b[?1049l\u001b[?1l\u001b[?1000l\u001b[?1002l\u001b[?1006l\u001b[?2004l\u001b[0m\r\n[" + tr("%s. Переподключение…", error) + "]\r\n").toByteArray())
                altScreen = false
                mouse = false
                sgrMouse = false
                secretPrompt = false
                delay(minOf(1L shl attempt, 30L) * 1000)
            }
        }
    }

    /** Одно подключение до конца shell. true — shell завершился сам (exit), false — связь оборвалась. */
    private suspend fun connect(): Boolean = coroutineScope {
        val c = Connection(target.host, target.port).also { conn = it }
        // Таймаут KEX включает и время ответа на «Доверять?» — новому хосту даём минуту прочитать отпечаток.
        // Совсем без таймаута нельзя: сервер, принявший TCP и молчащий, повесил бы подключение навсегда
        val kexTimeout = if ("${target.host}:${target.port}" in Store.knownHosts) 20_000 else 60_000
        c.connect({ host, port, algo, key -> verifyHostKey("$host:$port", algo, key) }, 10_000, kexTimeout)
        check(authenticate(c)) { tr("Неверный логин, пароль или ключ") }
        val s = c.openSession()
        val opened = size
        s.requestPTY("xterm-256color", opened.first, opened.second, 0, 0, null)
        s.startShell()
        ensureActive()
        // shell публикуем только после старта: resize посреди requestPTY мог подвесить открытие
        shell = s
        size.let { if (it != opened) s.resizePTY(it.first, it.second, 0, 0) }
        state = SessionState.Connected
        established = true
        if (target.startup.isNotBlank()) write(target.startup + "\r")
        val writer = launch { for (bytes in output) s.stdin.run { write(bytes); flush() } }
        // Keepalive: без него мёртвое соединение (сменилась сеть, уснул роутер) висит до таймаута TCP — минуты.
        // ping() не прерывается и держит замок Connection, поэтому ждём его отдельно, а по таймауту закрываем канал
        val keepalive = launch {
            while (true) {
                delay(KEEPALIVE)
                val pong = scope.async { runCatching { c.ping() }.isSuccess }
                if (withTimeoutOrNull(KEEPALIVE) { pong.await() } != true) break
            }
            s.close()
        }
        val buf = ByteArray(8192)
        val input = s.stdout
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            emulator.writeInput(buf, 0, n)
            scan(buf, n)
        }
        writer.cancel()
        keepalive.cancel()
        // Сервер присылает код выхода перед закрытием канала; нет кода — канал закрылся вместе с соединением
        s.exitStatus != null || s.exitSignal != null
    }

    /**
     * Смотрим на поток сервера: режимы DEC (`ESC[?1049;1006h` — альтернативный экран, мышь)
     * и приглашение ввести пароль в конце вывода.
     */
    private fun scan(buf: ByteArray, n: Int) {
        for (m in DEC_MODE.findAll(String(buf, 0, n, Charsets.ISO_8859_1))) {
            val on = m.groupValues[2] == "h"
            for (p in m.groupValues[1].split(';')) when (p) {
                "1049", "1047", "47" -> altScreen = on
                "1000", "1002", "1003" -> mouse = on
                "1006" -> sgrMouse = on
            }
        }
        val from = maxOf(0, n - 256)
        secretPrompt = SECRET_PROMPT.containsMatchIn(String(buf, from, n - from).replace(ANSI, ""))
    }

    /**
     * Прокрутка на альтернативном экране (истории у терминала там нет): программе с мышью (tmux, mc, vim) —
     * колесо в клетке [col], [row] (с 1), без мыши (less, man, htop) — ↑/↓, как alternateScroll в xterm.
     */
    fun wheel(up: Boolean, col: Int, row: Int) = when {
        !mouse -> emulator.dispatchKey(0, if (up) VTermKey.UP else VTermKey.DOWN)
        sgrMouse -> write("\u001b[<${if (up) 64 else 65};$col;${row}M")
        // Старый формат X10: байт = 32 + значение, в ASCII помещаются клетки до 95
        else -> write("\u001b[M" + (if (up) 96 else 97).toChar() + (32 + col.coerceAtMost(95)).toChar() + (32 + row.coerceAtMost(95)).toChar())
    }

    /** Соединение, пока вход выполнен: обзор и SFTP открывают свои каналы поверх него — без второго входа. */
    val connection: Connection? get() = conn.takeIf { state == SessionState.Connected }

    /** Выполнить скрипт в `sh` (через stdin: без экранирования и независимо от shell пользователя), вернуть вывод. */
    fun exec(script: String): String {
        val s = checkNotNull(connection) { tr("Нет соединения") }.openSession()
        try {
            s.execCommand("sh")
            s.stdin.use { it.write(script.toByteArray()) }
            return s.stdout.readBytes().decodeToString()
        } finally {
            s.close()
        }
    }

    fun addHistory(command: String) {
        if (command.isBlank() || secretPrompt || history.lastOrNull() == command) return
        history += command
        if (history.size > 200) history.removeAt(0)
    }

    private fun authenticate(c: Connection): Boolean {
        val t = target
        if (t.key.isNotBlank()) return c.authenticateWithPublicKey(t.user, pemLines(t.key).toCharArray(), t.passphrase.ifEmpty { null })
        val methods = c.getRemainingAuthMethods(t.user)
        if ("password" in methods && c.authenticateWithPassword(t.user, t.password)) return true
        // Многие серверы принимают пароль только через keyboard-interactive (PAM)
        return "keyboard-interactive" in methods &&
            c.authenticateWithKeyboardInteractive(t.user) { _, _, n, _, _ -> Array(n) { t.password } }
    }

    /** Ввод, пока нет соединения, отбрасываем: после переподключения он выполнился бы в новом shell вслепую. */
    fun write(bytes: ByteArray) {
        if (state == SessionState.Connected) output.trySend(bytes)
    }

    fun write(text: String) = write(text.toByteArray())

    /** Быстрая команда/сценарий: шаги подряд, каждый с Enter — shell выполнит их по очереди. */
    fun run(snippet: Snippet) = write(snippet.steps.joinToString("") { "$it\r" })

    /** Закрыть соединение; [start] откроет его заново в том же терминале. */
    fun close(error: String? = null) {
        if (state is SessionState.Closed) return
        state = SessionState.Closed(error)
        hostKeyPrompt?.answer?.complete(false)
        job?.cancel()
        disconnect()
    }

    private fun disconnect() {
        val s = shell
        val c = conn ?: return
        shell = null
        conn = null
        while (output.tryReceive().isSuccess) Unit
        // Не в UI-потоке: close() ждёт замок Connection, а его может держать ping на мёртвой сети
        thread { runCatching { s?.close() }; runCatching { c.close() } }
    }

    /** TOFU: известный ключ — пускаем молча, новый — спрашиваем, изменившийся — отказ. */
    private fun verifyHostKey(hostPort: String, algo: String, key: ByteArray): Boolean {
        val fp = "$algo SHA256:" + Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(key), Base64.NO_PADDING or Base64.NO_WRAP,
        )
        Store.knownHosts[hostPort]?.let { known ->
            if (known == fp) return true
            throw SecurityException(tr("Ключ сервера изменился! Возможна атака MITM.\nБыл: %s\nСейчас: %s", known, fp))
        }
        val prompt = HostKeyPrompt(hostPort, fp, CompletableDeferred())
        hostKeyPrompt = prompt
        val trusted = runBlocking { prompt.answer.await() }
        hostKeyPrompt = null
        if (trusted) Store.trustHost(hostPort, fp)
        return trusted
    }
}

private const val RETRIES = 10
private const val KEEPALIVE = 15_000L

private val Exception.text get() = message ?: javaClass.simpleName

private val DEC_MODE = Regex("\u001b\\[\\?([0-9;]+)([hl])")
private val ANSI = Regex("\u001b\\[[0-9;?]*[ -/]*[@-~]")
private val SECRET_PROMPT = Regex("(?i)(password|passphrase|пароль|密码)[^\n]*[:：]\\s*$")
