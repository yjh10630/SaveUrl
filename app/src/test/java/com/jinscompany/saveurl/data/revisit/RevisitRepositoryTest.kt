package com.jinscompany.saveurl.data.revisit

import android.content.Context
import com.jinscompany.saveurl.data.room.BaseSaveUrlDao
import com.jinscompany.saveurl.data.room.LinkReminderDao
import com.jinscompany.saveurl.domain.model.LinkReminder
import com.jinscompany.saveurl.domain.model.UpcomingReminder
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.revisit.ReminderPreset
import com.jinscompany.saveurl.domain.revisit.ReminderTimes
import com.jinscompany.saveurl.domain.revisit.RevisitRules
import com.jinscompany.saveurl.utils.PreferencesManager
import com.jinscompany.saveurl.utils.RemoteFlags
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 링크 알림 예약·발송, 주간 정리, 다시 볼 링크 고르기 (42번). 시계를 고정하고 DAO·알람·알림은 가짜로 바꾼다.
 */
class RevisitRepositoryTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private var now = ZonedDateTime.of(2026, 10, 14, 12, 0, 0, 0, zone).toInstant().toEpochMilli() // 수 12:00
    private val hour = 60 * 60_000L
    private val day = 24 * hour

    private val reminderDao = mockk<LinkReminderDao>(relaxed = true)
    private val urlDao = mockk<BaseSaveUrlDao>(relaxed = true)
    private val alarms = mockk<ReminderAlarms>(relaxed = true)
    private val notifier = mockk<RevisitNotifier>(relaxed = true)
    private val prefs = mockk<PreferencesManager>(relaxed = true)
    private val remote = mockk<RemoteFlags>(relaxed = true)
    private val clock = object : RevisitClock {
        override fun now() = this@RevisitRepositoryTest.now
        override fun zone() = this@RevisitRepositoryTest.zone
    }
    private val repo = RevisitRepository(mockk<Context>(relaxed = true), reminderDao, urlDao, alarms, notifier, prefs, remote, clock)

    private fun upcoming(linkId: Int, at: Long, locked: Boolean = false) =
        UpcomingReminder(linkId, at, "https://e.com/$linkId", "제목$linkId", null, "e", now - 14 * day, "개발", locked)

    private fun weeklyOff() {
        every { prefs.weeklyDigestEnabled } returns flowOf(false)
        every { remote.weeklyDigestEnabled } returns MutableStateFlow(true)
    }

    private fun weeklyOn(lastSentWeek: Int = -1) {
        every { prefs.weeklyDigestEnabled } returns flowOf(true)
        every { remote.weeklyDigestEnabled } returns MutableStateFlow(true)
        every { prefs.weeklyLastSentWeek } returns flowOf(lastSentWeek)
        coEvery { prefs.claimWeeklySent(any()) } returns true
    }

    private fun reminder(linkId: Int, at: Long) = LinkReminder(linkId = linkId, remindAt = at, createdAt = 0, preset = "tonight")

    @Test
    fun `예약하면 행을 덮어쓰고 가장 이른 예약 하나에만 알람을 건다, 끄면 남은 것으로 다시 건다`() = runTest {
        weeklyOff()
        val saved = slot<LinkReminder>()
        coEvery { reminderDao.upsert(capture(saved)) } returns 1L
        coEvery { reminderDao.getAll() } returns listOf(reminder(7, now + day), reminder(9, now + 3 * hour))
        repo.setReminder(7, now + day, ReminderPreset.TOMORROW)
        assertEquals(7, saved.captured.linkId)
        assertEquals("tomorrow", saved.captured.preset)
        verify { alarms.arm(now + 3 * hour, now) }

        coEvery { reminderDao.getAll() } returns emptyList()
        repo.cancelReminder(9)
        coVerify { reminderDao.delete(9) }
        verify { alarms.arm(null, now) }
    }

    @Test
    fun `알람이 울리면 기한이 된 것을 띄운 뒤에 지우고(순서), 다음 예약으로 다시 건다 - 잠금 링크도 같은 경로`() = runTest {
        weeklyOff()
        coEvery { reminderDao.getOrphanLinkIds() } returns emptyList()
        coEvery { reminderDao.getAll() } returnsMany listOf(
            listOf(reminder(3, now - 60_000L), reminder(5, now + 3 * hour)),
            listOf(reminder(5, now + 3 * hour)),
        )
        coEvery { reminderDao.getUpcoming(3) } returns upcoming(3, now - 60_000L, locked = true)
        repo.onTick()
        coVerifyOrder {
            notifier.notifyLink(match { it.linkId == 3 && it.locked }, null, now)
            reminderDao.delete(3)
        }
        verify { alarms.arm(now + 3 * hour, now) }
    }

    @Test
    fun `일찍 울렸으면(1분 넘게 남음) 띄우지 않고 다시 건다`() = runTest {
        weeklyOff()
        coEvery { reminderDao.getAll() } returns listOf(reminder(5, now + 3 * hour))
        repo.onTick()
        verify(exactly = 0) { notifier.notifyLink(any(), any(), any()) }
        coVerify(exactly = 0) { reminderDao.delete(5) }
        verify { alarms.arm(now + 3 * hour, now) }
    }

    @Test
    fun `내일 다시 - 알림을 지우고 내일 9시로 새로 예약`() = runTest {
        weeklyOff()
        coEvery { urlDao.getById(8) } returns UrlData(id = 8, url = "https://e.com/8")
        coEvery { reminderDao.get(8) } returns null
        val saved = slot<LinkReminder>()
        coEvery { reminderDao.upsert(capture(saved)) } returns 1L
        repo.snooze(8)
        verify { notifier.cancelLink(8) }
        assertEquals(ReminderTimes.tomorrowMorning(now, zone), saved.captured.remindAt)
        assertEquals("snooze", saved.captured.preset)
    }

    @Test
    fun `내일 다시 - 그사이 앱에서 다시 예약했으면 그 예약을 덮어쓰지 않는다`() = runTest {
        coEvery { urlDao.getById(8) } returns UrlData(id = 8, url = "https://e.com/8")
        coEvery { reminderDao.get(8) } returns reminder(8, now + 7 * day)
        repo.snooze(8)
        verify { notifier.cancelLink(8) }
        coVerify(exactly = 0) { reminderDao.upsert(any()) }
    }

    @Test
    fun `다시 맞추기 - 링크 없는 예약 정리, 지난 예약은 묶어서 띄움(썸네일 없이), 오래된 건 버림, 주간 꺼짐이면 링크 예약만`() = runTest {
        weeklyOff()
        coEvery { reminderDao.getOrphanLinkIds() } returns listOf(99)
        val all = listOf(reminder(1, now + day), reminder(2, now - hour), reminder(6, now - 2 * hour), reminder(3, now - 2 * day))
        coEvery { reminderDao.getAll() } returnsMany listOf(all, listOf(reminder(1, now + day)))
        coEvery { reminderDao.getUpcoming(2) } returns upcoming(2, now - hour)
        coEvery { reminderDao.getUpcoming(6) } returns upcoming(6, now - 2 * hour)

        repo.syncAll()

        coVerify { reminderDao.deleteAll(listOf(99)) }
        coVerify { reminderDao.deleteAll(listOf(3)) }
        verify { notifier.notifyLink(match { it.linkId == 2 }, null, now) }
        verify { notifier.notifyLink(match { it.linkId == 6 }, null, now) }
        coVerify { reminderDao.delete(2) }
        coVerify { reminderDao.delete(6) }
        verify { notifier.updateSummary() }
        verify { alarms.arm(now + day, now) }
    }

    @Test
    fun `주간 정리 - 일요일 8시가 지나 울리면 그 주 숫자로 한 번 보내고 다음 일요일로 다시 건다`() = runTest {
        now = ZonedDateTime.of(2026, 10, 18, 20, 5, 0, 0, zone).toInstant().toEpochMilli() // 일 20:05
        weeklyOn()
        val sunday20 = ZonedDateTime.of(2026, 10, 18, 20, 0, 0, 0, zone).toInstant().toEpochMilli()
        coEvery { urlDao.countSavedBetween(ReminderTimes.weekStart(sunday20, zone), sunday20) } returns 4
        coEvery { urlDao.countUnreadDirect() } returns 2
        coEvery { reminderDao.getAll() } returns emptyList()

        repo.onTick()

        verify { notifier.notifyWeekly(4, 2) }
        val nextSunday = ZonedDateTime.of(2026, 10, 25, 20, 0, 0, 0, zone).toInstant().toEpochMilli()
        verify { alarms.arm(nextSunday, now) }
    }

    @Test
    fun `주간 정리 - 월요일 1시에 늦게 울려도 지난주(울린 시각이 아닌 일요일) 기준으로 센다`() = runTest {
        now = ZonedDateTime.of(2026, 10, 19, 1, 0, 0, 0, zone).toInstant().toEpochMilli() // 월 01:00
        weeklyOn()
        val sunday20 = ZonedDateTime.of(2026, 10, 18, 20, 0, 0, 0, zone).toInstant().toEpochMilli()
        coEvery { urlDao.countSavedBetween(ReminderTimes.weekStart(sunday20, zone), sunday20) } returns 6
        coEvery { urlDao.countUnreadDirect() } returns 1
        coEvery { reminderDao.getAll() } returns emptyList()

        repo.onTick()

        verify { notifier.notifyWeekly(6, 1) }
        coVerify { prefs.claimWeeklySent(ReminderTimes.isoWeekKey(sunday20, zone)) }
    }

    @Test
    fun `주간 정리 - 일요일 8시 전이면 보내지 않고 일요일 8시로 건다`() = runTest {
        now = ZonedDateTime.of(2026, 10, 18, 18, 40, 0, 0, zone).toInstant().toEpochMilli() // 일 18:40
        weeklyOn()
        coEvery { reminderDao.getAll() } returns emptyList()
        repo.onTick()
        verify(exactly = 0) { notifier.notifyWeekly(any(), any()) }
        val sunday20 = ZonedDateTime.of(2026, 10, 18, 20, 0, 0, 0, zone).toInstant().toEpochMilli()
        verify { alarms.arm(sunday20, now) }
    }

    @Test
    fun `주간 정리 - 12시간 넘게 늦게 깨면 이번 주는 건너뛴다, 이미 보낸 주도 다시 보내지 않는다`() = runTest {
        now = ZonedDateTime.of(2026, 10, 19, 10, 0, 0, 0, zone).toInstant().toEpochMilli() // 월 10:00
        weeklyOn()
        coEvery { reminderDao.getAll() } returns emptyList()
        repo.onTick()
        verify(exactly = 0) { notifier.notifyWeekly(any(), any()) }

        now = ZonedDateTime.of(2026, 10, 18, 20, 30, 0, 0, zone).toInstant().toEpochMilli()
        val sunday20 = ZonedDateTime.of(2026, 10, 18, 20, 0, 0, 0, zone).toInstant().toEpochMilli()
        weeklyOn(lastSentWeek = ReminderTimes.isoWeekKey(sunday20, zone))
        repo.onTick()
        verify(exactly = 0) { notifier.notifyWeekly(any(), any()) }
    }

    @Test
    fun `주간 정리 - 원격으로 꺼지면 보내지 않고, 링크 예약도 없으면 알람을 취소`() = runTest {
        every { prefs.weeklyDigestEnabled } returns flowOf(true)
        every { remote.weeklyDigestEnabled } returns MutableStateFlow(false)
        coEvery { reminderDao.getAll() } returns emptyList()
        repo.onTick()
        verify(exactly = 0) { notifier.notifyWeekly(any(), any()) }
        verify { alarms.arm(null, now) }
    }

    @Test
    fun `떠 있는 알림 정리 - 일반 문구로 뜬 뒤 잠긴 링크와 지워진 링크만 지우고, 잠금 문구로 뜬 건 둔다`() = runTest {
        weeklyOff()
        every { notifier.shownLinks() } returns listOf(1 to false, 2 to true, 3 to false, 4 to false)
        coEvery { urlDao.getById(1) } returns UrlData(id = 1, url = "https://e.com/1")
        coEvery { urlDao.isLocked(1) } returns 1
        coEvery { urlDao.getById(2) } returns UrlData(id = 2, url = "https://e.com/2")
        coEvery { urlDao.isLocked(2) } returns 1
        coEvery { urlDao.getById(3) } returns null
        coEvery { urlDao.getById(4) } returns UrlData(id = 4, url = "https://e.com/4")
        coEvery { urlDao.isLocked(4) } returns 0
        coEvery { reminderDao.getOrphanLinkIds() } returns listOf(3)
        coEvery { reminderDao.getAll() } returns emptyList()

        repo.refreshShownNotifications()

        verify { notifier.cancelLink(1) }
        verify { notifier.cancelLink(3) }
        verify(exactly = 0) { notifier.cancelLink(2) }
        verify(exactly = 0) { notifier.cancelLink(4) }
        coVerify { reminderDao.deleteAll(listOf(3)) }
        verify { alarms.arm(null, now) }
    }

    @Test
    fun `다시 볼 링크 - 오늘 고른 링크가 있으면 그대로, 읽었으면 그날은 없음`() = runTest {
        val today = ReminderTimes.epochDay(now, zone)
        every { prefs.revisitCardPick } returns flowOf(RevisitRules.encodePick(today, 5))
        coEvery { urlDao.getById(5) } returns UrlData(id = 5, url = "https://e.com/5", isRead = false)
        coEvery { urlDao.isLocked(5) } returns 0
        coEvery { reminderDao.get(5) } returns null
        assertEquals(5, repo.todaysRevisitLink()?.id)

        coEvery { urlDao.getById(5) } returns UrlData(id = 5, url = "https://e.com/5", isRead = true)
        assertNull(repo.todaysRevisitLink())
        coVerify(exactly = 0) { urlDao.getRevisitCandidates(any(), any(), any()) }
    }

    @Test
    fun `다시 볼 링크 - 새로 고르면 기록하고 표시 시각을 남긴다, 링크가 10개 미만이면 없음`() = runTest {
        every { prefs.revisitCardPick } returns flowOf("")
        every { remote.revisitMinAgeDays } returns 7
        coEvery { urlDao.countNotLocked() } returns 9
        assertNull(repo.todaysRevisitLink())

        coEvery { urlDao.countNotLocked() } returns 30
        coEvery { urlDao.getRevisitCandidates(now - 7 * day, now - RevisitRules.SHOWN_COOLDOWN_MS, RevisitRules.CANDIDATE_LIMIT) } returns
            listOf(UrlData(id = 11, url = "https://e.com/11"))
        assertEquals(11, repo.todaysRevisitLink()?.id)
        coVerify { prefs.setRevisitCardPick(RevisitRules.encodePick(ReminderTimes.epochDay(now, zone), 11)) }
        coVerify { urlDao.setRevisitShownAt(11, now) }
    }
}
