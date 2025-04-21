package com.jinscompany.saveurl.domain.repository

import com.jinscompany.saveurl.domain.model.CategoryModel

interface CategoryRepository {
    suspend fun get(): List<CategoryModel>
    suspend fun insert(data: CategoryModel): Boolean
    suspend fun delete(deleteData: CategoryModel): Boolean
    suspend fun update(oldName: String, newName: String): Boolean
}