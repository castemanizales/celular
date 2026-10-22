package com.visum.autocall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class AutoCallService : Service() {

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var httpServer: LocalHttpServer? = null
    private lateinit var callEngine: CallEngine

    private var onLogCallback: ((String) -> Unit)? = null
    private var onStatusChanged: ((Boolean) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): AutoCallService = this@AutoCallService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        callEngine = CallEngine(applicationContext) { log ->
            postLog(log)
        }

        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Iniciando servicio de llamadas...")
        startForeground(NOTIFICATION_ID, notification)

        startServer()

        return START_STICKY
    }

    fun setCallbacks(onLog: (String) -> Unit, onStatus: (Boolean) -> Unit) {
        this.onLogCallback = onLog
        this.onStatusChanged = onStatus
    }

    private fun postLog(msg: String) {
        onLogCallback?.invoke(msg)
    }

    private fun startServer() {
        if (httpServer != null) return

        val ip = NetworkUtils.getLocalIpAddress(this)
        val port = 8080

        httpServer = LocalHttpServer(
            port = port,
            onLog = { log -> postLog(log) },
            onCallTriggered = { requestedNumbers ->
                triggerCalls(requestedNumbers)
            },
            onStopTriggered = {
                callEngine.cancelSequence()
            },
            getStatus = {
                mapOf(
                    "service" to "AutoCallServer",
                    "status" to "online",
                    "ip" to ip,
                    "port" to port,
                    "isCalling" to callEngine.isRunning()
                )
            }
        )

        httpServer?.start()
        updateNotification("Escuchando en http://$ip:$port")
        onStatusChanged?.invoke(true)
    }

    fun triggerCalls(customNumbers: List<String> = emptyList()) {
        val prefs = getSharedPreferences("autocall_prefs", Context.MODE_PRIVATE)
        val ringSeconds = prefs.getInt("ring_duration", 22)

        val targetList = if (customNumbers.isNotEmpty()) {
            customNumbers
        } else {
            listOf(
                prefs.getString("phone_1", "") ?: "",
                prefs.getString("phone_2", "") ?: "",
                prefs.getString("phone_3", "") ?: ""
            ).filter { it.isNotBlank() }
        }

        callEngine.startCallSequence(targetList, ringSeconds)
    }

    fun stopCallSequence() {
        callEngine.cancelSequence()
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoCall Server")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Llamadas AutoCall",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente para mantener el servidor HTTP activo"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AutoCall::WakeLock"
            ).apply {
                acquire(12 * 60 * 60 * 1000L) // 12 horas max
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "AutoCall::WifiLock"
            ).apply {
                acquire()
            }
        } catch (e: Exception) {
            postLog("[!] No se pudieron adquirir todos los wake/wifi locks: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        httpServer?.stop()
        httpServer = null
        callEngine.cancelSequence()

        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            // Ignore
        }

        onStatusChanged?.invoke(false)
        postLog("[*] Servicio en segundo plano destruido.")
    }

    companion object {
        const val CHANNEL_ID = "autocall_channel"
        const val NOTIFICATION_ID = 1001
    }
}
