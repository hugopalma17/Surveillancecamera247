package com.hpalma.Surveillance247

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraService : LifecycleService() {

    private val CHANNEL_ID = "CameraServiceChannel"
    private val TAG = "CameraService"
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var cameraExecutor: ExecutorService
    private var streamingServer: StreamingServer? = null
    private var imageCapture: ImageCapture? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "CameraService created")
        acquireWakeLock()
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Start HTTP streaming server on port 8080
        startStreamingServer()
        startCamera()
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

    private fun startStreamingServer() {
        try {
            streamingServer = StreamingServer(8080)
            streamingServer?.start()
            Log.d(TAG, "HTTP streaming server started on port 8080")
            Log.d(TAG, "Access stream via ADB port forwarding:")
            Log.d(TAG, "Run: adb forward tcp:8080 tcp:8080")
            Log.d(TAG, "Then browse to: http://localhost:8080")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start streaming server", e)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview use case
            val preview = Preview.Builder().build()

            // Image capture use case
            imageCapture = ImageCapture.Builder().build()

            // Image analysis use case for streaming
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageFrame(imageProxy)
                imageProxy.close()
            }

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )
                Log.d(TAG, "Camera bound successfully")
            } catch (exc: Exception) {
                Log.e(TAG, "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageFrame(imageProxy: ImageProxy) {
        // Convert image to JPEG bytes for streaming
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // Update streaming server with new frame
        streamingServer?.updateFrame(bytes)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(1, notification)

        Log.d(TAG, "Camera service started - streaming on http://localhost:8080")

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CameraService destroyed")
        streamingServer?.stop()
        wakeLock?.release()
        cameraExecutor.shutdown()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Surveillance Camera Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Surveillance Camera")
            .setContentText("Streaming on http://localhost:8080")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
