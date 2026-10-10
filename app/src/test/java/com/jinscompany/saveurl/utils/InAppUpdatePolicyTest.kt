package com.jinscompany.saveurl.utils

import android.app.Activity
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.jinscompany.saveurl.utils.InAppUpdatePolicy.CheckAction
import com.jinscompany.saveurl.utils.InAppUpdatePolicy.FlexibleDecline
import com.jinscompany.saveurl.utils.InAppUpdatePolicy.ForceUpdateBlockReason
import com.jinscompany.saveurl.utils.InAppUpdatePolicy.ResumeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppUpdatePolicyTest {

    private fun decide(
        availability: Int,
        status: Int = InstallStatus.UNKNOWN,
        started: Int? = null,
        forcedByRc: Boolean = false,
        immediateAllowed: Boolean = true,
    ) = InAppUpdatePolicy.decideResumeAction(availability, status, started, forcedByRc, immediateAllowed)

    private val inProgress = UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS

    @Test
    fun `IMMEDIATE 로 시작한 업데이트는 IMMEDIATE 로 재개`() {
        assertEquals(ResumeAction.ResumeImmediate, decide(inProgress, InstallStatus.DOWNLOADING, started = AppUpdateType.IMMEDIATE))
    }

    @Test
    fun `IMMEDIATE 로 시작했으면 다운로드 완료 상태여도 강제 플로우로 재개`() {
        assertEquals(ResumeAction.ResumeImmediate, decide(inProgress, InstallStatus.DOWNLOADED, started = AppUpdateType.IMMEDIATE))
    }

    @Test
    fun `시작 타입 기록이 없어도 Remote Config 상 강제 대상이면 IMMEDIATE 로 재개`() {
        assertEquals(ResumeAction.ResumeImmediate, decide(inProgress, InstallStatus.DOWNLOADING, started = null, forcedByRc = true))
        // FLEXIBLE 로 시작했더라도 그 사이 min_version 이 올라가 강제 대상이 되었으면 강제로 전환
        assertEquals(ResumeAction.ResumeImmediate, decide(inProgress, InstallStatus.DOWNLOADING, started = AppUpdateType.FLEXIBLE, forcedByRc = true))
    }

    @Test
    fun `FLEXIBLE 진행 중이면 동의 화면을 다시 띄우지 않고 리스너만 등록`() {
        assertEquals(ResumeAction.ObserveFlexibleDownload, decide(inProgress, InstallStatus.DOWNLOADING, started = AppUpdateType.FLEXIBLE))
        assertEquals(ResumeAction.ObserveFlexibleDownload, decide(inProgress, InstallStatus.PENDING, started = null))
    }

    @Test
    fun `FLEXIBLE 다운로드 완료면 설치 안내`() {
        assertEquals(ResumeAction.ShowFlexibleDownloaded, decide(inProgress, InstallStatus.DOWNLOADED, started = AppUpdateType.FLEXIBLE))
        assertEquals(ResumeAction.ShowFlexibleDownloaded, decide(UpdateAvailability.UPDATE_AVAILABLE, InstallStatus.DOWNLOADED))
    }

    @Test
    fun `IMMEDIATE 가 허용되지 않으면 FLEXIBLE 처리로 폴백`() {
        assertEquals(
            ResumeAction.ObserveFlexibleDownload,
            decide(inProgress, InstallStatus.DOWNLOADING, started = AppUpdateType.IMMEDIATE, immediateAllowed = false)
        )
    }

    @Test
    fun `진행 중인 업데이트가 없으면 시작 타입 기록 정리`() {
        assertEquals(ResumeAction.ClearStartedType, decide(UpdateAvailability.UPDATE_NOT_AVAILABLE, started = AppUpdateType.IMMEDIATE))
        assertEquals(ResumeAction.ClearStartedType, decide(UpdateAvailability.UPDATE_AVAILABLE, started = AppUpdateType.FLEXIBLE))
        assertEquals(ResumeAction.None, decide(UpdateAvailability.UPDATE_NOT_AVAILABLE, started = null))
        assertEquals(ResumeAction.None, decide(UpdateAvailability.UNKNOWN, started = AppUpdateType.IMMEDIATE))
    }

    @Test
    fun `업데이트 가능 배지는 Play 에 새 버전이 있거나 진행 중일 때만 노출`() {
        assertTrue(InAppUpdatePolicy.isUpdateBadgeVisible(UpdateAvailability.UPDATE_AVAILABLE))
        assertTrue(InAppUpdatePolicy.isUpdateBadgeVisible(inProgress))
        assertFalse(InAppUpdatePolicy.isUpdateBadgeVisible(UpdateAvailability.UPDATE_NOT_AVAILABLE))
        assertFalse(InAppUpdatePolicy.isUpdateBadgeVisible(UpdateAvailability.UNKNOWN))
    }

    @Test
    fun `Remote Config 버전 비교`() {
        assertEquals(AppUpdateType.IMMEDIATE, InAppUpdatePolicy.requiredUpdateType(38, minVersion = 39, latestVersion = 40))
        assertEquals(AppUpdateType.FLEXIBLE, InAppUpdatePolicy.requiredUpdateType(38, minVersion = 30, latestVersion = 39))
        assertNull(InAppUpdatePolicy.requiredUpdateType(38, minVersion = 38, latestVersion = 38))
    }

    // ---- FLEXIBLE 스누즈 ----

    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `거부한 같은 버전은 3일 동안 다시 묻지 않음`() {
        val decline = FlexibleDecline(versionCode = 39, declinedAtMillis = 1_000L)
        assertTrue(InAppUpdatePolicy.isFlexibleSnoozed(decline, 39, nowMillis = 1_000L))
        assertTrue(InAppUpdatePolicy.isFlexibleSnoozed(decline, 39, nowMillis = 1_000L + 3 * day - 1))
        assertFalse(InAppUpdatePolicy.isFlexibleSnoozed(decline, 39, nowMillis = 1_000L + 3 * day))
    }

    @Test
    fun `더 새 버전이 올라오거나 기록이 없으면 스누즈하지 않음`() {
        val decline = FlexibleDecline(versionCode = 39, declinedAtMillis = 1_000L)
        assertFalse(InAppUpdatePolicy.isFlexibleSnoozed(decline, 40, nowMillis = 2_000L))
        assertFalse(InAppUpdatePolicy.isFlexibleSnoozed(null, 39, nowMillis = 2_000L))
    }

    @Test
    fun `기기 시간이 거꾸로 가면 스누즈를 풀어 둠`() {
        val decline = FlexibleDecline(versionCode = 39, declinedAtMillis = 10 * day)
        assertFalse(InAppUpdatePolicy.isFlexibleSnoozed(decline, 39, nowMillis = day))
    }

    // ---- RC + Play 상태 → 할 일 ----

    private fun check(
        required: Int?,
        availability: Int?,
        immediateAllowed: Boolean = true,
        flexibleAllowed: Boolean = true,
        snoozed: Boolean = false,
    ) = InAppUpdatePolicy.decideCheckAction(required, availability, immediateAllowed, flexibleAllowed, snoozed)

    @Test
    fun `강제 대상이고 Play 에 업데이트가 있으면 IMMEDIATE 시작, 진행 중이면 IMMEDIATE 로 재개`() {
        assertEquals(CheckAction.StartImmediate, check(AppUpdateType.IMMEDIATE, UpdateAvailability.UPDATE_AVAILABLE))
        assertEquals(CheckAction.StartImmediate, check(AppUpdateType.IMMEDIATE, inProgress))
    }

    @Test
    fun `강제는 스누즈의 영향을 받지 않음`() {
        assertEquals(CheckAction.StartImmediate, check(AppUpdateType.IMMEDIATE, UpdateAvailability.UPDATE_AVAILABLE, snoozed = true))
    }

    @Test
    fun `강제 대상인데 Play 가 업데이트를 주지 않으면 차단 화면`() {
        // 단계적 출시 제외, Play 캐시
        assertEquals(CheckAction.ShowBlocker, check(AppUpdateType.IMMEDIATE, UpdateAvailability.UPDATE_NOT_AVAILABLE))
        assertEquals(CheckAction.ShowBlocker, check(AppUpdateType.IMMEDIATE, UpdateAvailability.UNKNOWN))
        // appUpdateInfo 실패 (사이드로드, Play 스토어 없음, 다른 계정)
        assertEquals(CheckAction.ShowBlocker, check(AppUpdateType.IMMEDIATE, null))
        // IMMEDIATE 미허용
        assertEquals(CheckAction.ShowBlocker, check(AppUpdateType.IMMEDIATE, UpdateAvailability.UPDATE_AVAILABLE, immediateAllowed = false))
    }

    @Test
    fun `권장 업데이트는 Play 에 있고 허용되고 스누즈 중이 아닐 때만 동의 창`() {
        assertEquals(CheckAction.StartFlexible, check(AppUpdateType.FLEXIBLE, UpdateAvailability.UPDATE_AVAILABLE))
        assertEquals(CheckAction.None, check(AppUpdateType.FLEXIBLE, UpdateAvailability.UPDATE_AVAILABLE, snoozed = true))
        assertEquals(CheckAction.None, check(AppUpdateType.FLEXIBLE, UpdateAvailability.UPDATE_AVAILABLE, flexibleAllowed = false))
        // 진행 중이면 재개 경로(decideResumeAction)가 맡으므로 동의 창을 다시 띄우지 않음
        assertEquals(CheckAction.None, check(AppUpdateType.FLEXIBLE, inProgress))
        // Play 가 주지 않으면 막지 않음
        assertEquals(CheckAction.None, check(AppUpdateType.FLEXIBLE, UpdateAvailability.UPDATE_NOT_AVAILABLE))
        assertEquals(CheckAction.None, check(AppUpdateType.FLEXIBLE, null))
    }

    @Test
    fun `업데이트가 필요 없으면 아무것도 하지 않음`() {
        assertEquals(CheckAction.None, check(null, UpdateAvailability.UPDATE_AVAILABLE))
        assertEquals(CheckAction.None, check(null, null))
    }

    // ---- IMMEDIATE 결과 ----

    @Test
    fun `IMMEDIATE 결과에 따라 차단 이유 구분`() {
        assertNull(InAppUpdatePolicy.immediateResultBlockReason(Activity.RESULT_OK))
        assertEquals(ForceUpdateBlockReason.CANCELED, InAppUpdatePolicy.immediateResultBlockReason(Activity.RESULT_CANCELED))
        assertEquals(ForceUpdateBlockReason.FAILED, InAppUpdatePolicy.immediateResultBlockReason(ActivityResult.RESULT_IN_APP_UPDATE_FAILED))
    }

    // ---- 포그라운드 재확인 ----

    private val interval = InAppUpdatePolicy.FOREGROUND_RECHECK_INTERVAL_MILLIS

    private fun recheck(
        blocker: Boolean = false,
        forced: Boolean = false,
        recreated: Boolean = false,
        last: Long? = null,
        now: Long = 100 * interval,
    ) = InAppUpdatePolicy.shouldRecheckOnResume(blocker, forced, recreated, last, now)

    @Test
    fun `첫 확인은 바로 하고 이후에는 간격마다`() {
        assertTrue(recheck(last = null))
        assertFalse(recheck(last = 100 * interval - 1, now = 100 * interval))
        assertTrue(recheck(last = 99 * interval, now = 100 * interval))
    }

    @Test
    fun `Activity 재생성 직후에는 다시 확인하지 않음`() {
        assertFalse(recheck(recreated = true, last = null))
        assertFalse(recheck(recreated = true, last = 0L))
    }

    @Test
    fun `강제 대상이면 재생성이나 간격과 관계없이 매번 확인`() {
        assertTrue(recheck(forced = true, recreated = true, last = 100 * interval - 1))
    }

    @Test
    fun `차단 화면이 떠 있으면 자동 확인하지 않음`() {
        assertFalse(recheck(blocker = true, forced = true))
        assertFalse(recheck(blocker = true, last = null))
    }
}
