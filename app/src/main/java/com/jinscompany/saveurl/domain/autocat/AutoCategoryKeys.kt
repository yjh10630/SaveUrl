package com.jinscompany.saveurl.domain.autocat

import com.jinscompany.saveurl.data.source.linkpreview.KnownSites

/** 규칙 종류. HOST = 사이트 전체(하위 도메인 포함), PATH = 사이트 + 주인(블로그·카페·스토어 등) */
enum class RuleMatchType { HOST, PATH }

/** 규칙의 매칭 키. HOST 는 `instagram.com`, PATH 는 `blog.naver.com/seoul_eat` (36번 §1) */
data class RuleKey(val type: RuleMatchType, val pattern: String) {
    /** PATH 키의 호스트 (`blog.naver.com`). HOST 키면 패턴 그대로 */
    val host: String get() = if (type == RuleMatchType.PATH) pattern.substringBefore('/') else pattern

    /** PATH 키의 주인 (`seoul_eat`). HOST 키면 null */
    val owner: String? get() = if (type == RuleMatchType.PATH) pattern.substringAfter('/') else null
}

/** 주인이 있는 호스트의 종류 (적용 범위 문구: 블로그 / 카페 / 스토어 / 작가 / 작성자 / 조직) */
enum class TenantKind { BLOG, CAFE, STORE, AUTHOR, WRITER, ORG }

/**
 * 한 URL 에서 뽑은 키.
 * @param host 정리한 호스트 (`www.`·`m.`·`mobile.` 제거, 별칭 적용)
 * @param ownerKey 주인 호스트면 PATH 키, 아니면 null
 */
data class UrlKeys(val host: String, val ownerKey: RuleKey?) {
    val hostKey: RuleKey get() = RuleKey(RuleMatchType.HOST, host)
    val tenant: TenantKind? get() = AutoCategoryKeys.tenantOf(host)

    /** "앞으로 항상" 의 기본 키: 주인 호스트면 이 주인만, 아니면 사이트 전체 */
    val defaultKey: RuleKey get() = ownerKey ?: hostKey
}

/**
 * 자동 분류 매칭 키 추출 (33번 §2.1). android.net.Uri 를 쓰지 않는 순수 함수라 JVM 테스트로 확인한다.
 *
 * - 호스트: 소문자, 끝 점 제거, 앞의 `www.`·`m.`·`mobile.` 제거 (남는 호스트에 점이 하나 이상 있을 때만)
 * - 별칭: `youtu.be` → `youtube.com`, `n.news.naver.com` → `news.naver.com`, `twitter.com` → `x.com`, `threads.net` → `threads.com`
 * - 단축 주소(`naver.me`, `bit.ly` …)는 키가 없다 (풀린 최종 URL 로만 판정)
 * - 주인 호스트: 첫 경로 조각을 소문자로, '@' 제거. `PostView.naver?blogId=…`, 카페 `ca-fe/cafes/{id}`, `ArticleRead.nhn?clubid=…` 는 따로 읽는다
 */
object AutoCategoryKeys {

    private val TENANT_HOSTS: Map<String, TenantKind> = mapOf(
        "blog.naver.com" to TenantKind.BLOG,
        "cafe.naver.com" to TenantKind.CAFE,
        "smartstore.naver.com" to TenantKind.STORE,
        "brand.naver.com" to TenantKind.STORE,
        "brunch.co.kr" to TenantKind.AUTHOR,
        "velog.io" to TenantKind.WRITER,
        "github.com" to TenantKind.ORG,
    )

    private val HOST_ALIASES: Map<String, String> = mapOf(
        "youtu.be" to "youtube.com",
        "n.news.naver.com" to "news.naver.com",
        // 같은 서비스의 다른 도메인: 한쪽 규칙이 다른 쪽에도 맞게 (40번 독립 리뷰 R9)
        "twitter.com" to "x.com",
        "threads.net" to "threads.com",
        "instagr.am" to "instagram.com",
        "link.coupang.com" to "coupang.com",
        "vt.tiktok.com" to "tiktok.com",
        "vm.tiktok.com" to "tiktok.com",
    )

