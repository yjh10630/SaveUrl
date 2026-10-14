package com.jinscompany.saveurl.data

import com.jinscompany.saveurl.data.source.CategoryDBSource
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor (private val categoryDBSource: CategoryDBSource) : CategoryRepository {
    override suspend fun get(): List<CategoryModel> = categoryDBSource.getAll()
    override suspend fun insert(data: CategoryModel): Boolean = categoryDBSource.insert(data)
    override suspend fun delete(deleteData: CategoryModel): Boolean = categoryDBSource.delete(deleteData)
    override suspend fun update(oldName: String, newName: String): Boolean = categoryDBSource.update(oldName, newName)
    override fun observeLockedNames(): Flow<List<String>> = categoryDBSource.observeLockedNames()
    override suspend fun isLocked(name: String): Boolean = categoryDBSource.isLocked(name)
    override suspend fun setLocked(name: String, locked: Boolean): Boolean = categoryDBSource.setLocked(name, locked)
    override suspend fun lockOrCreate(names: Collection<String>) = categoryDBSource.lockOrCreate(names)
}