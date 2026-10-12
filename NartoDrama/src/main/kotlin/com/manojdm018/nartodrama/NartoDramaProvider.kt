package com.manojdm018.nartodrama

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.Locale

class NartoDramaProvider : MainAPI() {
    override var mainUrl = "https://nartodrama.com"
    override var name = "Narto Drama"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries)

    private val episodePath = Regex("""/detail/watch/([^/?]+)/?(\d+)?""")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = "$mainUrl/?lang=en-US&page=$page"
        val document = app.get(url).document
        val results = parseCards(document)
        return newHomePageResponse(
            listOf(HomePageList("Popular Dramas", results)),
            hasNext = results.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val document = app.get("$mainUrl/?lang=en-US&q=$encoded").document
        return parseCards(document)
    }

    private fun parseCards(document: Document): List<SearchResponse> {
        val seen = HashSet<String>()
        return document.select("a[href*='/detail/watch/']").mapNotNull { link ->
            val href = link.absUrl("href").substringBefore("#")
            if (!href.startsWith(mainUrl) || !seen.add(href.substringBefore("?"))) {
                return@mapNotNull null
            }

            val title = titleFromLink(link)
            if (title.isBlank()) return@mapNotNull null

            val card = link.parents().firstOrNull { ancestor ->
                ancestor.select("a[href*='/detail/watch/']").size == 1 &&
                    ancestor.select("img").isNotEmpty()
            }
            val image = card?.selectFirst("img")?.let { img ->
                img.absUrl("src").ifBlank {
                    img.absUrl("data-src").ifBlank { img.absUrl("data-original") }
                }
            }

            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = image?.takeIf { it.startsWith("http") }
            }
        }
    }

    private fun titleFromLink(link: Element): String {
        val text = link.text().trim()
            .replace(Regex("""^Open\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+Episode\s+\d+.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (text.isNotBlank()) return text

        val parentTitle = link.parents().firstNotNullOfOrNull { ancestor ->
            ancestor.selectFirst("h2, h3, h4")?.text()?.trim()?.takeIf { it.isNotBlank() }
        }
        return parentTitle.orEmpty()
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url, headers = mapOf("Referer" to "$mainUrl/"))
        val document = response.document

        val title = document.selectFirst("h1")?.text()?.trim()
            ?.replace(Regex("""\s+Episode\s+\d+.*$""", RegexOption.IGNORE_CASE), "")
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.replace(Regex("""\s+Episode\s+\d+.*$""", RegexOption.IGNORE_CASE), "")
                ?.replace(Regex("""\s+-\s+Free Streaming.*$""", RegexOption.IGNORE_CASE), "")
                ?.trim()
            ?: document.title().substringBefore(" - ").trim()

        val poster = document.selectFirst("meta[property=og:image]")?.absUrl("content")
            ?.takeIf { it.startsWith("http") }
            ?: document.selectFirst("img[alt]")?.absUrl("src")?.takeIf { it.startsWith("http") }

        val plot = document.selectFirst("meta[name=description]")?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")

        val slug = episodePath.find(url)?.groupValues?.getOrNull(1)
            ?: url.substringBefore("?").substringAfterLast("/")
        val episodeLinks = document.select("a[href]").mapNotNull { anchor ->
            val episodeUrl = anchor.absUrl("href")
            val match = Regex(
                """/detail/watch/${Regex.escape(slug)}/(\d+)(?:[/?#].*)?$""",
                RegexOption.IGNORE_CASE
            ).find(episodeUrl) ?: return@mapNotNull null

            val number = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            if (!episodeUrl.startsWith(mainUrl)) return@mapNotNull null
            number to episodeUrl.substringBefore("#")
        }.distinctBy { it.first }.sortedBy { it.first }

        val currentNumber = episodePath.find(url)?.groupValues?.getOrNull(2)?.toIntOrNull()
        val episodes = if (episodeLinks.isNotEmpty()) {
            episodeLinks.map { (number, episodeUrl) ->
                newEpisode(episodeUrl) {
                    name = "Episode $number"
                    episode = number
                    posterUrl = poster
                }
            }
        } else {
            val firstUrl = if (currentNumber != null) url else "$mainUrl/detail/watch/$slug/1?lang=en-US"
            listOf(newEpisode(firstUrl) {
                name = "Episode ${currentNumber ?: 1}"
                episode = currentNumber ?: 1
                posterUrl = poster
            })
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = mapOf("Referer" to "$mainUrl/")).document
        var found = false

        // Use direct HTML5 media URLs only when the page itself provides them.
        val mediaNodes = document.select("video[src], video source[src], source[src]")
        for (node in mediaNodes) {
            val mediaUrl = node.absUrl("src")
            if (!mediaUrl.startsWith("http")) continue
            val type = node.attr("type").lowercase(Locale.ROOT)
            val isHls = mediaUrl.contains(".m3u8", true) ||
                type.contains("mpegurl") || type.contains("m3u8")
            if (isHls || mediaUrl.contains(".mp4", true) || type.startsWith("video/")) {
                callback(
                    ExtractorLink(
                        name,
                        name,
                        mediaUrl,
                        data,
                        Qualities.Unknown.value,
                        isHls
                    )
                )
                found = true
            }
        }

        // Hand public embeds to CloudStream's standard extractors; no bypassing protected streams.
        for (frame in document.select("iframe[src]")) {
            val embedUrl = frame.absUrl("src")
            if (!embedUrl.startsWith("http")) continue
            if (loadExtractor(embedUrl, data, subtitleCallback, callback)) {
                found = true
            }
        }

        return found
    }
}
