package com.jinscompany.saveurl.ui.search

sealed class SearchIntent {
    data class Search(val keyword: String, val filter: String) : SearchIntent()
    data object ClearSearch : SearchIntent()
}
