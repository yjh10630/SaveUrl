package com.jinscompany.saveurl.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

@Database(entities = [UrlData::class, CategoryModel::class], version = 1, exportSchema = true)
@TypeConverters(value = [ListTypeConverter::class])
abstract class AppDatabase: RoomDatabase() {
    abstract fun baseSaveUrlDao(): BaseSaveUrlDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        lateinit var INSTANCE: AppDatabase
        fun getInstance(context: Context): AppDatabase =
            if (Companion::INSTANCE.isInitialized) INSTANCE else {
                synchronized(AppDatabase::class) {
                    INSTANCE = Room
                        .databaseBuilder(context, AppDatabase::class.java, "SaveUrl.db")
                        .addCallback(object: Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                super.onCreate(db)
                                /*Executors.newSingleThreadExecutor().execute {
                                    runBlocking {
                                        getInstance(context).withTransaction {
                                            getInstance(context).categoryDao().insert(
                                                CategoryModel(
                                                    name = "북마크",
                                                    contentCnt = 0,
                                                    order = 1,
                                                    isEditable = false
                                                )
                                            )
                                            getInstance(context).categoryDao().insert(
                                                CategoryModel(
                                                    name = "전체",
                                                    contentCnt = 0,
                                                    order = 2,
                                                    isEditable = false
                                                )
                                            )
                                        }
                                    }
                                }*/
                            }
                        })
                        .build()
                }
                INSTANCE
            }
    }
}