package com.manojdm018.nartodrama

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

class NartoDramaProvider : MainAPI() {
    override var mainUrl = "https://nartodrama.com"
    override var name = "Narto Drama"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries)

    // Source-app, genre, tag, and subtitle-language pages supported by Narto Drama.
    private val providerChoices = listOf(
        "anyreel" to "AnyReel",
        "bilitv" to "BiliTV",
        "cubetv" to "CubeTV",
        "shortical" to "Shortical",
        "dotdrama" to "DotDrama",
        "dotdrama2" to "DotDrama II",
        "dramabite" to "DramaBite",
        "dramabox" to "DramaBox",
        "dramawave" to "DramaWave",
        "dramanova" to "DramaNova",
        "meloshort" to "MeloShort",
        "netshort" to "NetShort",
        "goodshort" to "GoodShort",
        "reelshort" to "ReelShort",
        "shortmax" to "ShortMax",
        "moboreels" to "MoboReels",
        "flickreels" to "FlickReels",
        "joyreels" to "JoyReels",
        "pinedrama" to "PineDrama",
        "happyshort" to "HappyShort",
        "freereels" to "FreeReels",
        "vigloo" to "Vigloo",
        "vyntage" to "Vyntage",
        "flareflow" to "FlareFlow",
        "reelbuzz" to "ReelBuzz",
        "stardusttv" to "StardustTV",
        "starshort" to "StarShort",
        "velolo" to "Velolo",
        "microdrama" to "MicroDrama",
        "fundrama" to "FunDrama",
        "flextv" to "FlexTV",
        "melolo" to "MeloLo",
        "reellife" to "ReelLife",
        "reelife" to "Reelife",
        "sereal" to "Sereal",
        "serealplus" to "Sereal Plus",
        "rapidtv" to "RapidTV",
        "idrama" to "iDrama",
        "iidrama" to "iiDrama",
        "bibishort" to "BibiShort",
        "kalostv" to "KalosTV"
    )
    private val genreChoices = listOf(
        "drama" to "Drama", "action" to "Action", "comedy" to "Comedy",
        "adventure" to "Adventure", "romance" to "Romance", "anime" to "Anime",
        "thriller" to "Thriller", "horror" to "Horror", "crime" to "Crime",
        "fantasy" to "Fantasy", "animation" to "Animation", "mystery" to "Mystery",
        "sci-fi" to "Sci-Fi", "family" to "Family", "biography" to "Biography",
        "documentary" to "Documentary", "history" to "History", "short" to "Short",
        "sport" to "Sport", "music" to "Music", "war" to "War",
        "true-love" to "True Love", "costume-drama" to "Costume Drama", "western" to "Western"
    )
    private val tagChoices = listOf(
        "rebirth" to "Reborn & Rebirth",
        "time-travel" to "Time Travel",
        "revenge" to "Revenge",
        "hidden-identity" to "Hidden Identity",
        "zero-to-hero" to "Zero to Hero",
        "second-chance" to "Second Chance",
        "counterattack" to "Counterattack",
        "underdog-rise" to "Underdog Rise",
        "system" to "System & Superpowers",
        "ceo" to "CEO",
        "billionaire" to "Billionaire",
        "high-society" to "High Society",
        "romantic-tension" to "Romantic Tension",
        "modern-romance" to "Modern Romance",
        "betrayal" to "Betrayal",
        "the-comeback" to "The Comeback",
        "love-after-marriage" to "Love After Marriage",
        "fated-lovers" to "Fated Lovers",
        "werewolf" to "Werewolf",
        "cute-kids" to "Cute Kids",
        "mafia" to "Mafia",
        "youth" to "Youth",
        "ancient" to "Ancient / Costume",
        "karma-payback" to "Karma Payback",
        "playing-dumb" to "Playing Dumb",
        "secret-identity" to "Secret Identity",
        "redemption" to "Redemption",
        "wish-fulfillment" to "Wish Fulfillment",
        "forbidden-love" to "Forbidden Love",
        "the-chosen-one" to "The Chosen One",
        "royalty" to "Royalty",
        "regret" to "Regret",
        "sweet-love" to "Sweet Love",
        "all-too-late" to "All Too Late",
        "love-triangle" to "Love Triangle",
        "fantasy-romance" to "Fantasy Romance"
    )
    private val languageChoices = listOf(
        "en-US" to "English", "id-ID" to "Bahasa Indonesia", "ja-JP" to "Japanese",
        "ko-KR" to "Korean", "zh-TW" to "Traditional Chinese", "es-ES" to "Spanish",
        "th-TH" to "Thai", "de-DE" to "German", "pt-PT" to "Portuguese",
        "fr-FR" to "French", "ar-SA" to "Arabic", "vi-VN" to "Vietnamese",
        "ru-RU" to "Russian", "it-IT" to "Italian", "tr-TR" to "Turkish",
        "tl-PH" to "Filipino", "ms-MY" to "Malay", "hi-IN" to "Hindi",
        "ta-IN" to "Tamil", "te-IN" to "Telugu", "bn-BD" to "Bengali", "pl-PL" to "Polish"
    )
    // Keep CloudStream's built-in page picker short. Individual categories are grouped
    // as vertically scrollable rows within these five browse pages.
    private val browsePages = listOf(
        ("$mainUrl/?csbrowse=home") to "For You",
        ("$mainUrl/?csbrowse=stories") to "Story Types",
        ("$mainUrl/?csbrowse=genres") to "Genres",
        ("$mainUrl/?csbrowse=languages") to "Languages",
        ("$mainUrl/?csbrowse=sources") to "Source Apps"
    )
    override val mainPage = mainPageOf(*browsePages.toTypedArray())

    private val episodePath = Regex("""/detail/watch/([^/?]+)/?(\d+)?""")
    private val blPattern = Regex(
        """\b(?:BL|boys['’ ]+love|danmei|yaoi|shounen[- ]?ai|male[- ]male romance)\b""",
        RegexOption.IGNORE_CASE
    )
    private val femaleLeadPattern = Regex(
        """\b(?:strong female lead|female[- ]led|female lead|female protagonist|female growth|heiress|villainess|princess|duchess|empress|queen|boss lady|girlboss|girl boss|independent woman|female magistrate|lady magistrate|female general|her revenge|sister['’]s revenge|the female lead|girl power|miss\s+[a-z]{2,})\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Exclude titles whose own title/card metadata explicitly identifies BL
     * or common female-led story tags. This is a conservative metadata filter;
     * a title with no reliable character metadata cannot be classified perfectly.
     */
    private fun isAllowedDrama(title: String, metadata: String = ""): Boolean {
        val signals = "$title $metadata"
            .replace(Regex("""<[^>]+>"""), " ")
            .replace(Regex("""\s+"""), " ")
        return !blPattern.containsMatchIn(signals) &&
            !femaleLeadPattern.containsMatchIn(signals)
    }

    private val streamApiBase = "https://edge.narto-drama.com"


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val key = request.data.substringAfter("csbrowse=", "").substringBefore("&")
            .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            .ifBlank { "provider:anyreel" }
        val rows = when {
            key == "home" -> fetchHomeRows(page)
            key == "stories" -> fetchStoryRows(page)
            key == "genres" -> fetchGenreRows(page)
            key == "languages" -> fetchLanguageRows(page)
            key == "sources" -> fetchSourceRows(page)
            key.startsWith("provider:") -> {
                val provider = key.substringAfter("provider:")
                val label = providerChoices.firstOrNull { it.first == provider }?.second ?: provider
                fetchProviderSections(provider, page, "en-US", label)
            }
            key.startsWith("lang:") -> {
                val locale = key.substringAfter("lang:")
                val label = languageChoices.firstOrNull { it.first == locale }?.second ?: locale
                fetchProviderSections("anyreel", page, locale, "AnyReel • " + label)
            }
            key.startsWith("genre:") || key.startsWith("tag:") || key.startsWith("page:") -> {
                val path = browsePath(key) ?: return newHomePageResponse(emptyList(), hasNext = false)
                val separator = if (path.contains("?")) "&" else "?"
                val url = mainUrl + path + separator + "lang=en-US&page=" + page
                val doc = runCatching { app.get(url, headers = requestHeaders()).document }.getOrNull()
                val results = doc?.let { parseCards(it).ifEmpty { parseJsonLdCards(it) } } ?: emptyList()
                if (results.isEmpty()) emptyList() else listOf(HomePageList(request.name, results))
            }
            else -> fetchHomeRows(page)
        }
        return newHomePageResponse(rows, hasNext = rows.isNotEmpty())
    }

    private suspend fun fetchHomeRows(page: Int): List<HomePageList> {
        val rows = mutableListOf<HomePageList>()

        // A popular row first, followed by official tag and genre pages from Narto Drama.
        rows.addAll(fetchProviderSections("anyreel", page, "en-US", "Popular"))

        val storyRows = listOf(
            "rebirth" to "Reborn & Rebirth",
            "time-travel" to "Time Travel",
            "revenge" to "Revenge",
            "hidden-identity" to "Hidden Identity",
            "zero-to-hero" to "Zero to Hero",
            "second-chance" to "Second Chance"
        )
        for ((slug, title) in storyRows) {
            val results = fetchCatalogPage("/tag/" + slug, page, "en-US")
            if (results.isNotEmpty()) rows.add(HomePageList(title, results))
        }

        val genreRows = listOf(
            "romance" to "Romance",
            "fantasy" to "Fantasy",
            "action" to "Action"
        )
        for ((slug, title) in genreRows) {
            val results = fetchCatalogPage("/genre/" + slug, page, "en-US")
            if (results.isNotEmpty()) rows.add(HomePageList(title, results))
        }

        return rows
    }

    private suspend fun fetchStoryRows(page: Int): List<HomePageList> {
        val slugs = listOf(
            "rebirth", "time-travel", "revenge", "hidden-identity",
            "zero-to-hero", "second-chance", "counterattack", "underdog-rise",
            "system", "ceo", "billionaire",
            "karma-payback", "werewolf", "ancient"
        )
        val rows = mutableListOf<HomePageList>()
        for (slug in slugs) {
            val title = tagChoices.firstOrNull { it.first == slug }?.second ?: slug
            val results = fetchCatalogPage("/tag/" + slug, page, "en-US")
            if (results.isNotEmpty()) rows.add(HomePageList(title, results))
        }
        return rows
    }

    private suspend fun fetchGenreRows(page: Int): List<HomePageList> {
        val slugs = listOf(
            "drama", "romance", "fantasy", "action", "comedy",
            "thriller", "mystery", "horror", "crime", "adventure", "sci-fi", "family"
        )
        val rows = mutableListOf<HomePageList>()
        for (slug in slugs) {
            val title = genreChoices.firstOrNull { it.first == slug }?.second ?: slug
            val results = fetchCatalogPage("/genre/" + slug, page, "en-US")
            if (results.isNotEmpty()) rows.add(HomePageList(title, results))
        }
        return rows
    }

    private suspend fun fetchLanguageRows(page: Int): List<HomePageList> {
        val locales = listOf("en-US", "id-ID", "hi-IN", "ta-IN", "te-IN", "tl-PH", "ja-JP", "ko-KR")
        val rows = mutableListOf<HomePageList>()
        for (locale in locales) {
            val label = languageChoices.firstOrNull { it.first == locale }?.second ?: locale
            rows.addAll(fetchProviderSections("anyreel", page, locale, label))
        }
        return rows
    }

    private suspend fun fetchSourceRows(page: Int): List<HomePageList> {
        val providers = listOf(
            "dramabox", "reelshort", "netshort", "goodshort",
            "shortmax", "moboreels", "dramawave", "anyreel"
        )
        val rows = mutableListOf<HomePageList>()
        for (provider in providers) {
            val label = providerChoices.firstOrNull { it.first == provider }?.second ?: provider
            rows.addAll(fetchProviderSections(provider, page, "en-US", label))
        }
        return rows
    }

    private suspend fun fetchCatalogPage(path: String, page: Int, locale: String): List<SearchResponse> {
        val separator = if (path.contains("?")) "&" else "?"
        val url = mainUrl + path + separator +
            "lang=" + URLEncoder.encode(locale, "UTF-8") + "&page=" + page
        val document = runCatching {
            app.get(url, headers = requestHeaders(mainUrl + "/")).document
        }.getOrNull() ?: return emptyList()

        return deduplicateResults(parseCards(document) + parseJsonLdCards(document))
    }

    private fun browsePath(key: String): String? = when {
        key == "page:featured" -> "/featured"
        key == "page:movies" -> "/?type=movie-series"
        key == "page:anime" -> "/?type=anime"
        key.startsWith("genre:") -> "/genre/" + key.substringAfter("genre:")
        key.startsWith("tag:") -> "/tag/" + key.substringAfter("tag:")
        else -> null
    }

    private fun requestHeaders(referer: String = "$mainUrl/"): Map<String, String> = mapOf(
        "Referer" to referer,
        "Origin" to mainUrl,
        "Accept" to "application/json, text/plain, */*",
        "X-Requested-With" to "XMLHttpRequest"
    )

    private suspend fun requestProviderJson(
        provider: String,
        locale: String,
        tabs: List<Pair<String, Int>> = emptyList(),
        query: String? = null,
        includeTarget: Boolean = true
    ): JSONObject? {
        val params = mutableListOf(
            "provider=" + URLEncoder.encode(provider, "UTF-8"),
            "lang=" + URLEncoder.encode(locale, "UTF-8")
        )
        if (includeTarget && locale != "all") params.add("target_lang=" + URLEncoder.encode(locale, "UTF-8"))
        if (!query.isNullOrBlank()) {
            params.add("q=" + URLEncoder.encode(query, "UTF-8"))
            params.add("page=1")
        }
        tabs.forEach { (tab, targetPage) ->
            params.add("tab_pages[" + URLEncoder.encode(tab, "UTF-8") + "]=" + targetPage)
        }
        val url = mainUrl + "/home/providers/sections?" + params.joinToString("&")
        val body = runCatching { app.get(url, headers = requestHeaders()).text }.getOrNull() ?: return null
        return runCatching { JSONObject(body) }.getOrNull()
    }

    private fun hasItems(json: JSONObject?): Boolean {
        val sections = json?.optJSONArray("sections") ?: return false
        for (i in 0 until sections.length()) {
            val items = sections.optJSONObject(i)?.optJSONArray("items")
            if (items != null && items.length() > 0) return true
        }
        return false
    }

    private suspend fun fetchProviderSections(
        provider: String,
        page: Int,
        locale: String,
        providerLabel: String
    ): List<HomePageList> {
        var json = requestProviderJson(provider, locale)
        if (!hasItems(json)) {
            val relaxed = requestProviderJson(provider, locale, includeTarget = false)
            if (hasItems(relaxed)) json = relaxed
        }
        if (page > 1 && json != null) {
            val sections = json.optJSONArray("sections")
            val tabs = mutableListOf<Pair<String, Int>>()
            if (sections != null) {
                for (i in 0 until sections.length()) {
                    val key = sections.optJSONObject(i)?.optString("tab_key").orEmpty()
                    if (key.isNotBlank()) tabs.add(key to page)
                }
            }
            if (tabs.isNotEmpty()) {
                val paged = requestProviderJson(provider, locale, tabs)
                if (hasItems(paged)) json = paged
                else {
                    val relaxedPage = requestProviderJson(provider, locale, tabs, includeTarget = false)
                    if (hasItems(relaxedPage)) json = relaxedPage
                }
            }
        }
        val sections = json?.optJSONArray("sections") ?: return emptyList()
        val allResults = mutableListOf<SearchResponse>()
        val seenUrls = HashSet<String>()
        for (i in 0 until sections.length()) {
            val sec = sections.optJSONObject(i) ?: continue
            val items = sec.optJSONArray("items") ?: continue
            for (result in parseApiItems(items)) {
                if (seenUrls.add(result.url)) allResults.add(result)
            }
        }

        // One clearly labelled home row per source app; merge that app's upstream tabs.
        return if (allResults.isEmpty()) emptyList()
        else listOf(HomePageList(providerLabel + " Dramas", allResults))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val variants = linkedSetOf(q)
        val shortQuery = q.split(Regex("""\s+""")).take(3).joinToString(" ")
        if (shortQuery.isNotBlank() && shortQuery != q) variants.add(shortQuery)

        // Search the site's real /search endpoint first, across English and Indonesian indexes.
        for (locale in listOf("en-US", "id-ID")) {
            for (variant in variants) {
                val url = mainUrl + "/search?q=" + URLEncoder.encode(variant, "UTF-8") +
                    "&limit=50&lang=" + URLEncoder.encode(locale, "UTF-8")
                val response = runCatching { app.get(url, headers = requestHeaders()) }.getOrNull() ?: continue
                val jsonResults = parseSearchJson(response.text)
                if (jsonResults.isNotEmpty()) return deduplicateResults(jsonResults)
                val pageResults = parseCards(response.document) + parseJsonLdCards(response.document)
                if (pageResults.isNotEmpty()) return deduplicateResults(pageResults)
            }
        }

        // Site's provider-search fallback; it is still the site's own public search API.
        for (locale in listOf("en-US", "id-ID")) {
            for (variant in variants) {
                val json = requestProviderJson("dc_search", locale, query = variant)
                    ?: requestProviderJson("dc_search", locale, query = variant, includeTarget = false)
                val sections = json?.optJSONArray("sections") ?: continue
                val out = mutableListOf<SearchResponse>()
                for (i in 0 until sections.length()) {
                    val items = sections.optJSONObject(i)?.optJSONArray("items") ?: continue
                    out.addAll(parseApiItems(items))
                }
                if (out.isNotEmpty()) return deduplicateResults(out)
            }
        }
        return emptyList()
    }

    private fun parseSearchJson(body: String): List<SearchResponse> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val items = root.optJSONArray("items")
            ?: root.optJSONObject("data")?.optJSONArray("items")
            ?: root.optJSONArray("results")
            ?: return emptyList()
        return parseApiItems(items)
    }

    private fun parseApiItems(items: JSONArray): List<SearchResponse> {
        val out = mutableListOf<SearchResponse>()
        val seen = HashSet<String>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val title = item.optString("title")
                .ifBlank { item.optString("name") }
                .ifBlank { item.optString("movie_title") }.trim()
            val rawUrl = item.optString("watch_url")
                .ifBlank { item.optString("url") }
                .ifBlank { item.optString("detail_url") }.trim()
            if (title.isBlank() || rawUrl.isBlank()) continue
            val url = absoluteUrl(rawUrl)
            if (!isNartoUrl(url) || !seen.add(url)) continue
            if (!isAllowedDrama(title, item.toString())) continue
            val image = item.optString("poster_url")
                .ifBlank { item.optString("cover_url") }
                .ifBlank { item.optString("cover") }
                .ifBlank { item.optString("poster") }
                .takeIf { it.isNotBlank() }?.let { absoluteUrl(it) }
            out.add(newTvSeriesSearchResponse(title, url, TvType.TvSeries) { posterUrl = image })
        }
        return out
    }

    private fun parseJsonLdCards(document: Document): List<SearchResponse> {
        val out = mutableListOf<SearchResponse>()
        val seen = HashSet<String>()
        for (script in document.select("script[type=application/ld+json]")) {
            val raw = script.data().ifBlank { script.html() }.trim()
            if (raw.isBlank()) continue
            val roots = mutableListOf<JSONObject>()
            runCatching { JSONObject(raw) }.getOrNull()?.let { roots.add(it) }
            runCatching { JSONArray(raw) }.getOrNull()?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { roots.add(it) }
            }
            for (root in roots) {
                val listObj = if (root.optString("@type") == "ItemList") root
                    else root.optJSONObject("mainEntity")?.takeIf { it.optString("@type") == "ItemList" }
                        ?: root.optJSONArray("@graph")?.let { graph ->
                            (0 until graph.length()).mapNotNull { graph.optJSONObject(it) }
                                .firstOrNull { it.optString("@type") == "ItemList" }
                        }
                    ?: continue
                val elements = listObj.optJSONArray("itemListElement") ?: continue
                for (i in 0 until elements.length()) {
                    val entry = elements.optJSONObject(i) ?: continue
                    val item = entry.optJSONObject("item") ?: entry
                    val title = item.optString("name").ifBlank { item.optString("title") }.trim()
                    val rawUrl = item.optString("url").trim()
                    if (title.isBlank() || rawUrl.isBlank()) continue
                    if (!isAllowedDrama(title, item.toString())) continue
                    val url = absoluteUrl(rawUrl)
                    if (!isNartoUrl(url) || !seen.add(url)) continue
                    val image = item.optString("image").takeIf { it.isNotBlank() }?.let { absoluteUrl(it) }
                    out.add(newTvSeriesSearchResponse(title, url, TvType.TvSeries) { posterUrl = image })
                }
            }
        }
        return out
    }

    private fun deduplicateResults(items: List<SearchResponse>): List<SearchResponse> {
        val seen = HashSet<String>()
        return items.filter { seen.add(it.url) }
    }

    private fun absoluteUrl(raw: String): String {
        val value = raw.trim().replace("&amp;", "&")
        return when {
            value.startsWith("https://", true) -> value
            value.startsWith("http://", true) -> "https://" + value.substringAfter("://")
            value.startsWith("//") -> "https:" + value
            value.startsWith("/") -> mainUrl + value
            else -> mainUrl + "/" + value.trimStart('/')
        }
    }

    private fun isNartoUrl(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return false
        return host in setOf(
            "nartodrama.com", "www.nartodrama.com",
            "narto-drama.com", "www.narto-drama.com",
            "narto.in", "www.narto.in", "edge.narto.in"
        )
    }

    private fun parseCards(document: Document): List<SearchResponse> {
        val cards = document.select("article.card")
        val pending = mutableListOf<Triple<String, String, String?>>()
        if (cards.isNotEmpty()) {
            for (card in cards) {
                val anchor = card.selectFirst("a[href*='/detail/watch/'], a[href*='/search/import'], a[href]")
                val rawHref = card.attr("data-watch-url").ifBlank { anchor?.attr("href").orEmpty() }
                if (rawHref.isBlank()) continue
                val href = absoluteUrl(rawHref)
                if (!isNartoUrl(href)) continue
                val title = card.attr("data-movie-title").ifBlank {
                    card.selectFirst("h1, h2, h3, h4, .title, .card-title")?.text()?.trim()
                        ?: anchor?.let { titleFromLink(it) }.orEmpty()
                }.trim()
                if (title.isBlank() || !isAllowedDrama(title, card.text() + " " + card.outerHtml())) continue
                val poster = card.selectFirst("img.poster, img")?.let { img ->
                    img.attr("src").ifBlank { img.attr("data-src") }
                        .ifBlank { img.attr("data-original") }
                        .ifBlank { img.attr("data-lazy-src") }
                        .takeIf { it.isNotBlank() }?.let { absoluteUrl(it) }
                }
                pending.add(Triple(title, href, poster))
            }
        } else {
            for (anchor in document.select("a[href*='/detail/watch/'], a[href*='/search/import']")) {
                val href = absoluteUrl(anchor.attr("href"))
                if (!isNartoUrl(href)) continue
                val title = titleFromLink(anchor)
                if (title.isBlank() || !isAllowedDrama(title, anchor.text() + " " + anchor.outerHtml())) continue
                val poster = anchor.selectFirst("img")?.let { img ->
                    img.attr("src").ifBlank { img.attr("data-src") }
                        .ifBlank { img.attr("data-original") }
                        .takeIf { it.isNotBlank() }?.let { absoluteUrl(it) }
                }
                pending.add(Triple(title, href, poster))
            }
        }
        val seen = HashSet<String>()
        return pending.mapNotNull { (title, href, poster) ->
            if (!seen.add(href)) return@mapNotNull null
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = poster }
        }
    }

    private fun titleFromLink(link: Element): String {
        val text = link.text().trim()
            .replace(Regex("""^Open\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+Episode\s+\d+.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (text.isNotBlank()) return text
        return link.parents().firstNotNullOfOrNull { parent ->
            parent.selectFirst("h1, h2, h3, h4")?.text()?.trim()?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }


    override suspend fun load(url: String): LoadResponse? {
        val pageResponse = app.get(url, headers = requestHeaders(mainUrl + "/"))
        val document = pageResponse.document
        val resolvedUrl = pageResponse.url.takeIf { it.isNotBlank() } ?: url

        val title = document.selectFirst("h1")?.text()?.trim()
            ?.replace(Regex("""\s+Episode\s+\d+.*$""", RegexOption.IGNORE_CASE), "")
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.replace(Regex("""\s+Episode\s+\d+.*$""", RegexOption.IGNORE_CASE), "")
                ?.replace(Regex("""\s+-\s+Free Streaming.*$""", RegexOption.IGNORE_CASE), "")
                ?.trim()
            ?: document.title().substringBefore(" - ").trim()

        val exclusionMetadata = listOf(
            document.selectFirst("meta[name=keywords]")?.attr("content").orEmpty(),
            document.selectFirst("meta[name=description]")?.attr("content").orEmpty(),
            document.selectFirst("meta[property=og:description]")?.attr("content").orEmpty(),
            document.select(".genres, .tags, .tag-links, .genre, .tag").text()
        ).joinToString(" ")
        if (!isAllowedDrama(title, exclusionMetadata)) return null

        val poster = document.selectFirst("meta[property=og:image]")?.absUrl("content")
            ?.takeIf { it.startsWith("http") }
            ?: document.selectFirst("img[alt]")?.absUrl("src")?.takeIf { it.startsWith("http") }

        val plot = document.selectFirst("meta[name=description]")?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")

        val route = episodePath.find(resolvedUrl) ?: episodePath.find(url)
        val slug = route?.groupValues?.getOrNull(1) ?: return null
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

        val currentNumber = route?.groupValues?.getOrNull(2)?.toIntOrNull()
        val episodes = if (episodeLinks.isNotEmpty()) {
            episodeLinks.map { (number, episodeUrl) ->
                newEpisode(episodeUrl) {
                    name = "Episode $number"
                    episode = number
                    posterUrl = poster
                }
            }
        } else {
            val firstUrl = if (currentNumber != null) resolvedUrl else "$mainUrl/detail/watch/$slug/1?lang=en-US"
            listOf(newEpisode(firstUrl) {
                name = "Episode ${currentNumber ?: 1}"
                episode = currentNumber ?: 1
                posterUrl = poster
            })
        }

        return newTvSeriesLoadResponse(title, resolvedUrl, TvType.TvSeries, episodes) {
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

        val emittedMediaUrls = linkedSetOf<String>()
        val expandedHlsMasters = linkedSetOf<String>()

        suspend fun emitMediaLink(mediaUrl: String, label: String, type: ExtractorLinkType): Boolean {
            if (mediaUrl.isBlank() || mediaUrl in emittedMediaUrls || mediaUrl in expandedHlsMasters) return false

            if (type == ExtractorLinkType.M3U8) {
                expandedHlsMasters.add(mediaUrl)
                val variants = parseHlsVariants(mediaUrl, data)
                if (variants.isNotEmpty()) {
                    // Keep the HLS master as an Auto option; child playlists may not expose
                    // the master playlist's alternate audio renditions.
                    if (emittedMediaUrls.add(mediaUrl)) {
                        callback(
                            newExtractorLink(name, "$label • Auto (audio tracks if available)", mediaUrl, ExtractorLinkType.M3U8) {
                                referer = data
                                quality = Qualities.Unknown.value
                            }
                        )
                    }
                    for (variant in variants) {
                        if (!emittedMediaUrls.add(variant.url)) continue
                        callback(
                            newExtractorLink(name, "$label • ${variant.label}", variant.url, ExtractorLinkType.M3U8) {
                                referer = data
                                quality = variant.quality
                            }
                        )
                    }
                    return true
                }
            }

            if (!emittedMediaUrls.add(mediaUrl)) return false
            callback(
                newExtractorLink(name, label, mediaUrl, type) {
                    referer = data
                    quality = Qualities.Unknown.value
                }
            )
            return true
        }

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
            if (emitMediaLink(mediaUrl, "$name HTML5", type)) {
                found = true
            }
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
            if (emitMediaLink(mediaUrl, label, type)) {
                    found = true
                }
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
                if (emitMediaLink(mediaUrl, label, type)) {
                    found = true
                }
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


    private data class HlsVariant(
        val url: String,
        val quality: Int,
        val label: String
    )

    /**
     * Resolve real variant URLs from an HLS master playlist.
     * If the source is a media playlist rather than a master playlist, the caller
     * falls back to offering the original stream URL.
     */
    private suspend fun parseHlsVariants(masterUrl: String, refererUrl: String): List<HlsVariant> {
        val playlist = runCatching {
            app.get(
                masterUrl,
                headers = mapOf(
                    "Referer" to refererUrl,
                    "Accept" to "application/vnd.apple.mpegurl, application/x-mpegURL, */*"
                )
            ).text
        }.getOrNull() ?: return emptyList()

        if (!playlist.lineSequence().any { it.trim() == "#EXTM3U" }) return emptyList()

        fun standardQuality(rawValue: Int): Int = when {
            rawValue >= 2000 -> Qualities.P2160.value
            rawValue >= 1300 -> Qualities.P1440.value
            rawValue >= 1000 -> Qualities.P1080.value
            rawValue >= 650 -> Qualities.P720.value
            rawValue >= 430 -> Qualities.P480.value
            rawValue >= 300 -> Qualities.P360.value
            rawValue >= 200 -> Qualities.P240.value
            else -> Qualities.Unknown.value
        }

        fun qualityLabel(value: Int, fallbackName: String, index: Int): String = when (value) {
            Qualities.P240.value -> "240p"
            Qualities.P360.value -> "360p"
            Qualities.P480.value -> "480p"
            Qualities.P720.value -> "720p"
            Qualities.P1080.value -> "1080p"
            Qualities.P1440.value -> "1440p"
            Qualities.P2160.value -> "2160p"
            else -> fallbackName.takeIf { it.isNotBlank() } ?: "Variant ${index + 1}"
        }

        val variants = mutableListOf<HlsVariant>()
        var streamInfo: String? = null
        for (rawLine in playlist.lineSequence()) {
            val line = rawLine.trim()
            if (line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) {
                streamInfo = line.substringAfter(":")
                continue
            }

            val currentInfo = streamInfo ?: continue
            if (line.isBlank() || line.startsWith("#")) continue

            val resolution = Regex("""RESOLUTION=(\d+)x(\d+)""", RegexOption.IGNORE_CASE)
                .find(currentInfo)
            val namedQuality = Regex("""(?:^|[^0-9])(2160|1440|1080|720|480|360|240)\s*p?(?:[^0-9]|$)""", RegexOption.IGNORE_CASE)
                .find(currentInfo)?.groupValues?.getOrNull(1)?.toIntOrNull()

            val resolutionQuality = resolution?.let {
                val width = it.groupValues[1].toIntOrNull() ?: 0
                val height = it.groupValues[2].toIntOrNull() ?: 0
                // The short edge maps portrait encodes such as 720x1280 to 720p.
                standardQuality(minOf(width, height))
            }?.takeIf { it != Qualities.Unknown.value }
            val namedQualityValue = namedQuality?.let { standardQuality(it) }
                ?.takeIf { it != Qualities.Unknown.value }
            val quality = resolutionQuality ?: namedQualityValue ?: Qualities.Unknown.value
            val rawName = Regex("""NAME="([^"]+)"""").find(currentInfo)?.groupValues?.getOrNull(1).orEmpty()
            val label = qualityLabel(quality, rawName, variants.size)

            val resolved = runCatching { URI(masterUrl).resolve(URI(line)).toString() }.getOrNull()
            if (!resolved.isNullOrBlank() && resolved.startsWith("http", ignoreCase = true) &&
                variants.none { it.url == resolved }) {
                variants.add(HlsVariant(resolved, quality, label))
            }
            streamInfo = null
        }

        return variants.sortedBy { if (it.quality == Qualities.Unknown.value) Int.MAX_VALUE else it.quality }
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
