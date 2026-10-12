package com.manojdm018.nartodrama

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = coroutineScope {
        // Language links use Narto Drama's own locale filters; genre links use its public genre pages.
        // No catalogue is copied or stored in this extension.
        val sections = listOf(
            "English Subtitles" to "/genre/drama?lang=en-US&page=$page",
            "Bahasa Indonesia" to "/genre/drama?lang=id-ID&page=$page",
            "Filipino Subtitles" to "/genre/drama?lang=tl-PH&page=$page",
            "Hindi Subtitles" to "/genre/drama?lang=hi-IN&page=$page",
            "Tamil Subtitles" to "/genre/drama?lang=ta-IN&page=$page",
            "Telugu Subtitles" to "/genre/drama?lang=te-IN&page=$page",
            "Romance" to "/genre/romance?lang=en-US&page=$page",
            "Fantasy" to "/genre/fantasy?lang=en-US&page=$page",
            "Revenge" to "/genre/revenge?lang=en-US&page=$page",
            "Action" to "/genre/action?lang=en-US&page=$page",
            "Comedy" to "/genre/comedy?lang=en-US&page=$page",
            "Thriller" to "/genre/thriller?lang=en-US&page=$page",
            "Horror" to "/genre/horror?lang=en-US&page=$page"
        )

        val fetchedSections = sections.map { (title, path) ->
            async {
                val results = runCatching {
                    parseCards(
                        app.get(
                            "$mainUrl$path",
                            headers = mapOf("Referer" to "$mainUrl/")
                        ).document
                    )
                }.getOrDefault(emptyList())
                title to results
            }
        }.awaitAll()

        val lists = fetchedSections
            .filter { it.second.isNotEmpty() }
            .map { (title, results) -> HomePageList(title, results) }

        newHomePageResponse(
            lists,
            hasNext = fetchedSections.any { it.second.isNotEmpty() }
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

        // Try the site's supported refresh-source routes in order. Some episodes need a
        // forced edge refresh while others respond to the uncached or origin route.
        // Only use context values supplied by the page; never fabricate tokens or bypass access checks.
        val route = episodePath.find(data) ?: return found
        val slug = route.groupValues.getOrNull(1).orEmpty()
        val episodeNumber = route.groupValues.getOrNull(2)?.toIntOrNull() ?: return found
        if (slug.isBlank()) return found

        val lang = Regex("""[?&]lang=([^&#]+)""", RegexOption.IGNORE_CASE)
            .find(data)?.groupValues?.getOrNull(1)
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            ?.takeIf { it.isNotBlank() } ?: "en-US"
        val encodedLang = URLEncoder.encode(lang, "UTF-8")
        val rsCtx = extractRsContext(data, document, html)
        val contextQuery = if (!rsCtx.isNullOrBlank()) {
            "&rs_ctx=${URLEncoder.encode(rsCtx, "UTF-8")}"
        } else {
            ""
        }
        val refreshUrls = listOf(
            "$streamApiBase/e/rs/detail/watch/$slug/$episodeNumber/refresh-source?force=1&force_edge=1&lang=$encodedLang$contextQuery",
            "$streamApiBase/e/rs/detail/watch/$slug/$episodeNumber/refresh-source?force=1&no_cache=1&lang=$encodedLang$contextQuery",
            "$streamApiBase/e/rs/detail/watch/$slug/$episodeNumber/refresh-source?force=1&lang=$encodedLang$contextQuery",
            "$mainUrl/detail/watch/$slug/$episodeNumber/refresh-source?force=1&force_edge=1&lang=$encodedLang$contextQuery"
        )
        val requestHeaders = mapOf(
            "Referer" to data,
            "Origin" to mainUrl,
            "Accept" to "application/json",
            "X-Requested-With" to "XMLHttpRequest"
        )

        var payload: JSONObject? = null
        for (apiUrl in refreshUrls) {
            val responseText = runCatching {
                app.get(apiUrl, headers = requestHeaders).text
            }.getOrNull() ?: continue
            val json = runCatching { JSONObject(responseText) }.getOrNull() ?: continue
            val candidatePayload = json.optJSONObject("data")?.let { nested ->
                nested.optJSONObject("data") ?: nested
            } ?: json

            if (payload == null) payload = candidatePayload
            val direct = candidatePayload.optString("direct_play_url")
            val play = candidatePayload.optString("play_url")
            if (direct.startsWith("http", true) || play.startsWith("http", true)) {
                payload = candidatePayload
                break
            }
        }

        payload?.optJSONArray("multi_subtitles")?.let { subtitles ->
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

        val candidates = linkedMapOf<String, String>()
        payload?.optString("direct_play_url")?.takeIf { it.startsWith("http", true) }
            ?.let { candidates["Narto Drama Direct"] = it }
        payload?.optString("play_url")?.takeIf { it.startsWith("http", true) }
            ?.let { candidates["Narto Drama Fallback"] = it }

        for ((label, mediaUrl) in candidates) {
            val type = when {
                mediaUrl.contains(".m3u8", true) ||
                    (payload?.optBoolean("direct_play_is_hls", false) == true && label.contains("Direct")) ->
                    ExtractorLinkType.M3U8
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

        // Last fallback: some pages embed episode sources in their episodeItemsRaw JSON.
        // Read only the current episode's publicly supplied URLs.
        if (!found) {
            val rawEpisodes = Regex(
                """(?:const|let|var)\s+episodeItemsRaw\s*=\s*(\[[\s\S]*?\]);""",
                RegexOption.IGNORE_CASE
            ).find(html)?.groupValues?.getOrNull(1)

            val episodeItems = rawEpisodes?.let {
                runCatching { JSONArray(it) }.getOrNull()
            }
            val currentEpisode = episodeItems?.let { items ->
                (0 until items.length())
                    .mapNotNull { items.optJSONObject(it) }
                    .firstOrNull {
                        it.optInt("number", it.optInt("route_episode_number", -1)) == episodeNumber
                    }
            }

            val htmlCandidates = linkedMapOf<String, String>()
            currentEpisode?.optString("direct_play_url")?.takeIf { it.startsWith("http", true) }
                ?.let { htmlCandidates["Narto Drama Direct"] = it }
            currentEpisode?.optString("play_url")?.takeIf { it.startsWith("http", true) }
                ?.let { htmlCandidates["Narto Drama Page Source"] = it }

            for ((label, mediaUrl) in htmlCandidates) {
                val type = when {
                    mediaUrl.contains(".m3u8", true) ||
                        (currentEpisode?.optBoolean("direct_play_is_hls", false) == true && label.contains("Direct")) ->
                        ExtractorLinkType.M3U8
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

            currentEpisode?.optString("subtitle_url")?.takeIf { it.isNotBlank() }?.let { raw ->
                val subtitleUrl = if (raw.startsWith("http", true)) raw
                    else "https://stream.narto-drama.com/${raw.trimStart('/')}"
                subtitleCallback(SubtitleFile("Subtitle", subtitleUrl))
            }
            currentEpisode?.optJSONArray("multi_subtitles")?.let { subtitles ->
                for (i in 0 until subtitles.length()) {
                    val subtitle = subtitles.optJSONObject(i) ?: continue
                    val rawUrl = subtitle.optString("subtitle_url").takeIf { it.isNotBlank() } ?: continue
                    val subtitleUrl = if (rawUrl.startsWith("http", true)) rawUrl
                        else "https://stream.narto-drama.com/${rawUrl.trimStart('/')}"
                    val label = subtitle.optString("label").takeIf { it.isNotBlank() } ?: "Subtitle"
                    subtitleCallback(SubtitleFile(label, subtitleUrl))
                }
            }
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
