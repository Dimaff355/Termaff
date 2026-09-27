package app.termaff.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.termaff.data.Server
import app.termaff.data.Store
import app.termaff.data.Vault
import app.termaff.tr

/** Добавление/редактирование сервера. Пароль расшифровывается только на время редактирования и не попадает в Bundle. */
@Composable
fun ServerEditScreen(server: Server?, onDone: () -> Unit) {
    val s = server ?: Server()
    var name by remember { mutableStateOf(s.name) }
    var host by remember { mutableStateOf(s.host) }
    var port by remember { mutableStateOf(s.port.toString()) }
    var user by remember { mutableStateOf(s.user) }
    var password by remember { mutableStateOf(Vault.decrypt(s.password)) }
    var keyId by remember { mutableStateOf(s.keyId.takeIf { Store.key(it) != null }.orEmpty()) }
    var useKey by remember { mutableStateOf(keyId.isNotEmpty()) }
    var tags by remember { mutableStateOf(s.tags.joinToString(", ")) }
    var startup by remember { mutableStateOf(s.startup) }
    val valid = host.isNotBlank() && user.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535 && (!useKey || keyId.isNotEmpty())

    fun save() {
        Store.save(
            s.copy(
                name = name.trim(), host = host.trim(), port = port.toInt(), user = user.trim(),
                password = if (useKey) "" else Vault.encrypt(password), keyId = if (useKey) keyId else "",
                tags = tags.split(',').map(String::trim).filter(String::isNotEmpty),
                startup = startup.trim(),
            ),
        )
        onDone()
    }

    BackHandler(onBack = onDone)
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад")) }
            Text(if (server == null) tr("Новый сервер") else tr("Изменить"), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            TextButton(onClick = ::save, enabled = valid) { Text(tr("Сохранить")) }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field(name, { name = it }, tr("Название (необязательно)"))
            Field(host, { host = it.trim() }, tr("Хост или IP"), KeyboardType.Uri)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(user, { user = it.trim() }, tr("Пользователь"), modifier = Modifier.weight(2f))
                Field(port, { port = it.filter(Char::isDigit).take(5) }, tr("Порт"), KeyboardType.Number, Modifier.weight(1f))
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(tr("Пароль"), tr("Ключ")).forEachIndexed { i, label ->
                    SegmentedButton(useKey == (i == 1), {
                        useKey = i == 1
                        if (useKey && keyId.isEmpty()) keyId = Store.keys.singleOrNull()?.id.orEmpty()
                    }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                }
            }
            if (!useKey) Field(password, { password = it }, tr("Пароль"), KeyboardType.Password, visual = PasswordVisualTransformation())
            else if (Store.keys.isEmpty()) Text(tr("Ключей пока нет. Создайте или импортируйте ключ в Настройках."), color = Muted)
            else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Store.keys.forEach { k ->
                    FilterChip(keyId == k.id, { keyId = k.id }, { Text(k.name) },
                        leadingIcon = { Icon(Icons.Filled.Key, null, Modifier.size(18.dp)) })
                }
            }
            Field(tags, { tags = it }, tr("Теги через запятую"))
            Field(startup, { startup = it }, tr("Команда после входа (необязательно)"))
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    type: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    visual: VisualTransformation = VisualTransformation.None,
) = TextField(
    value, onChange, modifier, label = { Text(label) }, singleLine = true, visualTransformation = visual,
    shape = RoundedCornerShape(12.dp), colors = fieldColors(),
    keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false),
)
