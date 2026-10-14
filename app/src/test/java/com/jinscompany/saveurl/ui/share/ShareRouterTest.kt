package com.jinscompany.saveurl.ui.share

import com.jinscompany.saveurl.ui.share.ShareRouter.Route
import org.junit.Assert.assertEquals
import org.junit.Test

/** 44번 §2 공유 나누기 순서 */
class ShareRouterTest {

    private fun route(
        text: String? = null,
        streams: Int = 0,
        multiple: Boolean = false,
        importEnabled: Boolean = true,
        quick: Boolean = true,
    ) = ShareRouter.route(multiple, streams, text, importEnabled, quick)

    @Test
    fun `파일은 가져오기, 가져오기가 꺼져 있으면 지원하지 않음`() {
        assertEquals(Route.ImportFiles, route(streams = 1))
        assertEquals(Route.ImportFiles, route(streams = 3, multiple = true))
        assertEquals(Route.FileNotSupported, route(streams = 1, importEnabled = false))
    }

    @Test
    fun `링크 1개는 빠른 저장 시트, 원격 스위치가 꺼지면 앱 저장 화면`() {
        val text = "이거 봐 https://m.blog.naver.com/abc/123 재밌음"
        assertEquals(Route.QuickSave("https://m.blog.naver.com/abc/123"), route(text))
        assertEquals(Route.OpenSaveInApp("https://m.blog.naver.com/abc/123"), route(text, quick = false))
    }

    @Test
    fun `링크 여러 개는 가져오기, 가져오기가 꺼져 있으면 첫 링크를 빠른 저장`() {
        val text = "https://a.example.com/1\nhttps://b.example.com/2"
        assertEquals(Route.ImportText, route(text))
        assertEquals(Route.QuickSave("https://a.example.com/1"), route(text, importEnabled = false))
    }

    @Test
    fun `카톡 대화 형식이면 링크가 하나여도 가져오기`() {
        val chat = """
            친구 님과 카카오톡 대화
            저장한 날짜 : 2026-10-01 오후 3:00

            2026년 10월 1일 오후 2:00, 친구 : https://a.example.com/1
        """.trimIndent()
        assertEquals(Route.ImportText, route(chat))
    }

    @Test
    fun `링크가 없거나 빈 글이면 링크를 찾지 못함`() {
        assertEquals(Route.NoLink, route("그냥 글"))
        assertEquals(Route.NoLink, route(""))
        assertEquals(Route.NoLink, route(null))
        assertEquals(Route.NoLink, route(multiple = true))
    }
}
