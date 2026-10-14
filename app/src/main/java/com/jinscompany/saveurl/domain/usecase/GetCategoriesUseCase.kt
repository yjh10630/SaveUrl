package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(
    private val repository: CategoryRepository,
    private val lockSession: CategoryLockSession,
) {
    /**
     * 카테고리 목록. 기본은 세션이 닫혀 있으면 잠금 카테고리를 뺀다.
     * 잠금 카테고리 개수 등이 필요한 곳(카테고리 편집, 저장 시트)만 [includeLocked] = true 로 부른다.
     */
    suspend operator fun invoke(includeLocked: Boolean = lockSession.isUnlocked.value): List<CategoryModel> =
        repository.get().filter { includeLocked || !it.isLocked }
}
