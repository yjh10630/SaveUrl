package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.repository.TrashRepository
import javax.inject.Inject

class DeleteTrashItemUseCase @Inject constructor(
    private val repository: TrashRepository
) {
    suspend operator fun invoke(item: TrashItem): Boolean = repository.deleteTrashItem(item)
}
