package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.model.CategoryCount
import com.jinscompany.saveurl.domain.model.LinkCounts
import com.jinscompany.saveurl.domain.model.startOfWeekMillis
import com.jinscompany.saveurl.domain.repository.UrlRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class GetLinkStatsUseCaseTest {

    private val seoul = TimeZone.getTimeZone("Asia/Seoul")

    private fun millis(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        Calendar.getInstance(seoul).apply {
            clear()
            set(y, m - 1, d, h, min)
        }.timeInMillis

    @Test
    fun `이번 주 시작은 월요일 0시`() {
        // 2026-10-07 은 수요일
        assertEquals(millis(2026, 10, 5), startOfWeekMillis(millis(2026, 10, 7, 15, 30), seoul))
        // 월요일 0시 그대로
        assertEquals(millis(2026, 10, 5), startOfWeekMillis(millis(2026, 10, 5), seoul))
        // 일요일 밤은 그 주 월요일로 (일요일 시작이 아님)
        assertEquals(millis(2026, 10, 5), startOfWeekMillis(millis(2026, 10, 11, 23, 59), seoul))
        // 연말을 넘는 주
        assertEquals(millis(2025, 12, 29), startOfWeekMillis(millis(2026, 1, 1, 9), seoul))
    }

    @Test
    fun `요약 숫자와 카테고리 상위 목록을 합친다`() = runTest {
        val repository: UrlRepository = mockk()
        every { repository.observeLinkCounts(123L) } returns flowOf(LinkCounts(total = 10, bookmarks = 3, thisWeek = 2))
        every { repository.observeTopCategoryCounts(GetLinkStatsUseCase.TOP_CATEGORY_LIMIT) } returns
            flowOf(listOf(CategoryCount("개발", 5), CategoryCount("뉴스", 2)))

        val stats = GetLinkStatsUseCase(repository)(123L).first()

        assertEquals(LinkCounts(10, 3, 2), stats.counts)
        assertEquals(listOf("개발", "뉴스"), stats.topCategories.map { it.name })
        assertEquals(6, GetLinkStatsUseCase.TOP_CATEGORY_LIMIT)
    }
}
