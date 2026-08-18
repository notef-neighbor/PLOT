package com.recall.android.codex

import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalConnectProxyTest {
    @Test
    fun connectTunnelRelaysBytesInBothDirections() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { echoServer ->
            val echoThread = thread {
                echoServer.accept().use { socket ->
                    val value = socket.getInputStream().read()
                    socket.getOutputStream().write(value + 1)
                }
            }
            LocalConnectProxy().use { proxy ->
                val proxyPort = proxy.url.substringAfterLast(':').toInt()
                Socket(InetAddress.getLoopbackAddress(), proxyPort).use { client ->
                    client.soTimeout = 5_000
                    client.getOutputStream().write(
                        "CONNECT 127.0.0.1:${echoServer.localPort} HTTP/1.1\r\nHost: test\r\n\r\n".toByteArray(),
                    )
                    client.getOutputStream().flush()
                    val input = BufferedInputStream(client.getInputStream())
                    var previous = -1
                    while (true) {
                        val current = input.read()
                        if (previous == '\r'.code && current == '\n'.code &&
                            input.markAndReadEndOfHeaders()
                        ) break
                        previous = current
                    }
                    client.getOutputStream().write(41)
                    client.getOutputStream().flush()
                    assertEquals(42, input.read())
                }
            }
            echoThread.join(5_000)
        }
    }

    @Test
    fun connectTunnelForwardsResponseBeforeUpstreamCloses() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val serverThread = thread {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    assertEquals(41, socket.getInputStream().read())
                    socket.getOutputStream().write(42)
                    socket.getOutputStream().flush()
                    // Keep the upstream connection open until the client proves
                    // that it received the first response byte.
                    assertEquals(43, socket.getInputStream().read())
                }
            }
            LocalConnectProxy().use { proxy ->
                val proxyPort = proxy.url.substringAfterLast(':').toInt()
                Socket(InetAddress.getLoopbackAddress(), proxyPort).use { client ->
                    client.soTimeout = 5_000
                    client.getOutputStream().write(
                        "CONNECT 127.0.0.1:${server.localPort} HTTP/1.1\r\nHost: test\r\n\r\n".toByteArray(),
                    )
                    client.getOutputStream().flush()
                    val input = BufferedInputStream(client.getInputStream())
                    input.readThroughHeaders()
                    client.getOutputStream().write(41)
                    client.getOutputStream().flush()
                    assertEquals(42, input.read())
                    client.getOutputStream().write(43)
                    client.getOutputStream().flush()
                }
            }
            serverThread.join(5_000)
        }
    }

    private fun BufferedInputStream.readThroughHeaders() {
        var previous = -1
        while (true) {
            val current = read()
            if (previous == '\r'.code && current == '\n'.code && markAndReadEndOfHeaders()) return
            previous = current
        }
    }

    private fun BufferedInputStream.markAndReadEndOfHeaders(): Boolean {
        mark(2)
        val first = read()
        val second = read()
        if (first == '\r'.code && second == '\n'.code) return true
        reset()
        return false
    }
}
