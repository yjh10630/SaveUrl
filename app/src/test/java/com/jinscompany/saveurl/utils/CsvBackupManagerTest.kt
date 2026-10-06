package com.jinscompany.saveurl.utils

import com.jinscompany.saveurl.domain.model.UrlData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class CsvBackupManagerTest {

    private fun item(id: Int, description: String) = UrlData(
        id = id,
        url = "https://example.com/$id?a=1&b=2",
        title = "제목, \"따옴표\" $id",
        description = description,
        tagList = listOf("태그1", "태그2"),
        addDate = 1_700_000_000_000L + id,
        category = "개발",
        isBookMark = true,
        normalizedUrl = "https://example.com/$id",
    )

    private fun roundTrip(items: List<UrlData>): List<UrlData> {
        val csv = buildString {
            append("header\n")
            items.forEach { append(CsvBackupManager.buildCsvRow(it)).append("\n") }
        }
        return CsvBackupManager.readRecords(BufferedReader(StringReader(csv)))
            .drop(1)
            .mapNotNull { CsvBackupManager.parseCsvRow(it) }
    }

    @Test
    fun `설명에 줄바꿈이 있어도 모든 행을 복원한다`() {
        val items = listOf(
            item(1, "첫 줄\n둘째 줄\n\n넷째 줄"),
            item(2, "한 줄"),
            item(3, "\"인용\"\n다음 줄, 쉼표"),
        )

        val restored = roundTrip(items)

        assertEquals(items, restored)
    }

    @Test
    fun `캐리지 리턴이 포함된 값도 한 행으로 유지한다`() {
        val restored = roundTrip(listOf(item(1, "a\r\nb"), item(2, "c")))

        assertEquals(2, restored.size)
        assertEquals("a\nb", restored[0].description)
        assertEquals("c", restored[1].description)
    }
}
