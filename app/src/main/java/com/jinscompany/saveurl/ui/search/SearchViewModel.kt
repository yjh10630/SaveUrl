package com.jinscompany.saveurl.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.SearchScope
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.usecase.SearchAllUseCase
import com.jinscompany.saveurl.domain.usecase.SearchByDescriptionUseCase
import com.jinscompany.saveurl.domain.usecase.SearchByTagUseCase
import com.jinscompany.saveurl.domain.usecase.SearchByTitleUseCase
import com.jinscompany.saveurl.domain.usecase.SearchSortedUseCase
import com.jinscompany.saveurl.ui.FilterDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 검색.
 * - 검색어·검색 범위는 SavedStateHandle 에 두어, 폰 검색 화면과 2분할 왼쪽 검색 패널이 바뀌어도
 *   (접기·펼치기 = 액티비티 재생성) 그대로 이어진다.
 * - 필터([filterParams])는 2분할의 오른쪽 필터 패널에서만 보이고 적용된다([setFilterEnabled]).
 *   폰 검색 화면에는 필터 UI 가 없으므로 기존처럼 필터 없이 검색한다.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val searchAllUseCase: SearchAllUseCase,
    private val searchByTitleUseCase: SearchByTitleUseCase,
    private val searchByDescriptionUseCase: SearchByDescriptionUseCase,
    private val searchByTagUseCase: SearchByTagUseCase,
    private val searchSortedUseCase: SearchSortedUseCase,
) : ViewModel() {

    val filterList = listOf(FilterDefaults.CATEGORY_ALL, "제목", "내용", "태그")

    var keyword by mutableStateOf(savedStateHandle.get<String>(KEY_KEYWORD).orEmpty())
        private set
    var selectedScope by mutableStateOf(savedStateHandle.get<String>(KEY_SCOPE) ?: filterList[0])
        private set
    var filterParams by mutableStateOf(restoreFilter())
        private set

    var searchResultFlow by mutableStateOf<Flow<PagingData<UrlData>>?>(null)
        private set

    private var filterEnabled = false
    /** 마지막으로 실행한 검색 (필터가 바뀌면 같은 검색어로 다시 검색) */
    private var lastSearch: Pair<String, String>? = null

    fun onKeywordChange(value: String) {
        keyword = value
        savedStateHandle[KEY_KEYWORD] = value
    }

    fun onScopeChange(scope: String) {
        selectedScope = scope
        savedStateHandle[KEY_SCOPE] = scope
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.Search -> search(intent.keyword, intent.filter)
            SearchIntent.ClearSearch -> { searchResultFlow = null }
        }
    }

    /** 2분할(필터 패널 표시)일 때만 true. 바뀌면 같은 검색어로 다시 검색한다. */
    fun setFilterEnabled(enabled: Boolean) {
        if (filterEnabled == enabled) return
        filterEnabled = enabled
        lastSearch?.let { (k, f) -> search(k, f) }
    }

    /** 필터 패널에서 바꾼 값 (바로 반영) */
    fun setFilter(params: FilterParams) {
        if (params == filterParams) return
        filterParams = params
        savedStateHandle[KEY_CATEGORIES] = ArrayList(params.categories)
        savedStateHandle[KEY_SORT] = params.sort
        savedStateHandle[KEY_SITES] = ArrayList(params.siteList)
        savedStateHandle[KEY_TAGS] = ArrayList(params.tagList)
        if (filterEnabled) lastSearch?.let { (k, f) -> search(k, f) }
    }

    private fun restoreFilter(): FilterParams = FilterParams(
        categories = savedStateHandle.get<ArrayList<String>>(KEY_CATEGORIES) ?: listOf(FilterDefaults.CATEGORY_ALL),
        sort = savedStateHandle.get<String>(KEY_SORT) ?: FilterDefaults.SORT_LATEST,
        siteList = savedStateHandle.get<ArrayList<String>>(KEY_SITES) ?: emptyList(),
        tagList = savedStateHandle.get<ArrayList<String>>(KEY_TAGS) ?: emptyList(),
    )

    private fun search(_keyword: String, filter: String) {
        lastSearch = _keyword to filter
        val keyword = _keyword.trim()
        if (keyword.isEmpty()) {
            searchResultFlow = null
            return
        }
        if (filterEnabled) {
            val params = filterParams
            searchResultFlow = Pager(
                config = PagingConfig(pageSize = 10, prefetchDistance = 5, enablePlaceholders = false),
                pagingSourceFactory = {
                    searchSortedUseCase(keyword, filter.toScope(), oldest = params.sort == FilterDefaults.SORT_OLDEST)
                }
            ).flow
                .map { paging -> paging.filter { it.matches(params) } }
                .cachedIn(viewModelScope)
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

    private fun String.toScope(): SearchScope = when (this) {
        "제목" -> SearchScope.TITLE
        "내용" -> SearchScope.DESCRIPTION
        "태그" -> SearchScope.TAG
        else -> SearchScope.ALL
    }

    companion object {
        private const val KEY_KEYWORD = "search_keyword"
        private const val KEY_SCOPE = "search_scope"
        private const val KEY_CATEGORIES = "search_filter_categories"
        private const val KEY_SORT = "search_filter_sort"
        private const val KEY_SITES = "search_filter_sites"
        private const val KEY_TAGS = "search_filter_tags"

        /** 필터 시트와 같은 의미: 전체 = 카테고리 무관, 북마크 = 즐겨찾기만, 그 외 = 고른 카테고리 중 하나. 사이트·태그는 하나라도 일치 */
        internal fun UrlData.matches(params: FilterParams): Boolean {
            val categories = params.categories
            val categoryOk = when {
                categories.isEmpty() || categories.contains(FilterDefaults.CATEGORY_ALL) -> true
                categories.contains(FilterDefaults.CATEGORY_BOOKMARK) -> isBookMark
                else -> category in categories
            }
            val siteOk = params.siteList.isEmpty() || siteName in params.siteList
            val tagOk = params.tagList.isEmpty() || tagList?.any { it in params.tagList } == true
            return categoryOk && siteOk && tagOk
        }
    }
}
