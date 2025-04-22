package com.jinscompany.saveurl.data

import androidx.paging.PagingSource
import androidx.room.withTransaction
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.source.LocalUrlDBSource
import com.jinscompany.saveurl.data.source.UrlParserSource
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.UrlRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UrlRepositoryImpl @Inject constructor(
    private val localUrlDBSource: LocalUrlDBSource,
    private val urlParser: UrlParserSource,
    private val db: AppDatabase,
): UrlRepository {
    override fun getUrlList(categoryName: String?): PagingSource<Int, UrlData> = localUrlDBSource.getLocalSaveDBUrlList(categoryName)
    override suspend fun saveUrl(data: UrlData): Boolean = localUrlDBSource.saveLocalDBUrl(data)
    override suspend fun removeUrl(data: UrlData): Boolean = withContext(Dispatchers.IO) {
        return@withContext localUrlDBSource.deleteLocalDBUrl(data)
    }
    override suspend fun parserUrl(url: String): UrlData = withContext(Dispatchers.IO){
        var data = urlParser.jsoupUrlParser(url)
        if (data.title.isNullOrEmpty()) {
            data = urlParser.webViewGetHtml(url)
        }
        return@withContext data
    }

    override suspend fun isSavedUrl(url: String): Boolean = localUrlDBSource.isSavedLocalDBUrl(url)
    override suspend fun findUrlData(url: String): UrlData? = localUrlDBSource.findLocalDBUrlData(url)
    override suspend fun updateUrl(data: UrlData): Boolean = localUrlDBSource.updateLocalDBUrlData(data)
    override fun searchAll(keyword: String): PagingSource<Int, UrlData> = localUrlDBSource.searchAll(keyword)
    override fun searchByTitle(keyword: String): PagingSource<Int, UrlData> = localUrlDBSource.searchByTitle(keyword)
    override fun searchByDescription(keyword: String): PagingSource<Int, UrlData> = localUrlDBSource.searchByDescription(keyword)
    override fun searchByTag(keyword: String): PagingSource<Int, UrlData> = localUrlDBSource.searchByTag(keyword)
}