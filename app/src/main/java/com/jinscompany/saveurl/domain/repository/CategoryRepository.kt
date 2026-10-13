package com.jinscompany.saveurl.domain.repository

import com.jinscompany.saveurl.domain.model.CategoryModel
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    suspend fun get(): List<CategoryModel>
    suspend fun insert(data: CategoryModel): Boolean
    suspend fun delete(deleteData: CategoryModel): Boolean
    suspend fun update(oldName: String, newName: String): Boolean
    /** 잠금 카테고리 이름 (순서대로) */
    fun observeLockedNames(): Flow<List<String>>
    suspend fun isLocked(name: String): Boolean
    suspend fun setLocked(name: String, locked: Boolean): Boolean
    /** [names] 를 잠근다. 없는 카테고리는 잠금 카테고리로 만든다 (CSV 가져오기) */
    suspend fun lockOrCreate(names: Collection<String>)
}