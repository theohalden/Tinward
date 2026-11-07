package dev.kosh.financetracker.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.data.settings.Appearance
import dev.kosh.financetracker.data.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val settings: SettingsStore,
) : ViewModel() {
    val appearance: StateFlow<Appearance> = settings.appearance
        .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance.SYSTEM)

    /** null while the first read is in flight. */
    val onboarded: StateFlow<Boolean?> = kotlinx.coroutines.flow.flow<Boolean?> {
        settings.onboarded.collect { emit(it) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun finishOnboarding() {
        viewModelScope.launch { settings.setOnboarded() }
    }
}
