package com.jinscompany.saveurl.data.mapper

import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.model.UrlData

fun UrlData.toTrashItem(): TrashItem = TrashItem(
    id = id,
    url = url ?: "",
    imgUrl = imgUrl ?: "",
    siteName = siteName ?: "",
    title = title ?: "",
    description = description ?: "",
    tagList = tagList ?: emptyList(),
    addDate = addDate,
    category = category ?: "",
    isBookMark = isBookMark,
)

fun TrashItem.toUrlData(): UrlData = UrlData(
    id = id,
    url = url,
    imgUrl = imgUrl,
    siteName = siteName,
    title = title,
    description = description,
    tagList = tagList,
    addDate = addDate,
    category = category,
    isBookMark = isBookMark
)
