package com.recall.android

internal object HistoryConversationPrompt {
    fun build(previous: List<HistoryConversationMessage>, latestQuestion: String): String {
        if (previous.isEmpty()) return latestQuestion
        return buildString {
            appendLine("<prior_conversation>")
            previous.takeLast(10).forEach { message ->
                append(if (message.isUser) "user: " else "assistant: ")
                appendLine(message.text)
            }
            appendLine("</prior_conversation>")
            append("<latest_question>").append(latestQuestion).append("</latest_question>")
        }
    }
}
