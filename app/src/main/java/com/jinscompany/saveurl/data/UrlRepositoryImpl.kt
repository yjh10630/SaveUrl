package com.jinscompany.saveurl.data

import androidx.room.withTransaction
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.source.LocalUrlDBSource
import com.jinscompany.saveurl.data.source.UrlParserSource
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.UrlRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UrlRepositoryImpl @Inject constructor(
    private val localUrlDBSource: LocalUrlDBSource,
    private val urlParser: UrlParserSource,
    private val db: AppDatabase,
): UrlRepository {
    override suspend fun getUrlList(categoryName: String?): List<UrlData> = localUrlDBSource.getLocalSaveDBUrlList(categoryName)
    override suspend fun saveUrl(data: UrlData): Boolean = localUrlDBSource.saveLocalDBUrl(data)
    override suspend fun removeUrl(data: UrlData): List<UrlData> = withContext(Dispatchers.IO) {
        return@withContext try {
            db.withTransaction {
                localUrlDBSource.deleteLocalDBUrl(data)
                localUrlDBSource.getLocalSaveDBUrlList()
            }
        } catch (e: Exception) {
            localUrlDBSource.getLocalSaveDBUrlList()
        }
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
    override suspend fun searchAll(keyword: String): List<UrlData> = localUrlDBSource.searchAll(keyword)
    override suspend fun searchByTitle(keyword: String): List<UrlData> = localUrlDBSource.searchByTitle(keyword)
    override suspend fun searchByDescription(keyword: String): List<UrlData> = localUrlDBSource.searchByDescription(keyword)
    override suspend fun searchByTag(keyword: String): List<UrlData> = localUrlDBSource.searchByTag(keyword)
}