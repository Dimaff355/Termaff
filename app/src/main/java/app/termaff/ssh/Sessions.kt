package app.termaff.ssh

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.termaff.data.Server

/** Живая сессия переживает экраны: «назад» из терминала её не рвёт. Пока одна; на этапе 5 — список + сервис. */
object Sessions {
    var current by mutableStateOf<SshSession?>(null)
        private set

    /** Вернуть живую сессию к этому серверу или открыть новую. */
    fun open(server: Server): SshSession {
        current?.takeIf { it.target.serverId == server.id && it.state !is SessionState.Closed }?.let { return it }
        return reconnect(Target(server))
    }

    fun reconnect(target: Target): SshSession {
        current?.close()
        return SshSession(target).also { current = it; it.start() }
    }

    fun close() {
        current?.close()
        current = null
    }

    fun isLive(serverId: String) = current?.let { it.target.serverId == serverId && it.state == SessionState.Connected } == true
}
