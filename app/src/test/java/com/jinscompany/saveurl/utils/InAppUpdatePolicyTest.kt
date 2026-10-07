package com.jinscompany.saveurl.utils

import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
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
}
