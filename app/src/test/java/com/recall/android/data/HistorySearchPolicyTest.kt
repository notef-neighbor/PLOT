package com.recall.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySearchPolicyTest {
    @Test
    fun `generic natural question is allowed to use recent fallback`() {
        assertEquals(emptyList<String>(), HistorySearchPolicy.terms("What did I do today?"))
        assertEquals(0, HistorySearchPolicy.score("Opened Chrome", emptyList()))
    }

    @Test
    fun `Japanese question extracts useful name fragments`() {
        val terms = HistorySearchPolicy.terms("Slackで山田さんと何を話した？")
        assertTrue(terms.contains("slack"))
        assertTrue(terms.contains("山田"))
        assertTrue(HistorySearchPolicy.score("Slack 山田さんから公開予定の連絡", terms) > 0)
    }

    @Test
    fun `partial matches are relevant without requiring every query term`() {
        val terms = HistorySearchPolicy.terms("calendar release planning")
        assertTrue(HistorySearchPolicy.score("Calendar release: product review", terms) > 0)
    }

    @Test
    fun `one generic Japanese fragment does not make unrelated text relevant`() {
        val terms = HistorySearchPolicy.terms("Xで見てたルーティングサービスについて教えて")
        assertEquals(0, HistorySearchPolicy.score("設定についてのお知らせ", terms))
        assertTrue(HistorySearchPolicy.score("新しいルーティングサービスを比較", terms) > 0)
    }

    @Test
    fun `recent fallback is limited to overview questions`() {
        assertTrue(HistorySearchPolicy.isOverviewQuery("今日何をしてた？"))
        assertTrue(!HistorySearchPolicy.isOverviewQuery("ルーティングサービスの名前は？"))
    }

    @Test
    fun `two word query does not accept a record matching only one generic word`() {
        val terms = HistorySearchPolicy.terms("open router")
        assertEquals(0, HistorySearchPolicy.score("Open the calendar event", terms))
        assertTrue(HistorySearchPolicy.score("Open Router account", terms) > 0)
    }
}
