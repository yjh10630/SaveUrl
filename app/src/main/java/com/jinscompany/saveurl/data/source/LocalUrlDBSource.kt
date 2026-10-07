package com.jinscompany.saveurl.data.source

import androidx.paging.PagingSource
import com.jinscompany.saveurl.domain.model.CategoryCount
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.LinkCounts
import com.jinscompany.saveurl.domain.model.SearchScope
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.flow.Flow

interface LocalUrlDBSource {
    fun getLocalSaveDBUrlList(params: FilterParams? = null): PagingSource<Int, UrlData>
    fun searchAll(keyword: String): PagingSource<Int, UrlData>
    fun searchByTitle(keyword: String): PagingSource<Int, UrlData>
    fun searchByDescription(keyword: String): PagingSource<Int, UrlData>
    fun searchByTag(keyword: String): PagingSource<Int, UrlData>
    fun searchSorted(keyword: String, scope: SearchScope, oldest: Boolean): PagingSource<Int, UrlData>
    fun observeLinkCounts(since: Long): Flow<LinkCounts>
    fun observeTopCategoryCounts(limit: Int): Flow<List<CategoryCount>>
    suspend fun saveLocalDBUrl(data: UrlData): Boolean
    suspend fun deleteLocalDBUrl(data: UrlData): Boolean
    suspend fun isSavedLocalDBUrl(url: String): Boolean
    suspend fun findLocalDBUrlData(url: String): UrlData?
    suspend fun findByNormalizedUrl(normalizedUrl: String): UrlData?
    suspend fun updateLocalDBUrlData(data: UrlData): Boolean
    suspend fun getSiteNameList(): List<String>
    suspend fun saveUrlDataList(list: List<UrlData>)
    suspend fun getTagList(): List<String>
    suspend fun markAsRead(url: String)
    suspend fun getAllUrlData(): List<UrlData>
}