package com.jinscompany.saveurl.data.source

import com.jinscompany.saveurl.domain.model.CategoryModel
import kotlinx.coroutines.flow.Flow

interface CategoryDBSource {
    suspend fun getAll(): List<CategoryModel>
    suspend fun insert(data: CategoryModel): Boolean
    suspend fun delete(data: CategoryModel): Boolean
    suspend fun update(oldName: String, newName: String): Boolean
    fun observeLockedNames(): Flow<List<String>>
    suspend fun isLocked(name: String): Boolean
    suspend fun setLocked(name: String, locked: Boolean): Boolean
    suspend fun lockOrCreate(names: Collection<String>)
}