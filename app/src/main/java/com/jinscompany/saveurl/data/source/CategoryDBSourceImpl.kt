package com.jinscompany.saveurl.data.source

import androidx.room.withTransaction
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.room.CategoryDao
import com.jinscompany.saveurl.domain.model.CategoryModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CategoryDBSourceImpl @Inject constructor (
    private val categoryDao: CategoryDao,
    private val db: AppDatabase
) : CategoryDBSource {
    override suspend fun getAll(): List<CategoryModel> = withContext(Dispatchers.IO) {
        return@withContext categoryDao.getAll()
    }

    override suspend fun insert(data: CategoryModel): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            db.withTransaction {
                val orderMaxCnt = categoryDao.getMaxOrder() ?: 0
                data.order = orderMaxCnt + 1
                val id = categoryDao.insert(data)
                -1 < id
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun delete(data: CategoryModel): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            db.withTransaction {
                categoryDao.delete(data)
                val itemsToUpdate = categoryDao.getCategoriesAfter(data.order)
                itemsToUpdate.forEach {
                    categoryDao.update(it.copy(order = it.order - 1))
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun update(oldName: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            db.withTransaction {
                val updateData = categoryDao.get(oldName)
                if (updateData != null) {
                    updateData.name = newName
                    categoryDao.update(updateData)
                    true
                } else false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}