package app.termaff.ssh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import app.termaff.R
import app.termaff.tr

/**
 * Foreground service: пока есть живые сессии, система не убивает процесс в фоне и не режет ему сеть.
 * Сам ничего не делает — соединения живут в [Sessions]; сервис лишь держит уведомление.
 */
class SessionService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            Sessions.closeAll()
            return START_NOT_STICKY
        }
        val n = notification(this, titles)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, n)
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val ID = 1
        private const val CHANNEL = "sessions"
        private const val STOP = "stop"
        /** Сервис запрошен (флаг ставим сразу, не дожидаясь onStartCommand/onDestroy). */
        @Volatile private var running = false
        @Volatile private var titles = emptyList<String>()

        /** Запустить/обновить/остановить сервис по списку живых сессий. */
        @Synchronized
        fun sync(context: Context, live: List<String>) {
            if (live == titles && running == live.isNotEmpty()) return
            titles = live
            val intent = Intent(context, SessionService::class.java)
            when {
                live.isEmpty() -> { running = false; context.stopService(intent) }
                // Запуск — из UI (подключение); из фона Android 12+ его запрещает, дальше только обновляем уведомление
                !running -> running = runCatching { context.startForegroundService(intent) }.isSuccess
                else -> context.getSystemService(NotificationManager::class.java).notify(ID, notification(context, live))
            }
        }

        private fun notification(context: Context, live: List<String>): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("SSH-сессии"), NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(
                context, 0, context.packageManager.getLaunchIntentForPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE,
            )
            val stop = PendingIntent.getService(
                context, 1, Intent(context, SessionService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE,
            )
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(if (live.size == 1) tr("SSH-сессия открыта") else tr("SSH-сессий открыто: %s", live.size))
                .setContentText(live.joinToString(", "))
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, tr("Отключить все"), stop).build())
                .build()
        }
    }
}
