package com.jinscompany.saveurl.utils

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.jinscompany.saveurl.BuildConfig
import com.jinscompany.saveurl.SaveUrlApplication
import com.jinscompany.saveurl.SharedViewModel
import com.jinscompany.saveurl.domain.model.AppInfo
import com.jinscompany.saveurl.utils.InAppUpdatePolicy.CheckAction
import com.jinscompany.saveurl.utils.InAppUpdatePolicy.FlexibleDecline
import com.jinscompany.saveurl.utils.InAppUpdatePolicy.ForceUpdateBlockReason
import com.jinscompany.saveurl.utils.InAppUpdatePolicy.ResumeAction
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Remote Config(app_info) + Play In-App Updates.
 *
 * 확인 시점은 onResume 하나로 모은다 (콜드 스타트, 백그라운드에서 복귀, Play 화면에서 돌아옴).
 * 1. Play 상태로 진행 중인 업데이트를 이어간다 (재개 경로, 매번)
 * 2. 이어서 Remote Config 를 다시 평가한다 (Activity 재생성 직후는 건너뛰고 15분 간격, 강제 대상이면 매번)
 * 판단은 [InAppUpdatePolicy] 의 순수 함수가 맡는다.
 *
 * @param isRecreated savedInstanceState != null. 회전, 폴드 접기/펴기, 2분할 창 크기·언어 변경으로 재생성된 경우
 */
