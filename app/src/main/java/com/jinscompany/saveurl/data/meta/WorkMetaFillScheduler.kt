package com.jinscompany.saveurl.data.meta

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jinscompany.saveurl.domain.importer.MetaFillScheduler
import com.jinscompany.saveurl.utils.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 미리보기 채우기 예약.
 * - 백그라운드: WorkManager 하나 (이름 고정). 기본은 비데이터 요금 네트워크(Wi-Fi) + 배터리 부족 아님.
 *   "모바일 데이터에서도 채우기"를 켜면 연결만 있으면 된다. 설정을 바꾸면 새 제약으로 다시 예약한다.
 * - 보이는 카드·연 링크: 앱 범위에서 바로 하나씩 (모바일 데이터 허용, 동시 1개).
 */
@Singleton
class WorkMetaFillScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesManager,
    private val filler: LinkMetaFiller,
    private val appScope: CoroutineScope,
) : MetaFillScheduler {

    private val priorityLock = Mutex()
    private val inFlight = HashSet<Int>()

    override fun schedule() {
        appScope.launch(Dispatchers.IO) { enqueue(ExistingWorkPolicy.KEEP) }
    }

    /** 멈추기·모바일 데이터 설정이 바뀌었을 때: 새 제약으로 다시 예약 (멈춤이면 취소) */
    fun reschedule() {
        appScope.launch(Dispatchers.IO) { enqueue(ExistingWorkPolicy.REPLACE) }
    }

    private suspend fun enqueue(policy: ExistingWorkPolicy) {
        val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (preferences.metaFillPaused.first()) {
            workManager.cancelUniqueWork(LinkMetaFillWorker.UNIQUE_NAME)
            return
        }
        val cellular = preferences.metaFillCellular.first()
        val request = OneTimeWorkRequestBuilder<LinkMetaFillWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (cellular) NetworkType.CONNECTED else NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(LinkMetaFillWorker.UNIQUE_NAME, policy, request)
    }

    override fun prioritize(linkId: Int) {
        synchronized(inFlight) { if (!inFlight.add(linkId)) return }
        appScope.launch(Dispatchers.IO) {
            try {
                priorityLock.withLock {
                    val row = filler.retryFailed(linkId) ?: return@withLock
                    runCatching { filler.fill(row) }
                }
            } finally {
                synchronized(inFlight) { inFlight.remove(linkId) }
            }
        }
    }
}
