package com.recall.android.data

import org.json.JSONArray
import org.json.JSONObject

object PayloadCodec {
    fun encodeEvent(payload: EventPayload): String = JSONObject().apply {
        put("packageName", payload.packageName)
        put("applicationLabel", payload.applicationLabel)
        put("className", payload.className)
        put("text", payload.text)
        put("viewId", payload.viewId)
        put("contentRedacted", payload.contentRedacted)
    }.toString()

    fun decodeEvent(raw: String): EventPayload = JSONObject(raw).let { json ->
        EventPayload(
            packageName = json.getString("packageName"),
            applicationLabel = json.getString("applicationLabel"),
            className = json.optString("className").takeIf { it.isNotBlank() && it != "null" },
            text = json.optString("text").takeIf { it.isNotBlank() && it != "null" },
            viewId = json.optString("viewId").takeIf { it.isNotBlank() && it != "null" },
            contentRedacted = json.optBoolean("contentRedacted"),
        )
    }

    fun encodeMemory(memory: HistoryMemory): String = JSONObject().apply {
        put("title", memory.title)
        put("summary", memory.summary)
        put("applications", JSONArray(memory.applications))
        put("keywords", JSONArray(memory.keywords))
    }.toString()

    fun decodeMemory(entity: MemoryEntity, raw: String): HistoryMemory = JSONObject(raw).let { json ->
        HistoryMemory(
            id = entity.id,
            startedAt = entity.startedAt,
            endedAt = entity.endedAt,
            title = json.getString("title"),
            summary = json.getString("summary"),
            applications = json.getJSONArray("applications").toStringList(),
            keywords = json.getJSONArray("keywords").toStringList(),
            eventCount = entity.eventCount,
            source = entity.source,
        )
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) add(getString(index))
    }
}
