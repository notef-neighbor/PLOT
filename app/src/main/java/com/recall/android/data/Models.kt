package com.recall.android.data

data class CapturedInteraction(
    val occurredAt: Long,
    val kind: String,
    val packageName: String,
    val applicationLabel: String,
    val className: String?,
    val text: String?,
    val viewId: String?,
    val contentRedacted: Boolean,
)

data class EventPayload(
    val packageName: String,
    val applicationLabel: String,
    val className: String?,
    val text: String?,
    val viewId: String?,
    val contentRedacted: Boolean,
)

data class HistoryMemory(
    val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val title: String,
    val summary: String,
    val applications: List<String>,
    val keywords: List<String>,
    val eventCount: Int,
    val source: String,
)

data class RecentActivity(
    val id: String,
    val occurredAt: Long,
    val kind: String,
    val applicationLabel: String,
    val preview: String?,
)
