package com.jinscompany.saveurl.utils

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
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
import java.util.concurrent.TimeUnit

class InAppUpdateCheck(
    private val activity: ComponentActivity,
    private val immediateLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val flexibleLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val sharedViewModel: SharedViewModel
) {
    private val remoteConfig = Firebase.remoteConfig
    private val appUpdateManager = AppUpdateManagerFactory.create(activity)

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

    private fun fetchRemoteConfig() {
        // Activity 범위 리스너: onStop 이후(회전/종료로 파괴된 Activity)에는 콜백이 호출되지 않아
        // 해제된 ActivityResultLauncher 로 업데이트 플로우를 시작하는 크래시를 방지
        remoteConfig.fetchAndActivate().addOnCompleteListener(activity) {
            // fetch 실패(오프라인 첫 실행 등)로 값이 없으면 "" -> Gson 이 null 을 반환하므로 NPE 방지
            val appInfo = try {
                Gson().fromJson(remoteConfig.getString("app_info"), AppInfo::class.java)
            } catch (e: Exception) {
                CmLog.e("app_info parse error > ${e.message}")
                null
            } ?: return@addOnCompleteListener
            val currentBuildCode = BuildConfig.VERSION_CODE

            val updateType = when {
                currentBuildCode < appInfo.minVersion -> AppUpdateType.IMMEDIATE
                currentBuildCode < appInfo.latestVersion -> AppUpdateType.FLEXIBLE
                else -> null
            }

            when (updateType) {
                AppUpdateType.IMMEDIATE -> checkUpdate(AppUpdateType.IMMEDIATE)
                AppUpdateType.FLEXIBLE -> checkUpdate(AppUpdateType.FLEXIBLE)
            }
        }
    }

    private fun checkUpdate(updateType: Int) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener(activity) { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(updateType)) {
                if (updateType == AppUpdateType.FLEXIBLE) {
                    appUpdateManager.registerListener(flexibleInstallListener)
                }
                val launcher = if (updateType == AppUpdateType.IMMEDIATE) immediateLauncher else flexibleLauncher
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    launcher,
                    AppUpdateOptions.newBuilder(updateType).build(),
                )
            }
        }
    }

    fun completeFlexibleUpdate() {
        appUpdateManager.completeUpdate()
    }

    fun onImmediateActivityResult(resultCode: Int) {
        if (resultCode != Activity.RESULT_OK) {
            activity.finish()
        }
    }

    fun resumeFlexibleUpdateCheck() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener(activity) { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                appUpdateManager.registerListener(flexibleInstallListener)
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    flexibleLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                )
            } else if (info.installStatus() == InstallStatus.DOWNLOADED) {
                sharedViewModel.setFlexibleUpdateDownloaded(true)
            }
        }
    }

    fun unregisterListener() {
        appUpdateManager.unregisterListener(flexibleInstallListener)
    }
}