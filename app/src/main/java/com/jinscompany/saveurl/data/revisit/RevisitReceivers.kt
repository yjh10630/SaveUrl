package com.jinscompany.saveurl.data.revisit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jinscompany.saveurl.utils.CrashReporter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** receiver 안 작업: goAsync 로 최대 [RECEIVER_BUDGET_MS] 동안 (시스템 한도 10초 안) */
private const val RECEIVER_BUDGET_MS = 8_000L
private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            withTimeoutOrNull(RECEIVER_BUDGET_MS) { block() }
        } catch (t: Throwable) {
            CrashReporter.record(t)
        } finally {
            pending.finish()
        }
    }
}

/**
 * Hilt 는 receiver 의 super.onReceive() 에서 주입한다. BroadcastReceiver.onReceive 는 abstract 라 Kotlin 에서 바로 부를 수 없어
 * 빈 구현을 한 겹 둔다 (Hilt Gradle 플러그인이 이 클래스의 부모를 생성 클래스로 바꾼다).
 */
abstract class HiltBroadcastReceiver : BroadcastReceiver() {
    @androidx.annotation.CallSuper
    override fun onReceive(context: Context, intent: Intent) = Unit
}

/** 링크 알람·주간 정리 알람·알림의 [내일 다시] (앱 안에서만 보내는 명시적 broadcast) */
@AndroidEntryPoint
class RevisitAlarmReceiver : HiltBroadcastReceiver() {
    @Inject
    lateinit var repository: RevisitRepository

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val linkId = intent.getIntExtra(RevisitNotifier.EXTRA_LINK_ID, -1)
        when (intent.action) {
            ACTION_LINK -> if (linkId >= 0) runAsync { repository.onLinkAlarm(linkId) }
            ACTION_SNOOZE -> if (linkId >= 0) runAsync { repository.snooze(linkId) }
            ACTION_WEEKLY -> runAsync { repository.onWeeklyAlarm() }
        }
    }

    companion object {
        const val ACTION_LINK = "com.jinscompany.saveurl.action.REMINDER_LINK"
        const val ACTION_SNOOZE = "com.jinscompany.saveurl.action.REMINDER_SNOOZE"
        const val ACTION_WEEKLY = "com.jinscompany.saveurl.action.REMINDER_WEEKLY"
    }
}

/**
 * 알람이 지워지거나 시각이 어긋날 수 있는 때 다시 맞춘다 (42번 §2.4):
 * 재부팅(BOOT_COMPLETED), 앱 업데이트(MY_PACKAGE_REPLACED), 시각·시간대 변경.
 * 직접 부팅(LOCKED_BOOT_COMPLETED)은 받지 않는다 (DB 가 자격 증명 보호 저장소에 있음).
 */
@AndroidEntryPoint
class RevisitRescheduleReceiver : HiltBroadcastReceiver() {
    @Inject
    lateinit var repository: RevisitRepository

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> runAsync { repository.syncAll() }
        }
    }
}
