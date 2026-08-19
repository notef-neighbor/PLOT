package com.recall.android.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

data class MacHistoryPairing(
    val url: String,
    val token: String,
    val tlsPin: String,
    val deviceId: String,
    val deviceName: String,
) {
    companion object {
        fun parse(value: String): MacHistoryPairing {
            val trimmed = value.trim()
            require(trimmed.startsWith(PREFIX)) { "This is not a PLOT Mac pairing code" }
            val encoded = trimmed.removePrefix(PREFIX)
            val decoded = String(Base64.getUrlDecoder().decode(encoded))
            val json = Json.parseToJsonElement(decoded).jsonObject
            require(json.getValue("version").jsonPrimitive.int == 1) { "Unsupported pairing code version" }
            val pairing = MacHistoryPairing(
                url = json.getValue("url").jsonPrimitive.content.trimEnd('/'),
                token = json.getValue("token").jsonPrimitive.content,
                tlsPin = json.getValue("tlsPin").jsonPrimitive.content,
                deviceId = json.getValue("deviceId").jsonPrimitive.content,
                deviceName = json.getValue("deviceName").jsonPrimitive.content,
            )
            require(pairing.url.startsWith("https://")) { "Mac connection must use HTTPS" }
            require(pairing.token.length >= 32) { "Invalid Mac connection token" }
            require(pairing.tlsPin.startsWith("sha256/")) { "Invalid Mac certificate pin" }
            require(pairing.deviceId.isNotBlank() && pairing.deviceName.isNotBlank()) { "Invalid Mac identity" }
            return pairing
        }

        private const val PREFIX = "PLOT-MAC-1:"
    }
}

data class MacHistoryPage(
    val memories: List<HistoryMemory>,
    val nextCursor: String,
    val hasMore: Boolean,
)

class MacHistoryClient {
    fun fetch(settings: ObservationState, after: String, limit: Int = 200): MacHistoryPage {
        require(settings.macHistoryEnabled && settings.macHistoryToken.isNotBlank()) { "Mac Computer History is not connected" }
        val client = pinnedClient(settings.macHistoryTlsPin)
        val url = "${settings.macHistoryUrl}/v1/history".toHttpUrl().newBuilder()
            .addQueryParameter("after", after)
            .addQueryParameter("limit", limit.coerceIn(1, 200).toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${settings.macHistoryToken}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Mac returned HTTP ${response.code}")
            val root = JSONObject(response.body?.string().orEmpty())
            require(root.getString("deviceId") == settings.macHistoryDeviceId) { "Connected Mac identity changed" }
            val entries = root.getJSONArray("entries")
            val memories = buildList {
                for (index in 0 until entries.length()) {
                    val entry = entries.getJSONObject(index)
                    val granularity = entry.getString("granularity")
                    add(
                        HistoryMemory(
                            id = "mac:${settings.macHistoryDeviceId}:${entry.getString("id")}",
                            startedAt = Instant.parse(entry.getString("startedAt")).toEpochMilli(),
                            endedAt = Instant.parse(entry.getString("endedAt")).toEpochMilli(),
                            title = entry.getString("title"),
                            summary = entry.getString("summary"),
                            applications = entry.stringList("applications"),
                            keywords = entry.stringList("keywords"),
                            eventCount = 1,
                            source = if (granularity == "6h") "mac_6h" else "mac_10min",
                        ),
                    )
                }
            }
            return MacHistoryPage(
                memories = memories,
                nextCursor = root.optString("nextCursor", after),
                hasMore = root.optBoolean("hasMore", false),
            )
        }
    }

    private fun pinnedClient(expectedPin: String): OkHttpClient {
        val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val certificate = chain?.firstOrNull() ?: throw java.security.cert.CertificateException("Missing Mac certificate")
                certificate.checkValidity()
                val digest = MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
                val actual = "sha256/" + Base64.getEncoder().encodeToString(digest)
                if (!MessageDigest.isEqual(actual.toByteArray(), expectedPin.toByteArray())) {
                    throw java.security.cert.CertificateException("Mac certificate does not match pairing code")
                }
            }
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .callTimeout(java.time.Duration.ofSeconds(30))
            .build()
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val values = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until values.length()) add(values.optString(index))
        }.filter(String::isNotBlank)
    }
}
