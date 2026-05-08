package com.jinscompany.saveurl.ui.filter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.GetSiteNameListUseCase
import com.jinscompany.saveurl.domain.usecase.GetTagListUseCase
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.ui.main.FilterState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FilterViewModel @Inject constructor(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getSiteNameListUseCase: GetSiteNameListUseCase,
    private val getTagListUseCase: GetTagListUseCase,
) : ViewModel() {

    var uiState by mutableStateOf(FilterUiState())
        private set

    private val _uiEffect = MutableSharedFlow<FilterUiEffect>()
    val uiEffect = _uiEffect.asSharedFlow()

    fun onIntent(intent: FilterIntent) {
        viewModelScope.launch {
            when (intent) {
                FilterIntent.Clear -> clearData()
                is FilterIntent.ToggleCategory -> toggleCategory(intent.category)
                is FilterIntent.ToggleSort -> toggleSort(intent.sort)
                FilterIntent.Confirm -> confirm()
                is FilterIntent.InitData -> loadInitialData(intent.params)
                FilterIntent.GoToCategorySetting -> _uiEffect.emit(FilterUiEffect.GoToCategorySetting)
                is FilterIntent.ToggleSite -> toggleSite(intent.site)
                is FilterIntent.ToggleTag -> toggleTag(intent.tag)
            }
        }
    }

    private fun toggleTag(value: String) {
        val updated = uiState.tagState.selected.toMutableList().apply {
            if (contains(value)) remove(value) else add(value)
        }
        uiState = uiState.copy(
            tagState = uiState.tagState.copy(selected = SnapshotStateList<String>().apply { addAll(updated) })
        )
    }

    private fun confirm() {
        viewModelScope.launch {
            _uiEffect.emit(FilterUiEffect.Confirm(
                uiState.categoryState.selected,
                uiState.sortState.selected.value,
                uiState.siteState.selected,
                uiState.tagState.selected
            ))
        }
    }

    private fun toggleSite(value: String) {
        val updated = uiState.siteState.selected.toMutableList().apply {
            if (contains(value)) remove(value) else add(value)
        }
        uiState = uiState.copy(
            siteState = uiState.siteState.copy(selected = SnapshotStateList<String>().apply { addAll(updated) })
        )
    }

    private fun toggleCategory(value: String) {
        val updated = uiState.categoryState.selected.toMutableList().apply {
            if (value == FilterDefaults.CATEGORY_ALL || value == FilterDefaults.CATEGORY_BOOKMARK) {
                clear()
                add(value)
            } else {
                removeAll(listOf(FilterDefaults.CATEGORY_ALL, FilterDefaults.CATEGORY_BOOKMARK))
                if (contains(value)) remove(value) else add(value)
            }
        }
        uiState = uiState.copy(
            categoryState = uiState.categoryState.copy(selected = SnapshotStateList<String>().apply { addAll(updated) })
        )
    }

    private fun toggleSort(value: String) {
        if (uiState.sortState.selected.value != value) {
            uiState = uiState.copy(sortState = uiState.sortState.copy(selected = mutableStateOf(value)))
        }
    }

    private fun clearData() {
        viewModelScope.launch {
            uiState = uiState.copy(
                categoryState = uiState.categoryState.copy(selected = mutableStateListOf(FilterDefaults.CATEGORY_ALL)),
                sortState = uiState.sortState.copy(selected = mutableStateOf(FilterDefaults.SORT_LATEST)),
                siteState = uiState.siteState.copy(selected = mutableStateListOf()),
                tagState = uiState.tagState.copy(selected = mutableStateListOf())
            )
        }
    }

    private fun loadInitialData(params: FilterParams) {
        viewModelScope.launch {
            val categories = listOf(FilterDefaults.CATEGORY_BOOKMARK, FilterDefaults.CATEGORY_ALL) + getCategoriesUseCase().map { it.name }
            val siteList = getSiteNameListUseCase()
            val tagList = getTagListUseCase()
            uiState = uiState.copy(
                categoryState = FilterState.MultiSelect(
                    options = categories,
                    selected = mutableStateListOf<String>().apply { addAll(params.categories) }
                ),
                sortState = FilterState.SingleSelect(
                    options = listOf(FilterDefaults.SORT_LATEST, FilterDefaults.SORT_OLDEST),
                    selected = mutableStateOf(params.sort)
                ),
                siteState = FilterState.MultiSelect(
                    options = siteList,
                    selected = mutableStateListOf<String>().apply { addAll(params.siteList) }
                ),
                tagState = FilterState.MultiSelect(
                    options = tagList,
                    selected = mutableStateListOf<String>().apply { addAll(params.tagList) }
                ),
            )
        }
    }
}
