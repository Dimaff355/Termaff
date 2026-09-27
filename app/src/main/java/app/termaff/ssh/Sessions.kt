package app.termaff.ssh

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.termaff.data.Server

/** Открытые сессии; переживают экраны, пока жив процесс (его держит [SessionService]). */
object Sessions {
    private lateinit var app: Context
    val list = mutableStateListOf<SshSession>()
    /** Сессия на экране терминала. */
    var current by mutableStateOf<SshSession?>(null)
        private set

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** Живая сессия к этому серверу — переключиться на неё; иначе открыть новую (со свежими настройками сервера). */
    fun open(server: Server): SshSession {
        list.firstOrNull { it.target.serverId == server.id }?.let {
            if (it.state !is SessionState.Closed) return it.also(::select)
            close(it)
        }
        return SshSession(Target(server)).also { list += it; current = it; it.start() }
    }

    fun select(session: SshSession) {
        current = session
    }

    fun close(session: SshSession) {
        session.close()
        list -= session
        if (current == session) current = list.lastOrNull()
        changed()
    }

    fun closeAll() = list.toList().forEach(::close)

    fun isLive(serverId: String) = list.any { it.target.serverId == serverId && it.state == SessionState.Connected }

    /** Состояние какой-то сессии изменилось (из любого потока) — сервис показывает, сколько соединений живо. */
    fun changed() {
        SessionService.sync(app, list.filter { it.state !is SessionState.Closed }.map { it.target.title })
    }
}
