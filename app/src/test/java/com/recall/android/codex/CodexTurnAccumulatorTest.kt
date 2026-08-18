package com.recall.android.codex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexTurnAccumulatorTest {
    private val json = Json

    @Test
    fun collectsDeltasAndMatchesNestedCompletedTurnId() {
        val accumulator = CodexTurnAccumulator("turn-1")
        assertFalse(accumulator.accept(event("""{"method":"item/agentMessage/delta","params":{"turnId":"turn-1","itemId":"item-1","delta":"Hello "}}""")))
        assertFalse(accumulator.accept(event("""{"method":"item/agentMessage/delta","params":{"turnId":"turn-1","itemId":"item-1","delta":"world"}}""")))
        assertTrue(accumulator.accept(event("""{"method":"turn/completed","params":{"threadId":"thread-1","turn":{"id":"turn-1","items":[],"status":"completed"}}}""")))
        assertEquals("Hello world", accumulator.answer())
    }

    @Test
    fun usesCompletedAgentMessageWhenDeltasWereNotStreamed() {
        val accumulator = CodexTurnAccumulator("turn-1")
        assertTrue(accumulator.accept(event("""{"method":"turn/completed","params":{"turn":{"id":"turn-1","status":"completed","items":[{"type":"agentMessage","id":"item-1","text":"Final answer"}]}}}""")))
        assertEquals("Final answer", accumulator.answer())
    }

    @Test
    fun ignoresAnotherTurnsCompletion() {
        val accumulator = CodexTurnAccumulator("turn-1")
        assertFalse(accumulator.accept(event("""{"method":"turn/completed","params":{"turn":{"id":"turn-old","status":"completed","items":[]}}}""")))
    }

    @Test
    fun exposesFailedTurnMessage() {
        val accumulator = CodexTurnAccumulator("turn-1")
        val error = assertThrows(IllegalStateException::class.java) {
            accumulator.accept(event("""{"method":"turn/completed","params":{"turn":{"id":"turn-1","status":"failed","items":[],"error":{"message":"Network unavailable"}}}}"""))
        }
        assertEquals("Network unavailable", error.message)
    }

    private fun event(value: String) = json.parseToJsonElement(value).jsonObject
}
