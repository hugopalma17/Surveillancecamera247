package com.hpalma.Surveillance247

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraService : LifecycleService(), MLDetectionService.DetectionCallback {

    private val CHANNEL_ID = "CameraServiceChannel"
    private val TAG = "CameraService"
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    // ML Detection Service
    private lateinit var mlDetectionService: MLDetectionService

    // Recording state
    private var isRecording = false
    private var recordingReason = ""

    // Storage directories
    private lateinit var videoDir: File
    private lateinit var faceDir: File

    // Service binder for MainActivity communication
    private var mainActivityCallback: MLDetectionService.DetectionCallback? = null

    // Camera preview sharing
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var previewView: PreviewView? = null

    inner class CameraBinder : Binder() {
        fun getService(): CameraService = this@CameraService
        fun setMainActivityCallback(callback: MLDetectionService.DetectionCallback) {
            mainActivityCallback = callback
        }

        // Share preview surface with UI
        fun connectPreviewView(previewView: PreviewView) {
            this@CameraService.previewView = previewView
            preview?.setSurfaceProvider(previewView.surfaceProvider)
            Log.d(TAG, "Preview connected to UI")
        }

        fun disconnectPreviewView() {
            preview?.setSurfaceProvider(null)
            this@CameraService.previewView = null
            Log.d(TAG, "Preview disconnected from UI")
        }
    }

    private val binder = CameraBinder()

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "CameraService created with ML detection")

        // Initialize ML detection service
        mlDetectionService = MLDetectionService()
        mlDetectionService.setDetectionCallback(this)

        // Create storage directories
        createStorageDirectories()

        acquireWakeLock()
        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
    }

    private fun createStorageDirectories() {
        val appDir = File(getExternalFilesDir(null), "SurveillanceCamera")
        videoDir = File(appDir, "videos")
        faceDir = File(appDir, "faces")

        videoDir.mkdirs()
        faceDir.mkdirs()

        Log.d(TAG, "Storage directories created: ${appDir.absolutePath}")
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

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview use case
            preview = Preview.Builder().build()

            // Image capture use case
            imageCapture = ImageCapture.Builder().build()

            // Image analysis use case for ML detection
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                // Send frame to ML detection service
                mlDetectionService.processFrame(imageProxy)
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
                this.cameraProvider = cameraProvider // Save for preview sharing
                Log.d(TAG, "Camera bound successfully with ML analysis")
            } catch (exc: Exception) {
                Log.e(TAG, "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ML Detection Callbacks - Forward to MainActivity
    override fun onObjectDetected(objects: List<String>, confidence: Float) {
        Log.d(TAG, "Objects detected: ${objects.joinToString(", ")} (confidence: $confidence)")
        updateNotification("Objects detected: ${objects.joinToString(", ")}")

        // Forward to MainActivity
        mainActivityCallback?.onObjectDetected(objects, confidence)
    }

    override fun onFaceDetected(faceCount: Int, faceBitmap: Bitmap?) {
        Log.d(TAG, "Face(s) detected: $faceCount")

        // Save face image with timestamp
        faceBitmap?.let { bitmap ->
            saveFaceImage(bitmap)
        }

        // Forward to MainActivity
        mainActivityCallback?.onFaceDetected(faceCount, faceBitmap)
    }

    override fun onTextDetected(text: String, location: Rect) {
        Log.d(TAG, "Text detected: $text at location: $location")

        // Forward to MainActivity
        mainActivityCallback?.onTextDetected(text, location)
    }

    override fun onMotionDetected(motionLevel: Float) {
        // Forward motion data to MainActivity UI
        mainActivityCallback?.onMotionDetected(motionLevel)

        // Only log significant motion to avoid spam
        if (motionLevel > 0.1f) {
            Log.d(TAG, "Motion detected: $motionLevel")
        }
    }

    override fun onRecordingTriggered(reason: String) {
        isRecording = true
        recordingReason = reason
        Log.d(TAG, "Recording triggered: $reason")
        updateNotification("Recording: $reason")

        // Forward to MainActivity
        mainActivityCallback?.onRecordingTriggered(reason)

        // TODO: Implement actual video recording in Phase 3
        // For now, simulate recording duration
        Timer().schedule(object : TimerTask() {
            override fun run() {
                isRecording = false
                updateNotification("Monitoring")
            }
        }, 30000)
    }

    private fun startRecording() {
        if (isRecording) return

        isRecording = true
        recordingReason = "Motion + Object Detection"
        updateNotification("🔴 RECORDING: $recordingReason")

        // TODO: Implement actual video recording in Phase 3
        Log.i(TAG, "Video recording started (simulation)")

        // Simulate 30-second recording
        Timer().schedule(object : TimerTask() {
            override fun run() {
                stopRecording()
            }
        }, 30000)
    }

    private fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        Log.i(TAG, "Recording stopped")
        updateNotification("Surveillance active - monitoring...")
    }

    private fun saveFaceImage(faceBitmap: Bitmap) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "face_$timestamp.jpg"
            val file = File(faceDir, filename)

            FileOutputStream(file).use { out ->
                faceBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            Log.i(TAG, "Face saved: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save face image", e)
        }
    }

    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val notification = createNotification("Surveillance active - monitoring...")
        startForeground(1, notification)

        Log.d(TAG, "Camera service started with ML detection")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CameraService destroyed")

        // Cleanup ML detection service
        mlDetectionService.cleanup()

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

    private fun createNotification(message: String = "Camera service running"): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Surveillance Camera")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .build()
    }

    // ML Status callback
    override fun onMLStatus(status: String, processingTime: Long) {
        Log.d(TAG, "ML Status: $status (${processingTime}ms)")

        // Forward to MainActivity
        mainActivityCallback?.onMLStatus(status, processingTime)
    }
}
