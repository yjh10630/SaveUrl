package com.jinscompany.saveurl.domain.usecase

import androidx.paging.PagingSource
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.UrlRepository
import javax.inject.Inject

class GetUrlListUseCase @Inject constructor(
    private val repository: UrlRepository
) {
    operator fun invoke(params: FilterParams?): PagingSource<Int, UrlData> = repository.getUrlList(params)
}
