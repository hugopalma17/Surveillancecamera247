package com.hpalma.Surveillance247

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import com.pedro.rtspserver.RtspServerCameraX
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraService : LifecycleService(), ConnectCheckerRtsp {

    private val CHANNEL_ID = "CameraServiceChannel"
    private val TAG = "CameraService"
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var cameraExecutor: ExecutorService
    private var rtspServer: RtspServerCameraX? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "CameraService created")
        acquireWakeLock()
        cameraExecutor = Executors.newSingleThreadExecutor()
        rtspServer = RtspServerCameraX(this, true, this, 1935)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CameraService::lock").apply {
                    acquire()
                }
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val notification = createNotification()
        startForeground(1, notification)

        Log.d(TAG, "CameraService started")
        startCamera()

        return START_STICKY
    }

    private fun startCamera() {
        if (rtspServer?.isStreaming == false) {
            rtspServer?.startStream()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        cameraExecutor.shutdown()
        if (rtspServer?.isStreaming == true) {
            rtspServer?.stopStream()
        }
        rtspServer?.stopServer()
    }

    override fun onNewBitrateRtsp(bitrate: Long) {
    }

    override fun onConnectionSuccessRtsp() {
    }

    override fun onConnectionFailedRtsp(reason: String) {
    }

    override fun onConnectionStartedRtsp(rtspUrl: String) {
    }

    override fun onDisconnectRtsp() {
    }

    override fun onAuthErrorRtsp() {
    }

    override fun onAuthSuccessRtsp() {
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Camera Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camera Service")
            .setContentText("Recording in background")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
