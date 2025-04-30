package com.jinscompany.saveurl.utils

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.gson.Gson
import com.jinscompany.saveurl.SaveUrlApplication
import com.jinscompany.saveurl.domain.model.AppInfo

class InAppUpdateCheck(
    private val activity: ComponentActivity,
) {
    private val _isChecking = mutableStateOf(true)
    val isChecking: State<Boolean> = _isChecking

    private val remoteConfig = Firebase.remoteConfig
    private val appUpdateManager = AppUpdateManagerFactory.create(activity)

    init {
        fetchRemoteConfig()
    }

    private fun fetchRemoteConfig() {
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            val appInfo = Gson().fromJson(remoteConfig.getString("app_info"), AppInfo::class.java)
            val currentVersion = getCurrentAppVersion()
            CmLog.d("currentVersion > ${currentVersion}, remoteVersion > ${appInfo.latestVersion}, remoteMinVersion > ${appInfo.minVersion}")
            val updateType = when {
                currentVersion < appInfo.minVersion -> AppUpdateType.IMMEDIATE // 강제 업데이트
                currentVersion < appInfo.latestVersion -> AppUpdateType.FLEXIBLE // 선택적 업데이트
                else -> null
            }
            CmLog.d("update Type >> ${when(updateType) {
                AppUpdateType.IMMEDIATE -> "강제 업데이트 필요"
                AppUpdateType.FLEXIBLE -> "일반 업데이트 필요"
                else -> "업데이트 없음"
            }}")
            if (updateType != null) {
                if (SaveUrlApplication.DEBUG) { //todo 디버깅 모드 에서는 in app update 진행 하지 않음
                    _isChecking.value = false
                } else {
                    checkUpdate(updateType)
                }
            } else {
                _isChecking.value = false
            }
        }
    }

    private fun getCurrentAppVersion(): Int {
        val versionName = try {
            val packageManager = activity.packageManager
            val packageName = activity.packageName
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
        return versionName.replace(".", "").toIntOrNull() ?: 100
    }

    private fun checkUpdate(type: Int) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(type)) {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    type,
                    activity,
                    REQUEST_CODE
                )
            } else {
                _isChecking.value = false
            }
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode == REQUEST_CODE && resultCode != Activity.RESULT_OK) {
            activity.finish() // 강제 업데이트 거부 시 앱 종료
        } else {
            _isChecking.value = false
        }
    }

    fun resumeFlexibleUpdateCheck() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                Snackbar.make(
                    activity.findViewById(android.R.id.content),
                    "업데이트가 완료되었습니다. 재시작해주세요.",
                    Snackbar.LENGTH_INDEFINITE
                ).setAction("재시작") {
                    appUpdateManager.completeUpdate()
                }.show()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 1001
    }
}