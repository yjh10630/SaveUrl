package com.jinscompany.saveurl.data.source.linkpreview

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * 링크 미리보기 수집기 (Android 의존성 없음).
 *
 * 1) 입력 정리 + 도메인별 URL 재작성 (blog.naver → m.blog, m.coupang → www.coupang, http → https)
 * 2) oEmbed 지원 도메인(YouTube/TikTok/Vimeo)은 oEmbed 우선
 * 3) 도메인 전략표의 UA 를 순서대로 시도. 리다이렉트는 같은 UA 로 직접 따라가며(쿠키 유지, https 강제),
 *    JS/meta refresh 리다이렉트도 따라간다.
 * 4) 단축 URL 등으로 최종 도메인이 바뀌어 전략이 달라지면, 최종 URL 기준으로 한 번 더 전략을 적용한다.
 * 5) og → twitter → JSON-LD → `<title>` 순으로 추출, 봇 차단/로그인 페이지는 버린다.
 *
 * 호출마다 시간 예산(deadline)을 갖는 상태 객체이므로, 요청 1건당 인스턴스 1개를 만들어 쓴다.
 */
class LinkPreviewFetcher(
    private val http: HttpGetter,
    /**
     * 차단(403/429/로그인 리다이렉트)됐을 때 같은 UA 로 한 번 더 시도할 보조 전송 계층.
     * 봇 차단 솔루션은 TLS/HTTP2 지문까지 보므로 HTTP/2(OkHttp) 와 HTTP/1.1(HttpURLConnection) 의
     * 통과 여부가 사이트마다 다르다 (예: G마켓은 HTTP/2 만, 오늘의집은 HTTP/1.1 만 통과 — 2026-10 JVM 실측).
     */
    private val fallbackHttp: HttpGetter? = null,
    private val deviceUserAgent: () -> String,
    private val totalBudgetMs: Long = 25_000,
    private val isCancelled: () -> Boolean = { false },
    private val log: (String) -> Unit = {},
) {
    private class Page(val url: String, val status: Int, val doc: Document?, val contentType: String?) {
        val blocked: Boolean = doc != null && HtmlMetaExtractor.isBlockedPage(doc, url, status)
    }

    private var deadline = 0L

    fun fetch(input: String): LinkMeta {
        deadline = System.currentTimeMillis() + totalBudgetMs
        var target = LinkFetchRules.rewrite(LinkFetchRules.normalizeInput(input))

        tryOEmbed(target)?.let { return it }

        var strategy = LinkFetchRules.strategyFor(target)
        var profiles = strategy.userAgents
        var switched = false
        var best: LinkMeta? = null
        var i = 0
        while (i < profiles.size) {
            if (timeUp()) break
            val profile = profiles[i++]
            var page = fetchPage(target, uaOf(profile), http)
            if (fallbackHttp != null && (page == null || page.blocked) && !timeUp()) {
                log("[${profile.name}] primary transport blocked/failed (${page?.status}) → fallback transport")
                val alt = fetchPage(target, uaOf(profile), fallbackHttp)
                if (alt != null && (page == null || !alt.blocked)) page = alt
            }
            if (page == null) continue

            // 단축 URL/리다이렉트로 최종 도메인 전략이 달라졌다면 최종 URL 기준으로 재시작
            if (!switched) {
                val newStrategy = LinkFetchRules.strategyFor(page.url)
                val needOEmbed = OEmbed.endpointFor(page.url) != null && OEmbed.endpointFor(target) == null
                if (newStrategy != strategy || needOEmbed) {
                    switched = true
                    log("redirected ${target} -> ${page.url}, switch strategy")
                    target = page.url
                    strategy = newStrategy
                    tryOEmbed(target)?.let { return it }
                    profiles = newStrategy.userAgents
                    i = 0
                    if (profiles.firstOrNull() != profile) continue
                    i = 1 // 현재 페이지가 새 전략의 첫 UA 로 받은 것이므로 그대로 평가
                }
            }

            val doc = page.doc
            if (doc == null) {
                // 이미지/PDF 등 HTML 이 아닌 응답
                val name = page.url.substringAfterLast('/').substringBefore('?').ifEmpty { null }
                val meta = LinkMeta(
                    url = page.url, title = name,
                    imageUrl = if (page.contentType?.startsWith("image/") == true) page.url else null,
                    siteName = KnownSites.nameOf(page.url) ?: HtmlMetaExtractor.hostOf(page.url),
                    titleFromMeta = name != null, method = "non-html",
                )
                return meta
            }
            val blocked = page.blocked
            val meta = HtmlMetaExtractor.extract(doc, page.url, method = "html:${profile.name}")
            log("[${profile.name}] ${page.status} ${page.url} blocked=$blocked title=${meta.title}")
            if (!blocked && meta.isGood) return meta
            if (!blocked && meta.hasTitle && (best == null || meta.score > best.score)) best = meta
        }
        return best ?: LinkMeta(url = target, siteName = KnownSites.nameOf(target), method = "failed")
    }

    private fun tryOEmbed(url: String): LinkMeta? {
        val endpoint = OEmbed.endpointFor(url) ?: return null
        return try {
            val res = http.get(endpoint, OEmbed.USER_AGENT, emptyMap())
            if (res.status != 200) return null
            OEmbed.parse(String(res.body, Charsets.UTF_8), url)?.takeIf { it.isGood }
        } catch (e: Exception) {
            log("oembed failed: ${e.message}")
            null
        }
    }

    private fun uaOf(profile: UaProfile): String = profile.fixed ?: deviceUserAgent()

    private fun timeUp(): Boolean = isCancelled() || System.currentTimeMillis() > deadline

    /** 같은 UA 로 HTTP/JS 리다이렉트를 따라가며 최종 페이지를 가져온다. */
    private fun fetchPage(start: String, ua: String, http: HttpGetter): Page? {
        var url = start
        val cookies = mutableMapOf<String, String>()
        val visited = mutableSetOf<String>()
        var jsHops = 0
        repeat(10) {
            if (timeUp()) return null
            visited += url
            val res = try {
                http.get(url, ua, cookies)
            } catch (e: Exception) {
                log("request failed $url: ${e.javaClass.simpleName} ${e.message}")
                return null
            }
            cookies.putAll(res.cookies)

            if (res.status in 300..399 && !res.location.isNullOrBlank()) {
                val next = LinkFetchRules.resolveLocation(url, res.location) ?: return null
                val rewritten = LinkFetchRules.rewrite(next)
                url = if (rewritten !in visited) rewritten else next
                return@repeat
            }

            val ct = res.contentType?.lowercase()
            if (ct != null && !ct.contains("html") && !ct.contains("xml") && !ct.startsWith("text/")) {
                return Page(url, res.status, null, res.contentType)
            }
            val html = CharsetDecoder.decode(res.body, res.contentType)
            val doc = Jsoup.parse(html, url)

            if (jsHops < 2) {
                val next = ClientRedirectDetector.find(doc, html, url)
                if (next != null && next != url) {
                    jsHops++
                    val rewritten = LinkFetchRules.rewrite(next)
                    url = if (rewritten !in visited) rewritten else next
                    return@repeat
                }
            }
            return Page(url, res.status, doc, res.contentType)
        }
        return null
    }
}
