package com.jinscompany.saveurl.ui.search

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.UrlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val urlRepository: UrlRepository
) : ViewModel() {

    val filterList = listOf("전체", "제목", "내용", "태그")

    private val _searchResultUiState: MutableState<SearchScreenUiState<List<UrlData>>> = mutableStateOf(SearchScreenUiState.Init)
    val searchResultUiState: State<SearchScreenUiState<List<UrlData>>> = _searchResultUiState

    fun search(_keyword: String, filter: String) {
        viewModelScope.launch {
            val keyword = _keyword.trim()
            if (keyword.isEmpty()) {
                _searchResultUiState.value = SearchScreenUiState.Error(SearchErrorType.KEYWORD_EMPTY)
                return@launch
            }
            _searchResultUiState.value = SearchScreenUiState.Loading

            val searchResult = when (filter) {
                "전체" -> urlRepository.searchAll(keyword)
                "제목" -> urlRepository.searchByTitle(keyword)
                "내용" -> urlRepository.searchByDescription(keyword)
                "태그" -> urlRepository.searchByTag(keyword)
                else -> null
            }
            searchResult?.let {
                if (it.isEmpty()) {
                    _searchResultUiState.value = SearchScreenUiState.Empty
                } else {
                    _searchResultUiState.value = SearchScreenUiState.Success(it)
                }
            } ?: run {
                _searchResultUiState.value = SearchScreenUiState.Empty
            }
        }
    }
}