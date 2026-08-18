package com.recall.android.codex

import android.util.Log
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal loopback-only HTTP CONNECT proxy.
 *
 * The bundled Codex binary is statically linked against musl, so it cannot use
 * Android's netd-backed DNS resolver directly. Opening the upstream socket from
 * the Android process lets InetAddress resolve through netd while TLS remains
 * end-to-end between Codex and the requested host.
 */
class LocalConnectProxy : Closeable {
    private val threadNumber = AtomicInteger()
    private val executor = Executors.newCachedThreadPool { task ->
        Thread(task, "plot-connect-proxy-${threadNumber.incrementAndGet()}").apply {
            isDaemon = true
        }
    }
    private val server = ServerSocket().apply {
        reuseAddress = true
        // Match the literal IPv4 address advertised in [url]. Some Android
        // builds return ::1 from getLoopbackAddress(), which would leave the
        // IPv4 proxy URL pointing at a different socket.
        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
    }

    val url: String = "http://127.0.0.1:${server.localPort}"

    init {
        executor.execute {
            while (!server.isClosed) {
                runCatching { server.accept() }
                    .onSuccess { client -> executor.execute { handle(client) } }
                    .onFailure { if (!server.isClosed) throw it }
            }
        }
    }

    override fun close() {
        server.close()
        executor.shutdownNow()
    }

    private fun handle(client: Socket) {
        client.use { downstream ->
            downstream.soTimeout = HEADER_TIMEOUT_MS
            val input = BufferedInputStream(downstream.getInputStream())
            // Do not buffer tunnel responses. TLS and WebSocket peers need each
            // frame as it arrives; waiting for EOF here deadlocks both sides.
            val output = downstream.getOutputStream()
            val requestLine = input.readHttpLine() ?: return
            val parts = requestLine.split(' ', limit = 3)
            if (parts.size < 2 || !parts[0].equals("CONNECT", ignoreCase = true)) {
                output.writeResponse("405 Method Not Allowed")
                return
            }
            while (!input.readHttpLine().isNullOrEmpty()) Unit

            val (host, port) = parseAuthority(parts[1])
            try {
                connectUpstream(host, port).use { remote ->
                    downstream.soTimeout = 0
                    output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    output.flush()
                    val upstreamCopy = executor.submit {
                        runCatching { input.copyTo(remote.getOutputStream()) }
                            .onFailure { Log.w(TAG, "Downstream copy failed for $host:$port", it) }
                        runCatching { remote.shutdownOutput() }
                    }
                    runCatching { remote.getInputStream().copyTo(output) }
                        .onFailure { Log.w(TAG, "Upstream copy failed for $host:$port", it) }
                    runCatching { output.flush() }
                    runCatching { downstream.shutdownOutput() }
                    upstreamCopy.get()
                }
            } catch (error: Exception) {
                Log.w(TAG, "CONNECT failed for $host:$port", error)
                runCatching { output.writeResponse("502 Bad Gateway") }
            }
        }
    }

    private fun connectUpstream(host: String, port: Int): Socket {
        var lastFailure: Exception? = null
        InetAddress.getAllByName(host).distinctBy(InetAddress::getHostAddress).forEach { address ->
            val candidate = Socket()
            try {
                candidate.connect(InetSocketAddress(address, port), CONNECT_ATTEMPT_TIMEOUT_MS)
                return candidate
            } catch (error: Exception) {
                lastFailure = error
                runCatching { candidate.close() }
            }
        }
        throw lastFailure ?: IllegalStateException("No address found for $host")
    }

    private fun parseAuthority(authority: String): Pair<String, Int> {
        if (authority.startsWith("[")) {
            val closingBracket = authority.indexOf(']')
            require(closingBracket > 1) { "Invalid CONNECT authority" }
            val host = authority.substring(1, closingBracket)
            val port = authority.substring(closingBracket + 1).removePrefix(":").toIntOrNull() ?: 443
            return host to port
        }
        val separator = authority.lastIndexOf(':')
        if (separator <= 0) return authority to 443
        return authority.substring(0, separator) to authority.substring(separator + 1).toInt()
    }

    private fun BufferedInputStream.readHttpLine(): String? {
        val bytes = ArrayList<Byte>()
        while (bytes.size < MAX_HEADER_LINE_BYTES) {
            val next = read()
            if (next == -1) return bytes.takeIf { it.isNotEmpty() }?.toByteArray()?.decodeToString()
            if (next == '\n'.code) {
                if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.removeAt(bytes.lastIndex)
                return bytes.toByteArray().decodeToString()
            }
            bytes += next.toByte()
        }
        error("HTTP header line is too long")
    }

    private fun OutputStream.writeResponse(status: String) {
        write("HTTP/1.1 $status\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray())
        flush()
    }

    private companion object {
        const val CONNECT_ATTEMPT_TIMEOUT_MS = 4_000
        const val HEADER_TIMEOUT_MS = 15_000
        const val MAX_HEADER_LINE_BYTES = 8_192
        const val TAG = "PlotConnectProxy"
    }
}
