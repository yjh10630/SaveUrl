package com.jinscompany.saveurl.data

import androidx.paging.PagingSource
import androidx.room.withTransaction
import com.jinscompany.saveurl.SaveUrlApplication.Companion.DEBUG
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.room.TrashDao
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.model.TrashRetention
import com.jinscompany.saveurl.domain.repository.TrashRepository
import com.jinscompany.saveurl.utils.CmLog
import com.jinscompany.saveurl.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.jinscompany.saveurl.utils.CrashReporter

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

    override fun getTrashItems(includeLocked: Boolean): PagingSource<Int, TrashItem> = trashDao.getPagingItems(includeLocked)
    override suspend fun getAllTrashItems(): List<TrashItem> = withContext(Dispatchers.IO) {
        return@withContext trashDao.getAll()
    }
    override suspend fun insertTrashItem(item: TrashItem): Boolean = withContext(Dispatchers.IO) {
        return@withContext trashDao.insert(item) > -1
    }

    override suspend fun getAllItemsAfterDeleteAll(includeLocked: Boolean): List<TrashItem> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                db.withTransaction {
                    // 닫힌 잠금 카테고리 항목은 보이지 않으므로 건드리지 않는다
                    val list = trashDao.getVisible(includeLocked)
                    if (list.isNotEmpty()) {
                        trashDao.deleteItems(list)
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
            // debug 15분 / release 7일 (TrashRetention 과 휴지통 화면의 남은 기간 표시가 같은 값을 사용)
            val threshold = TrashRetention.purgeThreshold(System.currentTimeMillis(), DEBUG)
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
            CrashReporter.record(e)
            return@withContext false
        }

    }


}