package com.jinscompany.saveurl.domain.repository

import androidx.paging.PagingSource
import com.jinscompany.saveurl.domain.model.TrashItem

interface TrashRepository {
    suspend fun getTrashState(): Boolean
    suspend fun setTrashState(isEnable: Boolean)
    fun getTrashItems(): PagingSource<Int, TrashItem>
    suspend fun getAllTrashItems(): List<TrashItem>
    suspend fun insertTrashItem(item: TrashItem): Boolean
    suspend fun getAllItemsAfterDeleteAll(): List<TrashItem>
    suspend fun deleteTrashItem(item: TrashItem): Boolean
    suspend fun deleteItemsPastEndDate(): Boolean
}