    /**
     * 단축 주소: 어디로 가는지 알 수 없으므로 키를 만들지 않는다 (40번 독립 리뷰 R9).
     * 미리보기 수집이 실패해 풀리지 않은 채 남으면 `naver.me` 규칙이 블로그·뉴스·지도를 한 카테고리로 묶게 된다.
     * 하위 도메인도 포함 (`tv.naver.me`).
     */
    private val SHORT_LINK_HOSTS = setOf(
        "naver.me", "me2.do", "kko.to", "kko.kakao.com", "bit.ly", "t.co", "han.gl", "url.kr", "vo.la", "buly.kr",
        "tinyurl.com", "goo.gl", "lrl.kr", "zrr.kr", "a.co", "amzn.to", "amzn.asia", "fb.me", "is.gd", "ow.ly",
        "buff.ly", "lnkd.in", "cutt.ly", "rebrand.ly", "shorturl.at", "tiny.cc", "11st.kr", "gmkt.kr",
    )

    private val STRIPPED_PREFIXES = listOf("www.", "m.", "mobile.")

    /** github.com 의 첫 경로 중 사용자·조직이 아닌 것 */
    private val GITHUB_RESERVED = setOf(
        "about", "account", "apps", "blog", "codespaces", "collections", "contact", "copilot", "customer-stories",
        "dashboard", "discussions", "education", "enterprise", "events", "explore", "features", "github-copilot",
        "home", "issues", "join", "login", "logout", "marketplace", "mobile", "new", "nonprofit", "notifications",
        "organizations", "partners", "premium-support", "pricing", "pulls", "readme", "resources", "search",
        "security", "sessions", "settings", "signup", "site", "solutions", "sponsors", "stars", "team", "topics",
        "trending", "watching", "why-github",
    )

    /** blog.naver.com 의 첫 경로 중 블로그 주인이 아닌 것 (`prologue/PrologueList.naver?blogId=…` 는 쿼리로 읽음) */
    private val BLOG_RESERVED = setOf(
        "prologue", "postlist", "postview", "mylog", "blogpeople", "guestbook", "profile", "sympathyhistory",
        "nblogs", "market", "section",
    )

    /** cafe.naver.com 의 첫 경로 중 카페 이름이 아닌 것. `ca-fe`·`f-e` 는 뒤의 `cafes/{id}` 를 읽는다 */
    private val CAFE_PATH_PREFIXES = setOf("ca-fe", "f-e")
    private val CAFE_RESERVED = setOf("cafes", "mycafelist", "cafelist", "cafesearch", "joincafe", "manage", "sectionlist", "favoritecafe")

    /** 스마트스토어·브랜드스토어 첫 경로 중 스토어 이름이 아닌 것 (`main/products/…` = 쇼핑 경유 상품) */
    private val STORE_RESERVED = setOf("main", "inflow", "outlink", "category", "search", "products", "shop")

    /** 같은 범용 사이트 목록 (제안 문턱 10개). 주인 호스트의 사이트 전체 키도 범용으로 본다 (33번 §3.1) */
    private val BROAD_HOSTS = listOf(
        "instagram.com", "youtube.com", "coupang.com", "x.com", "twitter.com", "tiktok.com", "facebook.com",
        "threads.net", "threads.com", "news.naver.com", "map.naver.com", "place.map.kakao.com", "kurly.com",
        "medium.com", "tistory.com", "naver.com", "daum.net",
        // 하위 서비스가 많은 큰 도메인 (지도·문서·쇼핑이 한 도메인 아래): 40번 독립 리뷰 R2
        "google.com", "kakao.com", "apple.com", "amazon.com", "microsoft.com", "blogspot.com", "wordpress.com",
    )

    fun tenantOf(host: String): TenantKind? = TENANT_HOSTS[host]

    fun isShortLinkHost(host: String): Boolean = SHORT_LINK_HOSTS.any { hostMatches(it, host) }

    /** URL 에서 키를 뽑는다. http/https 가 아니거나 호스트가 없으면 null */
    fun keysOf(url: String?): UrlKeys? {
        val raw = url?.trim().orEmpty()
        val schemeEnd = raw.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = raw.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null
        val rest = raw.substring(schemeEnd + 3).substringBefore('#')
        val authority = rest.substringBefore('/').substringBefore('?')
        val hostPort = authority.substringAfterLast('@')
        val rawHost = hostPort.substringBefore(':').lowercase().trimEnd('.')
        if (rawHost.isEmpty() || rawHost.startsWith("[")) return null
        val host = normalizeHost(rawHost)
        if (isShortLinkHost(host)) return null

        val afterAuthority = rest.substring(authority.length)
        val path = afterAuthority.substringBefore('?')
        val query = if (afterAuthority.contains('?')) afterAuthority.substringAfter('?') else ""
        val owner = TENANT_HOSTS[host]?.let { kind -> ownerOf(host, kind, path, query) }
        return UrlKeys(host = host, ownerKey = owner?.let { RuleKey(RuleMatchType.PATH, "$host/$it") })
    }

