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
import kotlinx.coroutines.Dispatchers
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
        alarms.scheduleLink(linkId, ReminderSync.wakeAt(at, clock.now()))
    }

    /** 실행 취소·되돌리기용: 예전 예약 그대로 (없으면 지움) */
    suspend fun restoreReminder(linkId: Int, previous: LinkReminder?) {
        if (previous == null) cancelReminder(linkId) else mutex.withLock {
            reminderDao.upsert(previous.copy(id = 0))
            alarms.scheduleLink(linkId, ReminderSync.wakeAt(previous.remindAt, clock.now()))
        }
    }

    suspend fun cancelReminder(linkId: Int) = mutex.withLock {
        reminderDao.delete(linkId)
        alarms.cancelLink(linkId)
    }

    /** 알림의 [내일 다시]: 내일 09:00 로 새로 예약하고 알림을 지운다 */
    suspend fun snooze(linkId: Int) {
        notifier.cancelLink(linkId)
        if (urlDao.getById(linkId) == null) return
        setReminder(linkId, ReminderTimes.tomorrowMorning(clock.now(), clock.zone()), ReminderPreset.SNOOZE)
    }

    // ---- 알람 ----

    /** 링크 알람이 울림 */
    suspend fun onLinkAlarm(linkId: Int) = mutex.withLock {
        val reminder = reminderDao.getUpcoming(linkId) ?: run {
            // 링크가 지워졌거나 예약이 이미 끝났음
            reminderDao.delete(linkId)
            return@withLock
        }
        val now = clock.now()
        when (ReminderSync.onAlarm(reminder.remindAt, now)) {
            ReminderSync.FireDecision.RESCHEDULE -> alarms.scheduleLink(linkId, ReminderSync.wakeAt(reminder.remindAt, now))
            ReminderSync.FireDecision.DROP -> reminderDao.delete(linkId)
            ReminderSync.FireDecision.FIRE -> {
                reminderDao.delete(linkId)
                post(reminder, now)
            }
        }
    }

    /**
     * 모든 예약을 다시 맞춘다 (앱 시작·재부팅·앱 업데이트·시간·시간대 변경). 여러 번 불러도 같다.
     * 지난 예약은 24시간 안이면 지금 띄우고(여러 개면 묶음), 그보다 오래면 버린다. 주간 정리 예약도 다시 맞춘다.
     */
    suspend fun syncAll() = mutex.withLock {
        runCatching {
            reminderDao.getOrphanLinkIds().takeIf { it.isNotEmpty() }?.let { orphans ->
                orphans.forEach { alarms.cancelLink(it) }
                reminderDao.deleteAll(orphans)
            }
            val now = clock.now()
            val plan = ReminderSync.plan(reminderDao.getAll().map { it.linkId to it.remindAt }, now)
            plan.schedule.forEach { (linkId, at) -> alarms.scheduleLink(linkId, ReminderSync.wakeAt(at, now)) }
            if (plan.drop.isNotEmpty()) reminderDao.deleteAll(plan.drop)
            val toFire = plan.fireNow.mapNotNull { reminderDao.getUpcoming(it) }
            if (plan.fireNow.isNotEmpty()) reminderDao.deleteAll(plan.fireNow)
            toFire.forEach { post(it, now) }
            notifier.notifyLinkSummary(toFire.size)
            syncWeeklyLocked()
        }.onFailure { CrashReporter.record(it) }
    }

    private suspend fun post(reminder: UpcomingReminder, now: Long) {
        val thumb = if (reminder.locked) null else loadThumbnail(reminder.imageUrl)
        notifier.notifyLink(reminder, thumb, now)
    }

    /** 썸네일은 링크 사이트에서 직접 받는다 (기존 미리보기와 같음). 3초 안에 못 받으면 없이 */
    private suspend fun loadThumbnail(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return withTimeoutOrNull(THUMBNAIL_TIMEOUT_MS) {
            runCatching {
                val request = ImageRequest.Builder(context).data(url).size(THUMBNAIL_PX).allowHardware(false).build()
                val result = context.imageLoader.execute(request) as? SuccessResult ?: return@runCatching null
                (result.drawable as? BitmapDrawable)?.bitmap ?: result.drawable.toBitmap()
            }.getOrNull()
        }
    }

    // ---- 주간 정리 (§4) ----

    /** 사용자 스위치 + 원격 스위치가 모두 켜졌을 때만 예약한다 */
    suspend fun syncWeekly() = mutex.withLock { syncWeeklyLocked() }

    private suspend fun syncWeeklyLocked() {
        val enabled = preferences.weeklyDigestEnabled.first() && remoteFlags.weeklyDigestEnabled.value
        val now = clock.now()
        if (enabled) alarms.scheduleWeekly(ReminderSync.wakeAt(ReminderTimes.nextWeeklyDigest(now, clock.zone()), now))
        else alarms.cancelWeekly()
    }

    suspend fun onWeeklyAlarm() = mutex.withLock {
        runCatching {
            val enabled = preferences.weeklyDigestEnabled.first() && remoteFlags.weeklyDigestEnabled.value
            if (!enabled) {
                alarms.cancelWeekly()
                return@runCatching
            }
            val now = clock.now()
            val zone = clock.zone()
            val next = ReminderTimes.nextWeeklyDigest(now, zone)
            // 단계 예약으로 일찍 깼으면 다시 건다 (일요일 20:00 전)
            if (next - now > WEEKLY_DUE_TOLERANCE_MS && next - now < WEEKLY_EARLY_LIMIT_MS) {
                alarms.scheduleWeekly(ReminderSync.wakeAt(next, now))
                return@runCatching
            }
            // 일요일 20:00 이 됐거나 지났음. 너무 늦게(12시간 넘게) 깼으면 이번 주는 건너뛴다
            val due = if (next - now <= WEEKLY_DUE_TOLERANCE_MS) next else next - WEEK_MS
            if (now - due <= WEEKLY_LATE_LIMIT_MS) {
                val (saved, unread) = weeklyCounts(now, zone)
                val week = ReminderTimes.isoWeekKey(due, zone)
                if (RevisitRules.shouldSendWeekly(saved, unread, preferences.weeklyLastSentWeek.first(), week) &&
                    preferences.claimWeeklySent(week)
                ) {
                    notifier.notifyWeekly(saved, unread)
                }
            }
            // 다음 일요일 (이번 일요일 20:00 은 지났으므로 그다음)
            val following = ReminderTimes.nextWeeklyDigest(maxOf(now, due) + 2 * 60_000L, zone)
            alarms.scheduleWeekly(ReminderSync.wakeAt(following, now))
        }.onFailure { CrashReporter.record(it) }
    }

    /** s = 이번 주(월 00:00~지금) 직접 저장, u = 지금 안 읽은 링크(직접 저장). 잠금 카테고리는 세지 않는다 */
    suspend fun weeklyCounts(now: Long, zone: ZoneId): Pair<Int, Int> = withContext(Dispatchers.IO) {
        urlDao.countSavedSince(ReminderTimes.weekStart(now, zone)) to urlDao.countUnreadDirect()
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
        /** 이보다 많이 남았으면 단계 예약으로 일찍 깬 것이 아니라 일요일이 지난 것 */
        const val WEEKLY_EARLY_LIMIT_MS = 24 * 60 * 60_000L
        const val WEEKLY_LATE_LIMIT_MS = 12 * 60 * 60_000L
        const val WEEK_MS = 7 * 24 * 60 * 60_000L
        const val THUMBNAIL_TIMEOUT_MS = 3_000L
        const val THUMBNAIL_PX = 256
    }
}
