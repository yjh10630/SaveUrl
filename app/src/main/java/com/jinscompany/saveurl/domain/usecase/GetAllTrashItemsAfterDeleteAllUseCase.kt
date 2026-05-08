package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.repository.TrashRepository
import javax.inject.Inject

class GetAllTrashItemsAfterDeleteAllUseCase @Inject constructor(
    private val repository: TrashRepository
) {
    suspend operator fun invoke(): List<TrashItem> = repository.getAllItemsAfterDeleteAll()
}
