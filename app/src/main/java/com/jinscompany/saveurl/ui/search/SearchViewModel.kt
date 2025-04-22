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
import com.jinscompany.saveurl.domain.repository.UrlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val urlRepository: UrlRepository
) : ViewModel() {

    val filterList = listOf("전체", "제목", "내용", "태그")
    var searchResultFlow by mutableStateOf<Flow<PagingData<UrlData>>?>(null)
        private set

    fun search(_keyword: String, filter: String){
        val keyword = _keyword.trim()
        if (keyword.isEmpty()) searchResultFlow = null
        searchResultFlow = Pager(
            config = PagingConfig(
                pageSize = 10,
                prefetchDistance = 5,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                when (filter) {
                    "제목" -> urlRepository.searchByTitle(keyword)
                    "내용" -> urlRepository.searchByDescription(keyword)
                    "태그" -> urlRepository.searchByTag(keyword)
                    else -> urlRepository.searchAll(keyword)
                }
            }
        ).flow.cachedIn(viewModelScope)
    }


}