package com.jinscompany.saveurl.utils

import com.jinscompany.saveurl.domain.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInfoParserTest {

    @Test
    fun `정상 형식`() {
        val result = AppInfoParser.parse("""{"latest_version":39,"min_version":38}""")
        assertEquals(AppInfo(latestVersion = 39, minVersion = 38), result.appInfo)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `기본값은 업데이트 없음`() {
        val result = AppInfoParser.parse(AppInfoParser.DEFAULT_JSON)
        assertEquals(AppInfo(latestVersion = 0, minVersion = 0), result.appInfo)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `값이 없으면 설정 없음, 경고 없음`() {
        assertNull(AppInfoParser.parse("").appInfo)
        assertNull(AppInfoParser.parse(null).appInfo)
        assertTrue(AppInfoParser.parse("").warnings.isEmpty())
    }

    @Test
    fun `숫자 문자열은 허용`() {
        val result = AppInfoParser.parse("""{"latest_version":"39","min_version":" 38 "}""")
        assertEquals(AppInfo(latestVersion = 39, minVersion = 38), result.appInfo)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `camelCase 키는 경고와 함께 허용`() {
        val result = AppInfoParser.parse("""{"latestVersion":39,"minVersion":39}""")
        assertEquals(AppInfo(latestVersion = 39, minVersion = 39), result.appInfo)
        assertEquals(2, result.warnings.size)
    }

    @Test
    fun `versionName 이나 음수는 그 항목만 0 으로 보고 경고`() {
        val result = AppInfoParser.parse("""{"latest_version":"2.0.1","min_version":-1}""")
        assertEquals(AppInfo(latestVersion = 0, minVersion = 0), result.appInfo)
        assertEquals(2, result.warnings.size)

        val partial = AppInfoParser.parse("""{"latest_version":40,"min_version":39.5}""")
        assertEquals(AppInfo(latestVersion = 40, minVersion = 0), partial.appInfo)
        assertEquals(1, partial.warnings.size)
    }

    @Test
    fun `키가 빠지거나 알 수 없는 키가 있으면 경고`() {
        val missing = AppInfoParser.parse("""{"min_version":39}""")
        assertEquals(AppInfo(latestVersion = 0, minVersion = 39), missing.appInfo)
        assertEquals(1, missing.warnings.size)

        val unknown = AppInfoParser.parse("""{"latest_version":39,"min_version":0,"min_versoin":39}""")
        assertEquals(AppInfo(latestVersion = 39, minVersion = 0), unknown.appInfo)
        assertEquals(1, unknown.warnings.size)
    }

    @Test
    fun `JSON 객체가 아니면 경고와 함께 null, 크래시 없음`() {
        listOf("{", "39", "[39,38]", "not json", """{"latest_version":}""").forEach { raw ->
            val result = AppInfoParser.parse(raw)
            assertNull(raw, result.appInfo)
            assertTrue(raw, result.warnings.isNotEmpty())
        }
    }
}
