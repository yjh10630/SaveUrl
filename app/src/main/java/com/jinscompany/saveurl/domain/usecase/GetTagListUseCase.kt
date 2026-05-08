package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.repository.UrlRepository
import javax.inject.Inject

class GetTagListUseCase @Inject constructor(
    private val repository: UrlRepository
) {
    suspend operator fun invoke(): List<String> = repository.getTagList()
}
