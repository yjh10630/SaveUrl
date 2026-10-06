package com.jinscompany.saveurl.linkpreview

import com.jinscompany.saveurl.data.source.linkpreview.JsoupHttpGetter
import com.jinscompany.saveurl.data.source.linkpreview.OkHttpGetter
import com.jinscompany.saveurl.data.source.linkpreview.LinkPreviewFetcher
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.URL

/**
 * 실제 네트워크로 "기존 로직 vs 새 로직" 을 비교하는 수동 점검용 테스트.
 * 기본 빌드(testDebugUnitTest)에서는 건너뛴다. 실행:
 *   LINK_LIVE=1 LINK_LIVE_OUT=/path/out.md ./gradlew testDebugUnitTest --tests '*LinkPreviewLiveCheck*'
 * (JVM 의 TLS/HTTP 스택은 Android 와 달라 봇 차단 결과가 기기와 다를 수 있음)
 */
class LinkPreviewLiveCheck {

    private val deviceUa =
        "Mozilla/5.0 (Linux; Android 14; SM-S918N Build/UP1A.231005.007; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/129.0.6668.100 Mobile Safari/537.36"

    private val urls = listOf(
        "네이버 뉴스" to "https://n.news.naver.com/article/001/0016354857",
        "네이버 뉴스(m)" to "https://m.news.naver.com/article/001/0016357255",
        "네이버 블로그" to "https://blog.naver.com/amazing_joy/224419427741",
        "네이버 블로그(PostView)" to "https://blog.naver.com/PostView.naver?blogId=choonsdailypaper&logNo=224432678582",
        "네이버 블로그(m)" to "https://m.blog.naver.com/bmdietcamp/224420985032",
        "네이버 카페" to "https://cafe.naver.com/anycallusershow/5235658",
        "네이버 카페(m)" to "https://m.cafe.naver.com/anycallusershow/5231717",
        "스마트스토어" to "https://smartstore.naver.com/myspot/products/4495365174",
        "브랜드스토어" to "https://brand.naver.com/pictory/products/4108300313",
        "인스타 게시물" to "https://www.instagram.com/p/BsOGulcndj-/",
        "인스타 릴스" to "https://www.instagram.com/reel/DITBVk3z6pJ/",
        "유튜브" to "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "유튜브 youtu.be" to "https://youtu.be/dQw4w9WgXcQ?si=abc",
        "유튜브 m." to "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=10",
        "유튜브 shorts" to "https://www.youtube.com/shorts/tPEE9ZwTmy0",
        "유튜브 playlist" to "https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI",
        "쿠팡" to "https://www.coupang.com/vp/products/7335597976",
        "쿠팡(m)" to "https://m.coupang.com/vm/products/7335597976",
        "쿠팡 link" to "https://link.coupang.com/a/cWb7xV",
        "11번가" to "https://www.11st.co.kr/products/1217141544",
        "G마켓" to "https://item.gmarket.co.kr/Item?goodscode=4697576434",
        "SSG" to "https://www.ssg.com/item/itemView.ssg?itemId=1000554051524",
        "무신사" to "https://www.musinsa.com/products/3929862",
        "29CM" to "https://product.29cm.co.kr/catalog/2280520",
        "오늘의집" to "https://ohou.se/productions/1047744/selling",
        "지그재그" to "https://zigzag.kr/catalog/products/165707075",
        "컬리" to "https://www.kurly.com/goods/5029850",
        "알리익스프레스" to "https://www.aliexpress.com/item/3256805795791292.html",
        "테무" to "https://www.temu.com/goods.html?goods_id=601099593661903",
        "다음 뉴스" to "https://v.daum.net/v/20260906203708564",
        "X" to "https://x.com/elonmusk/status/1585341984679469056",
        "TikTok" to "https://www.tiktok.com/@scout2015/video/6718335390845095173",
        "Threads" to "https://www.threads.com/@sangwan2840/post/DW3Nb9ZD03d",
        "EUC-KR(웃대)" to "https://www.humoruniv.com/board/humor/read.html?table=dump&number=49701",
    )

    @Test
    fun compareLegacyAndNew() {
        assumeTrue(System.getenv("LINK_LIVE") == "1")
        val only = System.getenv("LINK_LIVE_ONLY")
        val sb = StringBuilder()
        sb.appendLine("| 분류 | URL | 기존(제목/이미지/설명) | 신규(제목/이미지/설명) | 신규 방식 | 신규 제목 |")
        sb.appendLine("|---|---|---|---|---|---|")
        for ((label, url) in urls) {
            if (only != null && !label.contains(only)) continue
            val legacy = runCatching { legacy(url) }.getOrElse { Triple("", "", "ERR ${it.javaClass.simpleName}") }
            val logs = mutableListOf<String>()
            val meta = LinkPreviewFetcher(OkHttpGetter(), JsoupHttpGetter(), { deviceUa }, log = { logs += it }).fetch(url)
            fun ok(s: String?) = if (s.isNullOrBlank()) "X" else "O"
            val legacyCell = "${ok(legacy.first)}/${ok(legacy.second)}/${ok(legacy.third.takeIf { !it.startsWith("ERR") && it != url })}" +
                    (if (legacy.third.startsWith("ERR")) " (${legacy.third})" else "")
            val newCell = "${ok(meta.title)}/${ok(meta.imageUrl)}/${ok(meta.description)}"
            val line = "| $label | $url | $legacyCell | $newCell | ${meta.method} | ${meta.title?.take(40)?.replace("|", "/")} |"
            println(line)
            logs.forEach { println("    $it") }
            sb.appendLine(line)
        }
        System.getenv("LINK_LIVE_OUT")?.let { File(it).writeText(sb.toString()) }
    }

    /** 기존 UrlParserSourceImpl.jsoupUrlParser 동작 재현 (WebSettings UA = deviceUa) */
    private fun legacy(url: String): Triple<String, String, String> {
        val yt = Regex("(https?://)?(www\\.)?(youtube\\.com/watch|youtu\\.be/)(.+)")
        if (yt.containsMatchIn(url)) {
            runCatching {
                // 기존 코드는 org.json 사용 (JVM 단위테스트에선 stub 이므로 Gson 으로 동일하게 파싱)
                val json = JsonParser.parseString(URL("https://www.youtube.com/oembed?url=${URL(url)}&format=json").readText()).asJsonObject
                return Triple(json["title"]?.asString.orEmpty(), json["thumbnail_url"]?.asString.orEmpty(), json["author_name"]?.asString.orEmpty())
            }
        }
        var title = ""
        var img = ""
        var desc = ""
        try {
            val response = Jsoup.connect(url).followRedirects(true).execute().url().toExternalForm()
            val document = Jsoup.connect(response).timeout(30000).userAgent(deviceUa)
                .referrer("https://www.google.com/").ignoreHttpErrors(true).ignoreContentType(true).get()
            title = document.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
            img = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            desc = document.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
        } catch (e: Exception) {
            desc = "ERR ${e.javaClass.simpleName}"
        }
        if (title.isEmpty()) {
            try {
                val mobileUa = "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                val document = Jsoup.connect(url).timeout(15000).userAgent(mobileUa).referrer("https://www.google.com/")
                    .ignoreHttpErrors(true).ignoreContentType(true).get()
                val t = document.selectFirst("meta[property=og:title]")?.attr("content")
                if (!t.isNullOrEmpty()) {
                    return Triple(t, document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty(),
                        document.selectFirst("meta[property=og:description]")?.attr("content").orEmpty())
                }
            } catch (_: Exception) {
            }
        }
        return Triple(title, img, desc)
    }
}
