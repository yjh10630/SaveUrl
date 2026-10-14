package com.jinscompany.saveurl.domain.revisit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 놓친 알림 처리·다시 볼 링크 고르기·주간 정리 조건 (42번 §2.4, §4, §5) */
class RevisitRulesTest {
    private val hour = 60 * 60_000L

    @Test
    fun `다시 맞추기 - 안 온 예약은 다시 걸고, 24시간 안에 지난 건 지금, 더 지난 건 버린다`() {
        val now = 1_000 * hour
        val plan = ReminderSync.plan(
            listOf(1 to now + hour, 2 to now - hour, 3 to now - 25 * hour, 4 to now - 24 * hour, 5 to now + 2 * hour),
            now,
        )
        assertEquals(listOf(1 to now + hour, 5 to now + 2 * hour), plan.schedule)
        assertEquals(listOf(4, 2), plan.fireNow)
        assertEquals(listOf(3), plan.drop)
    }

    @Test
    fun `알람이 울렸을 때 - 예약이 뒤로 옮겨졌으면 다시 걸고, 하루 넘게 지났으면 버린다`() {
        val now = 1_000 * hour
        assertEquals(ReminderSync.FireDecision.FIRE, ReminderSync.onAlarm(now, now))
        assertEquals(ReminderSync.FireDecision.FIRE, ReminderSync.onAlarm(now + 30_000L, now))
        assertEquals(ReminderSync.FireDecision.RESCHEDULE, ReminderSync.onAlarm(now + 2 * hour, now))
        assertEquals(ReminderSync.FireDecision.DROP, ReminderSync.onAlarm(now - 25 * hour, now))
        assertEquals(ReminderSync.FireDecision.FIRE, ReminderSync.onAlarm(now - 23 * hour, now))
    }

    @Test
    fun `단계 예약 - 마지막 단계는 20분 안쪽에서 목표 시각, 그 전에는 창이 끝나도 목표 20분 전`() {
        val min = 60_000L
        val target = 10_000 * min
        assertEquals(target, ReminderSync.wakeAt(target, target - 20 * min))
        assertEquals(target, ReminderSync.wakeAt(target, target - 5 * min))
        // 멀리 있으면 80분 전 (창 최대 1시간)
        assertEquals(target - 80 * min, ReminderSync.wakeAt(target, target - 13 * 60 * min))
        // 20~160분 사이: T + 0.75(T - now) <= 목표 - 20분
        listOf(21L, 40L, 80L, 120L, 159L).forEach { m ->
            val now = target - m * min
            val t = ReminderSync.wakeAt(target, now)
            assertTrue("$m", t > now)
            val windowEnd = t + minOf((0.75 * (t - now)).toLong(), 60 * min)
            assertTrue("$m: $windowEnd", windowEnd <= target - 20 * min + min)
        }
        // 가장 일찍 울려도 몇 단계 안에 마지막 단계에 닿는다
        var now = target - 13 * 60 * min
        var stages = 0
        while (target - now > 20 * min) {
            now = ReminderSync.wakeAt(target, now)
            stages++
        }
        assertTrue("stages=$stages", stages <= 12)
    }

    @Test
    fun `오늘의 링크는 같은 날 같은 결과, 후보가 없으면 null`() {
        val ids = (1..50).toList()
        assertEquals(RevisitRules.pick(ids, 20_000), RevisitRules.pick(ids, 20_000))
        assertTrue(RevisitRules.pick(ids, 20_000) in ids)
        assertNull(RevisitRules.pick(emptyList(), 20_000))
        // 날마다 바뀐다 (30일 중 같은 값만 나오지는 않음)
        assertTrue((20_000L until 20_030L).map { RevisitRules.pick(ids, it) }.toSet().size > 1)
    }

    @Test
    fun `오늘 고른 링크 기록 - 다른 날이거나 깨진 값이면 없음`() {
        val v = RevisitRules.encodePick(20_000, 7)
        assertEquals(7, RevisitRules.decodePick(v, 20_000))
        assertNull(RevisitRules.decodePick(v, 20_001))
        assertNull(RevisitRules.decodePick("", 20_000))
        assertNull(RevisitRules.decodePick("x:y", 20_000))
        assertNull(RevisitRules.decodePick(null, 20_000))
    }

    @Test
    fun `카드는 최근 저장 + 전체 + 필터 없음에서만`() {
        assertTrue(RevisitRules.isCardContext(isRecentTab = true, isAllCategory = true, hasDetailFilter = false))
        assertFalse(RevisitRules.isCardContext(isRecentTab = false, isAllCategory = true, hasDetailFilter = false))
        assertFalse(RevisitRules.isCardContext(isRecentTab = true, isAllCategory = false, hasDetailFilter = false))
        assertFalse(RevisitRules.isCardContext(isRecentTab = true, isAllCategory = true, hasDetailFilter = true))
    }

    @Test
    fun `주간 정리 - 둘 다 0 이면 보내지 않고, 같은 주에 두 번 보내지 않는다`() {
        assertFalse(RevisitRules.shouldSendWeekly(saved = 0, unread = 0, lastSentWeek = -1, thisWeek = 202642))
        assertTrue(RevisitRules.shouldSendWeekly(saved = 0, unread = 3, lastSentWeek = -1, thisWeek = 202642))
        assertTrue(RevisitRules.shouldSendWeekly(saved = 2, unread = 0, lastSentWeek = 202641, thisWeek = 202642))
        assertFalse(RevisitRules.shouldSendWeekly(saved = 2, unread = 3, lastSentWeek = 202642, thisWeek = 202642))
    }

    @Test
    fun `주간 정리 제안 카드 조건 - 원격 켜짐, 처음, 직접 저장 20개, 설치 7일`() {
        val day = 24 * hour
        val now = 100 * day
        fun show(remote: Boolean = true, state: Int = WeeklyPromptState.NOT_SHOWN, links: Int = 20, installed: Long = now - 7 * day) =
            RevisitRules.shouldShowWeeklyPrompt(remote, state, links, 20, installed, now)
        assertTrue(show())
        assertFalse(show(remote = false))
        assertFalse(show(state = WeeklyPromptState.DECLINED))
        assertFalse(show(state = WeeklyPromptState.AUTO_HIDDEN))
        assertFalse(show(links = 19))
        assertFalse(show(installed = now - 6 * day))
    }
}
