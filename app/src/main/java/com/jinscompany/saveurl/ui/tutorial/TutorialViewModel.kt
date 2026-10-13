package com.jinscompany.saveurl.ui.tutorial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 튜토리얼 홈/주제 화면·설정 공통: 본 주제 집합과 NEW 표시 재료(DataStore)를 읽고 쓴다. */
@HiltViewModel
class TutorialViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    /** null = 아직 읽는 중 (진행 표시가 0 → N 으로 깜빡이지 않게) */
    val viewedTopicIds: StateFlow<Set<String>?> = preferencesManager.tutorialViewedTopics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** NEW 표시 재료 (본 주제, 기준 versionCode). null = 아직 읽는 중 → NEW 없음 */
    val badgePrefs: StateFlow<TutorialBadgePrefs?> = preferencesManager.tutorialBadgePrefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markViewed(topicId: String) {
        viewModelScope.launch { preferencesManager.addTutorialViewedTopic(topicId) }
    }
}
