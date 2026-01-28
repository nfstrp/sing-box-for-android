package io.nekohasekai.sfa.utils

import android.util.Log
import kotlinx.coroutines.*
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLContext
import kotlin.system.measureTimeMillis

object ServerLatencyChecker {
    private const val TAG = "SteroidLatency"
    private const val DEFAULT_TIMEOUT = 5000L
    private val lastCheckTime = AtomicLong(0L)
    private var cachedResult: LatencyResult? = null

    data class ServerInfo(val url: String, val hostname: String, val port: Int = 443, val name: String)
    data class LatencyResult(val server: ServerInfo, val latencyMs: Long, val isAlive: Boolean)

    private suspend fun measureTLS(server: ServerInfo): LatencyResult = withContext(Dispatchers.IO) {
        try {
            val latency = measureTimeMillis {
                Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(server.hostname, server.port), DEFAULT_TIMEOUT.toInt())
                    val sslContext = SSLContext.getInstance("TLSv1.2").apply { init(null, null, null) }
                    val sslSocket = sslContext.socketFactory.createSocket(socket, server.hostname, server.port, true) as javax.net.ssl.SSLSocket
                    sslSocket.startHandshake()
                }
            }
            LatencyResult(server, latency, true)
        } catch (e: Exception) {
            LatencyResult(server, Long.MAX_VALUE, false)
        }
    }

    suspend fun findBest(servers: List<ServerInfo>): LatencyResult? = withContext(Dispatchers.Default) {
        val results = servers.map { async { measureTLS(it) } }.awaitAll()
        val best = results.filter { it.isAlive }.minByOrNull { it.latencyMs }
        cachedResult = best
        lastCheckTime.set(System.currentTimeMillis())
        best
    }
}
