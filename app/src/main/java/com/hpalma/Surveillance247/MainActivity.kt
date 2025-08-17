package com.hpalma.Surveillance247

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.hpalma.Surveillance247.ui.theme.SurveillanceCameraTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

data class StatusMessage(
    val timestamp: String,
    val type: String, // "ML", "MOTION", "OBJECT", "FACE", "RECORDING", "ERROR"
    val message: String,
    val processingTime: Long = 0
)

class MainActivity : ComponentActivity(), MLDetectionService.DetectionCallback {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                requestBatteryOptimization()
            } else {
                // Handle permission denied
            }
        }

    // Status tracking
    private val statusMessages = mutableStateListOf<StatusMessage>()
    private var isServiceRunning by mutableStateOf(false)
    private var currentMotionLevel by mutableStateOf(0f)
    private var detectedObjects by mutableStateOf(listOf<String>())
    private var isRecording by mutableStateOf(false)
    private var faceCount by mutableStateOf(0)

    // ML Service connection - Initialize only after permissions
    private var mlService: MLDetectionService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        addStatusMessage("SYSTEM", "MainActivity created - checking permissions")

        setContent {
            SurveillanceCameraTheme {
                SplitScreenSurveillanceUI()
            }
        }

        checkPermissions()
    }

    private fun initializeMLService() {
        try {
            addStatusMessage("SYSTEM", "Initializing ML detection service")
            mlService = MLDetectionService()
            mlService?.setDetectionCallback(this)
            addStatusMessage("SYSTEM", "ML service initialized successfully")
        } catch (e: Exception) {
            addStatusMessage("ERROR", "Failed to initialize ML service: ${e.message}")
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SplitScreenSurveillanceUI() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Surveillance Camera - Live Monitoring") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                    )
                )
            }
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Left half - Camera preview
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.Black)
                ) {
                    CameraPreview()

                    // Overlay status indicators
                    StatusOverlay()
                }

                // Right half - Status panel
                StatusPanel(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }

    @Composable
    fun CameraPreview() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        // Check if camera permission is granted before attempting to initialize
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            // Show placeholder when no camera permission
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Camera Permission Required",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
            return
        }

        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)

                try {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        try {
                          val cameraProvider = cameraProviderFuture.get()
                          val preview = CameraXPreview.Builder().build()
                          val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                          preview.setSurfaceProvider(previewView.surfaceProvider)

                          cameraProvider.unbindAll()
                          cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)

                          addStatusMessage("CAMERA", "Camera preview initialized successfully")
                        } catch (exc: Exception) {
                            addStatusMessage("ERROR", "Camera binding failed: ${exc.message}")
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                } catch (exc: Exception) {
                    addStatusMessage("ERROR", "Camera provider failed: ${exc.message}")
                }

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    @Composable
    fun StatusOverlay() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top overlay - Recording indicator
            if (isRecording) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🔴 RECORDING",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom overlay - Current detection status
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Motion: ${String.format("%.3f", currentMotionLevel)}",
                        color = if (currentMotionLevel > 0.15f) Color.Green else Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    if (detectedObjects.isNotEmpty()) {
                        Text(
                            text = "Objects: ${detectedObjects.joinToString(", ")}",
                            color = Color.Yellow,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                    if (faceCount > 0) {
                        Text(
                            text = "Faces: $faceCount",
                            color = Color.Cyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun StatusPanel(modifier: Modifier = Modifier) {
        val listState = rememberLazyListState()

        // Auto-scroll to latest messages
        LaunchedEffect(statusMessages.size) {
            if (statusMessages.isNotEmpty()) {
                listState.animateScrollToItem(statusMessages.size - 1)
            }
        }

        Column(
            modifier = modifier
                .background(Color(0xFF1E1E1E))
                .padding(8.dp)
        ) {
            // Header
            Text(
                text = "ML Detection Status - Live Log",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Service status
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceRunning) Color.Green else Color.Red
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = if (isServiceRunning) "SERVICE ACTIVE" else "SERVICE STOPPED",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Status messages log
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(statusMessages) { message ->
                    StatusMessageItem(message)
                }
            }
        }
    }

    @Composable
    fun StatusMessageItem(message: StatusMessage) {
        val backgroundColor = when (message.type) {
            "ML" -> Color(0xFF2E2E2E)
            "MOTION" -> Color(0xFF004D00)
            "OBJECT" -> Color(0xFF4D4D00)
            "FACE" -> Color(0xFF004D4D)
            "RECORDING" -> Color(0xFF4D0000)
            "ERROR" -> Color(0xFF800000)
            else -> Color(0xFF1A1A1A)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = message.timestamp,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = message.type,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = message.message,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (message.processingTime > 0) {
                    Text(
                        text = "${message.processingTime}ms",
                        color = Color.Yellow,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    // ML Detection Callbacks
    override fun onObjectDetected(objects: List<String>, confidence: Float) {
        detectedObjects = objects
        addStatusMessage("OBJECT", "Objects: ${objects.joinToString(", ")} (${String.format("%.2f", confidence)})")
    }

    override fun onFaceDetected(faceCount: Int, faceBitmap: Bitmap?) {
        this.faceCount = faceCount
        val bitmapInfo = if (faceBitmap != null) " - Image saved" else " - No image"
        addStatusMessage("FACE", "Face(s) detected: $faceCount$bitmapInfo")
    }

    override fun onTextDetected(text: String, location: Rect) {
        addStatusMessage("TEXT", "Text: '$text' at $location")
    }

    override fun onMotionDetected(motionLevel: Float) {
        currentMotionLevel = motionLevel
        // Only log significant motion to avoid spam
        if (motionLevel > 0.1f) {
            addStatusMessage("MOTION", "Motion level: ${String.format("%.3f", motionLevel)}")
        }
    }

    override fun onRecordingTriggered(reason: String) {
        isRecording = true
        addStatusMessage("RECORDING", "Recording started: $reason")

        // Simulate recording duration
        Timer().schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    isRecording = false
                    addStatusMessage("RECORDING", "Recording stopped")
                }
            }
        }, 30000) // 30 seconds
    }

    override fun onMLStatus(status: String, processingTime: Long) {
        addStatusMessage("ML", status, processingTime)
    }

    private fun addStatusMessage(type: String, message: String, processingTime: Long = 0) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        statusMessages.add(StatusMessage(timestamp, type, message, processingTime))

        // Keep only last 100 messages to prevent memory issues
        if (statusMessages.size > 100) {
            statusMessages.removeAt(0)
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsNotGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsNotGranted.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNotGranted)
        } else {
            requestBatteryOptimization()
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                startCameraService()
            }
        } else {
            startCameraService()
        }
    }

    private fun startCameraService() {
        isServiceRunning = true
        addStatusMessage("SERVICE", "Starting camera service with ML detection")
        val intent = Intent(this, CameraService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        mlService?.cleanup()
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SurveillanceCameraTheme {
        Text("Surveillance Camera App")
    }
}
