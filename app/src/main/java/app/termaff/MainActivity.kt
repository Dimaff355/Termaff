package app.termaff

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.termaff.data.Server
import app.termaff.data.Snippet
import app.termaff.data.SshKey
import app.termaff.data.Store
import app.termaff.ssh.SessionState
import app.termaff.ssh.Sessions
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.termaff.ui.Accent
import app.termaff.ui.AppLock
import app.termaff.ui.Bg
import app.termaff.ui.Card
import app.termaff.ui.CommandsScreen
import app.termaff.ui.FilesScreen
import app.termaff.ui.HostKeyDialog
import app.termaff.ui.KeyEditScreen
import app.termaff.ui.SnippetEditScreen
import app.termaff.ui.LockScreen
import app.termaff.ui.OverviewScreen
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

/** Страница поверх вкладок; «назад» возвращает на предыдущую страницу или на ту же вкладку. */
private enum class Page { EditServer, EditSnippet, EditKey, Terminal, Overview, Files }

class MainActivity : ComponentActivity() {
    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Android 13+: без разрешения сервис работает, но уведомление о фоновых сессиях не видно — спросим при подключении. */
    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Sessions.init(this)
        // Тёмная тема всегда → светлые иконки статус-бара
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            TermaffTheme {
                // С блокировкой миниатюра в «Недавних» не должна показывать терминал (скриншоты при этом работают)
                LaunchedEffect(Store.lock) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!Store.lock)
                }
                var tab by remember { mutableStateOf(Tab.Servers) }
                val pages = remember { mutableStateListOf<Page>() }
                val page = pages.lastOrNull()
                // Страница уже открыта ниже в стеке (терминал → обзор → терминал) — вернуться к ней, а не плодить копии
                fun go(p: Page) {
                    val i = pages.indexOf(p)
                    if (i < 0) pages += p else while (pages.size > i + 1) pages.removeAt(pages.lastIndex)
                }
                var server by remember { mutableStateOf<Server?>(null) }
                var snippet by remember { mutableStateOf<Snippet?>(null) }
                var sshKey by remember { mutableStateOf(SshKey()) }
                val back: () -> Unit = { pages.removeLastOrNull() }
                val session = Sessions.current
                // Спрашиваем после входа, а не при нажатии: иначе системный диалог перекрыл бы вопрос о ключе сервера
                LaunchedEffect(session?.state == SessionState.Connected) { if (session?.state == SessionState.Connected) askNotifications() }
                when {
                    AppLock.locked -> LockScreen()
                    // key: у каждой сессии своё состояние экрана (строка ввода, режим, позиция в истории)
                    page == Page.Terminal && session != null -> key(session) {
                        TerminalScreen(
                            session = session,
                            onBack = back,
                            onClose = { Sessions.close(session); if (Sessions.current == null) back() },
                            onOverview = { go(Page.Overview) },
                            onFiles = { go(Page.Files) },
                        )
                    }
                    page == Page.Overview && session != null -> key(session) {
                        OverviewScreen(session, back, onTerminal = { go(Page.Terminal) }, onFiles = { go(Page.Files) })
                    }
                    page == Page.Files && session != null -> key(session) { FilesScreen(session, back) }
                    page == Page.EditServer -> ServerEditScreen(server, back)
                    page == Page.EditSnippet -> SnippetEditScreen(snippet, back)
                    page == Page.EditKey -> KeyEditScreen(sshKey, back)
                    // Сессию закрыли, а экран сессии остался в стеке — к вкладкам
                    page == Page.Terminal || page == Page.Overview || page == Page.Files -> LaunchedEffect(Unit) { pages.clear() }
                    else -> Column(Modifier.fillMaxSize()) {
                        BackHandler(tab != Tab.Servers) { tab = Tab.Servers }
                        Box(Modifier.weight(1f)) {
                            when (tab) {
                                Tab.Servers -> ServersScreen(
                                    onOpen = { Sessions.open(it); go(Page.Terminal) },
                                    onOverview = { Sessions.open(it); go(Page.Overview) },
                                    onFiles = { Sessions.open(it); go(Page.Files) },
                                    onEdit = { server = it; go(Page.EditServer) },
                                )
                                Tab.Commands -> CommandsScreen(
                                    onEdit = { snippet = it; go(Page.EditSnippet) },
                                    onRun = { Sessions.current?.run(it); go(Page.Terminal) },
                                )
                                Tab.Settings -> SettingsScreen(onEditKey = { sshKey = it; go(Page.EditKey) })
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
                // Вопрос о ключе нового сервера — поверх любого экрана (подключение могли начать из обзора или файлов)
                if (!AppLock.locked) Sessions.list.firstNotNullOfOrNull { it.hostKeyPrompt }?.let { HostKeyDialog(it) }
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
