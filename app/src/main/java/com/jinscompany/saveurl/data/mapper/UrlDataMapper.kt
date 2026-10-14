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
    // 복원할 때 휴지통에 가기 전 읽음 값을 돌려주려고 함께 옮긴다 (42번 §3.2)
    isRead = isRead,
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
    isBookMark = isBookMark,
    isRead = isRead,
)
