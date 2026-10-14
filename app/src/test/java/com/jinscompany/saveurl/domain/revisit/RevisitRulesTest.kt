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
    fun `처리 계획 - 기한이 된 것(1분 이내 포함)은 지금, 24시간 넘게 지난 건 버리고, 다음 알람은 가장 이른 남은 예약`() {
        val now = 1_000 * hour
        val plan = ReminderSync.plan(
            listOf(
                1 to now + hour, 2 to now - hour, 3 to now - 25 * hour, 4 to now - 24 * hour,
                5 to now + 2 * hour, 6 to now + 30_000L,
            ),
            now,
        )
        assertEquals(listOf(4, 2, 6), plan.fireNow)
        assertEquals(listOf(3), plan.drop)
        assertEquals(now + hour, plan.nextAt)
        assertNull(ReminderSync.plan(emptyList(), now).nextAt)
    }

    @Test
    fun `다음 알람은 링크 예약과 주간 정리 중 먼저 오는 것 하나`() {
        assertEquals(5L, ReminderSync.nextWake(9L, null, 5L))
        assertNull(ReminderSync.nextWake(null, null))
    }

    @Test
    fun `Doze 대비 알람 - Android 12+ 는 목표 그대로, 11 이하는 창 끝이 목표를 넘지 않게 앞당겨 몇 단계 안에 닿는다`() {
        val min = 60_000L
        val target = 100_000 * min
        assertEquals(target, ReminderSync.idleWakeAt(target, target - 7 * 24 * 60 * min, 31))
        assertEquals(target, ReminderSync.idleWakeAt(target, target - 20 * min, 30))
        listOf(13 * 60L, 7 * 24 * 60L, 45L, 31L).forEach { m ->
            var now = target - m * min
            var stages = 0
            while (true) {
                val t = ReminderSync.idleWakeAt(target, now, 26)
                assertTrue("$m: 앞으로 간다", t > now)
                if (t == target) break
                // Android 11 이하의 창: 남은 시간의 75% (상한 없음). 가장 늦게 울려도 목표 전
                val windowEnd = t + (0.75 * (t - now)).toLong()
                assertTrue("$m: $windowEnd", windowEnd <= target)
                now = windowEnd // 가장 늦게 울린 경우로 이어 간다
                stages++
            }
            assertTrue("$m: stages=$stages", stages <= 14)
            // 마지막 알람은 30분 안쪽에서 걸려 창이 22.5분 이하
            assertTrue(target - now <= 30 * min)
        }
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
