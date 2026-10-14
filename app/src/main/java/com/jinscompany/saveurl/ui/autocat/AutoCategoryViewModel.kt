package com.jinscompany.saveurl.ui.autocat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.jinscompany.saveurl.domain.autocat.AutoCategoryKeys
import com.jinscompany.saveurl.domain.autocat.AutoRuleStats
import com.jinscompany.saveurl.domain.autocat.RuleSuggestion
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.AutoRuleSource
import com.jinscompany.saveurl.domain.model.AutoRuleState
import com.jinscompany.saveurl.domain.model.AutoRuleWithCategory
import com.jinscompany.saveurl.domain.repository.AutoCategoryRepository
import com.jinscompany.saveurl.utils.PreferencesManager
import com.jinscompany.saveurl.utils.RemoteFlags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 설정 → 자동 분류 화면에서 보여 줄 규칙 목록 */
data class AutoCategoryRules(
    /** 화면에 보이는 규칙 (거절 기록 제외, 닫힌 세션이면 잠금 카테고리 규칙 제외) */
    val rules: List<AutoRuleWithCategory> = emptyList(),
    /** 확인이 필요한 규칙 (켜짐 + 최근 5번 중 2번 이상 바뀜) */
    val needsFix: List<AutoRuleWithCategory> = emptyList(),
)

/** 스낵바 (되돌리기 액션이 있으면 [undo]) */
data class AutoCategorySnack(val message: AutoCategoryMessage, val undo: (() -> Unit)? = null)

sealed interface AutoCategoryMessage {
    data object RuleCreated : AutoCategoryMessage
    data object SuggestionDismissed : AutoCategoryMessage
    data class Moved(val count: Int) : AutoCategoryMessage
    data class RuleChanged(val categoryName: String) : AutoCategoryMessage
    data object RuleDeleted : AutoCategoryMessage
    data object RuleDisabled : AutoCategoryMessage
    data object NothingToMove : AutoCategoryMessage
}

/** 설정 → 자동 분류 (36번 §5) */
@HiltViewModel
class AutoCategoryViewModel @Inject constructor(
    private val repository: AutoCategoryRepository,
    private val preferencesManager: PreferencesManager,
    private val remoteFlags: RemoteFlags,
    lockSession: CategoryLockSession,
) : ViewModel() {

    val enabled: StateFlow<Boolean> = preferencesManager.autoCategoryEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val rules: StateFlow<AutoCategoryRules> = combine(repository.observeRules(), lockSession.isUnlocked) { all, unlocked ->
        visibleRules(all, unlocked)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AutoCategoryRules())

    /** null = 계산 중 */
    private val _suggestions = MutableStateFlow<List<RuleSuggestion>?>(null)
    val suggestions: StateFlow<List<RuleSuggestion>?> = _suggestions.asStateFlow()

    private val _snacks = MutableSharedFlow<AutoCategorySnack>()
    val snacks = _snacks.asSharedFlow()

    init {
        refreshSuggestions()
        // 규칙이 바뀌면(수락·거절·삭제·카테고리 변경) 제안도 다시 계산한다
        viewModelScope.launch { repository.observeRules().drop(1).collect { refreshSuggestions() } }
    }

    fun refreshSuggestions() {
        viewModelScope.launch {
            try {
                _suggestions.value = repository.suggestions(remoteFlags.autoCategoryThresholds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 계산 실패: 제안 구역을 숨긴다 (사용자 문구 없음, §5.4)
                Firebase.crashlytics.recordException(e)
                _suggestions.value = emptyList()
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setAutoCategoryEnabled(enabled) }
    }

    fun setRuleEnabled(rule: AutoRuleWithCategory, enabled: Boolean) {
        viewModelScope.launch { repository.setState(rule.rule.id, if (enabled) AutoRuleState.ACTIVE else AutoRuleState.OFF) }
    }

    /** 제안 1탭 수락 (되돌리기 = 규칙 삭제, 거절로 남기지 않음) */
    fun accept(suggestion: RuleSuggestion) {
        viewModelScope.launch {
            val id = repository.createOrActivate(suggestion.key, suggestion.categoryId, AutoRuleSource.SUGGESTION)
            _snacks.emit(AutoCategorySnack(AutoCategoryMessage.RuleCreated) { viewModelScope.launch { repository.delete(id) } })
        }
    }

    /** 제안 거절 (되돌리기 = 거절 기록 삭제) */
    fun dismiss(suggestion: RuleSuggestion) {
        viewModelScope.launch {
            repository.dismissSuggestion(suggestion.key, suggestion.categoryId)
            _snacks.emit(AutoCategorySnack(AutoCategoryMessage.SuggestionDismissed) {
                viewModelScope.launch {
                    repository.findByKey(suggestion.key)?.takeIf { it.ruleState == AutoRuleState.DISMISSED }?.let { repository.delete(it.id) }
                }
            })
        }
    }

    /** 규칙 시트가 끝난 뒤 스낵바 */
    fun onSheetEvent(event: RuleSheetEvent, sheetViewModel: RuleSheetViewModel) {
        viewModelScope.launch {
            when (event) {
                is RuleSheetEvent.Moved -> _snacks.emit(AutoCategorySnack(AutoCategoryMessage.Moved(event.result.count)) { sheetViewModel.undoMove(event.result) })
                is RuleSheetEvent.FixChanged -> _snacks.emit(AutoCategorySnack(AutoCategoryMessage.RuleChanged(event.categoryName)) {
                    sheetViewModel.undoCategoryChange(event.ruleId, event.previousCategoryId)
                })
                is RuleSheetEvent.Deleted -> _snacks.emit(AutoCategorySnack(AutoCategoryMessage.RuleDeleted))
                RuleSheetEvent.FixDisabled -> _snacks.emit(AutoCategorySnack(AutoCategoryMessage.RuleDisabled))
                RuleSheetEvent.NothingToMove -> _snacks.emit(AutoCategorySnack(AutoCategoryMessage.NothingToMove))
                is RuleSheetEvent.Created, is RuleSheetEvent.Saved, RuleSheetEvent.FixKept, RuleSheetEvent.OpenSettings -> Unit
            }
        }
    }

    companion object {
        fun visibleRules(all: List<AutoRuleWithCategory>, unlocked: Boolean): AutoCategoryRules {
            val rules = all.filter { it.rule.ruleState != AutoRuleState.DISMISSED && (unlocked || !it.categoryLocked) }
            return AutoCategoryRules(
                rules = rules,
                needsFix = rules.filter { !it.categoryLocked && AutoRuleStats.needsFix(it.rule) },
            )
        }

        fun siteName(rule: AutoRuleWithCategory): String = AutoCategoryKeys.displayName(rule.rule.key)
    }
}
