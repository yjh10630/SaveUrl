package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** 잠금 카테고리 이름 (순서대로). 세션이 닫혀 있을 때 화면은 개수만 쓴다 (🔒 칩, "보기" 행 등) */
class ObserveLockedCategoriesUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    operator fun invoke(): Flow<List<String>> = repository.observeLockedNames().distinctUntilChanged()
}
