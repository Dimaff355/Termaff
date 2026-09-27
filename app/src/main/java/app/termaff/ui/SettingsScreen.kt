package app.termaff.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.termaff.data.SshKey
import app.termaff.data.Store
import app.termaff.data.Vault
import app.termaff.ssh.Keys
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onEditKey: (SshKey) -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Section("Ключи SSH")
        Store.keys.forEach { k -> KeyCard(k) { onEditKey(k) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val (pem, public) = Keys.generate("termaff")
                onEditKey(SshKey(name = "Ключ ${Store.keys.size + 1}", private = Vault.encrypt(pem), public = public))
            }) { Text("Создать Ed25519") }
            OutlinedButton(onClick = { onEditKey(SshKey()) }) { Text("Импортировать") }
        }
        Section("Терминал")
        TerminalSettings()
        Section("Безопасность")
        LockSetting()
    }
}

@Composable
private fun Section(title: String) =
    Text(title, color = Muted, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))

@Composable
private fun KeyCard(k: SshKey, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).clickable(onClick = onClick).padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(Icons.Filled.Key, null, tint = Accent)
    Column(Modifier.weight(1f).padding(start = 16.dp)) {
        Text(k.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val used = Store.servers.count { it.keyId == k.id }
        Text(k.type + if (used > 0) " · серверов: $used" else "", color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Превью в выбранной схеме и размере, размер шрифта (авто — под 80 колонок), цветовая схема. */
@Composable
private fun TerminalSettings() {
    val theme = termTheme(Store.theme)
    val size = terminalFontSize()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(16.dp)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color(theme.ansi[2]))) { append("user@server") }
                append(":")
                withStyle(SpanStyle(color = Color(theme.ansi[4]))) { append("~") }
                append("$ ls\n")
                withStyle(SpanStyle(color = Color(theme.ansi[12]))) { append("docs") }
                append("  notes.txt  ")
                withStyle(SpanStyle(color = Color(theme.ansi[10]))) { append("run.sh") }
            },
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.bg).padding(12.dp),
            color = theme.fg, fontFamily = Mono, fontSize = size.sp, lineHeight = (size * 1.3f).sp,
        )
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Размер шрифта", style = MaterialTheme.typography.titleMedium)
                Text(if (Store.fontSize > 0) "${size.roundToInt()} sp · меняется и щипком" else "Авто: 80 колонок по ширине",
                    color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { Store.saveFontSize((size.roundToInt() - 1f).coerceAtLeast(6f)) }) { Icon(Icons.Filled.Remove, "Меньше") }
            IconButton(onClick = { Store.saveFontSize((size.roundToInt() + 1f).coerceAtMost(24f)) }) { Icon(Icons.Filled.Add, "Больше") }
        }
        if (Store.fontSize > 0) FilterChip(false, { Store.saveFontSize(0f) }, { Text("Вернуть авто") })
        Text("Цветовая схема", Modifier.padding(top = 8.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermThemes.forEach { t ->
                FilterChip(
                    t == theme, { Store.saveTheme(t.id) }, { Text(t.name) },
                    leadingIcon = { Box(Modifier.size(16.dp).background(t.bg, CircleShape).border(2.dp, Color(t.ansi[2]), CircleShape)) },
                )
            }
        }
    }
}

@Composable
private fun LockSetting() {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Вход по отпечатку", style = MaterialTheme.typography.titleMedium)
            Text(
                if (AppLock.supported) "Отпечаток или PIN телефона при открытии приложения" else "Нужен Android 11 или новее",
                color = Muted, style = MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(
            checked = Store.lock,
            enabled = AppLock.supported,
            onCheckedChange = { on ->
                when {
                    // Подтверждение и при включении (проверка, что работает), и при выключении
                    AppLock.available(context) ->
                        AppLock.prompt(context, if (on) "Включить вход по отпечатку" else "Выключить вход по отпечатку") { ok ->
                            if (ok) Store.saveLock(on)
                        }
                    on -> Toast.makeText(context, "Сначала включите блокировку экрана в настройках телефона", Toast.LENGTH_LONG).show()
                    else -> Store.saveLock(false)
                }
            },
        )
    }
}
