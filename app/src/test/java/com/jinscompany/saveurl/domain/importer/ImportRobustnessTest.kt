package com.jinscompany.saveurl.domain.importer

import com.jinscompany.saveurl.domain.importer.ImportFileReader.Companion.koreanCharset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.StringReader

/** 39번 독립 리뷰 R3·R4·R10·R11·R12: 깨진·큰·이상한 파일에서도 링크를 잃지 않고 메모리·시간을 묶어 두지 않는다 */
class ImportRobustnessTest {

    private fun backupCsv(rows: Int, broken: Map<Int, String> = emptyMap()): String = buildString {
        append("id,url,imageUrl,siteName,title,description,tagList,addDate,category,isBookMark,isRead,normalizedUrl,categoryLocked\n")
        for (i in 1..rows) {
            val title = broken[i] ?: "제목 $i"
            append("$i,https://site.example.com/$i,,,${title},,[],${1_700_000_000_000L + i},읽을거리,false,false,,false\n")
        }
    }

    // ---- R3 CSV 따옴표 ----

    @Test
    fun `칸 중간의 따옴표는 글자로 읽어 나머지 줄을 잃지 않는다`() {
        val csv = backupCsv(1000, broken = mapOf(2 to "6\" ruler"))
        val source = ImportFixtures.read("backup.csv", csv.toByteArray()).sources.single()
        assertEquals(1000, source.candidates.size)
        assertEquals("6\" ruler", source.candidates[1].title)
        assertEquals(0, source.skippedRecords)
    }

    @Test
    fun `닫히지 않는 따옴표는 그 줄만 건너뛰고 다음 줄부터 읽는다`() {
        val csv = backupCsv(1000, broken = mapOf(2 to "\"닫히지 않음"))
        val source = ImportFixtures.read("backup.csv", csv.toByteArray()).sources.single()
        assertEquals(999, source.candidates.size)
        assertEquals(1, source.skippedRecords)
        assertFalse(source.candidates.any { it.url.endsWith("/2") })
        assertTrue(source.candidates.any { it.url.endsWith("/1000") })
    }

    @Test
    fun `따옴표 안 줄바꿈은 그대로 한 레코드`() {
        val csv = "url,title\nhttps://a.example.com,\"첫 줄\n둘째 줄\"\nhttps://b.example.com,b\n"
        val source = ImportFixtures.read("pocket.csv", csv.toByteArray()).sources.single()
        assertEquals(listOf("첫 줄\n둘째 줄", "b"), source.candidates.map { it.title })
        assertEquals(0, source.skippedRecords)
    }

    @Test
    fun `건너뛴 줄 수가 미리보기 숫자에 나온다`() {
        val csv = backupCsv(5, broken = mapOf(3 to "\"깨짐"))
        val outcome = ImportFixtures.read("backup.csv", csv.toByteArray())
        val preview = ImportPlanner.buildPreview(outcome.sources, emptySet(), "backup.csv", truncated = false)
        assertEquals(1, preview.counts.unreadableRows)
        assertEquals(4, preview.counts.found)
    }

    // ---- R4 큰 한 줄 ----

    @Test
    fun `줄바꿈 없는 큰 글도 조각으로 읽고 경계의 링크를 자르지 않는다`() {
        val sb = StringBuilder()
        var n = 0
        while (sb.length < 300_000) {
            sb.append("{\"url\":\"https://a.example.com/item/").append(n++).append("\"},")
        }
        val lines = BoundedLineReader(StringReader(sb.toString()), maxLine = 4096)
        var maxLen = 0
        val urls = ArrayList<String>()
        while (true) {
            val line = lines.readLine() ?: break
            maxLen = maxOf(maxLen, line.length)
            urls += UrlExtractor.extractAll(line)
        }
        assertTrue(maxLen <= 4096)
        assertEquals(n, urls.size)
        assertEquals((0 until n).map { "https://a.example.com/item/$it" }, urls)
    }

    @Test
    fun `줄 끝 문자는 readLine 과 같게 나눈다`() {
        val lines = BoundedLineReader(StringReader("a\r\nb\rc\n\nd"))
        assertEquals(listOf("a", "b", "c", "", "d"), generateSequence { lines.readLine() }.toList())
        assertNull(BoundedLineReader(StringReader("")).readLine())
    }

