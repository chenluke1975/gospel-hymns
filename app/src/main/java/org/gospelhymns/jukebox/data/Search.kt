package org.gospelhymns.jukebox.data

fun searchHymns(query: String, catalog: Catalog, cached: List<HymnDetail>): List<SearchHit> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val hits = linkedMapOf<Int, SearchHit>()
    fun add(number: Int, title: String?, detail: String, matchLine: String? = null) {
        val previous = hits[number]
        if (previous == null) {
            hits[number] = SearchHit(number, title, detail, matchLine)
        } else {
            hits[number] = previous.copy(
                title = previous.title ?: title,
                matchLine = previous.matchLine ?: matchLine,
                detail = if (previous.matchLine == null && matchLine != null) detail else previous.detail,
            )
        }
    }

    val asNumber = q.toIntOrNull()
    if (asNumber != null && (catalog.contains(asNumber) || asNumber in 1..780)) {
        add(asNumber, cached.firstOrNull { it.number == asNumber }?.chineseTitle, "編號")
    }

    catalog.hymns.values.forEach { hymn ->
        val names = catalog.creditNames(hymn)
        if (names.any { it.contains(q, ignoreCase = true) }) {
            add(hymn.number, null, names.distinct().joinToString("、"))
        }
    }

    catalog.categories.forEach { category ->
        category.subcategories.forEach { sub ->
            val matched = when {
                category.name.contains(q, ignoreCase = true) -> category.name
                sub.name.contains(q, ignoreCase = true) -> "${category.name} · ${sub.name}"
                else -> null
            }
            if (matched != null) {
                sub.hymns.forEach { number -> add(number, null, matched) }
            }
        }
    }

    cached.forEach { hymn ->
        val titleHit = listOfNotNull(hymn.chineseTitle, hymn.englishTitle).any { it.contains(q, ignoreCase = true) }
        val line = firstMatchingLine(hymn, q)
        if (titleHit || line != null) {
            val why = when {
                titleHit && line != null -> "標題與歌詞"
                titleHit -> "標題"
                else -> "歌詞"
            }
            add(hymn.number, hymn.chineseTitle.ifBlank { null }, why, line)
        }
    }

    return hits.values.sortedBy { it.number }
}

fun firstMatchingLine(hymn: HymnDetail, query: String): String? {
    val q = query.trim()
    if (q.isEmpty()) return null
    return (hymn.chineseStanzas + hymn.englishStanzas)
        .asSequence()
        .flatMap { it.lines.asSequence() }
        .firstOrNull { it.contains(q, ignoreCase = true) }
}
