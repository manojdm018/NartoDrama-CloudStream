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
    // CloudStream owns the browse UI, so use a small page picker and put the
    // genres/story types into vertically scrollable catalog rows instead of
    // requiring users to pick a short-drama source app first.
    private val browsePages = listOf(
        ("$mainUrl/?csbrowse=home") to "For You",
        ("$mainUrl/?csbrowse=stories") to "Story Types",
        ("$mainUrl/?csbrowse=genres") to "Genres",
        ("$mainUrl/?csbrowse=languages") to "Languages"
    )
    override val mainPage = mainPageOf(*browsePages.toTypedArray())

    private val episodePath = Regex("""/detail/watch/([^/?]+)/?(\d+)?""")
    private val blPattern = Regex(
        """\b(?:BL|boys[-'’ ]+love|danmei|yaoi|shounen[-_ ]?ai|male[-_ ]male romance)\b""",
        RegexOption.IGNORE_CASE
    )
    private val femaleLeadPattern = Regex(
        """\b(?:strong[-_ ]female[-_ ]lead|female[-_ ]led|female[-_ ]lead|female[-_ ]protagonist|female[-_ ]growth|heiress|villainess|boss[-_ ]lady|girlboss|girl boss|independent[-_ ]woman|female[-_ ]magistrate|lady[-_ ]magistrate|female[-_ ]general|her[-_ ]revenge|sister['’]s[-_ ]revenge|the female lead|girl power)\b""",
        RegexOption.IGNORE_CASE
    )

    // The source also uses tag-only signals like #female and #女频, without
    // spelling out "female lead" in the title or synopsis.
    private val femaleLeadTagPattern = Regex(
        """(?i)(?:#\s*(?:female|heroine|strong[-_ ]female[-_ ]lead|female[-_ ]lead)\b|["']\s*(?:female|heroine|strong[-_ ]female[-_ ]lead|female[-_ ]lead)\s*["']|女频|女主)"""
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
            !femaleLeadPattern.containsMatchIn(signals) &&
            !femaleLeadTagPattern.containsMatchIn(signals)
    }

    // Narto Drama marks dubbed audio in titles using forms such as
    // "(Dub)", "(Dubbed)", "[ENG DUB]", and "[English Dub]".
    private val dubbedTitlePattern = Regex(
        """(?i)\b(?:eng(?:lish)?[\s._-]*dub(?:bed)?|dub(?:bed|bing)?|dubbled|dublado|dublada|dublados|dubladas|doblado|doblada|doblados|dobladas|doubl[ée]s?|doublées?)\b"""
    )

    private fun isDubbedTitle(title: String): Boolean =
        dubbedTitlePattern.containsMatchIn(title)

    private fun isDubbedRelease(title: String, metadata: String = ""): Boolean =
        isDubbedTitle(title) || dubbedTitlePattern.containsMatchIn(metadata)

    private fun isDubbedRelease(title: String, item: JSONObject): Boolean {
        val explicitFlags = listOf("is_dubbed", "isDubbed", "dubbed", "has_dub", "hasDub")
        if (explicitFlags.any { key ->
                val raw = item.opt(key)
                raw == true || raw?.toString()?.equals("true", ignoreCase = true) == true
            }) return true

        val releaseMetadataKeys = listOf(
            "tags", "tag", "labels", "label", "audio_language", "audioLanguage",
            "audio_lang", "audioLang", "audio_type", "audioType", "version",
            "edition", "release_type", "releaseType", "dub_status", "dubStatus"
        )
        val releaseMetadata = releaseMetadataKeys.mapNotNull { key ->
            item.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()
        }.joinToString(" ")
        return isDubbedRelease(title, releaseMetadata)
    }

    private fun baseAudioTitle(rawTitle: String): String {
        val withoutDubMarkers = rawTitle
            .replace(
                Regex(
                    """(?i)\b(?:eng(?:lish)?[\s._-]*dub(?:bed)?|dub(?:bed|bing)?|dubbled|dublado|dublada|dublados|dubladas|doblado|doblada|doblados|dobladas|doubl[ée]s?|doublées?)\b"""
                ),
                " "
            )
            .replace(Regex("""[\[\](){}（）【】]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return withoutDubMarkers
    }

    private fun audioTitleKey(title: String): String =
        baseAudioTitle(title)
            .lowercase(Locale.ROOT)
            .replace(Regex("""[^\p{L}\p{N}]"""), "")

    // Carry an exact dubbed counterpart on the original drama URL. Remove this
    // internal marker before requesting the source site.
    private val linkedDubUrlParam = "narto_dub_url"
    private val homeDubbedCounterparts = mutableMapOf<String, String>()

    private fun stripLinkedDubParameter(url: String): String {
        return url.replace(Regex("[?&]$linkedDubUrlParam=[^&#]*"), "")
            .replace("?&", "?")
            .replace("&&", "&")
            .trimEnd('?', '&')
    }

    private fun linkedDubbedUrl(url: String): String? {
        val raw = Regex("[?&]$linkedDubUrlParam=([^&#]+)")
            .find(url)?.groupValues?.getOrNull(1) ?: return null
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
            .takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
    }

    private fun withLinkedDubVersion(item: SearchResponse, dubbedUrl: String): SearchResponse {
        val cleanUrl = stripLinkedDubParameter(item.url)
        val cleanDubUrl = stripLinkedDubParameter(dubbedUrl)
        if (cleanDubUrl.isBlank()) return item
        val separator = if (cleanUrl.contains("?")) "&" else "?"
        val linkedUrl = cleanUrl + separator + linkedDubUrlParam + "=" +
            URLEncoder.encode(cleanDubUrl, "UTF-8")
        return newTvSeriesSearchResponse(item.name, linkedUrl, TvType.TvSeries) {
            posterUrl = item.posterUrl
        }
    }

    // Multiple episode URLs or query parameters should not create duplicate cards.
    private fun catalogueIdentityKey(url: String): String {
        val cleanUrl = stripLinkedDubParameter(url)
        val slug = episodePath.find(cleanUrl)?.groupValues?.getOrNull(1)
        return if (!slug.isNullOrBlank()) "narto:" + slug.lowercase(Locale.ROOT)
        else cleanUrl.substringBefore("#").substringBefore("?").trimEnd('/').lowercase(Locale.ROOT)
    }

    private fun linkedOriginalResults(items: List<SearchResponse>): List<SearchResponse> {
        val candidates = items
            .filter { isDetailWatchUrl(it.url) && hasUsablePoster(it) && isAllowedDrama(it.name) }
            .distinctBy { it.url }
        val dubsByTitle = candidates
            .filter { isDubbedTitle(it.name) }
            .filter { audioTitleKey(it.name).isNotBlank() }
            .groupBy { audioTitleKey(it.name) }
            .mapValues { (_, versions) -> versions.first() }
        val seenTitles = HashSet<String>()
        val seenSeries = HashSet<String>()
        val results = mutableListOf<SearchResponse>()
        for (item in candidates) {
            if (isDubbedTitle(item.name)) continue
            if (!seenSeries.add(catalogueIdentityKey(item.url))) continue
            val key = audioTitleKey(item.name)
            if (key.isNotBlank() && !seenTitles.add(key)) continue
            val dub = dubsByTitle[key]
            results.add(
                if (key.isNotBlank() && dub != null && isAllowedDrama(dub.name)) {
                    withLinkedDubVersion(item, dub.url)
                } else item
            )
        }
        return results
    }

    private fun filterOriginalRows(rows: List<HomePageList>) {
        val allOriginals = rows.flatMap { it.list }
            .filterNot { isDubbedTitle(it.name) }
        val originalsByTitle = allOriginals
            .filter { audioTitleKey(it.name).isNotBlank() }
            .groupBy { audioTitleKey(it.name) }
            .mapValues { (_, candidates) -> candidates.first() }

        return rows.mapNotNull { row ->
            val visible = row.list.mapNotNull { item ->
                if (!isDubbedTitle(item.name)) item
                else originalsByTitle[audioTitleKey(item.name)]
            }.filterNot { isDubbedTitle(it.name) }
                .distinctBy { it.url }

            if (visible.isEmpty()) null
            else HomePageList(row.name, visible, row.isHorizontalImages)
        }
    }

    private suspend fun findOriginalCounterpart(dubbedTitle: String): SearchResponse? {
        val baseTitle = baseAudioTitle(dubbedTitle)
        val key = audioTitleKey(dubbedTitle)
        if (baseTitle.isBlank() || key.isBlank()) return null

        // Try the site's public search index by the undubbed title. Only accept
        // an unmarked result with the same normalized title; never guess a match.
        val searchUrl = mainUrl + "/search?q=" + URLEncoder.encode(baseTitle, "UTF-8") +
            "&limit=30&lang=en-US"
        val response = runCatching {
            app.get(searchUrl, headers = requestHeaders())
        }.getOrNull()
        if (response != null) {
            val candidates = parseSearchJson(response.text) +
                parseCards(response.document) +
                parseJsonLdCards(response.document)
            candidates.firstOrNull {
                !isDubbedTitle(it.name) && audioTitleKey(it.name) == key &&
                    isAllowedDrama(it.name)
            }?.let { return it }
        }

        val json = requestProviderJson("dc_search", "en-US", query = baseTitle)
            ?: requestProviderJson("dc_search", "en-US", query = baseTitle, includeTarget = false)
            ?: return null
        val sections = json.optJSONArray("sections") ?: return null
        for (i in 0 until sections.length()) {
            val items = sections.optJSONObject(i)?.optJSONArray("items") ?: continue
            val candidate = parseApiItems(items).firstOrNull {
                !isDubbedTitle(it.name) && audioTitleKey(it.name) == key &&
                    isAllowedDrama(it.name)
            }
            if (candidate != null) return candidate
        }
        return null
    }

    private suspend fun replaceHomeDubbedWithOriginals(rows: List<HomePageList>): List<HomePageList> {
        val allItems = rows.flatMap { it.list }.distinctBy { it.url }
        val originalsByTitle = allItems
            .filterNot { isDubbedTitle(it.name) }
            .filter { audioTitleKey(it.name).isNotBlank() }
            .groupBy { audioTitleKey(it.name) }
            .mapValues { (_, candidates) -> candidates.first() }
            .toMutableMap()
        val dubbedByTitle = allItems
            .filter { isDubbedTitle(it.name) && isAllowedDrama(it.name) }
            .filter { audioTitleKey(it.name).isNotBlank() }
            .groupBy { audioTitleKey(it.name) }
            .mapValues { (_, candidates) -> candidates.first() }
        val linkedByOriginalUrl = mutableMapOf<String, String>()
        for ((key, dubbed) in dubbedByTitle) {
            originalsByTitle[key]?.let { linkedByOriginalUrl[it.url] = dubbed.url }
        }

        // If the counterpart isn't already in another home row, query Narto
        // search for a title-exact original. Limit lookups so one home refresh
        // doesn't issue an unbounded number of extra requests.
        val missingCounterparts = allItems
            .filter { isDubbedTitle(it.name) }
            .distinctBy { audioTitleKey(it.name) }
            .filter { audioTitleKey(it.name).isNotBlank() &&
                !originalsByTitle.containsKey(audioTitleKey(it.name)) }
            .take(16)

        for (dubbed in missingCounterparts) {
            val key = audioTitleKey(dubbed.name)
            val original = runCatching { findOriginalCounterpart(dubbed.name) }.getOrNull()
            if (original != null && !isDubbedTitle(original.name) &&
                audioTitleKey(original.name) == key) {
                originalsByTitle[key] = original
                linkedByOriginalUrl[original.url] = dubbed.url
            }
        }

        for ((key, dubbed) in dubbedByTitle) {
            originalsByTitle[key]?.let { original ->
                linkedByOriginalUrl.putIfAbsent(original.url, dubbed.url)
            }
        }
        synchronized(homeDubbedCounterparts) {
            homeDubbedCounterparts.clear()
            homeDubbedCounterparts.putAll(linkedByOriginalUrl)
        }

        return rows.mapNotNull { row ->
            val visible = row.list.mapNotNull { item ->
                if (!isDubbedTitle(item.name)) item
                else originalsByTitle[audioTitleKey(item.name)]
            }
                .filterNot { isDubbedTitle(it.name) }
                .filter { hasUsablePoster(it) }
                .distinctBy { it.url }

            if (visible.isEmpty()) null
            else HomePageList(row.name, visible, row.isHorizontalImages)
        }
    }

    private fun normalizedImageUrl(rawValue: String): String? {
        var raw = rawValue.trim().replace("&amp;", "&")
        if (raw.isBlank() || raw.startsWith("data:", true) ||
            raw.startsWith("javascript:", true) || raw == "#" ||
            raw.equals("about:blank", true)) return null

        raw = raw.substringBefore(",").trim().substringBefore(" ").trim()
        if (raw.isBlank() || raw.startsWith("data:", true)) return null

        val normalized = when {
            raw.startsWith("https://", true) -> raw
            raw.startsWith("http://", true) -> "https://" + raw.substringAfter("://")
            raw.startsWith("//") -> "https:$raw"
            else -> runCatching { URI("$mainUrl/").resolve(raw).toString() }
                .getOrDefault(absoluteUrl(raw))
        }
        if (!normalized.startsWith("http", true)) return null
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        val lower = normalized.lowercase(Locale.ROOT)

        // A card's generic data-url often points to its detail page, not its poster.
        // Accept only image-looking URLs so CloudStream never tries to draw HTML as a poster.
        val hasImageExtension = Regex("""\.(?:avif|bmp|gif|jpe?g|png|webp|svg)$""")
            .containsMatchIn(path)
        val hasPosterEndpoint = Regex(
            """/(?:poster|posters|image|images|cover|covers|thumbnail|thumbnails|thumbs?)/[^/]+(?:/[^/]+)?$"""
        ).containsMatchIn(path)
        val knownImageHost = host.startsWith("img.") ||
            host.startsWith("image.") ||
            host.contains("cdn") ||
            host.contains("nartodrama-api")
        if (!hasImageExtension && !(hasPosterEndpoint && knownImageHost)) return null
        if (host == "nartodrama.com" &&
            (path.contains("/detail/watch/") || path.contains("/search/import"))) return null
        if (listOf(
                "placeholder", "no-poster", "no_poster", "no-image", "no_image",
                "noimage", "default-poster", "blank.gif", "transparent.png"
            ).any { it in lower }) return null
        return normalized
    }

    private fun posterUrlFrom(container: Element): String? {
        val nodes = mutableListOf<Element>()
        if (container.tagName() in setOf("img", "source") ||
            container.hasAttr("data-poster") ||
            container.hasAttr("data-cover") ||
            container.hasAttr("data-image") ||
            container.attr("style").contains("background", ignoreCase = true)) {
            nodes.add(container)
        }
        nodes.addAll(container.select(
            "img, source, [style*=background-image], [data-poster], [data-cover], [data-image]"
        ))
        // Never use generic data-url: it can be the drama page URL.
        val attributes = listOf(
            "data-original", "data-lazy-src", "data-src", "data-poster",
            "data-cover", "data-image", "data-srcset", "srcset", "src"
        )
        for (node in nodes) {
            for (attribute in attributes) {
                val normalized = normalizedImageUrl(node.attr(attribute))
                if (normalized != null) return normalized
            }
            val background = Regex("""(?i)background(?:-image)?\s*:[^;]*url\(['"]?([^'")]+)""")
                .find(node.attr("style"))?.groupValues?.getOrNull(1)
            val normalizedBackground = background?.let { normalizedImageUrl(it) }
            if (normalizedBackground != null) return normalizedBackground
        }
        return null
    }

    // These categories are inferred dynamically from the title and the public
    // metadata supplied with each catalogue item (tags, synopsis, plot, description).
    // Source-supplied tags are preferred; synopsis keywords are a fallback.
    private val synopsisCategoryPatterns: List<Pair<String, Regex>> = listOf(
        "Reborn & Rebirth" to Regex("""(?i)\b(?:rebirth|reborn|reincarnat\w*|resurrect\w*|previous life|past life|second life|born again)\b|重生|转世"""),
        "Time Travel" to Regex("""(?i)\b(?:time[\s-]*travel|time loop|travel\w* back|go(?:es|ing)? back in time|back to (?:the )?past|rewind|time reversal|timeline reset)\b|穿越|时空"""),
        "Revenge" to Regex("""(?i)\b(?:revenge|avenge\w*|payback|retaliat\w*|counterattack|strike\w* back|betray\w*|humiliat\w*|vendetta)\b|复仇|虐渣"""),
        "Hidden Identity" to Regex("""(?i)\b(?:hidden identity|secret identity|identity twist|double life|undercover|disguis\w*|conceal\w* (?:his|her|their) identity|pretend\w* to be|unknown heir)\b|隐藏身份|身份反转"""),
        "Zero to Hero" to Regex("""(?i)\b(?:zero to hero|underdog|rags to riches|underestimated|overlooked|weakling|lowly|rise\w* to (?:power|the top|fame))\b|逆袭"""),
        "Second Chance" to Regex("""(?i)\b(?:second chance|start\w* over|another chance|redo\w*|return\w* from the dead|given a second life|all too late|regret)\b"""),
        "System & Superpowers" to Regex("""(?i)\b(?:system|superpowers?|awaken\w*|level[\s-]*up|skill tree|quest system|powers? unlocked|chosen one|cultivation system)\b|系统|觉醒"""),
        "CEO & Billionaire" to Regex("""(?i)\b(?:ceo|billionaire|millionaire|tycoon|chairman|company heir|wealthy heir|heir to the|richest man|richest woman)\b"""),
        "Romance" to Regex("""(?i)\b(?:romance|romantic|love|lover|marriage|married|wife|husband|bride|wedding|dating|contract marriage|flash marriage|fianc[eé])\b|爱情|恋爱|婚姻"""),
        "Male Lead" to Regex("""(?i)(?:#|\b)(?:male lead|male protagonist|male[- ]led|male main character)\b|男频|男主"""),
        "Fantasy" to Regex("""(?i)\b(?:fantasy|magic|magical|dragon|demon|immortal|cultivation|werewolf|vampire|alpha king|supernatural|otherworld|realm|mythical)\b|玄幻|修仙"""),
        "Action" to Regex("""(?i)\b(?:action|fight\w*|battle\w*|martial arts|warrior|assassin|combat|soldier|weapon|warfare|tournament)\b"""),
        "Thriller & Mystery" to Regex("""(?i)\b(?:thriller|mystery|murder|investigation|detective|conspiracy|serial killer|mysterious|suspense)\b"""),
        "Crime & Mafia" to Regex("""(?i)\b(?:mafia|gangster|mob boss|underworld|crime|criminal|cartel|hitman|organized crime)\b"""),
        "Ancient / Costume" to Regex("""(?i)\b(?:ancient|dynasty|emperor|empress|imperial|palace|royal|royalty|historical|costume drama|warlord|martial arts dynasty)\b|古代|皇帝|王朝"""),
        "Family Drama" to Regex("""(?i)\b(?:family|father|mother|parent\w*|child\w*|children|twins|baby|inheritance|family drama|adopted son|adopted daughter)\b"""),
        "Comedy" to Regex("""(?i)\b(?:comedy|funny|hilarious|comic|comical|prank)\b"""),
        "Werewolf" to Regex("""(?i)\b(?:werewolf|wolf shifter|lycan|alpha mate|luna|pack leader)\b"""),
        "Vampire" to Regex("""(?i)\b(?:vampire|vampires|bloodsucker|immortal bloodline)\b"""),
        "Dragon & Mythology" to Regex("""(?i)\b(?:dragon|phoenix|olympus|mytholog\w*|deity|goddess|god king|dragon king)\b"""),
        "Apocalypse & Survival" to Regex("""(?i)\b(?:apocalypse|zombie|survival|end of the world|doomsday|disaster|infinite space)\b"""),
        "Sports & Competition" to Regex("""(?i)\b(?:athlete|soccer|football|basketball|champion|tournament|sports?|coach|team captain)\b"""),
        "Medical & Healing" to Regex("""(?i)\b(?:doctor|surgeon|medical|healer|physician|hospital|miracle healer)\b"""),
        "Police & Military" to Regex("""(?i)\b(?:police|officer|detective|soldier|commander|colonel|general|military|bodyguard)\b"""),
        "Young Adult" to Regex("""(?i)\b(?:young adult|school|classmate|student|campus|college|high school|bully)\b"""),
        "Forbidden Love" to Regex("""(?i)\b(?:forbidden love|forbidden affair|stepbrother|stepsister|affair|secret lover|love triangle)\b"""),
        "Betrayal & Redemption" to Regex("""(?i)\b(?:betrayal|betrayed|redemption|forgiveness|cheat(?:s|ed|ing)?|unfaithful|infidelity)\b"""),
        "Marriage & Divorce" to Regex("""(?i)\b(?:marriage|married|wedding|divorce|divorced|fianc[eé]|bride|groom|ex[- ]husband|ex[- ]wife|contract wife)\b""")
    )

    // Only explicit source audio/original-language fields qualify for English Shorts.
    // The page UI locale and translated English title are deliberately not used.
    private val explicitAudioLanguageKeys = listOf(
        "original_audio_language", "originalAudioLanguage", "original_language",
        "originalLanguage", "audio_language", "audioLanguage", "audio_lang",
        "audioLang", "audio_track_language", "audioTrackLanguage",
        "spoken_language", "spokenLanguage", "dialogue_language",
        "dialogueLanguage", "native_audio_language", "nativeAudioLanguage",
        "language_of_audio", "languageOfAudio", "audio_locale", "audioLocale",
        "source_language", "sourceLanguage"
    )

    private val englishOriginalPhrasePattern = Regex(
        """(?i)\b(?:english\s+(?:original\s+)?audio|original\s+audio\s+(?:is\s+)?english|original\s+language\s*[:=-]\s*english|english\s+original\s+(?:audio|language))\b"""
    )

    private fun isEnglishLanguageValue(raw: String): Boolean {
        val value = raw.trim().lowercase(Locale.ROOT)
            .replace('_', '-')
            .substringBefore(';')
            .substringBefore(',')
            .trim()
        return value in setOf(
            "en", "eng", "english", "en-us", "en-gb", "en-au", "en-ca",
            "en-nz", "en-ie", "en-za", "en-in", "en-ph", "en-001",
            "english (original)", "original english"
        )
    }

    private fun valueStrings(value: Any?): List<String> = when (value) {
        is String -> listOf(value)
        is JSONObject -> listOf("language", "name", "label", "code", "locale", "value")
            .mapNotNull { key ->
                value.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()
            }
        is JSONArray -> (0 until value.length()).flatMap { index -> valueStrings(value.opt(index)) }
        else -> emptyList()
    }

    private fun explicitAudioLanguageFromItem(item: JSONObject): String? {
        for (key in explicitAudioLanguageKeys) {
            val raw = item.opt(key) ?: continue
            if (raw == JSONObject.NULL) continue
            if (valueStrings(raw).any { isEnglishLanguageValue(it) }) return "English"
        }

        val releaseLabels = listOf(
            item.optString("audio_label"),
            item.optString("audioLabel"),
            item.optString("original_audio"),
            item.optString("originalAudio"),
            item.optString("release_label"),
            item.optString("releaseLabel"),
            item.optString("version_label"),
            item.optString("versionLabel")
        ).joinToString(" ")
        return if (englishOriginalPhrasePattern.containsMatchIn(releaseLabels)) "English" else null
    }

    private fun explicitAudioLanguageFromDocument(document: Document): String? {
        val audioMeta = document.select("meta[name], meta[property]").any { meta ->
            val key = (meta.attr("name") + " " + meta.attr("property"))
                .lowercase(Locale.ROOT)
            val audioKey = explicitAudioLanguageKeys.any { key.contains(it.lowercase(Locale.ROOT)) }
            audioKey && isEnglishLanguageValue(meta.attr("content"))
        }
        if (audioMeta) return "English"

        val languageAttributes = listOf(
            "data-original-audio-language", "data-original-language",
            "data-audio-language", "data-audio-lang", "data-audio-track-language",
            "data-spoken-language", "data-dialogue-language"
        )
        for (attribute in languageAttributes) {
            if (document.select("[$attribute]").any { isEnglishLanguageValue(it.attr(attribute)) }) {
                return "English"
            }
        }

        // Inspect structured page data only for explicit audio/original-language keys.
        val englishValue = """(?:en|eng|english|en[-_]us|en[-_]gb|en[-_]au|en[-_]ca|en[-_]nz|en[-_]in)"""
        val htmlLanguagePattern = Regex(
            """(?i)["'](?:original_audio_language|originalAudioLanguage|original_language|originalLanguage|audio_language|audioLanguage|audio_lang|audioLang|audio_track_language|audioTrackLanguage|spoken_language|spokenLanguage|dialogue_language|dialogueLanguage|native_audio_language|nativeAudioLanguage|language_of_audio|languageOfAudio|audio_locale|audioLocale|source_language|sourceLanguage)["']\s*:\s*["']""" + englishValue + """["']"""
        )
        if (htmlLanguagePattern.containsMatchIn(document.html())) return "English"

        val explicitLabelText = document.select(
            ".audio-language, .original-language, [class*=audio-language], [class*=original-audio]"
        ).text()
        if (englishOriginalPhrasePattern.containsMatchIn(explicitLabelText)) return "English"
        return null
    }

    private fun catalogueMetadata(item: JSONObject): String {
        val likelyTextFields = listOf(
            "title", "name", "description", "synopsis", "plot", "summary",
            "story", "storyline", "intro", "overview", "tags", "tag",
            "genres", "genre", "categories", "labels", "keywords",
            "audio_type", "audioType", "version", "edition"
        )
        val fields = likelyTextFields.mapNotNull { key ->
            item.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()
        }
        // Keep the full item too: sites sometimes put localized tag lists under
        // nested properties whose names are not stable between providers.
        return (fields + item.toString()).joinToString(" ")
            .replace(Regex("""<[^>]+>"""), " ")
            .replace(Regex("""\s+"""), " ")
    }

    private fun inferredCategories(title: String, metadata: String): List<String> {
        val signals = "$title $metadata"
        val categories = synopsisCategoryPatterns
            .filter { (_, pattern) -> pattern.containsMatchIn(signals) }
            .map { it.first }
            .toMutableList()

        val hasMaleLead = Regex(
            """(?i)(?:#|\b)(?:male lead|male protagonist|male[- ]led|male main character)\b|男频|男主"""
        ).containsMatchIn(signals)
        val hasRomance = synopsisCategoryPatterns.first { it.first == "Romance" }.second
            .containsMatchIn(signals)
        val hasRevenge = synopsisCategoryPatterns.first { it.first == "Revenge" }.second
            .containsMatchIn(signals)
        if (hasMaleLead && hasRomance) categories.add("Male Lead Romance")
        if (hasMaleLead && hasRevenge) categories.add("Male Lead Revenge")
        if (matchesRebornFamilyThoughtsTrope(signals)) {
            categories.add("Reborn: Family Hears His Thoughts")
        }
        return categories.distinct()
    }

    /**
     * Detect this specific story trope only when the public title/tags/synopsis
     * point to each part: a previous life, rebirth, family betrayal tied to death,
     * the family hearing his inner thoughts, and the family becoming supportive.
     */
    private fun matchesRebornFamilyThoughtsTrope(signals: String): Boolean {
        val text = signals.replace(Regex("""<[^>]+>"""), " ")
            .replace(Regex("""\s+"""), " ")
        val family = """(?:family|families|parents?|father|mother|dad|mom|brothers?|sisters?|siblings|relatives|adoptive parents?|adoptive family|grandparents?|grandfather|grandmother|clan|家人|父母|父亲|母亲|兄弟|姐妹)"""
        val hasPreviousLife = Regex(
            """(?i)\b(?:previous|past|former|prior|first|last)\s+(?:life|incarnation)\b|\bin (?:his|her|their) (?:former|past|previous) life\b|前世"""
        ).containsMatchIn(text)
        val hasRebirth = Regex(
            """(?i)\b(?:reborn|rebirth|reincarnat\w*|born again|return\w*\s+to life|regress\w*|restart\w* (?:his|her|their) life|live (?:his|her|their) life again)\b|重生|转世"""
        ).containsMatchIn(text)
        val hasDeath = Regex(
            """(?i)\b(?:die\w*|death|dead|kill\w*|murder\w*|execut\w*|poison\w*|sacrific\w*|perish\w*|demise|left\s+(?:him|her|them)\s+to\s+die|caus\w* (?:his|her|their) death|led to (?:his|her|their) death)\b|害死"""
        ).containsMatchIn(text)
        val betrayal = """(?:betray\w*|backstab\w*|turn(?:s|ed|ing)?\s+against|plot(?:s|ted|ting)?\s+against|kill\w*|murder\w*|poison\w*|frame\w*|abandon\w*|sacrific\w*|cause\w* (?:his|her|their) death|led to (?:his|her|their) death|害死|背叛)"""
        val hasFamilyBetrayal = Regex(
            """(?i)(?:$family).{0,180}$betrayal|$betrayal.{0,180}(?:$family)"""
        ).containsMatchIn(text)
        val hasInnerThoughts = Regex(
            """(?i)\b(?:inner thoughts?|thoughts? in (?:his|her|their) mind|inner voice|inner monologue|mind voice|what he thinks|his thoughts|thoughts? (?:are|can be) heard|hear(?:ing)? (?:his|her|their) thoughts|voice inside (?:his|her|their) mind|mind reading)\b|心声"""
        ).containsMatchIn(text)
        val canHear = Regex(
            """(?i)\b(?:hear\w*|listen\w*|read\w*|telepath\w*|mind[ -]?read\w*|thoughts? (?:are|can be) heard)\b|听见|听到"""
        ).containsMatchIn(text)
        val familyHearsThoughts = hasInnerThoughts && canHear &&
            Regex("(?i)$family").containsMatchIn(text)
        val familySupport = Regex("(?i)$family").containsMatchIn(text) &&
            Regex(
                """(?i)\b(?:support\w*|pamper\w*|spoil\w*|protect\w*|cherish\w*|dote\w*|treasure\w*|adore\w*|defend\w*|compensat\w*|make\s+(?:him|the boy|their son)\s+happy|treat\w*\s+him\s+(?:well|like)|care\s+for\s+him|stand\s+by\s+him|love\s+him|make\s+amends|regret\w*|overprotect\w*|favor\w*|宠爱|疼爱|保护|补偿|偏爱)\b"""
            ).containsMatchIn(text)

        return hasPreviousLife && hasRebirth && hasDeath && hasFamilyBetrayal &&
            familyHearsThoughts && familySupport
    }

    private data class SynopsisDetails(
        val synopsis: String,
        val tags: List<String>,
        val poster: String?,
        val originalAudioLanguage: String?
    )

    private val synopsisDetailCache = mutableMapOf<String, SynopsisDetails>()
    private val originalAudioLanguageByUrl = mutableMapOf<String, String>()
    private val synopsisAttempted = mutableSetOf<String>()
    private val maxSynopsisItemsPerHome = 180
    // Detail-page reads are cached and accumulated on later visits to avoid one huge refresh.
    private val maxNewSynopsisReadsPerHome = 40

    private fun extractRelatedTags(document: Document): List<String> {
        val output = linkedSetOf<String>()
        for (anchor in document.select("a[href*='/tag/'], a[href*='/genre/']")) {
            var parent: Element? = anchor
            var relatedTagRegion = false
            repeat(5) {
                val current = parent ?: return@repeat
                val label = current.text().trim()
                if (label.startsWith("Related tags:", ignoreCase = true) &&
                    label.length < 1200) {
                    relatedTagRegion = true
                }
                parent = current.parent()
            }
            if (!relatedTagRegion) continue
            val label = anchor.text().trim().trimStart('#', ' ', ':', '·')
            if (label.isNotBlank() && label.length < 60) output.add(label)
        }
        return output.toList()
    }

    private fun synopsisCandidates(document: Document): List<String> {
        val candidates = mutableListOf<String>()
        listOf(
            "meta[name=description]", "meta[property=og:description]",
            "meta[name=twitter:description]"
        ).forEach { selector ->
            document.selectFirst(selector)?.attr("content")?.trim()
                ?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        }
        listOf(
            "[itemprop=description]", ".synopsis", "#synopsis", "[class*=synopsis]",
            ".plot", "#plot", "[class*=plot]", ".storyline", ".summary", "#summary",
            ".detail-description", ".movie-description", ".drama-description",
            ".plot-summary", ".story-summary", ".drama-summary", ".description"
        ).forEach { selector ->
            document.select(selector).forEach { node ->
                node.text().trim().takeIf { it.length >= 60 }?.let { candidates.add(it) }
            }
        }
        document.select("main p, article p, p").forEach { p ->
            p.text().trim().takeIf { it.length >= 60 }?.let { candidates.add(it) }
        }

        // Some drama pages publish the actual plot only in structured JSON-LD.
        fun collectStructuredDescriptions(value: Any?) {
            when (value) {
                is JSONObject -> {
                    for (key in listOf("description", "synopsis", "plot", "storyline", "summary", "abstract")) {
                        val raw = value.opt(key)
                        if (raw is String) {
                            val candidate = raw.trim()
                            if (candidate.length >= 60 && candidate.length <= 4500) {
                                candidates.add(candidate)
                            }
                        }
                    }
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        collectStructuredDescriptions(value.opt(keys.next()))
                    }
                }
                is JSONArray -> {
                    for (index in 0 until value.length()) collectStructuredDescriptions(value.opt(index))
                }
            }
        }
        document.select("script[type=application/ld+json]").forEach { script ->
            val raw = script.data().ifBlank { script.html() }.trim()
            if (raw.isBlank()) return@forEach
            runCatching { JSONObject(raw) }.getOrNull()?.let { collectStructuredDescriptions(it) }
            runCatching { JSONArray(raw) }.getOrNull()?.let { collectStructuredDescriptions(it) }
        }

        return candidates
            .map { it.replace(Regex("""\s+"""), " ").trim() }
            .filter { it.length >= 60 && it.length <= 4500 }
            .filterNot {
                it.contains("Narto Drama is a short drama streaming platform", true) ||
                    it.contains("log in to watch without ads", true) ||
                    it.contains("you may like", true) ||
                    it.contains("start watching episode", true) ||
                    it.contains("watch now and enjoy", true) ||
                    it.contains("download our app", true) ||
                    it.contains("no. 1 short drama platform", true) ||
                    it.startsWith("watch ", true) && it.contains(" on Narto Drama ", true)
            }
            .map { candidate ->
                candidate.replace(
                    Regex("""(?i)^Synopsis\s+[^:]{1,140}:\s*"""),
                    ""
                ).trim()
            }
            .distinct()
    }

    private fun bestSynopsis(document: Document): String? {
        val explicitSelectors = listOf(
            ".synopsis", "#synopsis", "[class*=synopsis]",
            ".plot", "#plot", "[class*=plot]", ".storyline",
            ".detail-description", ".movie-description", ".drama-description",
            ".plot-summary", ".story-summary", ".drama-summary", "[itemprop=description]"
        )
        val explicit = explicitSelectors.flatMap { selector ->
            document.select(selector).map { it.text().replace(Regex("""\s+"""), " ").trim() }
        }.filter { it.length in 60..4500 }
            .filterNot {
                it.contains("Narto Drama is a short drama streaming platform", true) ||
                    it.contains("log in to watch without ads", true) ||
                    it.contains("you may like", true) ||
                    it.contains("start watching episode", true) ||
                    it.contains("download our app", true)
            }
            .distinct()
            .maxByOrNull { it.length }
        return explicit ?: synopsisCandidates(document).maxByOrNull { it.length }
    }

    private suspend fun inspectDramaSynopsis(item: SearchResponse): SynopsisDetails? {
        synchronized(synopsisDetailCache) {
            synopsisDetailCache[item.url]?.let { return it }
            if (!synopsisAttempted.add(item.url)) return null
        }

        val document = runCatching {
            app.get(item.url, headers = requestHeaders(mainUrl + "/")).document
        }.getOrNull() ?: return null

        val tags = extractRelatedTags(document)
        val synopsis = bestSynopsis(document)
            ?.take(4500)
            .orEmpty()
        val poster = sequenceOf(
            document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty(),
            document.selectFirst("meta[name=twitter:image]")?.attr("content").orEmpty(),
            document.selectFirst("img[alt]")?.absUrl("src").orEmpty()
        ).mapNotNull { normalizedImageUrl(it) }.firstOrNull()

        val audioLanguage = explicitAudioLanguageFromDocument(document)
            ?: synchronized(originalAudioLanguageByUrl) { originalAudioLanguageByUrl[item.url] }
        val details = SynopsisDetails(synopsis, tags, poster, audioLanguage)
        synchronized(synopsisDetailCache) {
            if (synopsisDetailCache.size >= 700) {
                synopsisDetailCache.clear()
                synopsisAttempted.clear()
            }
            synopsisDetailCache[item.url] = details
        }
        return details
    }

    private fun withPoster(item: SearchResponse, poster: String?): SearchResponse {
        val actualPoster = normalizedImageUrl(item.posterUrl.orEmpty()) ?: poster
        return newTvSeriesSearchResponse(item.name, item.url, TvType.TvSeries) {
            posterUrl = actualPoster
        }
    }

    private suspend fun categorizeHomeFromSynopses(rows: List<HomePageList>): List<HomePageList> {
        val rowLists = rows.map { row ->
            row.list.filter { isDetailWatchUrl(it.url) && !isDubbedTitle(it.name) }
        }
        val candidates = mutableListOf<SearchResponse>()
        val seenCandidateUrls = HashSet<String>()
        var rowOffset = 0
        // Round-robin keeps titles from smaller genre/provider rows in the synopsis pool.
        while (candidates.size < maxSynopsisItemsPerHome && rowLists.any { rowOffset < it.size }) {
            for (row in rowLists) {
                val item = row.getOrNull(rowOffset) ?: continue
                if (seenCandidateUrls.add(catalogueIdentityKey(item.url))) candidates.add(item)
                if (candidates.size >= maxSynopsisItemsPerHome) break
            }
            rowOffset++
        }

        if (candidates.isEmpty()) return rows

        // Read individual drama detail pages sequentially. The provider template
        // doesn't include kotlinx-coroutines as a direct dependency, so don't use
        // async/awaitAll here. Cache successful reads and inspect up to 40 new
        // titles per refresh, choosing uninspected URLs so repeated home visits
        // progressively classify the full 120-title candidate pool.
        val pendingReads = synchronized(synopsisDetailCache) {
            candidates.filter { item ->
                !synopsisDetailCache.containsKey(item.url) &&
                    !synopsisAttempted.contains(item.url)
            }.take(maxNewSynopsisReadsPerHome)
        }
        for (item in pendingReads) {
            runCatching { inspectDramaSynopsis(item) }
        }

        val inspected = linkedMapOf<String, Pair<SearchResponse, SynopsisDetails?>>()
        for (item in candidates) {
            val details = synchronized(synopsisDetailCache) {
                synopsisDetailCache[item.url]
            }
            inspected[item.url] = item to details
        }

        val validItems = linkedMapOf<String, SearchResponse>()
        val categoryItems = linkedMapOf<String, MutableList<SearchResponse>>()
        val categoryUrls = linkedMapOf<String, MutableSet<String>>()

        for ((url, pair) in inspected) {
            val item = pair.first
            val details = pair.second
            val tags = details?.tags.orEmpty()
            val signalText = listOf(item.name, tags.joinToString(" "), details?.synopsis.orEmpty())
                .joinToString(" ")
            val audioLanguage = details?.originalAudioLanguage
                ?: synchronized(originalAudioLanguageByUrl) { originalAudioLanguageByUrl[url] }
            if (audioLanguage == "English" && !isDubbedTitle(item.name) &&
                !isDubbedRelease(item.name, tags.joinToString(" "))) {
                val urls = categoryUrls.getOrPut("English Shorts") { linkedSetOf() }
                if (urls.add(url)) categoryItems.getOrPut("English Shorts") { mutableListOf() }.add(item)
            }
            // If the detail request succeeded, use its direct synopsis/tags to enforce
            // the user's existing BL/female-lead exclusions; on request failure, retain
            // the already-vetted listing card rather than emptying the home page.
            if (details != null && !isAllowedDrama(item.name, signalText)) continue
            if (isDubbedRelease(item.name, tags.joinToString(" "))) continue

            val fixedItem = withPoster(item, details?.poster)
            if (!hasUsablePoster(fixedItem)) continue
            validItems[url] = fixedItem

            val inferred = inferredCategories(item.name, signalText)
            for (category in inferred) {
                val urls = categoryUrls.getOrPut(category) { linkedSetOf() }
                if (urls.add(url)) categoryItems.getOrPut(category) { mutableListOf() }.add(fixedItem)
            }
        }

        // Keep the existing story/genre rows but replace entries with inspected
        // versions (which can also recover posters from og:image). A successful
        // synopsis/tag inspection may remove BL/female-led/dubbed releases.
        val cleanedRows = rows.mapNotNull { row ->
            val cleaned = row.list.mapNotNull { old ->
                val inspectedItem = validItems[old.url]
                if (inspectedItem != null) inspectedItem
                else if (
                    !isDubbedTitle(old.name) &&
                    (inspected[old.url] == null || inspected[old.url]?.second == null)
                ) old
                else null
            }.filter { hasUsablePoster(it) && !isDubbedTitle(it.name) }
                .distinctBy { it.url }

            if (cleaned.isEmpty()) null else HomePageList(row.name, cleaned)
        }.toMutableList()

        // Merge the synopsis-driven categories into the same row names already on
        // the home screen, then append genuinely new categories.
        val existingNames = cleanedRows.map { it.name.lowercase(Locale.ROOT) }.toMutableSet()
        for ((category, items) in categoryItems) {
            val mergedItems = (cleanedRows.firstOrNull {
                it.name.equals(category, ignoreCase = true)
            }?.list.orEmpty() + items).distinctBy { it.url }.filter { hasUsablePoster(it) }
            if (items.isEmpty()) continue
            val existingIndex = cleanedRows.indexOfFirst { it.name.equals(category, ignoreCase = true) }
            if (existingIndex >= 0) {
                cleanedRows[existingIndex] = HomePageList(category, mergedItems)
            } else if (existingNames.add(category.lowercase(Locale.ROOT))) {
                cleanedRows.add(HomePageList(category, items.distinctBy { it.url }))
            }
        }

        // Keep the requested trope first whenever matching dramas are available.
        val familyThoughtsIndex = cleanedRows.indexOfFirst {
            it.name.equals("Reborn: Family Hears His Thoughts", ignoreCase = true)
        }
        if (familyThoughtsIndex > 0) {
            val featuredRow = cleanedRows.removeAt(familyThoughtsIndex)
            cleanedRows.add(0, featuredRow)
        }

        return cleanedRows
    }

    private fun isDetailWatchUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val path = uri.path.orEmpty()
        return isNartoUrl(url) && path.contains("/detail/watch/", ignoreCase = true) &&
            episodePath.containsMatchIn(path)
    }

    private fun imageUrlFromItem(item: JSONObject): String? {
        val keys = listOf(
            "poster_url", "cover_url", "poster", "cover", "image_url",
            "thumbnail_url", "thumb_url", "thumbnail", "image", "artwork",
            "poster_image", "cover_image"
        )

        fun candidates(value: Any?): List<String> = when (value) {
            is String -> listOf(value)
            is JSONObject -> listOf(
                "url", "src", "large", "original", "full", "image_url",
                "poster_url", "cover_url", "path", "srcset"
            ).mapNotNull { key ->
                value.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()
            }
            is JSONArray -> (0 until value.length()).flatMap { index ->
                candidates(value.opt(index))
            }
            else -> emptyList()
        }

        for (key in keys) {
            val raw = item.opt(key) ?: continue
            val image = candidates(raw).firstNotNullOfOrNull { normalizedImageUrl(it) }
            if (image != null) return image
        }
        return null
    }

    private fun hasUsablePoster(item: SearchResponse): Boolean =
        !item.posterUrl.isNullOrBlank() && normalizedImageUrl(item.posterUrl.orEmpty()) != null

    private fun mergeHomeRows(rows: List<HomePageList>): List<HomePageList> {
        val merged = linkedMapOf<String, Pair<String, MutableList<SearchResponse>>>()
        val urlsByName = mutableMapOf<String, MutableSet<String>>()
        for (row in rows) {
            val key = row.name.trim().lowercase(Locale.ROOT)
            if (key.isBlank()) continue
            val pair = merged.getOrPut(key) { row.name to mutableListOf() }
            val urls = urlsByName.getOrPut(key) { HashSet() }
            for (item in row.list) {
                if (urls.add(catalogueIdentityKey(item.url))) pair.second.add(item)
            }
        }
        return merged.values.mapNotNull { (name, items) ->
            val postered = items.filter { hasUsablePoster(it) && !isDubbedTitle(it.name) }
            if (postered.isEmpty()) null else HomePageList(name, postered)
        }
    }

    private val streamApiBase = "https://edge.narto-drama.com"


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        synchronized(homeDubbedCounterparts) { homeDubbedCounterparts.clear() }
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
        val visibleRows = replaceHomeDubbedWithOriginals(rows)
        val classifiedRows = if (key == "home" || key == "stories") {
            categorizeHomeFromSynopses(visibleRows)
        } else {
            visibleRows
        }
        val finalRows = mergeHomeRows(classifiedRows)
        val linkedRows = finalRows.mapNotNull { row ->
            val linkedItems = row.list.map { item ->
                val dubbedUrl = synchronized(homeDubbedCounterparts) {
                    homeDubbedCounterparts[item.url]
                }
                if (dubbedUrl != null) withLinkedDubVersion(item, dubbedUrl) else item
            }.distinctBy { catalogueIdentityKey(it.url) }
            if (linkedItems.isEmpty()) null
            else HomePageList(row.name, linkedItems, row.isHorizontalImages)
        }
        return newHomePageResponse(linkedRows, hasNext = linkedRows.isNotEmpty())
    }

    private suspend fun fetchEnglishOriginalShorts(page: Int): List<SearchResponse> {
        val sourceProviders = listOf(
            "anyreel", "netshort", "dramabox", "goodshort", "reelshort",
            "shortmax", "dramawave", "moboreels", "flextv", "meloshort"
        )
        val results = linkedMapOf<String, SearchResponse>()
        for (provider in sourceProviders) {
            var json = requestProviderJson(provider, "en-US", includeTarget = false)
            if (page > 1 && json != null) {
                val sections = json.optJSONArray("sections")
                val tabs = mutableListOf<Pair<String, Int>>()
                if (sections != null) {
                    for (index in 0 until sections.length()) {
                        val key = sections.optJSONObject(index)?.optString("tab_key").orEmpty()
                        if (key.isNotBlank()) tabs.add(key to page)
                    }
                }
                if (tabs.isNotEmpty()) {
                    requestProviderJson(provider, "en-US", tabs, includeTarget = false)
                        ?.takeIf { hasItems(it) }
                        ?.let { json = it }
                }
            }

            val sections = json?.optJSONArray("sections") ?: continue
            for (sectionIndex in 0 until sections.length()) {
                val items = sections.optJSONObject(sectionIndex)?.optJSONArray("items") ?: continue
                for (itemIndex in 0 until items.length()) {
                    val sourceItem = items.optJSONObject(itemIndex) ?: continue
                    if (explicitAudioLanguageFromItem(sourceItem) != "English") continue

                    val parsed = parseApiItems(JSONArray().put(sourceItem)).firstOrNull() ?: continue
                    if (isDubbedTitle(parsed.name) || isDubbedRelease(parsed.name, sourceItem)) continue
                    synchronized(originalAudioLanguageByUrl) {
                        originalAudioLanguageByUrl[parsed.url] = "English"
                    }
                    results.putIfAbsent(parsed.url, parsed)
                    if (results.size >= 100) break
                }
                if (results.size >= 100) break
            }
            if (results.size >= 100) break
        }
        return results.values.toList()
    }

    private suspend fun fetchHomeRows(page: Int): List<HomePageList> {
        val rows = mutableListOf<HomePageList>()

        // English Shorts includes only source records that explicitly identify English
        // as the original/audio language. UI locale and English subtitles do not qualify.
        val englishOriginals = fetchEnglishOriginalShorts(page)
        if (englishOriginals.isNotEmpty()) {
            rows.add(HomePageList("English Shorts", englishOriginals))
        }

        // Prefer Narto's un-targeted provider feed, then merge in cards from its
        // server-rendered Hot catalogue. These cards contain actual drama-detail URLs,
        // poster URLs and tag chips even if the provider JSON shape changes.
        rows.addAll(fetchProviderSections("anyreel", page, "en-US", "Popular", includeMetadataCategoryRows = true))
        // Mix in other original-release catalogues so inferred story rows have broader coverage.
        for (provider in listOf("dramabox", "reelshort", "netshort", "goodshort")) {
            val label = providerChoices.firstOrNull { it.first == provider }?.second ?: provider
            rows.addAll(fetchProviderSections(provider, page, "en-US", "$label Originals"))
        }
        val baseFeeds = listOf(
            "/tag/hot" to "Popular Originals",
            "/tag/new" to "New Originals",
            "/tag/popular" to "Popular This Week",
            "/featured" to "Featured Originals"
        )
        for ((path, rowName) in baseFeeds) {
            val results = fetchCatalogPage(path, page, "en-US")
            if (results.isNotEmpty()) rows.add(HomePageList(rowName, results))
        }

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
        includeTarget: Boolean = false
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
        providerLabel: String,
        includeMetadataCategoryRows: Boolean = false
    ): List<HomePageList> {
        // IMPORTANT: omitting target_lang asks Narto Drama for its original
        // release instead of the locale-targeted dubbed variant.
        var json = requestProviderJson(provider, locale, includeTarget = false)
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
                val paged = requestProviderJson(
                    provider, locale, tabs, includeTarget = false
                )
                if (hasItems(paged)) json = paged
            }
        }

        val sections = json?.optJSONArray("sections") ?: return emptyList()
        val allResults = mutableListOf<SearchResponse>()
        val seenUrls = HashSet<String>()
        val categorized = linkedMapOf<String, MutableList<SearchResponse>>()
        val categorizedUrls = mutableMapOf<String, MutableSet<String>>()

        for (i in 0 until sections.length()) {
            val sec = sections.optJSONObject(i) ?: continue
            val items = sec.optJSONArray("items") ?: continue
            for (itemIndex in 0 until items.length()) {
                val sourceItem = items.optJSONObject(itemIndex) ?: continue
                val parsed = parseApiItems(JSONArray().put(sourceItem), includeDubbed = true)
                val result = parsed.firstOrNull() ?: continue
                explicitAudioLanguageFromItem(sourceItem)?.let { language ->
                    synchronized(originalAudioLanguageByUrl) {
                        originalAudioLanguageByUrl[result.url] = language
                    }
                }
                if (seenUrls.add(result.url)) allResults.add(result)

                if (includeMetadataCategoryRows) {
                    val signals = catalogueMetadata(sourceItem)
                    for (category in inferredCategories(result.name, signals)) {
                        val urls = categorizedUrls.getOrPut(category) { HashSet() }
                        if (urls.add(result.url)) {
                            categorized.getOrPut(category) { mutableListOf() }.add(result)
                        }
                    }
                }
            }
        }

        if (allResults.isEmpty()) return emptyList()
        val output = mutableListOf(HomePageList(providerLabel + " Originals", allResults))
        if (includeMetadataCategoryRows) {
            categorized.forEach { (category, items) ->
                if (items.isNotEmpty()) output.add(HomePageList(category, items))
            }
        }
        return output
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
                val jsonResults = parseSearchJson(response.text, includeDubbed = true)
                val originalJsonResults = linkedOriginalResults(jsonResults)
                if (originalJsonResults.isNotEmpty()) return originalJsonResults
                val pageResults = parseCards(response.document) + parseJsonLdCards(response.document)
                val originalPageResults = linkedOriginalResults(pageResults)
                if (originalPageResults.isNotEmpty()) return originalPageResults
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
                    out.addAll(parseApiItems(items, includeDubbed = true))
                }
                val originalResults = linkedOriginalResults(out)
                if (originalResults.isNotEmpty()) return originalResults
            }
        }
        return emptyList()
    }

    private fun parseSearchJson(body: String, includeDubbed: Boolean = false): List<SearchResponse> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val items = root.optJSONArray("items")
            ?: root.optJSONObject("data")?.optJSONArray("items")
            ?: root.optJSONArray("results")
            ?: return emptyList()
        return parseApiItems(items, includeDubbed)
    }

    private fun parseApiItems(items: JSONArray, includeDubbed: Boolean = false): List<SearchResponse> {
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
            if (!isDetailWatchUrl(url) || !seen.add(url)) continue
            if (!isAllowedDrama(title, catalogueMetadata(item))) continue
            val isDubbed = isDubbedRelease(title, item)
            if (isDubbed && !includeDubbed) continue
            val image = imageUrlFromItem(item) ?: continue
            // Some feeds mark a dub in metadata but omit "(Dub)" from the title.
            // Mark it for pairing; linkedOriginalResults hides it from the catalogue.
            val displayTitle = if (includeDubbed && isDubbed && !isDubbedTitle(title)) {
                "$title (Dub)"
            } else {
                title
            }
            out.add(newTvSeriesSearchResponse(displayTitle, url, TvType.TvSeries) { posterUrl = image })
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
                    if (!isDetailWatchUrl(url) || !seen.add(url)) continue
                    val image = when (val rawImage = item.opt("image")) {
                        is String -> normalizedImageUrl(rawImage)
                        is JSONObject -> normalizedImageUrl(rawImage.optString("url"))
                        is JSONArray -> {
                            var selected: String? = null
                            for (imageIndex in 0 until rawImage.length()) {
                                val entry = rawImage.opt(imageIndex)
                                val candidate = when (entry) {
                                    is String -> normalizedImageUrl(entry)
                                    is JSONObject -> normalizedImageUrl(entry.optString("url"))
                                    else -> null
                                }
                                if (candidate != null) { selected = candidate; break }
                            }
                            selected
                        }
                        else -> null
                    }
                    if (image == null) continue
                    out.add(newTvSeriesSearchResponse(title, url, TvType.TvSeries) { posterUrl = image })
                }
            }
        }
        return out
    }

    private fun deduplicateResults(items: List<SearchResponse>): List<SearchResponse> {
        val seen = HashSet<String>()
        return items.filter {
            isDetailWatchUrl(it.url) && hasUsablePoster(it) &&
                seen.add(catalogueIdentityKey(it.url))
        }
    }

    private fun originalOnlyResults(items: List<SearchResponse>): List<SearchResponse> {
        val originals = items.filterNot { isDubbedTitle(it.name) }.filter { hasUsablePoster(it) }
        val seenTitles = HashSet<String>()
        val seenUrls = HashSet<String>()
        return originals.filter { item ->
            val titleKey = audioTitleKey(item.name)
            seenUrls.add(catalogueIdentityKey(item.url)) &&
                (titleKey.isBlank() || seenTitles.add(titleKey))
        }
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
                val anchor = card.selectFirst("a[href*='/detail/watch/']")
                val rawHref = sequenceOf(
                    card.attr("data-watch-url"),
                    anchor?.attr("href").orEmpty()
                ).firstOrNull { it.contains("/detail/watch/", ignoreCase = true) }.orEmpty()
                if (rawHref.isBlank()) continue
                val href = absoluteUrl(rawHref)
                if (!isDetailWatchUrl(href)) continue
                val title = card.attr("data-movie-title").ifBlank {
                    card.selectFirst("h1, h2, h3, h4, .title, .card-title")?.text()?.trim()
                        ?: anchor?.let { titleFromLink(it) }.orEmpty()
                }.trim()
                if (title.isBlank() || !isAllowedDrama(title, card.text() + " " + card.outerHtml())) continue
                // Dub-labelled cards are retained here only long enough for the home
                // resolver to find an exact-title original; they are never displayed.
                val poster = posterUrlFrom(card)
                pending.add(Triple(title, href, poster))
            }
        } else {
            for (anchor in document.select("a[href*='/detail/watch/']")) {
                val href = absoluteUrl(anchor.attr("href"))
                if (!isDetailWatchUrl(href)) continue
                val title = titleFromLink(anchor)
                val cardContainer = anchor.parents().firstOrNull {
                    it.tagName() in setOf("article", "li", "div") &&
                        it.selectFirst("img, source, [style*=background-image], [data-poster], [data-cover], [data-image]") != null
                } ?: anchor
                val cardMetadata = cardContainer.text() + " " + cardContainer.outerHtml()
                if (title.isBlank() || !isAllowedDrama(title, cardMetadata)) continue
                val poster = posterUrlFrom(cardContainer)
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
        val linkedDubUrl = linkedDubbedUrl(url)
        val cleanUrl = stripLinkedDubParameter(url)
        val pageResponse = app.get(cleanUrl, headers = requestHeaders(mainUrl + "/"))
        val document = pageResponse.document
        val resolvedUrl = pageResponse.url.takeIf { it.isNotBlank() } ?: cleanUrl

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
        val releaseTags = document.select(".genres, .tags, .tag-links, .genre, .tag").text()
        val releaseKeywords = document.selectFirst("meta[name=keywords]")?.attr("content").orEmpty()
        if (isDubbedRelease(title, "$releaseTags $releaseKeywords")) return null

        val poster = document.selectFirst("meta[property=og:image]")?.absUrl("content")
            ?.takeIf { it.startsWith("http") }
            ?: document.selectFirst("img[alt]")?.absUrl("src")?.takeIf { it.startsWith("http") }

        val fallbackPlot = document.selectFirst("meta[name=description]")?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")
        val plot = bestSynopsis(document)
            ?.takeIf { it.length >= 60 }
            ?: fallbackPlot

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

        // Use paired episodes only when both public detail pages resolve to the same normalized title.
        val linkedDubEpisodes: Map<Int, String> = linkedDubUrl?.let { rawDubUrl ->
            val cleanDubUrl = stripLinkedDubParameter(rawDubUrl)
            val dubResponse = runCatching {
                app.get(cleanDubUrl, headers = requestHeaders(mainUrl + "/"))
            }.getOrNull()
            val dubDocument = dubResponse?.document
            val dubResolvedUrl = dubResponse?.url?.takeIf { it.isNotBlank() } ?: cleanDubUrl
            val dubTitle = dubDocument?.selectFirst("h1")?.text()?.trim()
                ?.replace(Regex("""\s+Episode\s+\d+.*$""", RegexOption.IGNORE_CASE), "")
                ?: dubDocument?.selectFirst("meta[property=og:title]")?.attr("content")
                    ?.replace(Regex("""\s+Episode\s+\d+.*$""", RegexOption.IGNORE_CASE), "")
                    ?.replace(Regex("""\s+-\s+Free Streaming.*$""", RegexOption.IGNORE_CASE), "")
                    ?.trim().orEmpty()
            val dubSlug = episodePath.find(dubResolvedUrl)?.groupValues?.getOrNull(1)
            if (dubDocument != null && audioTitleKey(dubTitle) == audioTitleKey(title) &&
                dubSlug != null && isAllowedDrama(dubTitle)) {
                val parsedDubEpisodes = dubDocument.select("a[href]").mapNotNull { anchor ->
                    val episodeUrl = anchor.absUrl("href")
                    val match = Regex(
                        """/detail/watch/${Regex.escape(dubSlug)}/(\d+)(?:[/?#].*)?$""",
                        RegexOption.IGNORE_CASE
                    ).find(episodeUrl) ?: return@mapNotNull null
                    if (!episodeUrl.startsWith(mainUrl)) return@mapNotNull null
                    val number = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                    number to episodeUrl.substringBefore("#")
                }.distinctBy { it.first }.sortedBy { it.first }.toMap().toMutableMap()
                if (parsedDubEpisodes.isEmpty()) {
                    val number = episodePath.find(dubResolvedUrl)?.groupValues?.getOrNull(2)?.toIntOrNull()
                    if (number != null) parsedDubEpisodes[number] = dubResolvedUrl
                }
                parsedDubEpisodes
            } else emptyMap()
        } ?: emptyMap()

        val regularEpisodeLinks = if (episodeLinks.isNotEmpty()) {
            episodeLinks
        } else {
            val firstUrl = if (currentNumber != null) resolvedUrl
                else "$mainUrl/detail/watch/$slug/1?lang=en-US"
            listOf((currentNumber ?: 1) to firstUrl)
        }
        val hasLinkedDub = linkedDubEpisodes.isNotEmpty()
        val episodes = regularEpisodeLinks.flatMap { (number, episodeUrl) ->
            val dubEpisodeUrl = linkedDubEpisodes[number]
            if (hasLinkedDub && dubEpisodeUrl != null) {
                listOf(
                    newEpisode(episodeUrl) {
                        name = "Episode $number · SUB"
                        episode = number
                        posterUrl = poster
                    },
                    newEpisode(dubEpisodeUrl) {
                        name = "Episode $number · DUB"
                        episode = number
                        posterUrl = poster
                    }
                )
            } else {
                listOf(newEpisode(episodeUrl) {
                    name = if (hasLinkedDub) "Episode $number · SUB" else "Episode $number"
                    episode = number
                    posterUrl = poster
                })
            }
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
                if (!emittedMediaUrls.add(mediaUrl)) return false
                callback(
                    newExtractorLink(
                        name,
                        "$label • Adaptive quality (use player quality selector)",
                        mediaUrl,
                        ExtractorLinkType.M3U8
                    ) {
                        referer = data
                        quality = Qualities.Unknown.value
                    }
                )
                return true
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

        // Prefer the refresh-source URL and expose one source; HLS master playlists
        // retain their own adaptive quality levels for the player selector.
        val prioritizedCandidates = candidates.entries.sortedBy { entry ->
            when {
                entry.value.contains(".m3u8", true) ||
                    (payload?.optBoolean("direct_play_is_hls", false) == true &&
                        entry.key.contains("Direct")) -> 0
                entry.value.contains(".mpd", true) -> 1
                else -> 2
            }
        }
        for ((label, mediaUrl) in prioritizedCandidates) {
            val type = when {
                mediaUrl.contains(".m3u8", true) ||
                    (payload?.optBoolean("direct_play_is_hls", false) == true && label.contains("Direct")) ->
                    ExtractorLinkType.M3U8
                mediaUrl.contains(".mpd", true) -> ExtractorLinkType.DASH
                else -> ExtractorLinkType.VIDEO
            }
            if (emitMediaLink(mediaUrl, label, type)) {
                found = true
                break
            }
        }

        // Use visible HTML5 media only when the preferred refresh source was unavailable.
        if (!found) {
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
                    break
                }
            }
        }

        // Last server fallback: try embeds one by one and forward only their first link.
        if (!found) {
            for (frame in document.select("iframe[src]")) {
                val embedUrl = frame.absUrl("src")
                if (!embedUrl.startsWith("http")) continue
                var emittedByThisExtractor = false
                val singleLinkCallback: (ExtractorLink) -> Unit = { link ->
                    if (!emittedByThisExtractor) {
                        callback(link)
                        emittedByThisExtractor = true
                    }
                }
                val extracted = loadExtractor(embedUrl, data, subtitleCallback, singleLinkCallback)
                if (extracted && emittedByThisExtractor) {
                    found = true
                    break
                }
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
                    break
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
