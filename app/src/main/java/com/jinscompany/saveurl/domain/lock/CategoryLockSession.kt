package com.jinscompany.saveurl.domain.lock

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 잠금 카테고리 해제 세션 (메모리에만 둔다. 프로세스가 죽으면 잠긴 상태로 시작).
 *
 * - 여는 곳: 🔒 칩, 카테고리 편집 "보기", 저장 시트, 검색, 휴지통, CSV 포함 백업, 카테고리 잠그기·풀기 (모두 인증 후)
 * - 닫히는 때: [relock], 앱을 나가 [RELOCK_AFTER_MS] 이상 지난 뒤 돌아올 때 ([onAppBackground] / [onAppForeground])
 *
 * 시각은 기기 시각 변경의 영향을 받지 않도록 elapsedRealtime 을 쓴다.
 */
@Singleton
class CategoryLockSession(private val elapsedRealtime: () -> Long) {

    @Inject constructor() : this({ SystemClock.elapsedRealtime() })

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var backgroundAt: Long? = null

    fun unlock() {
        _isUnlocked.value = true
    }

    fun relock() {
        _isUnlocked.value = false
    }

    /** 앱 전체가 백그라운드로 갔을 때 (ProcessLifecycleOwner ON_STOP) */
    fun onAppBackground() {
        backgroundAt = elapsedRealtime()
    }

    /** 앱으로 돌아왔을 때 (ProcessLifecycleOwner ON_START). 1분 이상 나가 있었으면 다시 잠근다 */
    fun onAppForeground() {
        val since = backgroundAt ?: return
        backgroundAt = null
        if (elapsedRealtime() - since >= RELOCK_AFTER_MS) relock()
    }

    companion object {
        /**
         * 앱을 나간 뒤 다시 잠그기까지의 시간 (고정). 0 이면 인증 화면(API 29 이하 기기 PIN 화면)이나
         * CSV 파일 선택 화면에 다녀올 때마다 닫혀서 쓸 수 없다 (37번 기획서 1.3).
         */
        const val RELOCK_AFTER_MS = 60_000L
    }
}
