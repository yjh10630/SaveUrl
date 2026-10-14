package com.jinscompany.saveurl.data.revisit

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 알람 예약 (테스트에서 바꿔 끼울 수 있게 인터페이스) */
interface ReminderAlarms {
    fun scheduleLink(linkId: Int, at: Long)
    fun cancelLink(linkId: Int)
    fun scheduleWeekly(at: Long)
    fun cancelWeekly()
}

/**
 * AlarmManager 의 정확하지 않은 알람 (`setAndAllowWhileIdle`). 정확한 알람 권한을 쓰지 않는다 (42번 §2.4):
 * 링크 알림은 몇 분 늦어도 되고, Play 정책상 알람·캘린더 앱이 아니면 받기 어렵다.
 * WorkManager 는 깊은 Doze 에서 수십 분~시간 단위로 밀릴 수 있어 쓰지 않는다.
 * 알람 하나 = 링크 하나 (requestCode·data 를 linkId 로 구분해 서로 덮어쓰지 않게).
 */
@Singleton
class AndroidReminderAlarms @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderAlarms {
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    override fun scheduleLink(linkId: Int, at: Long) {
        runCatching { alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, linkIntent(linkId)) }
    }

    override fun cancelLink(linkId: Int) {
        runCatching { alarmManager?.cancel(linkIntent(linkId)) }
    }

    override fun scheduleWeekly(at: Long) {
        runCatching { alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, weeklyIntent()) }
    }

    override fun cancelWeekly() {
        runCatching { alarmManager?.cancel(weeklyIntent()) }
    }

    private fun linkIntent(linkId: Int): PendingIntent = PendingIntent.getBroadcast(
        context, linkId,
        Intent(context, RevisitAlarmReceiver::class.java)
            .setAction(RevisitAlarmReceiver.ACTION_LINK)
            .setData(Uri.parse("saveurl://reminder/$linkId"))
            .putExtra(RevisitNotifier.EXTRA_LINK_ID, linkId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun weeklyIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, WEEKLY_REQUEST,
        Intent(context, RevisitAlarmReceiver::class.java).setAction(RevisitAlarmReceiver.ACTION_WEEKLY),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val WEEKLY_REQUEST = -1
    }
}
