package com.jinscompany.saveurl.data.room

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ListTypeConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromTagList(tags: List<String>?): String {
        return gson.toJson(tags)
    }

    @TypeConverter
    fun toTagList(data: String): List<String> {
        return gson.fromJson(data, object : TypeToken<List<String>>() {}.type) ?: emptyList()
    }
}
