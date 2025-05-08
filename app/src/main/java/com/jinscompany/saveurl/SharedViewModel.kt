package com.jinscompany.saveurl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.ui.navigation.Navigation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SharedViewModel @Inject constructor(): ViewModel() {

    val startDestination = Navigation.Routes.MAIN

    private val _snackBarEvent = MutableSharedFlow<SharedModelUiEffect>()
    val snackBarEvent = _snackBarEvent.asSharedFlow()

    private val _currentRoute = MutableSharedFlow<String>()
    val currentRoute = _currentRoute.asSharedFlow()

    fun notifySnackBarEvent(effect: SharedModelUiEffect) {
        viewModelScope.launch {
            _snackBarEvent.emit(effect)
        }
    }

    fun notifyCurrentRoute(route: String) {
        viewModelScope.launch {
            _currentRoute.emit(route)
        }
    }
}