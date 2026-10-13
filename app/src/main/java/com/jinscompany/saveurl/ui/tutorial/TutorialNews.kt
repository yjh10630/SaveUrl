package com.jinscompany.saveurl.ui.tutorial

/**
 * 튜토리얼 "NEW" 표시 판단에 쓰는 저장 값 (DataStore).
 *
 * @param viewedIds 본 주제 id
 * @param baselineVersionCode 사용자의 기준 versionCode (`tutorial_baseline_version_code`). null = 아직 기록 전 → NEW 없음
 * @param newCardDismissedVersionCode 메인 목록 "새 기능 안내" 카드를 닫았을 때의 최신 NEW 주제 versionCode (없으면 0)
 */
data class TutorialBadgePrefs(
    val viewedIds: Set<String> = emptySet(),
    val baselineVersionCode: Int? = null,
    val newCardDismissedVersionCode: Int = 0,
)

/** 메인 목록 맨 위 튜토리얼 카드 상태 */
sealed interface TutorialCardMode {
    data object Hidden : TutorialCardMode

    /** 첫 실행 안내 카드 ("SaveLink 사용 방법 보기") */
    data object Intro : TutorialCardMode

    /** 새 기능 안내 카드 ("새 기능 안내 n개"). ✕ 를 누르면 [latestVersionCode] 까지 닫은 것으로 기록한다. */
    data class News(val count: Int, val latestVersionCode: Int) : TutorialCardMode
}

/**
 * 튜토리얼 "NEW" 판단.
 *
 * ## 기준 versionCode (baseline)
 * 새로 설치한 사용자에게 모든 주제가 NEW 로 보이지 않도록, 사용자마다 기준 versionCode 를 한 번만 기록한다
 * (`PreferencesManager.ensureTutorialBaseline`, 앱 시작 시 Application.onCreate → ThemeMigrationRunner).
 * - 새로 설치: 설치한 버전의 versionCode. 그 버전까지 들어 있던 주제는 NEW 가 아니다.
 * - 이 기능 전(2.0.0 = versionCode 38 이하)부터 쓰던 사용자: [LEGACY_BASELINE_VERSION_CODE] (38).
 *   그래서 38 → 40 처럼 버전을 건너뛰어도 39, 40 에 추가된 주제가 NEW 로 보인다.
 *   지금 있는 14개 주제는 모두 addedInVersionCode = null 이라 지금은 아무것도 NEW 가 아니다.
 * - 기존 사용자 판별은 화면 모드 마이그레이션과 같은 hadPriorData(앱 시작 전 설정/DB 파일 존재 여부)를 쓴다.
 * 한 번 기록한 값은 바꾸지 않는다.
 *
 * ## NEW 조건
 * 지금 보이는 주제([TutorialTopics.visible]) 중 `addedInVersionCode > baseline` 이고 아직 보지 않은 주제.
 * 주제를 열면 본 주제로 저장되므로 NEW 가 사라진다. 숨겨진 주제(2분할 전용, 꺼진 플래그)는 세지 않는다.
 */
object TutorialNews {
    /** 2.0.0 의 versionCode. NEW 표시 기능 전부터 쓰던 사용자의 기준값 */
    const val LEGACY_BASELINE_VERSION_CODE = 38

    /** 처음 기록할 기준 versionCode */
    fun initialBaseline(hadPriorData: Boolean, currentVersionCode: Int): Int =
        if (hadPriorData) minOf(LEGACY_BASELINE_VERSION_CODE, currentVersionCode) else currentVersionCode

    fun isNew(topic: TutorialTopic, viewedIds: Set<String>, baselineVersionCode: Int?): Boolean {
        val addedIn = topic.addedInVersionCode ?: return false
        if (baselineVersionCode == null) return false
        return addedIn > baselineVersionCode && topic.id !in viewedIds
    }

    /** [visible] 중 NEW 주제 (화면 순서) */
    fun newTopics(visible: List<TutorialTopic>, prefs: TutorialBadgePrefs?): List<TutorialTopic> {
        if (prefs == null) return emptyList()
        return visible.filter { isNew(it, prefs.viewedIds, prefs.baselineVersionCode) }
    }

    fun newIds(visible: List<TutorialTopic>, prefs: TutorialBadgePrefs?): Set<String> =
        newTopics(visible, prefs).mapTo(mutableSetOf()) { it.id }

    /**
     * 메인 목록 카드.
     * - 첫 실행 안내 카드를 아직 닫지 않았으면 그 카드 ([TutorialCardMode.Intro]).
     * - 아니면 NEW 주제가 있고, 그 중 가장 새 버전이 지난번에 닫은 버전보다 새로우면 "새 기능 안내 n개".
     *   닫으면 그 버전까지 기록하므로, 더 새 버전의 주제가 추가될 때까지 다시 보이지 않는다.
     */
    fun cardMode(introCardVisible: Boolean, newTopics: List<TutorialTopic>, dismissedVersionCode: Int): TutorialCardMode {
        if (introCardVisible) return TutorialCardMode.Intro
        val latest = newTopics.mapNotNull { it.addedInVersionCode }.maxOrNull() ?: return TutorialCardMode.Hidden
        if (latest <= dismissedVersionCode) return TutorialCardMode.Hidden
        return TutorialCardMode.News(count = newTopics.size, latestVersionCode = latest)
    }
}
