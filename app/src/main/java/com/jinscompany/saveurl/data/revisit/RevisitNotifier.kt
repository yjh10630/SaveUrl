package com.jinscompany.saveurl.data.revisit

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jinscompany.saveurl.MainActivity
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.model.UpcomingReminder
import com.jinscompany.saveurl.domain.revisit.ReminderTimes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 링크 알림·주간 정리 알림을 만든다 (42번 §2.5, §4.2). 내용은 모두 폰 안에서 만든다 (서버 푸시 없음).
 *
 * 잠금 카테고리 링크는 띄우는 순간 기준으로 판단해 제목·썸네일·사이트 없이 "🔒 잠금 카테고리에 저장한 링크"만 보인다.
 * 보안 잠금 화면에서는 일반 링크도 공개용 버전("SaveLink · 다시 볼 링크가 있어요")만 보인다.
 */
@Singleton
class RevisitNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager get() = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, context.getString(R.string.channel_reminder_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_reminder_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WEEKLY, context.getString(R.string.channel_weekly_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_weekly_desc) }
        )
    }

    /** 이 채널로 알림을 보낼 수 있는지 (권한·앱 알림·채널 모두 켜짐) */
    fun canPost(channelId: String): Boolean = blockedReason(channelId) == null

    /** 보낼 수 없는 이유. null = 보낼 수 있음 */
    fun blockedReason(channelId: String): NotifBlock? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return NotifBlock.PERMISSION
        if (!manager.areNotificationsEnabled()) return NotifBlock.APP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ensureChannels()
            val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(channelId)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return NotifBlock.CHANNEL
        }
        return null
    }

    /** 링크 알림 하나. [thumbnail] 은 일반 링크만 (3초 안에 받은 것) */
    fun notifyLink(reminder: UpcomingReminder, thumbnail: Bitmap?, now: Long) {
        if (!canPost(CHANNEL_REMINDER)) return
        val title = context.getString(R.string.notif_reminder_title)
        val body: String
        val second: String?
        if (reminder.locked) {
            body = context.getString(R.string.notif_reminder_locked)
            second = null
        } else {
            body = reminder.title?.takeIf { it.isNotBlank() } ?: displayHost(reminder.url)
            val site = reminder.siteName?.takeIf { it.isNotBlank() } ?: displayHost(reminder.url)
            val ago = ReminderTimes.savedAgo(reminder.addDate, now, java.time.ZoneId.systemDefault())
            second = if (ago == null) context.getString(R.string.notif_reminder_saved_today, site)
                else context.getString(R.string.notif_reminder_saved_ago, site, ago)
        }
        val public = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_blue))
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_reminder_public))
            .build()
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_blue))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (second != null) "$body\n$second" else body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setGroup(GROUP_REMINDER)
            .setContentIntent(openLinkIntent(reminder.linkId))
            .addAction(0, context.getString(R.string.notif_action_open), openLinkIntent(reminder.linkId))
            .addAction(0, context.getString(R.string.notif_action_tomorrow), snoozeIntent(reminder.linkId))
        if (!reminder.locked && thumbnail != null) builder.setLargeIcon(thumbnail)
        post(TAG_LINK, reminder.linkId, builder)
    }

    /** 여러 개를 한꺼번에 띄울 때 묶음 요약 ("다시 볼 링크 n개") */
    fun notifyLinkSummary(count: Int) {
        if (count < 2 || !canPost(CHANNEL_REMINDER)) return
        val text = context.getString(R.string.notif_reminder_group, count)
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_blue))
            .setContentTitle(text)
            .setStyle(NotificationCompat.InboxStyle().setSummaryText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup(GROUP_REMINDER)
            .setGroupSummary(true)
            .setAutoCancel(true)
        post(TAG_SUMMARY, 0, builder)
    }

    /** 주간 정리. 숫자만 (링크 제목·카테고리 이름 없음) */
    fun notifyWeekly(saved: Int, unread: Int) {
        if (!canPost(CHANNEL_WEEKLY)) return
        val body = if (saved > 0) context.getString(R.string.notif_weekly_body, saved, unread)
            else context.getString(R.string.notif_weekly_body_none_saved, unread)
        val open = PendingIntent.getActivity(
            context, REQUEST_OPEN_UNREAD,
            Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_UNREAD).addFlags(OPEN_FLAGS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_WEEKLY)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_blue))
            .setContentTitle(context.getString(R.string.notif_weekly_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.notif_weekly_action), open)
        post(TAG_WEEKLY, 0, builder)
    }

    fun cancelLink(linkId: Int) {
        manager.cancel(TAG_LINK, linkId)
    }

    private fun post(tag: String, id: Int, builder: NotificationCompat.Builder) {
        // 권한을 확인한 뒤지만 그사이 바뀔 수 있다 (API 33+ SecurityException)
        runCatching { manager.notify(tag, id, builder.build()) }
    }

    private fun openLinkIntent(linkId: Int): PendingIntent = PendingIntent.getActivity(
        context, linkId,
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_REMINDER)
            .putExtra(EXTRA_LINK_ID, linkId)
            .addFlags(OPEN_FLAGS),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun snoozeIntent(linkId: Int): PendingIntent = PendingIntent.getBroadcast(
        context, linkId,
        Intent(context, RevisitAlarmReceiver::class.java)
            .setAction(RevisitAlarmReceiver.ACTION_SNOOZE)
            .putExtra(EXTRA_LINK_ID, linkId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun displayHost(url: String?): String =
        runCatching { android.net.Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url.orEmpty()

    companion object {
        const val CHANNEL_REMINDER = "link_reminder"
        const val CHANNEL_WEEKLY = "weekly_digest"
        const val GROUP_REMINDER = "link_reminder_group"
        const val TAG_LINK = "link_reminder"
        const val TAG_SUMMARY = "link_reminder_summary"
        const val TAG_WEEKLY = "weekly_digest"

        /** 알림 → 앱: 링크 열기 / 안 읽음 탭 (MainActivity 가 onCreate·onNewIntent 에서 받는다) */
        const val ACTION_OPEN_REMINDER = "com.jinscompany.saveurl.action.OPEN_REMINDER"
        const val ACTION_OPEN_UNREAD = "com.jinscompany.saveurl.action.OPEN_UNREAD"
        const val EXTRA_LINK_ID = "linkId"
        private const val REQUEST_OPEN_UNREAD = -2
        private const val OPEN_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}

/** 알림을 보낼 수 없는 이유 (설정 열기 대상이 다르다) */
enum class NotifBlock { PERMISSION, APP, CHANNEL }
