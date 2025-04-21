package com.jinscompany.saveurl.data.source

import androidx.room.withTransaction
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.room.BaseSaveUrlDao
import com.jinscompany.saveurl.data.room.CategoryDao
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LocalUrlDbSourceImpl @Inject constructor(
    private val baseSaveUrlDao: BaseSaveUrlDao,
    private val categoryDao: CategoryDao,
    private val db: AppDatabase,
    private val coroutine: CoroutineScope,
): LocalUrlDBSource {

    override suspend fun getLocalSaveDBUrlList(categoryName: String?): List<UrlData> = withContext(Dispatchers.IO) {
        return@withContext if (categoryName.isNullOrEmpty() || categoryName == "전체") {
            baseSaveUrlDao.get()
        } else if (categoryName == "북마크") {
            baseSaveUrlDao.getTargetBookMark()
        } else {
            baseSaveUrlDao.getTargetCategory(categoryName)
        }
    }

    override suspend fun saveLocalDBUrl(data: UrlData) = withContext(Dispatchers.IO) {
        return@withContext try {
            db.withTransaction {
                baseSaveUrlDao.insert(data)
                val categoryName = data.category ?: ""
                if (categoryName == "전체") {
                    true
                } else if (categoryName.isNotEmpty()) {
                    val category = categoryDao.get(categoryName)
                    if (category != null) {
                        category.contentCnt += 1
                        categoryDao.update(category)
                        true
                    } else false
                } else false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun deleteLocalDBUrl(data: UrlData) = withContext(Dispatchers.IO) {
        return@withContext try {
            db.withTransaction {
                baseSaveUrlDao.delete(data)
                val categoryName = data.category ?: ""
                if (categoryName.isNotEmpty()) {
                    val category = categoryDao.get(categoryName)
                    if (category != null) {
                        if (category.contentCnt > 0) {
                            category.contentCnt -= 1
                            categoryDao.update(category)
                        }
                        true
                    } else false
                } else false
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun isSavedLocalDBUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext baseSaveUrlDao.exists(url) > 0
    }

    override suspend fun findLocalDBUrlData(url: String): UrlData? = withContext(Dispatchers.IO) {
        return@withContext baseSaveUrlDao.get(url)
    }

    override suspend fun updateLocalDBUrlData(data: UrlData): Boolean = withContext(Dispatchers.IO) {
        return@withContext baseSaveUrlDao.update(data) > 0
    }

    override suspend fun searchAll(keyword: String): List<UrlData> = withContext(Dispatchers.IO) {
        return@withContext baseSaveUrlDao.searchAll(keyword)
    }

    override suspend fun searchByTitle(keyword: String): List<UrlData> = withContext(Dispatchers.IO) {
        return@withContext baseSaveUrlDao.searchByTitle(keyword)
    }

    override suspend fun searchByDescription(keyword: String): List<UrlData> = withContext(Dispatchers.IO) {
        return@withContext baseSaveUrlDao.searchByDescription(keyword)
    }

    override suspend fun searchByTag(keyword: String): List<UrlData> = withContext(Dispatchers.IO) {
        return@withContext baseSaveUrlDao.searchByTag(keyword)
    }
}