class InAppUpdateCheck(
    private val activity: ComponentActivity,
    private val immediateLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val flexibleLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val sharedViewModel: SharedViewModel,
    isRecreated: Boolean,
) {
    private val remoteConfig = Firebase.remoteConfig
    private val appUpdateManager = AppUpdateManagerFactory.create(activity)

    // 어떤 타입(IMMEDIATE/FLEXIBLE)으로 업데이트를 시작했는지 기록.
    // DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS 상태만으로는 타입을 알 수 없어서, 프로세스가 죽어도 남도록 디스크에 저장한다.
    private val updatePrefs = activity.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 재생성 직후 첫 onResume 은 RC 재평가를 건너뛴다 (강제 대상 제외) */
    private var isFirstResumeAfterRecreation = isRecreated

    /**
     * 이번 onResume 이후 업데이트 화면을 이미 띄웠는지.
     * 재개 경로와 RC 경로가 같은 onResume 안에서 각각 시작을 요청해 화면이 겹치는 것을 막는다.
     * Play 화면이 뜨면 이 Activity 가 pause 되므로, 다음 onResume 에서 풀어 준다.
     */
    private var launchedSinceResume = false

    private val flexibleInstallListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> sharedViewModel.setFlexibleUpdateDownloaded(true)
            InstallStatus.FAILED, InstallStatus.CANCELED -> {
                // 다운로드 실패/취소: 기록과 리스너를 정리해 다음 확인 때 처음부터 다시 판단 (스낵바가 남지 않게)
                log("flexible ${state.installStatus()} errorCode=${state.installErrorCode()}")
                clearStartedType()
                sharedViewModel.setFlexibleUpdateDownloaded(false)
                unregisterListener()
            }
            InstallStatus.INSTALLED -> unregisterListener()
            else -> Unit
        }
    }

    /** onResume 에서 호출 */
    fun onResume() {
        if (SaveUrlApplication.DEBUG) return
        launchedSinceResume = false
        val recheck = InAppUpdatePolicy.shouldRecheckOnResume(
            isBlockerVisible = sharedViewModel.forceUpdateBlock.value != null,
            isForcedByRemoteConfig = isForcedByRemoteConfig(readAppInfo()),
            isFirstResumeAfterRecreation = isFirstResumeAfterRecreation,
            lastCheckElapsedMillis = lastCheckElapsedMillis,
            nowElapsedMillis = SystemClock.elapsedRealtime(),
        )
        isFirstResumeAfterRecreation = false
        // 재개 경로를 먼저 끝낸 뒤 RC 를 평가한다. 동시에 돌리면 재개 경로의 기록 정리(ClearStartedType)가
        // RC 경로가 방금 쓴 시작 타입을 지울 수 있다.
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener(activity) { info ->
                resumeInProgressUpdate(info)
                if (recheck) fetchAndCheck(RC_FETCH_INTERVAL_SECONDS)
            }
            .addOnFailureListener(activity) { e ->
                recordFailure("appUpdateInfo (resume) failed", e)
                // Play 를 쓸 수 없어도 강제 대상이면 차단 화면을 띄워야 하므로 RC 평가는 계속한다
                if (recheck) fetchAndCheck(RC_FETCH_INTERVAL_SECONDS)
            }
    }

    /** 차단 화면의 "다시 시도/다시 확인": RC 를 짧은 간격으로 다시 받고 새 AppUpdateInfo 로 다시 판단 */
    fun recheckForceUpdate() {
        if (sharedViewModel.isForceUpdateRechecking.value) return
        sharedViewModel.setForceUpdateRechecking(true)
        launchedSinceResume = false
        fetchAndCheck(RETRY_FETCH_INTERVAL_SECONDS)
    }

    /** 마지막으로 활성화된 Remote Config 의 app_info. 값이 없으면(한 번도 못 받음) null */
    private fun readAppInfo(): AppInfo? {
        val result = AppInfoParser.parse(remoteConfig.getString(AppInfoParser.KEY_REMOTE_CONFIG))
        if (result.warnings.isNotEmpty()) {
            // 콘솔 설정 실수: release 에서도 보이도록 Crashlytics non-fatal 로 남긴다 (같은 내용은 프로세스당 한 번)
            val message = result.warnings.joinToString("; ")
            if (reportedAppInfoWarning != message) {
                reportedAppInfoWarning = message
                recordFailure("app_info 확인 필요: $message", null)
            }
        }
        return result.appInfo
    }

    private fun isForcedByRemoteConfig(appInfo: AppInfo?): Boolean =
        InAppUpdatePolicy.requiredUpdateType(
            currentVersionCode = BuildConfig.VERSION_CODE,
            minVersion = appInfo?.minVersion ?: 0,
            latestVersion = appInfo?.latestVersion ?: 0,
        ) == AppUpdateType.IMMEDIATE

    /** 설정·기본값은 프로세스당 한 번, fetch 전에 끝나도록 Task 로 묶어 둔다 */
    private fun ensureRemoteConfigReady(): Task<Void> = remoteConfigReady ?: Tasks.whenAll(
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings { minimumFetchIntervalInSeconds = RC_FETCH_INTERVAL_SECONDS }
        ),
        remoteConfig.setDefaultsAsync(mapOf(AppInfoParser.KEY_REMOTE_CONFIG to AppInfoParser.DEFAULT_JSON)),
    ).also { remoteConfigReady = it }

    private fun fetchAndCheck(fetchIntervalSeconds: Long) {
        lastCheckElapsedMillis = SystemClock.elapsedRealtime()
        // Activity 범위 리스너: onStop 이후(회전/종료로 파괴된 Activity)에는 콜백이 호출되지 않아
        // 해제된 ActivityResultLauncher 로 업데이트 플로우를 시작하는 크래시를 방지
        ensureRemoteConfigReady()
            .continueWithTask { remoteConfig.fetch(fetchIntervalSeconds) }
            .continueWithTask { fetch ->
                // fetch 실패(오프라인, 서버 제한)여도 이전에 받은 값을 쓰도록 activate 는 항상 시도
                if (!fetch.isSuccessful) log("remote config fetch failed: ${fetch.exception?.message}")
                remoteConfig.activate()
            }
            .addOnCompleteListener(activity) {
                val appInfo = readAppInfo()
                val requiredType = appInfo?.let {
                    InAppUpdatePolicy.requiredUpdateType(BuildConfig.VERSION_CODE, it.minVersion, it.latestVersion)
                }
                // min_version 을 내려 강제가 풀렸으면(되돌리기) 차단 화면을 닫는다
                if (requiredType != AppUpdateType.IMMEDIATE) sharedViewModel.hideForceUpdateBlock()
                if (requiredType == null) {
                    sharedViewModel.setForceUpdateRechecking(false)
                    return@addOnCompleteListener
                }
                checkUpdate(requiredType)
            }
    }

    private fun checkUpdate(requiredType: Int) {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener(activity) { info ->
                sharedViewModel.setFlexibleUpdate(InAppUpdatePolicy.isUpdateBadgeVisible(info.updateAvailability()))
                activity.lifecycleScope.launch {
                    val snoozed = requiredType == AppUpdateType.FLEXIBLE && InAppUpdatePolicy.isFlexibleSnoozed(
                        decline = sharedViewModel.getFlexibleUpdateDecline(),
                        availableVersionCode = info.availableVersionCode(),
                        nowMillis = System.currentTimeMillis(),
                    )
                    applyCheckAction(
                        action = InAppUpdatePolicy.decideCheckAction(
                            requiredType = requiredType,
                            updateAvailability = info.updateAvailability(),
                            isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                            isFlexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                            isFlexibleSnoozed = snoozed,
                        ),
                        info = info,
                        detail = "availability=${info.updateAvailability()} available=${info.availableVersionCode()} " +
                            "immediateAllowed=${info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)} " +
                            "failedPreconditions=${info.getFailedUpdatePreconditions(AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))}",
                    )
                }
            }
            .addOnFailureListener(activity) { e ->
                recordFailure("appUpdateInfo (check) failed", e)
                applyCheckAction(
                    action = InAppUpdatePolicy.decideCheckAction(requiredType, null, false, false, false),
                    info = null,
                    detail = "appUpdateInfo failed",
                )
            }
    }

    private fun applyCheckAction(action: CheckAction, info: AppUpdateInfo?, detail: String) {
        sharedViewModel.setForceUpdateRechecking(false)
        log("in-app update check > action=$action $detail")
        // 코루틴(스누즈 조회)을 거치는 사이 Activity 가 화면을 벗어났으면 시작하지 않는다 (다음 onResume 에서 다시 판단)
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        when (action) {
            CheckAction.StartImmediate -> info?.let { startUpdateFlow(it, AppUpdateType.IMMEDIATE) }
            CheckAction.StartFlexible -> info?.let { startUpdateFlow(it, AppUpdateType.FLEXIBLE) }
            CheckAction.ShowBlocker -> {
                recordFailure("force update not offered by Play: $detail", null)
                sharedViewModel.showForceUpdateBlock(ForceUpdateBlockReason.NOT_OFFERED)
            }
            CheckAction.None -> Unit
        }
    }

    private fun startUpdateFlow(info: AppUpdateInfo, updateType: Int) {
        if (launchedSinceResume) return
        if (updateType == AppUpdateType.FLEXIBLE) {
            appUpdateManager.registerListener(flexibleInstallListener)
            pendingFlexibleVersionCode = info.availableVersionCode()
        }
        // 플로우 시작 전에 기록: 다운로드 도중 프로세스가 종료되어도 재개 시 같은 타입으로 이어가도록
        updatePrefs.edit().putInt(KEY_STARTED_TYPE, updateType).apply()
        val launcher = if (updateType == AppUpdateType.IMMEDIATE) immediateLauncher else flexibleLauncher
        val started = appUpdateManager.startUpdateFlowForResult(
            info,
            launcher,
            AppUpdateOptions.newBuilder(updateType).build(),
        )
        launchedSinceResume = started
        if (started) return
        log("startUpdateFlowForResult returned false (type=$updateType)")
        if (updateType == AppUpdateType.FLEXIBLE) {
            clearStartedType()
            unregisterListener()
        } else {
            // IMMEDIATE 기록은 남겨둔다(강제 쪽으로 기우는 것이 안전). 화면을 띄우지 못했으니 차단 화면으로 안내
            sharedViewModel.showForceUpdateBlock(ForceUpdateBlockReason.FAILED)
        }
    }

    private fun startedType(): Int? =
        if (updatePrefs.contains(KEY_STARTED_TYPE)) updatePrefs.getInt(KEY_STARTED_TYPE, AppUpdateType.FLEXIBLE) else null

    private fun clearStartedType() {
        updatePrefs.edit().remove(KEY_STARTED_TYPE).apply()
    }

    fun completeFlexibleUpdate() {
        appUpdateManager.completeUpdate()
    }

    fun onImmediateActivityResult(resultCode: Int) {
        val reason = InAppUpdatePolicy.immediateResultBlockReason(resultCode) ?: return
        if (reason == ForceUpdateBlockReason.FAILED) {
            recordFailure("immediate update failed (RESULT_IN_APP_UPDATE_FAILED)", null)
        } else {
            log("immediate update canceled (resultCode=$resultCode)")
        }
        // 설명 없이 종료하지 않고, 닫을 수 없는 안내 화면에서 다시 시도 / Play 스토어 / 종료를 고르게 한다
        sharedViewModel.showForceUpdateBlock(reason)
    }

    fun onFlexibleActivityResult(resultCode: Int) {
        val versionCode = pendingFlexibleVersionCode
        pendingFlexibleVersionCode = null
        when (resultCode) {
            Activity.RESULT_OK -> log("flexible update accepted")
            Activity.RESULT_CANCELED -> {
                // "나중에": 이 버전은 3일 동안 다시 묻지 않는다. 다운로드를 시작하지 않았으니 기록과 리스너도 정리
                clearStartedType()
                unregisterListener()
                if (versionCode != null) {
                    sharedViewModel.recordFlexibleUpdateDecline(FlexibleDecline(versionCode, System.currentTimeMillis()))
                }
                log("flexible update declined (version=$versionCode)")
            }
            ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> {
                clearStartedType()
                unregisterListener()
                recordFailure("flexible update failed (RESULT_IN_APP_UPDATE_FAILED)", null)
            }
        }
    }

    /** 재개 경로: 진행 중인 업데이트를 원래 타입 그대로 이어간다 */
    private fun resumeInProgressUpdate(info: AppUpdateInfo) {
        sharedViewModel.setFlexibleUpdate(InAppUpdatePolicy.isUpdateBadgeVisible(info.updateAvailability()))
        val action = InAppUpdatePolicy.decideResumeAction(
            updateAvailability = info.updateAvailability(),
            installStatus = info.installStatus(),
            startedType = startedType(),
            isForcedByRemoteConfig = isForcedByRemoteConfig(readAppInfo()),
            isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
        )
        log("in-app update resume > availability=${info.updateAvailability()} status=${info.installStatus()} action=$action")
        when (action) {
            ResumeAction.ResumeImmediate -> startUpdateFlow(info, AppUpdateType.IMMEDIATE)
            ResumeAction.ObserveFlexibleDownload -> appUpdateManager.registerListener(flexibleInstallListener)
            ResumeAction.ShowFlexibleDownloaded -> sharedViewModel.setFlexibleUpdateDownloaded(true)
            ResumeAction.ClearStartedType -> clearStartedType()
            ResumeAction.None -> Unit
        }
    }

    fun unregisterListener() {
        appUpdateManager.unregisterListener(flexibleInstallListener)
    }

    /** CmLog 는 debug 에서만 출력하므로 release 에서도 남도록 Crashlytics 로그에도 남긴다 (크래시/non-fatal 보고에 함께 첨부) */
    private fun log(message: String) {
        CmLog.d(message)
        Firebase.crashlytics.log("InAppUpdate: $message")
    }

    /** 현장에서 원인을 추적할 수 있도록 Crashlytics non-fatal 로 기록 (InstallException 이면 errorCode 포함) */
    private fun recordFailure(message: String, e: Exception?) {
        val errorCode = (e as? InstallException)?.errorCode
        val fullMessage = if (errorCode != null) "$message (errorCode=$errorCode)" else message
        CmLog.e("$fullMessage ${e?.message ?: ""}")
        Firebase.crashlytics.log("InAppUpdate: $fullMessage")
        Firebase.crashlytics.recordException(InAppUpdateException(fullMessage, e))
    }

    private class InAppUpdateException(message: String, cause: Throwable?) : Exception(message, cause)

    private companion object {
        const val PREFS_NAME = "in_app_update"
        const val KEY_STARTED_TYPE = "started_update_type"

        /** 강제 업데이트 반영 속도 우선. 하루 24회 이하/기기 → Remote Config 할당량 안 */
        val RC_FETCH_INTERVAL_SECONDS: Long = TimeUnit.HOURS.toSeconds(1)
        /** 차단 화면에서 사용자가 직접 다시 확인할 때. 연타해도 서버 제한에 걸리지 않을 정도로만 줄인다 */
        val RETRY_FETCH_INTERVAL_SECONDS: Long = TimeUnit.MINUTES.toSeconds(1)

        // 아래는 프로세스 단위로 유지 (Activity 재생성에도 남고, 콜드 스타트에서는 초기화)
        var remoteConfigReady: Task<Void>? = null
        var lastCheckElapsedMillis: Long? = null
        /** FLEXIBLE 동의 창을 띄운 버전. 결과가 재생성된 Activity 로 전달되어도 거부한 버전을 알 수 있게 */
        var pendingFlexibleVersionCode: Int? = null
        var reportedAppInfoWarning: String? = null
    }
}
