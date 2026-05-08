package com.jinscompany.saveurl.domain.usecase

import android.net.Uri
import com.jinscompany.saveurl.data.room.DomainCategoryDao
import com.jinscompany.saveurl.data.source.CategoryKeywordDictionary
import com.jinscompany.saveurl.data.source.SeedCategoryMappings
import javax.inject.Inject

class SuggestCategoryUseCase @Inject constructor(
    private val domainCategoryDao: DomainCategoryDao,
) {
    suspend operator fun invoke(url: String, title: String?, description: String?): String? {
        val domain = extractDomain(url) ?: return null

        // 1순위: 사용자 학습 매핑
        domainCategoryDao.get(domain)?.let { return it.category }

        // 2순위: 시드 매핑 (도메인 접미사 매칭)
        SeedCategoryMappings.domainToCategory.entries
            .firstOrNull { (seed, _) -> domain.endsWith(seed) || domain == seed }
            ?.let { return it.value }

        // 3순위: 키워드 사전
        CategoryKeywordDictionary.suggest(title.orEmpty(), description.orEmpty())
            ?.let { return it }

        return null
    }

    private fun extractDomain(url: String): String? {
        return try {
            Uri.parse(url).host?.removePrefix("www.")
        } catch (e: Exception) {
            null
        }
    }
}
