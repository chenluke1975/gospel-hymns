package org.gospelhymns.jukebox

import org.gospelhymns.jukebox.data.Catalog
import org.gospelhymns.jukebox.data.HymnCache
import org.gospelhymns.jukebox.data.HymnDetail
import org.gospelhymns.jukebox.data.HymnRepository
import org.gospelhymns.jukebox.data.HymnalApi
import org.gospelhymns.jukebox.data.HymnParser
import org.gospelhymns.jukebox.data.PersonCredit
import org.gospelhymns.jukebox.data.SOURCE_UNKNOWN
import org.gospelhymns.jukebox.data.Stanza
import org.gospelhymns.jukebox.data.UNKNOWN_NAME
import org.gospelhymns.jukebox.data.mergeCredits
import org.gospelhymns.jukebox.data.searchHymns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HymnLogicTest {
    @Test
    fun parserReadsCreditsLyricsAndAudioWithoutBioLink() {
        val html = resource("sample-ch.html")
        val page = HymnParser.parse(html, "https://www.hymnal.net/en/hymn/ch/12")
        assertEquals("測試詩題", page.title)
        assertEquals(listOf("Ada Example"), page.lyricists.map { it.name })
        assertEquals("1900-1980", page.lyricists.single().years)
        assertTrue(page.composers.isEmpty())
        assertEquals(88, page.englishNumber)
        assertEquals("https://www.hymnal.net/a/example-full", page.audioUrl)
        assertEquals("mp3", page.audioKind)
        assertEquals(listOf("第一行", "第二行"), page.stanzas[0].lines)
        assertEquals("副歌", page.stanzas[1].label)
        assertFalse(page.stanzas.joinToString { it.lines.joinToString() }.contains("Am"))
    }

    @Test
    fun mergeUsesEnglishPageThenCatalogThenUnknown() {
        val live = mergeCredits(emptyList(), listOf(PersonCredit("Ada Example")), emptyList())
        assertEquals("Ada Example", live.single().name)
        val filled = mergeCredits(emptyList(), emptyList(), listOf(PersonCredit("Ben Tune", source = "hymnary.org")))
        assertEquals("hymnary.org", filled.single().source)
        val unknown = mergeCredits(listOf(PersonCredit("Anonymous")), emptyList(), emptyList())
        assertEquals(UNKNOWN_NAME, unknown.single().name)
        assertEquals(SOURCE_UNKNOWN, unknown.single().source)
    }

    @Test
    fun catalogCoversTheChineseHymnal() {
        val catalog = loadCatalog()
        assertEquals(30, catalog.categories.size)
        val numbers = catalog.orderedNumbers()
        assertTrue((1..780).all { it in numbers })
        assertTrue((1001..1006).all { it in numbers })
        assertEquals("藉恩得救", catalog.place(247)?.subcategory)
        assertEquals("頌讚三一神", catalog.place(1)?.category)
        assertEquals(786, catalog.hymns.size)
        assertEquals("John Newton", catalog.credit(247)?.lyricists?.single()?.name)
        assertTrue(catalog.author("李常受")?.hymns?.isNotEmpty() == true)
    }

    @Test
    fun bundledCatalogHasNoLyricsOrAudio() {
        val authors = File("src/main/assets/catalog/authors.json").readText()
        val categories = File("src/main/assets/catalog/categories.json").readText()
        listOf(authors, categories).forEach { text ->
            assertFalse(text.contains("\"lyrics\""))
            assertFalse(text.contains("\"stanzas\""))
            assertFalse(text.contains("\"audioUrl\""))
            assertFalse(text.contains(".mp3"))
        }
    }

    @Test
    fun searchUsesNumberAuthorCategoryAndCachedLyrics() {
        val catalog = Catalog.load(
            categoriesJson = """
                {"categories":[{"id":"5","name":"得救的證實與快樂","subcategories":[{"name":"藉恩得救","hymns":[247]}]}]}
            """.trimIndent(),
            authorsJson = """
                {"hymns":[{"number":247,"englishNumber":313,"lyricists":[{"name":"John Newton","years":"1725-1807"}],"composers":[{"name":"Traditional American melody"}]}]}
            """.trimIndent(),
            biosJson = """{"bios":{"John Newton":"牛頓小傳"},"aliases":{}}""",
        )
        val cached = listOf(
            HymnDetail(
                number = 247,
                chineseTitle = "測試恩典",
                englishTitle = "Example Grace",
                chineseStanzas = listOf(Stanza("verse", "第1節", listOf("這一行只存在快取"))),
            ),
        )
        assertEquals(247, searchHymns("247", catalog, emptyList()).single().number)
        assertEquals(247, searchHymns("Newton", catalog, emptyList()).single().number)
        assertEquals(247, searchHymns("藉恩得救", catalog, emptyList()).single().number)
        assertTrue(searchHymns("這一行只存在快取", catalog, emptyList()).isEmpty())
        assertEquals("已開啟 · 歌詞", searchHymns("這一行只存在快取", catalog, cached).single().detail)
        assertEquals("牛頓小傳", catalog.bio("John Newton"))
    }

    @Test
    fun repositoryFallsBackToCacheWhenNetworkFails() {
        val catalog = loadCatalog()
        val dir = File("build/test-cache").apply { deleteRecursively(); mkdirs() }
        val cache = HymnCache(dir)
        cache.write(HymnDetail(number = 3, chineseTitle = "快取詩題", chineseStanzas = listOf(Stanza("verse", "第1節", listOf("快取一行")))))
        val repo = HymnRepository(FailingApi, cache, catalog)
        val loaded = kotlinx.coroutines.runBlocking { repo.open(3) }
        assertTrue(loaded.fromCache)
        assertEquals("快取詩題", loaded.detail.chineseTitle)
    }

    private fun loadCatalog(): Catalog = Catalog.load(
        File("src/main/assets/catalog/categories.json").readText(),
        File("src/main/assets/catalog/authors.json").readText(),
        File("src/main/assets/catalog/bios.json").readText(),
    )

    private fun resource(name: String): String = javaClass.classLoader!!.getResource(name)!!.readText()

    private object FailingApi : HymnalApi {
        override fun get(url: String): String = error("offline")
    }
}
