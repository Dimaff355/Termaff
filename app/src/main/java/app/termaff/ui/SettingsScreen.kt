package app.termaff.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.termaff.data.Store

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Row(
            Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(16.dp),
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
}
