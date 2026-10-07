package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.utils.TrackingParamStripper
import javax.inject.Inject

class FindUrlDataUseCase @Inject constructor(
    private val repository: UrlRepository
) {
    // 저장 시 추적 파라미터를 제거하므로, 같은 링크를 utm 등이 붙은 채로 다시 공유해도 기존 링크(수정 모드)를 찾도록 함
    suspend operator fun invoke(url: String): UrlData? =
        repository.findUrlData(url)
            ?: TrackingParamStripper.strip(url).takeIf { it != url }?.let { repository.findUrlData(it) }
}
