package com.jinscompany.saveurl.ui.filter

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.ui.main.FilterState

sealed class FilterIntent {
    data class InitData(val params: FilterParams): FilterIntent()
    data class ToggleCategory(val category: String): FilterIntent()
    data class ToggleSort(val sort: String): FilterIntent()
    data class ToggleSite(val site: String): FilterIntent()
    data class ToggleTag(val tag: String): FilterIntent()
    data object Confirm: FilterIntent()
    data object Clear: FilterIntent()
    data object GoToCategorySetting: FilterIntent()
}

data class FilterUiState(
    val categoryState: FilterState.MultiSelect<String> = FilterState.MultiSelect(emptyList(), mutableStateListOf()),
    val sortState: FilterState.SingleSelect<String> = FilterState.SingleSelect(emptyList(), mutableStateOf("최신순")),
    val siteState: FilterState.MultiSelect<String> = FilterState.MultiSelect(emptyList(), mutableStateListOf()),
    val tagState: FilterState.MultiSelect<String> = FilterState.MultiSelect(emptyList(), mutableStateListOf())
)

sealed class FilterUiEffect {
    data class Confirm(val category: List<String>, val sort: String, val site: List<String>, val tag: List<String>): FilterUiEffect()
    data object GoToCategorySetting: FilterUiEffect()
}

enum class FilterTab(val label: String) {
    CATEGORY("카테고리"),
    SORT("정렬"),
    SITE("사이트"),
    TAG("태그")
}