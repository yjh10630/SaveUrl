package com.jinscompany.saveurl.data.room

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

//todo 마이그레이션을 시도해야 한다 포크를 이용해서 현재 적용되어 있는거 스태시로 옮기고 처음부터 다시 앱 설치 후 링크 저장 몇개 하고 마이그레이션 테스트 진행 해야함... !!!

@Database(
    entities = [UrlData::class, CategoryModel::class, TrashItem::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
@TypeConverters(value = [ListTypeConverter::class])
abstract class AppDatabase: RoomDatabase() { 

    abstract fun baseSaveUrlDao(): BaseSaveUrlDao
    abstract fun categoryDao(): CategoryDao
    abstract fun trashDao(): TrashDao

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
                            }
                        })
                        .build()
                }
                INSTANCE
            }
    }
}