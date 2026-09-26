package app.termaff.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Сервер. [password] и [key] хранятся зашифрованными ([Vault]); расшифровываются только при подключении/редактировании. */
data class Server(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val password: String = "",
    val key: String = "",
    val tags: List<String> = emptyList(),
    /** Команда, выполняемая сразу после входа. */
    val startup: String = "",
) {
    val title get() = name.ifBlank { host }
}

/** Быстрая команда. Несколько строк в [command] — сценарий: строки выполняются по очереди. */
data class Snippet(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val command: String = "",
    /** Сервер, для которого команда; пусто — для всех. */
    val serverId: String = "",
) {
    val steps get() = command.lines().map(String::trim).filter(String::isNotEmpty)
    val title get() = name.ifBlank { steps.firstOrNull().orEmpty() }
    fun fits(serverId: String) = this.serverId.isEmpty() || this.serverId == serverId
}

/** Всё состояние приложения — один JSON-файл в приватной папке. Записи атомарные (tmp + rename). */
object Store {
    private lateinit var file: File

    var servers by mutableStateOf(emptyList<Server>())
        private set

    var snippets by mutableStateOf(emptyList<Snippet>())
        private set

    /** host:port → «алгоритм SHA256:отпечаток» (TOFU). */
    var knownHosts by mutableStateOf(emptyMap<String, String>())
        private set

    /** Вход в приложение по отпечатку/PIN устройства. */
    var lock by mutableStateOf(false)
        private set

    /** Размер шрифта терминала, sp; 0 — авто (под 80 колонок). Меняется щипком. */
    var fontSize by mutableStateOf(0f)
        private set

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "state.json")
        runCatching { load(JSONObject(file.readText())) }
    }

    fun save(server: Server) = update {
        servers = if (servers.any { it.id == server.id }) servers.map { if (it.id == server.id) server else it } else servers + server
    }

    fun delete(id: String) = update {
        servers = servers.filter { it.id != id }
        snippets = snippets.filter { it.serverId != id }
    }

    fun save(snippet: Snippet) = update {
        snippets = if (snippets.any { it.id == snippet.id }) snippets.map { if (it.id == snippet.id) snippet else it } else snippets + snippet
    }

    fun deleteSnippet(id: String) = update { snippets = snippets.filter { it.id != id } }

    fun saveLock(on: Boolean) = update { lock = on }

    fun saveFontSize(sp: Float) = update { fontSize = sp }

    fun trustHost(hostPort: String, fingerprint: String) = update { knownHosts = knownHosts + (hostPort to fingerprint) }

    @Synchronized
    private fun update(change: () -> Unit) {
        change()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(toJson().toString())
        tmp.renameTo(file)
    }

    private fun load(o: JSONObject) {
        val arr = o.optJSONArray("servers") ?: JSONArray()
        servers = (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            Server(
                id = s.getString("id"),
                name = s.optString("name"),
                host = s.optString("host"),
                port = s.optInt("port", 22),
                user = s.optString("user"),
                password = s.optString("password"),
                key = s.optString("key"),
                tags = s.optJSONArray("tags")?.let { t -> (0 until t.length()).map(t::getString) }.orEmpty(),
                startup = s.optString("startup"),
            )
        }
        val sn = o.optJSONArray("snippets") ?: JSONArray()
        snippets = (0 until sn.length()).map { i ->
            val s = sn.getJSONObject(i)
            Snippet(s.getString("id"), s.optString("name"), s.optString("command"), s.optString("server"))
        }
        val hosts = o.optJSONObject("knownHosts") ?: JSONObject()
        knownHosts = hosts.keys().asSequence().associateWith(hosts::getString)
        lock = o.optBoolean("lock")
        fontSize = o.optDouble("font", 0.0).toFloat()
    }

    private fun toJson() = JSONObject()
        .put("version", 1)
        .put("servers", JSONArray(servers.map { s ->
            JSONObject()
                .put("id", s.id).put("name", s.name).put("host", s.host).put("port", s.port).put("user", s.user)
                .put("password", s.password).put("key", s.key).put("tags", JSONArray(s.tags)).put("startup", s.startup)
        }))
        .put("snippets", JSONArray(snippets.map { s ->
            JSONObject().put("id", s.id).put("name", s.name).put("command", s.command).put("server", s.serverId)
        }))
        .put("knownHosts", JSONObject(knownHosts))
        .put("lock", lock)
        .put("font", fontSize.toDouble())
}
