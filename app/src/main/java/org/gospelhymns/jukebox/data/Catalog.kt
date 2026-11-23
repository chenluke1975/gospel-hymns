package org.gospelhymns.jukebox.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream

class Catalog(
    val categories: List<HymnCategory>,
    val hymns: Map<Int, HymnCredit>,
    private val bios: Map<String, String>,
    private val aliases: Map<String, String>,
) {
    private val places: Map<Int, HymnPlace>
    private val order: List<Int>

    init {
        val found = linkedMapOf<Int, HymnPlace>()
        categories.forEach { category ->
            category.subcategories.forEach { sub ->
                sub.hymns.forEach { number ->
                    found[number] = HymnPlace(category.id, category.name, sub.name)
                }
            }
        }
        places = found
        order = found.keys.sorted()
    }

    fun credit(number: Int): HymnCredit? = hymns[number]

    fun creditNames(hymn: HymnCredit): List<String> {
        return (hymn.lyricists + hymn.composers).flatMap { person ->
            listOf(person.name, canonical(person.name))
        }.distinct()
    }

    fun place(number: Int): HymnPlace? = places[number]

    fun contains(number: Int): Boolean = number in places || number in hymns

    fun orderedNumbers(): List<Int> = order

    /** Index of a hymn row when only [categoryId] is expanded in the category list. */
    fun rowIndex(categoryId: String, hymn: Int): Int {
        var index = 1
        for (category in categories) {
            index += 1
            if (category.id != categoryId) continue
            for (sub in category.subcategories) {
                index += 1
                val at = sub.hymns.indexOf(hymn)
                if (at >= 0) return index + at
                index += sub.hymns.size
            }
        }
        return 1
    }

    fun step(number: Int, delta: Int): Int {
        if (order.isEmpty()) return number
        val index = order.indexOf(number)
        if (index < 0) {
            val neighbor = if (delta < 0) order.lastOrNull { it < number } else order.firstOrNull { it > number }
            return neighbor ?: number
        }
        val next = index + delta
        if (next !in order.indices) return number
        return order[next]
    }

    val authorGroups: List<AuthorGroup> by lazy {
        val map = linkedMapOf<String, MutableList<AuthorHymn>>()
        hymns.values.sortedBy { it.number }.forEach { hymn ->
            addRole(map, hymn.number, hymn.lyricists, "作詞")
            addRole(map, hymn.number, hymn.composers, "作曲")
        }
        map.map { (name, rows) ->
            val merged = rows.groupBy { it.number }.map { (number, roles) ->
                AuthorHymn(number, roles.joinToString("、") { it.role })
            }.sortedBy { it.number }
            AuthorGroup(name, merged)
        }.sortedWith(compareBy(java.text.Collator.getInstance(java.util.Locale.TRADITIONAL_CHINESE)) { it.name })
    }

    fun author(name: String): AuthorGroup? {
        val key = canonical(name)
        return authorGroups.firstOrNull { it.name == key }
    }

    fun bio(name: String): String {
        val key = canonical(name)
        if (key == UNKNOWN_NAME) return BIO_UNKNOWN
        return bios[key] ?: bios[name] ?: BIO_SPARSE
    }

    private fun canonical(name: String): String = aliases[name] ?: name

    private fun addRole(
        map: LinkedHashMap<String, MutableList<AuthorHymn>>,
        number: Int,
        people: List<PersonCredit>,
        role: String,
    ) {
        val names = people.map { canonical(it.name) }.filter { it.isNotBlank() }.ifEmpty { listOf(UNKNOWN_NAME) }
        names.forEach { name ->
            map.getOrPut(name) { mutableListOf() }.add(AuthorHymn(number, role))
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(categoriesJson: String, authorsJson: String, biosJson: String): Catalog {
            val categories = json.decodeFromString(CategoryFile.serializer(), categoriesJson).categories
            val authors = json.decodeFromString(AuthorFile.serializer(), authorsJson).hymns
            val biosFile = json.decodeFromString(BioFile.serializer(), biosJson)
            return Catalog(
                categories = categories,
                hymns = authors.associateBy { it.number },
                bios = biosFile.bios,
                aliases = biosFile.aliases,
            )
        }

        fun load(categories: InputStream, authors: InputStream, bios: InputStream): Catalog {
            return load(categories.bufferedReader().readText(), authors.bufferedReader().readText(), bios.bufferedReader().readText())
        }
    }
}

@Serializable
private data class CategoryFile(val categories: List<HymnCategory> = emptyList())

@Serializable
private data class AuthorFile(val hymns: List<HymnCredit> = emptyList())

@Serializable
private data class BioFile(
    val bios: Map<String, String> = emptyMap(),
    val aliases: Map<String, String> = emptyMap(),
)

const val BIO_UNKNOWN = "作者不詳。公開的詩歌目錄與英文對應頁都沒有留下可核對的姓名。"
const val BIO_SPARSE = "這個姓名列在詩歌的作詞或作曲欄。可靠的公開生平不足，這裡不補充未經查證的事蹟。"
