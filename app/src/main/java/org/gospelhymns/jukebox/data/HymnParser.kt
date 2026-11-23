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
    val vocalUrl: String?,
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
            audioUrl = audio(doc).instrumental,
            vocalUrl = audio(doc).vocal,
            audioKind = audio(doc).kind,
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

    private data class Tracks(val instrumental: String?, val vocal: String?, val kind: String?)

    private fun audio(doc: Document): Tracks {
        val vocal = mediaUrl(doc, "#main-audio-singing source[src]")
        val full = mediaUrl(doc, "#main-audio-full source[src]")
        val verse = mediaUrl(doc, "audio#main-audio source[src]")
        val midi = doc.selectFirst("source[type=audio/midi], a[href$=.mid], a[href$=.midi]")?.let { element ->
            val raw = if (element.tagName() == "source") {
                element.absUrl("src").ifBlank { element.attr("src") }
            } else {
                element.absUrl("href").ifBlank { element.attr("href") }
            }
            absolute(doc, raw)
        }
        val instrumental = full ?: verse ?: midi
        val kind = when {
            vocal != null || full != null || verse != null -> "mp3"
            midi != null -> "midi"
            else -> null
        }
        return Tracks(instrumental, vocal, kind)
    }

    private fun mediaUrl(doc: Document, selector: String): String? {
        val element = doc.selectFirst(selector) ?: return null
        val raw = element.absUrl("src").ifBlank { element.attr("src") }
        if (raw.isBlank()) return null
        return absolute(doc, raw)
    }

    private fun absolute(doc: Document, src: String): String {
        if (src.startsWith("http://") || src.startsWith("https://")) return src
        val base = doc.baseUri().ifBlank { "https://www.hymnal.net/" }
        return if (src.startsWith("/")) "https://www.hymnal.net$src" else base.trimEnd('/') + "/" + src
    }

    private fun String.clean(): String = replace(Regex("\\s+"), " ").trim()

    private val YEAR = Regex("""\((\d{4}\s*[-–]\s*\d{4})\)""")
}
