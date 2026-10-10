package com.jinscompany.saveurl.utils

import android.app.Activity
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import java.util.concurrent.TimeUnit

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

    /** FLEXIBLE 동의 창에서 "나중에" 를 누른 버전은 이 기간 동안 다시 묻지 않는다 (강제 업데이트는 해당 없음) */
    val FLEXIBLE_SNOOZE_MILLIS: Long = TimeUnit.DAYS.toMillis(3)

    /** 포그라운드 복귀 때 Remote Config·Play 를 다시 확인하는 최소 간격 (강제 대상이면 간격과 관계없이 매번 확인) */
    val FOREGROUND_RECHECK_INTERVAL_MILLIS: Long = TimeUnit.MINUTES.toMillis(15)

    /** FLEXIBLE 동의 창을 거부한 기록 (versionCode = 그때 Play 가 제시한 availableVersionCode) */
    data class FlexibleDecline(val versionCode: Int, val declinedAtMillis: Long)

    /** 강제 업데이트 차단 화면을 띄운 이유 */
    enum class ForceUpdateBlockReason {
        /** 사용자가 IMMEDIATE 화면을 닫음(뒤로 가기, X) */
        CANCELED,
        /** IMMEDIATE 설치 실패(저장 공간, 네트워크 등. RESULT_IN_APP_UPDATE_FAILED) */
        FAILED,
        /** Remote Config 상 강제 대상인데 Play 가 업데이트를 주지 않음(단계적 출시, Play 캐시, 사이드로드, Play 오류) */
        NOT_OFFERED,
    }

    sealed interface CheckAction {
        data object StartImmediate : CheckAction
        data object StartFlexible : CheckAction
        /** 강제 대상인데 Play 로 진행할 수 없음 → 닫을 수 없는 안내 화면 */
        data object ShowBlocker : CheckAction
        data object None : CheckAction
    }

    /**
     * 같은 버전을 [FLEXIBLE_SNOOZE_MILLIS] 안에 거부했으면 true.
     * 더 새 버전이 올라오면 다시 묻는다. 기기 시간이 거꾸로 가 경과 시간이 음수면 스누즈를 풀어 둔다.
     */
    fun isFlexibleSnoozed(decline: FlexibleDecline?, availableVersionCode: Int, nowMillis: Long): Boolean {
        if (decline == null || decline.versionCode != availableVersionCode) return false
        val elapsed = nowMillis - decline.declinedAtMillis
        return elapsed in 0 until FLEXIBLE_SNOOZE_MILLIS
    }

    /**
     * Remote Config 로 정한 업데이트 타입과 Play 상태로 할 일을 고른다.
     * @param updateAvailability Play 의 updateAvailability. appUpdateInfo 요청이 실패했으면 null
     */
    fun decideCheckAction(
        requiredType: Int?,
        updateAvailability: Int?,
        isImmediateAllowed: Boolean,
        isFlexibleAllowed: Boolean,
        isFlexibleSnoozed: Boolean,
    ): CheckAction = when (requiredType) {
        AppUpdateType.IMMEDIATE -> {
            val playCanUpdate = updateAvailability == UpdateAvailability.UPDATE_AVAILABLE ||
                // Play 문서: 진행 중인 업데이트는 IMMEDIATE 로 다시 시작해 이어간다
                updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            // 강제는 스누즈하지 않는다. Play 가 진행할 수 없으면(미제공/미허용/요청 실패) 우회하지 못하게 차단 화면
            if (playCanUpdate && isImmediateAllowed) CheckAction.StartImmediate else CheckAction.ShowBlocker
        }
        AppUpdateType.FLEXIBLE ->
            if (updateAvailability == UpdateAvailability.UPDATE_AVAILABLE && isFlexibleAllowed && !isFlexibleSnoozed) {
                CheckAction.StartFlexible
            } else {
                CheckAction.None
            }
        else -> CheckAction.None
    }

    /** IMMEDIATE 플로우 결과 → 차단 화면 이유 (RESULT_OK 면 Play 가 설치 후 앱을 다시 시작하므로 null) */
    fun immediateResultBlockReason(resultCode: Int): ForceUpdateBlockReason? = when (resultCode) {
        Activity.RESULT_OK -> null
        ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> ForceUpdateBlockReason.FAILED
        else -> ForceUpdateBlockReason.CANCELED
    }

    /**
     * onResume 에서 Remote Config 를 다시 평가할지.
     * - 차단 화면이 떠 있으면 화면의 버튼(다시 시도/다시 확인)이 확인을 맡으므로 자동 확인은 하지 않는다.
     * - 강제 대상(마지막으로 활성화된 값 기준)이면 매번 확인해 우회를 막는다.
     * - 그 외에는 Activity 재생성 직후(회전, 폴드, 창 크기·언어 변경)는 건너뛰고, [FOREGROUND_RECHECK_INTERVAL_MILLIS] 간격으로만 확인한다.
     * @param lastCheckElapsedMillis 이번 프로세스에서 마지막으로 확인한 시각(elapsedRealtime). 없으면 null
     */
    fun shouldRecheckOnResume(
        isBlockerVisible: Boolean,
        isForcedByRemoteConfig: Boolean,
        isFirstResumeAfterRecreation: Boolean,
        lastCheckElapsedMillis: Long?,
        nowElapsedMillis: Long,
    ): Boolean = when {
        isBlockerVisible -> false
        isForcedByRemoteConfig -> true
        isFirstResumeAfterRecreation -> false
        lastCheckElapsedMillis == null -> true
        else -> nowElapsedMillis - lastCheckElapsedMillis >= FOREGROUND_RECHECK_INTERVAL_MILLIS
    }
}
