package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.utils.UrlNormalizer
import javax.inject.Inject

sealed class SaveResult {
    data class Success(val urlData: UrlData) : SaveResult()
    data class Duplicate(val existing: UrlData) : SaveResult()
    data object Error : SaveResult()
}

class SaveUrlUseCase @Inject constructor(
    private val repository: UrlRepository
) {
    suspend operator fun invoke(data: UrlData, force: Boolean = false): SaveResult {
        if (!force) {
            val normalized = UrlNormalizer.normalize(data.url ?: "")
            val existing = repository.findByNormalizedUrl(normalized)
            if (existing != null) {
                return SaveResult.Duplicate(existing)
            }
        }
        val success = repository.saveUrl(data)
        return if (success) SaveResult.Success(data) else SaveResult.Error
    }
}
