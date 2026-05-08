package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.UrlRepository
import javax.inject.Inject

class FindUrlDataUseCase @Inject constructor(
    private val repository: UrlRepository
) {
    suspend operator fun invoke(url: String): UrlData? = repository.findUrlData(url)
}
