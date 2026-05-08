package com.jinscompany.saveurl.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DomainCategoryDao {

    @Query("SELECT * FROM DomainCategory WHERE domain = :domain LIMIT 1")
    suspend fun get(domain: String): DomainCategoryEntity?

    @Query("SELECT * FROM DomainCategory ORDER BY learnCount DESC")
    suspend fun getAll(): List<DomainCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DomainCategoryEntity)

    @Query("""
        INSERT INTO DomainCategory (domain, category, learnCount)
        VALUES (:domain, :category, 1)
        ON CONFLICT(domain) DO UPDATE SET
            category = CASE WHEN excluded.category != category THEN excluded.category ELSE category END,
            learnCount = learnCount + 1
    """)
    suspend fun upsert(domain: String, category: String)
}
