package com.jinscompany.saveurl.data.source.linkpreview

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

/**
 * HTML 문서에서 미리보기 정보(제목/설명/이미지/사이트명)를 추출한다.
 *
 * 우선순위
 * - 제목: og:title → twitter:title → JSON-LD(name/headline) → `<title>` → h1
 * - 설명: og:description → twitter:description → meta description → JSON-LD description
 * - 이미지: og:image(:secure_url/:url) → twitter:image(:src) → JSON-LD image → link[rel=image_src] → itemprop=image
 * - 사이트명: og:site_name → 알려진 도메인 이름 → application-name → JSON-LD publisher → 호스트
 *
 * 리플렉션을 쓰지 않으므로 (Gson JsonParser 트리 API) R8 keep 규칙이 필요 없다.
 */
object HtmlMetaExtractor {

    fun extract(html: String, pageUrl: String, method: String = "html"): LinkMeta =
        extract(Jsoup.parse(html, pageUrl), pageUrl, method)

    fun extract(doc: Document, pageUrl: String, method: String = "html"): LinkMeta {
        val ld = JsonLd.find(doc)

        val metaTitle = meta(doc, "og:title") ?: meta(doc, "twitter:title") ?: ld?.title
        val fallbackTitle = doc.title().trim().ifEmpty { null } ?: doc.selectFirst("h1")?.text()?.trim()?.ifEmpty { null }
        val title = metaTitle ?: fallbackTitle

        val description = meta(doc, "og:description")
            ?: meta(doc, "twitter:description")
            ?: meta(doc, "description")
            ?: ld?.description

        val image = metaUrl(doc, "og:image")
            ?: metaUrl(doc, "og:image:secure_url")
            ?: metaUrl(doc, "og:image:url")
            ?: metaUrl(doc, "twitter:image")
            ?: metaUrl(doc, "twitter:image:src")
            ?: ld?.image?.let { absolutize(it, pageUrl) }
            ?: doc.selectFirst("link[rel=image_src]")?.let { absolutize(it.attr("href"), pageUrl) }
            ?: doc.selectFirst("meta[itemprop=image]")?.let { absolutize(it.attr("content"), pageUrl) }

        val siteName = meta(doc, "og:site_name")
            ?: KnownSites.nameOf(pageUrl)
            ?: meta(doc, "application-name")
            ?: ld?.publisher
            ?: hostOf(pageUrl)?.removePrefix("www.")

        val canonical = listOfNotNull(
            meta(doc, "og:url"),
            doc.selectFirst("link[rel=canonical]")?.attr("href"),
        ).mapNotNull { absolutize(it, pageUrl) }
            .firstOrNull { isSameSite(it, pageUrl) }
            ?: pageUrl

        return LinkMeta(
            url = canonical,
            title = title?.let(::clean),
            description = description?.let(::clean),
            imageUrl = image,
            siteName = siteName?.let(::clean),
            titleFromMeta = metaTitle != null,
            method = method,
        )
    }

    /**
     * 봇 차단/로그인/에러 페이지인지 판정. 이런 페이지의 `<title>` 은 미리보기로 쓰면 안 된다.
     */
    fun isBlockedPage(doc: Document, pageUrl: String, statusCode: Int = 200): Boolean {
        if (statusCode == 403 || statusCode == 429 || statusCode >= 500) return true
        val host = hostOf(pageUrl).orEmpty()
        if (host.startsWith("nid.naver.com") || host.startsWith("accounts.") || host.startsWith("login.")) return true
        val title = doc.title().trim()
        if (BLOCK_TITLES.any { title.contains(it, ignoreCase = true) }) return true
        val head = doc.body()?.text()?.take(800).orEmpty()
        return BLOCK_BODY_TEXTS.any { head.contains(it) }
    }

    private val BLOCK_TITLES = listOf(
        "Access Denied", "Attention Required", "Just a moment", "Security Check", "Robot Check",
        "NAVER 로그인", "[에러]", "에러페이지", "403 Forbidden", "Too Many Requests", "잠시만 기다려",
    )
    private val BLOCK_BODY_TEXTS = listOf(
        "봇의 동작과 유사", "자동화된 요청", "비정상적인 접근", "Please enable JS and disable any ad blocker",
    )

    // ---------------------------------------------------------------------------------

    private fun meta(doc: Document, key: String): String? =
        doc.select("meta[property=$key], meta[name=$key]")
            .asSequence()
            .map { it.attr("content").trim() }
            .firstOrNull { it.isNotEmpty() }

