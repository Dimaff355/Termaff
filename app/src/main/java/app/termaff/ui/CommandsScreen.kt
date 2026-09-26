package app.termaff.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.termaff.data.Snippet
import app.termaff.data.Store
import app.termaff.ssh.SessionState
import app.termaff.ssh.Sessions

/**
 * Быстрые команды: однострочные — плитками, многострочные — сценариями с шагами.
 * ▶ выполняет команду в открытой сессии (если она подходит по серверу) и открывает терминал.
 */
@Composable
fun CommandsScreen(onEdit: (Snippet?) -> Unit, onRun: (Snippet) -> Unit) {
    val live = Sessions.current?.takeIf { it.state == SessionState.Connected }
    val (scenarios, quick) = Store.snippets.partition { it.steps.size > 1 }
    fun runnable(s: Snippet) = live != null && s.fits(live.target.serverId)

    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets.statusBars,
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(null) }, containerColor = Accent, contentColor = Bg, shape = CircleShape) {
                Icon(Icons.Filled.Add, "Добавить команду")
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 16.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Команды", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
            if (Store.snippets.isEmpty()) item {
                Text("Сохраняйте частые команды и сценарии из нескольких шагов — они появятся в терминале над клавишами.",
                    color = Muted, modifier = Modifier.padding(top = 16.dp))
            }
            // Плитки по две в ряд, как в макете
            items(quick.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { s -> Tile(s, runnable(s), onEdit, onRun, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (scenarios.isNotEmpty()) item {
                Text("Сценарии", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            items(scenarios, key = { it.id }) { s -> Scenario(s, runnable(s), onEdit, onRun) }
        }
    }
}

@Composable
private fun Tile(s: Snippet, runnable: Boolean, onEdit: (Snippet) -> Unit, onRun: (Snippet) -> Unit, modifier: Modifier) = Column(
    modifier.clip(RoundedCornerShape(20.dp)).background(Card).clickable { onEdit(s) }.padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(Bg, RoundedCornerShape(10.dp)), Alignment.Center) {
            Icon(Icons.Filled.Terminal, null, tint = Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.weight(1f))
        if (runnable) RunButton { onRun(s) }
    }
    Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(s.command.trim(), color = Muted, style = MaterialTheme.typography.bodySmall, fontFamily = Mono,
        maxLines = 2, overflow = TextOverflow.Ellipsis)
    ServerLabel(s)
}

@Composable
private fun Scenario(s: Snippet, runnable: Boolean, onEdit: (Snippet) -> Unit, onRun: (Snippet) -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).clickable { onEdit(s) }.padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ServerLabel(s)
        }
        if (runnable) RunButton { onRun(s) }
    }
    s.steps.forEachIndexed { i, step ->
        Text("${i + 1}. $step", color = Muted, style = MaterialTheme.typography.bodySmall, fontFamily = Mono,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RunButton(onClick: () -> Unit) = Box(
    Modifier.size(36.dp).clip(CircleShape).background(Accent).clickable(onClick = onClick), Alignment.Center,
) { Icon(Icons.Filled.PlayArrow, "Выполнить", tint = Bg) }

@Composable
private fun ServerLabel(s: Snippet) {
    val server = Store.servers.firstOrNull { it.id == s.serverId }
    Text(server?.title ?: "Все серверы", color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
}

/** Добавление/редактирование команды. */
@Composable
fun SnippetEditScreen(snippet: Snippet?, onDone: () -> Unit) {
    val s = snippet ?: Snippet(serverId = Sessions.current?.target?.serverId.orEmpty())
    var name by remember { mutableStateOf(s.name) }
    var command by remember { mutableStateOf(s.command) }
    var serverId by remember { mutableStateOf(s.serverId) }

    BackHandler(onBack = onDone)
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text(if (snippet == null) "Новая команда" else "Изменить", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            TextButton(
                onClick = { Store.save(s.copy(name = name.trim(), command = command.trim(), serverId = serverId)); onDone() },
                enabled = command.isNotBlank(),
            ) { Text("Сохранить") }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextField(
                name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Название (необязательно)") },
                singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors(),
            )
            TextField(
                command, { command = it }, Modifier.fillMaxWidth(), label = { Text("Команда") }, minLines = 3,
                supportingText = { Text("Несколько строк — сценарий: строки выполняются по очереди") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            Text("Сервер", color = Muted, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(serverId.isEmpty(), { serverId = "" }, { Text("Все серверы") })
                Store.servers.forEach { sv -> FilterChip(serverId == sv.id, { serverId = sv.id }, { Text(sv.title) }) }
            }
            if (snippet != null) TextButton(onClick = { Store.deleteSnippet(s.id); onDone() }) {
                Text("Удалить команду", color = Danger)
            }
        }
    }
}
