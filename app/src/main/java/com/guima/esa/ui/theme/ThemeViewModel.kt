package com.guima.esa.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guima.esa.data.CloudSyncRepository
import com.guima.esa.data.UserRepository
import kotlinx.coroutines.launch

class ThemeViewModel : ViewModel() {
    var isDarkTheme by mutableStateOf(false)
        private set

    fun hydrateFromStorage(enabled: Boolean) {
        isDarkTheme = enabled
    }

    fun toggleTheme() {
        setDarkMode(!isDarkTheme)
    }

    fun setDarkMode(enabled: Boolean) {
        isDarkTheme = enabled
        UserRepository.saveDarkMode(enabled)
        viewModelScope.launch {
            CloudSyncRepository.safeSyncCurrentUser()
        }
    }
}
