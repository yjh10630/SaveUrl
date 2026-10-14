package com.jinscompany.saveurl

import android.app.Application
import coil.ImageLoader
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoaderFactory
import com.google.android.gms.ads.MobileAds
import com.jinscompany.saveurl.data.backfill.NormalizedUrlBackfillRunner
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.importer.ImportCommitRunner
import com.jinscompany.saveurl.domain.importer.MetaFillScheduler
import com.jinscompany.saveurl.utils.CmLog
import com.jinscompany.saveurl.utils.CrashReporter
import com.jinscompany.saveurl.utils.ThemeMigrationRunner
import com.jinscompany.saveurl.utils.globalCoroutineExceptionHandler
import com.jinscompany.saveurl.utils.isDebuggable
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class SaveUrlApplication: Application(), ImageLoaderFactory {
    companion object {
        lateinit var INSTANCE: SaveUrlApplication
        var DEBUG: Boolean = true
    }

    @Inject
    lateinit var normalizedUrlBackfillRunner: NormalizedUrlBackfillRunner

    @Inject
    lateinit var themeMigrationRunner: ThemeMigrationRunner

    @Inject
    lateinit var categoryLockSession: CategoryLockSession

    @Inject
    lateinit var metaFillScheduler: MetaFillScheduler

    @Inject
    lateinit var importCommitRunner: ImportCommitRunner

    init {
        INSTANCE = this
    }


    override fun onCreate() {
        // 화면 모드 기본값 마이그레이션용: DataStore/Room 이 파일을 만들기 전(Hilt 주입·백필 시작 전)에 이전 데이터 유무를 확인
        val hadPriorData = ThemeMigrationRunner.snapshotPriorData(this)
        // release: 앱 종료 오류도 메시지(링크 주소 등) 없이 보낸다. debug 는 원래 메시지를 보도록 그대로 둔다.
        // super.onCreate()(Hilt 주입) 중 크래시도 정리되도록 그 전에 건다 (Crashlytics 핸들러는 ContentProvider 에서 이미 설치됨)
        DEBUG = isDebuggable(this)
        if (!DEBUG) CrashReporter.installUncaughtHandler()
        super.onCreate()
        themeMigrationRunner.start(hadPriorData)
        // 업데이트 전 저장된 링크의 normalizedUrl 채우기 / 정규화 규칙 변경 시 재계산 (IO 스레드, 시작을 막지 않음)
        normalizedUrlBackfillRunner.start()
        // 가져온 링크 중 미리보기를 못 채운 것이 있으면 이어서 채운다 (이미 예약돼 있으면 그대로)
        metaFillScheduler.schedule()
        // 지난번에 가져오다 앱이 죽어 링크 없이 남은 묶음 정리 (기록에 되돌릴 수 없는 빈 줄이 남지 않게)
        importCommitRunner.cleanupInterrupted()
        // 잠금 카테고리: 앱 전체가 나간 시각을 적고, 1분 넘게 지나 돌아오면 다시 잠근다 (회전·접기는 프로세스 이벤트가 아님)
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> categoryLockSession.onAppBackground()
                Lifecycle.Event.ON_START -> categoryLockSession.onAppForeground()
                else -> Unit
            }
        })
        CoroutineScope(Dispatchers.IO + globalCoroutineExceptionHandler).launch {
            MobileAds.initialize(this@SaveUrlApplication) {
                CmLog.d("MobileAds initialize")
            }
        }
    }

    // 썸네일 요청에 앱 User-Agent 를 붙인다 (위키미디어 등은 기본 okhttp UA 를 403 으로 거부)
    override fun newImageLoader(): ImageLoader {
        val userAgent = "SaveLink/${BuildConfig.VERSION_NAME} (Android; https://play.google.com/store/apps/details?id=$packageName)"
        return ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
                    }
                    .build()
            }
            .build()
    }
}