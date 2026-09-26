package app.termaff.ssh

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

/** Живые сессии переживают экраны. Пока одна; на этапе 5 — список + foreground service. */
object Sessions {
    /** host:port → отпечаток ключа. На этапе 2 переедет в постоянное хранилище. */
    val trustedKeys: MutableMap<String, String> = ConcurrentHashMap()

    var current by mutableStateOf<SshSession?>(null)
        private set

    fun open(target: Target): SshSession {
        current?.close()
        return SshSession(target, trustedKeys).also { current = it; it.start() }
    }

    fun closeCurrent() {
        current?.close()
        current = null
    }
}
