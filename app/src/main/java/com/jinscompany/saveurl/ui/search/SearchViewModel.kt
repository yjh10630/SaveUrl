package com.jinscompany.saveurl.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.usecase.SearchAllUseCase
import com.jinscompany.saveurl.domain.usecase.SearchByDescriptionUseCase
import com.jinscompany.saveurl.domain.usecase.SearchByTagUseCase
import com.jinscompany.saveurl.domain.usecase.SearchByTitleUseCase
import com.jinscompany.saveurl.ui.FilterDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchAllUseCase: SearchAllUseCase,
    private val searchByTitleUseCase: SearchByTitleUseCase,
    private val searchByDescriptionUseCase: SearchByDescriptionUseCase,
    private val searchByTagUseCase: SearchByTagUseCase,
) : ViewModel() {

    val filterList = listOf(FilterDefaults.CATEGORY_ALL, "제목", "내용", "태그")
    var searchResultFlow by mutableStateOf<Flow<PagingData<UrlData>>?>(null)
        private set

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.Search -> search(intent.keyword, intent.filter)
            SearchIntent.ClearSearch -> { searchResultFlow = null }
        }
    }

    private fun search(_keyword: String, filter: String) {
        val keyword = _keyword.trim()
        if (keyword.isEmpty()) {
            searchResultFlow = null
            return
        }
        searchResultFlow = Pager(
            config = PagingConfig(pageSize = 10, prefetchDistance = 5, enablePlaceholders = false),
            pagingSourceFactory = {
                when (filter) {
                    "제목" -> searchByTitleUseCase(keyword)
                    "내용" -> searchByDescriptionUseCase(keyword)
                    "태그" -> searchByTagUseCase(keyword)
                    else -> searchAllUseCase(keyword)
                }
            }
        ).flow.cachedIn(viewModelScope)
    }
}
