package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.repository.TrashRepository
import javax.inject.Inject

/** 휴지통 전체 복원·전체 삭제: 지금 보이는 항목만 (닫힌 잠금 카테고리 항목은 남긴다) */
class GetAllTrashItemsAfterDeleteAllUseCase @Inject constructor(
    private val repository: TrashRepository,
    private val lockSession: CategoryLockSession,
) {
    suspend operator fun invoke(): List<TrashItem> =
        repository.getAllItemsAfterDeleteAll(includeLocked = lockSession.isUnlocked.value)
}
