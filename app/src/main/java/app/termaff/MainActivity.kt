package app.termaff

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.termaff.data.Server
import app.termaff.data.Store
import app.termaff.ssh.Sessions
import app.termaff.ui.AppLock
import app.termaff.ui.LockScreen
import app.termaff.ui.ServerEditScreen
import app.termaff.ui.ServersScreen
import app.termaff.ui.SettingsScreen
import app.termaff.ui.TerminalScreen
import app.termaff.ui.TermaffTheme

private enum class Screen { Servers, Edit, Terminal, Settings }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        // Тёмная тема всегда → светлые иконки статус-бара
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            TermaffTheme {
                // С блокировкой миниатюра в «Недавних» не должна показывать терминал (скриншоты при этом работают)
                LaunchedEffect(Store.lock) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!Store.lock)
                }
                var screen by remember { mutableStateOf(Screen.Servers) }
                var editing by remember { mutableStateOf<Server?>(null) }
                val session = Sessions.current
                when {
                    AppLock.locked -> LockScreen()
                    screen == Screen.Terminal && session != null -> TerminalScreen(
                        session = session,
                        onBack = { screen = Screen.Servers },
                        onClose = { Sessions.close(); screen = Screen.Servers },
                        onReconnect = { Sessions.reconnect(session.target) },
                    )
                    screen == Screen.Edit -> ServerEditScreen(editing) { screen = Screen.Servers }
                    screen == Screen.Settings -> SettingsScreen { screen = Screen.Servers }
                    else -> ServersScreen(
                        onOpen = { Sessions.open(it); screen = Screen.Terminal },
                        onEdit = { editing = it; screen = Screen.Edit },
                        onSettings = { screen = Screen.Settings },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onStart()
    }

    override fun onStop() {
        super.onStop()
        AppLock.onStop()
    }
}
