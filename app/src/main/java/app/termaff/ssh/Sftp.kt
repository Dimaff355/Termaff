package app.termaff.ssh

import com.trilead.ssh2.Connection
import com.trilead.ssh2.SFTPv3Client
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import app.termaff.tr
import kotlin.coroutines.coroutineContext

class RemoteFile(val name: String, val path: String, val dir: Boolean, val size: Long, val mtime: Long)

/**
 * SFTP поверх соединения сессии. Клиент не потокобезопасен → все вызовы идут по очереди в одном потоке.
 * После переподключения клиент создаётся заново на новом соединении.
 */
class Sftp(private val session: SshSession) {
    private val io = Dispatchers.IO.limitedParallelism(1)
    private var conn: Connection? = null
    private var client: SFTPv3Client? = null

    private suspend fun <T> use(block: suspend (SFTPv3Client) -> T): T = withContext(io) {
        val c = checkNotNull(session.connection) { tr("Нет соединения") }
        if (c !== conn) {
            client?.close()
            // Нет «Subsystem sftp» в sshd_config — sshlib отвечает невнятным «subsystem request failed»
            client = try { SFTPv3Client(c) } catch (e: IOException) { throw IOException(tr("Сервер не поддерживает SFTP"), e) }
            conn = c
        }
        block(client!!)
    }

    suspend fun home(): String = use { it.canonicalPath(".") }

    /** Папки сверху, затем по имени. Ссылки на папки — тоже папки. */
    suspend fun list(path: String): List<RemoteFile> = use { c ->
        c.ls(path).filter { it.filename != "." && it.filename != ".." }.map { e ->
            val full = child(path, e.filename)
            val a = if (e.attributes.isSymlink) runCatching { c.stat(full) }.getOrDefault(e.attributes) else e.attributes
            RemoteFile(e.filename, full, a.isDirectory, a.size ?: 0, e.attributes.mtime ?: 0)
        }.sortedWith(compareBy<RemoteFile> { !it.dir }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** Скачать в [out]; [progress] — сколько байт уже передано. */
    suspend fun download(path: String, out: OutputStream, progress: (Long) -> Unit) = use { c ->
        val h = c.openFileRO(path)
        try {
            val buf = ByteArray(CHUNK)
            var pos = 0L
            while (true) {
                coroutineContext.ensureActive()
                val n = c.read(h, pos, buf, 0, CHUNK)
                if (n <= 0) break
                out.write(buf, 0, n)
                pos += n
                progress(pos)
            }
        } finally {
            c.closeFile(h)
        }
    }

    /** Загрузить из [input] в [path] (существующий файл перезаписывается). Прерванная загрузка удаляет недописанный файл. */
    suspend fun upload(input: InputStream, path: String, progress: (Long) -> Unit) = use { c ->
        val h = c.createFileTruncate(path)
        var done = false
        try {
            val buf = ByteArray(CHUNK)
            var pos = 0L
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                c.write(h, pos, buf, 0, n)
                pos += n
                progress(pos)
            }
            done = true
        } finally {
            c.closeFile(h)
            if (!done) runCatching { c.rm(path) }
        }
    }

    suspend fun mkdir(path: String) = use { it.mkdir(path, 493) } // 0755

    suspend fun rename(from: String, to: String) = use { it.mv(from, to) }

    /** Папку — только пустую: рекурсивное удаление по телефону слишком легко сделать случайно. */
    suspend fun delete(f: RemoteFile) = use { if (f.dir) it.rmdir(f.path) else it.rm(f.path) }

    /** Закрыть канал SFTP (соединение сессии остаётся). */
    fun close() {
        CoroutineScope(io).launch { client?.close(); client = null; conn = null }
    }

    companion object {
        /** Максимальный блок чтения/записи SFTP в sshlib. */
        private const val CHUNK = 32768

        fun child(dir: String, name: String) = if (dir.endsWith("/")) dir + name else "$dir/$name"
        fun parent(path: String) = path.substringBeforeLast('/', "").ifEmpty { "/" }
    }
}
