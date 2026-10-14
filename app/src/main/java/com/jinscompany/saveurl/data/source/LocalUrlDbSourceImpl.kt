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
import com.jinscompany.saveurl.domain.model.CategoryCount
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.LinkCounts
import com.jinscompany.saveurl.domain.model.SearchScope
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.utils.UrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LocalUrlDbSourceImpl @Inject constructor(
    private val baseSaveUrlDao: BaseSaveUrlDao,
    private val categoryDao: CategoryDao,
    private val db: AppDatabase,
    private val trashDao: TrashDao,
): LocalUrlDBSource {

    override fun getLocalSaveDBUrlList(params: FilterParams?, includeLocked: Boolean): PagingSource<Int, UrlData> {
        val categories = params?.categories ?: listOf(FilterDefaults.CATEGORY_ALL)
        val siteNames = params?.siteList.orEmpty()
        val sortDesc = (params?.sort ?: FilterDefaults.SORT_LATEST) == FilterDefaults.SORT_LATEST

        return with(baseSaveUrlDao) {
            when {
                categories.contains(FilterDefaults.CATEGORY_ALL) -> {
                    if (siteNames.isEmpty()) {
                        if (sortDesc) getUrlDataLatest(includeLocked) else getUrlDataOldest(includeLocked)
                    } else {
                        if (sortDesc) getUrlDataLatestBySites(siteNames, includeLocked)
                        else getUrlDataOldestBySites(siteNames, includeLocked)
                    }
                }

                categories.contains(FilterDefaults.CATEGORY_BOOKMARK) -> {
                    if (siteNames.isEmpty()) {
                        if (sortDesc) getTargetBookMarkUrlDataLatest(includeLocked)
                        else getTargetBookMarkUrlDataOldest(includeLocked)
                    } else {
                        if (sortDesc) getTargetBookMarkUrlDataLatestBySites(siteNames, includeLocked)
                        else getTargetBookMarkUrlDataOldestBySites(siteNames, includeLocked)
                    }
                }

                else -> {
                    if (siteNames.isEmpty()) {
                        if (sortDesc) getTargetCategoryUrlDataLatest(categories, includeLocked)
                        else getTargetCategoryUrlDataOldest(categories, includeLocked)
                    } else {
                        if (sortDesc) getTargetCategoryUrlDataLatestBySites(categories, siteNames, includeLocked)
                        else getTargetCategoryUrlDataOldestBySites(categories, siteNames, includeLocked)
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
                val categoryName = data.category ?: FilterDefaults.CATEGORY_ALL
                // 저장 화면이 확인한 뒤 다른 화면(2분할)에서 카테고리를 지웠으면 "선택 안 함"으로 넣는다.
                // 예전처럼 같은 이름으로 다시 만들면 지운 카테고리가 새 id 로 살아난다 (40번 독립 리뷰 R11).
                // '미분류'는 기본 카테고리라 없으면 만든다 (링크 수는 조회 시 집계하므로 contentCnt 는 갱신하지 않음)
                val missing = categoryName.isNotEmpty() && categoryName != FilterDefaults.CATEGORY_ALL &&
                    categoryDao.get(categoryName) == null
                val toInsert = when {
                    !missing -> data
                    categoryName == CategoryModel.UNCATEGORIZED -> {
                        val orderMaxCnt = categoryDao.getMaxOrder() ?: 0
                        categoryDao.insert(CategoryModel(name = categoryName, order = orderMaxCnt + 1, isEditable = false))
                        data
                    }
                    else -> data.copy(category = FilterDefaults.CATEGORY_ALL)
                }
                baseSaveUrlDao.insert(toInsert.withNormalizedUrl())
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun deleteLocalDBUrl(data: UrlData) = withContext(Dispatchers.IO) {
        return@withContext try {
            // 링크 수는 조회 시 집계하므로 카테고리 contentCnt 는 갱신하지 않음
            baseSaveUrlDao.delete(data)
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
        // 수정 화면에서 url 이 바뀌었을 수 있으므로 normalizedUrl 도 다시 계산
        return@withContext baseSaveUrlDao.update(data.withNormalizedUrl()) > 0
    }

    override suspend fun getSiteNameList(includeLocked: Boolean): List<String> = withContext(Dispatchers.IO) {
        return@withContext baseSaveUrlDao.getDistinctNonEmptySiteNames(includeLocked)
    }

    override suspend fun saveUrlDataList(list: List<UrlData>) {
        withContext(Dispatchers.IO) {
            // 휴지통 전체 복원(TrashItem 에는 normalizedUrl 이 없음), CSV 가져오기 등에서도 중복 감지가 되도록 계산
            baseSaveUrlDao.insertAll(*list.map { it.withNormalizedUrl() }.toTypedArray())
        }
    }

    override suspend fun getTagList(includeLocked: Boolean): List<String> = withContext(Dispatchers.IO) {
        val gson = Gson()
        // R8 full mode 대응: 익명 TypeToken 서브클래스 대신 getParameterized 사용
        val type = TypeToken.getParameterized(List::class.java, String::class.java).type
        return@withContext baseSaveUrlDao.getAllTagListJson(includeLocked).flatMap { json ->
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

    override suspend fun getAllUrlData(includeLocked: Boolean): List<UrlData> = withContext(Dispatchers.IO) {
        baseSaveUrlDao.getAll(includeLocked)
    }

    override fun searchAll(keyword: String, includeLocked: Boolean): PagingSource<Int, UrlData> =
        baseSaveUrlDao.searchAll(keyword, includeLocked)
    override fun searchByTitle(keyword: String, includeLocked: Boolean): PagingSource<Int, UrlData> =
        baseSaveUrlDao.searchByTitle(keyword, includeLocked)
    override fun searchByDescription(keyword: String, includeLocked: Boolean): PagingSource<Int, UrlData> =
        baseSaveUrlDao.searchByDescription(keyword, includeLocked)
    override fun searchByTag(keyword: String, includeLocked: Boolean): PagingSource<Int, UrlData> =
        baseSaveUrlDao.searchByTag(keyword, includeLocked)
    override fun searchSorted(keyword: String, scope: SearchScope, oldest: Boolean, includeLocked: Boolean): PagingSource<Int, UrlData> = when (scope) {
        SearchScope.ALL -> baseSaveUrlDao.searchAllSorted(keyword, oldest, includeLocked)
        SearchScope.TITLE -> baseSaveUrlDao.searchByTitleSorted(keyword, oldest, includeLocked)
        SearchScope.DESCRIPTION -> baseSaveUrlDao.searchByDescriptionSorted(keyword, oldest, includeLocked)
        SearchScope.TAG -> baseSaveUrlDao.searchByTagSorted(keyword, oldest, includeLocked)
    }
    override fun observeLinkCounts(since: Long): Flow<LinkCounts> = baseSaveUrlDao.observeLinkCounts(since)
    override fun observeTopCategoryCounts(limit: Int): Flow<List<CategoryCount>> =
        baseSaveUrlDao.observeTopCategoryCounts(excluded = FilterDefaults.CATEGORY_ALL, limit = limit)

    private fun UrlData.withNormalizedUrl(): UrlData =
        copy(normalizedUrl = url?.takeIf { it.isNotBlank() }?.let { UrlNormalizer.normalize(it) } ?: "")
}
