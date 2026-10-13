package com.jinscompany.saveurl.domain.usecase

import android.net.Uri
import com.jinscompany.saveurl.data.room.DomainCategoryDao
import com.jinscompany.saveurl.data.source.CategoryKeywordDictionary
import com.jinscompany.saveurl.data.source.SeedCategoryMappings
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import javax.inject.Inject

class SuggestCategoryUseCase @Inject constructor(
    private val domainCategoryDao: DomainCategoryDao,
    private val categoryRepository: CategoryRepository,
) {
    suspend operator fun invoke(url: String, title: String?, description: String?): String? {
        val suggested = suggest(url, title, description) ?: return null
        // 잠금 카테고리는 어디서 나왔든(학습값·시드·키워드) 추천하지 않는다 (잠긴 카테고리 이름이 저장 화면에 보이면 안 됨)
        return suggested.takeUnless { categoryRepository.isLocked(it) }
    }

    private suspend fun suggest(url: String, title: String?, description: String?): String? {
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
