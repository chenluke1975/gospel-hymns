package org.gospelhymns.jukebox.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LoadedHymn(
    val detail: HymnDetail,
    val fromCache: Boolean,
)

class HymnRepository(
    private val api: HymnalApi,
    private val cache: HymnCache,
    private val catalog: Catalog,
) {
    private val gate = Mutex()

    fun peek(number: Int): HymnDetail? = cache.read(number)

    fun cached(): List<HymnDetail> = cache.all()

    fun clearCache() = cache.clear()

    suspend fun open(number: Int): LoadedHymn = withContext(Dispatchers.IO) {
        gate.withLock {
            try {
                val fresh = fetch(number)
                cache.write(fresh)
                LoadedHymn(fresh, fromCache = false)
            } catch (error: Exception) {
                val saved = cache.read(number) ?: throw error
                LoadedHymn(saved, fromCache = true)
            }
        }
    }

    private fun fetch(number: Int): HymnDetail {
        val pageUrl = "https://www.hymnal.net/en/hymn/ch/$number"
        val chinese = HymnParser.parse(api.get(pageUrl), pageUrl)
        val englishNumber = chinese.englishNumber ?: catalog.credit(number)?.englishNumber
        val english = englishNumber?.let { en ->
            val url = "https://www.hymnal.net/en/hymn/h/$en"
            runCatching { HymnParser.parse(api.get(url), url) }.getOrNull()
        }
        val credit = catalog.credit(number)
        return HymnDetail(
            number = number,
            chineseTitle = chinese.title.ifBlank { "第 $number 首" },
            englishTitle = english?.title?.takeIf { it.isNotBlank() },
            englishNumber = englishNumber,
            lyricists = mergeCredits(chinese.lyricists, english?.lyricists.orEmpty(), credit?.lyricists.orEmpty()),
            composers = mergeCredits(chinese.composers, english?.composers.orEmpty(), credit?.composers.orEmpty()),
            chineseStanzas = chinese.stanzas,
            englishStanzas = english?.stanzas.orEmpty(),
            audioUrl = chinese.audioUrl,
            audioKind = chinese.audioKind,
            fetchedAtEpochMs = System.currentTimeMillis(),
        )
    }
}

fun mergeCredits(
    primary: List<PersonCredit>,
    secondary: List<PersonCredit>,
    fallback: List<PersonCredit>,
): List<PersonCredit> {
    fun known(list: List<PersonCredit>) = list.filter { !isUnknownCredit(it.name) && it.name != UNKNOWN_NAME }
    val live = known(primary).ifEmpty { known(secondary) }
    if (live.isNotEmpty()) return live
    val filled = known(fallback)
    if (filled.isNotEmpty()) return filled
    return listOf(PersonCredit(UNKNOWN_NAME, source = SOURCE_UNKNOWN))
}
