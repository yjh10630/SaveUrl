package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.model.LinkStats
import com.jinscompany.saveurl.domain.repository.UrlRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** 홈 패널(2분할) 요약: 전체/즐겨찾기/이번 주 저장 수 + 링크가 많은 카테고리 상위 [TOP_CATEGORY_LIMIT]개 */
class GetLinkStatsUseCase @Inject constructor(
    private val repository: UrlRepository
) {
    operator fun invoke(weekStartMillis: Long): Flow<LinkStats> =
        combine(
            repository.observeLinkCounts(weekStartMillis),
            repository.observeTopCategoryCounts(TOP_CATEGORY_LIMIT),
        ) { counts, categories -> LinkStats(counts = counts, topCategories = categories) }
            .distinctUntilChanged()

    companion object {
        const val TOP_CATEGORY_LIMIT = 6
    }
}
