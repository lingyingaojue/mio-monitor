package com.a41probe.monitor.data.toolbox

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.a41probe.monitor.MainActivity
import com.a41probe.monitor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 功能守护前台服务（v3.5，默认关闭）：
 * 每 5s 检查一次"应生效"的调校项，发现被系统重置（OriginOS 会定期清理）就重写。
 * 只在用户开启守护且至少一项功能启用时运行；常驻通知可见、可点击回 App。
 */
class ToolboxGuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    @Volatile private var active = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ToolboxConfig.ensureInit(this)
        createChannel()
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!active) startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        active = true
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive) {
                runCatching { ToolboxController.applyEnabledFeatures(this@ToolboxGuardService, notify = false) }
                delay(INTERVAL_MS)
            }
        }
        updateNotification()
    }

    override fun onDestroy() {
        active = false
        loopJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Mio 澪 · 功能守护",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "OriginOS 调校项被系统重置时自动重写（每 5 秒检查）"; setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun startAsForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        runCatching { nm.notify(NOTIF_ID, buildNotification()) }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_agent)
        .setContentTitle("Mio 澪 · 功能守护中")
        .setContentText("OriginOS 调校项被系统重置时会自动重写")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .addAction(
            0,
            "停止守护",
            PendingIntent.getService(
                this,
                1,
                Intent(this, ToolboxGuardService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    companion object {
        private const val CHANNEL_ID = "mio_toolbox_guard"
        private const val NOTIF_ID = 4201
        private const val INTERVAL_MS = 5_000L
        private const val ACTION_STOP = "com.a41probe.monitor.toolbox.STOP_GUARD"

        fun start(ctx: Context) {
            runCatching {
                ctx.startForegroundService(Intent(ctx, ToolboxGuardService::class.java))
            }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, ToolboxGuardService::class.java)) }
        }
    }
}
