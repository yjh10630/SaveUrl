package com.jinscompany.saveurl.utils

import com.jinscompany.saveurl.domain.model.UrlData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        // 가져오기 시 항상 현재 규칙으로 다시 계산되므로 실제 정규화 값과 같아야 함
        normalizedUrl = "https://example.com/$id?a=1&b=2",
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

    @Test
    fun `categoryLocked 열을 맨 끝에 쓰고 읽는다`() {
        val locked = CsvBackupManager.buildCsvRow(item(1, "줄\n바꿈, 쉼표"), categoryLocked = true)
        val plain = CsvBackupManager.buildCsvRow(item(2, "한 줄"))
        val records = CsvBackupManager.readRecords(BufferedReader(StringReader("header\n$locked\n$plain\n"))).drop(1)

        assertEquals(2, records.size)
        assertTrue(CsvBackupManager.parseCategoryLocked(records[0]))
        assertFalse(CsvBackupManager.parseCategoryLocked(records[1]))
        assertEquals("줄\n바꿈, 쉼표", CsvBackupManager.parseCsvRow(records[0])?.description)
    }

    @Test
    fun `categoryLocked 열이 없는 예전 파일은 잠금 없음으로 읽는다`() {
        // 2.0.x 백업 (12열)
        val oldRow = "1,https://a.com,,,제목,,[],1700000000000,개발,false,false,https://a.com"
        assertEquals("개발", CsvBackupManager.parseCsvRow(oldRow)?.category)
        assertFalse(CsvBackupManager.parseCategoryLocked(oldRow))
    }
}
