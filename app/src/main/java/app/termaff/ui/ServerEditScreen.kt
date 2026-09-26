package app.termaff.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.termaff.data.Server
import app.termaff.data.Store
import app.termaff.data.Vault

/** Добавление/редактирование сервера. Секреты расшифровываются только на время редактирования и не попадают в Bundle. */
@Composable
fun ServerEditScreen(server: Server?, onDone: () -> Unit) {
    val s = server ?: Server()
    var name by remember { mutableStateOf(s.name) }
    var host by remember { mutableStateOf(s.host) }
    var port by remember { mutableStateOf(s.port.toString()) }
    var user by remember { mutableStateOf(s.user) }
    var password by remember { mutableStateOf(Vault.decrypt(s.password)) }
    var key by remember { mutableStateOf(Vault.decrypt(s.key)) }
    var useKey by remember { mutableStateOf(s.key.isNotEmpty()) }
    var tags by remember { mutableStateOf(s.tags.joinToString(", ")) }
    var startup by remember { mutableStateOf(s.startup) }
    val context = LocalContext.current
    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openInputStream(uri)?.use { key = it.reader().readText().take(32_000) } }
    }
    val valid = host.isNotBlank() && user.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535

    fun save() {
        Store.save(
            s.copy(
                name = name.trim(), host = host.trim(), port = port.toInt(), user = user.trim(),
                password = Vault.encrypt(password), key = if (useKey) Vault.encrypt(key.trim()) else "",
                tags = tags.split(',').map(String::trim).filter(String::isNotEmpty),
                startup = startup.trim(),
            ),
        )
        onDone()
    }

    BackHandler(onBack = onDone)
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text(if (server == null) "Новый сервер" else "Изменить", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            TextButton(onClick = ::save, enabled = valid) { Text("Сохранить") }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field(name, { name = it }, "Название (необязательно)")
            Field(host, { host = it.trim() }, "Хост или IP", KeyboardType.Uri)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(user, { user = it.trim() }, "Пользователь", modifier = Modifier.weight(2f))
                Field(port, { port = it.filter(Char::isDigit).take(5) }, "Порт", KeyboardType.Number, Modifier.weight(1f))
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Пароль", "Ключ").forEachIndexed { i, label ->
                    SegmentedButton(useKey == (i == 1), { useKey = i == 1 }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                }
            }
            if (useKey) {
                TextField(
                    key, { key = it }, Modifier.fillMaxWidth(),
                    label = { Text("Приватный ключ (OpenSSH/PEM)") }, minLines = 3, maxLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                TextButton(onClick = { pickKey.launch(arrayOf("*/*")) }) { Text("Загрузить ключ из файла") }
            }
            Field(password, { password = it }, if (useKey) "Пароль ключа (если есть)" else "Пароль",
                KeyboardType.Password, visual = PasswordVisualTransformation())
            Field(tags, { tags = it }, "Теги через запятую")
            Field(startup, { startup = it }, "Команда после входа (необязательно)")
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
