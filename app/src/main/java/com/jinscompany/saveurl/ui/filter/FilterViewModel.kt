package com.jinscompany.saveurl.ui.filter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.ui.main.FilterState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FilterViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
): ViewModel() {

    var uiState by mutableStateOf(FilterUiState())
        private set

    private val _uiEffect = MutableSharedFlow<FilterUiEffect>()
    val uiEffect = _uiEffect.asSharedFlow()

    fun onIntent(intent: FilterIntent) {
        viewModelScope.launch {
            when(intent) {
                FilterIntent.Clear -> clearData()
                is FilterIntent.ToggleCategory -> toggleCategory(intent.category)
                is FilterIntent.ToggleSort -> toggleSort(intent.sort)
                FilterIntent.Confirm -> confirm()
                is FilterIntent.InitData -> {
                    loadInitialData(intent.params)
                }
            }
        }
    }

    private fun confirm() {
        viewModelScope.launch {
            val category = uiState.categoryState.selected
            val sort = uiState.sortState.selected.value
            _uiEffect.emit(FilterUiEffect.Confirm(category, sort))
        }
    }

    private fun toggleCategory(value: String) {
        val selected = uiState.categoryState.selected
        val updated = selected.toMutableList().apply {
            if (value == "전체" || value == "북마크") {
                clear()
                add(value)
            } else {
                removeAll(listOf("전체", "북마크"))
                if (contains(value)) remove(value) else add(value)
            }
        }
        uiState = uiState.copy(
            categoryState = uiState.categoryState.copy(selected = SnapshotStateList<String>().apply { addAll(updated) })
        )
    }

    private fun toggleSort(value: String) {
        if (uiState.sortState.selected.value != value) {
            uiState = uiState.copy(
                sortState = uiState.sortState.copy(selected = mutableStateOf(value))
            )
        }
    }

    private fun clearData() {
        viewModelScope.launch {
            uiState = uiState.copy(
                categoryState = uiState.categoryState.copy(selected = mutableStateListOf("전체")),
                sortState = uiState.sortState.copy(selected = mutableStateOf("최신순"))
            )
        }
    }

    private fun loadInitialData(params: FilterParams) {
        viewModelScope.launch {
            val categories = listOf("북마크", "전체") + categoryRepository.get().map { it.name }
            uiState = uiState.copy(
                categoryState = FilterState.MultiSelect(
                    options = categories,
                    selected = mutableStateListOf<String>().apply {
                        addAll(params.categories)
                    }
                ),
                sortState = FilterState.SingleSelect(
                    options = listOf("최신순", "과거순"),
                    selected = mutableStateOf(params.sort)
                )
            )
        }
    }



}