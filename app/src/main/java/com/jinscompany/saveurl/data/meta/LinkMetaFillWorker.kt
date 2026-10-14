package com.jinscompany.saveurl.data.meta

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jinscompany.saveurl.data.room.ImportDao
import com.jinscompany.saveurl.utils.PreferencesManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 가져온 링크의 제목·썸네일을 백그라운드에서 채운다. 제약(Wi-Fi·배터리)은 [WorkMetaFillScheduler] 가 정한다.
 * 동시 2개, 최신 날짜부터. 채울 링크가 없거나 멈추기를 누르면 끝낸다. 시스템이 멈추면(제약·10분 한도) WorkManager 가 다시 실행한다.
 */
class LinkMetaFillWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun importDao(): ImportDao
        fun filler(): LinkMetaFiller
        fun preferences(): PreferencesManager
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val dao = deps.importDao()
        val filler = deps.filler()
        val prefs = deps.preferences()
        val attempted = HashSet<Int>()
        val semaphore = Semaphore(CONCURRENCY)
        while (!isStopped) {
            if (prefs.metaFillPaused.first()) return Result.success()
            val rows = dao.getPendingMeta(PAGE).filter { it.id !in attempted }
            if (rows.isEmpty()) return Result.success()
            coroutineScope {
                rows.map { row ->
                    attempted.add(row.id)
                    async { semaphore.withPermit { if (!isStopped) runCatching { filler.fill(row) } } }
                }.awaitAll()
            }
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "link_meta_fill"
        private const val CONCURRENCY = 2
        private const val PAGE = 50
    }
}
