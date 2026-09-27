package app.termaff.data

import android.content.Context
import app.termaff.ssh.Keys
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Сервер. [password] хранится зашифрованным ([Vault]); [keyId] — ключ из [Store.keys], пусто — вход по паролю. */
data class Server(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val password: String = "",
    val keyId: String = "",
    val tags: List<String> = emptyList(),
    /** Команда, выполняемая сразу после входа. */
    val startup: String = "",
) {
    val title get() = name.ifBlank { host }
}

/** SSH-ключ. [private] и [passphrase] зашифрованы [Vault]; [public] — строка для authorized_keys. */
data class SshKey(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val private: String = "",
    val passphrase: String = "",
    val public: String = "",
) {
    val type get() = public.substringBefore(' ')
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

    var keys by mutableStateOf(emptyList<SshKey>())
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

    /** Цветовая схема терминала (id из TermThemes). */
    var theme by mutableStateOf("")
        private set

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "state.json")
        runCatching { if (load(JSONObject(file.readText()))) update {} }
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

    fun save(key: SshKey) = update {
        keys = if (keys.any { it.id == key.id }) keys.map { if (it.id == key.id) key else it } else keys + key
    }

    /** Серверы с этим ключом переходят на вход по паролю. */
    fun deleteKey(id: String) = update {
        keys = keys.filter { it.id != id }
        servers = servers.map { if (it.keyId == id) it.copy(keyId = "") else it }
    }

    fun key(id: String) = keys.firstOrNull { it.id == id }

    fun saveLock(on: Boolean) = update { lock = on }

    fun saveFontSize(sp: Float) = update { fontSize = sp }

    fun saveTheme(id: String) = update { theme = id }

    fun trustHost(hostPort: String, fingerprint: String) = update { knownHosts = knownHosts + (hostPort to fingerprint) }

    @Synchronized
    private fun update(change: () -> Unit) {
        change()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(toJson().toString())
        tmp.renameTo(file)
    }

    /** true — данные старого формата переведены в новый, файл надо перезаписать. */
    private fun load(o: JSONObject): Boolean {
        val ks = o.optJSONArray("keys") ?: JSONArray()
        keys = (0 until ks.length()).map { i ->
            val k = ks.getJSONObject(i)
            SshKey(k.getString("id"), k.optString("name"), k.optString("private"), k.optString("passphrase"), k.optString("public"))
        }
        // До 0.5.0 ключ хранился прямо в сервере, а поле пароля было паролем ключа: выносим в список ключей (одинаковые — в один)
        val migrated = HashMap<String, String>()
        val arr = o.optJSONArray("servers") ?: JSONArray()
        servers = (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            var server = Server(
                id = s.getString("id"),
                name = s.optString("name"),
                host = s.optString("host"),
                port = s.optInt("port", 22),
                user = s.optString("user"),
                password = s.optString("password"),
                keyId = s.optString("keyId"),
                tags = s.optJSONArray("tags")?.let { t -> (0 until t.length()).map(t::getString) }.orEmpty(),
                startup = s.optString("startup"),
            )
            val inline = s.optString("key")
            if (inline.isNotEmpty()) {
                val pem = Vault.decrypt(inline)
                val id = migrated.getOrPut(pem) {
                    val pass = Vault.decrypt(server.password)
                    val public = runCatching { Keys.publicKey(pem, pass, "termaff") }.getOrDefault("")
                    SshKey(name = "Ключ ${server.title}", private = inline, passphrase = server.password, public = public)
                        .also { keys = keys + it }.id
                }
                server = server.copy(keyId = id, password = "")
            }
            server
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
        theme = o.optString("theme")
        return migrated.isNotEmpty()
    }

    private fun toJson() = JSONObject()
        .put("version", 1)
        .put("servers", JSONArray(servers.map { s ->
            JSONObject()
                .put("id", s.id).put("name", s.name).put("host", s.host).put("port", s.port).put("user", s.user)
                .put("password", s.password).put("keyId", s.keyId).put("tags", JSONArray(s.tags)).put("startup", s.startup)
        }))
        .put("snippets", JSONArray(snippets.map { s ->
            JSONObject().put("id", s.id).put("name", s.name).put("command", s.command).put("server", s.serverId)
        }))
        .put("keys", JSONArray(keys.map { k ->
            JSONObject().put("id", k.id).put("name", k.name).put("private", k.private)
                .put("passphrase", k.passphrase).put("public", k.public)
        }))
        .put("knownHosts", JSONObject(knownHosts))
        .put("lock", lock)
        .put("font", fontSize.toDouble())
        .put("theme", theme)
}
