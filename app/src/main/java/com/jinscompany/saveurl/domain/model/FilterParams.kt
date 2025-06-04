package com.jinscompany.saveurl.domain.model

data class FilterParams(
    val categories: List<String>,
    val sort: String,
    val siteList: List<String>,
    val tagList: List<String>
) {
    fun getMainSelectedList(): List<String> =
        mutableListOf<String>().apply {
            addAll(categories)
            add(sort)
            addAll(siteList)
            addAll(tagList)
        }
}
