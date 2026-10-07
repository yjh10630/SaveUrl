package com.jinscompany.saveurl.domain.model

import java.util.Calendar
import java.util.TimeZone

/** 홈 패널(2분할) 요약 숫자: 전체 링크 / 즐겨찾기 / 이번 주 저장 */
data class LinkCounts(
    val total: Int = 0,
    val bookmarks: Int = 0,
    val thisWeek: Int = 0,
)

/** 카테고리별 링크 수 (Room 집계 결과 컬럼 name, count) */
data class CategoryCount(
    val name: String,
    val count: Int,
)

data class LinkStats(
    val counts: LinkCounts = LinkCounts(),
    val topCategories: List<CategoryCount> = emptyList(),
)

/** 검색 범위 (검색 화면의 전체/제목/내용/태그 칩) */
enum class SearchScope { ALL, TITLE, DESCRIPTION, TAG }

/** [now] 가 속한 주의 월요일 0시(기기 시간대) 시각. "이번 주 저장" 집계 기준. */
fun startOfWeekMillis(now: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
    val cal = Calendar.getInstance(timeZone).apply {
        firstDayOfWeek = Calendar.MONDAY
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val daysSinceMonday = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    cal.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
    return cal.timeInMillis
}
