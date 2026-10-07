package com.jinscompany.saveurl.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplaySettingsTest {

    @Test
    fun `기존 dark_mode 저장값이 화면 모드로 그대로 이어진다`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromDarkModePref(null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromDarkModePref(true))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromDarkModePref(false))
    }

    @Test
    fun `화면 모드를 저장값으로 바꿨다가 다시 읽으면 같은 값이다`() {
        ThemeMode.entries.forEach {
            assertEquals(it, ThemeMode.fromDarkModePref(it.toDarkModePref()))
        }
    }

    @Test
    fun `보기 방식 키가 없거나 알 수 없으면 기본 보기`() {
        assertEquals(ListViewMode.DEFAULT, ListViewMode.fromKey(null))
        assertEquals(ListViewMode.DEFAULT, ListViewMode.fromKey("unknown"))
        ListViewMode.entries.forEach { assertEquals(it, ListViewMode.fromKey(it.key)) }
    }
}
