package com.manojdm018.nartodrama

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

class NartoDramaProvider : MainAPI() {
    override var mainUrl = "https://nartodrama.com"
    override var name = "Narto Drama"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries)

    private val episodePath = Regex("""/detail/watch/([^/?]+)/?(\d+)?""")
    private val streamApiBase = "https://edge.narto-drama.com"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get("$mainUrl/?lang=en-US&page=$page").document
        val results = parseCards(document)
        return newHomePageResponse(
            listOf(HomePageList("Popular Dramas", results)),
            hasNext = results.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()
        val url = "$mainUrl/?lang=en-US&q=${URLEncoder.encode(query, "UTF-8")}"
        return parseCards(app.get(url).document)
    }

    private fun parseCards(document: Document): List<SearchResponse> {
        val seen = HashSet<String>()
        val cards = document.select("article.card")
        val links = if (cards.isNotEmpty()) {
            cards.mapNotNull { card ->
                val link = card.selectFirst("a[href*='/detail/watch/']") ?: return@mapNotNull null
                Triple(link, card, link.absUrl("href"))
            }
        } else {
            document.select("a[href*='/detail/watch/']").map { link ->
                Triple(link, link, link.absUrl("href"))
            }
        }

        return links.mapNotNull { (link, card, rawHref) ->
            val href = rawHref.substringBefore("#")
            if (!href.startsWith(mainUrl) || !seen.add(href.substringBefore("?"))) {
                return@mapNotNull null
            }

            val title = card.attr("data-movie-title").ifBlank { titleFromLink(link) }
            if (title.isBlank()) return@mapNotNull null

            val image = card.selectFirst("img.poster, img")?.let { img ->
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
            ancestor.selectFirst("h1, h2, h3, h4")?.text()?.trim()?.takeIf { it.isNotBlank() }
        }
        return parentTitle.orEmpty()
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = mapOf("Referer" to "$mainUrl/")).document

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
        val response = app.get(data, headers = mapOf("Referer" to "$mainUrl/"))
        val document = response.document
        val html = document.outerHtml()
        var found = false

        // First collect every publicly exposed HTML5 media URL and iframe server.
        // These become independent links so CloudStream can try another if one fails.
        for (node in document.select("video[src], video source[src], source[src]")) {
            val mediaUrl = node.absUrl("src")
            if (!mediaUrl.startsWith("http")) continue
            val type = when {
                mediaUrl.contains(".m3u8", true) || node.attr("type").contains("mpegurl", true) ->
                    ExtractorLinkType.M3U8
                mediaUrl.contains(".mpd", true) || node.attr("type").contains("dash", true) ->
                    ExtractorLinkType.DASH
                else -> ExtractorLinkType.VIDEO
            }
            callback(
                newExtractorLink(name, "$name HTML5", mediaUrl, type) {
                    referer = data
                    quality = Qualities.Unknown.value
                }
            )
            found = true
        }

        for (frame in document.select("iframe[src]")) {
            val embedUrl = frame.absUrl("src")
            if (!embedUrl.startsWith("http")) continue
            if (loadExtractor(embedUrl, data, subtitleCallback, callback)) {
                found = true
            }
        }

        // Resolve the site's public refresh-source endpoint using context supplied by
        // the page when available. Never invent tokens or bypass access checks.
        val rsCtx = extractRsContext(data, document, html)
        val route = episodePath.find(data) ?: return found
        val slug = route.groupValues.getOrNull(1).orEmpty()
        val episodeNumber = route.groupValues.getOrNull(2)?.toIntOrNull() ?: return found
        if (slug.isBlank()) return found

        val lang = Regex("""[?&]lang=([^&#]+)""", RegexOption.IGNORE_CASE)
            .find(data)?.groupValues?.getOrNull(1)
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            ?.takeIf { it.isNotBlank() } ?: "en-US"
        val apiQuery = buildString {
            append("force=1&force_edge=1&lang=")
            append(URLEncoder.encode(lang, "UTF-8"))
            if (!rsCtx.isNullOrBlank()) {
                append("&rs_ctx=")
                append(URLEncoder.encode(rsCtx, "UTF-8"))
            }
        }
        val apiUrl = "$streamApiBase/e/rs/detail/watch/$slug/$episodeNumber/refresh-source?$apiQuery"
        val apiResponse = runCatching {
            app.get(apiUrl, headers = mapOf(
                "Referer" to data,
                "Origin" to mainUrl,
                "Accept" to "application/json",
                "X-Requested-With" to "XMLHttpRequest"
            ))
        }.getOrNull() ?: return found

        val json = runCatching { JSONObject(apiResponse.text) }.getOrNull() ?: return found
        val payload = json.optJSONObject("data") ?: json

        val subtitles = payload.optJSONArray("multi_subtitles")
        if (subtitles != null) {
            for (i in 0 until subtitles.length()) {
                val subtitle = subtitles.optJSONObject(i) ?: continue
                val rawUrl = subtitle.optString("subtitle_url").takeIf { it.isNotBlank() } ?: continue
                val subtitleUrl = when {
                    rawUrl.startsWith("http://", true) || rawUrl.startsWith("https://", true) -> rawUrl
                    rawUrl.startsWith("/") -> "https://stream.narto-drama.com$rawUrl"
                    else -> "https://stream.narto-drama.com/$rawUrl"
                }
                val label = subtitle.optString("label").takeIf { it.isNotBlank() } ?: "Subtitle"
                subtitleCallback(SubtitleFile(label, subtitleUrl))
            }
        }

        // Do not discard the fallback when a direct link is present. Both are
        // valid candidates and CloudStream can try a different link if playback fails.
        val candidates = linkedMapOf<String, String>()
        payload.optString("direct_play_url").takeIf { it.startsWith("http") }
            ?.let { candidates["Narto Drama Direct"] = it }
        payload.optString("play_url").takeIf { it.startsWith("http") }
            ?.let { candidates["Narto Drama Fallback"] = it }

        for ((label, mediaUrl) in candidates) {
            val type = when {
                mediaUrl.contains(".m3u8", true) -> ExtractorLinkType.M3U8
                mediaUrl.contains(".mpd", true) -> ExtractorLinkType.DASH
                else -> ExtractorLinkType.VIDEO
            }
            callback(
                newExtractorLink(name, label, mediaUrl, type) {
                    referer = data
                    quality = Qualities.Unknown.value
                }
            )
            found = true
        }

        return found
    }

    private fun extractRsContext(url: String, document: Document, html: String): String? {
        val fromUrl = Regex("""[?&]rs_ctx=([^&#]+)""", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.getOrNull(1)
        if (!fromUrl.isNullOrBlank()) {
            return runCatching { URLDecoder.decode(fromUrl, "UTF-8") }.getOrDefault(fromUrl)
        }

        val fromAttribute = document.selectFirst("[data-rs-ctx]")?.attr("data-rs-ctx")
            ?.takeIf { it.isNotBlank() }
        if (!fromAttribute.isNullOrBlank()) return fromAttribute

        val fromInput = document.selectFirst("input[name=rs_ctx], input[name=rsCtx]")?.attr("value")
            ?.takeIf { it.isNotBlank() }
        if (!fromInput.isNullOrBlank()) return fromInput

        // The current site can name its page-provided token
        // refreshSourceContextToken instead of rs_ctx / rsCtx.
        val contextVariable = Regex(
            """\brefreshSourceContextToken\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
        if (!contextVariable.isNullOrBlank()) return contextVariable

        val tokenPattern = Regex(
            """(?:refreshSourceContextToken|rs_ctx|rsCtx)["']?\s*[:=]\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        return tokenPattern.find(html)?.groupValues?.getOrNull(1)
    }
}
