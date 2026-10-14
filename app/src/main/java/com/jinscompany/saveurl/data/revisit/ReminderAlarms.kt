package com.jinscompany.saveurl.data.revisit

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jinscompany.saveurl.domain.revisit.ReminderSync
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 알람 예약 (테스트에서 바꿔 끼울 수 있게 인터페이스) */
interface ReminderAlarms {
    /** 다음에 처리할 시각 하나에만 알람을 건다. null 이면 취소 */
    fun arm(at: Long?, now: Long)
}

/**
 * AlarmManager 의 정확하지 않은 알람 두 개 (42번 §2.4). 정확한 알람 권한은 쓰지 않는다 (알람·캘린더 앱이 아니면 Play 정책상 받기 어려움).
 * - 기본: `setWindow(목표, 10분)` → 화면이 켜져 있거나 가벼운 Doze 면 목표 ~ 목표+10분 안에 울림 (API 와 관계없이 창이 정해짐)
 * - Doze 대비: `setAndAllowWhileIdle` → 깊은 Doze 에서도 울림. 시각은 [ReminderSync.idleWakeAt] (Android 11 이하는 앞당겨 단계로)
 * 두 알람은 같은 처리(가장 이른 예약 + 주간 정리)를 부르고, 먼저 울린 쪽이 처리 뒤 둘 다 다시 건다. 늦게 울린 쪽은 할 일이 없으면 다시 걸기만 한다.
 * WorkManager 는 깊은 Doze 에서 수십 분~시간 단위로 밀릴 수 있어 쓰지 않는다.
 */
@Singleton
class AndroidReminderAlarms @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderAlarms {
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    override fun arm(at: Long?, now: Long) {
        val am = alarmManager ?: return
        if (at == null) {
            existing(REQUEST_WINDOW)?.let { am.cancel(it); it.cancel() }
            existing(REQUEST_IDLE)?.let { am.cancel(it); it.cancel() }
            return
        }
        runCatching { am.setWindow(AlarmManager.RTC_WAKEUP, at, ReminderSync.WINDOW_MS, tick(REQUEST_WINDOW)) }
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ReminderSync.idleWakeAt(at, now, Build.VERSION.SDK_INT), tick(REQUEST_IDLE))
        }
    }

    private fun tickIntent() = Intent(context, RevisitAlarmReceiver::class.java).setAction(RevisitAlarmReceiver.ACTION_TICK)

    private fun tick(request: Int): PendingIntent =
        PendingIntent.getBroadcast(context, request, tickIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** 이미 만든 것만 찾는다 (취소하려고 새로 만들지 않게) */
    private fun existing(request: Int): PendingIntent? =
        PendingIntent.getBroadcast(context, request, tickIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)

    private companion object {
        const val REQUEST_WINDOW = 1
        const val REQUEST_IDLE = 2
    }
}
