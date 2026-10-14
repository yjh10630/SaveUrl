package com.jinscompany.saveurl.domain.usecase

import androidx.paging.PagingSource
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.ui.FilterDefaults
import javax.inject.Inject

class GetUrlListUseCase @Inject constructor(
    private val repository: UrlRepository,
    private val lockSession: CategoryLockSession,
) {
    operator fun invoke(params: FilterParams?): PagingSource<Int, UrlData> =
        repository.getUrlList(params, includeLocked = includeLocked(params, lockSession.isUnlocked.value))

    companion object {
        /**
         * "전체"·"북마크"에는 세션이 열려 있어도 잠금 카테고리 링크를 섞지 않는다.
         * 카테고리를 골랐을 때만 세션을 따른다 (닫혀 있으면 잠금 카테고리를 골라도 비어 보인다).
         */
        fun includeLocked(params: FilterParams?, unlocked: Boolean): Boolean {
            val categories = params?.categories ?: listOf(FilterDefaults.CATEGORY_ALL)
            if (categories.contains(FilterDefaults.CATEGORY_ALL) || categories.contains(FilterDefaults.CATEGORY_BOOKMARK)) return false
            return unlocked
        }
    }
}
