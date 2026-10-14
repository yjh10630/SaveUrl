package com.jinscompany.saveurl.domain.importer

import com.jinscompany.saveurl.domain.importer.ImportFixtures.millis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class KakaoChatParserTest {

    private fun only(outcome: ReadOutcome): ParsedSource = outcome.sources.single()

    @Test
    fun `Android 한국어 - 이어진 줄의 링크, 날짜, 보낸 사람, 방 이름`() {
        val source = only(ImportFixtures.read("kakao_android_ko.txt"))
        assertEquals(ImportFormat.KAKAO, source.format)
        assertEquals("맛집 탐방", source.roomName)
        val urls = source.candidates.map { it.url }
        assertEquals(
            listOf(
                "https://blog.naver.com/minji/223000000001",
                "https://map.naver.com/p/entry/place/1234567?c=15.00,0,0,0,dh",
                "https://youtu.be/dQw4w9WgXcQ?si=abcdEFGH",
                "https://blog.naver.com/minji/223000000001?utm_source=kakao",
                "https://www.instagram.com/p/C1abcDEF/",
                "https://www.coupang.com/vp/products/12345",
                "https://namu.wiki/w/카카오톡에서",
            ),
            urls,
        )
        val first = source.candidates.first()
        assertEquals("김민지", first.sender)
        assertEquals(millis(2026, 3, 2, 9, 5), first.date)
        // 오전 12시 = 0시, 오후 11:59
        assertEquals(millis(2026, 3, 3, 23, 59), source.candidates[3].date)
        assertEquals("이준호", source.candidates[3].sender)
        // 이어진 줄(보낸 사람 없는 줄)도 앞 메시지의 보낸 사람·날짜
        assertEquals("김민지", source.candidates[5].sender)
        assertEquals(millis(2026, 3, 4, 15, 0), source.candidates[5].date)
        // "저장한 날짜" 줄은 메시지가 아니다
        assertEquals(millis(2026, 3, 2, 0, 30), source.firstDate)
    }

    @Test
    fun `Android 영어`() {
        val source = only(ImportFixtures.read("kakao_android_en.txt"))
        assertEquals(ImportFormat.KAKAO, source.format)
        assertEquals("Study Group", source.roomName)
        assertEquals(3, source.candidates.size)
        assertEquals("Alice", source.candidates[0].sender)
        assertEquals(millis(2026, 3, 2, 9, 5), source.candidates[0].date)
        assertEquals(millis(2026, 3, 2, 12, 30), source.candidates[1].date)
        assertEquals(millis(2026, 3, 3, 0, 5), source.candidates[2].date)
    }

    @Test
    fun `iOS 12시간제와 24시간제`() {
        val s12 = only(ImportFixtures.read("kakao_ios_ko_12h.txt"))
        assertEquals(3, s12.candidates.size)
        assertEquals("지수", s12.candidates[0].sender)
        assertEquals(millis(2025, 3, 31, 12, 4), s12.candidates[0].date)
        assertEquals("민호", s12.candidates[1].sender)
        assertEquals(millis(2025, 4, 1, 9, 30), s12.candidates[2].date)

        val s24 = only(ImportFixtures.read("kakao_ios_ko_24h.txt"))
        assertEquals(millis(2025, 5, 31, 15, 4), s24.candidates[0].date)
        assertEquals(millis(2025, 5, 31, 23, 59), s24.candidates[1].date)
    }

    @Test
    fun `PC Windows - 날짜 구분 줄 + 시각만 있는 메시지`() {
        val source = only(ImportFixtures.read("kakao_pc_windows.txt"))
        assertEquals(ImportFormat.KAKAO, source.format)
        assertEquals("회사 동기", source.roomName)
        assertEquals(3, source.candidates.size)
        assertEquals("김모임", source.candidates[0].sender)
        assertEquals(millis(2026, 10, 1, 19, 1), source.candidates[0].date)
        assertEquals("이대리", source.candidates[1].sender)
        assertEquals(millis(2026, 10, 2, 9, 0), source.candidates[2].date)
    }

    @Test
    fun `PC 파일이 CP949 여도 읽는다`() {
        val cp949 = ImportFixtures.text("kakao_pc_windows.txt").toByteArray(Charset.forName("MS949"))
        val source = only(ImportFixtures.read("KakaoTalk_회사동기.txt", cp949))
        assertEquals(ImportFormat.KAKAO, source.format)
        assertEquals("회사 동기", source.roomName)
        assertEquals(3, source.candidates.size)
    }

    @Test
    fun `붙여 넣은 옛 메시지 줄은 날짜·보낸 사람만 바꾸고 링크는 그대로`() {
        val source = only(ImportFixtures.read("kakao_tricky.txt"))
        assertEquals("나와의 채팅", source.roomName)
        val urls = source.candidates.map { it.url }
        assertEquals(
            listOf(
                "https://brunch.co.kr/@author/12",
                "https://old.example.com/post",
                "https://next.example.com/a",
                "https://next2.example.com/b",
                "https://last.example.com/x",
            ),
            urls,
        )
        assertEquals("옛날사람", source.candidates[1].sender)
        assertEquals(millis(2020, 5, 5, 10, 0), source.candidates[2].date)
    }

    @Test
    fun `미래·2000년 이전 날짜는 못 읽음`() {
        val text = "2099년 1월 1일 오전 9:00, 가 : https://a.com/1\n1999년 1월 1일 오전 9:00, 나 : https://b.com/2\n"
        val source = ImportFixtures.reader().readText(text, "공유한 글")
        assertEquals(ImportFormat.KAKAO, source.format)
        assertNull(source.candidates[0].date)
        assertNull(source.candidates[1].date)
    }

    @Test
    fun `형식 판정 - 헤더 또는 앞 8줄 중 2줄`() {
        assertTrue(KakaoChatParser.looksLikeKakao(listOf("친구 님과 카카오톡 대화", "")))
        assertTrue(
            KakaoChatParser.looksLikeKakao(
                listOf("2026년 3월 2일 오전 9:05, 가 : 안녕", "2026년 3월 2일 오전 9:06, 나 : 응")
            )
        )
        assertFalse(KakaoChatParser.looksLikeKakao(listOf("메모", "https://a.com", "2026년 3월 2일 오전 9:05, 가 : 하나만")))
    }
}
