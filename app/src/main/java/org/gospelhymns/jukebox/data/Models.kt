package org.gospelhymns.jukebox.data

import kotlinx.serialization.Serializable

@Serializable
data class PersonCredit(
    val name: String,
    val years: String? = null,
    val source: String = SOURCE_HYMNAL,
) {
    fun label(): String = if (years.isNullOrBlank()) name else "$name（$years）"
}

@Serializable
data class Stanza(
    val kind: String,
    val label: String,
    val lines: List<String>,
)

@Serializable
data class HymnDetail(
    val number: Int,
    val chineseTitle: String = "",
    val englishTitle: String? = null,
    val englishNumber: Int? = null,
    val lyricists: List<PersonCredit> = emptyList(),
    val composers: List<PersonCredit> = emptyList(),
    val chineseStanzas: List<Stanza> = emptyList(),
    val englishStanzas: List<Stanza> = emptyList(),
    val audioUrl: String? = null,
    val audioKind: String? = null,
    val fetchedAtEpochMs: Long = 0L,
)

@Serializable
data class HymnCredit(
    val number: Int,
    val englishNumber: Int? = null,
    val hasMp3: Boolean = false,
    val hasMidi: Boolean = false,
    val lyricists: List<PersonCredit> = emptyList(),
    val composers: List<PersonCredit> = emptyList(),
)

@Serializable
data class Subcategory(
    val name: String,
    val hymns: List<Int>,
)

@Serializable
data class HymnCategory(
    val id: String,
    val name: String,
    val subcategories: List<Subcategory>,
) {
    val hymnCount: Int get() = subcategories.sumOf { it.hymns.size }
}

data class HymnPlace(
    val categoryId: String,
    val category: String,
    val subcategory: String,
)

data class AuthorHymn(
    val number: Int,
    val role: String,
)

data class AuthorGroup(
    val name: String,
    val hymns: List<AuthorHymn>,
)

data class SearchHit(
    val number: Int,
    val title: String?,
    val detail: String,
)

const val SOURCE_HYMNAL = "hymnal.net"
const val SOURCE_UNKNOWN = "unknown"
const val UNKNOWN_NAME = "佚名"

fun isUnknownCredit(name: String): Boolean {
    val key = name.trim().lowercase()
    return key.isEmpty() || key in UNKNOWN_TOKENS
}

private val UNKNOWN_TOKENS = setOf(
    "anonymous",
    "anon",
    "anon.",
    "unknown",
    "佚名",
    "不詳",
    "無",
    "n/a",
    "na",
    "none",
    "—",
    "-",
    "–",
)
