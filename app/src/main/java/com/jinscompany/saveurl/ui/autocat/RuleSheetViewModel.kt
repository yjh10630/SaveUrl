package com.jinscompany.saveurl.ui.autocat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.autocat.AutoCategoryKeys
import com.jinscompany.saveurl.domain.autocat.AutoRuleStats
import com.jinscompany.saveurl.domain.autocat.RuleKey
import com.jinscompany.saveurl.domain.autocat.RuleSuggester
import com.jinscompany.saveurl.domain.autocat.UrlKeys
import com.jinscompany.saveurl.domain.model.AutoCatLinkRow
import com.jinscompany.saveurl.domain.model.AutoRuleSource
import com.jinscompany.saveurl.domain.model.AutoRuleState
import com.jinscompany.saveurl.domain.model.AutoRuleWithCategory
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.repository.AutoCategoryRepository
import com.jinscompany.saveurl.domain.repository.BulkMoveResult
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import kotlinx.coroutines.flow.drop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 규칙 시트를 여는 요청 (저장 화면·메인 목록·설정이 같은 시트를 쓴다, 36번 §3.1) */
sealed interface RuleSheetRequest {
    /** 저장 화면 "앞으로 항상": 지금 URL 키 + 고른 카테고리 */
    data class Create(val keys: UrlKeys, val categoryId: Int, val categoryName: String) : RuleSheetRequest
    /** 규칙 수정. [activateOnSave] = 저장 화면 상태 G (꺼진 규칙을 켜며 저장) */
    data class Edit(val ruleId: Long, val presetCategoryId: Int? = null, val activateOnSave: Boolean = false) : RuleSheetRequest
    /** 규칙을 고칠까요? */
    data class Fix(val ruleId: Long) : RuleSheetRequest
}

data class CategoryOption(val id: Int, val name: String)

/** 키에 맞는 내 링크 분포 (잠금 카테고리 링크는 들어오지 않음) */
data class RuleDistribution(
    val total: Int,
    /** 분류된 링크: 카테고리 이름 → 개수 (많은 순) */
    val byCategory: List<Pair<String, Int>>,
    /** 분류 안 된 링크 (선택 안 함·미분류, 최근 순) */
    val unfiled: List<AutoCatLinkRow>,
) {
    fun countOf(name: String): Int = byCategory.firstOrNull { it.first == name }?.second ?: 0
    fun otherCount(chosen: String): Int = byCategory.filter { it.first != chosen }.sumOf { it.second }

    companion object {
        fun of(rows: List<AutoCatLinkRow>): RuleDistribution {
            val (unfiled, filed) = rows.partition { RuleSuggester.isUnfiled(it.category) || it.category == CategoryModel.UNCATEGORIZED }
            val byCategory = filed.groupingBy { it.category!! }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key to it.value }
            return RuleDistribution(total = rows.size, byCategory = byCategory, unfiled = unfiled)
        }
    }
}

enum class RuleEditorMode { CREATE, EDIT }

/** 열려 있는 시트 */
sealed interface RuleSheet {
    data class Editor(
        val mode: RuleEditorMode,
        val ruleId: Long?,
        /** 적용 범위 선택지 (만들기 + 주인 호스트일 때 [이 주인만, 사이트 전체]) */
        val scopeKeys: List<RuleKey>,
        val selectedKey: RuleKey,
        val enabled: Boolean = true,
        val category: CategoryOption,
        val categories: List<CategoryOption>,
        /** null = 계산 중 */
        val distribution: RuleDistribution? = null,
        /** 만들기: 분류 안 된 링크도 옮기기 (기본 끔) */
        val moveUnfiled: Boolean = false,
        val appliedCount: Int = 0,
        val overriddenCount: Int = 0,
        val activateOnSave: Boolean = false,
        val picking: Boolean = false,
        val confirmDelete: Boolean = false,
    ) : RuleSheet {
        val siteName: String get() = AutoCategoryKeys.displayName(selectedKey)
    }

    /** 기존 링크에 적용 미리 보기 (닫으면 [returnTo] 로 돌아감) */
    data class Apply(
        val ruleId: Long,
        val key: RuleKey,
        val categoryName: String,
        val rows: List<AutoCatLinkRow>,
        val returnTo: Editor?,
    ) : RuleSheet