    private fun metaUrl(doc: Document, key: String): String? =
        doc.select("meta[property=$key], meta[name=$key]")
            .asSequence()
            .mapNotNull { el ->
                val abs = el.absUrl("content")
                if (abs.isNotEmpty()) abs else absolutize(el.attr("content"), doc.location())
            }
            .firstOrNull { it.isNotEmpty() }

    internal fun absolutize(raw: String?, base: String): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.startsWith("data:")) return null
        return try {
            val resolved = when {
                value.startsWith("//") -> "https:$value"
                value.startsWith("http://") || value.startsWith("https://") -> value
                else -> URI(base).resolve(value.replace(" ", "%20")).toString()
            }
            if (resolved.startsWith("http")) resolved else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isSameSite(candidate: String, pageUrl: String): Boolean {
        val a = rootDomain(hostOf(candidate)) ?: return false
        val b = rootDomain(hostOf(pageUrl)) ?: return false
        return a == b
    }

    private fun rootDomain(host: String?): String? {
        if (host == null) return null
        val parts = host.split('.')
        if (parts.size <= 2) return host
        // co.kr / or.kr 같은 2단계 TLD 처리
        val twoLevel = parts.takeLast(2).joinToString(".")
        return if (twoLevel in setOf("co.kr", "or.kr", "ne.kr", "go.kr", "co.jp", "co.uk", "com.cn")) {
            parts.takeLast(3).joinToString(".")
        } else twoLevel
    }

    internal fun hostOf(url: String?): String? = try {
        url?.let { URI(it).host?.lowercase() }
    } catch (e: Exception) {
        null
    }

    private fun clean(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    // ---------------------------------------------------------------------------------

    internal data class LdInfo(val title: String?, val description: String?, val image: String?, val publisher: String?)

    /** schema.org JSON-LD (Product / Article / VideoObject 등) 파서 */
    internal object JsonLd {
        private val PREFERRED_TYPES = listOf(
            "Product", "NewsArticle", "Article", "BlogPosting", "VideoObject", "Recipe",
            "Movie", "Book", "Event", "Place", "LocalBusiness", "Restaurant", "WebPage",
        )

        fun find(doc: Document): LdInfo? {
            val objects = mutableListOf<JsonObject>()
            doc.select("script[type=application/ld+json]").forEach { script ->
                val json = script.data().trim()
                if (json.isEmpty()) return@forEach
                try {
                    collect(JsonParser.parseString(json), objects)
                } catch (e: Exception) {
                    // 깨진 JSON-LD 는 무시
                }
            }
            if (objects.isEmpty()) return null
            val best = PREFERRED_TYPES.firstNotNullOfOrNull { type -> objects.firstOrNull { typeOf(it).contains(type) } }
                ?: objects.first()
            val info = LdInfo(
                title = str(best, "name") ?: str(best, "headline"),
                description = str(best, "description"),
                image = image(best["image"]) ?: image(best["thumbnailUrl"]),
                publisher = (best["publisher"] as? JsonObject)?.let { str(it, "name") },
            )
            return if (info.title == null && info.image == null && info.description == null) null else info
        }

        private fun collect(el: JsonElement, out: MutableList<JsonObject>) {
            when {
                el.isJsonArray -> el.asJsonArray.forEach { collect(it, out) }
                el.isJsonObject -> {
                    val obj = el.asJsonObject
                    out.add(obj)
                    obj["@graph"]?.let { collect(it, out) }
                }
            }
        }

        private fun typeOf(obj: JsonObject): List<String> = when (val t = obj["@type"]) {
            null -> emptyList()
            is JsonArray -> t.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
            else -> if (t.isJsonPrimitive) listOf(t.asString) else emptyList()
        }

        private fun str(obj: JsonObject, key: String): String? {
            val v = obj[key] ?: return null
            return if (v.isJsonPrimitive) v.asString.trim().ifEmpty { null } else null
        }

        private fun image(el: JsonElement?): String? = when {
            el == null || el.isJsonNull -> null
            el.isJsonPrimitive -> el.asString.trim().ifEmpty { null }
            el.isJsonArray -> el.asJsonArray.firstNotNullOfOrNull { image(it) }
            el.isJsonObject -> el.asJsonObject.let { o -> o["url"]?.let { image(it) } ?: o["contentUrl"]?.let { image(it) } }
            else -> null
        }
    }
}
