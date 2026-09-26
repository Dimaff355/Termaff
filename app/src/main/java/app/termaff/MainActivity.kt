package app.termaff

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.termaff.data.Server
import app.termaff.data.Store
import app.termaff.ssh.Sessions
import app.termaff.ui.ServerEditScreen
import app.termaff.ui.ServersScreen
import app.termaff.ui.TerminalScreen
import app.termaff.ui.TermaffTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        // Тёмная тема всегда → светлые иконки статус-бара
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            TermaffTheme {
                var editing by remember { mutableStateOf<Server?>(null) }
                var isEditing by remember { mutableStateOf(false) }
                var inTerminal by remember { mutableStateOf(false) }
                val session = Sessions.current
                when {
                    inTerminal && session != null -> TerminalScreen(
                        session = session,
                        onBack = { inTerminal = false },
                        onClose = { Sessions.close(); inTerminal = false },
                        onReconnect = { Sessions.reconnect(session.target) },
                    )
                    isEditing -> ServerEditScreen(editing) { isEditing = false }
                    else -> ServersScreen(
                        onOpen = { Sessions.open(it); inTerminal = true },
                        onEdit = { editing = it; isEditing = true },
                    )
                }
            }
        }
    }
}
