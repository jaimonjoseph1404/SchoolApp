package org.familytools.educationtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.familytools.educationtracker.data.SettingsRepository

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val isDarkTheme: StateFlow<Boolean> = repository.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isPinLockEnabled: StateFlow<Boolean> = repository.isPinLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isBiometricEnabled: StateFlow<Boolean> = repository.isBiometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { repository.setDarkTheme(enabled) }
    }

    fun setPin(pin: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.setPin(pin)
            onDone()
        }
    }

    fun disablePin() {
        viewModelScope.launch { repository.disablePin() }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBiometricEnabled(enabled) }
    }

    suspend fun verifyPin(pin: String): Boolean = repository.verifyPin(pin)

    companion object {
        fun factory(repository: SettingsRepository) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(repository) as T
            }
        }
    }
}
