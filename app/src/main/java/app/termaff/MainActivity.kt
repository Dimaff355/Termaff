package app.termaff

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.termaff.data.Server
import app.termaff.data.Snippet
import app.termaff.data.Store
import app.termaff.ssh.Sessions
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.termaff.ui.Accent
import app.termaff.ui.AppLock
import app.termaff.ui.Bg
import app.termaff.ui.Card
import app.termaff.ui.CommandsScreen
import app.termaff.ui.SnippetEditScreen
import app.termaff.ui.LockScreen
import app.termaff.ui.ServerEditScreen
import app.termaff.ui.ServersScreen
import app.termaff.ui.SettingsScreen
import app.termaff.ui.TerminalScreen
import app.termaff.ui.TermaffTheme

/** Вкладки нижней панели. */
private enum class Tab(val title: String, val icon: ImageVector) {
    Servers("Серверы", Icons.Filled.Dns),
    Commands("Команды", Icons.Filled.Terminal),
    Settings("Настройки", Icons.Filled.Settings),
}

/** Страница поверх вкладок; «назад» с неё возвращает на ту же вкладку. */
private enum class Page { Tabs, EditServer, EditSnippet, Terminal }

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
                var tab by remember { mutableStateOf(Tab.Servers) }
                var page by remember { mutableStateOf(Page.Tabs) }
                var server by remember { mutableStateOf<Server?>(null) }
                var snippet by remember { mutableStateOf<Snippet?>(null) }
                val back = { page = Page.Tabs }
                val session = Sessions.current
                when {
                    AppLock.locked -> LockScreen()
                    page == Page.Terminal && session != null -> TerminalScreen(
                        session = session,
                        onBack = back,
                        onClose = { Sessions.close(); back() },
                        onReconnect = { Sessions.reconnect(session.target) },
                    )
                    page == Page.EditServer -> ServerEditScreen(server, back)
                    page == Page.EditSnippet -> SnippetEditScreen(snippet, back)
                    else -> Column(Modifier.fillMaxSize()) {
                        BackHandler(tab != Tab.Servers) { tab = Tab.Servers }
                        Box(Modifier.weight(1f)) {
                            when (tab) {
                                Tab.Servers -> ServersScreen(
                                    onOpen = { Sessions.open(it); page = Page.Terminal },
                                    onEdit = { server = it; page = Page.EditServer },
                                )
                                Tab.Commands -> CommandsScreen(
                                    onEdit = { snippet = it; page = Page.EditSnippet },
                                    onRun = { Sessions.current?.run(it); page = Page.Terminal },
                                )
                                Tab.Settings -> SettingsScreen()
                            }
                        }
                        NavigationBar(containerColor = Card) {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t, onClick = { tab = t },
                                    icon = { Icon(t.icon, null) }, label = { Text(t.title) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Bg,
                                    ),
                                )
                            }
                        }
                    }
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
