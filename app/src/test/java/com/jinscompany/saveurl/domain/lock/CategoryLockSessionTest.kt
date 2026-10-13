package com.jinscompany.saveurl.domain.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryLockSessionTest {

    private var now = 1_000L
    private val session = CategoryLockSession { now }

    @Test
    fun `처음에는 잠겨 있고 unlock relock 으로 열고 닫는다`() {
        assertFalse(session.isUnlocked.value)
        session.unlock()
        assertTrue(session.isUnlocked.value)
        session.relock()
        assertFalse(session.isUnlocked.value)
    }

    @Test
    fun `앱을 나가 1분이 안 됐으면 열린 채로 둔다`() {
        session.unlock()
        session.onAppBackground()
        now += 59_000L
        session.onAppForeground()
        assertTrue(session.isUnlocked.value)
    }

    @Test
    fun `앱을 나가 1분이 지나면 다시 잠근다`() {
        session.unlock()
        session.onAppBackground()
        now += CategoryLockSession.RELOCK_AFTER_MS
        session.onAppForeground()
        assertFalse(session.isUnlocked.value)

        session.unlock()
        session.onAppBackground()
        now += 61_000L
        session.onAppForeground()
        assertFalse(session.isUnlocked.value)
    }

    @Test
    fun `나간 기록 없이 돌아오면(첫 시작) 아무것도 하지 않고, 한 번 쓴 기록은 다시 쓰지 않는다`() {
        session.unlock()
        session.onAppForeground()
        assertTrue(session.isUnlocked.value)

        session.onAppBackground()
        now += 10_000L
        session.onAppForeground()
        // 같은 기록으로 두 번째 복귀를 판단하지 않는다
        now += 120_000L
        session.onAppForeground()
        assertTrue(session.isUnlocked.value)
    }
}
