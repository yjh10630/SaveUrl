package com.jinscompany.saveurl.domain.usecase

import androidx.paging.PagingSource
import com.jinscompany.saveurl.domain.model.SearchScope
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.UrlRepository
import javax.inject.Inject

/** 정렬(최신순/과거순)을 지정한 검색. 2분할 검색 화면의 필터 패널이 켜져 있을 때만 쓴다. */
class SearchSortedUseCase @Inject constructor(
    private val repository: UrlRepository
) {
    operator fun invoke(keyword: String, scope: SearchScope, oldest: Boolean): PagingSource<Int, UrlData> =
        repository.searchSorted(keyword, scope, oldest)
}
