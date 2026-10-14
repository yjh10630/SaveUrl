package com.jinscompany.saveurl.ui.add_category

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.usecase.DeleteCategoryUseCase
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.InsertCategoryUseCase
import com.jinscompany.saveurl.domain.usecase.SetCategoryLockedUseCase
import com.jinscompany.saveurl.domain.usecase.UpdateCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import com.jinscompany.saveurl.domain.repository.AutoCategoryRepository
import com.jinscompany.saveurl.utils.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditCategoryViewModel @Inject constructor(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val insertCategoryUseCase: InsertCategoryUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val setCategoryLockedUseCase: SetCategoryLockedUseCase,
    lockSession: CategoryLockSession,
    private val autoCategoryRepository: AutoCategoryRepository,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    /** 처음 카테고리를 잠갔을 때 한 번 보이는 "잠근 링크는 나만 볼 수 있어요" 시트. 닫을 때 본 것으로 적는다 (37번 §19) */
    private val _showLockIntro = MutableStateFlow(false)
    val showLockIntro: StateFlow<Boolean> = _showLockIntro.asStateFlow()

    private val _pendingDelete = MutableStateFlow<DeleteConfirm?>(null)
    val pendingDelete: StateFlow<DeleteConfirm?> = _pendingDelete.asStateFlow()

    /** 편집할 수 있는 카테고리 전체 (잠금 카테고리 포함). 세션이 닫혀 있으면 화면이 잠금 카테고리를 "잠금 카테고리 n개 · 보기" 한 행으로 접는다 */
    private val _categoryItemsState: MutableState<List<CategoryModel>> = mutableStateOf(listOf())
    val categoryItemsState: State<List<CategoryModel>> = _categoryItemsState

    val isUnlocked: StateFlow<Boolean> = lockSession.isUnlocked

    init {
        // 다시 잠기면 잠금 카테고리 삭제 확인을 닫는다: 인증 없이 "삭제"를 누를 수 없게, 이름이 남지 않게 (40번 독립 리뷰 R16)
        viewModelScope.launch {
            lockSession.isUnlocked.drop(1).collect { unlocked ->
                if (!unlocked && _pendingDelete.value?.locked == true) _pendingDelete.value = null
            }
        }
    }

    private val _effect = MutableSharedFlow<EditCategoryEffect>()
    val effect = _effect.asSharedFlow()

    fun onIntent(intent: EditCategoryIntent) {
        when (intent) {
            EditCategoryIntent.Load -> getCategoryList()
            is EditCategoryIntent.Insert -> insertCategory(intent.name)
            is EditCategoryIntent.Delete -> {
                _pendingDelete.value = null
                deleteCategory(intent.name)
            }
            is EditCategoryIntent.RequestDelete -> requestDelete(intent.name)
            EditCategoryIntent.CancelDelete -> _pendingDelete.value = null
            is EditCategoryIntent.Update -> updateCategoryName(intent.oldName, intent.newName)
            is EditCategoryIntent.SetLocked -> setLocked(intent.name, intent.locked)
            EditCategoryIntent.DismissLockIntro -> {
                _showLockIntro.value = false
                viewModelScope.launch { preferencesManager.setLockIntroSeen() }
            }
        }
    }

    private fun setLocked(name: String, locked: Boolean) {
        viewModelScope.launch {
            if (setCategoryLockedUseCase(name, locked)) {
                _categoryItemsState.value = _categoryItemsState.value.map {
                    if (it.name == name) it.copy(isLocked = locked) else it
                }
                // 처음 잠그면 스낵바 대신 안내 시트 (누가 볼 수 있는지 한 번에 답한다)
                if (locked && !preferencesManager.lockIntroSeen.first()) {
                    _showLockIntro.value = true
                } else {
                    _effect.emit(EditCategoryEffect.LockChanged(name, locked))
                }
            }
        }
    }

    private fun getCategoryList() {
        viewModelScope.launch {
            // 중복 이름 검사도 이 목록으로 하므로 닫힌 잠금 카테고리까지 모두 읽는다 (화면에서 접어 보여 줌)
            val list = getCategoriesUseCase(includeLocked = true).filter { it.isEditable }
            _categoryItemsState.value = list
        }
    }

    private fun requestDelete(name: String) {
        viewModelScope.launch {
            val target = _categoryItemsState.value.firstOrNull { it.name == name } ?: return@launch
            val rules = runCatching { autoCategoryRepository.countRulesForCategory(target.id) }.getOrDefault(0)
            if (target.isLocked || rules > 0) {
                _pendingDelete.value = DeleteConfirm(name, target.contentCnt, rules, target.isLocked)
            } else {
                deleteCategory(name)
            }
        }
    }

    private fun deleteCategory(name: String) {
        viewModelScope.launch {
            val deleteData = _categoryItemsState.value.firstOrNull { it.name == name } ?: return@launch
            val isDeleted = deleteCategoryUseCase(deleteData)
            if (isDeleted)
                _categoryItemsState.value = _categoryItemsState.value.filterNot { it.name == name }
        }
    }

    private fun insertCategory(name: String) {
        viewModelScope.launch {
            if (_categoryItemsState.value.any { it.name == name }) return@launch
            val newCategory = CategoryModel(name = name)
            val isAdded = insertCategoryUseCase(newCategory)
            if (isAdded) _categoryItemsState.value += newCategory
        }
    }

    private fun updateCategoryName(oldName: String, newName: String) {
        viewModelScope.launch {
            if (newName.isBlank() || _categoryItemsState.value.any { it.name == newName }) return@launch
            val isUpdated = updateCategoryUseCase(oldName, newName)
            if (isUpdated) {
                _categoryItemsState.value = _categoryItemsState.value.map {
                    if (it.name == oldName) it.copy(name = newName) else it
                }
            }
        }
    }
}
