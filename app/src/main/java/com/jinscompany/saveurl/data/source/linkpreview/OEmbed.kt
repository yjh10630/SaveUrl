package com.jinscompany.saveurl.data.source.linkpreview

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * 인증 없이 쓸 수 있는 oEmbed 엔드포인트.
 * (Instagram/Facebook oEmbed 는 2020년부터 Meta 앱 토큰이 필요하므로 제외 — 대신 크롤러 UA 로 og 태그 수집)
 */
object OEmbed {

    /**
     * oEmbed 요청용 UA. 공개 API 이므로 앱을 정직하게 밝힌다.
     * (TikTok oEmbed 는 브라우저 UA 에 429/503 을 주고, 비브라우저 UA 에는 200 을 준다 — 2026-10 실측)
     */
    const val USER_AGENT = "SaveUrl/1.1 (Android; +https://play.google.com/store/apps/details?id=com.jinscompany.saveurl)"

    private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")
    private val TIKTOK_HOSTS = setOf("tiktok.com", "www.tiktok.com", "m.tiktok.com")
    private val VIMEO_HOSTS = setOf("vimeo.com", "www.vimeo.com")

    fun endpointFor(url: String): String? {
        val host = HtmlMetaExtractor.hostOf(url) ?: return null
        val enc = URLEncoder.encode(url, "UTF-8")
        return when {
            host in YOUTUBE_HOSTS -> {
                // music.youtube.com 은 oEmbed 미지원 → www 로 변환
                val target = if (host == "music.youtube.com") url.replaceFirst("music.youtube.com", "www.youtube.com") else url
                "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(target, "UTF-8")
            }
            host in TIKTOK_HOSTS && url.contains("/video/") -> "https://www.tiktok.com/oembed?url=$enc"
            host in VIMEO_HOSTS -> "https://vimeo.com/api/oembed.json?url=$enc"
            else -> null
        }
    }

    /** oEmbed JSON → LinkMeta. 제목이 없으면 null */
    fun parse(json: String, pageUrl: String): LinkMeta? {
        val obj: JsonObject = try {
            JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject ?: return null
        } catch (e: Exception) {
            return null
        }
        fun s(key: String): String? = obj[key]?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifEmpty { null }

        val author = s("author_name")
        // TikTok 은 제목 = 캡션. 캡션이 비어있으면 작성자명으로 대체
        val title = s("title") ?: author ?: return null
        val htmlText = s("html")?.let { Jsoup.parse(it).select("p").firstOrNull()?.text() }
        val description = when {
            author != null && htmlText != null && htmlText != title -> "$author · $htmlText"
            else -> author ?: htmlText
        }
        return LinkMeta(
            url = pageUrl,
            title = title,
            description = description,
            imageUrl = s("thumbnail_url"),
            siteName = s("provider_name") ?: KnownSites.nameOf(pageUrl),
            titleFromMeta = true,
            method = "oembed",
        )
    }
}
