package com.hpalma.Surveillance247

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraService : Service() {

    private val CHANNEL_ID = "CameraServiceChannel"
    private val TAG = "CameraService"
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var serviceLifecycleOwner: ServiceLifecycleOwner

    override fun onCreate() {
        super.onCreate()
        serviceLifecycleOwner = ServiceLifecycleOwner()
        createNotificationChannel()
        Log.d(TAG, "CameraService created")
        acquireWakeLock()
        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        serviceLifecycleOwner.start()
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
        val notification = createNotification()
        startForeground(1, notification)

        Log.d(TAG, "CameraService started")
        startCamera()

        return START_STICKY
    }

    private fun startCamera() {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { imageProxy ->
            // This is where we will process the image frames
            Log.d(TAG, "Image frame received")
            imageProxy.close()
        })

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                serviceLifecycleOwner,
                cameraSelector,
                imageAnalysis
            )
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        cameraExecutor.shutdown()
        serviceLifecycleOwner.stop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Surveillance App")
            .setContentText("Running in background...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your app's icon
            .build()
    }
}
