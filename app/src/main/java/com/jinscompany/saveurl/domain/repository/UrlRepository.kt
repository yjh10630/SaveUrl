package com.jinscompany.saveurl.domain.repository

import androidx.paging.PagingSource
import com.jinscompany.saveurl.domain.model.CategoryCount
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.LinkCounts
import com.jinscompany.saveurl.domain.model.SearchScope
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.flow.Flow

interface UrlRepository {
    // includeLocked: 잠금 카테고리 링크도 포함할지 (유스케이스가 CategoryLockSession 으로 정한다)
    fun getUrlList(params: FilterParams?, includeLocked: Boolean): PagingSource<Int, UrlData>
    /** 안 읽음 탭 (42번 §1). [keepIds] = 이번 탭 진입 뒤 연 링크 (바로 빠지지 않게) */
    fun getUnreadList(params: FilterParams?, keepIds: List<Int>, includeLocked: Boolean): PagingSource<Int, UrlData>
    fun searchAll(keyword: String, includeLocked: Boolean): PagingSource<Int, UrlData>
    fun searchByTitle(keyword: String, includeLocked: Boolean): PagingSource<Int, UrlData>
    fun searchByDescription(keyword: String, includeLocked: Boolean): PagingSource<Int, UrlData>
    fun searchByTag(keyword: String, includeLocked: Boolean): PagingSource<Int, UrlData>
    fun searchSorted(keyword: String, scope: SearchScope, oldest: Boolean, includeLocked: Boolean): PagingSource<Int, UrlData>
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
    suspend fun getSiteNameList(includeLocked: Boolean): List<String>
    suspend fun getTagList(includeLocked: Boolean): List<String>
    suspend fun markAsRead(url: String)
    suspend fun getAllUrlData(includeLocked: Boolean): List<UrlData>
}