    fun normalizeHost(rawHost: String): String {
        var host = rawHost.lowercase().trimEnd('.')
        HOST_ALIASES[host]?.let { return it }
        for (prefix in STRIPPED_PREFIXES) {
            if (host.startsWith(prefix) && host.removePrefix(prefix).contains('.')) {
                host = host.removePrefix(prefix)
                break
            }
        }
        return HOST_ALIASES[host] ?: host
    }

    private fun ownerOf(host: String, kind: TenantKind, path: String, query: String): String? {
        val segments = path.split('/').filter { it.isNotEmpty() }
        val first = segments.firstOrNull()
        val owner: String? = when (kind) {
            TenantKind.BLOG ->
                if (first == null || first.contains('.') || first.lowercase() in BLOG_RESERVED) queryParam(query, "blogId") else first
            TenantKind.CAFE -> when {
                first == null || first.contains('.') -> queryParam(query, "clubid")
                // PC·모바일 새 형식: ca-fe/cafes/{id}, ca-fe/web/cafes/{id}, f-e/cafes/{id} (40번 독립 리뷰 R5)
                first in CAFE_PATH_PREFIXES -> segments.indexOf("cafes").takeIf { it > 0 }?.let { segments.getOrNull(it + 1) }
                first.lowercase() in CAFE_RESERVED -> null
                else -> first
            }
            TenantKind.STORE -> first?.takeUnless { it.contains('.') || it.lowercase() in STORE_RESERVED }
            TenantKind.AUTHOR, TenantKind.WRITER -> first?.takeIf { it.startsWith("@") }
            TenantKind.ORG -> when {
                first == null -> null
                first == "orgs" || first == "users" -> segments.getOrNull(1)
                first.lowercase() in GITHUB_RESERVED || first.contains('.') -> null
                else -> first
            }
        }
        return owner?.let { decode(it) }?.removePrefix("@")?.lowercase()?.takeIf { it.isNotBlank() }
    }

    private fun queryParam(query: String, name: String): String? =
        query.split('&').firstOrNull { it.substringBefore('=').equals(name, ignoreCase = true) }
            ?.substringAfter('=', "")?.takeIf { it.isNotEmpty() }

    private fun decode(s: String): String =
        if (s.indexOf('%') < 0) s else runCatching { java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

    /** HOST 규칙 [pattern] 이 [host] 에 맞는지 (점 경계: `notgithub.com` 은 `github.com` 에 맞지 않음) */
    fun hostMatches(pattern: String, host: String): Boolean = host == pattern || host.endsWith(".$pattern")

    /** 제안 문턱이 큰 범용 키 (범용 사이트 목록 + 주인 호스트의 사이트 전체) */
    fun isBroad(key: RuleKey): Boolean {
        if (key.type == RuleMatchType.PATH) return false
        if (TENANT_HOSTS.containsKey(key.pattern)) return true
        return BROAD_HOSTS.any { hostMatches(it, key.pattern) }
    }

    /** 화면 표시용 사이트 이름 (`Instagram`, `네이버 블로그 · seoul_eat`, `브런치 · @travelnote`, 모르는 사이트는 호스트) */
    fun displayName(key: RuleKey): String {
        val site = siteName(key.host)
        val owner = key.owner ?: return site
        val shown = if (tenantOf(key.host) == TenantKind.AUTHOR || tenantOf(key.host) == TenantKind.WRITER) "@$owner" else owner
        return "$site · $shown"
    }

    /** 사이트 이름만 (주인 없이) */
    fun siteName(host: String): String = KnownSites.nameOf("https://$host/") ?: when (host) {
        "github.com" -> "GitHub"
        else -> host
    }

    /** 규칙 시트 사이트 줄 아래 회색 설명: HOST = `instagram.com · 하위 주소 포함` 용 호스트, PATH = 패턴 그대로 */
    fun technicalLabel(key: RuleKey): String = key.pattern
}
