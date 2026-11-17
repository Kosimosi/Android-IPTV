package com.retro.iptv

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale

data class LiveContentCard(
    val id: String,
    val badge: String,
    val title: String,
    val channel: Channel
)

data class LiveContentSnapshot(
    val headlines: List<String>,
    val cards: List<LiveContentCard>
)

/** Free, bounded live-content discovery isolated from playback. */
object LiveContentRepository {
    private const val HIGHLIGHTS_URL =
        "https://raw.githubusercontent.com/Kosimosi/Android-IPTV/main/highlights.json"
    private const val EVENTS_URL =
        "https://raw.githubusercontent.com/Kosimosi/Android-IPTV/main/live_events.json"
    private const val CRICKET_URL = "https://www.espncricinfo.com/rss/livescores.xml"
    private val footballFeeds = listOf(
        "https://feeds.bbci.co.uk/sport/football/rss.xml",
        "https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/news"
    )

    private val newsFeeds = listOf(
        "https://news.google.com/rss?hl=bn&gl=BD&ceid=BD:bn",
        "https://news.google.com/rss?hl=en-BD&gl=BD&ceid=BD:en",
        "https://www.jagonews24.com/rss/rss.xml",
        "https://www.banglatribune.com/feed",
        "https://www.risingbd.com/rss/rss.xml"
    )

    suspend fun load(channels: List<Channel>): LiveContentSnapshot = withContext(Dispatchers.IO) {
        coroutineScope {
            val headlineJobs = newsFeeds.map { feed ->
                async { parseRssTitles(fetchText(feed, 700_000), 12) }
            }
            val highlightsJob = async { parseManualHeadlines(fetchText(HIGHLIGHTS_URL, 250_000)) }
            val eventsJob = async { parseOverrideEvents(fetchText(EVENTS_URL, 250_000), channels, System.currentTimeMillis()) }
            val cricketJob = async { parseCricketEvents(fetchText(CRICKET_URL, 500_000), channels) }
            val footballJobs = footballFeeds.map { feed -> async {
                val body = fetchText(feed, 500_000)
                if (feed.contains("espn.com")) parseEspnFootball(body, channels) else parseFootballEvents(body, channels)
            } }

            val rssHeadlines = headlineJobs.awaitAll().flatten()
            val headlines = (highlightsJob.await() + rssHeadlines)
                .map(::cleanHeadline)
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .take(30)

            val cards = (eventsJob.await() + cricketJob.await() + footballJobs.awaitAll().flatten())
                .distinctBy { it.id }
                .take(8)
            LiveContentSnapshot(headlines, cards)
        }
    }

    private fun parseManualHeadlines(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONObject(text).optJSONArray("headlines") ?: JSONArray()
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
    }

    private fun parseOverrideEvents(text: String?, channels: List<Channel>, now: Long): List<LiveContentCard> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = if (text.trimStart().startsWith("[")) JSONArray(text)
                else JSONObject(text).optJSONArray("events") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val event = array.optJSONObject(index) ?: continue
                    val start = parseIsoTime(event.optString("startUtc"))
                    val end = parseIsoTime(event.optString("endUtc"))
                    if (start <= 0L || end <= start || now < start || now > end) continue
                    val keywords = event.optJSONArray("channelKeywords") ?: JSONArray()
                    val names = (0 until keywords.length())
                        .mapNotNull { keywords.optString(it).trim().takeIf(String::isNotBlank) }
                    val title = event.optString("title").trim().takeIf(String::isNotBlank) ?: continue
                    val rawBadge = event.optString("badge").trim().ifBlank { "LIVE EVENT" }
                    val badge = when {
                        rawBadge.contains("drama", true) || rawBadge.contains("pak", true) -> "PAK DRAMA"
                        rawBadge.contains("football", true) -> "LIVE FOOTBALL"
                        rawBadge.contains("cricket", true) -> "LIVE CRICKET"
                        else -> rawBadge.uppercase(Locale.ROOT)
                    }
                    val channelNames = when (badge) {
                        "PAK DRAMA" -> listOf("HUM TV", "ARY Digital", "Geo Entertainment", "Geo TV", "Green TV")
                        "LIVE FOOTBALL" -> listOf("Sony Sports Ten 1", "Sony Sports Ten 2", "T Sports")
                        else -> names
                    }
                    val channel = findOnlineChannel(channels, channelNames.ifEmpty { names }) ?: continue
                    val id = event.optString("id").trim().ifBlank { "event-" + index }
                    add(LiveContentCard(id, badge, title, channel))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseCricketEvents(text: String?, channels: List<Channel>): List<LiveContentCard> {
        if (text.isNullOrBlank()) return emptyList()
        val titles = parseRssTitles(text, 30)
        return titles.mapIndexedNotNull { index, rawTitle ->
            val title = cleanHeadline(rawTitle)
            if (title.isBlank()) return@mapIndexedNotNull null
            val priorityTerms = listOf("bangladesh", "bpl", "icc", "asia cup", "ipl")
            if (priorityTerms.none { title.contains(it, true) }) return@mapIndexedNotNull null
            val keywords = when {
                title.contains("bangladesh", true) || title.contains("bpl", true) ->
                    listOf("T Sports", "GTV", "Gazi TV", "Nagorik TV", "Star Sports")
                else -> listOf("Star Sports 1", "Star Sports Select", "T Sports", "GTV", "Sony Sports Ten")
            }
            val channel = findOnlineChannel(channels, keywords) ?: return@mapIndexedNotNull null
            LiveContentCard(
                id = "espn-live-" + title.hashCode() + "-" + index,
                badge = "LIVE CRICKET",
                title = title,
                channel = channel
            )
        }
    }

