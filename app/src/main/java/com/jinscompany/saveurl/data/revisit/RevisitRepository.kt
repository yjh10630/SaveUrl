package com.jinscompany.saveurl.data.revisit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.jinscompany.saveurl.data.room.BaseSaveUrlDao
import com.jinscompany.saveurl.data.room.LinkReminderDao
import com.jinscompany.saveurl.domain.model.LinkReminder
import com.jinscompany.saveurl.domain.model.UpcomingReminder
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.revisit.ReminderPreset
import com.jinscompany.saveurl.domain.revisit.ReminderSync
import com.jinscompany.saveurl.domain.revisit.ReminderTimes
import com.jinscompany.saveurl.domain.revisit.RevisitRules
import com.jinscompany.saveurl.utils.CrashReporter
import com.jinscompany.saveurl.utils.PreferencesManager
import com.jinscompany.saveurl.utils.RemoteFlags
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** 지금 시각·시간대 (테스트에서 고정) */
interface RevisitClock {
    fun now(): Long
    fun zone(): ZoneId
}

class SystemRevisitClock @Inject constructor() : RevisitClock {
    override fun now(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/**
 * 다시 보게 만들기 (42번): 링크 알림 예약·발송, 주간 정리, 다시 볼 링크 카드 후보.
 * 알람·알림은 폰 안에서만 예약한다. 예약과 발송이 겹치지 않도록 [mutex] 로 한 번에 하나씩 처리한다.
 */
@Singleton
class RevisitRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderDao: LinkReminderDao,
    private val urlDao: BaseSaveUrlDao,
    private val alarms: ReminderAlarms,
    private val notifier: RevisitNotifier,
    private val preferences: PreferencesManager,
    private val remoteFlags: RemoteFlags,
    private val clock: RevisitClock,
) {
    private val mutex = Mutex()

    /** 목록 ⏰ 표시: linkId → remindAt */
    fun observeReminderTimes(): Flow<Map<Int, Long>> =
        reminderDao.observeAll().map { list -> list.associate { it.linkId to it.remindAt } }

    fun observeUpcoming(): Flow<List<UpcomingReminder>> = reminderDao.observeUpcoming()

    /** 안 읽음 탭 숫자 ("전체"처럼 잠금 카테고리는 세지 않음) */
    fun observeUnreadCount(): Flow<Int> = urlDao.observeUnreadCount()

    /** 링크 메뉴 "읽음/안 읽음으로 표시" */
    suspend fun setRead(linkId: Int, read: Boolean) = withContext(Dispatchers.IO) { urlDao.setRead(linkId, read) }

    /** 주간 정리 제안 조건: 직접 저장한 링크 수 (잠금·가져온 링크 제외) */
    suspend fun countDirectLinks(): Int = withContext(Dispatchers.IO) { urlDao.countDirect() }

    /** 방금 저장한 링크 id (같은 주소를 두 번 저장했으면 나중 것) */
    suspend fun latestLinkIdForUrl(url: String): Int? = withContext(Dispatchers.IO) { urlDao.getLatestIdByUrl(url) }

    suspend fun getReminder(linkId: Int): LinkReminder? = reminderDao.get(linkId)

    suspend fun setReminder(linkId: Int, at: Long, preset: ReminderPreset) = mutex.withLock {
        reminderDao.upsert(LinkReminder(linkId = linkId, remindAt = at, createdAt = clock.now(), preset = preset.key))
        rearmLocked()
    }

    /** 실행 취소·되돌리기용: 예전 예약 그대로 (없으면 지움) */
    suspend fun restoreReminder(linkId: Int, previous: LinkReminder?) {
        if (previous == null) cancelReminder(linkId) else mutex.withLock {
            reminderDao.upsert(previous.copy(id = 0))
            rearmLocked()
        }
    }

    suspend fun cancelReminder(linkId: Int) = mutex.withLock {
        reminderDao.delete(linkId)
        rearmLocked()
    }

    /**
     * 알림의 [내일 다시]: 내일 09:00 로 새로 예약하고 알림을 지운다.
     * 알림이 뜬 뒤 앱에서 그 링크를 이미 다시 예약했으면(행이 있음) 그 예약을 덮어쓰지 않고 알림만 지운다.
     */
    suspend fun snooze(linkId: Int) {
        notifier.cancelLink(linkId)
        if (urlDao.getById(linkId) == null) return
        if (reminderDao.get(linkId) != null) return
        setReminder(linkId, ReminderTimes.tomorrowMorning(clock.now(), clock.zone()), ReminderPreset.SNOOZE)
    }

    // ---- 알람 ----

    /** 예약 알람이 울림: 기한이 된 링크 알림·주간 정리를 처리하고 다음 시각으로 다시 건다 */
    suspend fun onTick() = mutex.withLock { processLocked() }

    /**
     * 모든 예약을 다시 맞춘다 (앱 시작·재부팅·앱 업데이트·시간·시간대 변경). 여러 번 불러도 같다.
     * 알람은 하나만 다시 걸므로 앱을 켤 때마다 불러도 가볍다.
     */
    suspend fun syncAll() = mutex.withLock { processLocked() }

    /** 주간 정리 스위치(사용자·원격)가 바뀌었을 때 */
    suspend fun syncWeekly() = mutex.withLock { catchingNonCancel { rearmLocked() } }

    /**
     * 한 번의 처리. 띄운 뒤에 행을 지운다 (중간에 프로세스가 죽어도 다음 처리 때 다시 띄움, mutex 가 중복을 막음).
     * 썸네일은 하나만 띄울 때만, 짧게 받는다 (재부팅 직후 여러 개를 하나하나 받느라 receiver 시간을 다 쓰지 않게).
     */
    private suspend fun processLocked() = catchingNonCancel {
        val orphans = reminderDao.getOrphanLinkIds()
        if (orphans.isNotEmpty()) reminderDao.deleteAll(orphans)
        val now = clock.now()
        val plan = ReminderSync.plan(reminderDao.getAll().map { it.linkId to it.remindAt }, now)
        if (plan.drop.isNotEmpty()) reminderDao.deleteAll(plan.drop)
        val toFire = plan.fireNow.mapNotNull { reminderDao.getUpcoming(it) }
        val single = toFire.singleOrNull()
        val thumb = if (single != null && !single.locked) loadThumbnail(single.imageUrl) else null
        plan.fireNow.forEach { linkId ->
            toFire.firstOrNull { it.linkId == linkId }?.let { notifier.notifyLink(it, thumb, now) }
            reminderDao.delete(linkId)
        }
        if (toFire.isNotEmpty()) notifier.updateSummary()
        processWeeklyLocked(now)
        rearmLocked()
    }

    /** 다음 알람: 남은 링크 예약 중 가장 이른 것과 다음 주간 정리 중 먼저 오는 것 하나 */
    private suspend fun rearmLocked() {
        val now = clock.now()
        val nextLink = reminderDao.getAll().minOfOrNull { it.remindAt }?.coerceAtLeast(now)
        val nextWeekly = if (weeklyEnabled()) ReminderTimes.nextWeeklyDigest(now + WEEKLY_DUE_TOLERANCE_MS, clock.zone()) else null
        alarms.arm(ReminderSync.nextWake(nextLink, nextWeekly), now)
    }

    /** 썸네일은 링크 사이트에서 직접 받는다 (기존 미리보기와 같음). 짧게 기다리고 못 받으면 없이 */
    private suspend fun loadThumbnail(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return withTimeoutOrNull(THUMBNAIL_TIMEOUT_MS) {
            try {
                val request = ImageRequest.Builder(context).data(url).size(THUMBNAIL_PX).allowHardware(false).build()
                val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withTimeoutOrNull null
                (result.drawable as? BitmapDrawable)?.bitmap ?: result.drawable.toBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    // ---- 이미 뜬 알림 정리 (잠금·이동·삭제 뒤) ----

    /**
     * 떠 있는 링크 알림 중 링크가 지워졌거나, 일반 문구로 띄운 뒤 잠금 카테고리가 된 것을 지운다 (37번: 닫힌 세션 밖에 잠금 링크 제목이 남지 않게).
     * 링크가 지워진 예약 행도 정리하고 알람을 다시 건다 (휴지통·영구 삭제·가져오기 되돌리기).
     * [startNotificationGuard] 가 링크·카테고리 표가 바뀔 때마다 부른다.
     */
    suspend fun refreshShownNotifications() = mutex.withLock {
        catchingNonCancel {
            notifier.shownLinks().forEach { (linkId, postedLocked) ->
                val gone = urlDao.getById(linkId) == null
                val nowLocked = !gone && urlDao.isLocked(linkId) > 0
                if (gone || (nowLocked && !postedLocked)) notifier.cancelLink(linkId)
            }
            val orphans = reminderDao.getOrphanLinkIds()
            if (orphans.isNotEmpty()) {
                reminderDao.deleteAll(orphans)
                rearmLocked()
            }
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun startNotificationGuard(scope: CoroutineScope) {
        scope.launch {
            urlDao.observeLinkOrLockChanges().drop(1).debounce(GUARD_DEBOUNCE_MS).collect { refreshShownNotifications() }
        }
    }

    // ---- 주간 정리 (§4) ----

    private suspend fun weeklyEnabled(): Boolean =
        preferences.weeklyDigestEnabled.first() && remoteFlags.weeklyDigestEnabled.value

    /**
     * 가장 최근 일요일 20:00(이번 처리 시각 기준)이 12시간 안이면 그 주 정리를 한 번 보낸다.
     * 숫자는 울린 시각이 아니라 그 일요일 기준으로 센다 (월요일 새벽에 늦게 울려도 지난주 숫자).
     */
    private suspend fun processWeeklyLocked(now: Long) {
        if (!weeklyEnabled()) return
        val zone = clock.zone()
        val next = ReminderTimes.nextWeeklyDigest(now, zone)
        val due = if (next - now <= WEEKLY_DUE_TOLERANCE_MS) next else next - WEEK_MS
        if (now - due > WEEKLY_LATE_LIMIT_MS || due - now > WEEKLY_DUE_TOLERANCE_MS) return
        val week = ReminderTimes.isoWeekKey(due, zone)
        if (preferences.weeklyLastSentWeek.first() == week) return
        val (saved, unread) = weeklyCounts(due, zone)
        if (RevisitRules.shouldSendWeekly(saved, unread, preferences.weeklyLastSentWeek.first(), week) &&
            preferences.claimWeeklySent(week)
        ) {
            notifier.notifyWeekly(saved, unread)
        }
    }

    /** s = 그 주(월 00:00~일 20:00) 직접 저장, u = 지금 안 읽은 링크(직접 저장). 잠금 카테고리는 세지 않는다 */
    suspend fun weeklyCounts(due: Long, zone: ZoneId): Pair<Int, Int> = withContext(Dispatchers.IO) {
        urlDao.countSavedBetween(ReminderTimes.weekStart(due, zone), due) to urlDao.countUnreadDirect()
    }

    /** CancellationException 은 다시 던지고 나머지는 기록만 (receiver 시간 초과가 오류로 남지 않게) */
    private inline fun catchingNonCancel(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CrashReporter.record(e)
        }
    }

    // ---- 다시 볼 링크 카드 (§5) ----

    /**
     * 오늘의 카드 링크. 같은 날엔 같은 링크이고, 그 링크가 읽음이 되거나 지워지면 그날은 null (다른 걸 또 꺼내지 않음).
     */
    suspend fun todaysRevisitLink(): UrlData? = withContext(Dispatchers.IO) {
        val now = clock.now()
        val today = ReminderTimes.epochDay(now, clock.zone())
        RevisitRules.decodePick(preferences.revisitCardPick.first(), today)?.let { pickedId ->
            val link = urlDao.getById(pickedId) ?: return@withContext null
            val lockedNow = urlDao.isLocked(pickedId) > 0
            val reminded = reminderDao.get(pickedId) != null
            return@withContext link.takeIf { !it.isRead && !lockedNow && !reminded }
        }
        if (urlDao.countNotLocked() < RevisitRules.MIN_LINKS_FOR_CARD) return@withContext null
        val minAge = remoteFlags.revisitMinAgeDays * 24L * 60 * 60_000L
        val candidates = urlDao.getRevisitCandidates(
            maxAddDate = now - minAge,
            shownBefore = now - RevisitRules.SHOWN_COOLDOWN_MS,
            limit = RevisitRules.CANDIDATE_LIMIT,
        )
        val pickedId = RevisitRules.pick(candidates.map { it.id }, today) ?: return@withContext null
        preferences.setRevisitCardPick(RevisitRules.encodePick(today, pickedId))
        urlDao.setRevisitShownAt(pickedId, now)
        candidates.firstOrNull { it.id == pickedId }
    }

    private companion object {
        const val WEEKLY_DUE_TOLERANCE_MS = 60_000L
        const val GUARD_DEBOUNCE_MS = 500L
        const val WEEKLY_LATE_LIMIT_MS = 12 * 60 * 60_000L
        const val WEEK_MS = 7 * 24 * 60 * 60_000L
        const val THUMBNAIL_TIMEOUT_MS = 2_000L
        const val THUMBNAIL_PX = 256
    }
}
