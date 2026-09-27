package app.termaff.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.termaff.ssh.SshSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Скрипт метрик: только чтение /proc и стандартные утилиты, без агентов на сервере.
 * Каждая строка с ключом в начале — отсутствующая утилита не сдвигает остальные.
 */
private const val SCRIPT = """exec 2>/dev/null
head -1 /proc/stat
grep -E '^Mem(Total|Available):' /proc/meminfo
echo "disk $(df -Pk / | tail -1)"
echo "up $(cat /proc/uptime)"
echo "load $(cat /proc/loadavg)"
echo "cpus $(nproc)"
echo "kernel $(uname -srm)"
echo "host $(cat /proc/sys/kernel/hostname)"
echo "os $(. /etc/os-release; echo "${'$'}PRETTY_NAME")"
"""
private const val PERIOD = 3000L
private const val POINTS = 40

private class Stats(val lines: Map<String, String>) {
    private fun num(key: String, i: Int) = lines[key]?.split(Regex("\\s+"))?.getOrNull(i)?.toDoubleOrNull()
    val memTotal = (num("MemTotal:", 0) ?: 0.0) * 1024
    val memUsed = memTotal - (num("MemAvailable:", 0) ?: 0.0) * 1024
    // df -Pk: устройство, всего, занято, свободно (КБ); процент как у df — от занятого + свободного
    val diskUsed = (num("disk", 2) ?: 0.0) * 1024
    val diskTotal = diskUsed + (num("disk", 3) ?: 0.0) * 1024
    val uptime = num("up", 0)?.toLong()
    val load = lines["load"]?.split(' ')?.take(3)?.joinToString(" ")
    /** Счётчики CPU: всего и простоя (idle + iowait) — загрузка считается по разнице между опросами. */
    val cpu = lines["cpu"]?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toLongOrNull() }?.take(8)
        ?.let { longArrayOf(it.sum(), it.getOrElse(3) { 0 } + it.getOrElse(4) { 0 }) }
}

/** Обзор сервера: CPU, память, диск, аптайм, система. Обновляется раз в 3 с, пока экран открыт. */
@Composable
fun OverviewScreen(session: SshSession, onBack: () -> Unit, onTerminal: () -> Unit, onFiles: () -> Unit) {
    var stats by remember { mutableStateOf<Stats?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val cpu = remember { mutableStateListOf<Float>() }
    val conn = session.connection

    BackHandler(onBack = onBack)
    LaunchedEffect(conn) {
        if (conn == null) return@LaunchedEffect
        var prev: LongArray? = null
        while (true) {
            try {
                val s = Stats(withContext(Dispatchers.IO) { session.exec(SCRIPT) }.lines().filter { ' ' in it }
                    .associate { it.substringBefore(' ') to it.substringAfter(' ').trim() })
                val c = s.cpu
                if (c != null && prev != null && c[0] > prev[0]) {
                    cpu += 1 - (c[1] - prev[1]).toFloat() / (c[0] - prev[0])
                    if (cpu.size > POINTS) cpu.removeAt(0)
                }
                prev = c
                stats = s
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            delay(PERIOD)
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
        SessionHeader(session, onBack)
        if (conn == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { SessionStatus(session) }
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { Text(it, color = Danger) }
            val s = stats
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("CPU", cpu.lastOrNull()?.let { "${(it * 100).toInt()}%" } ?: "…",
                    s?.lines?.get("cpus")?.let { "ядер: $it" }) {
                    Sparkline(cpu)
                }
                Metric("RAM", s?.let { "${percent(it.memUsed, it.memTotal)}%" } ?: "…",
                    s?.let { "${humanSize(it.memUsed.toLong())} / ${humanSize(it.memTotal.toLong())}" }) {
                    s?.let { Bar(it.memUsed, it.memTotal) }
                }
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Диск /", s?.let { "${percent(it.diskUsed, it.diskTotal)}%" } ?: "…",
                    s?.let { "${humanSize(it.diskUsed.toLong())} / ${humanSize(it.diskTotal.toLong())}" }) {
                    s?.let { Bar(it.diskUsed, it.diskTotal) }
                }
                Metric("Аптайм", s?.uptime?.let(::duration) ?: "…", s?.load?.let { "нагрузка $it" })
            }

            Text("Быстрые действия", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Action(Icons.Filled.Terminal, "Терминал", "SSH", onTerminal)
                Action(Icons.Filled.Folder, "Файлы", "SFTP", onFiles)
            }

            if (s != null) Column(
                Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Система", style = MaterialTheme.typography.titleMedium)
                listOfNotNull(s.lines["os"], s.lines["kernel"], s.lines["host"]).filter { it.isNotBlank() }
                    .forEach { Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun RowScope.Metric(title: String, value: String, sub: String?, extra: @Composable () -> Unit = {}) = Column(
    Modifier.weight(1f).fillMaxHeight().background(Card, RoundedCornerShape(16.dp)).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
) {
    Text(title, color = Muted, style = MaterialTheme.typography.labelLarge)
    Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    Text(sub ?: "", color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    extra()
}

@Composable
private fun Bar(used: Double, total: Double) {
    val f = if (total > 0) (used / total).toFloat() else 0f
    LinearProgressIndicator(
        progress = { f }, Modifier.fillMaxWidth().padding(top = 6.dp),
        color = if (f > 0.9f) Danger else Accent, trackColor = Bg, drawStopIndicator = {},
    )
}

/** Загрузка CPU за последние опросы: 0 — низ, 100% — верх. */
@Composable
private fun Sparkline(values: List<Float>) = Canvas(Modifier.fillMaxWidth().height(28.dp)) {
    if (values.size < 2) return@Canvas
    val step = size.width / (POINTS - 1)
    val x0 = size.width - step * (values.size - 1)
    val path = Path()
    values.forEachIndexed { i, v ->
        val p = Offset(x0 + step * i, size.height * (1 - v.coerceIn(0f, 1f)))
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    drawPath(path, Accent, style = Stroke(2.dp.toPx()))
}

@Composable
private fun RowScope.Action(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) = Column(
    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Card).clickable(onClick = onClick).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Icon(icon, null, tint = Accent)
    Text(title, style = MaterialTheme.typography.titleSmall)
    Text(sub, color = Muted, style = MaterialTheme.typography.bodySmall)
}

private fun percent(used: Double, total: Double) = if (total > 0) (used * 100 / total).toInt() else 0

private fun duration(s: Long): String {
    val d = s / 86400
    val h = s % 86400 / 3600
    val m = s % 3600 / 60
    return when {
        d > 0 -> "$d д $h ч"
        h > 0 -> "$h ч $m мин"
        else -> "$m мин"
    }
}
