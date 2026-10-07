package com.jinscompany.saveurl.data.backfill

import androidx.room.withTransaction
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.utils.CmLog
import com.jinscompany.saveurl.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Room + DataStore 기반 [NormalizedUrlStore] */
class RoomNormalizedUrlStore(
    private val db: AppDatabase,
    private val versionGetter: suspend () -> Int,
    private val versionSetter: suspend (Int) -> Unit,
) : NormalizedUrlStore {
    private val dao get() = db.baseSaveUrlDao()

    override suspend fun fetchAfter(afterId: Int, onlyEmpty: Boolean, limit: Int): List<NormalizedUrlRow> =
        if (onlyEmpty) dao.getEmptyNormalizedUrlRowsAfter(afterId, limit)
        else dao.getNormalizedUrlRowsAfter(afterId, limit)

    override suspend fun apply(updates: List<NormalizedUrlRow>) {
        db.withTransaction {
            updates.forEach { dao.updateNormalizedUrl(it.id, it.url, it.normalizedUrl) }
        }
    }

    override suspend fun completedVersion(): Int = versionGetter()
    override suspend fun setCompletedVersion(version: Int) = versionSetter(version)
}

/**
 * 앱 시작 시 normalizedUrl 백필을 백그라운드(IO)에서 1회 실행.
 * 시작을 막지 않고, 실패해도 다음 실행에서 다시 시도한다.
 */
@Singleton
class NormalizedUrlBackfillRunner @Inject constructor(
    private val db: AppDatabase,
    private val preferencesManager: PreferencesManager,
    private val appScope: CoroutineScope,
) {
    fun start() {
        appScope.launch(Dispatchers.IO) {
            try {
                val store = RoomNormalizedUrlStore(
                    db = db,
                    versionGetter = preferencesManager::getNormalizedUrlVersion,
                    versionSetter = preferencesManager::setNormalizedUrlVersion,
                )
                val result = NormalizedUrlBackfiller(store).run()
                CmLog.d("normalizedUrl backfill > $result")
            } catch (e: Exception) {
                CmLog.e("normalizedUrl backfill failed > ${e.message}")
            }
        }
    }
}
