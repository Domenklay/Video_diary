package com.quietmoments.diary

import java.util.Locale

/**
 * Search across a memory's human-readable fields. The query is whitespace
 * tolerant and all words must occur (in any order).
 */
internal object MomentSearch {
    fun matches(
        title: String,
        album: String,
        note: String,
        date: String,
        query: String
    ): Boolean {
        val terms = query.trim().lowercase(Locale.ROOT)
            .split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return true
        val content = listOf(title, album, note, date)
            .joinToString(" ").lowercase(Locale.ROOT)
        return terms.all { it in content }
    }
}
