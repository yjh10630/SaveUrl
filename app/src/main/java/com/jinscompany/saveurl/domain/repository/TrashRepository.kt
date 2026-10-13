package com.jinscompany.saveurl.domain.repository

import androidx.paging.PagingSource
import com.jinscompany.saveurl.domain.model.TrashItem

interface TrashRepository {
    suspend fun getTrashState(): Boolean
    suspend fun setTrashState(isEnable: Boolean)
    fun getTrashItems(includeLocked: Boolean): PagingSource<Int, TrashItem>
    suspend fun getAllTrashItems(): List<TrashItem>
    suspend fun insertTrashItem(item: TrashItem): Boolean
    /** 지금 보이는 항목([includeLocked])만 휴지통에서 꺼내고 그 목록을 돌려준다 (전체 복원·전체 삭제) */
    suspend fun getAllItemsAfterDeleteAll(includeLocked: Boolean): List<TrashItem>
    suspend fun deleteTrashItem(item: TrashItem): Boolean
    suspend fun deleteItemsPastEndDate(): Boolean
}