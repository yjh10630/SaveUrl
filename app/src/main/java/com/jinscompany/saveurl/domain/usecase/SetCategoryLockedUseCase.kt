package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.repository.CategoryRepository
import javax.inject.Inject

/** 카테고리 잠그기·풀기 (인증은 화면에서 마친 뒤 부른다). "미분류" 는 잠글 수 없다 */
class SetCategoryLockedUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(name: String, locked: Boolean): Boolean = repository.setLocked(name, locked)
}
