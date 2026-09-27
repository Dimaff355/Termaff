package app.termaff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.termaff.data.Server
import app.termaff.data.Store
import app.termaff.ssh.Sessions
import java.util.UUID

@Composable
fun ServersScreen(onOpen: (Server) -> Unit, onOverview: (Server) -> Unit, onFiles: (Server) -> Unit, onEdit: (Server?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Server?>(null) }
    val q = query.trim()
    val list = Store.servers.filter {
        q.isEmpty() || listOf(it.name, it.host, it.user).any { f -> f.contains(q, true) } || it.tags.any { t -> t.contains(q, true) }
    }

    Scaffold(
        containerColor = Bg,
        // Низ занимает нижняя панель навигации (она сама учитывает системную)
        contentWindowInsets = WindowInsets.statusBars,
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(null) }, containerColor = Accent, contentColor = Bg, shape = CircleShape) {
                Icon(Icons.Filled.Add, "Добавить сервер")
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 16.dp, 16.dp, pad.calculateBottomPadding() + 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Серверы", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
            item {
                TextField(
                    query, { query = it }, Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск серверов…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors(),
                )
            }
            items(list, key = { it.id }) { s -> ServerCard(s, onOpen, onOverview, onFiles, onEdit, onDelete = { deleting = s }) }
            if (Store.servers.isEmpty()) item {
                Text("Пока пусто. Нажмите «+», чтобы добавить сервер.", color = Muted, modifier = Modifier.padding(top = 32.dp))
            }
        }
    }

    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить «${s.title}»?") },
            confirmButton = { TextButton(onClick = { Store.delete(s.id); deleting = null }) { Text("Удалить", color = Danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ServerCard(
    s: Server, onOpen: (Server) -> Unit, onOverview: (Server) -> Unit, onFiles: (Server) -> Unit,
    onEdit: (Server) -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).clickable { onOpen(s) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(Bg, RoundedCornerShape(14.dp)), Alignment.Center) {
            Icon(Icons.Filled.Dns, null, tint = Accent)
        }
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Sessions.isLive(s.id)) Box(Modifier.padding(end = 8.dp).size(8.dp).background(Accent, CircleShape))
                Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            Text("${s.user}@${s.host}" + if (s.port != 22) ":${s.port}" else "", color = Muted,
                style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (s.tags.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { s.tags.forEach { Tag(it) } }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Меню", tint = Muted) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Обзор") }, { menu = false; onOverview(s) })
                DropdownMenuItem({ Text("Файлы") }, { menu = false; onFiles(s) })
                DropdownMenuItem({ Text("Изменить") }, { menu = false; onEdit(s) })
                DropdownMenuItem({ Text("Копировать") }, {
                    menu = false
                    Store.save(s.copy(id = UUID.randomUUID().toString(), name = "${s.title} (копия)"))
                })
                DropdownMenuItem({ Text("Удалить", color = Danger) }, { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun Tag(text: String) = Text(
    text, color = Muted, style = MaterialTheme.typography.labelSmall,
    modifier = Modifier.background(Bg, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
)

@Composable
fun fieldColors() = TextFieldDefaults.colors(
    focusedIndicatorColor = Bg, unfocusedIndicatorColor = Bg,
    focusedContainerColor = Card, unfocusedContainerColor = Card,
)
