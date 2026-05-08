package com.jinscompany.saveurl.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "DomainCategory")
data class DomainCategoryEntity(
    @PrimaryKey @ColumnInfo(name = "domain") val domain: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "learnCount", defaultValue = "1") val learnCount: Int = 1,
)
