package com.hpalma.Surveillance247

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.hpalma.Surveillance247.ui.theme.SurveillanceCameraTheme
import java.util.concurrent.Executors
import android.widget.VideoView
import android.net.Uri

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                requestBatteryOptimization()
            } else {
                // Handle the case where the user denies permissions
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestPermissions()

        setContent {
            SurveillanceCameraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CameraPreview(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun requestPermissions() {
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

    override fun onResume() {
        super.onResume()
        // Check if returning from battery optimization settings
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            startCameraService()
        }
    }

    private fun startCameraService() {
        val intent = Intent(this, CameraService::class.java)
        startService(intent)
    }
}

@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val videoView = remember { VideoView(context) }
    val rtspUrl = "rtsp://127.0.0.1:1935/"

    LaunchedEffect(Unit) {
        videoView.setVideoURI(Uri.parse(rtspUrl))
        videoView.start()
    }

    AndroidView({ videoView }, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun CameraPreviewPreview() {
    SurveillanceCameraTheme {
        CameraPreview()
    }
}