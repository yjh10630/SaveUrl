package com.jinscompany.saveurl.utils

import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * 인앱 업데이트 판단 로직 (Android 의존성 없는 순수 함수 → 단위 테스트 대상).
 *
 * Play 의 `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS` 는 IMMEDIATE / FLEXIBLE 중 어느 타입으로 시작했는지 알려주지 않는다.
 * 그래서 시작 시점에 저장해 둔 타입(startedType)과 Remote Config 상의 강제 여부(isForcedByRemoteConfig)로 판단한다.
 * 둘 중 하나라도 강제(IMMEDIATE)를 가리키면 IMMEDIATE 로 재개하여 강제 업데이트를 우회할 수 없게 한다.
 */
object InAppUpdatePolicy {

    sealed interface ResumeAction {
        /** 강제 업데이트 재개 (Play 문서: DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS 이면 IMMEDIATE 로 다시 시작) */
        data object ResumeImmediate : ResumeAction
        /** 유연 업데이트 다운로드 진행 중 → 진행 상태 리스너만 다시 등록 (동의 화면을 다시 띄우지 않음) */
        data object ObserveFlexibleDownload : ResumeAction
        /** 유연 업데이트 다운로드 완료 → 설치 스낵바 표시 */
        data object ShowFlexibleDownloaded : ResumeAction
        /** 진행 중인 업데이트 없음 → 저장된 시작 타입 정리 */
        data object ClearStartedType : ResumeAction
        data object None : ResumeAction
    }

    fun decideResumeAction(
        updateAvailability: Int,
        installStatus: Int,
        startedType: Int?,
        isForcedByRemoteConfig: Boolean,
        isImmediateAllowed: Boolean,
    ): ResumeAction {
        val forced = startedType == AppUpdateType.IMMEDIATE || isForcedByRemoteConfig
        return when {
            // 강제 대상이면 다운로드 완료 여부와 관계없이 IMMEDIATE 플로우로 재개 (Play 가 설치까지 진행)
            updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS && forced && isImmediateAllowed ->
                ResumeAction.ResumeImmediate
            installStatus == InstallStatus.DOWNLOADED ->
                ResumeAction.ShowFlexibleDownloaded
            updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
                ResumeAction.ObserveFlexibleDownload
            updateAvailability == UpdateAvailability.UPDATE_NOT_AVAILABLE ||
                updateAvailability == UpdateAvailability.UPDATE_AVAILABLE ->
                if (startedType != null) ResumeAction.ClearStartedType else ResumeAction.None
            else -> ResumeAction.None
        }
    }

    /** 설정 화면 "업데이트 가능" 배지 노출 여부 (Play 기준 새 버전이 있거나 업데이트 진행 중) */
    fun isUpdateBadgeVisible(updateAvailability: Int): Boolean =
        updateAvailability == UpdateAvailability.UPDATE_AVAILABLE ||
            updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS

    /** Remote Config app_info 기준 필요한 업데이트 타입 (필요 없으면 null) */
    fun requiredUpdateType(currentVersionCode: Int, minVersion: Int, latestVersion: Int): Int? = when {
        currentVersionCode < minVersion -> AppUpdateType.IMMEDIATE
        currentVersionCode < latestVersion -> AppUpdateType.FLEXIBLE
        else -> null
    }
}
