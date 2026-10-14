package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.repository.UrlRepository
import javax.inject.Inject

class GetSiteNameListUseCase @Inject constructor(
    private val repository: UrlRepository,
    private val lockSession: CategoryLockSession,
) {
    /** 세션이 닫혀 있으면 잠금 카테고리 링크에만 있는 값은 빠진다 */
    suspend operator fun invoke(): List<String> = repository.getSiteNameList(includeLocked = lockSession.isUnlocked.value)
}
