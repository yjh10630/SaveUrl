package com.jinscompany.saveurl.domain.usecase

import androidx.paging.PagingSource
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.repository.TrashRepository
import javax.inject.Inject

class GetTrashItemsUseCase @Inject constructor(
    private val repository: TrashRepository,
    private val lockSession: CategoryLockSession,
) {
    operator fun invoke(): PagingSource<Int, TrashItem> = repository.getTrashItems(includeLocked = lockSession.isUnlocked.value)
}
