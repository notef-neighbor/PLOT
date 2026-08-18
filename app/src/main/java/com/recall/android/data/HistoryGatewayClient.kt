package com.recall.android.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class HistoryGatewayClient {
    suspend fun summarize(
        baseUrl: String,
        token: String,
        sessionId: String,
        locale: String,
        events: List<Pair<InteractionEventEntity, EventPayload>>,
    ): GatewaySummary = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("sessionId", sessionId)
            put("locale", locale)
            put("events", JSONArray().apply {
                events.forEach { (event, payload) ->
                    put(JSONObject().apply {
                        put("at", java.time.Instant.ofEpochMilli(event.occurredAt).toString())
                        put("application", payload.applicationLabel)
                        put("kind", event.kind)
                        put("text", payload.text)
                        put("contentRedacted", payload.contentRedacted)
                    })
                }
            })
        }
        val response = post(baseUrl, token, "/v1/history/summarize", body)
        GatewaySummary(
            title = response.getString("title").take(120),
            summary = response.getString("summary").take(2_000),
            keywords = response.optJSONArray("keywords").toStringList().take(20),
        )
    }

    suspend fun ask(
        baseUrl: String,
        token: String,
        question: String,
        memories: List<HistoryMemory>,
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("question", question.take(1_000))
            put("memories", JSONArray().apply {
                memories.take(30).forEach { memory ->
                    put(JSONObject().apply {
                        put("id", memory.id)
                        put("startedAt", java.time.Instant.ofEpochMilli(memory.startedAt).toString())
                        put("endedAt", java.time.Instant.ofEpochMilli(memory.endedAt).toString())
                        put("title", memory.title)
                        put("summary", memory.summary)
                        put("applications", JSONArray(memory.applications))
                    })
                }
            })
        }
        post(baseUrl, token, "/v1/history/ask", body).getString("answer").take(8_000)
    }

    private fun post(baseUrl: String, token: String, path: String, body: JSONObject): JSONObject {
        require(baseUrl.startsWith("https://")) { "Gateway must use HTTPS" }
        require(token.isNotBlank()) { "A device token is required" }
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(status in 200..299) { "Gateway request failed ($status)" }
            return JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray?.toStringList(): List<String> = buildList {
        if (this@toStringList == null) return@buildList
        for (index in 0 until length()) add(optString(index))
    }
}

data class GatewaySummary(
    val title: String,
    val summary: String,
    val keywords: List<String>,
)
