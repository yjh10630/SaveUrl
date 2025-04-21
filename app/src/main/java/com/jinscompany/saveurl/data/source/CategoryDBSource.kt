package com.jinscompany.saveurl.data.source

import com.jinscompany.saveurl.domain.model.CategoryModel

interface CategoryDBSource {
    suspend fun getAll(): List<CategoryModel>
    suspend fun insert(data: CategoryModel): Boolean
    suspend fun delete(data: CategoryModel): Boolean
    suspend fun update(oldName: String, newName: String): Boolean
}