    @Test
    fun `큰 한 줄 파일에서도 링크를 찾는다`() {
        val body = (1..20_000).joinToString(",") { "\"https://b.example.com/$it\"" }
        val source = ImportFixtures.read("export.json", body.toByteArray()).sources.single()
        assertEquals(20_000, source.candidates.size)
    }

    // ---- R11 인코딩 ----

    @Test
    fun `앞부분이 영어뿐이고 뒤에 한글이 나오는 CP949 북마크도 한글이 깨지지 않는다`() {
        val cp949 = koreanCharset()!!
        val head = buildString {
            append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n<DT><H3>English</H3>\n<DL><p>\n")
            repeat(2000) { append("<DT><A HREF=\"https://en.example.com/$it\" ADD_DATE=\"1700000000\">Page $it</A>\n") }
            append("</DL><p>\n")
        }
        val tail = "<DT><H3>한글폴더</H3>\n<DL><p>\n<DT><A HREF=\"https://ko.example.com/\" ADD_DATE=\"1700000000\">한국어 제목</A>\n</DL><p>\n</DL><p>\n"
        val bytes = head.toByteArray(Charsets.US_ASCII) + tail.toByteArray(cp949)
        assertTrue(bytes.size > 64 * 1024)
        val source = ImportFixtures.read("bookmarks.html", bytes).sources.single()
        val ko = source.candidates.single { it.url.startsWith("https://ko.example.com") }
        assertEquals("한국어 제목", ko.title)
        assertEquals(listOf("한글폴더"), ko.folderPath)
    }

    @Test
    fun `UTF-8 파일은 그대로 UTF-8`() {
        val reader = Utf8OrFallbackReader(ByteArrayInputStream("가나다 https://a.example.com".toByteArray()), koreanCharset())
        assertEquals("가나다 https://a.example.com", reader.readText())
        assertFalse(reader.switchedToFallback)
    }

    // ---- R12 긴 괄호 ----

    @Test
    fun `닫는 괄호가 아주 많아도 빨리 끝난다`() {
        val text = "https://a.example.com/x" + ")".repeat(200_000)
        val started = System.nanoTime()
        val urls = UrlExtractor.extractAll(text)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        // 8 KB 를 넘는 것은 링크로 보지 않는다
        assertTrue(urls.isEmpty())
        assertTrue("took $elapsedMs ms", elapsedMs < 1_000)
        assertEquals("https://a.example.com/(x)", UrlExtractor.trimTrailing("https://a.example.com/(x))))"))
        assertEquals("https://a.example.com/x", UrlExtractor.trimTrailing("https://a.example.com/x" + ")".repeat(5000)))
    }

    // ---- R10 카테고리 이름 ----

    @Test
    fun `폴더 이름은 20자에서 자르고 고정 칩 이름은 바꾼다`() {
        assertEquals("가".repeat(20), ImportPlanner.normalizeName("가".repeat(200)))
        assertEquals("전체 (가져옴)", ImportPlanner.normalizeName("전체"))
        assertEquals("북마크 (가져옴)", ImportPlanner.normalizeName(" 북마크 "))
        assertNull(ImportPlanner.normalizeName("미분류"))
        assertNull(ImportPlanner.normalizeName("  "))
    }

    @Test
    fun `북마크 폴더 묶음 기본 이름도 같은 규칙`() {
        val html = buildString {
            append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n")
            append("<DT><H3>${"폴".repeat(200)}</H3>\n<DL><p>\n<DT><A HREF=\"https://a.example.com/\">a</A>\n</DL><p>\n")
            append("<DT><H3>전체</H3>\n<DL><p>\n<DT><A HREF=\"https://b.example.com/\">b</A>\n</DL><p>\n")
            append("</DL><p>\n")
        }
        val outcome = ImportFixtures.read("bookmarks.html", html.toByteArray())
        val preview = ImportPlanner.buildPreview(outcome.sources, emptySet(), "bookmarks.html", truncated = false)
        val names = preview.groups.mapNotNull { it.defaultName }
        assertTrue(names.all { it.length <= 20 })
        assertTrue("전체 (가져옴)" in names)
        val request = ImportPlanner.buildRequest(preview, ImportPlanner.defaultSelection(preview), now = 0L)
        assertTrue(request.records.none { it.category == "전체" })
    }
}
