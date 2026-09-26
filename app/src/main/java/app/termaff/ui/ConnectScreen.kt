package app.termaff.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.termaff.ssh.Target

/** Временный экран быстрого подключения (этап 1). На этапе 2 его заменит список серверов. */
@Composable
fun ConnectScreen(onConnect: (Target) -> Unit) {
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("22") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var key by rememberSaveable { mutableStateOf("") }
    val plain = KeyboardOptions(autoCorrectEnabled = false)

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Termaff", style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(host, { host = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Хост") },
            singleLine = true, keyboardOptions = plain.copy(keyboardType = KeyboardType.Uri))
        OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(),
            label = { Text("Порт") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(user, { user = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Пользователь") },
            singleLine = true, keyboardOptions = plain)
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(),
            label = { Text(if (key.isBlank()) "Пароль" else "Пароль ключа (если есть)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("Приватный ключ (необязательно)") },
            minLines = 2, maxLines = 4, keyboardOptions = plain)
        Button(
            onClick = { onConnect(Target(host, port.toIntOrNull() ?: 22, user, password, key)) },
            enabled = host.isNotBlank() && user.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Подключиться") }
    }
}
