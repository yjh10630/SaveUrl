package com.jinscompany.saveurl.utils

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.jinscompany.saveurl.ui.tutorial.TutorialFeatureFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote Config 기능 스위치. 값은 [InAppUpdateCheck] 가 app_info 와 함께 받아 활성화한 뒤 [refresh] 로 다시 읽는다.
 *
 * - `import_enabled` (기본 true): 링크 가져오기. 끄면 설정 줄·공유 분기·빈 화면 버튼·튜토리얼 주제가 숨고
 *   설정에 예전 "데이터 가져오기 (CSV)" 줄이 돌아온다 (34번 §0).
 *
 * 콘솔에 값이 없거나 아직 한 번도 받지 못했으면 기본값을 쓴다 (기본값 등록 전에도 켜져 있도록 여기서 직접 판단).
 */
@Singleton
class RemoteFlags @Inject constructor() : TutorialFeatureFlags {

    private val _importEnabled = MutableStateFlow(read(KEY_IMPORT_ENABLED, DEFAULT_IMPORT_ENABLED))
    val importEnabled: StateFlow<Boolean> = _importEnabled.asStateFlow()

    /** Remote Config 를 활성화한 뒤 부른다 */
    fun refresh() {
        _importEnabled.value = read(KEY_IMPORT_ENABLED, DEFAULT_IMPORT_ENABLED)
    }

    override fun isEnabled(key: String): Boolean = when (key) {
        KEY_IMPORT_ENABLED -> _importEnabled.value
        else -> false
    }

    private fun read(key: String, default: Boolean): Boolean = runCatching {
        val value = Firebase.remoteConfig.getValue(key)
        if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) default else value.asBoolean()
    }.getOrDefault(default)

    companion object {
        const val KEY_IMPORT_ENABLED = "import_enabled"
        const val DEFAULT_IMPORT_ENABLED = true

        /** Remote Config 기본값 (InAppUpdateCheck.setDefaultsAsync 에 함께 넣는다) */
        val DEFAULTS: Map<String, Any> = mapOf(KEY_IMPORT_ENABLED to DEFAULT_IMPORT_ENABLED)
    }
}
