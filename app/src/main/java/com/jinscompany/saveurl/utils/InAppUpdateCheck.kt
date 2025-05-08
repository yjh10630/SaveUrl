package com.jinscompany.saveurl.utils

import android.app.Activity.RESULT_OK
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.gson.Gson
import com.jinscompany.saveurl.SaveUrlApplication
import com.jinscompany.saveurl.SharedModelUiEffect
import com.jinscompany.saveurl.SharedViewModel
import com.jinscompany.saveurl.domain.model.AppInfo
import com.jinscompany.saveurl.domain.model.SnackBarModel

/**
 * [[ 정리 ]]
 * - 선택 업데이트의 예외 케이스를 다루기에는 많은 시간을 들여야 할 것으로 판단됨.
 * - 이번에 출시에는 인앱 업데이트를 강제 업데이트가 있을 경우에만 실행 되도록 하고, 선택 업데이트는 단순 팝업이나 설정 화면에 진입 시에만 노출 하는 것으로 진행 할 예정
 * - 그럼, 강제 업데이트 인지 선택 업데이트 인지 구분을 어떻게 하냐는 .. Firebase Remote Config 를 이용해서 Json 형태의 데이터를 내려받아 MinVersion 값을 확인 후에 선택 or 강제로 구분 짖는 방향으로 진행 예정
 */

class InAppUpdateCheck(
    private val activity: ComponentActivity,
    private val sharedViewModel: SharedViewModel,
    private val updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
) {
    private val _isChecking = mutableStateOf(true)
    val isChecking: State<Boolean> = _isChecking

    private val remoteConfig = Firebase.remoteConfig
    private val appUpdateManager = AppUpdateManagerFactory.create(activity)
    private var currentUpdateType: Int? = null

    private val listener = InstallStateUpdatedListener { status ->
        installStatusResult(status.installStatus())
    }

    init {
        fetchRemoteConfig()
    }

    private fun fetchRemoteConfig() {
        Log.e("####", "fetchRemoteConfig")
        try {
            remoteConfig.fetchAndActivate().addOnCompleteListener {
                val appInfo = Gson().fromJson(remoteConfig.getString("app_info"), AppInfo::class.java)
                val currentVersion = getCurrentAppVersion()
                Log.e("####", "currentVersion > ${currentVersion}, remoteVersion > ${appInfo.latestVersion}, remoteMinVersion > ${appInfo.minVersion}")
                val updateType = when {
                    currentVersion < appInfo.minVersion -> AppUpdateType.IMMEDIATE // 강제 업데이트
                    currentVersion < appInfo.latestVersion -> AppUpdateType.FLEXIBLE // 선택적 업데이트
                    else -> null
                }
                Log.e("####", "update Type >> ${when(updateType) {
                    AppUpdateType.IMMEDIATE -> "강제 업데이트 필요"
                    AppUpdateType.FLEXIBLE -> "일반 업데이트 필요"
                    else -> "업데이트 없음"
                }}")

                if (updateType != null) {
                    if (SaveUrlApplication.DEBUG) { //todo 디버깅 모드 에서는 in app update 진행 하지 않음
                        _isChecking.value = false
                    } else {
                        Log.e("####", "checkUpdate!! currentUpdateType > ${currentUpdateType}")
                        currentUpdateType = updateType
                        checkUpdate(updateType)
                    }
                } else {
                    _isChecking.value = false
                }
            }
        } catch (e: Exception) {
            Log.e("####", "fetchRemoteConfig error > ${e.printStackTrace()}")
            _isChecking.value = false
        }
    }

    private fun getCurrentAppVersion(): Int {
        val versionName = getCurrentAppVersion(activity)
        return versionName.replace(".", "").toIntOrNull() ?: 100
    }

    private fun checkUpdate(type: Int) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            Log.e("####", "checkUpdateListener > ${info.updateAvailability()}")
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(type)) {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(type).build(),
                )
            } else {
                _isChecking.value = false
            }
        }
    }

    fun onActivityResult(resultCode: Int) {
        if (resultCode != RESULT_OK) {
            currentUpdateType?.let {
                if (currentUpdateType == AppUpdateType.FLEXIBLE) _isChecking.value = false
                else activity.finish() // 강제 업데이트 거부 시 앱 종료
            }
        } else {
            _isChecking.value = false
        }
    }

    fun userActionCompleteUpdate() {
        appUpdateManager.completeUpdate()
    }

    fun onInstallStatusListener() {
        appUpdateManager.registerListener(listener)
    }

    fun unOnInstallStatusListener() {
        appUpdateManager.unregisterListener(listener)
    }

    /**
     * InstallStatus 설명
     * PENDING:	업데이트가 대기 중입니다. 다운로드는 아직 시작되지 않았습니다.
     * DOWNLOADING:	업데이트가 현재 다운로드 중입니다.
     * DOWNLOADED:	업데이트가 완전히 다운로드 완료되었습니다. 사용자에게 설치를 요청할 수 있습니다.
     * 📌 이때 appUpdateManager.completeUpdate()를 호출해야 설치가 시작됩니다.
     * INSTALLING:	다운로드된 APK가 설치 중입니다. 이 과정은 사용자 입력 없이 자동으로 이루어집니다.
     * INSTALLED:	업데이트가 성공적으로 설치 완료되었습니다.
     * FAILED:	업데이트가 실패했습니다. 오류로 인해 설치가 중단됨.
     * CANCELED:	사용자가 업데이트를 취소했습니다. 예: 선택적 업데이트에서 취소 버튼 누름.
     * UNKNOWN:	상태를 확인할 수 없음. 일반적으로 예외적인 상황 또는 오류 발생 시 나타납니다.
     */

    fun resumeFlexibleUpdateCheck() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            Log.e("####", "installStatus > ${info.installStatus()}")
            installStatusResult(info.installStatus())
        }
    }

    private fun installStatusResult(status: Int) {
        if (currentUpdateType != null) {
            when (status) {
                InstallStatus.FAILED,
                InstallStatus.CANCELED -> {
                    if (currentUpdateType == AppUpdateType.FLEXIBLE) {
                        _isChecking.value = false
                        sharedViewModel.notifySnackBarEvent(
                            effect = SharedModelUiEffect.ShowSnackBarInAppUpdateResult(
                                model = SnackBarModel(
                                    message = "업데이트가 취소 되었습니다. 앱을 재 실행 시 다시 노출 될 수 있어요. 다음번 실행 시 업데이트 부탁 드립니다.",
                                    actionLabel = "",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Short
                                )
                            )
                        )
                    } else {
                        activity.finish()   //todo 알럿을 노출해서 종료 하는 방향으로 ??
                    }
                }
                InstallStatus.DOWNLOADED -> {
                    if (currentUpdateType == AppUpdateType.FLEXIBLE) {
                        _isChecking.value = false
                        sharedViewModel.notifySnackBarEvent(
                            effect = SharedModelUiEffect.ShowSnackBarInAppUpdateResult(
                                model = SnackBarModel(
                                    message = "업데이트 APK 파일 다운로드를 완료했습니다. 설치 하시겠습니까?",
                                    actionLabel = "설치",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Indefinite
                                )
                            )
                        )
                    }
                }

                InstallStatus.DOWNLOADING -> {
                    if (currentUpdateType == AppUpdateType.FLEXIBLE) {
                        _isChecking.value = false
                        sharedViewModel.notifySnackBarEvent(
                            effect = SharedModelUiEffect.ShowSnackBarInAppUpdateResult(
                                model = SnackBarModel(
                                    message = "업데이트 APK 파일을 다운로드 중 입니다.",
                                    actionLabel = "",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Short
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}