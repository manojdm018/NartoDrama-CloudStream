package com.manojdm018.dramafren

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.VidStack
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
        "$mainUrl/?csbrowse=home" to "For You",
        "$mainUrl/?csbrowse=stories" to "Story Types",
        "$mainUrl/?csbrowse=genres" to "Genres",
        "$mainUrl/series/?csbrowse=latest" to "Latest Series"
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
        val view = request.data.substringAfter("csbrowse=", "").substringBefore("&")
        if (view == "stories") {
            val rows = fetchSearchRows(page, listOf(
                "rebirth" to "Reborn & Rebirth",
                "time travel" to "Time Travel",
                "revenge" to "Revenge",
                "hidden identity" to "Hidden Identity",
                "CEO" to "CEO Romance",
                "billionaire" to "Billionaire"
            ))
            return newHomePageResponse(rows, hasNext = rows.isNotEmpty())
        }
        if (view == "genres") {
            val rows = fetchSearchRows(page, listOf(
                "romance" to "Romance",
                "fantasy" to "Fantasy",
                "action" to "Action",
                "thriller" to "Thriller"
            ))
            return newHomePageResponse(rows, hasNext = rows.isNotEmpty())
        }

        val latest = view == "latest"
        val base = if (latest) "$mainUrl/series/" else "$mainUrl/"
        val url = if (page <= 1) base else {
            val clean = base.trimEnd('/')
            "$clean/page/$page/"
        }

        val document = runCatching {
            app.get(url, headers = requestHeaders(base)).document
        }.getOrNull() ?: return newHomePageResponse(emptyList(), hasNext = false)

        val results = parseCards(document)
        val rows = if (results.isEmpty()) emptyList() else listOf(
            HomePageList(if (latest) "Latest Series" else "For You", results)
        )
        return newHomePageResponse(rows, hasNext = results.isNotEmpty())
    }

    private suspend fun fetchSearchRows(
        page: Int,
        searches: List<Pair<String, String>>
    ): List<HomePageList> {
        val rows = mutableListOf<HomePageList>()
        for ((query, label) in searches) {
            val url = "$mainUrl/?s=" + URLEncoder.encode(query, "UTF-8") +
                if (page > 1) "&paged=" + page else ""
            val document = runCatching {
                app.get(url, headers = requestHeaders()).document
            }.getOrNull() ?: continue
            val results = parseCards(document)
            if (results.isNotEmpty()) rows.add(HomePageList(label, results))
        }
        return rows
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
        return Regex("""(?i)\b(?:EP(?:ISODE)?)[\s._-]*(\d+)\b""")
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: fallback
    }

    private fun episodeDisplayTitle(raw: String, number: Int): String {
        val cleaned = cleanTitle(raw)
            .replace(Regex("""(?i)^\s*EP(?:ISODE)?[\s._-]*\d+[\s|:–-]*"""), "")
            .replace(Regex("""(?i)\s+(?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{1,2},\s+\d{4}\s*$"""), "")
            .trim(' ', '-', '|', '–', ':')
        return cleaned.takeIf { it.isNotBlank() && it.length < 90 } ?: "Episode " + number
    }

    private fun parseEpisodeLinks(document: Document, currentUrl: String): List<EpisodeCandidate> {
        val episodes = mutableListOf<EpisodeCandidate>()
        val seen = HashSet<String>()
        val selector = "a[href], a[data-href], a[data-url], a[data-link], a[data-src]"

        for (anchor in document.select(selector)) {
            // Some episode anchors use href="#" and store the actual destination
            // in data-href/data-url. Validate each attribute rather than taking
            // the first non-empty value.
            val href = listOf("href", "data-href", "data-url", "data-link", "data-src")
                .map { anchor.attr(it).trim() }
                .filter { it.isNotBlank() }
                .mapNotNull { raw ->
                    val candidate = runCatching { URI(currentUrl).resolve(raw).toString() }
                        .getOrDefault(raw)
                        .substringBefore("#")
                    if (!candidate.startsWith(mainUrl, ignoreCase = true)) return@mapNotNull null
                    val candidatePath = runCatching { URI(candidate).path.orEmpty() }
                        .getOrDefault("")
                    candidate.takeIf { watchPath.find(candidatePath) != null }
                }
                .firstOrNull()
                ?: continue

            val key = href.substringBefore("?").trimEnd('/').lowercase(Locale.ROOT)
            if (!seen.add(key)) continue

            val rawTitle = listOf(
                anchor.text(),
                anchor.attr("title"),
                anchor.attr("aria-label"),
                anchor.attr("data-title"),
                anchor.attr("data-episode")
            ).firstOrNull { it.isNotBlank() }.orEmpty()
            val number = episodeNumber(rawTitle + " " + href, episodes.size + 1)
            episodes.add(EpisodeCandidate(href, number, episodeDisplayTitle(rawTitle, number)))
        }

        if (episodes.isEmpty() && watchPath.containsMatchIn(currentUrl)) {
            val number = episodeNumber(currentUrl, 1)
            episodes.add(EpisodeCandidate(currentUrl, number, "Episode " + number))
        }

        return episodes.sortedBy { it.number }
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
        val seriesUrl = if (seriesPath.containsMatchIn(resolvedUrl)) {
            resolvedUrl.substringBefore("?").substringBefore("#")
        } else {
            routeSlug?.let { "$mainUrl/series/$it/" }
                ?: document.select("a[href*='/series/']")
                    .map { it.absUrl("href") }
                    .firstOrNull {
                        val path = runCatching { URI(it).path.orEmpty().trimEnd('/') }.getOrDefault("")
                        seriesPath.containsMatchIn(path) && it.startsWith(mainUrl, ignoreCase = true)
                    }
                ?: resolvedUrl
        }

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

    private suspend fun loadDramaFrenEmbed(
        embed: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val host = runCatching { URI(embed).host.orEmpty().lowercase(Locale.ROOT) }
            .getOrDefault("")

        // DramaFren's primary player is a VidStack/UPNShare-style embed on a
        // custom host. CloudStream's generic host lookup does not match that
        // custom hostname, so invoke VidStack directly before trying others.
        if (host == "upns.pro" || host.endsWith(".upns.pro") ||
            host.contains("rpmshare") || host.contains("streamp2p")) {
            val extractedLinks = mutableListOf<ExtractorLink>()
            val extractedSubtitles = mutableListOf<SubtitleFile>()
            runCatching {
                VidStack().getUrl(
                    embed,
                    referer,
                    { extractedSubtitles.add(it) },
                    { extractedLinks.add(it) }
                )
            }
            extractedSubtitles.forEach(subtitleCallback)
            extractedLinks.forEach(callback)
            if (extractedLinks.isNotEmpty()) return true
        }

        return runCatching {
            loadExtractor(embed, referer, subtitleCallback, callback)
        }.getOrDefault(false)
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
            val episodeUrl = parseEpisodeLinks(document, watchUrl)
                .firstOrNull()?.url
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
        for (frame in document.select("iframe[src], iframe[data-src], iframe[data-lazy-src], iframe[data-url], iframe[data-embed]")) {
            for (attribute in listOf("src", "data-src", "data-lazy-src", "data-url", "data-embed")) {
                val raw = frame.attr(attribute).trim()
                if (raw.isBlank()) continue
                val embed = runCatching { URI(watchUrl).resolve(raw).toString() }.getOrDefault(raw)
                if (embed.startsWith("http", ignoreCase = true) && embed != watchUrl) {
                    embedUrls.add(embed)
                }
            }
        }

        // Alternate server tabs may link to external embeds. Ignore same-page
        // controls here: their active iframe was already collected above.
        for (anchor in document.select("a[href]")) {
            val label = anchor.text().lowercase(Locale.ROOT)
            val href = anchor.absUrl("href")
            if (!href.startsWith("http", ignoreCase = true)) continue
            val host = runCatching { URI(href).host.orEmpty().lowercase(Locale.ROOT) }
                .getOrDefault("")
            if (host == "dramafren.org" || host.endsWith(".dramafren.org")) continue
            if (label.contains("upnshare") || label.contains("abyss") ||
                host.endsWith("upns.pro") || host.contains("abyss", true)) {
                embedUrls.add(href)
            }
        }

        for (embed in embedUrls) {
            found = loadDramaFrenEmbed(embed, watchUrl, subtitleCallback, callback) || found
        }

        return found
    }
}
