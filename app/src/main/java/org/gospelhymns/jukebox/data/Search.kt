package org.gospelhymns.jukebox.data

fun searchHymns(query: String, catalog: Catalog, cached: List<HymnDetail>): List<SearchHit> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val hits = linkedMapOf<Int, SearchHit>()
    fun add(number: Int, title: String?, detail: String) {
        val previous = hits[number]
        if (previous == null) {
            hits[number] = SearchHit(number, title, detail)
        } else if (previous.title == null && title != null) {
            hits[number] = previous.copy(title = title)
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
        val lyricHit = (hymn.chineseStanzas + hymn.englishStanzas).any { stanza ->
            stanza.lines.any { it.contains(q, ignoreCase = true) }
        }
        if (titleHit || lyricHit) {
            val why = when {
                titleHit && lyricHit -> "標題與歌詞"
                titleHit -> "標題"
                else -> "歌詞"
            }
            add(hymn.number, hymn.chineseTitle.ifBlank { null }, "已開啟 · $why")
        }
    }

    return hits.values.sortedBy { it.number }
}
