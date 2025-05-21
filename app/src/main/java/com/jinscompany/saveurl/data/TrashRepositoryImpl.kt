package com.jinscompany.saveurl.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.paging.PagingSource
import androidx.room.withTransaction
import com.jinscompany.saveurl.data.datastore.SettingsKeys.TRASH_ENABLE
import com.jinscompany.saveurl.data.datastore.dataStore
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.room.TrashDao
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.repository.TrashRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TrashRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trashDao: TrashDao,
    private val db: AppDatabase
): TrashRepository {
    override suspend fun getTrashState(): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.dataStore.data.first()
        return@withContext prefs[TRASH_ENABLE] != false
    }

    override suspend fun setTrashState(isEnable: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[TRASH_ENABLE] = isEnable
        }
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


}