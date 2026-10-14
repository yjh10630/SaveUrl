package com.jinscompany.saveurl.data.room

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.ImportBatch
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.model.AutoCategoryRule
import com.jinscompany.saveurl.domain.model.LinkReminder
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

//todo 마이그레이션을 시도해야 한다 포크를 이용해서 현재 적용되어 있는거 스태시로 옮기고 처음부터 다시 앱 설치 후 링크 저장 몇개 하고 마이그레이션 테스트 진행 해야함... !!!

@Database(
    entities = [UrlData::class, CategoryModel::class, TrashItem::class, ImportBatch::class, AutoCategoryRule::class, LinkReminder::class],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        // 잠금 카테고리 (Category.isLocked DEFAULT 0)
        AutoMigration(from = 4, to = 5),
        // 링크 가져오기 (BaseSaveUrl.importBatchId·metaState, ImportBatch 테이블)
        AutoMigration(from = 5, to = 6),
        // 자동 분류 규칙 (AutoCategoryRule 추가, 조용히 추측하던 DomainCategory 학습 테이블 삭제. 학습값은 옮기지 않음: 36번 §10.1)
        AutoMigration(from = 6, to = 7, spec = AppDatabase.AutoMigration6to7::class),
        // 다시 보게 만들기 (LinkReminder 추가, BaseSaveUrl.revisitShownAt·(isRead, addDate) 인덱스, Trash.isRead DEFAULT 1. 42번 §3)
        AutoMigration(from = 7, to = 8, spec = AppDatabase.AutoMigration7to8::class),
    ]
)
@TypeConverters(value = [ListTypeConverter::class])
abstract class AppDatabase: RoomDatabase() {

    abstract fun baseSaveUrlDao(): BaseSaveUrlDao
    abstract fun categoryDao(): CategoryDao
    abstract fun trashDao(): TrashDao
    abstract fun importDao(): ImportDao
    abstract fun autoCategoryRuleDao(): AutoCategoryRuleDao
    abstract fun linkReminderDao(): LinkReminderDao

    @DeleteTable(tableName = "DomainCategory")
    class AutoMigration6to7 : AutoMigrationSpec

    /**
     * 가져온 링크는 읽음으로 넣는다 (42번 §7.1). 그 규칙 전에 가져온 링크(백업 복원 제외)도 읽음으로 맞춘다.
     * v8 전에는 안 읽음을 따로 보여 주는 화면이 없어 사용자가 직접 바꾼 값을 덮어쓸 일이 없다. 여러 번 실행해도 같다.
     */
    class AutoMigration7to8 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL(MARK_IMPORTED_AS_READ_SQL)
        }
    }

    companion object {
        const val MARK_IMPORTED_AS_READ_SQL =
            "UPDATE BaseSaveUrl SET isRead = 1 WHERE isRead = 0 AND importBatchId IN (SELECT id FROM ImportBatch WHERE source != 'backup')"

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