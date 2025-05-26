package com.jinscompany.saveurl.data

import androidx.paging.PagingSource
import androidx.room.withTransaction
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.jinscompany.saveurl.SaveUrlApplication.Companion.DEBUG
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.room.TrashDao
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.repository.TrashRepository
import com.jinscompany.saveurl.utils.CmLog
import com.jinscompany.saveurl.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TrashRepositoryImpl @Inject constructor(
    private val trashDao: TrashDao,
    private val db: AppDatabase,
    private val preferencesManager: PreferencesManager,
): TrashRepository {
    override suspend fun getTrashState(): Boolean = withContext(Dispatchers.IO) {
        val isEnable = preferencesManager.autoDeleteEnabled.first()
        return@withContext isEnable != false
    }

    override suspend fun setTrashState(isEnable: Boolean) {
        preferencesManager.setAutoDeleteEnabled(isEnable)
    }

    override fun getTrashItems(): PagingSource<Int, TrashItem> = trashDao.getPagingItemAll()
    override suspend fun getAllTrashItems(): List<TrashItem> = withContext(Dispatchers.IO) {
        return@withContext trashDao.getAll()
    }
    override suspend fun insertTrashItem(item: TrashItem): Boolean = withContext(Dispatchers.IO) {
        return@withContext trashDao.insert(item) > -1
    }

    override suspend fun getAllItemsAfterDeleteAll(): List<TrashItem> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                db.withTransaction {
                    val list = trashDao.getAll()
                    if (list.isNotEmpty()) {
                        trashDao.deleteAll()
                    }
                    list
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList<TrashItem>()
            }

    }

    override suspend fun deleteTrashItem(item: TrashItem): Boolean = withContext(Dispatchers.IO) {
        return@withContext  trashDao.delete(item) > -1
    }

    override suspend fun deleteItemsPastEndDate(): Boolean = withContext(Dispatchers.IO) {
        try {
            val threshold = if (DEBUG) {
                System.currentTimeMillis() - 15 * 60 * 1000L // 15분 전
            } else {
                System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L // 7일 전
            }
            val items = trashDao.getAll()
            CmLog.d("Trash items cnt > ${items.count()}")
            val expiredItems = items.filter { it.deleteDate < threshold }
            CmLog.d("Trash expiredItems cnt > ${expiredItems.count()}")

            return@withContext if (expiredItems.isNotEmpty()) {
                val cnt = trashDao.deleteItems(expiredItems)
                cnt > 0
            } else {
                false
            }
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            return@withContext false
        }

    }


}