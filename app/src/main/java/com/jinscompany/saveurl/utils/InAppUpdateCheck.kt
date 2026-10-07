package com.jinscompany.saveurl.utils

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.gson.Gson
import com.jinscompany.saveurl.BuildConfig
import com.jinscompany.saveurl.SaveUrlApplication
import com.jinscompany.saveurl.SharedViewModel
import com.jinscompany.saveurl.domain.model.AppInfo
import com.jinscompany.saveurl.utils.InAppUpdatePolicy.ResumeAction
import java.util.concurrent.TimeUnit

class InAppUpdateCheck(
    private val activity: ComponentActivity,
    private val immediateLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val flexibleLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val sharedViewModel: SharedViewModel
) {
    private val remoteConfig = Firebase.remoteConfig
    private val appUpdateManager = AppUpdateManagerFactory.create(activity)

    // 어떤 타입(IMMEDIATE/FLEXIBLE)으로 업데이트를 시작했는지 기록.
    // DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS 상태만으로는 타입을 알 수 없어서, 프로세스가 죽어도 남도록 디스크에 저장한다.
    private val updatePrefs = activity.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val flexibleInstallListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            sharedViewModel.setFlexibleUpdateDownloaded(true)
        }
    }

    init {
        if (SaveUrlApplication.DEBUG) {
            CmLog.d("Current Version > DEBUG - In-App Check Pass")
        } else {
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = TimeUnit.HOURS.toSeconds(24)
            }
            remoteConfig.setConfigSettingsAsync(configSettings)
            fetchRemoteConfig()
        }
    }

    /** 마지막으로 활성화된 Remote Config 의 app_info. fetch 실패/형식 오류면 null */
    private fun readAppInfo(): AppInfo? = try {
        // fetch 실패(오프라인 첫 실행 등)로 값이 없으면 "" -> Gson 이 null 을 반환하므로 NPE 방지
        Gson().fromJson(remoteConfig.getString("app_info"), AppInfo::class.java)
    } catch (e: Exception) {
        CmLog.e("app_info parse error > ${e.message}")
        null
    }

    private fun fetchRemoteConfig() {
        // Activity 범위 리스너: onStop 이후(회전/종료로 파괴된 Activity)에는 콜백이 호출되지 않아
        // 해제된 ActivityResultLauncher 로 업데이트 플로우를 시작하는 크래시를 방지
        remoteConfig.fetchAndActivate().addOnCompleteListener(activity) {
            val appInfo = readAppInfo() ?: return@addOnCompleteListener
            val updateType = InAppUpdatePolicy.requiredUpdateType(
                currentVersionCode = BuildConfig.VERSION_CODE,
                minVersion = appInfo.minVersion,
                latestVersion = appInfo.latestVersion,
            ) ?: return@addOnCompleteListener
            checkUpdate(updateType)
        }
    }

    private fun checkUpdate(updateType: Int) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener(activity) { info ->
            sharedViewModel.setFlexibleUpdate(InAppUpdatePolicy.isUpdateBadgeVisible(info.updateAvailability()))
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(updateType)) {
                startUpdateFlow(info, updateType)
            }
        }
    }

    private fun startUpdateFlow(info: AppUpdateInfo, updateType: Int) {
        if (updateType == AppUpdateType.FLEXIBLE) {
            appUpdateManager.registerListener(flexibleInstallListener)
        }
        // 플로우 시작 전에 기록: 다운로드 도중 프로세스가 종료되어도 재개 시 같은 타입으로 이어가도록
        updatePrefs.edit().putInt(KEY_STARTED_TYPE, updateType).apply()
        val launcher = if (updateType == AppUpdateType.IMMEDIATE) immediateLauncher else flexibleLauncher
        val started = appUpdateManager.startUpdateFlowForResult(
            info,
            launcher,
            AppUpdateOptions.newBuilder(updateType).build(),
        )
        // IMMEDIATE 기록은 실패해도 남겨둔다(강제 쪽으로 기우는 것이 안전). FLEXIBLE 만 정리
        if (!started && updateType == AppUpdateType.FLEXIBLE) clearStartedType()
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
        if (resultCode != Activity.RESULT_OK) {
            activity.finish()
        }
    }

    /** onResume 에서 호출: 진행 중인 업데이트를 원래 타입 그대로 이어간다 */
    fun resumeUpdateIfNeeded() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener(activity) { info ->
            sharedViewModel.setFlexibleUpdate(InAppUpdatePolicy.isUpdateBadgeVisible(info.updateAvailability()))
            val forcedByRemoteConfig = readAppInfo()?.let {
                BuildConfig.VERSION_CODE < it.minVersion
            } ?: false
            val action = InAppUpdatePolicy.decideResumeAction(
                updateAvailability = info.updateAvailability(),
                installStatus = info.installStatus(),
                startedType = startedType(),
                isForcedByRemoteConfig = forcedByRemoteConfig,
                isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
            )
            CmLog.d("in-app update resume > availability=${info.updateAvailability()} status=${info.installStatus()} action=$action")
            when (action) {
                ResumeAction.ResumeImmediate -> startUpdateFlow(info, AppUpdateType.IMMEDIATE)
                ResumeAction.ObserveFlexibleDownload -> appUpdateManager.registerListener(flexibleInstallListener)
                ResumeAction.ShowFlexibleDownloaded -> sharedViewModel.setFlexibleUpdateDownloaded(true)
                ResumeAction.ClearStartedType -> clearStartedType()
                ResumeAction.None -> Unit
            }
        }
    }

    fun unregisterListener() {
        appUpdateManager.unregisterListener(flexibleInstallListener)
    }

    private companion object {
        const val PREFS_NAME = "in_app_update"
        const val KEY_STARTED_TYPE = "started_update_type"
    }
}
