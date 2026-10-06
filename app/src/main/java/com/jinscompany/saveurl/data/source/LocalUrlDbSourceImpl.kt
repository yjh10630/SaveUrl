package com.jinscompany.saveurl.data.source

import androidx.paging.PagingSource
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.room.BaseSaveUrlDao
import com.jinscompany.saveurl.data.room.CategoryDao
import com.jinscompany.saveurl.data.room.TrashDao
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.utils.UrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LocalUrlDbSourceImpl @Inject constructor(
    private val baseSaveUrlDao: BaseSaveUrlDao,
    private val categoryDao: CategoryDao,
    private val db: AppDatabase,
    private val trashDao: TrashDao,
): LocalUrlDBSource {

    override fun getLocalSaveDBUrlList(params: FilterParams?): PagingSource<Int, UrlData> {
        return with(baseSaveUrlDao) {
            val categories = params?.categories ?: listOf(FilterDefaults.CATEGORY_ALL)
            val siteNames = params?.siteList.orEmpty()
            val sortDesc = (params?.sort ?: FilterDefaults.SORT_LATEST) == FilterDefaults.SORT_LATEST

            return when {
                categories.contains(FilterDefaults.CATEGORY_ALL) -> {
                    if (siteNames.isEmpty()) {
                        if (sortDesc) baseSaveUrlDao.getUrlDataLatest()
                        else baseSaveUrlDao.getUrlDataOldest()
                    } else {
                        if (sortDesc) getUrlDataLatestBySites(siteNames)
                        else getUrlDataOldestBySites(siteNames)
                    }
                }

                categories.contains(FilterDefaults.CATEGORY_BOOKMARK) -> {
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

    override suspend fun findByNormalizedUrl(normalizedUrl: String): UrlData? = withContext(Dispatchers.IO) {
        return@withContext baseSaveUrlDao.findByNormalizedUrl(normalizedUrl)
    }

    override suspend fun saveLocalDBUrl(data: UrlData) = withContext(Dispatchers.IO) {
        return@withContext try {
            db.withTransaction {
                val normalized = UrlNormalizer.normalize(data.url ?: "")
                baseSaveUrlDao.insert(data.copy(normalizedUrl = normalized))
                val categoryName = data.category ?: FilterDefaults.CATEGORY_ALL
                if (categoryName == FilterDefaults.CATEGORY_ALL) {
                    true
                } else if (categoryName.isNotEmpty()) {
                    val category = categoryDao.get(categoryName)
                    if (category != null) {
                        category.contentCnt += 1
                        categoryDao.update(category)
                    } else {
                        // 자동 추천(도메인/키워드) 카테고리는 아직 DB 에 없을 수 있음
                        // 링크는 이미 insert 되었으므로 false 를 반환하면 저장 화면이 멈추고 재시도 시 중복 다이얼로그가 뜸
                        // -> 카테고리를 함께 생성
                        val orderMaxCnt = categoryDao.getMaxOrder() ?: 0
                        categoryDao.insert(
                            CategoryModel(name = categoryName, contentCnt = 1, order = orderMaxCnt + 1)
                        )
                    }
                    true
                } else true
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

    override suspend fun saveUrlDataList(list: List<UrlData>) {
        withContext(Dispatchers.IO) {
            baseSaveUrlDao.insertAll(*list.toTypedArray())
        }
    }

    override suspend fun getTagList(): List<String> = withContext(Dispatchers.IO) {
        val gson = Gson()
        // R8 full mode 대응: 익명 TypeToken 서브클래스 대신 getParameterized 사용
        val type = TypeToken.getParameterized(List::class.java, String::class.java).type
        return@withContext baseSaveUrlDao.getAllTagListJson().flatMap { json ->
            try {
                gson.fromJson<List<String>>(json, type) ?: emptyList() // "null" 문자열이면 null 반환
            } catch (e: Exception) {
                emptyList()
            }
        }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    override suspend fun markAsRead(url: String) = withContext(Dispatchers.IO) {
        baseSaveUrlDao.markAsRead(url)
    }

    override suspend fun getAllUrlData(): List<UrlData> = withContext(Dispatchers.IO) {
        baseSaveUrlDao.get()
    }

    override fun searchAll(keyword: String): PagingSource<Int, UrlData> = baseSaveUrlDao.searchAll(keyword)
    override fun searchByTitle(keyword: String): PagingSource<Int, UrlData> = baseSaveUrlDao.searchByTitle(keyword)
    override fun searchByDescription(keyword: String): PagingSource<Int, UrlData> = baseSaveUrlDao.searchByDescription(keyword)
    override fun searchByTag(keyword: String): PagingSource<Int, UrlData> = baseSaveUrlDao.searchByTag(keyword)
}