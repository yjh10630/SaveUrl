package com.jinscompany.saveurl.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jinscompany.saveurl.MainActivity
import com.jinscompany.saveurl.domain.revisit.ReminderPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 공유 시트 → 앱(MainActivity 자기 작업)으로 넘기는 요청 (44번 §6.2) */
sealed interface AppHandoff {
    /** [자세히]·빠른 저장 꺼짐: 저장 화면. [prefill] = 시트에서 고른 값 */
    data class OpenSave(val url: String, val prefill: SavePrefill? = null) : AppHandoff
    /** [수정하기]: 저장된 링크 수정 화면 */
    data class OpenEdit(val url: String) : AppHandoff
    /** [앱에서 보기]: 메인 목록에서 그 카테고리 칩 (null = 전체) */
    data class OpenList(val category: String?) : AppHandoff
    /** 링크 가져오기 (재료는 [com.jinscompany.saveurl.ui.importer.ImportShareInbox] 에 먼저 넣는다) */
    data object Import : AppHandoff
}

/** 시트에서 고른 값을 저장 화면으로 들고 간다 ([자세히]) */
data class SavePrefill(
    val categoryName: String,
    /** 사용자가 직접 고른 카테고리 (규칙이 고른 값이면 저장 화면에서 다시 판정한다) */
    val categoryByUser: Boolean,
    val isBookMark: Boolean,
    val remindAt: Long?,
    val reminderPreset: ReminderPreset?,
)

/**
 * 공유 시트 → MainActivity 전달함.
 *
 * MainActivity 는 런처 때문에 내보내져 있어(exported) Intent extra 로 요청을 받으면 다른 앱이 흉내 낼 수 있다.
 * 그래서 42번 알림 입구(RevisitOpenActivity)와 같은 방식으로, 요청은 같은 프로세스의 이 전달함에 넣고
 * MainActivity 에는 [ACTION_HANDOFF] 만 보낸다. 다른 앱이 이 action 을 보내도 전달함이 비어 있어 아무 일도 없다.
 * 프로세스가 그 사이 죽으면 비어 있다 (매우 드묾: 시트가 닫히자마자 MainActivity 가 뜬다).
 */
@Singleton
class AppHandoffInbox @Inject constructor() {
    private val _pending = MutableStateFlow<AppHandoff?>(null)
    val pending: StateFlow<AppHandoff?> = _pending.asStateFlow()

    fun put(handoff: AppHandoff) {
        _pending.value = handoff
    }

    fun take(): AppHandoff? = _pending.value.also { _pending.value = null }

    companion object {
        const val ACTION_HANDOFF = "com.jinscompany.saveurl.action.HANDOFF"

        /**
         * MainActivity 를 자기 작업에서 띄운다 (떠 있으면 앞으로 가져와 onNewIntent).
         * [files] = 공유로 받은 파일. 받은 Activity(공유 시트)가 닫히면 읽기 권한이 끊길 수 있어
         * ClipData + FLAG_GRANT_READ_URI_PERMISSION 으로 MainActivity 에 권한을 다시 넘긴다 (44번 §7).
         */
        fun startMain(context: Context, files: List<Uri> = emptyList()) {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(ACTION_HANDOFF)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (files.isNotEmpty()) {
                val clip = ClipData.newRawUri("", files.first())
                files.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                intent.clipData = clip
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        }
    }
}

/** [자세히] 로 넘긴 값 → 저장 화면 ViewModel 이 처음 열릴 때 한 번 가져간다 (URL 이 같을 때만) */
@Singleton
class SavePrefillStore @Inject constructor() {
    private var pending: Pair<String, SavePrefill>? = null

    fun put(url: String, prefill: SavePrefill) {
        pending = url to prefill
    }

    fun take(url: String?): SavePrefill? {
        val (storedUrl, prefill) = pending ?: return null
        pending = null
        return prefill.takeIf { storedUrl == url }
    }
}
