package app.termaff.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.termaff.data.SshKey
import app.termaff.data.Store
import app.termaff.data.Vault
import app.termaff.ssh.Keys
import app.termaff.tr

/**
 * Ключ: импорт (вставить или из файла), просмотр публичной части для authorized_keys, удаление.
 * [key] без записи в Store — новый (сгенерированный или пустой для импорта). Секреты не попадают в Bundle.
 */
@Composable
fun KeyEditScreen(key: SshKey, onDone: () -> Unit) {
    val saved = Store.key(key.id) != null
    var name by remember { mutableStateOf(key.name) }
    var pem by remember { mutableStateOf(Vault.decrypt(key.private)) }
    var passphrase by remember { mutableStateOf(Vault.decrypt(key.passphrase)) }
    // Приватную часть не показываем при открытии — только по кнопке (и по отпечатку, если включён вход)
    var showKey by remember { mutableStateOf(pem.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openInputStream(uri)?.use { pem = it.reader().readText().take(32_000) } }
        showKey = pem.isEmpty()
    }

    fun save() {
        val comment = name.trim().replace(Regex("\\s+"), "-").ifEmpty { "termaff" }
        // Разбор ключа заодно проверяет формат и пароль — ошибка видна сразу, а не при подключении
        val public = runCatching { Keys.publicKey(pem, passphrase, comment) }.getOrElse {
            error = tr("Не удалось прочитать ключ: неверный формат или пароль ключа")
            return
        }
        Store.save(key.copy(name = name.trim(), private = Vault.encrypt(pem.trim()), passphrase = Vault.encrypt(passphrase), public = public))
        onDone()
    }

    BackHandler(onBack = onDone)
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад")) }
            Text(if (saved) tr("Ключ") else tr("Новый ключ"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = ::save, enabled = name.isNotBlank() && pem.isNotBlank()) { Text(tr("Сохранить")) }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(tr("Название")) }, singleLine = true,
                shape = RoundedCornerShape(12.dp), colors = fieldColors())
            if (key.public.isNotEmpty()) PublicKey(key.public)
            if (!showKey) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Key, null, tint = Accent)
                    Text(tr("Приватный ключ скрыт"), Modifier.weight(1f).padding(start = 12.dp))
                    TextButton(onClick = {
                        if (Store.lock && AppLock.available(context)) AppLock.prompt(context, tr("Показать ключ")) { showKey = it }
                        else showKey = true
                    }) { Text(tr("Показать")) }
                }
            } else {
                TextField(
                    pem, { pem = it; error = null }, Modifier.fillMaxWidth(),
                    label = { Text(tr("Приватный ключ (OpenSSH/PEM)")) }, minLines = 3, maxLines = 8,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono),
                    shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                TextButton(onClick = { pickKey.launch(arrayOf("*/*")) }) { Text(tr("Загрузить из файла")) }
            }
            TextField(
                passphrase, { passphrase = it; error = null }, Modifier.fillMaxWidth(), label = { Text(tr("Пароль ключа (если есть)")) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            )
            error?.let { Text(it, color = Danger) }
            if (saved) TextButton(onClick = { deleting = true }) { Text(tr("Удалить ключ"), color = Danger) }
        }
    }

    if (deleting) {
        val users = Store.servers.filter { it.keyId == key.id }.joinToString { it.title }
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(tr("Удалить «%s»?", key.name)) },
            text = if (users.isEmpty()) null else ({ Text(tr("Серверы с этим ключом перейдут на вход по паролю: %s", users)) }),
            confirmButton = { TextButton(onClick = { Store.deleteKey(key.id); onDone() }) { Text(tr("Удалить"), color = Danger) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(tr("Отмена")) } },
        )
    }
}

/** Публичный ключ: его добавляют на сервер в ~/.ssh/authorized_keys. */
@Composable
private fun PublicKey(public: String) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp, 12.dp, 8.dp, 4.dp)) {
        Text(tr("Публичный ключ — добавьте его на сервер в ~/.ssh/authorized_keys"), color = Muted,
            style = MaterialTheme.typography.labelMedium)
        Text(public, Modifier.padding(top = 8.dp, end = 8.dp), style = MaterialTheme.typography.bodySmall, fontFamily = Mono)
        Row(Modifier.align(Alignment.End)) {
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("SSH key", public))
                // Android 13+ сам показывает, что скопировано
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, tr("Скопировано"), Toast.LENGTH_SHORT).show()
            }) { Text(tr("Копировать")) }
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, public)
                context.startActivity(Intent.createChooser(send, null))
            }) { Text(tr("Отправить")) }
        }
    }
}
