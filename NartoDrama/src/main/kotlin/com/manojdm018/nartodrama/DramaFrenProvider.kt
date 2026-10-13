package com.manojdm018.nartodrama

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/**
 * Independent DramaFren catalogue for CloudStream.
 *
 * It only reads public series/watch pages and hands their published media
 * embeds to CloudStream's ordinary extractors. It does not bypass logins,
 * paid access, DRM, or other playback restrictions.
 */
class DramaFrenProvider : MainAPI() {
    override var mainUrl = "https://dramafren.org"
    override var name = "DramaFren"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Home",
        "$mainUrl/series/" to "Latest Series"
    )

    private val seriesPath = Regex("""/series/([^/?#]+)/?""", RegexOption.IGNORE_CASE)
    private val watchPath = Regex("""/watch/([^/?#]+)/?""", RegexOption.IGNORE_CASE)

    private val blPattern = Regex(
        """\b(?:BL|boys[-'’ ]+love|danmei|yaoi|shounen[-_ ]?ai|male[-_ ]male romance)\b""",
        RegexOption.IGNORE_CASE
    )
    private val femaleLeadPattern = Regex(
        """\b(?:strong[-_ ]female[-_ ]lead|female[-_ ]led|female[-_ ]lead|female[-_ ]protagonist|female[-_ ]growth|heiress|villainess|princess|duchess|empress|queen|boss[-_ ]lady|girlboss|girl boss|independent[-_ ]woman|female[-_ ]magistrate|lady[-_ ]magistrate|female[-_ ]general|her[-_ ]revenge|sister['’]s[-_ ]revenge|the female lead|girl power|miss\s+[a-z]{2,})\b""",
        RegexOption.IGNORE_CASE
    )

    private data class EpisodeCandidate(
        val url: String,
        val number: Int,
        val title: String
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val base = request.data.takeIf { it.startsWith(mainUrl) } ?: "$mainUrl/"
        val url = if (page <= 1) base else {
            val clean = base.trimEnd('/')
            "$clean/page/$page/"
        }

        val document = runCatching {
            app.get(url, headers = requestHeaders(base)).document
        }.getOrNull() ?: return newHomePageResponse(emptyList(), hasNext = false)

        val results = parseCards(document)
        val rows = if (results.isEmpty()) emptyList() else listOf(HomePageList(request.name, results))
        return newHomePageResponse(rows, hasNext = results.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val url = "$mainUrl/?s=" + URLEncoder.encode(q, "UTF-8")
        val document = runCatching {
            app.get(url, headers = requestHeaders()).document
        }.getOrNull() ?: return emptyList()

        return parseCards(document)
    }

    private fun requestHeaders(referer: String = "$mainUrl/"): Map<String, String> = mapOf(
        "Referer" to referer,
        "Origin" to mainUrl,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    )

    private fun isAllowedDrama(title: String, metadata: String = ""): Boolean {
        val signals = "$title $metadata"
            .replace(Regex("""<[^>]+>"""), " ")
            .replace(Regex("""\s+"""), " ")
        return !blPattern.containsMatchIn(signals) &&
            !femaleLeadPattern.containsMatchIn(signals)
    }

    private fun cleanTitle(raw: String): String = raw
        .replace(Regex("""(?i)^\s*(?:completed|ongoing|dropped)\s+"""), "")
        .replace(Regex("""(?i)\s*[-|–]\s*EP\.?\s*\d+.*$"""), "")
        .replace(Regex("""(?i)\s+EP\.?\s*\d+\s*(?:FULL EPISODE)?\s*(?:TV)?\s*$"""), "")
        .replace(Regex("""(?i)\s+FULL EPISODE.*$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '-', '|', '–', ':')

    private fun parseCards(document: Document): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val seen = HashSet<String>()

        for (anchor in document.select("a[href*='/series/']")) {
            val href = anchor.absUrl("href").substringBefore("#")
            if (href.isBlank()) continue

            val path = runCatching { URI(href).path.orEmpty().trimEnd('/') }.getOrDefault("")
            seriesPath.find(path) ?: continue
            if (path == "/series" || path.contains("/page/")) continue
            if (!href.startsWith(mainUrl, ignoreCase = true)) continue

            val card = anchor.parents().firstOrNull {
                it.tagName() in setOf("article", "li") ||
                    it.hasClass("TPost") || it.hasClass("TPostMv") ||
                    it.hasClass("item") || it.hasClass("post") ||
                    it.hasClass("bs")
            } ?: anchor

            val image = card.selectFirst("img")
            val rawTitle = anchor.attr("title")
                .ifBlank { card.selectFirst("h1, h2, h3, h4, .Title, .title, .entry-title")?.text().orEmpty() }
                .ifBlank { image?.attr("alt").orEmpty() }
                .ifBlank { anchor.text() }
            val title = cleanTitle(rawTitle)
            if (title.isBlank() || !isAllowedDrama(title, card.text() + " " + card.outerHtml())) continue

            // The same series can be linked by both its poster and its title.
            val key = href.substringBefore("?").trimEnd('/').lowercase(Locale.ROOT)
            if (!seen.add(key)) continue

            val poster = image?.let { img ->
                val attr = listOf("data-src", "data-lazy-src", "data-original", "src")
                    .firstOrNull { img.attr(it).isNotBlank() }
                attr?.let { img.absUrl(it).ifBlank { img.attr(it) } }
            }?.takeIf { it.startsWith("http", ignoreCase = true) }

            results.add(newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
            })
        }
        return results
    }

    private fun episodeNumber(text: String, fallback: Int): Int {
        return Regex("""(?i)\b(?:EP(?:ISODE)?\.?\s*)(\d+)\b""")
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: fallback
    }

    private fun parseEpisodeLinks(document: Document, currentUrl: String): List<EpisodeCandidate> {
        val episodes = mutableListOf<EpisodeCandidate>()
        val seen = HashSet<String>()

        for (anchor in document.select("a[href*='/watch/']")) {
            val href = anchor.absUrl("href").substringBefore("#")
            if (href.isBlank() || !href.startsWith(mainUrl, ignoreCase = true)) continue
            val path = runCatching { URI(href).path.orEmpty() }.getOrDefault("")
            if (watchPath.find(path) == null) continue
            if (!seen.add(href.substringBefore("?").trimEnd('/').lowercase(Locale.ROOT))) continue

            val title = cleanTitle(anchor.attr("title").ifBlank { anchor.text() })
            val number = episodeNumber(anchor.text() + " " + anchor.attr("title"), episodes.size + 1)
            episodes.add(EpisodeCandidate(href, number, title))
        }

        if (episodes.isEmpty() && watchPath.containsMatchIn(currentUrl)) {
            val number = episodeNumber(currentUrl, 1)
            episodes.add(EpisodeCandidate(currentUrl, number, "Episode " + number))
        }

        return episodes.distinctBy { it.url }.sortedBy { it.number }
    }

    override suspend fun load(url: String): LoadResponse? {
        val pageResponse = runCatching {
            app.get(url, headers = requestHeaders("$mainUrl/"))
        }.getOrNull() ?: return null

        val document = pageResponse.document
        val resolvedUrl = pageResponse.url.takeIf { it.isNotBlank() } ?: url
        val title = cleanTitle(
            document.selectFirst("h1")?.text()
                .orEmpty()
                .ifBlank { document.selectFirst("meta[property=og:title]")?.attr("content").orEmpty() }
                .ifBlank { document.title().substringBefore(" - ") }
        )

        val description = document.selectFirst("meta[name=description]")?.attr("content")
            ?.ifBlank { document.selectFirst("meta[property=og:description]")?.attr("content").orEmpty() }
        val keywords = document.selectFirst("meta[name=keywords]")?.attr("content").orEmpty()
        val tagText = document.select(".genres, .tags, .tag-links, .Genre, .Tags, .Category").text()
        if (title.isBlank() || !isAllowedDrama(title, "$keywords $tagText " + (description ?: ""))) {
            return null
        }

        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: document.selectFirst("img[alt]")?.let { it.absUrl("src") }
                ?.takeIf { it.startsWith("http", ignoreCase = true) }

        val routeSlug = seriesPath.find(resolvedUrl)?.groupValues?.getOrNull(1)
            ?: watchPath.find(resolvedUrl)?.groupValues?.getOrNull(1)
        val seriesUrl = document.selectFirst("a[href*='/series/']")?.absUrl("href")
            ?.takeIf { it.startsWith(mainUrl, ignoreCase = true) }
            ?: routeSlug?.let { "$mainUrl/series/$it/" }
            ?: resolvedUrl

        val episodeCandidates = parseEpisodeLinks(document, resolvedUrl)
        val episodes = episodeCandidates.map { candidate ->
            newEpisode(candidate.url) {
                name = candidate.title.takeIf { it.isNotBlank() && it.length < 90 }
                    ?: "Episode " + candidate.number
                episode = candidate.number
                posterUrl = poster
            }
        }.ifEmpty {
            listOf(newEpisode(resolvedUrl) {
                name = "Episode 1"
                episode = 1
                posterUrl = poster
            })
        }

        return newTvSeriesLoadResponse(title, seriesUrl, TvType.TvSeries, episodes) {
            posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var watchUrl = data
        var document = runCatching {
            app.get(watchUrl, headers = requestHeaders("$mainUrl/")).document
        }.getOrNull() ?: return false

        // If the provider has only supplied a series URL, follow its first published episode.
        if (!watchPath.containsMatchIn(watchUrl)) {
            val episodeUrl = document.select("a[href*='/watch/']")
                .firstOrNull()?.absUrl("href")
            if (!episodeUrl.isNullOrBlank() && episodeUrl.startsWith(mainUrl, ignoreCase = true)) {
                watchUrl = episodeUrl
                document = runCatching {
                    app.get(watchUrl, headers = requestHeaders(data)).document
                }.getOrNull() ?: return false
            }
        }

        var found = false
        val seen = HashSet<String>()

        for (node in document.select("video[src], video source[src], video source[data-src]")) {
            val source = node.absUrl("src").ifBlank { node.absUrl("data-src") }
                .ifBlank { node.attr("src").ifBlank { node.attr("data-src") } }
            if (!source.startsWith("http", ignoreCase = true) || !seen.add(source)) continue
            val type = when {
                source.contains(".m3u8", true) -> ExtractorLinkType.M3U8
                source.contains(".mpd", true) -> ExtractorLinkType.DASH
                else -> ExtractorLinkType.VIDEO
            }
            callback(newExtractorLink(name, "DramaFren Direct", source, type) {
                referer = watchUrl
                quality = Qualities.Unknown.value
            })
            found = true
        }

        val embedUrls = linkedSetOf<String>()
        for (frame in document.select("iframe[src], iframe[data-src]")) {
            val embed = frame.absUrl("src")
                .ifBlank { frame.absUrl("data-src") }
                .ifBlank { frame.attr("src").ifBlank { frame.attr("data-src") } }
            if (embed.startsWith("http", ignoreCase = true) && embed != watchUrl) embedUrls.add(embed)
        }

        // DramaFren lists alternate player servers alongside its primary iframe.
        for (anchor in document.select("a[href]")) {
            val label = anchor.text().lowercase(Locale.ROOT)
            val href = anchor.absUrl("href")
            if (!href.startsWith("http", ignoreCase = true)) continue
            if (label.contains("upnshare") || label.contains("abyss") ||
                href.contains("upns.pro", true) || href.contains("abyss", true)) {
                embedUrls.add(href)
            }
        }

        for (embed in embedUrls) {
            found = loadExtractor(embed, watchUrl, subtitleCallback, callback) || found
        }

        return found
    }
}