    /** 규칙을 고칠까요? */
    data class Fix(
        val rule: AutoRuleWithCategory,
        /** 최근 기록 (오래된 것 → 최근): 카테고리 이름(선택 안 함 = null) + 바뀜 여부 */
        val recent: List<Pair<String?, Boolean>>,
        val common: CategoryOption?,
    ) : RuleSheet {
        val siteName: String get() = AutoCategoryKeys.displayName(rule.rule.key)
    }
}

/** 시트가 끝났을 때 여는 쪽에 알린다 (스낵바·저장 화면 확인 문구) */
sealed interface RuleSheetEvent {
    data class Created(val ruleId: Long, val siteName: String, val categoryName: String, val move: BulkMoveResult?) : RuleSheetEvent
    data class Saved(val ruleId: Long, val siteName: String, val categoryName: String) : RuleSheetEvent
    data class Deleted(val siteName: String) : RuleSheetEvent
    data class Moved(val result: BulkMoveResult) : RuleSheetEvent
    data class FixChanged(val ruleId: Long, val previousCategoryId: Int, val categoryName: String) : RuleSheetEvent
    data object FixDisabled : RuleSheetEvent
    data object FixKept : RuleSheetEvent
    data object OpenSettings : RuleSheetEvent
    data object NothingToMove : RuleSheetEvent
}

/**
 * 규칙 만들기·수정 / 기존 링크 적용 / 규칙을 고칠까요 시트 (36번 §3, §6, §7).
 * 여는 화면마다 하나씩 (백스택 항목 범위). 카테고리 목록에는 "선택 안 함"·"미분류"·잠금 카테고리가 없다
 * (잠금 카테고리로 가는 규칙은 닫힌 상태에서 저장 화면에 잠금 이름을 띄우게 되므로 만들지 않는다).
 */
