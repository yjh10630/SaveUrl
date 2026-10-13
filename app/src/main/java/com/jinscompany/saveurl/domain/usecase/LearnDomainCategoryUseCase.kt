package com.jinscompany.saveurl.domain.usecase

import android.net.Uri
import com.jinscompany.saveurl.data.room.DomainCategoryDao
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import javax.inject.Inject

class LearnDomainCategoryUseCase @Inject constructor(
    private val domainCategoryDao: DomainCategoryDao,
    private val categoryRepository: CategoryRepository,
) {
    suspend operator fun invoke(url: String, category: String) {
        // 잠금 카테고리로 저장한 링크는 학습하지 않는다 (다음 저장에서 잠금 카테고리를 추천하지 않도록)
        if (categoryRepository.isLocked(category)) return
        val domain = extractDomain(url) ?: return
        domainCategoryDao.upsert(domain, category)
    }

    private fun extractDomain(url: String): String? {
        return try {
            Uri.parse(url).host?.removePrefix("www.")
        } catch (e: Exception) {
            null
        }
    }
}
