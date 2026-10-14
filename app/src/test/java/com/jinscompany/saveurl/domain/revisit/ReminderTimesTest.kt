package com.jinscompany.saveurl.domain.revisit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** 링크 알림 시각 계산 (42번 §2.2, §4.2). 2026-10-12 는 월요일 */
class ReminderTimesTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0, zone: ZoneId = seoul) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private fun presets(now: Long) = ReminderTimes.options(now, seoul).map { it.preset }

    @Test
    fun `평일 오후 6시 59분 - 오늘 저녁 보임, 이번 주말은 다가오는 토요일 10시`() {
        val now = at(2026, 10, 14, 18, 59) // 수
        val options = ReminderTimes.options(now, seoul)
        assertEquals(listOf(ReminderPreset.TONIGHT, ReminderPreset.TOMORROW, ReminderPreset.WEEKEND, ReminderPreset.NEXT_WEEK), options.map { it.preset })
        assertEquals(at(2026, 10, 14, 20), options[0].at)
        assertEquals(at(2026, 10, 15, 9), options[1].at)
        assertEquals(at(2026, 10, 17, 10), options[2].at)
        assertEquals(at(2026, 10, 19, 9), options[3].at)
    }

    @Test
    fun `저녁 7시 이후에는 오늘 저녁이 없다`() {
        assertFalse(ReminderPreset.TONIGHT in presets(at(2026, 10, 14, 19, 1)))
        assertFalse(ReminderPreset.TONIGHT in presets(at(2026, 10, 14, 19, 0)))
    }

    @Test
    fun `토요일 10시 전은 오늘 10시가 이번 주말, 10시 이후와 일요일은 다음 주말`() {
        val satMorning = ReminderTimes.options(at(2026, 10, 17, 9, 30), seoul).first { it.preset == ReminderPreset.WEEKEND }
        assertEquals(at(2026, 10, 17, 10), satMorning.at)
        val satNoon = ReminderTimes.options(at(2026, 10, 17, 12), seoul).first { it.preset == ReminderPreset.NEXT_WEEKEND }
        assertEquals(at(2026, 10, 24, 10), satNoon.at)
        val sunday = ReminderTimes.options(at(2026, 10, 18, 9), seoul)
        assertFalse(sunday.any { it.preset == ReminderPreset.WEEKEND })
        assertEquals(at(2026, 10, 24, 10), sunday.first { it.preset == ReminderPreset.NEXT_WEEKEND }.at)
        // 일요일의 "다음 주"는 바로 다음 날 월요일 9시
        assertEquals(at(2026, 10, 19, 9), sunday.first { it.preset == ReminderPreset.NEXT_WEEK }.at)
    }

    @Test
    fun `직접 고르기는 5분 뒤부터 1년 안`() {
        val now = at(2026, 10, 14, 12)
        assertFalse(ReminderTimes.isValidCustom(now + 60_000L, now))
        assertTrue(ReminderTimes.isValidCustom(now + 5 * 60_000L, now))
        assertTrue(ReminderTimes.isValidCustom(now + 364L * 24 * 60 * 60_000L, now))
        assertFalse(ReminderTimes.isValidCustom(now + 366L * 24 * 60 * 60_000L, now))
        assertFalse(ReminderTimes.isValidCustom(now - 60_000L, now))
    }

    @Test
    fun `주간 정리는 일요일 저녁 8시, 일요일 8시가 지나면 다음 주`() {
        assertEquals(at(2026, 10, 18, 20), ReminderTimes.nextWeeklyDigest(at(2026, 10, 14, 12), seoul))
        assertEquals(at(2026, 10, 18, 20), ReminderTimes.nextWeeklyDigest(at(2026, 10, 18, 19, 59), seoul))
        assertEquals(at(2026, 10, 25, 20), ReminderTimes.nextWeeklyDigest(at(2026, 10, 18, 20, 1), seoul))
        // 울린 직후 다음 예약 (+1분)
        assertEquals(at(2026, 10, 25, 20), ReminderTimes.nextWeeklyDigest(at(2026, 10, 18, 20) + 60_000L, seoul))
    }

    @Test
    fun `이번 주는 월요일 0시부터, 같은 주 키는 같다`() {
        assertEquals(at(2026, 10, 12, 0), ReminderTimes.weekStart(at(2026, 10, 18, 23), seoul))
        assertEquals(ReminderTimes.isoWeekKey(at(2026, 10, 12, 0), seoul), ReminderTimes.isoWeekKey(at(2026, 10, 18, 23, 59), seoul))
        assertTrue(ReminderTimes.isoWeekKey(at(2026, 10, 19, 0), seoul) != ReminderTimes.isoWeekKey(at(2026, 10, 18, 23), seoul))
    }

    @Test
    fun `시간대가 바뀌어도 절대 시각은 같은 순간 - 표기만 현지`() {
        val remindAt = at(2026, 10, 15, 9) // 서울 9시
        val la = ZoneId.of("America/Los_Angeles")
        val nowLa = at(2026, 10, 14, 12, zone = la)
        // LA 에서는 전날 17시로 보인다
        assertEquals("오후 5:00", ReminderTimes.shortLabel(remindAt, nowLa, la))
    }

    @Test
    fun `목록 표기 - 오늘, 내일, 7일 안 요일, 그 뒤 날짜`() {
        val now = at(2026, 10, 14, 10)
        assertEquals("오후 8:00", ReminderTimes.shortLabel(at(2026, 10, 14, 20), now, seoul))
        assertEquals("내일 오전 9:00", ReminderTimes.shortLabel(at(2026, 10, 15, 9), now, seoul))
        assertEquals("토 오전 10:00", ReminderTimes.shortLabel(at(2026, 10, 17, 10), now, seoul))
        assertEquals("10월 24일", ReminderTimes.shortLabel(at(2026, 10, 24, 9), now, seoul))
        assertEquals("10월 15일 (목) 오전 9:00", ReminderTimes.sheetLabel(at(2026, 10, 15, 9), now, seoul))
        assertEquals("오늘", ReminderTimes.groupLabel(at(2026, 10, 14, 20), now, seoul))
        assertEquals("내일", ReminderTimes.groupLabel(at(2026, 10, 15, 9), now, seoul))
        assertEquals("10월 17일 (토)", ReminderTimes.groupLabel(at(2026, 10, 17, 10), now, seoul))
    }

    @Test
    fun `저장 경과 표기`() {
        val day = 24 * 60 * 60_000L
        val now = 1000 * day
        assertNull(ReminderTimes.savedAgo(now - 1000, now))
        assertEquals("3일", ReminderTimes.savedAgo(now - 3 * day, now))
        assertEquals("2주", ReminderTimes.savedAgo(now - 15 * day, now))
        assertEquals("2개월", ReminderTimes.savedAgo(now - 61 * day, now))
        assertEquals("1년", ReminderTimes.savedAgo(now - 400 * day, now))
    }
}
