package com.jinscompany.saveurl.ui.main.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.model.LinkStats
import com.jinscompany.saveurl.domain.model.startOfWeekMillis
import com.jinscompany.saveurl.domain.usecase.GetLinkStatsUseCase
import com.jinscompany.saveurl.domain.usecase.IsSavedUrlUseCase
import com.jinscompany.saveurl.utils.ClipboardReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 2분할 홈 패널 (메인 오른쪽 기본 상태).
 * - 요약 숫자·카테고리별 개수: Room Flow 집계라 링크가 저장/삭제되면 바로 갱신된다.
 * - 클립보드: 패널을 열 때는 읽지 않는다. 빠른 저장 입력칸에 포커스할 때만 [onQuickSaveFocused] 로 확인한다.
 */
@HiltViewModel
class HomePanelViewModel @Inject constructor(
    getLinkStatsUseCase: GetLinkStatsUseCase,
    private val clipboardReader: ClipboardReader,
    private val isSavedUrlUseCase: IsSavedUrlUseCase,
) : ViewModel() {

    val stats: StateFlow<LinkStats?> = getLinkStatsUseCase(startOfWeekMillis(System.currentTimeMillis()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "복사한 링크 저장하기" 제안 URL (아직 저장하지 않은 링크일 때만) */
    private val _clipboardSuggestion = MutableStateFlow<String?>(null)
    val clipboardSuggestion: StateFlow<String?> = _clipboardSuggestion.asStateFlow()

    /** 사용자가 닫은 제안은 같은 URL 로 다시 띄우지 않는다 */
    private var dismissedUrl: String? = null

    fun onQuickSaveFocused() {
        viewModelScope.launch {
            val url = runCatching { clipboardReader.readUrl() }.getOrNull() ?: return@launch
            if (url == dismissedUrl) return@launch
            _clipboardSuggestion.value = if (isSavedUrlUseCase(url)) null else url
        }
    }

    fun dismissClipboardSuggestion() {
        dismissedUrl = _clipboardSuggestion.value
        _clipboardSuggestion.value = null
    }

    /** 제안을 눌러 저장 화면으로 넘어간 뒤에는 제안을 지운다 */
    fun consumeClipboardSuggestion() {
        dismissedUrl = _clipboardSuggestion.value
        _clipboardSuggestion.value = null
    }
}
