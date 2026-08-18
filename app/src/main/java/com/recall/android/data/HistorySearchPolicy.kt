package com.recall.android.data

import java.util.Locale
import kotlin.math.ceil

internal object HistorySearchPolicy {
    private val latinTokenPattern = Regex("[a-z0-9]+")
    private val cjkPattern = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}ー]+")
    private val katakanaPhrasePattern = Regex("[ァ-ヶー]{3,}")
    private val stopWords = setOf(
        "a", "an", "at", "did", "do", "does", "for", "from", "how", "i", "in", "is",
        "my", "of", "on", "the", "this", "to", "today", "was", "were", "what", "when",
        "where", "who", "why", "with", "yesterday",
        "いつ", "して", "した", "どこ", "今日", "何を", "履歴", "教え", "昨日", "誰と",
    )
    private val weakJapaneseFragments = setOf(
        "ある", "いて", "えて", "こと", "これ", "した", "して", "それ", "てた", "で見",
        "どの", "ない", "なに", "なん", "につ", "もの", "教え", "見て",
    )
    private val overviewMarkers = listOf(
        "today", "yesterday", "recently", "this morning", "this afternoon", "this evening",
        "what did i do", "where was i", "今日", "昨日", "最近", "今朝", "午前", "午後", "今週",
        "何して", "何をして", "どこにいた",
    )

    fun terms(query: String): List<String> {
        val normalized = query.lowercase(Locale.ROOT)
        val ordinary = latinTokenPattern.findAll(normalized)
            .map { it.value }
            .filter { it.length >= 2 && it !in stopWords }
            .toList()
        val katakanaPhrases = katakanaPhrasePattern.findAll(normalized)
            .map { it.value }
            .filterNot(stopWords::contains)
            .toList()
        val cjkBigrams = cjkPattern.findAll(normalized)
            .flatMap { match ->
                val value = match.value
                if (value.length < 2) emptySequence()
                else (0 until value.length - 1).asSequence().map { value.substring(it, it + 2) }
            }
            .filter { it !in stopWords && it !in weakJapaneseFragments }
            .toList()
        return (ordinary + katakanaPhrases + cjkBigrams).distinct().take(64)
    }

    fun score(text: String, terms: List<String>): Int {
        if (terms.isEmpty()) return 0
        val normalized = text.lowercase(Locale.ROOT)
        val matched = terms.filter(normalized::contains)
        if (matched.isEmpty()) return 0

        val hasDistinctiveMatch = matched.any { term ->
            term.length >= 6 || (term.length >= 4 && term.all { it in 'ァ'..'ヶ' || it == 'ー' })
        }
        val requiredMatches = when (terms.size) {
            1 -> 1
            in 2..5 -> 2
            else -> ceil(terms.size * 0.20).toInt().coerceIn(2, 6)
        }
        if (!hasDistinctiveMatch && matched.size < requiredMatches) return 0

        val weightedMatches = matched.fold(0) { total, term ->
            total + if (term.length >= 4) 4 else 1
        }
        return weightedMatches * 100 / terms.size
    }

    fun isOverviewQuery(query: String): Boolean {
        val normalized = query.lowercase(Locale.ROOT)
        return overviewMarkers.any(normalized::contains)
    }
}
