package com.hpalma.Surveillance247

import com.pedro.rtsp.utils.ConnectCheckerRtsp
import com.pedro.rtspserver.RtspServer
import java.io.IOException

class RtspServer(
    private val connectCheckerRtsp: ConnectCheckerRtsp,
    private val port: Int
) {
    private var rtspServer: RtspServer? = null

    fun start() {
        rtspServer = RtspServer(connectCheckerRtsp, port)
        rtspServer?.startServer()
    }

    fun stop() {
        rtspServer?.stopServer()
    }

    fun getEndPointConnection(): String? {
        return rtspServer?.getEndPointConnection()
    }
}
