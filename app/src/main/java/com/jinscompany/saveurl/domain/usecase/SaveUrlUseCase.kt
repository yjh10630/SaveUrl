package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.utils.TrackingParamStripper
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
        // 저장되는 URL 에서 추적 파라미터(utm_*, fbclid 등) 제거
        val cleaned = data.url?.let { data.copy(url = TrackingParamStripper.strip(it)) } ?: data
        if (!force) {
            val normalized = UrlNormalizer.normalize(cleaned.url ?: "")
            val existing = repository.findByNormalizedUrl(normalized)
            if (existing != null) {
                return SaveResult.Duplicate(existing)
            }
        }
        val success = repository.saveUrl(cleaned)
        return if (success) SaveResult.Success(cleaned) else SaveResult.Error
    }
}
