package com.jinscompany.saveurl.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashRetentionTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L

    @Test
    fun `방금 삭제한 항목은 7일 후 삭제`() {
        assertEquals(TrashRetention.Remaining.Days(7), TrashRetention.remaining(now, now, debug = false))
    }

    @Test
    fun `남은 시간은 올림해서 일 단위로 표시`() {
        // 5일 1시간 남음 → 6일
        val deleted = now - (2 * day - 60 * 60 * 1000L)
        assertEquals(TrashRetention.Remaining.Days(6), TrashRetention.remaining(deleted, now, debug = false))
    }

    @Test
    fun `24시간 이내면 임박`() {
        val deleted = now - (6 * day + 1)
        val r = TrashRetention.remaining(deleted, now, debug = false)
        assertEquals(TrashRetention.Remaining.WithinADay, r)
        assertTrue(r.isUrgent)
    }

    @Test
    fun `보관 기간이 지나면 Expired`() {
        val deleted = now - 7 * day
        assertEquals(TrashRetention.Remaining.Expired, TrashRetention.remaining(deleted, now, debug = false))
    }

    @Test
    fun `2일 이상 남으면 임박 아님`() {
        assertFalse(TrashRetention.Remaining.Days(2).isUrgent)
    }

    @Test
    fun `debug 는 15분 보관, 정리 기준 시각과 일치`() {
        assertEquals(now - 15 * 60 * 1000L, TrashRetention.purgeThreshold(now, debug = true))
        assertEquals(now - 7 * day, TrashRetention.purgeThreshold(now, debug = false))
        assertEquals(TrashRetention.Remaining.WithinADay, TrashRetention.remaining(now, now, debug = true))
    }
}
