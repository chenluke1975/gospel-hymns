package org.gospelhymns.jukebox.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class ParsedPage(
    val title: String,
    val lyricists: List<PersonCredit>,
    val composers: List<PersonCredit>,
    val stanzas: List<Stanza>,
    val englishNumber: Int?,
    val audioUrl: String?,
    val audioKind: String?,
)

object HymnParser {
    fun parse(html: String, pageUrl: String): ParsedPage {
        val doc = Jsoup.parse(html, pageUrl)
        val title = doc.selectFirst("#song-title-text")?.text()?.clean()
            ?: doc.selectFirst("h1")?.text()?.clean()
            ?: ""
        return ParsedPage(
            title = title,
            lyricists = credits(doc, "Lyrics"),
            composers = credits(doc, "Music"),
            stanzas = stanzas(doc),
            englishNumber = englishNumber(doc),
            audioUrl = audio(doc).first,
            audioKind = audio(doc).second,
        )
    }

    private fun credits(doc: Document, label: String): List<PersonCredit> {
        val row = doc.select("dl.hn-dl div.hn-dl__row").firstOrNull { element ->
            element.selectFirst("dt")?.text()?.trim() == label
        } ?: return jsonNames(doc, if (label == "Lyrics") "lyricist" else "composer")
        val dd = row.selectFirst("dd") ?: return emptyList()
        val anchors = dd.select("a[href*=/search/all/author/], a[href*=/search/all/composer/]")
        val years = YEAR.find(dd.text())?.groupValues?.get(1)?.replace("–", "-")?.replace(" ", "")
        if (anchors.isEmpty()) {
            val text = dd.text().replace(YEAR, "").trim().trim(',', ';', '/', ' ')
            if (text.isBlank() || isUnknownCredit(text)) return emptyList()
            return listOf(PersonCredit(text, years))
        }
        val people = anchors.map { it.text().clean() }.filter { it.isNotBlank() && !isUnknownCredit(it) }
        return people.mapIndexed { index, name ->
            PersonCredit(name, if (people.size == 1) years else null)
        }.distinctBy { it.name }
    }

    private fun jsonNames(doc: Document, field: String): List<PersonCredit> {
        val html = doc.html()
        val array = Regex(""""$field"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL).find(html) ?: return emptyList()
        return Regex(""""name"\s*:\s*"([^"]+)"""").findAll(array.groupValues[1])
            .map { it.groupValues[1].clean() }
            .filter { it.isNotBlank() && !isUnknownCredit(it) }
            .map { PersonCredit(it) }
            .distinctBy { it.name }
            .toList()
    }

    private fun stanzas(doc: Document): List<Stanza> {
        val root = doc.selectFirst("div.hymn-content div.lyrics") ?: return emptyList()
        val article = root.selectFirst("article.js-stanzas") ?: return emptyList()
        return article.children().mapNotNull { stanza(it) }
    }

    private fun stanza(element: Element): Stanza? {
        val kind = element.attr("data-type").ifBlank { "verse" }
        val container = element.selectFirst(".text-container") ?: return null
        val lines = container.html()
            .split(Regex("(?i)<br\\s*/?>"))
            .map { Jsoup.parse(it).text().clean() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val number = element.selectFirst(".verse-num")?.text()?.clean().orEmpty()
        val label = when (kind) {
            "verse" -> if (number.isBlank()) "節" else "第${number}節"
            "chorus", "refrain" -> "副歌"
            else -> kind
        }
        return Stanza(kind, label, lines)
    }

    private fun englishNumber(doc: Document): Int? {
        val href = doc.select("a[href*=/en/hymn/h/]").firstOrNull()?.attr("href") ?: return null
        return Regex("""/en/hymn/h/(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun audio(doc: Document): Pair<String?, String?> {
        val mp3 = doc.selectFirst("#main-audio-full source[src]")
            ?: doc.selectFirst("audio#main-audio source[src]")
            ?: doc.selectFirst("source[type=audio/mpeg]")
        val mp3Src = mp3?.absUrl("src").orEmpty().ifBlank { mp3?.attr("src").orEmpty() }
        if (mp3Src.isNotBlank()) return absolute(doc, mp3Src) to "mp3"
        val midi = doc.selectFirst("source[type=audio/midi], a[href$=.mid], a[href$=.midi]")
        val midiSrc = when {
            midi == null -> ""
            midi.tagName() == "source" -> midi.absUrl("src").ifBlank { midi.attr("src") }
            else -> midi.absUrl("href").ifBlank { midi.attr("href") }
        }
        if (midiSrc.isNotBlank()) return absolute(doc, midiSrc) to "midi"
        return null to null
    }

    private fun absolute(doc: Document, src: String): String {
        if (src.startsWith("http://") || src.startsWith("https://")) return src
        val base = doc.baseUri().ifBlank { "https://www.hymnal.net/" }
        return if (src.startsWith("/")) "https://www.hymnal.net$src" else base.trimEnd('/') + "/" + src
    }

    private fun String.clean(): String = replace(Regex("\\s+"), " ").trim()

    private val YEAR = Regex("""\((\d{4}\s*[-–]\s*\d{4})\)""")
}
