package app.termaff.ui

import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.termaff.ssh.RemoteFile
import app.termaff.ssh.Sftp
import app.termaff.ssh.SshSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

private class Transfer(val name: String, val total: Long, val done: Long = 0)

/**
 * Файлы сервера по SFTP: переход по папкам, скачивание в выбранное место телефона, загрузка в текущую папку,
 * новая папка, переименование, удаление. Передача идёт, пока экран открыт; уход с экрана её прерывает.
 */
@Composable
fun FilesScreen(session: SshSession, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sftp = remember { Sftp(session) }
    val conn = session.connection
    var path by remember { mutableStateOf<String?>(null) }
    /** Откуда пришли — системное «назад» возвращает в предыдущую папку. */
    val stack = remember { mutableStateListOf<String>() }
    var files by remember { mutableStateOf(emptyList<RemoteFile>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var transfer by remember { mutableStateOf<Transfer?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var menu by remember { mutableStateOf<RemoteFile?>(null) }
    var naming by remember { mutableStateOf<RemoteFile?>(null) } // переименование; пустой name — новая папка
    var deleting by remember { mutableStateOf<RemoteFile?>(null) }
    var downloading by remember { mutableStateOf<RemoteFile?>(null) }
    var replacing by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    val dates = remember { SimpleDateFormat("d MMM yyyy, HH:mm") }

    fun Exception.text() = message ?: javaClass.simpleName
    fun act(block: suspend () -> Unit) = scope.launch {
        loading = true
        try { block(); error = null } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.text() }
        loading = false
    }
    suspend fun show(p: String) { files = sftp.list(p); path = p }
    suspend fun refresh() = path?.let { show(it) }
    fun load(p: String, onOk: () -> Unit = {}) = act { show(p); onOk() }
    fun open(p: String) = path.let { from -> load(p) { if (from != null) stack += from } }
    /** Одна передача за раз; прогресс внизу экрана, отмена — крестиком. */
    fun run(name: String, total: Long, onFail: () -> Unit = {}, block: suspend ((Long) -> Unit) -> Unit) {
        job = scope.launch {
            transfer = Transfer(name, total)
            try {
                block { transfer = Transfer(name, total, it) }
                error = null
            } catch (e: CancellationException) {
                onFail(); throw e
            } catch (e: Exception) {
                onFail(); error = e.text()
            } finally {
                transfer = null
            }
        }
    }
    fun upload(uri: Uri, name: String) {
        val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1 } ?: -1
        val to = Sftp.child(path ?: return, name)
        run(name, size) { progress ->
            checkNotNull(context.contentResolver.openInputStream(uri)).use { sftp.upload(it, to, progress) }
            refresh()
        }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val f = downloading ?: return@rememberLauncherForActivityResult
        downloading = null
        if (uri == null) return@rememberLauncherForActivityResult
        // Прерванное скачивание не оставляет в телефоне обрезанный файл
        run(f.name, f.size, onFail = { runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) } }) { progress ->
            checkNotNull(context.contentResolver.openOutputStream(uri)).use { sftp.download(f.path, it, progress) }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }?.substringAfterLast('/') ?: "file"
        if (files.any { it.name == name }) replacing = uri to name else upload(uri, name)
    }

    BackHandler { if (stack.isEmpty()) onBack() else load(stack.last()) { stack.removeAt(stack.lastIndex) } }
    // После переподключения — тот же путь на новом соединении
    LaunchedEffect(conn) { if (conn != null) act { show(path ?: sftp.home()) } }
    DisposableEffect(Unit) { onDispose { sftp.close() } }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        SessionHeader(session, onBack) {
            if (conn != null && path != null) {
                IconButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = transfer == null) {
                    Icon(Icons.Filled.Upload, "Загрузить файл")
                }
                IconButton(onClick = { naming = RemoteFile("", "", true, 0, 0) }) { Icon(Icons.Filled.CreateNewFolder, "Новая папка") }
            }
        }
        if (conn == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { SessionStatus(session) }
            return@Column
        }
        Text(
            path ?: "…", fontFamily = Mono, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .background(Card, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Accent, trackColor = Bg)
        error?.let { Text(it, color = Danger, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
            path?.takeIf { it != "/" }?.let { p ->
                item { FileRow(Icons.Filled.Folder, "..", "Наверх", onClick = { open(Sftp.parent(p)) }) }
            }
            items(files, key = { it.name }) { f ->
                Box {
                    val sub = (if (f.dir) "" else humanSize(f.size) + " · ") + dates.format(Date(f.mtime * 1000))
                    FileRow(
                        if (f.dir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile, f.name, sub,
                        onClick = { if (f.dir) open(f.path) else menu = f }, onLongClick = { menu = f },
                    )
                    DropdownMenu(menu == f, { menu = null }) {
                        if (!f.dir) DropdownMenuItem({ Text("Скачать") }, {
                            menu = null; downloading = f; saver.launch(f.name)
                        }, enabled = transfer == null)
                        DropdownMenuItem({ Text("Переименовать") }, { menu = null; naming = f })
                        DropdownMenuItem({ Text("Удалить", color = Danger) }, { menu = null; deleting = f })
                    }
                }
            }
            if (!loading && path != null && files.isEmpty()) item {
                Text("Пусто", color = Muted, modifier = Modifier.padding(16.dp))
            }
        }

        transfer?.let { t ->
            Row(Modifier.fillMaxWidth().background(Card).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${t.name} — ${humanSize(t.done)}" + if (t.total > 0) " / ${humanSize(t.total)}" else "",
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t.total > 0) LinearProgressIndicator({ t.done.toFloat() / t.total }, Modifier.fillMaxWidth(),
                        color = Accent, trackColor = Bg, drawStopIndicator = {})
                    else LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent, trackColor = Bg)
                }
                IconButton(onClick = { job?.cancel() }) { Icon(Icons.Filled.Close, "Отменить") }
            }
        }
    }

    naming?.let { f ->
        var name by remember(f) { mutableStateOf(f.name) }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text(if (f.name.isEmpty()) "Новая папка" else "Переименовать") },
            text = { TextField(name, { name = it }, singleLine = true, colors = fieldColors()) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && '/' !in name && name != f.name, onClick = {
                    naming = null
                    path?.let { p ->
                        val to = Sftp.child(p, name.trim())
                        act { if (f.name.isEmpty()) sftp.mkdir(to) else sftp.rename(f.path, to); refresh() }
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Отмена") } },
        )
    }
    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить «${f.name}»?") },
            text = if (f.dir) ({ Text("Удаляется только пустая папка.") }) else null,
            confirmButton = { TextButton(onClick = { deleting = null; act { sftp.delete(f); refresh() } }) { Text("Удалить", color = Danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
    replacing?.let { (uri, name) ->
        AlertDialog(
            onDismissRequest = { replacing = null },
            title = { Text("Заменить «$name»?") },
            text = { Text("Файл с таким именем уже есть в этой папке.") },
            confirmButton = { TextButton(onClick = { replacing = null; upload(uri, name) }) { Text("Заменить", color = Danger) } },
            dismissButton = { TextButton(onClick = { replacing = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun FileRow(icon: ImageVector, name: String, sub: String,
                    onClick: () -> Unit, onLongClick: (() -> Unit)? = null) = Row(
    Modifier.fillMaxWidth().combinedClickable(onLongClick = onLongClick, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(icon, null, tint = if (icon == Icons.Filled.Folder) Accent else Muted)
    Column(Modifier.weight(1f).padding(start = 16.dp)) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

/** 1536 → «1,5 КБ». */
fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    var v = bytes.toDouble()
    var i = -1
    while (v >= 1024 && i < 3) { v /= 1024; i++ }
    return "%.1f %s".format(v, listOf("КБ", "МБ", "ГБ", "ТБ")[i])
}
