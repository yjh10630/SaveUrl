package com.jinscompany.saveurl.ui.revisit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.jinscompany.saveurl.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 알림 → 앱 입구 (42번 §2.7). 내보내지 않아(exported=false) 우리 알림의 PendingIntent 로만 열린다.
 * 요청을 앱 안 [RevisitOpenInbox] 에 넣고 MainActivity 를 띄운 뒤 바로 닫힌다 (화면 없음).
 *
 * - MainActivity 는 런처·공유 때문에 내보내져 있어, 알림 action 을 직접 받으면 다른 앱이 링크를 열 수 있다 (리뷰 R7).
 * - 자기 태스크(taskAffinity="")·최근 앱 제외라, 최근 앱에서 다시 열어도 알림 요청이 다시 들어오지 않는다 (리뷰 R6).
 */
@AndroidEntryPoint
class RevisitOpenActivity : ComponentActivity() {
    @Inject
    lateinit var inbox: RevisitOpenInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) RevisitOpenRequest.from(intent)?.let(inbox::put)
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(ACTION_FROM_NOTIFICATION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        /** 묶음 요약: 요청 없이 앱만 연다 */
        const val ACTION_OPEN_APP = "com.jinscompany.saveurl.action.OPEN_APP"
        /** MainActivity 로 넘길 때 (요청은 inbox 에 있음. 이 action 만으로는 아무것도 하지 않음) */
        const val ACTION_FROM_NOTIFICATION = "com.jinscompany.saveurl.action.FROM_NOTIFICATION"
    }
}
