package com.jinscompany.saveurl

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.jinscompany.saveurl.data.backfill.NormalizedUrlBackfillRunner
import com.jinscompany.saveurl.utils.CmLog
import com.jinscompany.saveurl.utils.ThemeMigrationRunner
import com.jinscompany.saveurl.utils.globalCoroutineExceptionHandler
import com.jinscompany.saveurl.utils.isDebuggable
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SaveUrlApplication: Application() {
    companion object {
        lateinit var INSTANCE: SaveUrlApplication
        var DEBUG: Boolean = true
    }

    @Inject
    lateinit var normalizedUrlBackfillRunner: NormalizedUrlBackfillRunner

    @Inject
    lateinit var themeMigrationRunner: ThemeMigrationRunner

    init {
        INSTANCE = this
    }


    override fun onCreate() {
        // 화면 모드 기본값 마이그레이션용: DataStore/Room 이 파일을 만들기 전(Hilt 주입·백필 시작 전)에 이전 데이터 유무를 확인
        val hadPriorData = ThemeMigrationRunner.snapshotPriorData(this)
        super.onCreate()
        DEBUG = isDebuggable(this)
        themeMigrationRunner.start(hadPriorData)
        // 업데이트 전 저장된 링크의 normalizedUrl 채우기 / 정규화 규칙 변경 시 재계산 (IO 스레드, 시작을 막지 않음)
        normalizedUrlBackfillRunner.start()
        CoroutineScope(Dispatchers.IO + globalCoroutineExceptionHandler).launch {
            MobileAds.initialize(this@SaveUrlApplication) {
                CmLog.d("MobileAds initialize")
            }
        }
    }
}