package com.jinscompany.saveurl.domain.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class ImportFileReaderTest {

    private fun expectError(expected: ImportError, block: () -> Unit) {
        try {
            block()
            fail("expected $expected")
        } catch (e: ImportFailure) {
            assertEquals(expected, e.error)
        }
    }

    @Test
    fun `iOS 대화 zip - 사진과 MACOSX 는 건너뛰고 대화만`() {
        val zip = ImportFixtures.zip(
            "KakaoTalkChats.txt" to ImportFixtures.bytes("kakao_ios_ko_12h.txt"),
            "__MACOSX/._KakaoTalkChats.txt" to byteArrayOf(0, 1, 2),
            "2025-03-31-12-04-00.jpg" to byteArrayOf(-1, -40, -1, 0, 1),
        )
        val outcome = ImportFixtures.read("Kakaotalk_Chat_여행.zip", zip)
        assertTrue(outcome.isZip)
        assertEquals(1, outcome.sources.size)
        assertEquals(ImportFormat.KAKAO, outcome.sources[0].format)
        assertEquals(3, outcome.sources[0].candidates.size)
    }

    @Test
    fun `zip 안 대화 여러 개`() {
        val zip = ImportFixtures.zip(
            "a/맛집.txt" to ImportFixtures.bytes("kakao_android_ko.txt"),
            "b/캠핑.txt" to ImportFixtures.bytes("kakao_ios_ko_24h.txt"),
            "readme.md" to "링크 없음".toByteArray(),
        )
        val outcome = ImportFixtures.read("chats.zip", zip)
        assertEquals(listOf("맛집 탐방", "캠핑"), outcome.sources.map { it.roomName })
    }

    @Test
    fun `테이크아웃 - Chrome 북마크만 읽고 방문 기록은 열지 않는다`() {
        val zip = ImportFixtures.zip(
            "Takeout/Chrome/Bookmarks.html" to ImportFixtures.bytes("chrome_bookmarks.html"),
            "Takeout/Chrome/History.json" to """{"Browser History":[{"url":"https://secret-history.example.com"}]}""".toByteArray(),
            "Takeout/archive_browser.html" to "<a href=\"https://takeout.google.com\">x</a>".toByteArray(),
        )
        val outcome = ImportFixtures.read("takeout-20261013.zip", zip)
        val source = outcome.sources.single()
        assertEquals(ImportFormat.TAKEOUT, source.format)
        assertEquals(7, source.candidates.size)
        assertTrue(source.candidates.none { it.url.contains("secret-history") })
    }

    @Test
    fun `테이크아웃에 Chrome 북마크가 없으면 오류`() {
        val zip = ImportFixtures.zip("Takeout/YouTube/history.json" to "{}".toByteArray())
        expectError(ImportError.TAKEOUT_NO_BOOKMARKS) { ImportFixtures.read("takeout.zip", zip) }
    }

    @Test
    fun `zip 안에 읽을 파일이 없으면 오류`() {
        val zip = ImportFixtures.zip("photo.jpg" to byteArrayOf(1, 2, 3))
        expectError(ImportError.ZIP_EMPTY) { ImportFixtures.read("photos.zip", zip) }
    }

    @Test
    fun `빈 파일, 바이너리, 링크 없음`() {
        expectError(ImportError.EMPTY) { ImportFixtures.read("empty.txt", ByteArray(0)) }
        expectError(ImportError.UNSUPPORTED) { ImportFixtures.read("doc.pdf", "%PDF-1.7\n...".toByteArray()) }
        expectError(ImportError.UNSUPPORTED) { ImportFixtures.read("img.png", byteArrayOf(-119, 80, 78, 71, 0, 0, 0, 13)) }
        expectError(ImportError.NO_LINKS) { ImportFixtures.read("memo.txt", "링크가 없는 메모".toByteArray()) }
    }

    @Test
    fun `너무 큰 파일`() {
        val reader = ImportFileReader(limits = ImportLimits(maxFileBytes = 1_000), timeZone = ImportFixtures.SEOUL)
        val big = ("https://a.com/x\n").repeat(200).toByteArray()
        try {
            reader.read("big.txt", null, ByteArrayInputStream(big))
            fail()
        } catch (e: ImportFailure) {
            assertEquals(ImportError.TOO_BIG, e.error)
        }
        // 크기를 미리 알면 열기 전에
        try {
            reader.read("big.txt", 5_000, ByteArrayInputStream(big))
            fail()
        } catch (e: ImportFailure) {
            assertEquals(ImportError.TOO_BIG, e.error)
        }
    }

    @Test
    fun `zip 폭탄 - 압축 푼 합계 상한`() {
        val reader = ImportFileReader(limits = ImportLimits(maxZipTotalBytes = 10_000), timeZone = ImportFixtures.SEOUL)
        val zip = ImportFixtures.zip("a.txt" to ByteArray(20_000) { 'a'.code.toByte() })
        try {
            reader.read("bomb.zip", zip.size.toLong(), ByteArrayInputStream(zip))
            fail()
        } catch (e: ImportFailure) {
            assertEquals(ImportError.TOO_BIG, e.error)
        }
    }

    @Test
    fun `후보 상한을 넘으면 자르고 표시`() {
        val reader = ImportFileReader(limits = ImportLimits(maxCandidates = 5), timeZone = ImportFixtures.SEOUL)
        val text = (1..10).joinToString("\n") { "https://a.com/$it" }.toByteArray()
        val outcome = reader.read("list.txt", null, ByteArrayInputStream(text))
        assertTrue(outcome.truncated)
        assertEquals(5, outcome.sources.single().candidates.size)
    }

    @Test
    fun `SaveLink 백업 CSV - 여러 줄 설명, 잠금 열, 잘못된 링크`() {
        val source = ImportFixtures.read("savelink_backup.csv").sources.single()
        assertEquals(ImportFormat.BACKUP, source.format)
        assertEquals(3, source.candidates.size)
        assertEquals(1, source.nonHttpCount)
        val first = source.candidates[0]
        assertEquals("제목, 쉼표 포함", first.title)
        assertEquals("여러 줄\n설명", first.description)
        assertEquals(listOf("레시피", "주말"), first.tags)
        assertEquals("요리", first.category)
        assertTrue(first.isBookmark)
        assertTrue(source.candidates[1].categoryLocked)
        assertEquals(null, source.candidates[2].category)
    }

    @Test
    fun `일반 CSV (Pocket) - 열 이름으로 찾는다`() {
        val source = ImportFixtures.read("pocket_export.csv").sources.single()
        assertEquals(ImportFormat.TEXT, source.format)
        assertEquals(2, source.candidates.size)
        assertEquals("Kotlin Flow", source.candidates[0].title)
        assertEquals(1_700_000_000_000L, source.candidates[0].date)
        assertEquals(listOf("kotlin", "android"), source.candidates[0].tags)
    }

    @Test
    fun `메모 글 - 괄호, 한글 경로, 붙은 링크`() {
        val source = ImportFixtures.read("memo_share.txt").sources.single()
        assertEquals(ImportFormat.TEXT, source.format)
        assertEquals(
            listOf(
                "https://www.youtube.com/watch?v=abc123&t=30s",
                "https://brunch.co.kr/@writer/99",
                "https://namu.wiki/w/서울특별시",
                "https://a.example.com/x",
                "https://b.example.com/y",
                "https://c.example.com/z",
            ),
            source.candidates.map { it.url },
        )
    }

    @Test
    fun `같은 파일은 언제나 같은 결과 (골든)`() {
        val a = ImportFixtures.read("kakao_android_ko.txt")
        val b = ImportFixtures.read("kakao_android_ko.txt")
        assertEquals(a, b)
    }
}
