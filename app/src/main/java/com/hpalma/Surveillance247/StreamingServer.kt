package com.hpalma.Surveillance247

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.IOException

class StreamingServer(port: Int) : NanoHTTPD(port) {
    private val TAG = "StreamingServer"
    private var currentFrame: ByteArray? = null

    fun updateFrame(frameData: ByteArray) {
        currentFrame = frameData
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Log.d(TAG, "Request for: $uri")

        return when (uri) {
            "/stream" -> {
                // Serve MJPEG stream
                val mimeType = "multipart/x-mixed-replace; boundary=--myboundary"
                return newChunkedResponse(Response.Status.OK, mimeType, ByteArrayInputStream(byteArrayOf()))
            }
            "/snapshot" -> {
                // Serve single frame
                currentFrame?.let { frame ->
                    newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(frame), frame.size.toLong())
                } ?: newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No frame available")
            }
            "/" -> {
                // Serve simple HTML page
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Surveillance Camera</title></head>
                    <body>
                        <h1>Surveillance Camera Stream</h1>
                        <img src="/snapshot" alt="Camera Feed" id="stream" />
                        <script>
                            // Auto refresh every 100ms
                            setInterval(() => {
                                document.getElementById('stream').src = '/snapshot?' + Date.now();
                            }, 100);
                        </script>
                    </body>
                    </html>
                """.trimIndent()
                newFixedLengthResponse(Response.Status.OK, "text/html", html)
            }
            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
            }
        }
    }
}
