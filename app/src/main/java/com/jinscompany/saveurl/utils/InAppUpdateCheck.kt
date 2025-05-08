package com.jinscompany.saveurl.utils

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.gson.Gson
import com.jinscompany.saveurl.SaveUrlApplication
import com.jinscompany.saveurl.SharedViewModel
import com.jinscompany.saveurl.domain.model.AppInfo
import java.util.concurrent.TimeUnit

class InAppUpdateCheck(
    private val activity: ComponentActivity,
    private val updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val sharedViewModel: SharedViewModel
) {
    private val remoteConfig = Firebase.remoteConfig
    private val appUpdateManager = AppUpdateManagerFactory.create(activity)

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
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            val appInfo = Gson().fromJson(remoteConfig.getString("app_info"), AppInfo::class.java)
            val currentVersion = getCurrentAppVersion()

            val updateType = when {
                currentVersion < appInfo.minVersion -> AppUpdateType.IMMEDIATE // 강제 업데이트
                currentVersion < appInfo.latestVersion -> AppUpdateType.FLEXIBLE // 선택적 업데이트
                else -> null
            }

            if (updateType == AppUpdateType.FLEXIBLE) {
                sharedViewModel.setFlexibleUpdate(true)
            }

            if (updateType == AppUpdateType.IMMEDIATE) {
                checkUpdate()
            }
        }
    }

    private fun getCurrentAppVersion(): Int {
        val versionName = getCurrentAppVersion(activity)
        return versionName.replace(".", "").toIntOrNull() ?: 100
    }

    private fun checkUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                )
            }
        }
    }

    fun onActivityResult(resultCode: Int) {
        if (resultCode != Activity.RESULT_OK) {
            activity.finish() // 강제 업데이트 거부 시 앱 종료
        }
    }

    fun resumeFlexibleUpdateCheck() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                )
            }
        }
    }
}