package com.jinscompany.saveurl.domain.model

/**
 * 화면 모드 기본값 1회 마이그레이션 (리디자인 2단계).
 *
 * 리디자인 전 앱은 설정과 무관하게 항상 어두운 화면이었다. 새 버전의 기본값은 "시스템 따르기"이므로,
 * 화면 모드를 한 번도 고르지 않은(dark_mode 키 없음) 기존 사용자는 업데이트 후 갑자기 밝아질 수 있다.
 * 그래서 첫 실행 1회에 한해 "기존 사용자 + dark_mode 없음"이면 다크로 고정한다. 새로 설치한 사용자는 시스템을 따른다.
 *
 * 기존 사용자 판별: 앱 프로세스가 DataStore/Room 을 열기 전(Application.onCreate 맨 앞)에
 * 설정 파일(datastore/settings.preferences_pb) 또는 DB 파일(SaveUrl.db)이 이미 있었는지로 본다. → [hadPriorData]
 */
object ThemeDefaultMigration {

    enum class Action {
        /** dark_mode = true 로 기록 */
        SET_DARK,
        /** 바꾸지 않음 (완료 표시만 남김) */
        NONE,
    }

    fun hadPriorData(settingsFileExisted: Boolean, databaseFileExisted: Boolean): Boolean =
        settingsFileExisted || databaseFileExisted

    /**
     * @param alreadyMigrated 이전 실행에서 이미 마이그레이션을 끝냈는지 (theme_default_migrated)
     * @param darkModePref 현재 저장된 dark_mode 값 (null = 키 없음 = 시스템)
     * @param hadPriorData 이번 실행 시작 시점에 이전 앱 데이터가 있었는지
     */
    fun decide(alreadyMigrated: Boolean, darkModePref: Boolean?, hadPriorData: Boolean): Action = when {
        alreadyMigrated -> Action.NONE
        darkModePref != null -> Action.NONE   // 이미 직접 고른 사용자: 선택 존중
        hadPriorData -> Action.SET_DARK        // 기존 사용자 + 선택한 적 없음: 지금까지처럼 다크 유지
        else -> Action.NONE                    // 새 설치: 시스템 따르기
    }
}