@HiltViewModel
class RuleSheetViewModel @Inject constructor(
    private val repository: AutoCategoryRepository,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val lockSession: CategoryLockSession,
) : ViewModel() {

    private val _sheet = MutableStateFlow<RuleSheet?>(null)
    val sheet: StateFlow<RuleSheet?> = _sheet.asStateFlow()

    private val _events = MutableSharedFlow<RuleSheetEvent>()
    val events = _events.asSharedFlow()

    private var distributionJob: Job? = null

    init {
        // 다시 잠기면 잠금 카테고리 규칙을 보여 주던 시트를 닫는다 (이름이 화면에 남지 않게)
        viewModelScope.launch {
            lockSession.isUnlocked.drop(1).collect { unlocked -> if (!unlocked && showsLockedCategory()) _sheet.value = null }
        }
    }

    private suspend fun showsLockedCategory(): Boolean {
        val ruleId = when (val sheet = _sheet.value) {
            is RuleSheet.Editor -> sheet.ruleId
            is RuleSheet.Apply -> sheet.ruleId
            is RuleSheet.Fix -> sheet.rule.rule.id
            null -> return false
        } ?: return false
        return repository.getRule(ruleId)?.categoryLocked != false
    }

    /** 닫힌 세션에서는 잠금 카테고리로 가는 규칙을 열지 않는다 */
    private fun AutoRuleWithCategory.hiddenByLock(): Boolean = categoryLocked && !lockSession.isUnlocked.value

    fun open(request: RuleSheetRequest) {
        viewModelScope.launch {
            val categories = ruleCategories()
            when (request) {
                is RuleSheetRequest.Create -> {
                    val keys = request.keys
                    val scope = listOfNotNull(keys.ownerKey, keys.hostKey)
                    val editor = RuleSheet.Editor(
                        mode = RuleEditorMode.CREATE, ruleId = null,
                        scopeKeys = scope, selectedKey = scope.first(),
                        category = CategoryOption(request.categoryId, request.categoryName), categories = categories,
                    )
                    _sheet.value = editor
                    loadDistribution(editor.selectedKey)
                }
                is RuleSheetRequest.Edit -> {
                    val rule = repository.getRule(request.ruleId)?.takeUnless { it.hiddenByLock() } ?: return@launch
                    val preset = request.presetCategoryId?.let { id -> categories.firstOrNull { it.id == id } }
                    val editor = RuleSheet.Editor(
                        mode = RuleEditorMode.EDIT, ruleId = rule.rule.id,
                        scopeKeys = listOf(rule.rule.key), selectedKey = rule.rule.key,
                        enabled = request.activateOnSave || rule.rule.ruleState == AutoRuleState.ACTIVE,
                        category = preset ?: CategoryOption(rule.rule.categoryId, rule.categoryName),
                        categories = categories,
                        appliedCount = rule.rule.appliedCount, overriddenCount = rule.rule.overriddenCount,
                        activateOnSave = request.activateOnSave,
                    )
                    _sheet.value = editor
                    loadDistribution(editor.selectedKey)
                }
                is RuleSheetRequest.Fix -> openFix(request.ruleId)
            }
        }
    }

    private suspend fun openFix(ruleId: Long) {
        val rule = repository.getRule(ruleId)?.takeUnless { it.hiddenByLock() } ?: return
        val all = getCategoriesUseCase(includeLocked = false)
        val nameOf = { id: Int -> all.firstOrNull { it.id == id }?.name }
        val recent = rule.rule.recentCategoryIds.mapNotNull { it.toIntOrNull() }.map { id ->
            val name = if (id == AutoRuleStats.NONE_CATEGORY_ID) null else nameOf(id)
            name to (id != rule.rule.categoryId)
        }
        val common = AutoRuleStats.commonOverrideCategoryId(rule.rule)
            ?.let { id -> nameOf(id)?.let { CategoryOption(id, it) } }
            ?.takeIf { LinkableCategory.isTarget(it.name) }
        _sheet.value = RuleSheet.Fix(rule, recent, common)
    }

    private suspend fun ruleCategories(): List<CategoryOption> =
        getCategoriesUseCase(includeLocked = false)
            .filter { !it.isLocked && LinkableCategory.isTarget(it.name) }
            .map { CategoryOption(it.id, it.name) }

    private fun loadDistribution(key: RuleKey) {
        distributionJob?.cancel()
        distributionJob = viewModelScope.launch {
            val distribution = RuleDistribution.of(repository.linksForKey(key))
            updateEditor { if (it.selectedKey == key) it.copy(distribution = distribution) else it }
        }
    }

    private fun updateEditor(change: (RuleSheet.Editor) -> RuleSheet.Editor) {
        _sheet.update { (it as? RuleSheet.Editor)?.let(change) ?: it }
    }

    fun dismiss() {
        _sheet.value = null
    }

    fun selectScope(key: RuleKey) {
        updateEditor { it.copy(selectedKey = key, distribution = null) }
        loadDistribution(key)
    }

    fun setPicking(picking: Boolean) = updateEditor { it.copy(picking = picking) }

    fun selectCategory(option: CategoryOption) = updateEditor { it.copy(category = option, picking = false) }

    fun setMoveUnfiled(checked: Boolean) = updateEditor { it.copy(moveUnfiled = checked) }

    fun setEnabled(enabled: Boolean) = updateEditor { it.copy(enabled = enabled) }

    fun setConfirmDelete(show: Boolean) = updateEditor { it.copy(confirmDelete = show) }

    /** 만들기 */
    fun create() {
        val editor = _sheet.value as? RuleSheet.Editor ?: return
        if (editor.mode != RuleEditorMode.CREATE) return
        viewModelScope.launch {
            val ruleId = repository.createOrActivate(editor.selectedKey, editor.category.id, AutoRuleSource.SAVE_SCREEN)
            val move = if (editor.moveUnfiled) {
                val ids = (editor.distribution ?: RuleDistribution.of(repository.linksForKey(editor.selectedKey))).unfiled.map { it.id }
                if (ids.isEmpty()) null else repository.moveUnfiled(ids, editor.category.name)
            } else null
            _sheet.value = null
            _events.emit(RuleSheetEvent.Created(ruleId, editor.siteName, editor.category.name, move))
        }
    }

    /** 수정 저장 (카테고리·켜짐) */
    fun save() {
        val editor = _sheet.value as? RuleSheet.Editor ?: return
        val ruleId = editor.ruleId ?: return
        viewModelScope.launch {
            val current = repository.getRule(ruleId)?.rule ?: return@launch
            if (current.categoryId != editor.category.id) repository.setCategory(ruleId, editor.category.id)
            val state = if (editor.enabled) AutoRuleState.ACTIVE else AutoRuleState.OFF
            if (current.ruleState != state) repository.setState(ruleId, state)
            _sheet.value = null
            if (editor.activateOnSave) {
                _events.emit(RuleSheetEvent.Created(ruleId, editor.siteName, editor.category.name, null))
            } else {
                _events.emit(RuleSheetEvent.Saved(ruleId, editor.siteName, editor.category.name))
            }
        }
    }

    fun delete() {
        val editor = _sheet.value as? RuleSheet.Editor ?: return
        val ruleId = editor.ruleId ?: return
        viewModelScope.launch {
            repository.delete(ruleId)
            _sheet.value = null
            _events.emit(RuleSheetEvent.Deleted(editor.siteName))
        }
    }

    /** 수정 시트 "기존 링크에 적용" → 미리 보기 */
    fun openApply() {
        val editor = _sheet.value as? RuleSheet.Editor ?: return
        val ruleId = editor.ruleId ?: return
        viewModelScope.launch {
            val distribution = editor.distribution ?: RuleDistribution.of(repository.linksForKey(editor.selectedKey))
            _sheet.value = RuleSheet.Apply(ruleId, editor.selectedKey, editor.category.name, distribution.unfiled, editor)
        }
    }

    fun cancelApply() {
        val apply = _sheet.value as? RuleSheet.Apply ?: return
        _sheet.value = apply.returnTo
    }

    fun confirmApply() {
        val apply = _sheet.value as? RuleSheet.Apply ?: return
        viewModelScope.launch {
            val result = repository.moveUnfiled(apply.rows.map { it.id }, apply.categoryName)
            _sheet.value = null
            _events.emit(if (result.count == 0) RuleSheetEvent.NothingToMove else RuleSheetEvent.Moved(result))
        }
    }

    // ---- 규칙을 고칠까요? (§7.2): 셋 다 최근 기록을 초기화한다 ----

    fun fixChangeToCommon() {
        val fix = _sheet.value as? RuleSheet.Fix ?: return
        val target = fix.common ?: return
        viewModelScope.launch {
            repository.setCategory(fix.rule.rule.id, target.id)
            _sheet.value = null
            _events.emit(RuleSheetEvent.FixChanged(fix.rule.rule.id, fix.rule.rule.categoryId, target.name))
        }
    }

    /** "카테고리 바꾸기": 수정 시트로 */
    fun fixPickCategory() {
        val fix = _sheet.value as? RuleSheet.Fix ?: return
        viewModelScope.launch {
            repository.resetRecent(fix.rule.rule.id)
            open(RuleSheetRequest.Edit(fix.rule.rule.id))
        }
    }

    fun fixDisable() {
        val fix = _sheet.value as? RuleSheet.Fix ?: return
        viewModelScope.launch {
            repository.setState(fix.rule.rule.id, AutoRuleState.OFF)
            _sheet.value = null
            _events.emit(RuleSheetEvent.FixDisabled)
        }
    }

    fun fixKeep() {
        val fix = _sheet.value as? RuleSheet.Fix ?: return
        viewModelScope.launch {
            repository.resetRecent(fix.rule.rule.id)
            _sheet.value = null
            _events.emit(RuleSheetEvent.FixKept)
        }
    }

    fun fixOpenSettings() {
        viewModelScope.launch {
            _sheet.value = null
            _events.emit(RuleSheetEvent.OpenSettings)
        }
    }

    // ---- 되돌리기 (스낵바) ----

    fun undoCategoryChange(ruleId: Long, previousCategoryId: Int) {
        viewModelScope.launch { repository.setCategory(ruleId, previousCategoryId) }
    }

    fun undoMove(result: BulkMoveResult) {
        viewModelScope.launch { repository.undoMove(result) }
    }
}

/** 규칙이 가리킬 수 있는 카테고리 ("선택 안 함"·"미분류"·"전체"·"북마크" 가상 값 제외) */
object LinkableCategory {
    fun isTarget(name: String?): Boolean =
        !RuleSuggester.isUnfiled(name) && name != CategoryModel.UNCATEGORIZED &&
            name != com.jinscompany.saveurl.ui.FilterDefaults.CATEGORY_BOOKMARK
}
