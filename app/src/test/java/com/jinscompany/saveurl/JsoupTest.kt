package com.jinscompany.saveurl

import org.jsoup.Jsoup
import org.junit.Test

class JsoupTest {

    val userAgents = listOf(
        /*"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.157 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36"*/
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Version/16.3 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_1 like Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/537.36"
    )

    @Test
    fun UrlTest() {
        val response = Jsoup.connect("https://meal-coding.tistory.com/29").followRedirects(true).execute().url().toExternalForm()
        val document = Jsoup.connect(response).timeout(30000).userAgent(userAgents.random()).get()

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
        val imageUrl = document.selectFirst("meta[property=og:image]")?.attr("content")
        val siteName = document.selectFirst("meta[property=og:site_name]")?.attr("content")
        val url = document.selectFirst("meta[property=og:url]")?.attr("content")

        val keywords = document.selectFirst("meta[name=keywords]")?.attr("content")
        val author = document.selectFirst("meta[name=author]")?.attr("content")
        val publishedTime = document.selectFirst("meta[property=article:published_time]")?.attr("content")
        println("title > ${title}")
        println("description > ${description}")
        println("imageUrl > ${imageUrl}")
        println("siteName > ${siteName}")
        println("url > ${url}")
        println("keywords > ${keywords}")
        println("author > ${author}")
        println("publishedTime > ${publishedTime}")
    }
}