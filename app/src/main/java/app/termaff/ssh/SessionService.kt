package app.termaff.ssh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import app.termaff.R
import app.termaff.tr

/**
 * Foreground service: пока есть живые сессии, система не убивает процесс в фоне и не режет ему сеть.
 * Соединения живут в [Sessions]; сервис держит уведомление и блокировки CPU/Wi-Fi, чтобы сон экрана не рвал сессии.
 */
class SessionService : Service() {
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        wake?.release()
        wifi?.release()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            Sessions.closeAll()
            return START_NOT_STICKY
        }
        val n = notification(this, states)
        if (wake == null) {
            wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "termaff:sessions").apply { acquire() }
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifi = applicationContext.getSystemService(WifiManager::class.java).createWifiLock(mode, "termaff:sessions").apply { acquire() }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, n)
        }
        // Сессии могли закрыться раньше, чем система запустила сервис (неверный порт — отказ за миллисекунды).
        // stopService до startForeground роняет приложение, поэтому такой запуск гасим здесь, уже после startForeground
        synchronized(Companion) {
            started = true
            if (!running) { started = false; stopSelf(startId) }
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val ID = 1
        private const val CHANNEL = "sessions"
        private const val STOP = "stop"
        /** Сервис запрошен (флаг ставим сразу, не дожидаясь onStartCommand/onDestroy). */
        @Volatile private var running = false
        /** Сервис уже вызвал startForeground — только теперь его можно останавливать через stopService. */
        private var started = false
        /** Живые сессии: имя и признак «связь есть». */
        @Volatile private var states = emptyList<Pair<String, Boolean>>()

        /** Запустить/обновить/остановить сервис по списку живых сессий. */
        @Synchronized
        fun sync(context: Context, live: List<Pair<String, Boolean>>) {
            if (live == states && running == live.isNotEmpty()) return
            states = live
            val intent = Intent(context, SessionService::class.java)
            when {
                live.isEmpty() -> {
                    running = false
                    if (started) { started = false; context.stopService(intent) }
                }
                // Запуск — из UI (подключение); из фона Android 12+ его запрещает, дальше только обновляем уведомление
                !running -> running = runCatching { context.startForegroundService(intent) }.isSuccess
                else -> context.getSystemService(NotificationManager::class.java).notify(ID, notification(context, live))
            }
        }

        private fun notification(context: Context, live: List<Pair<String, Boolean>>): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("SSH-сессии"), NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(
                context, 0, context.packageManager.getLaunchIntentForPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE,
            )
            val stop = PendingIntent.getService(
                context, 1, Intent(context, SessionService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE,
            )
            val up = live.count { it.second }
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(when {
                    up == 0 -> tr("Нет соединения, переподключение…")
                    live.size == 1 -> tr("SSH-сессия открыта")
                    else -> tr("SSH-сессий открыто: %s", up)
                })
                .setContentText(live.joinToString(", ") { if (it.second) it.first else "${it.first} ✕" })
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, tr("Отключить все"), stop).build())
                .build()
        }
    }
}
