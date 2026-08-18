package com.recall.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryConversationPromptTest {
    @Test
    fun `first question stays direct`() {
        assertEquals("What did I read?", HistoryConversationPrompt.build(emptyList(), "What did I read?"))
    }

    @Test
    fun `follow-up includes prior question and answer`() {
        val prompt = HistoryConversationPrompt.build(
            previous = listOf(
                HistoryConversationMessage(true, "Which routing service did I see?"),
                HistoryConversationMessage(false, "The captured name was incomplete."),
            ),
            latestQuestion = "Can you look again?",
        )
        assertTrue(prompt.contains("user: Which routing service did I see?"))
        assertTrue(prompt.contains("assistant: The captured name was incomplete."))
        assertTrue(prompt.contains("<latest_question>Can you look again?</latest_question>"))
    }
}
