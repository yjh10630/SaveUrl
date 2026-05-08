package com.jinscompany.saveurl.domain.usecase

import androidx.paging.PagingSource
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.UrlRepository
import javax.inject.Inject

class SearchByDescriptionUseCase @Inject constructor(
    private val repository: UrlRepository
) {
    operator fun invoke(keyword: String): PagingSource<Int, UrlData> = repository.searchByDescription(keyword)
}
