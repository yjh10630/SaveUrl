package com.jinscompany.saveurl.domain.model

import com.jinscompany.saveurl.domain.model.ThemeDefaultMigration.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeDefaultMigrationTest {

    @Test
    fun `기존 사용자이고 화면 모드를 고른 적 없으면 다크로 고정`() {
        assertEquals(Action.SET_DARK, ThemeDefaultMigration.decide(alreadyMigrated = false, darkModePref = null, hadPriorData = true))
    }

    @Test
    fun `새 설치는 시스템 따르기 유지`() {
        assertEquals(Action.NONE, ThemeDefaultMigration.decide(alreadyMigrated = false, darkModePref = null, hadPriorData = false))
    }

    @Test
    fun `직접 고른 라이트·다크는 그대로 둔다`() {
        assertEquals(Action.NONE, ThemeDefaultMigration.decide(false, darkModePref = false, hadPriorData = true))
        assertEquals(Action.NONE, ThemeDefaultMigration.decide(false, darkModePref = true, hadPriorData = true))
    }

    @Test
    fun `이미 마이그레이션했으면 다시 하지 않는다 (나중에 시스템으로 바꾼 사용자 보호)`() {
        assertEquals(Action.NONE, ThemeDefaultMigration.decide(alreadyMigrated = true, darkModePref = null, hadPriorData = true))
    }

    @Test
    fun `설정 파일이나 DB 중 하나라도 있으면 기존 사용자`() {
        assertTrue(ThemeDefaultMigration.hadPriorData(settingsFileExisted = true, databaseFileExisted = false))
        assertTrue(ThemeDefaultMigration.hadPriorData(settingsFileExisted = false, databaseFileExisted = true))
        assertFalse(ThemeDefaultMigration.hadPriorData(settingsFileExisted = false, databaseFileExisted = false))
    }
}
