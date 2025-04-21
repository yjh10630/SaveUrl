package com.jinscompany.saveurl

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun test() {
        val url = "나는야 테스트 url 입니당 https://jihunstudy.tistory.com/66 이거좀 바보라공"
        println(extractUrlFromText(url))
    }

    fun extractUrlFromText(text: String): String? {
        val urlRegex = Regex(
            "(https?://[a-zA-Z0-9./?=_-]+)",
            RegexOption.IGNORE_CASE
        )
        return urlRegex.find(text)?.value
    }
}