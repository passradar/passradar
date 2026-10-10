package io.github.passradar.domain

import io.github.passradar.data.Office

/**
 * Lowercase spellings of [text] without Norwegian letters, so "tromso" finds Tromsø,
 * "barum" and "baerum" find Bærum, and "aalesund" finds Ålesund.
 */
private fun spellings(text: String): Set<String> {
    val lower = text.lowercase()
    return setOf("ae", "a").mapTo(HashSet()) { ae ->
        lower.replace("æ", ae).replace("ø", "o").replace("å", "a").replace("aa", "a")
    }
}

fun matchesSearch(office: Office, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val wanted = spellings(q)
    return (spellings(office.name) + spellings(office.city)).any { candidate -> wanted.any { candidate.contains(it) } }
}
