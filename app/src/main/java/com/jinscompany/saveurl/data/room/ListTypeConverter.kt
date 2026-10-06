package com.jinscompany.saveurl.data.room

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ListTypeConverter {
    private val gson = Gson()
    // R8 full mode 에서 익명 TypeToken 서브클래스의 제네릭 시그니처가 제거되어 크래시가 나므로 getParameterized 사용
    private val listType = TypeToken.getParameterized(List::class.java, String::class.java).type

    @TypeConverter
    fun fromTagList(tags: List<String>?): String {
        return gson.toJson(tags)
    }

    @TypeConverter
    fun toTagList(data: String): List<String> {
        return gson.fromJson<List<String>>(data, listType) ?: emptyList()
    }
}
