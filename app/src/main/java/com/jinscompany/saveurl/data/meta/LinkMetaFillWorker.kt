package com.jinscompany.saveurl.data.meta

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jinscompany.saveurl.data.room.ImportDao
import com.jinscompany.saveurl.utils.PreferencesManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * 가져온 링크의 제목·썸네일을 백그라운드에서 채운다. 제약(Wi-Fi·배터리)은 [WorkMetaFillScheduler] 가 정한다.
 * 동시 2개, 최신 날짜부터. 채울 링크가 없거나 멈추기를 누르면 끝낸다. 시스템이 멈추면(제약·10분 한도) WorkManager 가 다시 실행한다.
 * 인터넷이 안 돼 응답을 못 받으면 대기 상태로 두고 백오프 뒤 다시 실행한다 (순서는 [MetaFillLoop]).
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
        val loop = MetaFillLoop(
            loadPage = { limit, after -> dao.getPendingMeta(limit, after?.addDate ?: Long.MAX_VALUE, after?.id ?: Int.MAX_VALUE) },
            fill = { filler.fill(it) },
            markFailed = { filler.markFailed(it) },
            isStopped = { isStopped },
            isPaused = { prefs.metaFillPaused.first() },
            isNetworkValidated = { isNetworkValidated(applicationContext) },
            isLastAttempt = runAttemptCount >= MAX_RETRIES,
            page = PAGE,
            concurrency = CONCURRENCY,
        )
        return when (loop.run()) {
            MetaFillLoop.Outcome.DONE -> Result.success()
            MetaFillLoop.Outcome.RETRY -> Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "link_meta_fill"
        private const val CONCURRENCY = 2
        private const val PAGE = 50
        /** 응답 없는 링크 때문에 다시 시도하는 횟수. 넘으면 응답 없는 링크를 실패로 확정한다 */
        private const val MAX_RETRIES = 4

        /** 지금 기본 네트워크가 인터넷에 실제로 닿는지 (로그인 페이지가 뜨는 Wi‑Fi 는 false) */
        fun isNetworkValidated(context: Context): Boolean {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }
}
