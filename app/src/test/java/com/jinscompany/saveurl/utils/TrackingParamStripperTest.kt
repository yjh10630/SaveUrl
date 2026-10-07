package com.jinscompany.saveurl.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingParamStripperTest {

    private fun assertStrip(input: String, expected: String) =
        assertEquals(input, expected, TrackingParamStripper.strip(input))

    private fun assertUnchanged(input: String) = assertStrip(input, input)

    // ---------- 제거 대상 ----------

    @Test
    fun `utm 계열은 모든 도메인에서 제거`() {
        assertStrip(
            "https://www.bbc.com/news/technology?utm_source=twitter&utm_medium=social&utm_campaign=bbc",
            "https://www.bbc.com/news/technology"
        )
        assertStrip(
            "https://blog.example.com/post/123?id=5&utm_source=newsletter&utm_term=kotlin&page=2",
            "https://blog.example.com/post/123?id=5&page=2"
        )
        // 표준 외 utm_* 도 접두어로 제거, 대소문자 무시
        assertStrip("https://example.com/a?UTM_Source=x&utm_brand=y&q=1", "https://example.com/a?q=1")
        assertStrip("https://example.com/a?utm_id=1&utm_reader=feedly&utm_name=n", "https://example.com/a")
    }

    @Test
    fun `광고 클릭 ID 제거`() {
        assertStrip("https://shop.example.com/item/42?fbclid=IwAR0abc_DEF-123", "https://shop.example.com/item/42")
        assertStrip("https://www.example.co.kr/landing?gclid=Cj0KCQjw&dclid=CJ&gbraid=0AA&wbraid=Cl",
            "https://www.example.co.kr/landing")
        assertStrip("https://example.com/p?msclkid=abc123&yclid=999&q=shoes", "https://example.com/p?q=shoes")
        assertStrip("https://example.com/p?mc_cid=a1b2&mc_eid=c3d4", "https://example.com/p")
        assertStrip("https://example.com/p?_hsenc=p2ANqtz&_hsmi=12345&lang=ko", "https://example.com/p?lang=ko")
    }

    @Test
    fun `인스타그램 공유 파라미터 제거`() {
        assertStrip("https://www.instagram.com/p/C1a2B3c4D5e/?igsh=MWQ1ZGUxMzBkMA==", "https://www.instagram.com/p/C1a2B3c4D5e/")
        assertStrip("https://www.instagram.com/reel/C9xYz/?igshid=NTc4MTIwNjQ2YQ%3D%3D", "https://www.instagram.com/reel/C9xYz/")
        assertStrip("https://www.instagram.com/reel/C9xYz/?utm_source=ig_web_copy_link&igsh=abc",
            "https://www.instagram.com/reel/C9xYz/")
    }

    @Test
    fun `유튜브 si 는 제거하고 v list t 는 유지`() {
        assertStrip("https://youtu.be/dQw4w9WgXcQ?si=AbCdEfGhIjKlMnOp", "https://youtu.be/dQw4w9WgXcQ")
        assertStrip("https://youtu.be/dQw4w9WgXcQ?si=AbCd&t=42", "https://youtu.be/dQw4w9WgXcQ?t=42")
        assertStrip(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG&si=xyz&t=1m2s",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG&t=1m2s"
        )
        assertStrip("https://m.youtube.com/shorts/abcDEF12345?si=q1w2e3", "https://m.youtube.com/shorts/abcDEF12345")
        assertStrip("https://music.youtube.com/watch?v=XyZ123&si=abc&feature=share", "https://music.youtube.com/watch?v=XyZ123&feature=share")
        assertStrip("https://youtube.com/playlist?list=PL123&si=zzz", "https://youtube.com/playlist?list=PL123")
    }

    @Test
    fun `스포티파이 si 제거`() {
        assertStrip("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=1a2b3c4d5e6f4a7b", "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
        assertStrip("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc&pi=u-xyz", "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?pi=u-xyz")
    }

    @Test
    fun `알리익스프레스 spm 제거`() {
        assertStrip(
            "https://ko.aliexpress.com/item/1005006123456789.html?spm=a2g0o.productlist.main.1.abc&algo_pvid=xyz",
            "https://ko.aliexpress.com/item/1005006123456789.html?algo_pvid=xyz"
        )
        assertStrip("https://www.aliexpress.us/item/3256805.html?spm=a2g0o&gatewayAdapt=glo2usa", "https://www.aliexpress.us/item/3256805.html?gatewayAdapt=glo2usa")
        assertStrip("https://aliexpress.com/item/1.html?spm=x", "https://aliexpress.com/item/1.html")
    }

    // ---------- 보존 대상 ----------

    @Test
    fun `si 와 spm 은 해당 도메인 외에서는 유지`() {
        assertUnchanged("https://example.com/search?si=1&q=x")
        assertUnchanged("https://www.taobao.com/item.htm?id=123&spm=a21bo")
        assertUnchanged("https://notyoutube.com/watch?v=1&si=2")
        assertUnchanged("https://spotify.com/track/1?si=abc")
    }

    @Test
    fun `콘텐츠 식별 파라미터는 유지`() {
        assertUnchanged("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1&index=3&t=120s")
        assertUnchanged("https://smartstore.naver.com/brand/products/1234567890?NaPm=ct%3Dabc")
        assertUnchanged("https://search.shopping.naver.com/catalog/12345?productNo=987654321&query=%ED%82%A4%EB%B3%B4%EB%93%9C")
        assertUnchanged("https://www.coupang.com/vp/products/7654321?itemId=20000001&vendorItemId=80000001&sourceType=srp_product_ads")
        assertUnchanged("https://n.news.naver.com/mnews/article/001/0014000000?sid=105")
        assertUnchanged("https://m.blog.naver.com/PostView.naver?blogId=someone&logNo=223456789012")
        assertUnchanged("https://cafe.naver.com/ArticleRead.nhn?clubid=10050146&articleid=123456")
        assertUnchanged("https://www.11st.co.kr/products/1234567?trTypeCd=22")
        assertUnchanged("https://www.google.com/search?q=kotlin+coroutines&hl=ko")
        assertUnchanged("https://github.com/square/okhttp/issues?q=is%3Aopen+label%3Abug")
        assertUnchanged("https://www.amazon.com/dp/B0C1234567?th=1&psc=1")
        assertUnchanged("https://map.naver.com/p/entry/place/11591546?c=15.00,0,0,0,dh")
    }

    @Test
    fun `share_id share_source 는 보수적으로 유지`() {
        assertUnchanged("https://www.xiaohongshu.com/discovery/item/64a1b2?share_id=abc&share_source=link")
    }

    @Test
    fun `남은 파라미터의 순서와 인코딩 유지`() {
        assertStrip(
            "https://example.com/s?z=1&utm_source=a&b=%EA%B0%80%20%EB%82%98&a=+plus+&fbclid=x",
            "https://example.com/s?z=1&b=%EA%B0%80%20%EB%82%98&a=+plus+"
        )
    }

    @Test
    fun `fragment 유지`() {
        assertStrip("https://example.com/docs/page?utm_source=x#section-2", "https://example.com/docs/page#section-2")
        assertStrip("https://example.com/docs/page?a=1&gclid=x#top", "https://example.com/docs/page?a=1#top")
        // fragment 안의 쿼리(SPA 해시 라우팅)는 건드리지 않음
        assertUnchanged("https://example.com/#/list?utm_source=x")
    }

    @Test
    fun `제거할 것이 없거나 URL 형식이 아니면 그대로`() {
        assertUnchanged("https://example.com")
        assertUnchanged("https://example.com/path/")
        assertUnchanged("https://example.com/?")
        assertUnchanged("example.com/a?utm_source=x")
        assertUnchanged("not a url")
        assertUnchanged("")
    }

    @Test
    fun `값 없는 키, 빈 세그먼트, 포트, userinfo`() {
        assertStrip("https://example.com:8443/a?utm_source&b", "https://example.com:8443/a?b")
        assertStrip("https://user@Example.COM/a?fbclid=1", "https://user@Example.COM/a")
        assertStrip("https://EXAMPLE.com/a?b=1&&utm_medium=x", "https://EXAMPLE.com/a?b=1&")
        assertStrip("https://www.YouTube.com/watch?v=abc&SI=zzz", "https://www.YouTube.com/watch?v=abc")
    }

    @Test
    fun `퍼센트 인코딩된 키도 판단`() {
        assertStrip("https://example.com/a?utm%5Fsource=x&q=1", "https://example.com/a?q=1")
    }

    @Test
    fun `앞뒤 공백은 제거`() {
        assertStrip("  https://example.com/a?utm_source=x  ", "https://example.com/a")
    }
}
