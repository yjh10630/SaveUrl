package com.jinscompany.saveurl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    val darkModeEnabled: StateFlow<Boolean?> = preferencesManager.darkModeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setFlexibleUpdate(isUpdatable: Boolean) {
        _isFlexibleUpdatable.value = isUpdatable
    }

    fun setFlexibleUpdateDownloaded(downloaded: Boolean) {
        _isFlexibleUpdateDownloaded.value = downloaded
    }

    fun setDarkMode(enabled: Boolean?) {
        viewModelScope.launch { preferencesManager.setDarkMode(enabled) }
    }
}
