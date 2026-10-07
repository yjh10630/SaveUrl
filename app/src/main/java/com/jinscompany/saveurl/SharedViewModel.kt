package com.jinscompany.saveurl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.model.ListViewMode
import com.jinscompany.saveurl.domain.model.ThemeMode
import com.jinscompany.saveurl.utils.PreferencesManager
import com.jinscompany.saveurl.utils.ThemeMigrationRunner
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SharedViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    themeMigrationRunner: ThemeMigrationRunner,
): ViewModel() {

    private val _isFlexibleUpdatable = MutableStateFlow(false)
    val isFlexibleUpdatable: StateFlow<Boolean> = _isFlexibleUpdatable.asStateFlow()

    private val _isFlexibleUpdateDownloaded = MutableStateFlow(false)
    val isFlexibleUpdateDownloaded: StateFlow<Boolean> = _isFlexibleUpdateDownloaded.asStateFlow()

    /**
     * null = 아직 읽기 전. 화면 모드 기본값 마이그레이션이 끝난 뒤에 값을 내보내므로,
     * 기존 사용자가 첫 실행에 잠깐 밝은 화면을 보는 일이 없다 (그동안 스플래시 유지, MainActivity 참고).
     */
    val themeMode: StateFlow<ThemeMode?> = flow {
        themeMigrationRunner.awaitDone()
        emitAll(preferencesManager.themeMode)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val listViewMode: StateFlow<ListViewMode> = preferencesManager.listViewMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListViewMode.DEFAULT)

    fun setFlexibleUpdate(isUpdatable: Boolean) {
        _isFlexibleUpdatable.value = isUpdatable
    }

    fun setFlexibleUpdateDownloaded(downloaded: Boolean) {
        _isFlexibleUpdateDownloaded.value = downloaded
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun setListViewMode(mode: ListViewMode) {
        viewModelScope.launch { preferencesManager.setListViewMode(mode) }
    }
}
