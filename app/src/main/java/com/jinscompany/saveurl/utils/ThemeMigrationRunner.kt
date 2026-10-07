package com.jinscompany.saveurl.utils

import android.content.Context
import com.jinscompany.saveurl.domain.model.ThemeDefaultMigration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ThemeDefaultMigration] 실행기.
 * Application.onCreate 에서 [snapshotPriorData] 로 이전 데이터 유무를 먼저 확인한 뒤 [start] 한다.
 * 화면 모드는 [awaitDone] 이후에 읽어서 첫 화면이 잘못된 테마로 그려지지 않게 한다 (스플래시가 그동안 유지됨).
 */
@Singleton
class ThemeMigrationRunner @Inject constructor(
    private val preferencesManager: PreferencesManager,
) {
    private val done = MutableStateFlow(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(hadPriorData: Boolean) {
        scope.launch {
            try {
                val action = preferencesManager.migrateThemeDefault(hadPriorData)
                CmLog.d("ThemeDefaultMigration hadPriorData=$hadPriorData action=$action")
            } catch (e: Exception) {
                // 실패해도 앱은 계속 동작해야 한다 (다음 실행에서 다시 시도: 완료 표시가 남지 않았으므로)
                CmLog.e("ThemeDefaultMigration failed: ${e.message}")
            } finally {
                done.value = true
            }
        }
    }

    suspend fun awaitDone() {
        done.first { it }
    }

    companion object {
        /**
         * DataStore/Room 이 파일을 만들기 전에 호출해야 한다 (Application.onCreate 의 super.onCreate() 이전).
         * 설정 파일 또는 DB 파일이 이미 있으면 이전에 앱을 실행한 적이 있는 사용자다.
         */
        fun snapshotPriorData(context: Context): Boolean = ThemeDefaultMigration.hadPriorData(
            settingsFileExisted = File(context.filesDir, "datastore/settings.preferences_pb").exists(),
            databaseFileExisted = context.getDatabasePath("SaveUrl.db").exists(),
        )
    }
}
