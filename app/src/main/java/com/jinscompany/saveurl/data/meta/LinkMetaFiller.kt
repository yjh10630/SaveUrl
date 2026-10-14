package com.jinscompany.saveurl.data.meta

import com.jinscompany.saveurl.data.room.ImportDao
import com.jinscompany.saveurl.data.room.MetaFillRow
import com.jinscompany.saveurl.data.source.UrlParserSource
import com.jinscompany.saveurl.domain.model.MetaState
import com.jinscompany.saveurl.utils.TrackingParamStripper
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 가져온 링크 하나의 제목·썸네일을 채운다 (31번 §2.6).
 *
 * - 요청 1건 예산 8초 (저장 화면은 25초). WebView 폴백은 쓰지 않는다.
 * - 같은 호스트는 1초에 1건 (네이버·인스타그램 연속 요청 차단 대비).
 * - 북마크·백업처럼 제목이 이미 있으면([MetaState.PENDING_IMAGE]) 제목은 덮어쓰지 않고 썸네일·설명만 채운다.
 * - 제목을 못 얻으면 [MetaState.FAILED] (카드는 도메인 + URL 로 보이고, 링크를 열면 다시 시도).
 * - 그사이 사용자가 고쳤으면(상태가 바뀜) 덮어쓰지 않는다 ([ImportDao.applyMeta] 조건).
 */
@Singleton
class LinkMetaFiller @Inject constructor(
    private val importDao: ImportDao,
    private val parser: UrlParserSource,
) {
    private val hostLock = Mutex()
    private val lastRequestAt = HashMap<String, Long>()

    /** @return 채웠으면(또는 이미 다 채워져 있으면) true */
    suspend fun fill(row: MetaFillRow): Boolean {
        val url = row.url ?: return false
        waitForHost(url)
        val meta = parser.fetchMeta(url, BUDGET_MS)
        val hasTitle = meta?.hasTitle == true
        return when (row.metaState) {
            MetaState.PENDING_IMAGE -> {
                importDao.applyMeta(
                    id = row.id, expectedState = row.metaState, newState = MetaState.DONE,
                    title = null, imageUrl = meta?.imageUrl, description = meta?.description, siteName = meta?.siteName,
                )
                true
            }
            else -> {
                importDao.applyMeta(
                    id = row.id, expectedState = row.metaState,
                    newState = if (hasTitle) MetaState.DONE else MetaState.FAILED,
                    title = meta?.title?.takeIf { hasTitle }, imageUrl = meta?.imageUrl,
                    description = meta?.description, siteName = meta?.siteName,
                )
                hasTitle
            }
        }
    }

    /** 실패한 링크를 다시 채울 대상으로 (링크를 열었을 때) */
    suspend fun retryFailed(id: Int): MetaFillRow? {
        val row = importDao.getMetaRow(id) ?: return null
        if (row.metaState != MetaState.FAILED) return row.takeIf { MetaState.isPending(it.metaState) }
        val updated = importDao.applyMeta(id, MetaState.FAILED, MetaState.PENDING, null, null, null, null)
        return if (updated > 0) row.copy(metaState = MetaState.PENDING) else null
    }

    private suspend fun waitForHost(url: String) {
        val host = TrackingParamStripper.extractHost(url.substringBefore('?')) ?: return
        val wait = hostLock.withLock {
            val now = System.currentTimeMillis()
            val next = (lastRequestAt[host] ?: 0L) + SAME_HOST_INTERVAL_MS
            val start = maxOf(now, next)
            lastRequestAt[host] = start
            start - now
        }
        if (wait > 0) delay(wait)
    }

    companion object {
        const val BUDGET_MS = 8_000L
        private const val SAME_HOST_INTERVAL_MS = 1_000L
    }
}
