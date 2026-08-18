package com.recall.android.codex

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal class CodexTurnAccumulator(private val turnId: String) {
    private val fragments = mutableListOf<String>()

    /** Returns true only when this accumulator's turn has completed. */
    fun accept(event: JsonObject): Boolean {
        if (event.turnId() != turnId) return false
        when (event.string("method")) {
            "item/agentMessage/delta" -> event.findFirstString("delta")
                ?.takeIf(String::isNotEmpty)
                ?.let(fragments::add)

            "item/completed" -> if (fragments.isEmpty()) {
                event.findAgentMessage()?.takeIf(String::isNotBlank)?.let(fragments::add)
            }

            "turn/completed" -> {
                val turn = (event["params"] as? JsonObject)?.get("turn") as? JsonObject
                val status = turn?.string("status")
                if (status != "completed") {
                    val message = turn?.findFirstString("message")
                    error(message ?: "Codex turn ended with status ${status ?: "unknown"}")
                }
                if (fragments.isEmpty()) {
                    turn?.findAgentMessage()?.takeIf(String::isNotBlank)?.let(fragments::add)
                }
                return true
            }
        }
        return false
    }

    fun answer(): String = fragments.joinToString("").trim()
}

private fun JsonObject.turnId(): String? {
    val params = this["params"] as? JsonObject ?: return null
    return params.string("turnId")
        ?: ((params["turn"] as? JsonObject)?.string("id"))
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonElement.findFirstString(key: String): String? = when (this) {
    is JsonObject -> (this[key] as? JsonPrimitive)?.contentOrNull
        ?: values.firstNotNullOfOrNull { it.findFirstString(key) }
    is JsonArray -> firstNotNullOfOrNull { it.findFirstString(key) }
    else -> null
}

private fun JsonElement.findAgentMessage(): String? = when (this) {
    is JsonObject -> {
        val type = (this["type"] as? JsonPrimitive)?.contentOrNull
        if (type == "agentMessage") findFirstString("text") else values.firstNotNullOfOrNull { it.findAgentMessage() }
    }
    is JsonArray -> firstNotNullOfOrNull { it.findAgentMessage() }
    else -> null
}
