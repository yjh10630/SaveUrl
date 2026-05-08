package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.repository.CategoryRepository
import javax.inject.Inject

class UpdateCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository
) {
    suspend operator fun invoke(oldName: String, newName: String): Boolean = repository.update(oldName, newName)
}
