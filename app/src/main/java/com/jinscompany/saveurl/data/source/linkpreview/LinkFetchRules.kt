package com.jinscompany.saveurl.data.source.linkpreview

import java.net.URI
import java.net.URLDecoder

/**
 * User-Agent 프로필.
 * - [DEVICE_WEBVIEW]: 기기의 실제 WebView UA (WebSettings.getDefaultUserAgent). 런타임에 주입된다.
 * - 나머지는 각 서비스가 "링크 미리보기 봇"으로 허용하는 UA.
 */
enum class UaProfile(val fixed: String?) {
    DEVICE_WEBVIEW(null),
    DESKTOP_CHROME("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"),
    KAKAOTALK_SCRAP("Mozilla/5.0 (compatible; kakaotalk-scrap/1.0; +https://devtalk.kakao.com/t/scrap/33984)"),
    FACEBOOK_BOT("facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"),
    TWITTER_BOT("Twitterbot/1.0"),
}

/** 도메인별 수집 전략 */
data class FetchStrategy(
    val userAgents: List<UaProfile>,
    /** 이 도메인은 앱 내 WebView 로도 사실상 불가능 (봇 차단) — 실패 시 빠르게 포기하기 위한 힌트 */
    val note: String = "",
)

object LinkFetchRules {

    private val DEFAULT = FetchStrategy(
        listOf(UaProfile.DEVICE_WEBVIEW, UaProfile.KAKAOTALK_SCRAP, UaProfile.FACEBOOK_BOT)
    )

    /**
     * 호스트 접미사 → 전략. 위에서부터 먼저 매칭되는 항목을 사용한다.
     * 근거는 docs/리포트의 실측 결과 참고 (2026-10 기준).
     */
    private val TABLE: List<Pair<List<String>, FetchStrategy>> = listOf(
        // Meta 계열: 일반 UA 에는 로그인 월(og 없음), 크롤러 UA 에만 og 태그를 내려준다.
        listOf("instagram.com", "threads.net", "threads.com", "facebook.com", "fb.watch") to
                FetchStrategy(listOf(UaProfile.FACEBOOK_BOT, UaProfile.KAKAOTALK_SCRAP, UaProfile.TWITTER_BOT)),
        // 네이버 카페: SPA. facebookexternalhit 에만 og:title/og:image 제공
        listOf("cafe.naver.com") to FetchStrategy(listOf(UaProfile.FACEBOOK_BOT, UaProfile.TWITTER_BOT)),
        // 스마트스토어/브랜드스토어/네이버쇼핑: 데스크톱/봇 UA 는 429 또는 로그인 리다이렉트.
        // Android WebView UA(; wv / Version/4.0) 로 요청하면 m.smartstore 로 이동하며 og 를 준다.
        listOf("smartstore.naver.com", "brand.naver.com", "shopping.naver.com", "shopsquare.naver.com") to
                FetchStrategy(listOf(UaProfile.DEVICE_WEBVIEW)),
        // 쿠팡: 크롤러 UA 는 403. link.coupang.com 은 모바일 UA 에 앱 딥링크 페이지를 주므로 데스크톱 UA 사용.
        listOf("link.coupang.com") to FetchStrategy(listOf(UaProfile.DESKTOP_CHROME)),
        listOf("coupang.com") to FetchStrategy(listOf(UaProfile.DESKTOP_CHROME, UaProfile.DEVICE_WEBVIEW)),
        // SSG: WebView UA 403, 카카오/페북 스크랩 UA 허용
        listOf("ssg.com") to FetchStrategy(listOf(UaProfile.KAKAOTALK_SCRAP, UaProfile.FACEBOOK_BOT)),
        // 11번가: 모바일 UA 는 http://m.11st.co.kr 로 리다이렉트(평문) → 카카오 UA 로 데스크톱 페이지 수집
        listOf("11st.co.kr") to FetchStrategy(listOf(UaProfile.KAKAOTALK_SCRAP, UaProfile.DEVICE_WEBVIEW)),
        // 알리익스프레스: 모바일 UA 는 og:title 없는 SPA 셸, 스크랩 UA 는 og 제공
        listOf("aliexpress.com") to FetchStrategy(listOf(UaProfile.KAKAOTALK_SCRAP, UaProfile.FACEBOOK_BOT)),
        // TikTok: oEmbed 우선, 실패 시 facebookexternalhit
        listOf("tiktok.com") to FetchStrategy(listOf(UaProfile.FACEBOOK_BOT)),
        // X(Twitter): 카카오 UA 가 설명까지 포함된 og 를 준다
        listOf("x.com", "twitter.com") to FetchStrategy(listOf(UaProfile.KAKAOTALK_SCRAP, UaProfile.DEVICE_WEBVIEW)),
        // G마켓/옥션/오늘의집/테무: 봇 차단(JS 챌린지/Akamai). 앱 내 시도는 1회만 하고 WebView 폴백에 맡긴다.
        listOf("gmarket.co.kr", "auction.co.kr", "ohou.se", "temu.com") to
                FetchStrategy(listOf(UaProfile.DEVICE_WEBVIEW), note = "bot-protected"),
    )

