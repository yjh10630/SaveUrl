package com.jinscompany.saveurl.domain.usecase

import android.net.Uri
import com.jinscompany.saveurl.data.room.DomainCategoryDao
import javax.inject.Inject

class LearnDomainCategoryUseCase @Inject constructor(
    private val domainCategoryDao: DomainCategoryDao,
) {
    suspend operator fun invoke(url: String, category: String) {
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
