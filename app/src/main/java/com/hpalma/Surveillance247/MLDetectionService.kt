package com.hpalma.Surveillance247

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class MLDetectionService {
    private val TAG = "MLDetectionService"

    // ML Kit detectors
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
    )

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    )

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Detection thresholds and state - OPTIMIZED FOR PERFORMANCE
    private var lastDetectionTime = 0L
    private val detectionCooldown = 2000L // Increased to 2 seconds between detections
    private val motionThreshold = 0.15f // Increased threshold to reduce false positives
    private var frameSkipCounter = 0
    private val frameSkipInterval = 3 // Process every 3rd frame only

    // Processing state
    private var isProcessing = false
    private var frameCount = 0L

    // Callbacks
    interface DetectionCallback {
        fun onObjectDetected(objects: List<String>, confidence: Float)
        fun onFaceDetected(faceCount: Int, faceBitmap: Bitmap?)
        fun onTextDetected(text: String, location: Rect)
        fun onMotionDetected(motionLevel: Float)
        fun onRecordingTriggered(reason: String)
        fun onMLStatus(status: String, processingTime: Long) // NEW: Status callback
    }

    private var callback: DetectionCallback? = null
    private var previousFrame: Bitmap? = null

    fun setDetectionCallback(callback: DetectionCallback) {
        this.callback = callback
    }

    fun processFrame(imageProxy: ImageProxy) {
        val startTime = System.currentTimeMillis()
        frameCount++

        // Skip frames for performance
        if (frameSkipCounter++ < frameSkipInterval) {
            callback?.onMLStatus("Skipping frame ${frameCount} (performance)", 0)
            imageProxy.close()
            return
        }
        frameSkipCounter = 0

        // Throttle detection to avoid overloading
        if (startTime - lastDetectionTime < detectionCooldown) {
            callback?.onMLStatus("Cooling down... (${detectionCooldown - (startTime - lastDetectionTime)}ms)", 0)
            imageProxy.close()
            return
        }

        // Prevent concurrent processing
        if (isProcessing) {
            callback?.onMLStatus("Previous frame still processing, skipping", 0)
            imageProxy.close()
            return
        }

        isProcessing = true
        callback?.onMLStatus("Processing frame ${frameCount}...", 0)

        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            val inputImage = InputImage.fromBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)

            // Step 1: Check for motion first (lightweight)
            val motionStart = System.currentTimeMillis()
            val motionLevel = detectMotion(bitmap)
            callback?.onMotionDetected(motionLevel)
            val motionTime = System.currentTimeMillis() - motionStart

            // Step 2: Only do ML analysis if motion detected
            if (motionLevel > motionThreshold) {
                callback?.onMLStatus("Motion detected ($motionLevel), starting ML analysis", motionTime)

                // Object detection (primary trigger)
                objectDetector.process(inputImage)
                    .addOnSuccessListener { detectedObjects ->
                        val processingTime = System.currentTimeMillis() - startTime

                        if (detectedObjects.isNotEmpty()) {
                            val objects = detectedObjects.map {
                                it.labels.firstOrNull()?.text ?: "Unknown Object"
                            }
                            val confidence = detectedObjects.maxOfOrNull {
                                it.labels.firstOrNull()?.confidence ?: 0f
                            } ?: 0f

                            callback?.onObjectDetected(objects, confidence)
                            callback?.onMLStatus("Objects: ${objects.joinToString(", ")} (${String.format("%.2f", confidence)})", processingTime)

                            // Trigger recording if object confidence is high enough
                            if (confidence > 0.7f) {
                                callback?.onRecordingTriggered("Motion + Object Detection (${objects.joinToString(", ")})")

                                // Also check for faces when recording is triggered
                                detectFaces(inputImage, bitmap)

                                // Check for text (license plates, signs) - less frequently
                                if (frameCount % 10 == 0L) { // Only every 10th processed frame
                                    detectText(inputImage)
                                }
                            }
                        } else {
                            callback?.onMLStatus("No objects detected", processingTime)
                        }

                        isProcessing = false
                        lastDetectionTime = startTime
                    }
                    .addOnFailureListener { e ->
                        val processingTime = System.currentTimeMillis() - startTime
                        callback?.onMLStatus("Object detection failed: ${e.message}", processingTime)
                        isProcessing = false
                    }

            } else {
                val processingTime = System.currentTimeMillis() - startTime
                callback?.onMLStatus("No motion detected ($motionLevel)", processingTime)
                isProcessing = false
            }

            // Store current frame for next motion comparison (downsized for memory)
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, false)
            previousFrame = scaledBitmap

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            callback?.onMLStatus("Frame processing failed: ${e.message}", processingTime)
            isProcessing = false
        } finally {
            imageProxy.close()
        }
    }

    private fun detectMotion(currentFrame: Bitmap): Float {
        val previousBitmap = previousFrame ?: return 0f

        if (currentFrame.width != previousBitmap.width || currentFrame.height != previousBitmap.height) {
            return 0f
        }

        // Simple motion detection by comparing pixel differences
        val width = currentFrame.width
        val height = currentFrame.height
        val sampleStep = 10 // Sample every 10th pixel for performance

        var totalDifference = 0L
        var pixelCount = 0

        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                val currentPixel = currentFrame.getPixel(x, y)
                val previousPixel = previousBitmap.getPixel(x, y)

                val rDiff = Math.abs((currentPixel shr 16) and 0xFF - (previousPixel shr 16) and 0xFF)
                val gDiff = Math.abs((currentPixel shr 8) and 0xFF - (previousPixel shr 8) and 0xFF)
                val bDiff = Math.abs(currentPixel and 0xFF - previousPixel and 0xFF)

                totalDifference += rDiff + gDiff + bDiff
                pixelCount++
            }
        }

        return if (pixelCount > 0) {
            (totalDifference.toFloat() / (pixelCount * 255 * 3)) // Normalize to 0-1
        } else 0f
    }

    private fun detectFaces(inputImage: InputImage, sourceBitmap: Bitmap) {
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    Log.d(TAG, "Detected ${faces.size} face(s)")

                    // Extract first face as bitmap for saving
                    val firstFace = faces.first()
                    val faceBounds = firstFace.boundingBox

                    try {
                        val faceBitmap = Bitmap.createBitmap(
                            sourceBitmap,
                            faceBounds.left.coerceAtLeast(0),
                            faceBounds.top.coerceAtLeast(0),
                            (faceBounds.width()).coerceAtMost(sourceBitmap.width - faceBounds.left.coerceAtLeast(0)),
                            (faceBounds.height()).coerceAtMost(sourceBitmap.height - faceBounds.top.coerceAtLeast(0))
                        )
                        callback?.onFaceDetected(faces.size, faceBitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "Face bitmap extraction failed", e)
                        callback?.onFaceDetected(faces.size, null)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Face detection failed", e)
            }
    }

    private fun detectText(inputImage: InputImage) {
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                if (visionText.text.isNotBlank()) {
                    Log.d(TAG, "Detected text: ${visionText.text}")

                    // Look for license plate patterns or significant text
                    for (block in visionText.textBlocks) {
                        val text = block.text.trim()
                        if (isLicensePlatePattern(text) || text.length >= 3) {
                            callback?.onTextDetected(text, block.boundingBox ?: Rect())
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Text recognition failed", e)
            }
    }

    private fun isLicensePlatePattern(text: String): Boolean {
        // Simple license plate pattern detection
        val cleanText = text.replace(Regex("[^A-Z0-9]"), "")
        return cleanText.length in 4..10 &&
               cleanText.any { it.isDigit() } &&
               cleanText.any { it.isLetter() }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer: ByteBuffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun cleanup() {
        try {
            objectDetector.close()
            faceDetector.close()
            textRecognizer.close()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }
}