    fun strategyFor(url: String): FetchStrategy {
        val host = HtmlMetaExtractor.hostOf(url) ?: return DEFAULT
        return TABLE.firstOrNull { (suffixes, _) -> suffixes.any { host == it || host.endsWith(".$it") } }?.second
            ?: DEFAULT
    }

    /** HTTP 리다이렉트만으로 풀리는 단축 URL 호스트 */
    private val SHORT_LINK_HOSTS = setOf(
        "naver.me", "youtu.be", "link.coupang.com", "a.co", "amzn.to", "amzn.asia", "bit.ly", "t.co",
        "han.gl", "url.kr", "me2.do", "vo.la", "buly.kr", "tinyurl.com", "goo.gl", "lrl.kr", "zrr.kr",
        "vt.tiktok.com", "vm.tiktok.com", "s.click.aliexpress.com", "a.aliexpress.com", "share.temu.com",
        "kko.to", "kko.kakao.com", "11st.kr", "gmkt.kr", "s.zigzag.kr", "tv.naver.me", "fb.me", "instagr.am",
    )

    fun isShortLink(url: String): Boolean {
        val host = HtmlMetaExtractor.hostOf(url) ?: return false
        return host in SHORT_LINK_HOSTS || host.removePrefix("www.") in SHORT_LINK_HOSTS
    }

