package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.repository.UrlRepository
import javax.inject.Inject

class IsSavedUrlUseCase @Inject constructor(
    private val repository: UrlRepository
) {
    suspend operator fun invoke(url: String): Boolean = repository.isSavedUrl(url)
}
