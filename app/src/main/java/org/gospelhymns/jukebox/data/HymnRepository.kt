package org.gospelhymns.jukebox.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LyricSyncProgress(
    val saved: Int,
    val total: Int,
    val current: Int?,
    val running: Boolean,
    val note: String?,
) {
    val complete: Boolean get() = total > 0 && saved >= total && !running
}

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

    fun lyricLibrarySize(): Int = catalog.orderedNumbers().size

    fun lyricsSavedCount(): Int = catalog.orderedNumbers().count { cache.read(it) != null }

    /**
     * Fetches any catalog hymn that is not yet on the device.
     * Already stored hymns are skipped, so a second run resumes.
     * Waits [pauseMs] after each network fetch.
     */
    suspend fun downloadMissingLyrics(
        pauseMs: Long = 2_000L,
        onProgress: suspend (LyricSyncProgress) -> Unit,
    ): Int {
        val numbers = catalog.orderedNumbers()
        val total = numbers.size
        var failures = 0
        onProgress(LyricSyncProgress(lyricsSavedCount(), total, null, running = true, note = null))
        for (number in numbers) {
            coroutineContext.ensureActive()
            if (cache.read(number) != null) continue
            onProgress(LyricSyncProgress(lyricsSavedCount(), total, number, running = true, note = null))
            try {
                withContext(Dispatchers.IO) {
                    gate.withLock {
                        if (cache.read(number) == null) {
                            cache.write(fetch(number))
                        }
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failures += 1
            }
            if (pauseMs > 0) delay(pauseMs)
        }
        val note = if (failures > 0) "有 $failures 首暫時讀不到，連線後會再試" else null
        onProgress(LyricSyncProgress(lyricsSavedCount(), total, null, running = false, note = note))
        return failures
    }

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
            instrumentalUrl = chinese.audioUrl ?: english?.audioUrl,
            vocalUrl = chinese.vocalUrl ?: english?.vocalUrl,
            audioUrl = (chinese.vocalUrl ?: english?.vocalUrl) ?: (chinese.audioUrl ?: english?.audioUrl),
            audioKind = chinese.audioKind ?: english?.audioKind,
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
