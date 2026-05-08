package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.repository.TrashRepository
import javax.inject.Inject

class GetTrashStateUseCase @Inject constructor(
    private val repository: TrashRepository
) {
    suspend operator fun invoke(): Boolean = repository.getTrashState()
}
