package com.jinscompany.saveurl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.model.ListViewMode
import com.jinscompany.saveurl.domain.model.ThemeMode
import com.jinscompany.saveurl.utils.InAppUpdatePolicy.FlexibleDecline
import com.jinscompany.saveurl.utils.InAppUpdatePolicy.ForceUpdateBlockReason
import com.jinscompany.saveurl.utils.PreferencesManager
import com.jinscompany.saveurl.utils.LegacyTutorialLinkCleanup
import com.jinscompany.saveurl.utils.ThemeMigrationRunner
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SharedViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    themeMigrationRunner: ThemeMigrationRunner,
    legacyTutorialLinkCleanup: LegacyTutorialLinkCleanup,
): ViewModel() {

    private val _isFlexibleUpdatable = MutableStateFlow(false)
    val isFlexibleUpdatable: StateFlow<Boolean> = _isFlexibleUpdatable.asStateFlow()

    private val _isFlexibleUpdateDownloaded = MutableStateFlow(false)
    val isFlexibleUpdateDownloaded: StateFlow<Boolean> = _isFlexibleUpdateDownloaded.asStateFlow()

    /**
     * 강제 업데이트 차단 화면. null 이면 숨김.
     * ViewModel 에 두어 회전·폴드 등으로 Activity 가 재생성되어도 그대로 남는다 (프로세스가 죽으면 다음 실행에서 다시 판단).
     */
    private val _forceUpdateBlock = MutableStateFlow<ForceUpdateBlockReason?>(null)
    val forceUpdateBlock: StateFlow<ForceUpdateBlockReason?> = _forceUpdateBlock.asStateFlow()

    /** 차단 화면의 "다시 시도/다시 확인" 진행 중 (버튼 연타 방지) */
    private val _isForceUpdateRechecking = MutableStateFlow(false)
    val isForceUpdateRechecking: StateFlow<Boolean> = _isForceUpdateRechecking.asStateFlow()

    /**
     * FLEXIBLE 거부 기록. DataStore 저장은 비동기라, 저장이 끝나기 전에 다시 확인해도 다시 묻지 않도록 메모리에도 둔다.
     */
    private var flexibleDeclineCache: FlexibleDecline? = null

    /**
     * null = 아직 읽기 전. 화면 모드 기본값 마이그레이션이 끝난 뒤에 값을 내보내므로,
     * 기존 사용자가 첫 실행에 잠깐 밝은 화면을 보는 일이 없다 (그동안 스플래시 유지, MainActivity 참고).
     */
    val themeMode: StateFlow<ThemeMode?> = flow {
        themeMigrationRunner.awaitDone()
        // 2.0.0 튜토리얼 링크 항목 정리도 스플래시가 떠 있는 동안 끝낸다 (첫 목록에 잠깐 보였다 사라지지 않게, 45번 R8)
        legacyTutorialLinkCleanup.awaitDone()
        emitAll(preferencesManager.themeMode)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val listViewMode: StateFlow<ListViewMode> = preferencesManager.listViewMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListViewMode.DEFAULT)

    fun setFlexibleUpdate(isUpdatable: Boolean) {
        _isFlexibleUpdatable.value = isUpdatable
    }

    fun setFlexibleUpdateDownloaded(downloaded: Boolean) {
        _isFlexibleUpdateDownloaded.value = downloaded
    }

    fun showForceUpdateBlock(reason: ForceUpdateBlockReason) {
        _forceUpdateBlock.value = reason
    }

    fun hideForceUpdateBlock() {
        _forceUpdateBlock.value = null
    }

    fun setForceUpdateRechecking(rechecking: Boolean) {
        _isForceUpdateRechecking.value = rechecking
    }

    suspend fun getFlexibleUpdateDecline(): FlexibleDecline? =
        flexibleDeclineCache ?: preferencesManager.getFlexibleUpdateDecline()

    fun recordFlexibleUpdateDecline(decline: FlexibleDecline) {
        flexibleDeclineCache = decline
        viewModelScope.launch { preferencesManager.setFlexibleUpdateDecline(decline) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun setListViewMode(mode: ListViewMode) {
        viewModelScope.launch { preferencesManager.setListViewMode(mode) }
    }
}