    /** 사용자 입력 정리: 공백 제거, 스킴 보정 */
    fun normalizeInput(raw: String): String {
        var url = raw.trim().trimEnd('.', ',', ')', ']', '>', '"', '\'', '」', '。')
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) url = "https://$url"
        return url
    }

    /**
     * 같은 콘텐츠를 더 잘 파싱할 수 있는 URL 로 변환한다.
     * - http → https (Android 9+ 는 평문 HTTP 가 기본 차단)
     * - blog.naver.com 의 iframe 구조 → m.blog.naver.com 단일 페이지
     * - m.coupang.com/vm/products → www.coupang.com/vp/products (모바일 도메인은 Akamai 403)
     * - m.cafe.naver.com → cafe.naver.com (봇 UA 에 og 제공)
     * - 11st m 페이지 http → https
     */
    fun rewrite(url: String): String {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return url
        }
        val host = uri.host?.lowercase() ?: return url
        val path = uri.rawPath.orEmpty()
        val query = parseQuery(uri.rawQuery)

        // 네이버 블로그
        if (host == "blog.naver.com" || host == "m.blog.naver.com") {
            val blogId = query["blogId"]
            val logNo = query["logNo"]
            if (blogId != null && logNo != null) return "https://m.blog.naver.com/$blogId/$logNo"
            val seg = path.trim('/').split('/')
            if (seg.size == 2 && seg[1].all { it.isDigit() }) return "https://m.blog.naver.com/${seg[0]}/${seg[1]}"
            if (seg.size == 1 && seg[0].isNotEmpty() && logNo != null) return "https://m.blog.naver.com/${seg[0]}/$logNo"
        }
        // 네이버 카페 모바일 → PC (facebookexternalhit 에 og 제공)
        if (host == "m.cafe.naver.com") {
            val seg = path.trim('/').split('/')
            if (seg.size == 2 && seg[1].all { it.isDigit() }) return "https://cafe.naver.com/${seg[0]}/${seg[1]}"
            // m.cafe.naver.com/ca-fe/web/cafes/{cafe}/articles/{id}
            val i = seg.indexOf("cafes")
            if (i >= 0 && seg.size > i + 3 && seg[i + 2] == "articles") return "https://cafe.naver.com/${seg[i + 1]}/${seg[i + 3]}"
        }
        // 쿠팡 모바일 상품 → PC 상품
        if (host == "m.coupang.com") {
            val m = Regex("/vm/products/(\\d+)").find(path)
            if (m != null) {
                val q = uri.rawQuery?.let { "?$it" }.orEmpty()
                return "https://www.coupang.com/vp/products/${m.groupValues[1]}$q"
            }
        }
        if (uri.scheme.equals("http", true)) return "https" + url.substring(4)
        return url
    }

    /**
     * HTTP 3xx 의 Location 을 정리한다. 상대 경로 처리 + https 강제.
     */
    fun resolveLocation(base: String, location: String): String? {
        val abs = HtmlMetaExtractor.absolutize(location, base) ?: return null
        return if (abs.startsWith("http://")) "https://" + abs.removePrefix("http://") else abs
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return raw.split('&').mapNotNull {
            val idx = it.indexOf('=')
            if (idx <= 0) null else it.substring(0, idx) to try {
                URLDecoder.decode(it.substring(idx + 1), "UTF-8")
            } catch (e: Exception) {
                it.substring(idx + 1)
            }
        }.toMap()
    }
}

/** 호스트 → 표시용 사이트명 (og:site_name 이 없을 때) */
object KnownSites {
    private val NAMES: List<Pair<String, String>> = listOf(
        "m.blog.naver.com" to "네이버 블로그",
        "blog.naver.com" to "네이버 블로그",
        "cafe.naver.com" to "네이버 카페",
        "news.naver.com" to "네이버 뉴스",
        "smartstore.naver.com" to "네이버 스마트스토어",
        "brand.naver.com" to "네이버 브랜드스토어",
        "shopping.naver.com" to "네이버 쇼핑",
        "post.naver.com" to "네이버 포스트",
        "map.naver.com" to "네이버 지도",
        "naver.com" to "네이버",
        "v.daum.net" to "다음 뉴스",
        "daum.net" to "다음",
        "coupang.com" to "쿠팡",
        "11st.co.kr" to "11번가",
        "gmarket.co.kr" to "G마켓",
        "auction.co.kr" to "옥션",
        "ssg.com" to "SSG.COM",
        "musinsa.com" to "무신사",
        "29cm.co.kr" to "29CM",
        "ohou.se" to "오늘의집",
        "zigzag.kr" to "지그재그",
        "kurly.com" to "컬리",
        "aliexpress.com" to "AliExpress",
        "temu.com" to "Temu",
        "youtube.com" to "YouTube",
        "youtu.be" to "YouTube",
        "instagram.com" to "Instagram",
        "threads.net" to "Threads",
        "threads.com" to "Threads",
        "x.com" to "X",
        "twitter.com" to "X",
        "tiktok.com" to "TikTok",
        "tistory.com" to "티스토리",
        "brunch.co.kr" to "브런치",
        "velog.io" to "velog",
    )

    fun nameOf(url: String): String? {
        val host = HtmlMetaExtractor.hostOf(url) ?: return null
        return NAMES.firstOrNull { (suffix, _) -> host == suffix || host.endsWith(".$suffix") }?.second
    }
}
