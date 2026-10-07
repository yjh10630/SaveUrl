package com.jinscompany.saveurl.domain.repository

import androidx.paging.PagingSource
import com.jinscompany.saveurl.domain.model.CategoryCount
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.LinkCounts
import com.jinscompany.saveurl.domain.model.SearchScope
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.flow.Flow

interface UrlRepository {
    fun getUrlList(categoryName: FilterParams? = null): PagingSource<Int, UrlData>
    fun searchAll(keyword: String): PagingSource<Int, UrlData>
    fun searchByTitle(keyword: String): PagingSource<Int, UrlData>
    fun searchByDescription(keyword: String): PagingSource<Int, UrlData>
    fun searchByTag(keyword: String): PagingSource<Int, UrlData>
    fun searchSorted(keyword: String, scope: SearchScope, oldest: Boolean): PagingSource<Int, UrlData>
    fun observeLinkCounts(since: Long): Flow<LinkCounts>
    fun observeTopCategoryCounts(limit: Int): Flow<List<CategoryCount>>
    suspend fun saveUrlDataList(list: List<UrlData>)
    suspend fun removeUrl(data: UrlData): Boolean
    suspend fun saveUrl(data: UrlData): Boolean
    suspend fun parserUrl(url: String): UrlData
    suspend fun isSavedUrl(url: String): Boolean
    suspend fun findUrlData(url: String): UrlData?
    suspend fun findByNormalizedUrl(normalizedUrl: String): UrlData?
    suspend fun updateUrl(data: UrlData): Boolean
    suspend fun getSiteNameList(): List<String>
    suspend fun getTagList(): List<String>
    suspend fun markAsRead(url: String)
    suspend fun getAllUrlData(): List<UrlData>
}