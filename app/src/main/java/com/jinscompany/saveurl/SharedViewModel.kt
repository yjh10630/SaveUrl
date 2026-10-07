package com.jinscompany.saveurl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.model.ListViewMode
import com.jinscompany.saveurl.domain.model.ThemeMode
import com.jinscompany.saveurl.utils.PreferencesManager
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
): ViewModel() {

    private val _isFlexibleUpdatable = MutableStateFlow(false)
    val isFlexibleUpdatable: StateFlow<Boolean> = _isFlexibleUpdatable.asStateFlow()

    private val _isFlexibleUpdateDownloaded = MutableStateFlow(false)
    val isFlexibleUpdateDownloaded: StateFlow<Boolean> = _isFlexibleUpdateDownloaded.asStateFlow()

    /** null = 아직 DataStore 에서 읽기 전 (첫 프레임은 시스템 설정을 따른다) */
    val themeMode: StateFlow<ThemeMode?> = preferencesManager.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