    private fun parseFootballEvents(text: String?, channels: List<Channel>): List<LiveContentCard> {
        if (text.isNullOrBlank()) return emptyList()
        return parseRssTitles(text, 35).mapIndexedNotNull { index, raw ->
            val title = cleanHeadline(raw)
            if (title.isBlank()) return@mapIndexedNotNull null
            if (listOf("football", "premier league", "champions league", "fa cup", "la liga", "world cup", "match").none { title.contains(it, true) }) return@mapIndexedNotNull null
            val channel = findOnlineChannel(channels, listOf("Sony Sports Ten 1", "Sony Sports Ten 2", "T Sports")) ?: return@mapIndexedNotNull null
            LiveContentCard("bbc-football-" + title.hashCode() + "-" + index, "LIVE FOOTBALL", title, channel)
        }
    }

    private fun parseEspnFootball(text: String?, channels: List<Channel>): List<LiveContentCard> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(text)
            val articles = root.optJSONArray("articles") ?: JSONArray()
            (0 until articles.length()).mapNotNull { index ->
                val title = cleanHeadline(articles.optJSONObject(index)?.optString("headline").orEmpty())
                if (title.isBlank()) return@mapNotNull null
                val channel = findOnlineChannel(channels, listOf("Sony Sports Ten 1", "Sony Sports Ten 2", "T Sports")) ?: return@mapNotNull null
                LiveContentCard("espn-football-" + title.hashCode(), "LIVE FOOTBALL", title, channel)
            }
        }.getOrDefault(emptyList())
    }

    private fun findOnlineChannel(channels: List<Channel>, keywords: List<String>): Channel? {
        for (keyword in keywords) {
            val match = channels.firstOrNull {
                it.status == ChannelStatus.ONLINE && it.title.contains(keyword, ignoreCase = true)
            }
            if (match != null) return match
        }
        return null
    }

    private fun parseRssTitles(xml: String?, limit: Int): List<String> {
        if (xml.isNullOrBlank()) return emptyList()
        return runCatching {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            val results = mutableListOf<String>()
            var insideItem = false
            var itemTitle: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && results.size < limit) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name.lowercase(Locale.ROOT)) {
                        "item", "entry" -> { insideItem = true; itemTitle = null }
                        "title" -> if (insideItem) itemTitle = parser.nextText()
                    }
                    XmlPullParser.END_TAG -> when (parser.name.lowercase(Locale.ROOT)) {
                        "item", "entry" -> {
                            itemTitle?.trim()?.takeIf(String::isNotBlank)?.let(results::add)
                            insideItem = false
                            itemTitle = null
                        }
                    }
                }
                event = parser.next()
            }
            results
        }.getOrDefault(emptyList())
    }

    private fun cleanHeadline(value: String): String =
        Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun parseIsoTime(value: String): Long =
        runCatching { Instant.parse(value.trim()).toEpochMilli() }.getOrDefault(0L)

    private fun fetchText(url: String, maxBytes: Int): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_500
                readTimeout = 4_500
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "RETROTV/2.1 (Android)")
                setRequestProperty("Accept", "application/rss+xml, application/xml, application/json, text/xml, */*")
            }
            if (connection.responseCode !in 200..299 || connection.contentLengthLong > maxBytes) return null
            connection.inputStream.use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(4096)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxBytes) return null
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
