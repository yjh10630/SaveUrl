package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.repository.TrashRepository
import javax.inject.Inject

class SetTrashStateUseCase @Inject constructor(
    private val repository: TrashRepository
) {
    suspend operator fun invoke(isEnable: Boolean) = repository.setTrashState(isEnable)
}
