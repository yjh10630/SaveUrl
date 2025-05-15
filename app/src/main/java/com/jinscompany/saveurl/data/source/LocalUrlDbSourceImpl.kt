package com.jinscompany.saveurl.data.source

import androidx.paging.PagingSource
import androidx.room.withTransaction
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.room.BaseSaveUrlDao
import com.jinscompany.saveurl.data.room.CategoryDao
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LocalUrlDbSourceImpl @Inject constructor(
    private val baseSaveUrlDao: BaseSaveUrlDao,
    private val categoryDao: CategoryDao,
    private val db: AppDatabase,
): LocalUrlDBSource {

    override fun getLocalSaveDBUrlList(params: FilterParams?): PagingSource<Int, UrlData> {
        return with(baseSaveUrlDao) {
            val categories = params?.categories ?: listOf("전체")
            val siteNames = params?.siteList.orEmpty()
            val sortDesc = (params?.sort ?: "최신순") == "최신순"

            return when {
                categories.contains("전체") -> {
                    if (siteNames.isEmpty()) {
                        if (sortDesc) baseSaveUrlDao.getUrlDataLatest()
                        else baseSaveUrlDao.getUrlDataOldest()
                    } else {
                        if (sortDesc) getUrlDataLatestBySites(siteNames)
                        else getUrlDataOldestBySites(siteNames)
                    }
                }

                categories.contains("북마크") -> {
                    if (siteNames.isEmpty()) {
                        if (sortDesc) getTargetBookMarkUrlDataLatest()
                        else getTargetBookMarkUrlDataOldest()
                    } else {
                        if (sortDesc) getTargetBookMarkUrlDataLatestBySites(siteNames)
                        else getTargetBookMarkUrlDataOldestBySites(siteNames)
                    }
                }

                else -> {
                    if (siteNames.isEmpty()) {
                        if (sortDesc) getTargetCategoryUrlDataLatest(categories)
                        else getTargetCategoryUrlDataOldest(categories)
                    } else {
                        if (sortDesc) getTargetCategoryUrlDataLatestBySites(categories, siteNames)
                        else getTargetCategoryUrlDataOldestBySites(categories, siteNames)
                    }
                }
            }
        }
    }

    override suspend fun saveLocalDBUrl(data: UrlData) = withContext(Dispatchers.IO) {
        return@withContext try {
            db.withTransaction {
                baseSaveUrlDao.insert(data)
                val categoryName = data.category ?: "전체"
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

    override suspend fun getSiteNameList(): List<String> = withContext(Dispatchers.IO) {
        return@withContext baseSaveUrlDao.getDistinctNonEmptySiteNames()
    }

    override fun searchAll(keyword: String): PagingSource<Int, UrlData> = baseSaveUrlDao.searchAll(keyword)
    override fun searchByTitle(keyword: String): PagingSource<Int, UrlData> = baseSaveUrlDao.searchByTitle(keyword)
    override fun searchByDescription(keyword: String): PagingSource<Int, UrlData> = baseSaveUrlDao.searchByDescription(keyword)
    override fun searchByTag(keyword: String): PagingSource<Int, UrlData> = baseSaveUrlDao.searchByTag(keyword)
}