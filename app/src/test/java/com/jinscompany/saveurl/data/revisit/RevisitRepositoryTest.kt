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
    private val now = ZonedDateTime.of(2026, 10, 14, 12, 0, 0, 0, zone).toInstant().toEpochMilli() // 수 12:00
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

    @Test
    fun `예약하면 행을 덮어쓰고 알람을 건다, 끄면 행과 알람을 지운다`() = runTest {
        val saved = slot<LinkReminder>()
        coEvery { reminderDao.upsert(capture(saved)) } returns 1L
        repo.setReminder(7, now + day, ReminderPreset.TOMORROW)
        assertEquals(7, saved.captured.linkId)
        assertEquals(now + day, saved.captured.remindAt)
        assertEquals("tomorrow", saved.captured.preset)
        verify { alarms.scheduleLink(7, now + day) }

        repo.cancelReminder(7)
        coVerify { reminderDao.delete(7) }
        verify { alarms.cancelLink(7) }
    }

    @Test
    fun `알람이 울리면 알림을 띄우고 예약을 지운다 - 잠금 링크도 같은 경로 (문구는 알림이 가림)`() = runTest {
        coEvery { reminderDao.getUpcoming(3) } returns upcoming(3, now - 60_000L, locked = true)
        repo.onLinkAlarm(3)
        coVerify { reminderDao.delete(3) }
        verify { notifier.notifyLink(match { it.linkId == 3 && it.locked }, null, now) }
    }

    @Test
    fun `링크가 지워졌으면 아무것도 띄우지 않는다`() = runTest {
        coEvery { reminderDao.getUpcoming(4) } returns null
        repo.onLinkAlarm(4)
        coVerify { reminderDao.delete(4) }
        verify(exactly = 0) { notifier.notifyLink(any(), any(), any()) }
    }

    @Test
    fun `시간을 뒤로 바꿔서 일찍 울린 알람은 다시 건다`() = runTest {
        coEvery { reminderDao.getUpcoming(5) } returns upcoming(5, now + 3 * hour)
        repo.onLinkAlarm(5)
        verify { alarms.scheduleLink(5, now + 3 * hour) }
        verify(exactly = 0) { notifier.notifyLink(any(), any(), any()) }
        coVerify(exactly = 0) { reminderDao.delete(5) }
    }

    @Test
    fun `내일 다시 - 알림을 지우고 내일 9시로 새로 예약`() = runTest {
        coEvery { urlDao.getById(8) } returns UrlData(id = 8, url = "https://e.com/8")
        val saved = slot<LinkReminder>()
        coEvery { reminderDao.upsert(capture(saved)) } returns 1L
        repo.snooze(8)
        verify { notifier.cancelLink(8) }
        assertEquals(ReminderTimes.tomorrowMorning(now, zone), saved.captured.remindAt)
        assertEquals("snooze", saved.captured.preset)
    }

    @Test
    fun `다시 맞추기 - 링크 없는 예약 정리, 지난 예약 묶어서 띄움, 오래된 건 버림, 주간 정리는 꺼져 있으면 취소`() = runTest {
        coEvery { reminderDao.getOrphanLinkIds() } returns listOf(99)
        coEvery { reminderDao.getAll() } returns listOf(
            LinkReminder(linkId = 1, remindAt = now + day, createdAt = 0, preset = "tomorrow"),
            LinkReminder(linkId = 2, remindAt = now - hour, createdAt = 0, preset = "tonight"),
            LinkReminder(linkId = 6, remindAt = now - 2 * hour, createdAt = 0, preset = "tonight"),
            LinkReminder(linkId = 3, remindAt = now - 2 * day, createdAt = 0, preset = "tonight"),
        )
        coEvery { reminderDao.getUpcoming(2) } returns upcoming(2, now - hour)
        coEvery { reminderDao.getUpcoming(6) } returns upcoming(6, now - 2 * hour)
        every { prefs.weeklyDigestEnabled } returns flowOf(false)
        every { remote.weeklyDigestEnabled } returns MutableStateFlow(true)

        repo.syncAll()

        verify { alarms.cancelLink(99) }
        coVerify { reminderDao.deleteAll(listOf(99)) }
        verify { alarms.scheduleLink(1, now + day) }
        coVerify { reminderDao.deleteAll(listOf(3)) }
        coVerify { reminderDao.deleteAll(listOf(6, 2)) }
        verify { notifier.notifyLink(match { it.linkId == 2 }, any(), now) }
        verify { notifier.notifyLink(match { it.linkId == 6 }, any(), now) }
        verify { notifier.notifyLinkSummary(2) }
        verify { alarms.cancelWeekly() }
    }

    @Test
    fun `주간 정리 - 켜져 있으면 이번 주 숫자로 한 번 보내고 다음 일요일 예약`() = runTest {
        every { prefs.weeklyDigestEnabled } returns flowOf(true)
        every { remote.weeklyDigestEnabled } returns MutableStateFlow(true)
        every { prefs.weeklyLastSentWeek } returns flowOf(-1)
        coEvery { prefs.claimWeeklySent(any()) } returns true
        coEvery { urlDao.countSavedSince(ReminderTimes.weekStart(now, zone)) } returns 4
        coEvery { urlDao.countUnreadDirect() } returns 2

        repo.onWeeklyAlarm()

        verify { notifier.notifyWeekly(4, 2) }
        verify { alarms.scheduleWeekly(ReminderTimes.nextWeeklyDigest(now + 60_000L, zone)) }
    }

    @Test
    fun `주간 정리 - 저장도 안 읽음도 0 이면 보내지 않지만 다음 주는 예약`() = runTest {
        every { prefs.weeklyDigestEnabled } returns flowOf(true)
        every { remote.weeklyDigestEnabled } returns MutableStateFlow(true)
        every { prefs.weeklyLastSentWeek } returns flowOf(-1)
        coEvery { urlDao.countSavedSince(any()) } returns 0
        coEvery { urlDao.countUnreadDirect() } returns 0

        repo.onWeeklyAlarm()

        verify(exactly = 0) { notifier.notifyWeekly(any(), any()) }
        verify { alarms.scheduleWeekly(any()) }
    }

    @Test
    fun `주간 정리 - 원격으로 꺼지면 보내지 않고 예약을 취소`() = runTest {
        every { prefs.weeklyDigestEnabled } returns flowOf(true)
        every { remote.weeklyDigestEnabled } returns MutableStateFlow(false)
        repo.onWeeklyAlarm()
        verify(exactly = 0) { notifier.notifyWeekly(any(), any()) }
        verify { alarms.cancelWeekly() }
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
