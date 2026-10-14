package com.jinscompany.saveurl.utils

import com.jinscompany.saveurl.data.room.BaseSaveUrlDao
import com.jinscompany.saveurl.data.room.TrashDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 2.0.0 까지 첫 실행에 넣던 "이렇게 사용하세요" 링크 항목([tutorialUrl])을 업데이트 뒤 한 번만 지운다.
 * 튜토리얼은 설정 > 사용 방법과 시작 안내 카드로 옮겨 갔으므로 목록에 남길 이유가 없다.
 * 사용자가 저장한 링크가 아니므로 휴지통을 거치지 않고 지우고, 휴지통에 들어가 있던 것도 같이 지운다.
 * 링크 알림은 RevisitRepository 의 고아 정리가 맡는다.
 * 실패하면 완료 표시를 남기지 않아 다음 실행에서 다시 시도한다.
 * 첫 화면은 [awaitDone] 뒤에 그린다 (스플래시가 유지되는 동안 지워져 목록에 잠깐 보였다 사라지지 않게, 45번 독립 리뷰 R8).
 */
@Singleton
class LegacyTutorialLinkCleanup @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val urlDao: BaseSaveUrlDao,
    private val trashDao: TrashDao,
) {
    private val done = MutableStateFlow(false)

    /** 정리가 끝날 때까지(최대 [MAX_WAIT_MS]) 기다린다. 오래 걸리면 기다리지 않고 진행한다 */
    suspend fun awaitDone() {
        withTimeoutOrNull(MAX_WAIT_MS) { done.first { it } }
    }

    suspend fun run() {
        try {
            if (preferencesManager.isLegacyTutorialLinkRemoved()) return
            val removed = urlDao.deleteByUrl(tutorialUrl) + trashDao.deleteByUrl(tutorialUrl)
            preferencesManager.setLegacyTutorialLinkRemoved()
            CmLog.d("LegacyTutorialLinkCleanup removed=$removed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CmLog.e("LegacyTutorialLinkCleanup failed: ${e.javaClass.simpleName}")
        } finally {
            done.value = true
        }
    }

    private companion object {
        const val MAX_WAIT_MS = 1_500L
    }
}
